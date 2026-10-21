package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * The plugin's publication flow: the debounced, session-fenced killclog.com
 * sync push with its single-flight gate and queued intent, and the character
 * publish that may run one prerequisite sync and retry once. Event
 * subscriptions stay in the plugin; it forwards the moments that matter
 * (captures, login, logout, opt-in, opt-out, shutdown) and hands the panel's
 * feedback in through {@link Feedback}. Sync and character publish share
 * state on purpose: a publish can be parked behind a sync, so they are one
 * unit.
 */
@Slf4j
final class PublicationCoordinator
{
	/** The panel's side of the flow. Every call arrives on the EDT. */
	interface Feedback
	{
		void showSyncProgress(boolean manual, String text, boolean autoClear);

		void showSyncResult(boolean manual, boolean ok, String message);

		void showCharacterPublishStatus(CharacterStatus status, boolean autoClear, String failureMessage);

		/** The sync's own line and memory taken down, nothing else on the row. */
		void resetSyncFeedback();
	}

	// Quiet time: a log walk lands page after page, and one push carries them all once the captures stop for this
	// long. A walk that never pauses still sends a minute after its first capture.
	static final int SYNC_DEBOUNCE_SECONDS = 10;
	static final long SYNC_MAX_WAIT_MS = 60_000L;
	private static final String SYNC_FAILED = "Collection log sync failed. See the client log.";

	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	// Who is logged in: every push and character request is for the login it began in, and stands down once it ends.
	private final SessionTracker sessions;
	private final SyncService syncService;
	private final ProfileAppearanceService profileAppearanceService;
	private final KillClogChatNotifier chatNotifier;
	private final Feedback feedback;
	private final Supplier<AccountType> localAccountType;
	// The world's game mode, each mode's own store and a League's RuneLite PB profile type;
	// no mode means nothing is sent.
	private final Supplier<String> mode;
	private final Function<String, LocalClogCache> modeCache;
	private final Function<String, String> leagueProfileType;

	private volatile ScheduledFuture<?> pendingKillclogSync;
	// The push that timer sends.
	private Push pendingPush;
	private int withdrawals;
	// Whether the push waiting is an automatic one, which a capture may push back, and when its first capture came.
	private boolean pendingAutomatic;
	private long quietSince;
	// The login the server turned away (opted out, the name another account's, an update needed): its automatic
	// pushes stop, so the log stops leaving the client, until the next login or a click.
	private volatile long stoppedSession = -1;
	/* package */ java.util.function.LongSupplier clock = System::currentTimeMillis;
	private final KillclogSyncGate<Push> syncGate = new KillclogSyncGate<>(Push::deliberate, Push::merge);

	// The character request, one at a time, all under this coordinator's lock so a check and the change it allows are
	// one step. The generation only grows: a withdrawal (a logout, a setting turned off) moves it on, so work from
	// before can never act, wait or free the slot again. A request keeps its slot through its log's sync and retry.
	private static final int NONE = -1;
	private int characterGeneration;
	private int characterHolder = NONE;
	private int characterWaiting = NONE;
	private boolean characterPrerequisiteAttempted;
	// The login the request in flight belongs to, taken where it began (the press).
	private Session characterSession = Session.none(0);

	PublicationCoordinator(KillClogConfig config, ConfigManager configManager,
		ClientThread clientThread, ScheduledExecutorService executor, SessionTracker sessions,
		SyncService syncService, ProfileAppearanceService profileAppearanceService,
		KillClogChatNotifier chatNotifier, Feedback feedback, Supplier<AccountType> localAccountType,
		Supplier<String> mode, Function<String, LocalClogCache> modeCache, Function<String, String> leagueProfileType)
	{
		this.config = config;
		this.configManager = configManager;
		this.clientThread = clientThread;
		this.executor = executor;
		this.sessions = sessions;
		this.syncService = syncService;
		this.profileAppearanceService = profileAppearanceService;
		this.chatNotifier = chatNotifier;
		this.feedback = feedback;
		this.localAccountType = localAccountType;
		this.mode = mode;
		this.modeCache = modeCache;
		this.leagueProfileType = leagueProfileType;
	}

