package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kill Clog",
	description = "HiScores and Collection Log Overhaul",
	tags = {"boss", "kc", "kill count", "collection log", "clog", "hiscores", "pvm", "pb",
		"personal best", "ironman", "comparison", "sync", "templeosrs", "runeprofile",
		"combat achievements"}
)
public class KillClogPlugin extends Plugin
{
	static final int CLOG_INTERFACE = 621;
	static final String CHARACTER_RENDERING_STATUS = "rendering...";
	static final String CHARACTER_PUBLISHED_STATUS = "character published!";
	static final String CHARACTER_FAILED_STATUS = "Publish failed";

	/** Config keys whose changes require rebuilding the right-click lookup menu entry. */
	private static final java.util.Set<String> MENU_CONFIG_KEYS = java.util.Set.of(
		"playerMenuLookup", "menuLabel", "menuOnPlayers", "menuOnFriendsList",
		"menuOnIgnoreList", "menuOnClanList", "menuOnGuestClanList",
		"menuOnChatChannels", "menuOnChat", "menuOnPrivateMessages", "menuOnGroupIronman");

	@Inject
	private Client client;

	@Inject
	private KillClogConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KillClogPanel panel;

	@Inject
	private Provider<MenuManager> menuManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private NameAutocompleter nameAutocompleter;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ClogButtonOverlay clogButtonOverlay;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClogService clogService;

	@Inject
	private RuneProfileService runeProfileService;

	@Inject
	private KillclogService killclogService;

	@Inject
	private HiscoreService hiscoreService;

	@Inject
	private LocalClogCache localClogCache;

	@Inject
	private SyncService syncService;

	@Inject
	private ProfileAppearanceService profileAppearanceService;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private LocalCaCache localCaCache;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private KillClogChatCommand kclogCommand;

	@Inject
	private KillClogChatEmoji chatEmoji;

	@Inject
	private KillClogChatNotifier chatNotifier;

	@Inject
	private ConfigManager configManager;

	private NavigationButton navButton;
	private String lastLocalName;

	// Adventure-log pb harvest state, vanilla's two-stage shape: the menu
	// load names the owner, the Counters scroll load triggers the parse.
	private boolean advLogTitleLoaded;
	private boolean advLogCountersLoaded;
	private String advLogOwner;
	// One rename-continuity check per login, latched when name AND account
	// hash are both available (they arrive on different ticks).
	private boolean renameChecked;

	private final ChatAutoLookupGate chatAutoLookup = new ChatAutoLookupGate();
	private final ClogSessionState sessionState = new ClogSessionState();
	private final ClogIndex clogIndex = new ClogIndex();
	private final VisibleClogCategoryReader visibleClogCategoryReader = new VisibleClogCategoryReader();
	private final LiveClogSync liveClogSync = new LiveClogSync();
	private final ManualClogSync manualClogSync = new ManualClogSync();
	private final LocalCaReader localCaReader = new LocalCaReader();
	private final CaCatalog caCatalog = new CaCatalog();
	private final ClogLookupMenu lookupMenu = new ClogLookupMenu();
	private final AtomicBoolean characterPublishInFlight = new AtomicBoolean();
	private final AtomicBoolean characterPublishAfterSync = new AtomicBoolean();
	private final AtomicBoolean characterPrerequisiteAttempted = new AtomicBoolean();
	private final AtomicInteger characterPublishGeneration = new AtomicInteger();

