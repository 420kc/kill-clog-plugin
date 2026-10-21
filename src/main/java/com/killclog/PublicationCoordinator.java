package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
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
	// Whether the push waiting is an automatic one, which a capture may push back, and when its first capture came.
	private boolean pendingAutomatic;
	private long quietSince;
	// The login the server turned away (opted out, the name another account's, an update needed): its automatic
	// pushes stop, so the log stops leaving the client, until the next login or a click.
	private volatile long stoppedSession = -1;
	/* package */ java.util.function.LongSupplier clock = System::currentTimeMillis;
	private final KillclogSyncGate syncGate = new KillclogSyncGate();
	private final AtomicBoolean characterPublishInFlight = new AtomicBoolean();
	// The character request waiting on a sync of the log first, by its generation; -1 for none. Only the request still
	// current is waiting, so one withdrawn while it handed over can never publish for whoever logs in next.
	private final AtomicInteger characterWaitingGeneration = new AtomicInteger(-1);
	private final AtomicBoolean characterPrerequisiteAttempted = new AtomicBoolean();
	private final AtomicInteger characterPublishGeneration = new AtomicInteger();

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

	/** A capture, login or settled identity, on the client thread: one quiet push for this game after the debounce. */
	void scheduleAutomaticSync()
	{
		scheduleSync(SYNC_DEBOUNCE_SECONDS, false, mode.get(), false);
	}

	/**
	 * Manual web pushes (the panel sync button, an explicit opt-in) narrate in chat;
	 * automatic ones (capture debounce and login catch-up) default to silent
	 * panel feedback. Chat still follows its separate setting.
	 */
	void scheduleSync(int delaySeconds, boolean manual)
	{
		// A press arrives on the panel's thread; the game it pushes is read on the client's. Its account is fixed at
		// the press: a logout before the hop drops it, never letting it push the next account's log.
		Session pressed = sessions.current();
		clientThread.invoke(() ->
		{
			if (sessions.isCurrent(pressed))
			{
				scheduleSync(delaySeconds, manual, mode.get(), false);
			}
		});
	}

	/** Sync is on, and an automatic send has Automatic sync too: a click and a character's own sync always go. */
	private boolean sends(boolean manual)
	{
		return config.killclogSync() && (manual || (config.automaticSync() || characterWaiting())
			&& sessions.current().getNumber() != stoppedSession);
	}

	/**
	 * A retry keeps the game its attempt was for, wherever the player has hopped since. A push that is itself the
	 * server-advised retry never retries again; every other push starts with its one retry.
	 */
	private synchronized void scheduleSync(int delaySeconds, boolean manual, String scheduledMode, boolean retry)
	{
		if (manual)
		{
			stoppedSession = -1;
		}
		if (!sends(manual))
		{
			return;
		}
		long now = clock.getAsLong();
		long delayMs = delaySeconds * 1000L;
		if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
		{
			if (!manual)
			{
				// A capture while an automatic push waits starts its quiet time again, up to the minute's cap; a
				// waiting retry or click keeps its own time.
				long left = quietSince + SYNC_MAX_WAIT_MS - now;
				if (pendingAutomatic && !retry && left > 0)
				{
					pendingKillclogSync.cancel(false);
					schedulePush(Math.min(delayMs, left), false, scheduledMode, false);
				}
				return;
			}
			pendingKillclogSync.cancel(false);
		}
		quietSince = now;
		schedulePush(delayMs, manual, scheduledMode, retry);
	}

	private void schedulePush(long delayMs, boolean manual, String scheduledMode, boolean retry)
	{
		pendingAutomatic = !manual && !retry;
		Session scheduled = sessions.current();
		pendingKillclogSync = executor.schedule(() -> pushKillclogSync(manual, scheduled, scheduledMode, retry),
			delayMs, TimeUnit.MILLISECONDS);
	}

	/** The retry keeps its own delay: a capture's shorter debounce must not stand in for it. */
	private synchronized void scheduleRetry(int delaySeconds, boolean manual, String gameMode)
	{
		dropPendingSync();
		scheduleSync(delaySeconds, manual, gameMode, true);
	}

	/** The push waiting on its timer, if any, never fires. */
	private synchronized void dropPendingSync()
	{
		if (pendingKillclogSync != null)
		{
			pendingKillclogSync.cancel(false);
			pendingKillclogSync = null;
		}
	}

	synchronized void cancelSync()
	{
		syncGate.cancel();
		dropPendingSync();
		// A request already in the air keeps the single-flight slot until it
		// completes (no overlap on re-enable); its completion sees a newer
		// generation and stays silent.
	}

	/** Automatic sync turned off: an automatic push not yet sent never goes; a click or a character's own sync does. */
	void cancelAutomaticSync()
	{
		if (!characterWaiting())
		{
			syncGate.cancelAutomatic();
		}
	}

	/**
	 * The panel's sync arrow: push now, skipping any pending debounce. The
	 * single-flight gate remembers a click during an in-flight request, so
	 * icon-only feedback never makes that deliberate action disappear.
	 */
	void manualSync()
	{
		if (config.killclogSync())
		{
			syncNow(true);
		}
	}

	private void syncNow(boolean manual)
	{
		dropPendingSync();
		scheduleSync(0, manual);
	}

	void publishCharacter()
	{
		startCharacterPublish(true);
	}

	/**
	 * Shift-click on the character: removes it from killclog.com, so the profile goes back to having none. It shares
	 * the publish's one-at-a-time slot and its status line.
	 */
	void deleteCharacter()
	{
		startCharacterRequest(CharacterStatus.DELETING, profileAppearanceService::deleteCurrent, (result, error, generation) ->
		{
			if (generation != characterPublishGeneration.get())
			{
				return;
			}
			characterPublishInFlight.set(false);
			if (error != null || result == null)
			{
				failCharacterPublish(generation);
				return;
			}
			showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome), true, result.message);
		});
	}

	void cancelCharacterPublish()
	{
		int generation = characterPublishGeneration.incrementAndGet();
		characterWaitingGeneration.set(-1);
		characterPublishInFlight.set(false);
		characterPrerequisiteAttempted.set(false);
		showCharacterPublishStatus(generation, CharacterStatus.CLEAR, false);
	}

	// ── character publish ──────────────────────────────────────────────

	/** The attempt is over and failed: the slot frees and the row says so. */
	private void failCharacterPublish(int generation)
	{
		characterPublishInFlight.set(false);
		showCharacterPublishStatus(generation, CharacterStatus.FAILED, true);
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
			if (generation == characterPublishGeneration.get()
				&& (status == CharacterStatus.CLEAR || characterPublishingEnabled()))
			{
				feedback.showCharacterPublishStatus(status, autoClear, failureMessage);
			}
		});
	}

	private void retryCharacterPublish()
	{
		startCharacterPublish(false);
	}

	private void startCharacterPublish(boolean newRequest)
	{
		if (startCharacterRequest(CharacterStatus.RENDERING, profileAppearanceService::publishCurrent,
			this::handleCharacterPublishResult) && newRequest)
		{
			characterPrerequisiteAttempted.set(false);
		}
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
	 * One character request at a time: its status line at once, then on the client thread the player it is for (a
	 * logout, a hop to a League or a new request since makes it fail or stand down), then the request. False when one
	 * is already running or the character setting is off.
	 */
	private boolean startCharacterRequest(CharacterStatus progress, CharacterRequest request, CharacterOutcome outcome)
	{
		if (!characterPublishingEnabled() || !characterPublishInFlight.compareAndSet(false, true))
		{
			return false;
		}
		int generation = characterPublishGeneration.incrementAndGet();
		showCharacterPublishStatus(generation, progress, false);
		clientThread.invokeLater(() ->
		{
			if (generation != characterPublishGeneration.get())
			{
				return;
			}
			Session session = sessions.current();
			if (!characterPublishingEnabled() || !session.loggedIn() || !GameMode.MAIN.equals(mode.get()))
			{
				failCharacterPublish(generation);
				return;
			}
			request.start(session, () -> generation == characterPublishGeneration.get() && characterPublishingEnabled()
					&& sessions.isCurrent(session))
				.whenComplete((result, error) -> outcome.handle(result, error, generation));
		});
		return true;
	}

	private void handleCharacterPublishResult(ProfileAppearanceService.PublishResult result,
		Throwable error, int generation)
	{
		if (generation != characterPublishGeneration.get() || !characterPublishingEnabled())
		{
			return;
		}
		if (error != null || result == null)
		{
			failCharacterPublish(generation);
			return;
		}

		if (result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED
			&& characterPublishingEnabled()
			&& characterPrerequisiteAttempted.compareAndSet(false, true))
		{
			characterWaitingGeneration.set(generation);
			// Withdrawn while it handed over (a logout, say): it waits for nothing, and starts no sync.
			if (!characterWaiting())
			{
				return;
			}
			showCharacterPublishStatus(generation, CharacterStatus.RENDERING, false);
			syncNow(false);
			return;
		}

		characterPublishInFlight.set(false);
		showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome),
			true, result.message);
	}

	/** Whether the character request still current waits on a sync of the log. */
	private boolean characterWaiting()
	{
		return characterWaitingGeneration.get() == characterPublishGeneration.get();
	}

	private boolean failQueuedCharacterPublish()
	{
		int generation = characterPublishGeneration.get();
		if (characterWaitingGeneration.getAndSet(-1) != generation)
		{
			return false;
		}
		failCharacterPublish(generation);
		return true;
	}

	private void scheduleCharacterPublishAfterSync(int expectedGeneration)
	{
		try
		{
			executor.schedule(() ->
			{
				if (expectedGeneration != characterPublishGeneration.get()
					|| !characterPublishingEnabled())
				{
					return;
				}
				characterPublishInFlight.set(false);
				retryCharacterPublish();
			}, ProfileAppearanceService.PUBLISH_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			failCharacterPublish(expectedGeneration);
		}
	}

	// ── sync push ──────────────────────────────────────────────────────

	// If a push arrived while the slot was occupied, launch it now that the
	// slot is free (the opt-out/opt-in-mid-request case).
	private void launchQueuedSync()
	{
		KillclogSyncGate.Intent queued = syncGate.consumeQueuedIntent();
		if (queued != null && config.killclogSync())
		{
			scheduleSync(0, queued.manual, queued.mode, false);
		}
	}

	private void pushKillclogSync(boolean manual, Session session, String scheduledMode, boolean retry)
	{
		// Re-checked at fire time: the player may have opted out while the debounce was pending. The login was
		// taken when this exact timer was scheduled, so a task that escaped cancellation cannot bind itself to
		// whichever account happens to be logged in later.
		if (!sends(manual) || !sessions.isCurrent(session))
		{
			return;
		}
		final int generation = syncGate.beginAttempt(manual, scheduledMode);
		if (generation < 0)
		{
			return;
		}
		if (!sessions.isCurrent(session))
		{
			syncGate.abortAttempt();
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
				// The timer may have entered pushKillclogSync just before
				// logout, leaving this client-thread callback queued behind the
				// account switch. Never gather the next account under the old
				// attempt's generation.
				if (!syncGate.isCurrent(generation) || !sessions.isCurrent(session))
				{
					syncGate.abortAttempt();
					launchQueuedSync();
					return;
				}
				// A League sync reads only that League's store and profile, so it finishes after a hop
				// off its worlds; a main sync needs a settled main world, so it follows this one.
				String gameMode = scheduledMode == null || GameMode.MAIN.equals(scheduledMode)
					? mode.get() : scheduledMode;
				LocalClogCache cache = gameMode == null ? null : modeCache.apply(gameMode);
				if (!session.loggedIn() || gameMode == null || cache == null)
				{
					syncGate.abortAttempt();
					failQueuedCharacterPublish();
					launchQueuedSync();
					if (scheduledMode != null && gameMode == null && !manual)
					{
						// Fired before a new world settled: its login catch-up waited behind this timer.
						scheduleAutomaticSync();
					}
					return;
				}
				// The type supplier answers only on a settled main world. A League's PBs come only from
				// the RuneLite profile the server names: until RuneLite knows a new League, it files
				// that League's PBs under an older one.
				boolean main = GameMode.MAIN.equals(gameMode);
				// A League is one account status for everyone: its sync never carries a main type.
				AccountType accountType = main ? localAccountType.get() : null;
				if (manual)
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
				executor.execute(() -> dispatchKillclogSync(
					session, accountType, pbs, detailedPbs, manual, generation, cache, gameMode, retry));
			}
			catch (RuntimeException e)
			{
				log.warn("killclog sync push failed before dispatch", e);
				failBeforeSend(generation, session, manual);
			}
		});
	}

	private void dispatchKillclogSync(Session session, AccountType accountType,
		Map<String, Double> pbs, Map<String, PbRecord> detailedPbs,
		boolean manual, int generation, LocalClogCache cache, String gameMode, boolean retry)
	{
		boolean sessionEnded = !sessions.isCurrent(session);
		if (sessionEnded || !syncGate.isCurrent(generation) || !sends(manual))
		{
			// The session ended, or the player stopped this send, between gather and dispatch: release the
			// single-flight slot and walk away clean. Only a dead session fails a character publish behind it.
			syncGate.abortAttempt();
			if (sessionEnded)
			{
				failQueuedCharacterPublish();
			}
			launchQueuedSync();
			return;
		}
		try
		{
			syncService.syncCollectionLog(session, accountType, pbs, detailedPbs,
				syncGate, generation, cache, gameMode, manual || characterWaiting(), () ->
				{
					// Only a push that really goes says so: one killclog.com already holds stays quiet.
					if (!characterWaiting())
					{
						withSyncFeedback(generation, session,
							() -> feedback.showSyncProgress(manual, "syncing...", false));
					}
				})
				.whenComplete((result, err) ->
				{
					boolean current = syncGate.complete(generation);
					int characterGeneration = characterPublishGeneration.get();
					boolean characterWaiting = characterWaiting();
					if (result != null && current && config.killclogSync())
					{
						// Server-advised contention retry: another client of
						// this account held the lock. Keep a pending character
						// publication attached to that one allowed retry.
						if (result.retryAdvised && !retry)
						{
							if (characterWaiting)
							{
								showCharacterPublishStatus(characterGeneration,
									CharacterStatus.RENDERING, false);
							}
							else
							{
								withSyncFeedback(generation, session,
									() -> feedback.showSyncProgress(manual, "retrying...", false));
							}
							// Nobody is watching an automatic sync, so it waits out a
							// slow restart. A player waiting on a click or a character
							// publish gets the advised delay as it is.
							int delay = Math.max(result.retryAfterSeconds, 2);
							scheduleRetry(manual || characterWaiting ? delay : delay * 2, manual, gameMode);
							launchQueuedSync();
							return;
						}

						// Everything below is a terminal outcome for this episode.
						if (result.stopsSession)
						{
							stoppedSession = session.getNumber();
						}
						if (characterWaiting)
						{
							characterWaitingGeneration.set(-1);
							if (result.ok && !result.dryRun && characterPublishingEnabled())
							{
								scheduleCharacterPublishAfterSync(characterGeneration);
							}
							else
							{
								failCharacterPublish(characterGeneration);
							}
						}
						else if (result != SyncService.UNCHANGED)
						{
							withSyncFeedback(generation, session,
								() -> feedback.showSyncResult(manual, result.ok, result.message));
						}
						if (manual || !result.ok)
						{
							clientThread.invoke(() ->
								chatNotifier.send(ChatNotice.SYNC_RESULT, result.message));
						}
					}
					else
					{
						if (current && !failQueuedCharacterPublish() && err != null)
						{
							withSyncFeedback(generation, session, () -> feedback.showSyncResult(manual,
								false, SYNC_FAILED));
						}
					}
					launchQueuedSync();
				});
		}
		catch (RuntimeException e)
		{
			log.warn("killclog sync push failed at dispatch", e);
			failBeforeSend(generation, session, manual);
		}
	}

	/** Failures always chat; chat sends need the client thread, which invoke runs at once when already on it. */
	private void failBeforeSend(int generation, Session session, boolean manual)
	{
		syncGate.abortAttempt();
		if (!failQueuedCharacterPublish())
		{
			withSyncFeedback(generation, session, () -> feedback.showSyncResult(manual,
				false, SYNC_FAILED));
		}
		clientThread.invoke(() -> chatNotifier.send(ChatNotice.SYNC_RESULT,
			SYNC_FAILED));
		launchQueuedSync();
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
