package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
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
	static final String CHARACTER_RENDERING_STATUS = "updating character...";
	static final String CHARACTER_PUBLISHED_STATUS = "character updated!";
	static final String CHARACTER_FAILED_STATUS = "Character failed";
	static final String CHARACTER_APPEARANCE_STATUS = "Change equipment, then retry";
	static final String CHARACTER_PENDING_STATUS = "Still rendering...";
	static final String CHARACTER_RECOVERY_STATUS = "Publishing on hold";
	static final String CHARACTER_DISABLED_STATUS = "Publishing unavailable";
	static final String CHARACTER_UNKNOWN_STATUS = "Check your profile";
	static final String CHARACTER_BUSY_STATUS = "Finishing previous request...";

	static String characterPublishTerminalStatus(ProfileAppearanceService.Outcome outcome)
	{
		switch (outcome)
		{
			case PUBLISHED: return CHARACTER_PUBLISHED_STATUS;
			case RENDERING: return CHARACTER_PENDING_STATUS;
			case RECOVERY_PENDING: return CHARACTER_RECOVERY_STATUS;
			case DISABLED: return CHARACTER_DISABLED_STATUS;
			case BUSY: return CHARACTER_BUSY_STATUS;
			case UNKNOWN: return CHARACTER_UNKNOWN_STATUS;
			case APPEARANCE_PENDING: return CHARACTER_APPEARANCE_STATUS;
			case CANCELLED: return " ";
			default: return CHARACTER_FAILED_STATUS;
		}
	}

	/** The panel's side of the flow. Every call arrives on the EDT. */
	interface Feedback
	{
		void showSyncProgress(boolean manual, String text, boolean autoClear);

		void showSyncResult(boolean manual, boolean ok, String message);

		void showCharacterPublishStatus(String text, boolean ok, boolean autoClear, String failureMessage);
	}

	// Debounce window: a bulk capture completes many category writes in a
	// burst; one push carries them all.
	static final int SYNC_DEBOUNCE_SECONDS = 10;

	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final LocalClogCache localClogCache;
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
	private final KillclogSyncGate syncGate = new KillclogSyncGate();
	private final AtomicBoolean characterPublishInFlight = new AtomicBoolean();
	private final AtomicBoolean characterPublishAfterSync = new AtomicBoolean();
	private final AtomicBoolean characterPrerequisiteAttempted = new AtomicBoolean();
	private final AtomicInteger characterPublishGeneration = new AtomicInteger();

	PublicationCoordinator(KillClogConfig config, ConfigManager configManager, Client client,
		ClientThread clientThread, ScheduledExecutorService executor, LocalClogCache localClogCache,
		SyncService syncService, ProfileAppearanceService profileAppearanceService,
		KillClogChatNotifier chatNotifier, Feedback feedback, Supplier<AccountType> localAccountType,
		Supplier<String> mode, Function<String, LocalClogCache> modeCache, Function<String, String> leagueProfileType)
	{
		this.config = config;
		this.configManager = configManager;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.localClogCache = localClogCache;
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
		scheduleSync(SYNC_DEBOUNCE_SECONDS, false, mode.get());
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
		long pressed = localClogCache.currentSessionEpoch();
		clientThread.invoke(() ->
		{
			if (localClogCache.currentSessionEpoch() == pressed)
			{
				scheduleSync(delaySeconds, manual, mode.get());
			}
		});
	}

	/** Sync is on, and an automatic send has Automatic sync too: a click and a character's own sync always go. */
	private boolean sends(boolean manual)
	{
		return config.killclogSync() && (manual || config.automaticSync() || characterPublishAfterSync.get());
	}

	/** A retry keeps the game its attempt was for, wherever the player has hopped since. */
	private synchronized void scheduleSync(int delaySeconds, boolean manual, String scheduledMode)
	{
		if (!sends(manual))
		{
			return;
		}
		if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
		{
			if (!manual)
			{
				return;
			}
			pendingKillclogSync.cancel(false);
		}
		long scheduledEpoch = localClogCache.currentSessionEpoch();
		pendingKillclogSync = executor.schedule(() -> pushKillclogSync(manual, scheduledEpoch, scheduledMode),
			delaySeconds, TimeUnit.SECONDS);
	}

	/** The retry keeps its own delay: a capture's shorter debounce must not stand in for it. */
	private synchronized void scheduleRetry(int delaySeconds, boolean manual, String gameMode)
	{
		if (pendingKillclogSync != null)
		{
			pendingKillclogSync.cancel(false);
		}
		pendingKillclogSync = null;
		scheduleSync(delaySeconds, manual, gameMode);
	}

	synchronized void cancelSync()
	{
		syncGate.cancel();
		if (pendingKillclogSync != null)
		{
			pendingKillclogSync.cancel(false);
			pendingKillclogSync = null;
		}
		// A request already in the air keeps the single-flight slot until it
		// completes (no overlap on re-enable); its completion sees a newer
		// generation and stays silent.
	}

	/** Automatic sync turned off: an automatic push not yet sent never goes; a click or a character's own sync does. */
	void cancelAutomaticSync()
	{
		if (!characterPublishAfterSync.get())
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
		synchronized (this)
		{
			if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
			{
				pendingKillclogSync.cancel(false);
				pendingKillclogSync = null;
			}
		}
		scheduleSync(0, manual);
	}

	void publishCharacter()
	{
		startCharacterPublish(true);
	}

	void cancelCharacterPublish()
	{
		int generation = characterPublishGeneration.incrementAndGet();
		characterPublishAfterSync.set(false);
		characterPublishInFlight.set(false);
		characterPrerequisiteAttempted.set(false);
		showCharacterPublishStatus(generation, " ", false, false);
	}

	// ── character publish ──────────────────────────────────────────────

	private void showCharacterPublishStatus(int generation, String text, boolean ok, boolean autoClear)
	{
		showCharacterPublishStatus(generation, text, ok, autoClear, null);
	}

	private void showCharacterPublishStatus(int generation, String text, boolean ok, boolean autoClear,
		String failureMessage)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (generation == characterPublishGeneration.get()
				&& (text.trim().isEmpty() || characterPublishingEnabled()))
			{
				feedback.showCharacterPublishStatus(text, ok, autoClear, failureMessage);
			}
		});
	}

	private void retryCharacterPublish()
	{
		startCharacterPublish(false);
	}

	private void startCharacterPublish(boolean newRequest)
	{
		if (!characterPublishingEnabled()
			|| !characterPublishInFlight.compareAndSet(false, true))
		{
			return;
		}

		if (newRequest)
		{
			characterPrerequisiteAttempted.set(false);
		}
		int generation = characterPublishGeneration.incrementAndGet();
		showCharacterPublishStatus(generation, CHARACTER_RENDERING_STATUS, false, false);
		clientThread.invokeLater(() ->
		{
			if (generation != characterPublishGeneration.get())
			{
				return;
			}
			Player local = client.getLocalPlayer();
			String rsn = local != null ? local.getName() : null;
			long accountHash = client.getAccountHash();
			if (!characterPublishingEnabled() || rsn == null || accountHash == -1 || !GameMode.MAIN.equals(mode.get()))
			{
				characterPublishInFlight.set(false);
				showCharacterPublishStatus(generation, CHARACTER_FAILED_STATUS, false, true);
				return;
			}

			profileAppearanceService.publishCurrent(rsn, accountHash,
				() -> generation == characterPublishGeneration.get() && characterPublishingEnabled())
				.whenComplete((result, error) ->
					handleCharacterPublishResult(result, error, generation));
		});
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
			characterPublishInFlight.set(false);
			showCharacterPublishStatus(generation, CHARACTER_FAILED_STATUS, false, true);
			return;
		}

		if (result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED
			&& characterPublishingEnabled()
			&& characterPrerequisiteAttempted.compareAndSet(false, true))
		{
			characterPublishAfterSync.set(true);
			showCharacterPublishStatus(generation, CHARACTER_RENDERING_STATUS, false, false);
			syncNow(false);
			return;
		}

		characterPublishInFlight.set(false);
		boolean published = result.outcome == ProfileAppearanceService.Outcome.PUBLISHED;
		showCharacterPublishStatus(generation, characterPublishTerminalStatus(result.outcome),
			published, true, result.message);
	}

	private boolean failQueuedCharacterPublish()
	{
		int generation = characterPublishGeneration.get();
		if (!characterPublishAfterSync.getAndSet(false))
		{
			return false;
		}
		characterPublishInFlight.set(false);
		showCharacterPublishStatus(generation, CHARACTER_FAILED_STATUS, false, true);
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
			characterPublishInFlight.set(false);
			showCharacterPublishStatus(expectedGeneration, CHARACTER_FAILED_STATUS, false, true);
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
			scheduleSync(0, queued.manual, queued.mode);
		}
	}

	private void pushKillclogSync(boolean manual, long scheduledEpoch, String scheduledMode)
	{
		// Re-checked at fire time: the player may have opted out while the
		// debounce was pending. The session fence was captured when this exact
		// timer was scheduled, so a task that escaped cancellation cannot bind
		// itself to whichever account happens to be logged in later.
		if (!sends(manual)
			|| localClogCache.currentSessionEpoch() != scheduledEpoch)
		{
			// A push dropped before it claims the slot ends its episode; one still in flight keeps its own retry.
			syncGate.restoreRetryCreditIfIdle();
			return;
		}
		final int generation = syncGate.beginAttempt(manual, scheduledMode);
		if (generation < 0)
		{
			return;
		}
		if (localClogCache.currentSessionEpoch() != scheduledEpoch)
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
				if (!syncGate.isCurrent(generation)
					|| localClogCache.currentSessionEpoch() != scheduledEpoch)
				{
					syncGate.abortAttempt();
					launchQueuedSync();
					return;
				}
				Player local = client.getLocalPlayer();
				String rsn = local != null ? local.getName() : null;
				long accountHash = client.getAccountHash();
				// A League sync reads only that League's store and profile, so it finishes after a hop
				// off its worlds; a main sync needs a settled main world, so it follows this one.
				String gameMode = scheduledMode == null || GameMode.MAIN.equals(scheduledMode)
					? mode.get() : scheduledMode;
				LocalClogCache cache = gameMode == null ? null : modeCache.apply(gameMode);
				if (rsn == null || accountHash == -1 || gameMode == null || cache == null)
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
				List<String> profileKeys = PersonalBests.profileKeys(configManager.getRSProfiles(), accountHash,
					main ? RuneScapeProfileType.STANDARD.name() : leagueProfileType.apply(gameMode));
				Map<String, Double> pbs = new LinkedHashMap<>();
				Map<String, SyncService.DetailedPb> detailedPbs = gatherPersonalBests(profileKeys, pbs);
				// Off the client thread before dispatch: the sync pre-flight
				// can block up to ten seconds waiting for the rename disk
				// verdict, and game ticks must never pay that wait. The
				// session fence rides along - a logout between this gather
				// and the dispatch must kill the attempt, not let a dead
				// session's sync restore its anchor or post after the end.
				long cacheEpoch = scheduledEpoch;
				long storeEpoch = cache.currentSessionEpoch();
				executor.execute(() -> dispatchKillclogSync(
					rsn, accountHash, accountType, pbs, detailedPbs, manual, generation, cacheEpoch,
					cache, gameMode, storeEpoch));
			}
			catch (RuntimeException e)
			{
				log.warn("killclog sync push failed before dispatch", e);
				failBeforeSend(generation, scheduledEpoch, manual);
			}
		});
	}

	private void dispatchKillclogSync(String rsn, long accountHash, AccountType accountType,
		Map<String, Double> pbs, Map<String, SyncService.DetailedPb> detailedPbs,
		boolean manual, int generation, long cacheEpoch, LocalClogCache cache, String gameMode, long storeEpoch)
	{
		boolean sessionEnded = localClogCache.currentSessionEpoch() != cacheEpoch;
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
			syncService.syncCollectionLog(rsn, accountHash, accountType, pbs, detailedPbs,
				storeEpoch, syncGate, generation, cache, gameMode, manual || characterPublishAfterSync.get(), () ->
				{
					// Only a push that really goes says so: one killclog.com already holds stays quiet.
					if (!characterPublishAfterSync.get())
					{
						withSyncFeedback(generation, cacheEpoch,
							() -> feedback.showSyncProgress(manual, "syncing...", false));
					}
				})
				.whenComplete((result, err) ->
				{
					boolean current = syncGate.complete(generation);
					int characterGeneration = characterPublishGeneration.get();
					boolean characterWaiting = characterPublishAfterSync.get();
					if (result != null && current && config.killclogSync())
					{
						// Server-advised contention retry: another client of
						// this account held the lock. Keep a pending character
						// publication attached to that one allowed retry.
						if (result.retryAdvised && syncGate.consumeRetryCredit())
						{
							if (characterWaiting)
							{
								showCharacterPublishStatus(characterGeneration,
									CHARACTER_RENDERING_STATUS, false, false);
							}
							else
							{
								withSyncFeedback(generation, cacheEpoch,
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
						syncGate.restoreRetryCredit();
						if (characterWaiting)
						{
							characterPublishAfterSync.set(false);
							if (result.ok && !result.dryRun && characterPublishingEnabled())
							{
								scheduleCharacterPublishAfterSync(characterGeneration);
							}
							else
							{
								characterPublishInFlight.set(false);
								showCharacterPublishStatus(characterGeneration,
									CHARACTER_FAILED_STATUS, false, true);
							}
						}
						else if (result != SyncService.UNCHANGED)
						{
							withSyncFeedback(generation, cacheEpoch,
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
						if (current)
						{
							syncGate.restoreRetryCredit();
						}
						if (current && !failQueuedCharacterPublish() && err != null)
						{
							withSyncFeedback(generation, cacheEpoch, () -> feedback.showSyncResult(manual,
								false, "Collection log sync failed. See the client log."));
						}
					}
					launchQueuedSync();
				});
		}
		catch (RuntimeException e)
		{
			log.warn("killclog sync push failed at dispatch", e);
			failBeforeSend(generation, cacheEpoch, manual);
		}
	}

	/** Failures always chat; chat sends need the client thread, which invoke runs at once when already on it. */
	private void failBeforeSend(int generation, long epoch, boolean manual)
	{
		syncGate.abortAttempt();
		if (!failQueuedCharacterPublish())
		{
			withSyncFeedback(generation, epoch, () -> feedback.showSyncResult(manual,
				false, "Collection log sync failed. See the client log."));
		}
		clientThread.invoke(() -> chatNotifier.send(ChatNotice.SYNC_RESULT,
			"Collection log sync failed. See the client log."));
		launchQueuedSync();
	}

	private void withSyncFeedback(int generation, long epoch, Runnable feedbackCall)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (syncGate.isCurrent(generation) && config.killclogSync()
				&& localClogCache.currentSessionEpoch() == epoch)
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
	 * over time, so the gather sweeps every fragment owned by the captured
	 * account hash and keeps the fastest time per boss. STANDARD-world fragments
	 * only: Leagues and speedrun profiles share the display name but store
	 * buffed-world times, and the min-merge would launder those into the
	 * player's real record. Client thread (config reads).
	 */
	private Map<String, SyncService.DetailedPb> gatherPersonalBests(List<String> profileKeys, Map<String, Double> pbs)
	{
		PersonalBests store = new PersonalBests(configManager);
		Map<String, SyncService.DetailedPb> out = new LinkedHashMap<>();
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			Map<String, Double> vanilla = store.variantSecondsAcrossProfiles("personalbest", "", profileKeys, boss.getName());
			vanilla.values().stream().min(Double::compare).ifPresent(seconds -> pbs.put(boss.getName(), seconds));
			vanilla.forEach((key, seconds) -> mergeDetailedPb(out, key, seconds, "store"));
			store.variantSecondsAcrossProfiles(AdvLogPbs.CONFIG_GROUP, AdvLogPbs.KEY_PREFIX, profileKeys, boss.getName())
				.forEach((key, seconds) -> mergeDetailedPb(out, key, seconds, "advlog"));
		}
		log.debug("killclog sync pb gather: {} owned profiles, {} pbs", profileKeys.size(), pbs.size());
		return out;
	}

	/** Faster wins; on a tie the earlier lane keeps the tag. */
	private static void mergeDetailedPb(Map<String, SyncService.DetailedPb> out,
		String key, double seconds, String source)
	{
		SyncService.DetailedPb existing = out.get(key);
		if (existing == null || seconds < existing.seconds)
		{
			out.put(key, new SyncService.DetailedPb(seconds, source));
		}
	}
}