	// ── plugin-facing ──────────────────────────────────────────────────

	boolean characterPublishingEnabled()
	{
		return config.killclogSync() && config.characterModel();
	}

	/**
	 * One push of the log: the login it is for, taken where it began (a press, the opt-in, a capture) and carried
	 * through its timer, its retry and its queue, so nothing begun in one login sends another's; its game (null follows
	 * the world); whether a player asked for it; whether it is the server's one advised retry; and the character request
	 * it is the sync for, by generation (NONE for none), which only this push can let go on.
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class Push
	{
		final Session session;
		final String mode;
		final boolean manual;
		final boolean retry;
		final int forCharacter;

		/** The server's one retry, for the game the attempt resolved, wherever the player has hopped since. */
		Push retriedIn(String gameMode)
		{
			return new Push(session, gameMode, manual, true, forCharacter);
		}

		Push withoutCharacter()
		{
			return new Push(session, mode, manual, retry, NONE);
		}

		/** A click's or a character's: it goes past Automatic sync being off, and nothing automatic stands in for it. */
		boolean deliberate()
		{
			return manual || forCharacter != NONE;
		}

		/**
		 * Two pushes, the one waiting and a later one, go as one: a click's game over an automatic one's, the later
		 * click's among clicks, the later character request over an earlier one, and one advised retry for both.
		 */
		static Push merge(Push waiting, Push later)
		{
			Push lead = later.manual || !waiting.manual ? later : waiting;
			return new Push(lead.session, lead.mode, waiting.manual || later.manual, waiting.retry || later.retry,
				later.forCharacter != NONE ? later.forCharacter : waiting.forCharacter);
		}
	}

	/** A capture, login or settled identity, on the client thread: one quiet push for this game after the debounce. */
	void scheduleAutomaticSync()
	{
		scheduleAutomaticSync(sessions.current());
	}

	private void scheduleAutomaticSync(Session session)
	{
		schedule(SYNC_DEBOUNCE_SECONDS, new Push(session, mode.get(), false, false, NONE));
	}

	/**
	 * Manual web pushes (the panel sync button, an explicit opt-in) narrate in chat;
	 * automatic ones (capture debounce and login catch-up) default to silent
	 * panel feedback. Chat still follows its separate setting.
	 */
	void pushNow(Session origin, boolean manual)
	{
		pushNow(origin, manual, NONE);
	}

	private void pushNow(Session origin, boolean manual, int forCharacter)
	{
		// The game it pushes is read on the client thread; a logout before the hop drops it.
		clientThread.invoke(() ->
		{
			if (sessions.isCurrent(origin))
			{
				schedule(0, new Push(origin, mode.get(), manual, false, forCharacter));
			}
			else
			{
				failWaiting(forCharacter);
			}
		});
	}

	/**
	 * Whether a push goes now: sync is on and its login lasts; a click always; a character's own sync while that
	 * request still waits on it; an automatic one with Automatic sync on and its login not turned away.
	 */
	private boolean sends(Push push)
	{
		if (!config.killclogSync() || !sessions.isCurrent(push.session))
		{
			return false;
		}
		if (push.manual)
		{
			return true;
		}
		if (push.forCharacter != NONE)
		{
			return waitingFor(push.forCharacter);
		}
		return config.automaticSync() && push.session.getNumber() != stoppedSession;
	}

	/**
	 * A push after its delay. A push that can't go fails the character request it was for, so none waits forever. An
	 * automatic push while another automatic one waits starts its quiet time again, up to the minute's cap; one behind
	 * a click, a character's sync or a retry rides it. A click or a character's sync takes over what waits, keeping
	 * any character request that rode on it.
	 */
	private synchronized void schedule(int delaySeconds, Push asked)
	{
		Push push = live(asked);
		if (push.manual)
		{
			stoppedSession = -1;
		}
		if (!sends(push))
		{
			failWaiting(push.forCharacter);
			return;
		}
		long now = clock.getAsLong();
		long delayMs = delaySeconds * 1000L;
		if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
		{
			if (!push.deliberate())
			{
				long left = quietSince + SYNC_MAX_WAIT_MS - now;
				if (pendingAutomatic && !push.retry && left > 0)
				{
					pendingKillclogSync.cancel(false);
					schedulePush(Math.min(delayMs, left), push);
				}
				return;
			}
			pendingKillclogSync.cancel(false);
			push = Push.merge(pendingPush, push);
		}
		quietSince = now;
		schedulePush(delayMs, push);
	}

	private void schedulePush(long delayMs, Push push)
	{
		pendingPush = push;
		pendingAutomatic = !push.deliberate() && !push.retry;
		pendingKillclogSync = executor.schedule(() -> send(push), delayMs, TimeUnit.MILLISECONDS);
	}

	/**
	 * The server's one advised retry, after its own delay: a capture's shorter debounce must not stand in for it. Its
	 * login is checked before anything waiting is touched, so a retry from an ended login cancels nothing of the next.
	 */
	private synchronized boolean scheduleRetry(int delaySeconds, Push retry)
	{
		if (!sends(live(retry)))
		{
			failWaiting(retry.forCharacter);
			return false;
		}
		// A click or a character's sync asked meanwhile is the later of the two, and rides this retry.
		Push next = pendingKillclogSync != null && !pendingKillclogSync.isDone() && pendingPush.deliberate()
			? Push.merge(retry, pendingPush) : retry;
		dropPendingSync();
		schedule(delaySeconds, next);
		return true;
	}

	/** A push whose character request was withdrawn is just a push again, so it goes, or not, on its own terms. */
	private Push live(Push push)
	{
		return push.forCharacter == NONE || waitingFor(push.forCharacter) ? push : push.withoutCharacter();
	}

	/** How many times automatic pushes were withdrawn (Automatic sync off, opt-out): a retry's line checks it. */
	private synchronized int withdrawals()
	{
		return withdrawals;
	}

	/** The push waiting on its timer, if any, never fires. */
	private synchronized void dropPendingSync()
	{
		if (pendingKillclogSync != null)
		{
			pendingKillclogSync.cancel(false);
			pendingKillclogSync = null;
			pendingPush = null;
		}
	}

	synchronized void cancelSync()
	{
		withdrawals++;
		syncGate.cancel();
		dropPendingSync();
		// A request already in the air keeps the single-flight slot until it
		// completes (no overlap on re-enable); its completion sees a newer
		// generation and stays silent.
	}

	/**
	 * Automatic sync turned off: an automatic push not yet sent never goes, waiting on its timer or its retry, and one
	 * in the air ends quietly, its line taken down; a click or a character's own sync goes on.
	 */
	void cancelAutomaticSync()
	{
		boolean silenced = syncGate.cancelAutomatic();
		if (dropPendingAutomatic() || silenced)
		{
			SwingUtilities.invokeLater(feedback::resetSyncFeedback);
		}
	}

	private synchronized boolean dropPendingAutomatic()
	{
		withdrawals++;
		if (pendingKillclogSync == null || pendingKillclogSync.isDone() || pendingPush.deliberate())
		{
			return false;
		}
		dropPendingSync();
		return true;
	}

	/**
	 * The panel's sync arrow: push now, skipping any pending debounce. The
	 * single-flight gate remembers a click during an in-flight request, so
	 * icon-only feedback never makes that deliberate action disappear.
	 */
	void manualSync()
	{
		Session pressed = sessions.current();
		if (config.killclogSync())
		{
			pushNow(pressed, true);
		}
	}

	void publishCharacter()
	{
		startCharacterPublish(sessions.current(), NONE);
	}

	/**
	 * Shift-click on the character: removes it from killclog.com, so the profile goes back to having none. It shares
	 * the publish's one-at-a-time slot and its status line.
	 */
	void deleteCharacter()
	{
		Session pressed = sessions.current();
		startCharacterRequest(pressed, NONE, CharacterStatus.DELETING, profileAppearanceService::deleteCurrent,
			(result, error, generation) ->
			{
				if (error != null || result == null)
				{
					failCharacterPublish(generation);
				}
				else if (finishCharacter(generation))
				{
					showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome), true, result.message);
				}
			});
	}

	void cancelCharacterPublish()
	{
		int generation;
		synchronized (this)
		{
			generation = ++characterGeneration;
			characterHolder = NONE;
			characterWaiting = NONE;
			characterPrerequisiteAttempted = false;
		}
		showCharacterPublishStatus(generation, CharacterStatus.CLEAR, false);
	}

	// ── character publish ──────────────────────────────────────────────

	/** The attempt is over and failed: the slot frees and the row says so, only while the attempt is still current. */
	private void failCharacterPublish(int generation)
	{
		if (finishCharacter(generation))
		{
			showCharacterPublishStatus(generation, CharacterStatus.FAILED, true);
		}
	}

	/** The request still current, holding the slot, ends and frees it; false for anything withdrawn or stale. */
	private synchronized boolean finishCharacter(int generation)
	{
		if (generation != characterGeneration || characterHolder != generation)
		{
			return false;
		}
		characterHolder = NONE;
		characterWaiting = NONE;
		return true;
	}

	private synchronized boolean currentCharacter(int generation)
	{
		return generation == characterGeneration;
	}

	private void showCharacterPublishStatus(int generation, CharacterStatus status, boolean autoClear)
	{
		showCharacterPublishStatus(generation, status, autoClear, null);
	}

	private void showCharacterPublishStatus(int generation, CharacterStatus status, boolean autoClear,
		String failureMessage)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (currentCharacter(generation) && (status == CharacterStatus.CLEAR || characterPublishingEnabled()))
			{
				feedback.showCharacterPublishStatus(status, autoClear, failureMessage);
			}
		});
	}

	/** A publish: a press's own ({@code continues} NONE), or the same request again after its log's sync. */
	private void startCharacterPublish(Session origin, int continues)
	{
		startCharacterRequest(origin, continues, CharacterStatus.RENDERING, profileAppearanceService::publishCurrent,
			this::handleCharacterPublishResult);
	}

	/** A character request: publish or delete, for the logged-in player of the main game. */
	@FunctionalInterface
	private interface CharacterRequest
	{
		CompletableFuture<ProfileAppearanceService.PublishResult> start(Session session, BooleanSupplier authorized);
	}

	@FunctionalInterface
	private interface CharacterOutcome
	{
		void handle(ProfileAppearanceService.PublishResult result, Throwable error, int generation);
	}

	/**
	 * One character request at a time, for the login it was pressed in ({@code origin}): its status line at once, then
	 * on the client thread the request, unless that login has ended or left the main game (it fails, or a withdrawn
	 * one stands down). A request that {@code continues} an earlier one (its generation) takes its place only while
	 * that one is still current and holds the slot, keeping its login.
	 */
	private void startCharacterRequest(Session origin, int continues, CharacterStatus progress, CharacterRequest request,
		CharacterOutcome outcome)
	{
		if (!characterPublishingEnabled())
		{
			return;
		}
		int generation;
		Session session;
		synchronized (this)
		{
			// A press whose login has ended claims nothing; a continuation needs its own request still holding the slot.
			if (continues == NONE ? characterHolder != NONE || !sessions.isCurrent(origin)
				: continues != characterGeneration || characterHolder != continues)
			{
				return;
			}
			if (continues == NONE)
			{
				characterSession = origin;
				characterPrerequisiteAttempted = false;
			}
			generation = ++characterGeneration;
			characterHolder = generation;
			session = characterSession;
		}
		showCharacterPublishStatus(generation, progress, false);
		clientThread.invokeLater(() ->
		{
			if (!currentCharacter(generation))
			{
				return;
			}
			if (!characterPublishingEnabled() || !session.loggedIn() || !sessions.isCurrent(session)
				|| !GameMode.MAIN.equals(mode.get()))
			{
				failCharacterPublish(generation);
				return;
			}
			request.start(session, () -> currentCharacter(generation) && characterPublishingEnabled()
					&& sessions.isCurrent(session))
				.whenComplete((result, error) -> outcome.handle(result, error, generation));
		});
	}

	private void handleCharacterPublishResult(ProfileAppearanceService.PublishResult result,
		Throwable error, int generation)
	{
		if (!currentCharacter(generation) || !characterPublishingEnabled())
		{
			return;
		}
		if (error != null || result == null)
		{
			failCharacterPublish(generation);
			return;
		}

		Session waiting = result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED ? waitOnSync(generation) : null;
		if (waiting != null)
		{
			showCharacterPublishStatus(generation, CharacterStatus.RENDERING, false);
			pushNow(waiting, false, generation);
		}
		else if (finishCharacter(generation))
		{
			showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome), true, result.message);
		}
	}

	/**
	 * The request, still current and holding the slot, waits on one sync of its log first; its login, which the sync
	 * is for. Null when it was withdrawn, or has had its one sync already.
	 */
	private synchronized Session waitOnSync(int generation)
	{
		if (generation != characterGeneration || characterHolder != generation || characterPrerequisiteAttempted)
		{
			return null;
		}
		characterPrerequisiteAttempted = true;
		characterWaiting = generation;
		return characterSession;
	}

	/** Whether this request is still current and waits on its sync. */
	private synchronized boolean waitingFor(int generation)
	{
		return generation != NONE && characterWaiting == generation && generation == characterGeneration;
	}

	/** Its sync ended: the request stops waiting; false when it was no longer the one waiting. */
	private synchronized boolean takeWaiting(int generation)
	{
		if (!waitingFor(generation))
		{
			return false;
		}
		characterWaiting = NONE;
		return true;
	}

	/** The sync a request waited on can't go or broke: the request fails, if it still waited on it. */
	private void failWaiting(int generation)
	{
		if (takeWaiting(generation))
		{
			failCharacterPublish(generation);
		}
	}

	/** The publish again once its log's sync landed: the same request, keeping its slot and its login. */
	private void scheduleCharacterPublishAfterSync(int generation)
	{
		try
		{
			executor.schedule(() ->
			{
				if (characterPublishingEnabled())
				{
					startCharacterPublish(null, generation);
				}
			}, ProfileAppearanceService.PUBLISH_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			failCharacterPublish(generation);
		}
	}

	// ── sync push ──────────────────────────────────────────────────────

	// If a push arrived while the slot was occupied, launch it now that the
	// slot is free (the opt-out/opt-in-mid-request case).
	private void launchQueuedSync()
	{
		Push queued = syncGate.consumeQueued();
		if (queued != null && config.killclogSync())
		{
			schedule(0, queued);
		}
	}

	private void send(Push asked)
	{
		// Re-checked at fire time: the player may have opted out while the debounce was pending, and the login is the
		// push's own, so a task that escaped cancellation can never bind itself to whoever is logged in later.
		Push push = live(asked);
		if (!sends(push))
		{
			failWaiting(push.forCharacter);
			return;
		}
		final int generation = syncGate.beginAttempt(push);
		if (generation < 0)
		{
			return;
		}
		Session session = push.session;
		if (!sessions.isCurrent(session))
		{
			syncGate.abortAttempt();
			failWaiting(push.forCharacter);
			launchQueuedSync();
			return;
		}
		clientThread.invoke(() ->
		{
			// Any throw before the future takes ownership must release the
			// single-flight slot, or sync is silently dead until restart -
			// the client thread swallows the exception and the user sees
			// nothing.
			try
			{
				// The timer may have entered send just before logout, leaving this client-thread callback queued
				// behind the account switch. Never gather the next account under the old attempt's generation.
				if (!syncGate.isCurrent(generation) || !sessions.isCurrent(session))
				{
					syncGate.abortAttempt();
					failWaiting(push.forCharacter);
					launchQueuedSync();
					return;
				}
				// A League sync reads only that League's store and profile, so it finishes after a hop
				// off its worlds; a main sync needs a settled main world, so it follows this one.
				String gameMode = push.mode == null || GameMode.MAIN.equals(push.mode) ? mode.get() : push.mode;
				LocalClogCache cache = gameMode == null ? null : modeCache.apply(gameMode);
				if (!session.loggedIn() || gameMode == null || cache == null)
				{
					syncGate.abortAttempt();
					failWaiting(push.forCharacter);
					launchQueuedSync();
					if (push.mode != null && gameMode == null && !push.manual)
					{
						// Fired before a new world settled: its login catch-up waited behind this timer.
						scheduleAutomaticSync(session);
					}
					return;
				}
				// The type supplier answers only on a settled main world. A League's PBs come only from
				// the RuneLite profile the server names: until RuneLite knows a new League, it files
				// that League's PBs under an older one.
				boolean main = GameMode.MAIN.equals(gameMode);
				// A League is one account status for everyone: its sync never carries a main type.
				AccountType accountType = main ? localAccountType.get() : null;
				if (push.manual)
				{
					chatNotifier.send(ChatNotice.SYNC_RESULT, "Syncing collection log...");
				}
				List<String> profileKeys = PersonalBests.profileKeys(configManager.getRSProfiles(), session.getAccountHash(),
					main ? RuneScapeProfileType.STANDARD.name() : leagueProfileType.apply(gameMode));
				Map<String, Double> pbs = new LinkedHashMap<>();
				Map<String, PbRecord> detailedPbs = gatherPersonalBests(profileKeys, pbs);
				// Off the client thread before dispatch: the sync pre-flight can block up to ten seconds waiting for
				// the rename disk verdict, and game ticks must never pay that wait. The login rides along: a logout
				// between this gather and the dispatch must kill the attempt, not let a dead session post.
				executor.execute(() -> dispatch(push, accountType, pbs, detailedPbs, generation, cache, gameMode));
			}
			catch (RuntimeException e)
			{
				log.warn("killclog sync push failed before dispatch", e);
				failBeforeSend(generation, push);
			}
		});
	}

	private void dispatch(Push push, AccountType accountType, Map<String, Double> pbs, Map<String, PbRecord> detailedPbs,
		int generation, LocalClogCache cache, String gameMode)
	{
		Session session = push.session;
		boolean character = push.forCharacter != NONE;
		if (!syncGate.isCurrent(generation) || !sends(push))
		{
			// The login ended, or the player stopped this send, between gather and dispatch: release the
			// single-flight slot and walk away clean, failing the character request it was for.
			syncGate.abortAttempt();
			failWaiting(push.forCharacter);
			launchQueuedSync();
			return;
		}
		try
		{
			syncService.syncCollectionLog(session, accountType, pbs, detailedPbs,
				syncGate, generation, cache, gameMode, push.deliberate(), () ->
				{
					// Only a push that really goes says so: one killclog.com already holds stays quiet.
					if (!character)
					{
						withSyncFeedback(generation, session,
							() -> feedback.showSyncProgress(push.manual, "syncing...", false));
					}
				})
				.whenComplete((result, err) ->
				{
					boolean current = syncGate.complete(generation);
					if (result != null && current && config.killclogSync())
					{
						// Server-advised contention retry: another client of this account held the lock. A character
						// request rides that one allowed retry.
						if (result.retryAdvised && !push.retry)
						{
							// Nobody is watching an automatic sync, so it waits out a
							// slow restart. A player waiting on a click or a character
							// publish gets the advised delay as it is.
							int delay = Math.max(result.retryAfterSeconds, 2);
							int withdrawn = withdrawals();
							boolean retrying = scheduleRetry(push.deliberate() ? delay : delay * 2, push.retriedIn(gameMode));
							if (character)
							{
								showCharacterPublishStatus(push.forCharacter, CharacterStatus.RENDERING, false);
							}
							else if (retrying)
							{
								// Said only while nothing withdrew it: Automatic sync turned off meanwhile drops an automatic one.
								SwingUtilities.invokeLater(() ->
								{
									if ((push.deliberate() || withdrawals() == withdrawn) && config.killclogSync()
										&& sessions.isCurrent(session))
									{
										feedback.showSyncProgress(push.manual, "retrying...", false);
									}
								});
							}
							else
							{
								// Its "syncing..." has nothing left to say.
								SwingUtilities.invokeLater(feedback::resetSyncFeedback);
							}
							launchQueuedSync();
							return;
						}

						// Everything below is a terminal outcome for this episode.
						if (result.stopsSession)
						{
							stoppedSession = session.getNumber();
						}
						if (character)
						{
							// Only the request this sync was for goes on or fails; a withdrawn one has nothing to say.
							if (takeWaiting(push.forCharacter))
							{
								if (result.ok && !result.dryRun && characterPublishingEnabled())
								{
									scheduleCharacterPublishAfterSync(push.forCharacter);
								}
								else
								{
									failCharacterPublish(push.forCharacter);
								}
							}
						}
						else if (result != SyncService.UNCHANGED)
						{
							withSyncFeedback(generation, session,
								() -> feedback.showSyncResult(push.manual, result.ok, result.message));
						}
						if (push.manual || !result.ok)
						{
							chatFor(session, result.message);
						}
					}
					else if (current)
					{
						if (character)
						{
							failWaiting(push.forCharacter);
						}
						else if (err != null)
						{
							withSyncFeedback(generation, session, () -> feedback.showSyncResult(push.manual,
								false, SYNC_FAILED));
						}
					}
					launchQueuedSync();
				});
		}
		catch (RuntimeException e)
		{
			log.warn("killclog sync push failed at dispatch", e);
			failBeforeSend(generation, push);
		}
	}

	/** Failures always chat; chat sends need the client thread, which invoke runs at once when already on it. */
	private void failBeforeSend(int generation, Push push)
	{
		syncGate.abortAttempt();
		if (push.forCharacter != NONE)
		{
			failWaiting(push.forCharacter);
		}
		else
		{
			withSyncFeedback(generation, push.session, () -> feedback.showSyncResult(push.manual,
				false, SYNC_FAILED));
		}
		chatFor(push.session, SYNC_FAILED);
		launchQueuedSync();
	}

	/** A sync's chat line, said only while its login lasts: the next login never hears the last one's. */
	private void chatFor(Session session, String message)
	{
		clientThread.invoke(() ->
		{
			if (sessions.isCurrent(session))
			{
				chatNotifier.send(ChatNotice.SYNC_RESULT, message);
			}
		});
	}

	private void withSyncFeedback(int generation, Session session, Runnable feedbackCall)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (syncGate.isCurrent(generation) && config.killclogSync() && sessions.isCurrent(session))
			{
				feedbackCall.run();
			}
		});
	}

	// ── personal-best cargo ────────────────────────────────────────────

	/**
	 * RuneLite's own chat-commands store records the local player's personal
	 * bests; no public provider serves them, which makes this map the sync's
	 * defining cargo. One account splinters into many rs-profile fragments
	 * over time, so the gather reads every fragment owned by the captured
	 * account hash ({@link PersonalBests#records}). STANDARD-world fragments
	 * only: Leagues and speedrun profiles share the display name but store
	 * buffed-world times, and the min-merge would launder those into the
	 * player's real record. Client thread (config reads).
	 */
	private Map<String, PbRecord> gatherPersonalBests(List<String> profileKeys, Map<String, Double> pbs)
	{
		PersonalBests store = new PersonalBests(configManager, profileKeys);
		Map<String, PbRecord> out = new LinkedHashMap<>();
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			// Each team size's time as the card shows it: RuneLite's store, the game's own records over it.
			Map<String, PbRecord> records = store.records(boss.getName());
			records.forEach((key, record) -> out.merge(key, record, PbRecord::pick));
			// The boss's best at any team size, from those same records.
			records.values().stream().mapToDouble(record -> record.seconds).min()
				.ifPresent(seconds -> pbs.put(boss.getName(), seconds));
		}
		log.debug("killclog sync pb gather: {} owned profiles, {} bosses from RuneLite's store, {} detailed pbs"
			+ " (the store, the adventure log, the collection log, kills seen)", profileKeys.size(), pbs.size(), out.size());
		return out;
	}

}
