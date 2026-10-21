package com.killclog;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * The plugin's publication flow: the debounced killclog.com sync push with its one slot and the push waiting for it,
 * and the character publish that may run one sync of the log first and try again once. Event subscriptions stay in
 * the plugin; it forwards the moments that matter (captures, login, logout, opt-in, opt-out, shutdown) and hands the
 * panel's feedback in through {@link Feedback}.
 *
 * <p>One owner: every bit of the flow's state belongs to a serial executor, and every other thread (the client's, the
 * panel's, a timer's, a network reply's) only posts to it, so a check and the change it allows are never pulled apart.
 * Work that leaves the owner (the client thread's gather, the network) carries tokens back: the login it is for, the
 * era it was asked in (an opt-out, logout or shutdown moves it on at once), and its attempt's number. The two places a
 * request goes out, the sync gate's commit and the character's send, check their tokens under their own lock, so
 * nothing goes once its withdrawal has returned.
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
	static final int NONE = -1;

	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
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

	// The owner, and everything only its tasks touch.
	private final SerialExecutor owner;
	private final KillclogSyncGate<Push> syncGate = new KillclogSyncGate<>(Push::deliberate, Push::merge);
	private ScheduledFuture<?> pendingTimer;
	// The push that timer sends; a timer whose push is no longer this one was superseded.
	private Push pendingPush;
	// Whether the push waiting is an automatic one, which a capture may push back, and when its first capture came.
	private boolean pendingAutomatic;
	private long quietSince;
	// The login the server turned away (opted out, the name another account's, an update needed): its automatic
	// pushes stop, so the log stops leaving the client, until the next login or a click's attempt.
	private long stoppedSession = -1;
	/* package */ java.util.function.LongSupplier clock = System::currentTimeMillis;
	// The attempt whose line ("syncing...", "retrying...") the row shows, 0 for none: a line comes down only for its own.
	private volatile int lineAttempt;

	// The character request, one at a time. The generation only grows: a withdrawal moves it on, so work from before
	// can never act, wait or free the slot again. A request keeps its slot through its log's sync and retry. Read on
	// the Swing thread to fence status lines, so volatile; changed only by the owner.
	private volatile int characterGeneration;
	private int characterHolder = NONE;
	private int characterWaiting = NONE;
	private boolean characterPrerequisiteAttempted;
	private Session characterSession = Session.none(0);
	private int characterRequestEra;
	// The character's withdrawal fence: turning publication off, or a logout, moves the era at once, and a request's
	// final send checks it in the same step, so nothing goes once the withdrawal has returned.
	private final Object characterFence = new Object();
	private int characterEra;

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
		this.owner = new SerialExecutor();
	}

	boolean characterPublishingEnabled()
	{
		return config.killclogSync() && config.characterModel();
	}

	/**
	 * One push of the log: the login it is for, taken where it began (a press, the opt-in, a capture); the era it was
	 * asked in; its game (null follows the world); whether a player asked for it; the character request it is the sync
	 * for, by generation (NONE for none); when it was asked; the games whose one advised retry it has spent; and the
	 * attempt whose line speaks for it while it waits (0 for none).
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Push
	{
		private static final java.util.concurrent.atomic.AtomicLong ASKED = new java.util.concurrent.atomic.AtomicLong();

		final Session session;
		final int era;
		final String mode;
		final boolean manual;
		final int forCharacter;
		final long asked;
		final Set<String> retried;
		final int line;

		/** A push asked for now. */
		static Push ask(Session session, int era, String mode, boolean manual, int forCharacter)
		{
			return new Push(session, era, mode, manual, forCharacter, ASKED.incrementAndGet(), Collections.emptySet(), 0);
		}

		/** The server's one retry, for the game its attempt resolved, its line kept by that attempt. */
		Push retriedIn(String game, int attempt)
		{
			Set<String> spent = new HashSet<>(retried);
			spent.add(game);
			return new Push(session, era, game, manual, forCharacter, asked, Collections.unmodifiableSet(spent), attempt);
		}

		Push withoutCharacter()
		{
			return new Push(session, era, mode, manual, NONE, asked, retried, line);
		}

		/** Whether its one advised retry is spent for its game: it never retries again. */
		boolean retry()
		{
			return retried.contains(mode);
		}

		/** A click's or a character's: it goes past Automatic sync being off, and nothing automatic stands in for it. */
		boolean deliberate()
		{
			return manual || forCharacter != NONE;
		}

		/** The later of two to lead: a click over an automatic push, then the later asked, a retry after its own push. */
		private static boolean leads(Push one, Push other)
		{
			if (one.manual != other.manual)
			{
				return one.manual;
			}
			if (one.asked != other.asked)
			{
				return one.asked > other.asked;
			}
			return one.retry() && !other.retry();
		}

		/**
		 * Two pushes go as one, the same in whatever order and grouping they meet: pushes of two logins never mix (the
		 * newer login's stands whole; login numbers only grow); within one, the lead's game and age, a click if either
		 * was, the newer character request (generations only grow), and the retries spent by either.
		 */
		static Push merge(Push one, Push other)
		{
			if (one.session.getNumber() != other.session.getNumber())
			{
				return one.session.getNumber() > other.session.getNumber() ? one : other;
			}
			Push lead = leads(other, one) ? other : one;
			Set<String> spent = new HashSet<>(one.retried);
			spent.addAll(other.retried);
			return new Push(lead.session, Math.max(one.era, other.era), lead.mode, one.manual || other.manual,
				Math.max(one.forCharacter, other.forCharacter), lead.asked, Collections.unmodifiableSet(spent),
				Math.max(one.line, other.line));
		}
	}

	// ── entry points: any thread; each takes its tokens where it begins, then posts to the owner ──

	/** A capture, login or settled identity, on the client thread: one quiet push for this game after the debounce. */
	void scheduleAutomaticSync()
	{
		Push push = Push.ask(sessions.current(), syncGate.era(), mode.get(), false, NONE);
		owner.execute(() -> schedule(SYNC_DEBOUNCE_SECONDS, push));
	}

	/**
	 * Manual web pushes (the panel sync button, an explicit opt-in) narrate in chat;
	 * automatic ones (capture debounce and login catch-up) default to silent
	 * panel feedback. Chat still follows its separate setting.
	 */
	void pushNow(Session origin, boolean manual)
	{
		pushNow(origin, syncGate.era(), manual, NONE);
	}

	private void pushNow(Session origin, int era, boolean manual, int forCharacter)
	{
		// The game it pushes is read on the client thread; a logout before the hop drops it.
		clientThread.invoke(() ->
		{
			String game = mode.get();
			owner.execute(() ->
			{
				if (sessions.isCurrent(origin))
				{
					schedule(0, Push.ask(origin, era, game, manual, forCharacter));
				}
				else
				{
					failWaiting(forCharacter);
				}
			});
		});
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

	/** Opt-out, logout, shutdown: the era moves on at once, so nothing begun before claims, commits or speaks. */
	void cancelSync()
	{
		syncGate.cancel();
		owner.execute(this::dropPending);
		// A request already in the air keeps the slot until it completes (no overlap on re-enable); its completion
		// finds its era passed and stays silent.
	}

	/**
	 * Automatic sync turned off: an automatic push not yet sent never goes, waiting on its timer or its retry, and one
	 * in the air is silenced, its line taken down; a click or a character's own sync goes on.
	 */
	void cancelAutomaticSync()
	{
		int silenced = syncGate.silenceAutomatic();
		owner.execute(() ->
		{
			takeLineDown(silenced);
			if (pendingPush != null && !pendingPush.deliberate())
			{
				takeLineDown(pendingPush.line);
				dropPending();
			}
		});
	}

	void publishCharacter()
	{
		Session pressed = sessions.current();
		int era = characterEra();
		owner.execute(() -> startCharacterRequest(pressed, era, NONE, CharacterStatus.RENDERING,
			profileAppearanceService::publishCurrent, this::handleCharacterPublishResult));
	}

	/**
	 * Shift-click on the character: removes it from killclog.com, so the profile goes back to having none. It shares
	 * the publish's one-at-a-time slot and its status line.
	 */
	void deleteCharacter()
	{
		Session pressed = sessions.current();
		int era = characterEra();
		owner.execute(() -> startCharacterRequest(pressed, era, NONE, CharacterStatus.DELETING,
			profileAppearanceService::deleteCurrent, (result, error, generation) ->
			{
				if (error != null || result == null)
				{
					failCharacterPublish(generation);
				}
				else if (finishCharacter(generation))
				{
					showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome), true, result.message);
				}
			}));
	}

	/** Publication turned off, or a logout: no character request goes out from the moment this returns. */
	void cancelCharacterPublish()
	{
		synchronized (characterFence)
		{
			characterEra++;
		}
		owner.execute(() ->
		{
			int generation = ++characterGeneration;
			characterHolder = NONE;
			characterWaiting = NONE;
			characterPrerequisiteAttempted = false;
			showCharacterPublishStatus(generation, CharacterStatus.CLEAR, false);
		});
	}

	private int characterEra()
	{
		synchronized (characterFence)
		{
			return characterEra;
		}
	}

	// ── sync push: the owner's ─────────────────────────────────────────

	/**
	 * Whether a push goes now: sync is on, its era and login last; a click always; a character's own sync while that
	 * request still waits on it; an automatic one with Automatic sync on and its login not turned away.
	 */
	private boolean sends(Push push)
	{
		if (!config.killclogSync() || push.era != syncGate.era() || !sessions.isCurrent(push.session))
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

	/** The settings still want it sent: sync on, and an automatic push with Automatic sync on. Any thread. */
	private boolean stillWanted(Push push)
	{
		return config.killclogSync() && (push.deliberate() || config.automaticSync());
	}

	/** A push whose character request was withdrawn is just a push again, so it goes, or not, on its own terms. */
	private Push live(Push push)
	{
		return push.forCharacter == NONE || waitingFor(push.forCharacter) ? push : push.withoutCharacter();
	}

	/**
	 * A push after its delay. A push that can't go fails the character request it was for, so none waits forever. An
	 * automatic push while another automatic one waits starts its quiet time again, up to the minute's cap; one behind
	 * a click, a character's sync or a retry rides it. A click or a character's sync takes over what waits, merged.
	 */
	private void schedule(int delaySeconds, Push asked)
	{
		Push push = live(asked);
		if (!sends(push))
		{
			abandon(push, 0);
			return;
		}
		long now = clock.getAsLong();
		long delayMs = delaySeconds * 1000L;
		if (pendingPush != null)
		{
			if (!push.deliberate())
			{
				long left = quietSince + SYNC_MAX_WAIT_MS - now;
				if (pendingAutomatic && !push.retry() && left > 0)
				{
					startTimer(Math.min(delayMs, left), push);
				}
				return;
			}
			push = Push.merge(pendingPush, push);
		}
		quietSince = now;
		startTimer(delayMs, push);
	}

	private void startTimer(long delayMs, Push push)
	{
		if (pendingTimer != null)
		{
			pendingTimer.cancel(false);
		}
		pendingPush = push;
		pendingAutomatic = !push.deliberate() && !push.retry();
		try
		{
			pendingTimer = executor.schedule(() -> owner.execute(() -> fire(push)), delayMs, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			pendingPush = null;
			pendingTimer = null;
			abandon(push, 0);
		}
	}

	/** The push waiting on its timer, if any, never fires. */
	private void dropPending()
	{
		if (pendingTimer != null)
		{
			pendingTimer.cancel(false);
		}
		pendingTimer = null;
		pendingPush = null;
	}

	/** A timer fired: its push goes, unless another has taken its place since. */
	private void fire(Push push)
	{
		if (push != pendingPush)
		{
			return;
		}
		pendingPush = null;
		pendingTimer = null;
		send(push);
	}

	private void launchQueued()
	{
		Push queued = syncGate.takeQueued();
		if (queued != null)
		{
			schedule(0, queued);
		}
	}

	private void send(Push asked)
	{
		Push push = live(asked);
		if (!sends(push))
		{
			abandon(push, 0);
			return;
		}
		int attempt = syncGate.begin(push, push.era);
		if (attempt == KillclogSyncGate.BUSY)
		{
			return;
		}
		if (attempt == KillclogSyncGate.STALE)
		{
			abandon(push, 0);
			return;
		}
		if (push.manual)
		{
			// A click's attempt begins: its login's automatic pushes go again.
			stoppedSession = -1;
		}
		Session session = push.session;
		clientThread.invoke(() ->
		{
			// Any throw before the request takes ownership must release the slot, or sync is silently dead until
			// restart: the client thread swallows the exception and the player sees nothing.
			try
			{
				gather(push, attempt, session);
			}
			catch (RuntimeException e)
			{
				log.warn("killclog sync push failed before dispatch", e);
				owner.execute(() -> failBeforeSend(push, attempt));
			}
		});
	}

	/** On the client thread: the game, its store, the account type and the PBs the attempt carries. */
	private void gather(Push push, int attempt, Session session)
	{
		// Queued behind a logout, an opt-out or Automatic sync going off, it never gathers the next account.
		if (!syncGate.live(attempt) || !sessions.isCurrent(session) || !stillWanted(push))
		{
			owner.execute(() -> stopped(push, attempt, false));
			return;
		}
		// A League sync reads only that League's store and profile, so it finishes after a hop off its worlds; a
		// main sync needs a settled main world, so it follows this one. A retry is for the game its attempt resolved:
		// a main one off the main game is let go, since the main account type answers only on a settled main world.
		String world = mode.get();
		String gameMode = push.retry() ? (GameMode.MAIN.equals(push.mode) && !GameMode.MAIN.equals(world) ? null
			: push.mode) : push.mode == null || GameMode.MAIN.equals(push.mode) ? world : push.mode;
		LocalClogCache cache = gameMode == null ? null : modeCache.apply(gameMode);
		if (!session.loggedIn() || gameMode == null || cache == null)
		{
			// Fired before a new world settled: its login catch-up waited behind this timer.
			boolean again = push.mode != null && gameMode == null && !push.manual && !push.retry();
			owner.execute(() -> stopped(push, attempt, again));
			return;
		}
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
		// Off the client thread before dispatch: the sync pre-flight can block up to ten seconds waiting for the
		// rename disk verdict, and game ticks must never pay that wait.
		executor.execute(() -> dispatch(push, attempt, accountType, pbs, detailedPbs, cache, gameMode));
	}

	/** The attempt never went: its slot frees, it ends what it began, and the push waiting goes. */
	private void stopped(Push push, int attempt, boolean scheduleCatchUp)
	{
		syncGate.release(attempt);
		abandon(push, attempt);
		launchQueued();
		if (scheduleCatchUp)
		{
			// The catch-up follows the world, so a world still unknown when it fires gets no more.
			schedule(SYNC_DEBOUNCE_SECONDS, Push.ask(push.session, push.era, null, false, NONE));
		}
	}

	/** Off the owner (the pre-flight can block): the request, its reply posted back to the owner. */
	private void dispatch(Push push, int attempt, AccountType accountType, Map<String, Double> pbs,
		Map<String, PbRecord> detailedPbs, LocalClogCache cache, String gameMode)
	{
		Session session = push.session;
		if (!syncGate.live(attempt) || !sessions.isCurrent(session) || !stillWanted(push))
		{
			owner.execute(() -> stopped(push, attempt, false));
			return;
		}
		try
		{
			syncService.syncCollectionLog(session, accountType, pbs, detailedPbs, syncGate, attempt, cache, gameMode,
				push.deliberate(), () -> owner.execute(() -> sending(push, attempt)))
				.whenComplete((result, error) -> owner.execute(() -> completed(push, attempt, gameMode, result, error)));
		}
		catch (RuntimeException e)
		{
			log.warn("killclog sync push failed at dispatch", e);
			owner.execute(() -> failBeforeSend(push, attempt));
		}
	}

	/** The push really goes: only then does the row say so (one killclog.com already holds stays quiet). */
	private void sending(Push push, int attempt)
	{
		if (push.forCharacter == NONE && syncGate.live(attempt))
		{
			lineAttempt = attempt;
			say(push, () -> feedback.showSyncProgress(push.manual, "syncing...", false));
		}
	}

	private void completed(Push push, int attempt, String gameMode, SyncService.SyncResult result, Throwable error)
	{
		boolean live = syncGate.release(attempt);
		boolean character = push.forCharacter != NONE;
		if (live && result != null && config.killclogSync())
		{
			// Server-advised contention retry: another client of this account held the lock. A character request
			// rides that one allowed retry.
			if (result.retryAdvised && !push.retry())
			{
				retry(push, attempt, gameMode, result.retryAfterSeconds);
				launchQueued();
				return;
			}
			if (result.stopsSession)
			{
				stoppedSession = push.session.getNumber();
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
				lineAttempt = 0;
				say(push, () -> feedback.showSyncResult(push.manual, result.ok, result.message));
			}
			if (push.manual || !result.ok)
			{
				chatFor(push.session, result.message);
			}
		}
		else if (live)
		{
			if (character)
			{
				failWaiting(push.forCharacter);
			}
			else if (error != null)
			{
				lineAttempt = 0;
				say(push, () -> feedback.showSyncResult(push.manual, false, SYNC_FAILED));
			}
		}
		else
		{
			// Silenced or from a passed era: what it began ends quietly.
			abandon(push, attempt);
		}
		launchQueued();
	}

	/**
	 * The server's one advised retry, after its own delay: a capture's shorter debounce must not stand in for it.
	 * Nobody is watching an automatic sync, so it waits out a slow restart; a player waiting on a click or a
	 * character publish gets the advised delay as it is.
	 */
	private void retry(Push push, int attempt, String gameMode, int retryAfterSeconds)
	{
		Push retry = live(push.retriedIn(gameMode, attempt));
		if (!sends(retry))
		{
			abandon(push, attempt);
			return;
		}
		int delay = Math.max(retryAfterSeconds, 2);
		// A click or a character's sync asked meanwhile rides this retry; an automatic push waiting gives way to it.
		Push next = pendingPush != null && pendingPush.deliberate() ? Push.merge(retry, pendingPush) : retry;
		dropPending();
		schedule(retry.deliberate() ? delay : delay * 2, next);
		if (retry.forCharacter != NONE)
		{
			showCharacterPublishStatus(retry.forCharacter, CharacterStatus.RENDERING, false);
		}
		else if (pendingPush == next)
		{
			lineAttempt = attempt;
			say(push, () -> feedback.showSyncProgress(push.manual, "retrying...", false));
		}
	}

	/** Failures always chat; chat sends need the client thread, which invoke runs at once when already on it. */
	private void failBeforeSend(Push push, int attempt)
	{
		boolean live = syncGate.release(attempt);
		if (push.forCharacter != NONE)
		{
			failWaiting(push.forCharacter);
		}
		else if (live)
		{
			lineAttempt = 0;
			say(push, () -> feedback.showSyncResult(push.manual, false, SYNC_FAILED));
		}
		if (live)
		{
			chatFor(push.session, SYNC_FAILED);
		}
		launchQueued();
	}

	/**
	 * A push that will not go after all, perhaps after saying "syncing..." or "retrying...": the character request it
	 * was for fails, or its line comes down, if the row still shows it ({@code attempt}'s, or the one it waits under).
	 */
	private void abandon(Push push, int attempt)
	{
		if (push.forCharacter != NONE)
		{
			failWaiting(push.forCharacter);
			return;
		}
		takeLineDown(attempt);
		takeLineDown(push.line);
	}

	/** The row's sync line comes down, only if it is this attempt's (never a newer sync's, never the next login's). */
	private void takeLineDown(int attempt)
	{
		if (attempt == 0 || lineAttempt != attempt)
		{
			return;
		}
		lineAttempt = 0;
		SwingUtilities.invokeLater(() ->
		{
			if (lineAttempt == 0)
			{
				feedback.resetSyncFeedback();
			}
		});
	}

	/** A line for the row, said only while its era and login last and sync is on, and the row still answers to it. */
	private void say(Push push, Runnable line)
	{
		int shown = lineAttempt;
		SwingUtilities.invokeLater(() ->
		{
			if (lineAttempt == shown && push.era == syncGate.era() && config.killclogSync()
				&& sessions.isCurrent(push.session))
			{
				line.run();
			}
		});
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

	// ── character publish: the owner's ─────────────────────────────────

	/** A character request: publish or delete, for the logged-in player of the main game. */
	@FunctionalInterface
	private interface CharacterRequest
	{
		CompletableFuture<ProfileAppearanceService.PublishResult> start(Session session,
			ProfileAppearanceService.Authorization authorized);
	}

	@FunctionalInterface
	private interface CharacterOutcome
	{
		void handle(ProfileAppearanceService.PublishResult result, Throwable error, int generation);
	}

	/**
	 * One character request at a time, for the login it was pressed in ({@code origin}) and the withdrawal era of the
	 * press: its status line at once, then on the client thread the request, unless that login has ended or left the
	 * main game (it fails, or a withdrawn one stands down). A request that {@code continues} an earlier one (its
	 * generation) takes its place only while that one is still current and holds the slot, keeping its login and era.
	 */
	private void startCharacterRequest(Session origin, int era, int continues, CharacterStatus progress,
		CharacterRequest request, CharacterOutcome outcome)
	{
		if (!characterPublishingEnabled())
		{
			return;
		}
		if (continues == NONE ? characterHolder != NONE || era != characterEra() || !sessions.isCurrent(origin)
			: continues != characterGeneration || characterHolder != continues)
		{
			return;
		}
		if (continues == NONE)
		{
			characterSession = origin;
			characterRequestEra = era;
			characterPrerequisiteAttempted = false;
		}
		int generation = ++characterGeneration;
		characterHolder = generation;
		Session session = characterSession;
		int requestEra = characterRequestEra;
		showCharacterPublishStatus(generation, progress, false);
		clientThread.invokeLater(() ->
		{
			if (generation != characterGeneration)
			{
				return;
			}
			if (!characterPublishingEnabled() || !session.loggedIn() || !sessions.isCurrent(session)
				|| !GameMode.MAIN.equals(mode.get()))
			{
				owner.execute(() -> failCharacterPublish(generation));
				return;
			}
			request.start(session, new ProfileAppearanceService.Authorization()
			{
				@Override
				public boolean getAsBoolean()
				{
					return generation == characterGeneration && characterPublishingEnabled() && sessions.isCurrent(session);
				}

				@Override
				public boolean sendIf(Runnable send)
				{
					// One step with a withdrawal (cancelCharacterPublish moves the era under this lock).
					synchronized (characterFence)
					{
						if (requestEra != characterEra || !sessions.isCurrent(session))
						{
							return false;
						}
						send.run();
						return true;
					}
				}
			}).whenComplete((result, error) -> owner.execute(() -> outcome.handle(result, error, generation)));
		});
	}

	private void handleCharacterPublishResult(ProfileAppearanceService.PublishResult result,
		Throwable error, int generation)
	{
		if (generation != characterGeneration || !characterPublishingEnabled())
		{
			return;
		}
		if (error != null || result == null)
		{
			failCharacterPublish(generation);
			return;
		}
		if (result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED && characterHolder == generation
			&& !characterPrerequisiteAttempted)
		{
			// It waits on one sync of its log first, for its own login and era.
			characterPrerequisiteAttempted = true;
			characterWaiting = generation;
			showCharacterPublishStatus(generation, CharacterStatus.RENDERING, false);
			pushNow(characterSession, syncGate.era(), false, generation);
		}
		else if (finishCharacter(generation))
		{
			showCharacterPublishStatus(generation, CharacterStatus.terminal(result.outcome), true, result.message);
		}
	}

	/** The attempt is over and failed: the slot frees and the row says so, only while the attempt is still current. */
	private void failCharacterPublish(int generation)
	{
		if (finishCharacter(generation))
		{
			showCharacterPublishStatus(generation, CharacterStatus.FAILED, true);
		}
	}

	/** The request still current, holding the slot, ends and frees it; false for anything withdrawn or stale. */
	private boolean finishCharacter(int generation)
	{
		if (generation != characterGeneration || characterHolder != generation)
		{
			return false;
		}
		characterHolder = NONE;
		characterWaiting = NONE;
		return true;
	}

	/** Whether this request is still current and waits on its sync. */
	private boolean waitingFor(int generation)
	{
		return generation != NONE && characterWaiting == generation && generation == characterGeneration;
	}

	/** Its sync ended: the request stops waiting; false when it was no longer the one waiting. */
	private boolean takeWaiting(int generation)
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

	/** The publish again once its log's sync landed: the same request, keeping its slot, login and era. */
	private void scheduleCharacterPublishAfterSync(int generation)
	{
		try
		{
			executor.schedule(() -> owner.execute(() -> startCharacterRequest(null, NONE, generation,
					CharacterStatus.RENDERING, profileAppearanceService::publishCurrent, this::handleCharacterPublishResult)),
				ProfileAppearanceService.PUBLISH_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			failCharacterPublish(generation);
		}
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
			if (generation == characterGeneration && (status == CharacterStatus.CLEAR || characterPublishingEnabled()))
			{
				feedback.showCharacterPublishStatus(status, autoClear, failureMessage);
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
