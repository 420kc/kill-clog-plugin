package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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

	private volatile ScheduledFuture<?> pendingKillclogSync;
	private final KillclogSyncGate syncGate = new KillclogSyncGate();
	private final AtomicBoolean characterPublishInFlight = new AtomicBoolean();
	private final AtomicBoolean characterPublishAfterSync = new AtomicBoolean();
	private final AtomicBoolean characterPrerequisiteAttempted = new AtomicBoolean();
	private final AtomicInteger characterPublishGeneration = new AtomicInteger();

	PublicationCoordinator(KillClogConfig config, ConfigManager configManager, Client client,
		ClientThread clientThread, ScheduledExecutorService executor, LocalClogCache localClogCache,
		SyncService syncService, ProfileAppearanceService profileAppearanceService,
		KillClogChatNotifier chatNotifier, Feedback feedback, Supplier<AccountType> localAccountType)
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
	}

	// ── plugin-facing ──────────────────────────────────────────────────

	boolean characterPublishingEnabled()
	{
		return config.killclogSync() && config.characterModel();
	}

	/** A capture, login or settled identity: one quiet push after the debounce. */
	void scheduleAutomaticSync()
	{
		scheduleSync(SYNC_DEBOUNCE_SECONDS, false);
	}

	/**
	 * Manual web pushes (the panel sync button, an explicit opt-in) narrate in chat;
	 * automatic ones (capture debounce and login catch-up) default to silent
	 * panel feedback. Chat still follows its separate setting.
	 */
	synchronized void scheduleSync(int delaySeconds, boolean manual)
	{
		if (!config.killclogSync())
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
		pendingKillclogSync = executor.schedule(() -> pushKillclogSync(manual, scheduledEpoch),
			delaySeconds, TimeUnit.SECONDS);
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

	/**
	 * The panel's sync arrow: push now, skipping any pending debounce. The
	 * single-flight gate remembers a click during an in-flight request, so
	 * icon-only feedback never makes that deliberate action disappear.
	 */
	void manualSync()
	{
		if (!config.killclogSync())
		{
			return;
		}
		synchronized (this)
		{
			if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
			{
				pendingKillclogSync.cancel(false);
				pendingKillclogSync = null;
			}
		}
		scheduleSync(0, true);
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
		showCharacterPublishStatus(generation, KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false);
		clientThread.invokeLater(() ->
		{
			if (generation != characterPublishGeneration.get())
			{
				return;
			}
			Player local = client.getLocalPlayer();
			String rsn = local != null ? local.getName() : null;
			long accountHash = client.getAccountHash();
			if (!characterPublishingEnabled() || rsn == null || accountHash == -1)
			{
				characterPublishInFlight.set(false);
				showCharacterPublishStatus(generation, KillClogPlugin.CHARACTER_FAILED_STATUS, false, true);
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
			showCharacterPublishStatus(generation, KillClogPlugin.CHARACTER_FAILED_STATUS, false, true);
			return;
		}

		if (result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED
			&& characterPublishingEnabled()
			&& characterPrerequisiteAttempted.compareAndSet(false, true))
		{
			characterPublishAfterSync.set(true);
			showCharacterPublishStatus(generation, KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false);
			startCharacterPrerequisiteSync();
			return;
		}

		characterPublishInFlight.set(false);
		boolean published = result.outcome == ProfileAppearanceService.Outcome.PUBLISHED;
		showCharacterPublishStatus(generation, KillClogPlugin.characterPublishTerminalStatus(result.outcome),
			published, true, result.message);
	}

	private void startCharacterPrerequisiteSync()
	{
		synchronized (this)
		{
			if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
			{
				pendingKillclogSync.cancel(false);
				pendingKillclogSync = null;
			}
		}
		scheduleSync(0, false);
	}

	private boolean failQueuedCharacterPublish()
	{
		int generation = characterPublishGeneration.get();
		if (!characterPublishAfterSync.getAndSet(false))
		{
			return false;
		}
		characterPublishInFlight.set(false);
		showCharacterPublishStatus(generation, KillClogPlugin.CHARACTER_FAILED_STATUS, false, true);
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
			showCharacterPublishStatus(expectedGeneration, KillClogPlugin.CHARACTER_FAILED_STATUS, false, true);
		}
	}

	// ── sync push ──────────────────────────────────────────────────────

	// If a push arrived while the slot was occupied, launch it now that the
	// slot is free (the opt-out/opt-in-mid-request case).
	private void launchQueuedSync()
	{
		Boolean manual = syncGate.consumeQueuedIntent();
		if (manual != null && config.killclogSync())
		{
			scheduleSync(0, manual);
		}
	}

	private void pushKillclogSync(boolean manual, long scheduledEpoch)
	{
		// Re-checked at fire time: the player may have opted out while the
		// debounce was pending. The session fence was captured when this exact
		// timer was scheduled, so a task that escaped cancellation cannot bind
		// itself to whichever account happens to be logged in later.
		if (!config.killclogSync()
			|| localClogCache.currentSessionEpoch() != scheduledEpoch)
		{
			return;
		}
		final int generation = syncGate.beginAttempt(manual);
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
				if (rsn == null || accountHash == -1)
				{
					syncGate.abortAttempt();
					failQueuedCharacterPublish();
					launchQueuedSync();
					return;
				}
				AccountType accountType = localAccountType.get();
				if (manual)
				{
					chatNotifier.send(ChatNotice.SYNC_RESULT, "Syncing collection log to killclog.com...");
				}
				if (!characterPublishAfterSync.get())
				{
					withSyncFeedback(generation, scheduledEpoch,
						() -> feedback.showSyncProgress(manual, "syncing...", false));
				}
				List<String> profileKeys = PersonalBests.profileKeys(
					configManager.getRSProfiles(), accountHash);
				Map<String, Double> pbs = gatherPersonalBests(profileKeys);
				Map<String, SyncService.DetailedPb> detailedPbs =
					gatherDetailedPersonalBests(profileKeys);
				// Off the client thread before dispatch: the sync pre-flight
				// can block up to ten seconds waiting for the rename disk
				// verdict, and game ticks must never pay that wait. The
				// session fence rides along - a logout between this gather
				// and the dispatch must kill the attempt, not let a dead
				// session's sync restore its anchor or post after the end.
				long cacheEpoch = scheduledEpoch;
				executor.execute(() -> dispatchKillclogSync(
					rsn, accountHash, accountType, pbs, detailedPbs, manual, generation, cacheEpoch));
			}
			catch (RuntimeException e)
			{
				log.warn("killclog sync push failed before dispatch", e);
				syncGate.abortAttempt();
				if (!failQueuedCharacterPublish())
				{
					withSyncFeedback(generation, scheduledEpoch, () -> feedback.showSyncResult(manual,
						false, "Collection log sync failed. See the client log."));
				}
				// Failures always chat, this path included.
				chatNotifier.send(ChatNotice.SYNC_RESULT,
					"Collection log sync failed - see the client log.");
				launchQueuedSync();
			}
		});
	}

	private void dispatchKillclogSync(String rsn, long accountHash, AccountType accountType,
		Map<String, Double> pbs, Map<String, SyncService.DetailedPb> detailedPbs,
		boolean manual, int generation, long cacheEpoch)
	{
		if (localClogCache.currentSessionEpoch() != cacheEpoch)
		{
			// The session ended between gather and dispatch: release the
			// single-flight slot and walk away clean.
			syncGate.abortAttempt();
			failQueuedCharacterPublish();
			launchQueuedSync();
			return;
		}
		try
		{
			syncService.syncCollectionLog(rsn, accountHash, accountType, pbs, detailedPbs,
				cacheEpoch, syncGate, generation)
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
									KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false);
							}
							else
							{
								withSyncFeedback(generation, cacheEpoch,
									() -> feedback.showSyncProgress(manual, "retrying...", false));
							}
							scheduleSync(Math.max(result.retryAfterSeconds, 2), manual);
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
									KillClogPlugin.CHARACTER_FAILED_STATUS, false, true);
							}
						}
						else
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
			syncGate.abortAttempt();
			if (!failQueuedCharacterPublish())
			{
				withSyncFeedback(generation, cacheEpoch, () -> feedback.showSyncResult(manual,
					false, "Collection log sync failed. See the client log."));
			}
			// Failures always chat, this path included; chat sends need the
			// client thread and this body runs on the executor.
			clientThread.invoke(() -> chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Collection log sync failed - see the client log."));
			launchQueuedSync();
		}
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
	private Map<String, Double> gatherPersonalBests(List<String> profileKeys)
	{
		PersonalBests pbs = new PersonalBests(configManager);
		Map<String, Double> out = new LinkedHashMap<>();
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			putBestSeconds(out, pbs, profileKeys, boss.getName());
		}
		log.debug("killclog sync pb gather: {} owned profiles, {} pbs", profileKeys.size(), out.size());
		return out;
	}

	/**
	 * Variant-keyed personal bests for the ladder payload: team sizes stay
	 * SPLIT (solo and 5-man runs are different sports on a leaderboard),
	 * keyed by vanilla's own stored key shape. The collapsed map above stays
	 * as-is for tooltip display. Same STANDARD-only fragment sweep, merged
	 * min-wins with the adventure-log harvest; each entry keeps the lane it
	 * was observed through.
	 */
	private Map<String, SyncService.DetailedPb> gatherDetailedPersonalBests(List<String> profileKeys)
	{
		PersonalBests pbs = new PersonalBests(configManager);
		AdvLogPbs advLog = new AdvLogPbs(configManager);
		Map<String, SyncService.DetailedPb> out = new LinkedHashMap<>();
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			for (Map.Entry<String, Double> entry
				: pbs.variantSecondsAcrossProfiles(profileKeys, boss.getName()).entrySet())
			{
				mergeDetailedPb(out, entry.getKey(), entry.getValue(), "store");
			}
			for (Map.Entry<String, Double> entry
				: advLog.variantSecondsAcrossProfiles(profileKeys, boss.getName()).entrySet())
			{
				mergeDetailedPb(out, entry.getKey(), entry.getValue(), "advlog");
			}
		}
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

	private static void putBestSeconds(Map<String, Double> out, PersonalBests pbs,
		List<String> profileKeys, String bossName)
	{
		double seconds = pbs.bestSecondsAcrossProfiles(profileKeys, bossName);
		if (seconds > 0)
		{
			out.put(bossName, seconds);
		}
	}
}