	@Provides
	KillClogConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KillClogConfig.class);
	}

	@Override
	protected void startUp()
	{
		navButton = NavigationButton.builder()
			.tooltip("Kill Clog")
			.icon(getIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(clogButtonOverlay);
		mouseManager.registerMouseListener(clogButtonOverlay);
		panel.setPluginManager(pluginManager);
		panel.setNameAutocompleter(nameAutocompleter);
		panel.setClogIndex(clogIndex);

		lookupMenu.start(config, menuManager);

		enforceCharacterSettingDependency();
		panel.setKillclogSyncHandler(this::manualKillclogSync);
		panel.setCharacterPublishHandler(this::publishCharacter);
		panel.setSyncArrowEnabled(config.killclogSync());
		panel.setCharacterPublishEnabled(characterPublishingEnabled());
		// The sync trigger lives at the data seam: any path that lands a
		// first-party observation (bulk page capture, search-and-back walk,
		// live unlock) schedules a debounced push.
		localClogCache.setFirstPartyChangedListener(() ->
		{
			// A capture only counts once the payload is genuinely non-empty:
			// an empty first walk must neither reveal the chalice nor
			// schedule a push that would fail with nothing to send.
			if (localClogCache.hasFirstPartyDataForActive())
			{
				panel.setSyncArrowHasData(true);
				scheduleKillclogSync(KILLCLOG_SYNC_DEBOUNCE_SECONDS);
			}
		});

		kclogCommand.setClogIndex(clogIndex);
		localCaCache.setCaCatalog(caCatalog);
		runeProfileService.setCaCatalog(caCatalog);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND, kclogCommand::handle);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_MISSING, kclogCommand::handleMissing);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_THIRD_AGE, kclogCommand::handleThirdAge);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_GILDED, kclogCommand::handleGilded);

		// If installed mid-session, run login init on the client thread
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> enterLoggedInState(false));
		}

		log.debug("Kill Clog plugin started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(clogButtonOverlay);
		mouseManager.unregisterMouseListener(clogButtonOverlay);
		lookupMenu.stop(config, menuManager);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_MISSING);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_THIRD_AGE);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_GILDED);
		kclogCommand.clear();
		chatEmoji.clear();
		localClogCache.shutdown();
		localCaCache.shutdown();
		manualClogSync.reset();
		clogIndex.clear();
		localCaCache.setCaCatalog(null);
		runeProfileService.setCaCatalog(null);
		caCatalog.clear();
		sessionState.reset();
		liveClogSync.resetFirstSyncWarning();
		nameAutocompleter.clearClientSnapshot();
		localClogCache.setFirstPartyChangedListener(null);
		cancelCharacterPublish();
		cancelKillclogSync();
		SwingUtilities.invokeLater(() -> panel.shutdown());
		// The rename session dies with the plugin: if the account changes
		// while disabled, a surviving latch or anchor would let the OLD
		// account's continuity state authorize the NEW account's session.
		renameChecked = false;
		localClogCache.onSessionEnded();
		log.debug("Kill Clog plugin stopped");
	}

	private void enterLoggedInState(boolean requestLocalReads)
	{
		if (requestLocalReads)
		{
			sessionState.requestLocalReads();
		}

		Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null)
		{
			String name = local.getName();
			lastLocalName = name;
			AccountType acctType = getLocalAccountType();
			boolean localClogReady = localClogCache.setActivePlayer(name);
			localCaCache.setActivePlayer(name);
			SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			if (!requestLocalReads)
			{
				sessionState.requestLocalReads();
			}
			// Login catch-up: a debounced push that fired after logout (or
			// mid world-hop) aborted with nothing to relaunch it, so the last
			// capture of a session stayed unpublished. One quiet scheduled
			// push per login closes that hole; the server merge no-ops when
			// nothing changed.
			boolean hasLocalClog = localClogReady
				&& localClogCache.hasFirstPartyDataFor(name);
			panel.setSyncArrowHasData(hasLocalClog);
			if (hasLocalClog)
			{
				scheduleKillclogSync(KILLCLOG_SYNC_DEBOUNCE_SECONDS, false);
			}
		}

		nameAutocompleter.refreshClientSnapshot();
		GimBadgeLoader.load(client);
		clogIndex.ensureParsed(client, itemManager);
		caCatalog.capture(client);
		sessionState.requestAutoLookup(config.autoLookupOnLogin());
		lookupMenu.warnIfPlayerMenuSlotUnavailable(client, config, chatNotifier);
		reconcileClogTotalsFromVarps();
	}

	/**
	 * Publish the name slot only after its account-hash ledger verdict landed.
	 * The future completes on the cache writer, so every RuneLite and panel
	 * read is marshalled back onto its owning thread and re-fenced against the
	 * session that started the arbitration.
	 */
	private void onClogIdentitySettled(String name, long accountHash,
		long expectedEpoch, boolean settled)
	{
		if (!settled)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Player local = client.getLocalPlayer();
			if (localClogCache.currentSessionEpoch() != expectedEpoch
				|| local == null || local.getName() == null
				|| !local.getName().equalsIgnoreCase(name)
				|| client.getAccountHash() != accountHash
				|| !localClogCache.setActivePlayer(name))
			{
				return;
			}

			boolean hasLocalClog = localClogCache.hasFirstPartyDataFor(name);
			SwingUtilities.invokeLater(() -> panel.setSyncArrowHasData(hasLocalClog));
			if (hasLocalClog)
			{
				scheduleKillclogSync(KILLCLOG_SYNC_DEBOUNCE_SECONDS, false);
			}
		});
	}

	/**
	 * True-live total bump with no chat dependency: the client pushes the
	 * collection log counts as varps, so any unlock (and the login flood)
	 * moves them regardless of the player's notification settings. Upward
	 * only; chalice sync stays the downward authority. Runs on the client
	 * thread (varbit events and enterLoggedInState both arrive there).
	 */
	private void reconcileClogTotalsFromVarps()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		String name = local.getName();
		if (!localClogCache.hasDataFor(name))
		{
			return;
		}
		int obtained = client.getVarpValue(ClogVarps.OBTAINED);
		int total = client.getVarpValue(ClogVarps.TOTAL);
		if ((obtained > 0 || total > 0) && localClogCache.updateTotalsUpward(name, obtained, total))
		{
			SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(name));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			renameChecked = false;
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
			clogService.clearTempleFailures();
			runeProfileService.clearFailures();
			killclogService.clearFailures();
			enterLoggedInState(true);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
			manualClogSync.reset();
			clogIndex.clear();
			sessionState.resetAutoLookupSession();
			liveClogSync.resetFirstSyncWarning();
			nameAutocompleter.clearClientSnapshot();
			markLocalHiscoresDirty();
			// Silence old completions before ending the cache epoch, then
			// cancel again so an attempt that began in that narrow handoff
			// cannot narrate into the next login.
			cancelCharacterPublish();
			cancelKillclogSync();
			// The capture anchor and any queued rename checks die with the
			// session - a stale hash must never authorize the next account's
			// saves.
			localClogCache.onSessionEnded();
			cancelKillclogSync();
		}
		else if (event.getGameState() == GameState.HOPPING)
		{
			markLocalHiscoresDirty();
		}

		// The owner claim is scoped to one POH visit, exactly as vanilla
		// scopes it: any region load or hop drops it, so a friend's log can
		// never linger and gate (or worse, misattribute) a later harvest.
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING)
		{
			advLogOwner = null;
		}
	}

	// Jagex republishes the local player's hiscore row on logout and world
	// hop; the cached self-row predates it, so the next self-search refetches.
	private void markLocalHiscoresDirty()
	{
		if (lastLocalName != null)
		{
			hiscoreService.markDirty(lastLocalName);
		}
	}

	// Live collection-log unlock messages update local cache immediately.
	// Player-sent chat still rate-limits a self lookup refresh for older data paths.
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (config.showChatEmojis())
		{
			chatEmoji.rewrite(event);
		}

		// !kc <item name> provenance reveal. Boss arguments stay with the
		// built-in plugin's own "!kc" registration; the handler ignores them.
		kclogCommand.handleKcItem(event, clogIndex, localClogCache);

		// Kill-count messages land the same tick as the clog unlock they
		// caused; remembering the freshest one lets the unlock carry its
		// "obtained at N kc" provenance. SPAM covers filtered game messages.
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			ClogUnlockParser.KillContext kill = ClogUnlockParser.parseKillCount(event.getMessage());
			if (kill != null)
			{
				liveClogSync.rememberKill(kill, client.getTickCount());
			}
		}

		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String unlockName = ClogUnlockParser.parseItemName(event.getMessage());
			if (unlockName != null)
			{
				clientThread.invokeLater(() -> handleCollectionLogUnlock(unlockName, -1, -1));
				return;
			}
		}

		// Players whose notification setting is popup-only never produce the
		// personal game message; their own clan broadcast is the only chat
		// signal of the unlock, and it carries the fresh obtained/total pair.
		if (event.getType() == ChatMessageType.CLAN_MESSAGE)
		{
			ClogUnlockParser.BroadcastUnlock broadcast =
				ClogUnlockParser.parseClanBroadcast(event.getMessage());
			Player broadcastLocal = client.getLocalPlayer();
			if (broadcast != null && broadcastLocal != null && broadcastLocal.getName() != null
				&& Text.toJagexName(broadcast.playerName)
					.equalsIgnoreCase(Text.toJagexName(broadcastLocal.getName())))
			{
				clientThread.invokeLater(() -> handleCollectionLogUnlock(
					broadcast.itemName, broadcast.obtained, broadcast.total));
				return;
			}
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		String localName = local.getName();
		if (!chatAutoLookup.shouldRefresh(event, localName, panel.getDisplayedRsn(), System.currentTimeMillis()))
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			panel.setPlayerName(localName);
			panel.doLookup();
		});
	}

	private void handleCollectionLogUnlock(String itemName, int broadcastObtained, int broadcastTotal)
	{
		liveClogSync.handleUnlock(itemName, broadcastObtained, broadcastTotal, client,
			itemManager, clogIndex, localClogCache, chatNotifier, clogButtonOverlay,
			panel::onBulkCaptureComplete);
	}

	// Debounce window: a bulk capture completes many category writes in a
	// burst; one push carries them all.
	private static final int KILLCLOG_SYNC_DEBOUNCE_SECONDS = 10;

	private volatile ScheduledFuture<?> pendingKillclogSync;
	private final KillclogSyncGate syncGate = new KillclogSyncGate();

	private synchronized void scheduleKillclogSync(int delaySeconds)
	{
		scheduleKillclogSync(delaySeconds, false);
	}

	/**
	 * Manual pushes (the chalice, an explicit opt-in) narrate in chat;
	 * automatic ones (capture debounce, login catch-up, queued relaunch)
	 * keep to the status bar so an ordinary play session is not two extra
	 * chat lines per drop. Failures always speak.
	 */
	private synchronized void scheduleKillclogSync(int delaySeconds, boolean manual)
	{
		if (!config.killclogSync())
		{
			return;
		}
		if (pendingKillclogSync != null && !pendingKillclogSync.isDone())
		{
			return;
		}
		long scheduledEpoch = localClogCache.currentSessionEpoch();
		pendingKillclogSync = executor.schedule(() -> pushKillclogSync(manual, scheduledEpoch),
			delaySeconds, TimeUnit.SECONDS);
	}

	private synchronized void cancelKillclogSync()
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
	 * RuneLite's own chat-commands store records the local player's personal
	 * bests; no public provider serves them, which makes this map the sync's
	 * defining cargo. One account splinters into many rs-profile fragments
	 * over time, so the gather sweeps every fragment wearing the player's
	 * name and keeps the fastest time per boss. STANDARD-world fragments
	 * only: Leagues and speedrun profiles share the display name but store
	 * buffed-world times, and the min-merge would launder those into the
	 * player's real record. Client thread (config reads).
	 */
	private java.util.Map<String, Double> gatherPersonalBests(String rsn)
	{
		java.util.List<String> profileKeys = new java.util.ArrayList<>();
		for (net.runelite.client.config.RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (rsn.equalsIgnoreCase(profile.getDisplayName())
				&& profile.getType() == net.runelite.client.config.RuneScapeProfileType.STANDARD)
			{
				String key = profile.getKey();
				profileKeys.add(key.startsWith("rsprofile.") ? key : "rsprofile." + key);
			}
		}

		PersonalBests pbs = new PersonalBests(configManager);
		java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
		for (net.runelite.client.hiscore.HiscoreSkill boss : PanelData.BOSSES)
		{
			putBestSeconds(out, pbs, profileKeys, boss.getName());
		}
		log.debug("killclog sync pb gather: {} rs-profiles total, {} matched '{}', {} pbs",
			configManager.getRSProfiles().size(), profileKeys.size(), rsn, out.size());
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
	private java.util.Map<String, SyncService.DetailedPb> gatherDetailedPersonalBests(String rsn)
	{
		java.util.List<String> profileKeys = new java.util.ArrayList<>();
		for (net.runelite.client.config.RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (rsn.equalsIgnoreCase(profile.getDisplayName())
				&& profile.getType() == net.runelite.client.config.RuneScapeProfileType.STANDARD)
			{
				String key = profile.getKey();
				profileKeys.add(key.startsWith("rsprofile.") ? key : "rsprofile." + key);
			}
		}

		PersonalBests pbs = new PersonalBests(configManager);
		AdvLogPbs advLog = new AdvLogPbs(configManager);
		java.util.Map<String, SyncService.DetailedPb> out = new java.util.LinkedHashMap<>();
		for (net.runelite.client.hiscore.HiscoreSkill boss : PanelData.BOSSES)
		{
			for (java.util.Map.Entry<String, Double> entry
				: pbs.variantSecondsAcrossProfiles(profileKeys, boss.getName()).entrySet())
			{
				mergeDetailedPb(out, entry.getKey(), entry.getValue(), "store");
			}
			for (java.util.Map.Entry<String, Double> entry
				: advLog.variantSecondsAcrossProfiles(profileKeys, boss.getName()).entrySet())
			{
				mergeDetailedPb(out, entry.getKey(), entry.getValue(), "advlog");
			}
		}
		return out;
	}

	/** Faster wins; on a tie the earlier lane keeps the tag. */
	private static void mergeDetailedPb(java.util.Map<String, SyncService.DetailedPb> out,
		String key, double seconds, String source)
	{
		SyncService.DetailedPb existing = out.get(key);
		if (existing == null || seconds < existing.seconds)
		{
			out.put(key, new SyncService.DetailedPb(seconds, source));
		}
	}

	private static void putBestSeconds(java.util.Map<String, Double> out, PersonalBests pbs,
		java.util.List<String> profileKeys, String bossName)
	{
		double seconds = profileKeys.isEmpty()
			? pbs.bestSeconds(bossName)
			: pbs.bestSecondsAcrossProfiles(profileKeys, bossName);
		if (seconds > 0)
		{
			out.put(bossName, seconds);
		}
	}

	// If a push arrived while the slot was occupied, launch it now that the
	// slot is free (the opt-out/opt-in-mid-request case).
	private void launchQueuedKillclogSync()
	{
		if (syncGate.consumeQueued() && config.killclogSync())
		{
			scheduleKillclogSync(0);
		}
	}

	private boolean characterPublishingEnabled()
	{
		return config.killclogSync() && config.characterModel();
	}

	/**
	 * RuneLite's public config API has no dynamic disabled-state attribute.
	 * Enforce the dependency at the data boundary instead: the child opt-in
	 * cannot survive while first-party sync is disabled.
	 */
	private void enforceCharacterSettingDependency()
	{
		if (!config.killclogSync() && config.characterModel())
		{
			configManager.unsetConfiguration("killclog", "characterModel");
		}
	}

	private void publishCharacter()
	{
		startCharacterPublish(true);
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
		panel.showCharacterPublishStatus(CHARACTER_RENDERING_STATUS, false, false);
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
				panel.showCharacterPublishStatus(CHARACTER_FAILED_STATUS, false, true);
				return;
			}

			profileAppearanceService.publishCurrent(rsn, accountHash)
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
			panel.showCharacterPublishStatus(CHARACTER_FAILED_STATUS, false, true);
			return;
		}

		if (result.outcome == ProfileAppearanceService.Outcome.PROFILE_REQUIRED
			&& characterPublishingEnabled()
			&& characterPrerequisiteAttempted.compareAndSet(false, true))
		{
			characterPublishAfterSync.set(true);
			panel.showCharacterPublishStatus(CHARACTER_RENDERING_STATUS, false, false);
			startCharacterPrerequisiteSync();
			return;
		}

		characterPublishInFlight.set(false);
		boolean published = result.outcome == ProfileAppearanceService.Outcome.PUBLISHED;
		panel.showCharacterPublishStatus(characterPublishTerminalStatus(result.outcome), published, true);
	}

	static String characterPublishTerminalStatus(ProfileAppearanceService.Outcome outcome)
	{
		return outcome == ProfileAppearanceService.Outcome.PUBLISHED
			? CHARACTER_PUBLISHED_STATUS : CHARACTER_FAILED_STATUS;
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
		scheduleKillclogSync(0, false);
	}

	private void cancelCharacterPublish()
	{
		characterPublishGeneration.incrementAndGet();
		characterPublishAfterSync.set(false);
		characterPublishInFlight.set(false);
		characterPrerequisiteAttempted.set(false);
		panel.showCharacterPublishStatus(" ", false, false);
	}

	private boolean failQueuedCharacterPublish()
	{
		if (!characterPublishAfterSync.getAndSet(false))
		{
			return false;
		}
		characterPublishInFlight.set(false);
		panel.showCharacterPublishStatus(CHARACTER_FAILED_STATUS, false, true);
		return true;
	}

	private void scheduleCharacterPublishAfterSync()
	{
		int expectedGeneration = characterPublishGeneration.get();
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
			panel.showCharacterPublishStatus(CHARACTER_FAILED_STATUS, false, true);
		}
	}

	/**
	 * The panel's sync arrow: push now, skipping any pending debounce. The
	 * single-flight gate still applies; the arrow is hidden while the status
	 * bar is occupied, so mid-flight re-clicks cannot happen.
	 */
	private void manualKillclogSync()
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
		scheduleKillclogSync(0, true);
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
		final int generation = syncGate.beginAttempt();
		if (generation < 0)
		{
			return;
		}
		if (localClogCache.currentSessionEpoch() != scheduledEpoch)
		{
			syncGate.abortAttempt();
			launchQueuedKillclogSync();
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
					panel.showSyncStatus(" ", false, false);
					launchQueuedKillclogSync();
					return;
				}
				Player local = client.getLocalPlayer();
				String rsn = local != null ? local.getName() : null;
				long accountHash = client.getAccountHash();
				if (rsn == null || accountHash == -1)
				{
					syncGate.abortAttempt();
					if (!failQueuedCharacterPublish())
					{
						panel.showSyncStatus(" ", false, false);
					}
					launchQueuedKillclogSync();
					return;
				}
				AccountType accountType = getLocalAccountType();
				// The debounce plus the round trip is a long quiet gap; say the
				// push is underway so the result line has an antecedent.
				if (manual)
				{
					chatNotifier.send(ChatNotice.SYNC_RESULT, "Syncing collection log to killclog.com...");
				}
				panel.showSyncStatus("syncing...", false, false);
				java.util.Map<String, Double> pbs = gatherPersonalBests(rsn);
				java.util.Map<String, SyncService.DetailedPb> detailedPbs =
					gatherDetailedPersonalBests(rsn);
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
					panel.showSyncStatus("sync failed", false, true);
				}
				// Failures always chat, this path included.
				chatNotifier.send(ChatNotice.SYNC_RESULT,
					"Collection log sync failed - see the client log.");
				launchQueuedKillclogSync();
			}
		});
	}

	private void dispatchKillclogSync(String rsn, long accountHash, AccountType accountType,
		java.util.Map<String, Double> pbs, java.util.Map<String, SyncService.DetailedPb> detailedPbs,
		boolean manual, int generation, long cacheEpoch)
	{
		if (localClogCache.currentSessionEpoch() != cacheEpoch)
		{
			// The session ended between gather and dispatch: release the
			// single-flight slot and walk away clean.
			syncGate.abortAttempt();
			if (!failQueuedCharacterPublish())
			{
				panel.showSyncStatus(" ", false, false);
			}
			launchQueuedKillclogSync();
			return;
		}
		try
		{
			syncService.syncCollectionLog(rsn, accountHash, accountType, pbs, detailedPbs,
				cacheEpoch, syncGate, generation)
				.whenComplete((result, err) ->
				{
					boolean current = syncGate.complete(generation);
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
								panel.showCharacterPublishStatus(CHARACTER_RENDERING_STATUS, false, false);
							}
							else
							{
								panel.showSyncStatus("retrying...", false, false);
							}
							scheduleKillclogSync(Math.max(result.retryAfterSeconds, 2), manual);
							launchQueuedKillclogSync();
							return;
						}

						// Everything below is a terminal outcome for this episode.
						syncGate.restoreRetryCredit();
						if (characterWaiting)
						{
							characterPublishAfterSync.set(false);
							if (result.ok && !result.dryRun && characterPublishingEnabled())
							{
								scheduleCharacterPublishAfterSync();
							}
							else
							{
								characterPublishInFlight.set(false);
								panel.showCharacterPublishStatus(CHARACTER_FAILED_STATUS, false, true);
							}
						}
						else
						{
							panel.showSyncStatus(result.ok ? "synced!" : "sync failed", result.ok, true);
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
						if (!failQueuedCharacterPublish())
						{
							panel.showSyncStatus(" ", false, false);
						}
					}
					launchQueuedKillclogSync();
				});
		}
		catch (RuntimeException e)
		{
			log.warn("killclog sync push failed at dispatch", e);
			syncGate.abortAttempt();
			if (!failQueuedCharacterPublish())
			{
				panel.showSyncStatus("sync failed", false, true);
			}
			// Failures always chat, this path included; chat sends need the
			// client thread and this body runs on the executor.
			clientThread.invoke(() -> chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Collection log sync failed - see the client log."));
			launchQueuedKillclogSync();
		}
	}

	// Keep local CA current when a task completes mid-session, and the live
	// catalog current when the game moves a tier threshold (a CA release).
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (localCaReader.isCaVarbit(event.getVarbitId())
			|| CaCatalog.isThresholdVarbit(event.getVarbitId()))
		{
			sessionState.requestCaRead();
		}

		if (event.getVarpId() == ClogVarps.OBTAINED || event.getVarpId() == ClogVarps.TOTAL)
		{
			reconcileClogTotalsFromVarps();
		}
	}

	/** Read per-tier CA completed counts from game varbits and persist them for the active player. */
	private boolean captureLocalCa()
	{
		caCatalog.capture(client);
		return localCaReader.capture(client, localCaCache);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		nameAutocompleter.refreshClientSnapshot();

		// Rename continuity: once per login, when both halves of the local
		// identity have arrived, the cache follows the account onto its
		// current name (the server migrates its own copy on the next sync).
		// The check runs on the cache's writer thread - it reads files - and
		// the notice comes back through the poll below on a later tick.
		if (!renameChecked)
		{
			Player renameLocal = client.getLocalPlayer();
			long renameHash = client.getAccountHash();
			if (renameLocal != null && renameLocal.getName() != null && renameHash != -1)
			{
				renameChecked = true;
				String renameName = renameLocal.getName();
				long renameEpoch = localClogCache.currentSessionEpoch();
				localClogCache.followNameChangeAsync(renameName, renameHash, renameEpoch)
					.thenAccept(settled -> onClogIdentitySettled(
						renameName, renameHash, renameEpoch, settled));
			}
		}
		// The notice survives whichever path migrated first (the sync
		// pre-flight can win the race); one line either way.
		String previousName = localClogCache.consumeRenameNotice();
		if (previousName != null)
		{
			chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Kill Clog followed your name change from '" + previousName
				+ "' - your collection log came along.");
			Player noticeLocal = client.getLocalPlayer();
			if (noticeLocal != null && noticeLocal.getName() != null)
			{
				String renameName = noticeLocal.getName();
				SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(renameName));
			}
		}

		if (sessionState.pendingAcctTypeRecheck())
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				sessionState.clearAcctTypeRecheck();
				String name = local.getName();
				AccountType acctType = getLocalAccountType();
				SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			}
		}

		if (sessionState.pendingCaRead())
		{
			sessionState.setPendingCaRead(!captureLocalCa());
		}

		if (sessionState.pendingAutoLookup())
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				String name = local.getName();
				if (localClogCache.setActivePlayer(name))
				{
					sessionState.markAutoLookupStarted();
					localCaCache.setActivePlayer(name);
					// The login transition often fires before the player's name is
					// readable. This tick path waits for identity arbitration too,
					// so a resident name-slot file can never become the self view.
					panel.setSyncArrowHasData(localClogCache.hasFirstPartyDataFor(name));
					captureLocalCa();
					AccountType acctType = getLocalAccountType();
					SwingUtilities.invokeLater(() ->
					{
						panel.setLoggedInPlayer(name, acctType);

						// Do not overwrite the user's research. LOGGED_IN fires on
						// every world hop, so skip auto-lookup when viewing someone else.
						String displayed = panel.getDisplayedRsn();
						if (displayed != null && !displayed.equalsIgnoreCase(name))
						{
							return;
						}

						panel.setPlayerName(name);
						panel.doLookup();
					});
				}
			}
		}

		manualClogSync.onGameTick(client, clogIndex, localClogCache,
			chatNotifier, clogButtonOverlay, liveClogSync::resetFirstSyncWarning,
			panel::onBulkCaptureComplete);

		// Adventure-log pb harvest, one tick after each widget load so the
		// children are populated (vanilla's own deferral). The new menu
		// interface hosts more than the Adventure Log; a non-matching title
		// simply leaves the owner as-is.
		if (advLogTitleLoaded)
		{
			advLogTitleLoaded = false;
			String owner = AdvLogPbs.readOwner(client);
			if (owner != null)
			{
				advLogOwner = owner;
			}
		}
		if (advLogCountersLoaded)
		{
			advLogCountersLoaded = false;
			Player local = client.getLocalPlayer();
			if (local != null && AdvLogPbs.sameName(local.getName(), advLogOwner))
			{
				new AdvLogPbs(configManager).harvest(client);
			}
			else
			{
				// Someone else's house, or the title never resolved. Saying so
				// separates "not yours" from "parser found nothing".
				log.debug("adventure log counters skipped: owner '{}' is not the local player",
					advLogOwner);
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Both menu interfaces are watched: the player's interface-style
		// setting decides which one the Adventure Log opens in, and watching
		// only one harvests nothing for everyone on the other style.
		if (event.getGroupId() == InterfaceID.MENU_NEW || event.getGroupId() == InterfaceID.MENU)
		{
			advLogTitleLoaded = true;
		}
		else if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
		{
			advLogCountersLoaded = true;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptEvent() == null)
		{
			return;
		}

		manualClogSync.captureScriptArguments(event.getScriptId(),
			event.getScriptEvent().getArguments(), client.getTickCount());
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		String pluginName = event.getPlugin().getClass().getSimpleName();
		if (pluginName.equals("ResourcePacksPlugin"))
		{
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
		}

		// String check; FourTwentyKcPlugin lives in a separate plugin.
		if (pluginName.equals("FourTwentyKcPlugin"))
		{
			SwingUtilities.invokeLater(() -> panel.setFourTwentyVisible(event.isLoaded()));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("resourcepacks".equals(event.getGroup()))
		{
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
			return;
		}

		if (!event.getGroup().equals("killclog"))
		{
			return;
		}

		if (MENU_CONFIG_KEYS.contains(event.getKey()))
		{
			// Refresh the menu option when its label or locations change.
			lookupMenu.refresh(config, menuManager);
		}

		if ("killclogSync".equals(event.getKey()))
		{
			panel.setSyncArrowEnabled(config.killclogSync());
			if (config.killclogSync())
			{
				panel.setCharacterPublishEnabled(config.characterModel());
				// Opting in mid-session pushes the already-captured log right
				// away; nothing else fires until the next capture or unlock.
				scheduleKillclogSync(0, true);
			}
			else
			{
				if (config.characterModel())
				{
					configManager.unsetConfiguration("killclog", "characterModel");
				}
				panel.setCharacterPublishEnabled(false);
				cancelCharacterPublish();
				cancelKillclogSync();
			}
		}
		else if ("characterModel".equals(event.getKey()))
		{
			if (config.characterModel() && !config.killclogSync())
			{
				configManager.unsetConfiguration("killclog", "characterModel");
			}
			boolean enabled = characterPublishingEnabled();
			panel.setCharacterPublishEnabled(enabled);
			if (!enabled)
			{
				cancelCharacterPublish();
			}
		}

		SwingUtilities.invokeLater(() -> panel.onConfigChanged(event.getKey()));
	}

	private void openPanelAndLookup(String name)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.setPlayerName(name);
			panel.doLookup();
		});
	}

	public void lookupFromExternalPlugin(String name)
	{
		if (name == null || name.isBlank())
		{
			return;
		}

		openPanelAndLookup(Text.toJagexName(name.trim()));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		lookupMenu.addLookupEntry(event, client, config, this::openPanelAndLookup);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		lookupMenu.handlePlayerLookup(event, config, this::openPanelAndLookup);
	}

	// Sync button.

	/**
	 * Called by ClogButtonOverlay on click.
	 * First click: buffers visible category + arms manual bulk capture.
	 * Subsequent clicks: captures visible category directly.
	 */
	void onSyncClicked()
	{
		clogButtonOverlay.flashGreen();
		clientThread.invokeLater(() ->
		{
			Player local = client.getLocalPlayer();
			if (local == null || local.getName() == null)
			{
				return;
			}

			manualClogSync.onSyncClicked(client, clogIndex, visibleClogCategoryReader,
				localClogCache, chatNotifier, panel::onBulkCaptureComplete);
		});
	}

	private AccountType getLocalAccountType()
	{
		return AccountType.fromRuneLiteVarbit(client.getVarbitValue(VarbitID.IRONMAN));
	}

	private BufferedImage getIcon()
	{
		return KillClogIcons.pluginIconOrCollectionLog(itemManager);
	}
}
