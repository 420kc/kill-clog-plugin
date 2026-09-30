package com.killclog;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kill Clog",
	description = "HiScores and Collection Log Overhaul",
	tags = {"boss", "kc", "kill count", "collection log", "clog", "hiscores", "pvm", "pb",
		"personal best", "ironman", "comparison", "sync", "templeosrs", "runeprofile",
		"combat achievements"},
	internalName = "kill-clog",
	legacyDataDirectory = "kill-clog"
)
public class KillClogPlugin extends Plugin
{
	static final int CLOG_INTERFACE = 621;
	private static final int CLOG_SEARCH_TOGGLE_SCRIPT = 4084;
	private static final int CLOG_SEARCH_CONTAINER = 71;
	private static final String RUNEPROFILE_PLUGIN_NAME = "RuneProfile";
	static final String CHARACTER_RENDERING_STATUS = "updating character...";
	static final String CHARACTER_PUBLISHED_STATUS = "character updated!";
	static final String CHARACTER_FAILED_STATUS = "Publish failed";
	static final String CHARACTER_APPEARANCE_STATUS = "Change equipment, then retry";
	static final String CHARACTER_PENDING_STATUS = "Still rendering...";
	static final String CHARACTER_RECOVERY_STATUS = "Publishing on hold";
	static final String CHARACTER_DISABLED_STATUS = "Publishing unavailable";
	static final String CHARACTER_UNKNOWN_STATUS = "Check your profile";
	static final String CHARACTER_BUSY_STATUS = "Finishing previous request...";

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
	// Ticks on this world with an unchanged account type. Counters, CA tiers and the account
	// type sent to the server wait for it, so a hop or login never carries another mode's
	// values into this one.
	private int settledTicks;
	private static final int SETTLED_TICKS = 10;
	@Inject
	private Gson gson;
	// .runelite/plugin-data/kill-clog, or null for a session that could not open it.
	private Filepath dataFolder;
	// The active League's own store, opened on first use; one League at a time.
	private LocalClogCache leagueCache;
	private String leagueCacheId;
	private java.util.function.Function<String, LocalClogCache> leagueCacheFactory = id ->
		new LocalClogCache(gson, dataFolder == null ? null : dataFolder.join("leagues", id));
	// The store the collection log walk started with; a different one means start over.
	private LocalClogCache walkCache;
	// The logged-in account, for PB reads on the panel's thread, and the world's and running League last given to the panel.
	private volatile long localAccountHash = -1;
	private String panelLeague;

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
	private final LiveClogSync liveClogSync = new LiveClogSync();
	private final ManualClogSync manualClogSync = new ManualClogSync();
	private final LocalCaReader localCaReader = new LocalCaReader();
	private final CaCatalog caCatalog = new CaCatalog();
	private final ClogLookupMenu lookupMenu = new ClogLookupMenu();
	private PublicationCoordinator publication;

	@Provides
	KillClogConfig provideConfig(ConfigManager configManager)
	{
		// Saved settings take their current shape before anything reads them:
		// the panel is built at injection, ahead of startUp.
		ConfigMigrations.run(configManager);
		return configManager.getConfig(KillClogConfig.class);
	}

	@Override
	protected void startUp()
	{
		// RuneLite moves the 2.4 folder in on first use; a failed move stays in
		// memory this session and retries next start, never writing a new folder.
		try
		{
			dataFolder = getPluginDirectory();
		}
		catch (IOException | RuntimeException e)
		{
			dataFolder = null;
			log.warn("Kill Clog data folder unavailable this session: {}", e.getMessage());
		}
		localClogCache.useFolder(dataFolder);
		localCaCache.useFolder(dataFolder == null ? null : dataFolder.join("ca"));

		navButton = NavigationButton.builder()
			.tooltip("Kill Clog")
			.icon(getIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		panel.setNameAutocompleter(nameAutocompleter);
		panel.setClogIndex(clogIndex);

		lookupMenu.start(config, menuManager);

		// One coordinator for the life of this plugin instance: RuneLite reuses
		// the instance across disable and enable, and the sync gate, its queued
		// intent and a request still in the air must survive that, or a re-enabled
		// plugin would run a second sync beside the old one.
		if (publication == null)
		{
			publication = new PublicationCoordinator(config, configManager, client, clientThread, executor,
				localClogCache, syncService, profileAppearanceService, chatNotifier, panelFeedback(),
				() -> mainSettled() ? getLocalAccountType() : null, this::mode, this::cacheFor,
				killclogService::leagueProfileType);
		}
		enforceCharacterSettingDependency();
		panel.setKillclogSyncHandler(publication::manualSync);
		panel.setCharacterPublishHandler(publication::publishCharacter);
		panel.setSelfPb(this::selfPb);
		// Nothing matches it, so the first tick hands the panel and chat this world's game again.
		panelLeague = "";
		panel.setSyncArrowEnabled(config.killclogSync());
		panel.setCharacterPublishEnabled(publication.characterPublishingEnabled());
		// The sync trigger lives at the data seam: any path that lands a
		// first-party observation (bulk page capture, Collection Log Search,
		// live unlock) schedules a debounced push.
		localClogCache.setFirstPartyChangedListener(() -> firstPartyChanged(localClogCache));

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
		profileAppearanceService.clearOriginalAppearance();
		clientToolbar.removeNavigation(navButton);
		lookupMenu.stop(config, menuManager);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_MISSING);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_THIRD_AGE);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_GILDED);
		kclogCommand.clear();
		chatEmoji.clear();
		localClogCache.shutdown();
		if (leagueCache != null)
		{
			leagueCache.shutdown();
			leagueCache = null;
			leagueCacheId = null;
		}
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
		publication.cancelCharacterPublish();
		publication.cancelSync();
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

		killclogService.refreshIndex();
		Player local = client.getLocalPlayer();
		LocalClogCache cache = captureCache();
		if (local != null && local.getName() != null && cache != null)
		{
			String name = local.getName();
			lastLocalName = name;
			AccountType acctType = getLocalAccountType();
			boolean localClogReady = cache.setActivePlayer(name);
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
				&& cache.hasFirstPartyDataFor(name);
			panel.setSyncArrowHasData(hasLocalClog);
			if (hasLocalClog)
			{
				publication.scheduleAutomaticSync();
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
	private void onClogIdentitySettled(LocalClogCache cache, String name, long accountHash,
		long expectedEpoch, boolean settled)
	{
		if (!settled)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Player local = client.getLocalPlayer();
			if (cache.currentSessionEpoch() != expectedEpoch || cache != captureCache()
				|| local == null || local.getName() == null
				|| !local.getName().equalsIgnoreCase(name)
				|| client.getAccountHash() != accountHash
				|| !cache.setActivePlayer(name))
			{
				return;
			}

			boolean hasLocalClog = cache.hasFirstPartyDataFor(name);
			SwingUtilities.invokeLater(() -> panel.setSyncArrowHasData(hasLocalClog));
			if (hasLocalClog)
			{
				publication.scheduleAutomaticSync();
			}
		});
	}

	/**
	 * True-live total bump with no chat dependency: the client pushes the
	 * collection log counts as varps, so any unlock (and the login flood)
	 * moves them regardless of the player's notification settings. Upward
	 * only; full log refresh stays the downward authority. Runs on the client
	 * thread (varbit events and enterLoggedInState both arrive there).
	 */
	private void reconcileClogTotalsFromVarps()
	{
		Player local = client.getLocalPlayer();
		if (!mainSettled() || local == null || local.getName() == null)
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

	// Run before Fashionscape (0) and Weapon/Gear/Anim Replacer (1).
	@Subscribe(priority = 2)
	public void onPlayerChanged(PlayerChanged event)
	{
		profileAppearanceService.captureOriginalAppearance(event.getPlayer());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			profileAppearanceService.clearOriginalAppearance();
		}
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
			// The sync affordance belongs to the account that just ended. Hiding
			// it also fences a success callback queued during the logout handoff.
			panel.setSyncArrowHasData(false);
			panel.resetSyncFeedback();
			manualClogSync.reset();
			clogIndex.clear();
			sessionState.resetAutoLookupSession();
			liveClogSync.resetFirstSyncWarning();
			nameAutocompleter.clearClientSnapshot();
			markLocalHiscoresDirty();
			// Silence old completions before ending the cache epoch, then
			// cancel again so an attempt that began in that narrow handoff
			// cannot narrate into the next login.
			publication.cancelCharacterPublish();
			publication.cancelSync();
			// The capture anchor and any queued rename checks die with the
			// session - a stale hash must never authorize the next account's
			// saves.
			localClogCache.onSessionEnded();
			if (leagueCache != null)
			{
				leagueCache.onSessionEnded();
			}
			publication.cancelSync();
			localAccountHash = -1;
			followWorld();
		}
		else if (event.getGameState() == GameState.HOPPING)
		{
			markLocalHiscoresDirty();
			// A walk never spans two worlds: the next one may be a different game.
			manualClogSync.reset();
		}
		// A region load stays on the same world; everything else may change it.
		if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
		{
			settledTicks = 0;
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
		LocalClogCache kcCache = captureCache();
		if (kcCache != null)
		{
			kclogCommand.handleKcItem(event, clogIndex, kcCache);
		}

		// RuneLite keeps one registered handler per command string, so claiming
		// !log here would replace RuneProfile's handler. Read the raw chat line
		// only as a fallback when RuneProfile itself is not enabled.
		String message = event.getMessage();
		if (KillClogChatCommand.isCompatibleLogCommand(event.getType(), message)
			&& !isRuneProfileActive())
		{
			executor.execute(() -> kclogCommand.handleLogCompatibility(event, message));
		}

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
		LocalClogCache cache = captureCache();
		if (cache == null)
		{
			return;
		}
		liveClogSync.handleUnlock(itemName, broadcastObtained, broadcastTotal, client,
			itemManager, clogIndex, cache, chatNotifier,
			panel::onBulkCaptureComplete, settledTicks >= SETTLED_TICKS);
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
		if (event.getVarbitId() == VarbitID.IRONMAN)
		{
			settledTicks = 0;
		}
	}

	/** Read per-tier CA completed counts from game varbits and persist them for the active player. */
	private boolean captureLocalCa()
	{
		if (!mainSettled())
		{
			return false;
		}
		caCatalog.capture(client);
		return localCaReader.capture(client, localCaCache);
	}

	private boolean isRuneProfileActive()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(plugin -> RUNEPROFILE_PLUGIN_NAME.equals(plugin.getName())
				&& pluginManager.isPluginActive(plugin));
	}

	/** Main, the announced League, or null: logged in on a world whose game is known. */
	private String mode()
	{
		return client.getGameState() == GameState.LOGGED_IN
			? GameMode.of(client.getWorldType(), killclogService.activeLeague()) : null;
	}

	private boolean onMainWorld()
	{
		return GameMode.MAIN.equals(mode());
	}

	/** The store this world's captures belong in, or null when the world's game is unknown. */
	private LocalClogCache captureCache()
	{
		String mode = mode();
		return mode == null ? null : cacheFor(mode);
	}

	private LocalClogCache cacheFor(String mode)
	{
		if (GameMode.MAIN.equals(mode))
		{
			return localClogCache;
		}
		if (!mode.equals(leagueCacheId))
		{
			if (leagueCache != null)
			{
				leagueCache.shutdown();
			}
			LocalClogCache created = leagueCacheFactory.apply(mode);
			created.setFirstPartyChangedListener(() -> firstPartyChanged(created));
			leagueCache = created;
			leagueCacheId = mode;
		}
		return leagueCache;
	}

	private void firstPartyChanged(LocalClogCache cache)
	{
		// A capture only counts once the payload is genuinely non-empty:
		// an empty first walk must neither reveal the chalice nor
		// schedule a push that would fail with nothing to send.
		if (cache.hasFirstPartyDataForActive())
		{
			panel.setSyncArrowHasData(true);
			publication.scheduleAutomaticSync();
		}
	}

	/** A League world reads its League in the panel and chat; every other world, or none, the main game. */
	private void followWorld()
	{
		String mode = mode();
		String league = mode == null || GameMode.MAIN.equals(mode) ? null : mode;
		String active = killclogService.activeLeague();
		// Keyed on the mode itself, so a logout (no mode) ends a flip made on a main world.
		if (!(mode + "/" + active).equals(panelLeague))
		{
			panelLeague = mode + "/" + active;
			LocalClogCache leagueLog = league == null ? null : captureCache();
			kclogCommand.readLeague(league, leagueLog);
			SwingUtilities.invokeLater(() -> panel.followWorld(league, leagueLog, active));
		}
	}

	/** Your PB for a panel boss in one game: the main game's profiles, or the League's announced one. */
	private String selfPb(String league, String boss)
	{
		return new PersonalBests(configManager).pbText(PersonalBests.profileKeys(configManager.getRSProfiles(), localAccountHash,
			league == null ? RuneScapeProfileType.STANDARD.name() : killclogService.leagueProfileType(league)), boss);
	}

	void setLeagueCacheFactory(java.util.function.Function<String, LocalClogCache> factory)
	{
		leagueCacheFactory = factory;
	}

	private boolean mainSettled()
	{
		return settledTicks >= SETTLED_TICKS && onMainWorld();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		nameAutocompleter.refreshClientSnapshot();
		localAccountHash = client.getAccountHash();
		if (mode() == null)
		{
			settledTicks = 0;
		}
		else if (++settledTicks == SETTLED_TICKS && onMainWorld())
		{
			// Counters and CA tiers read before the world settled were skipped: take them now.
			reconcileClogTotalsFromVarps();
			sessionState.requestCaRead();
		}
		followWorld();

		// Rename continuity: once per login, when both halves of the local
		// identity have arrived, the cache follows the account onto its
		// current name (the server migrates its own copy on the next sync).
		// The check runs on the cache's writer thread - it reads files - and
		// the notice comes back through the poll below on a later tick.
		if (!renameChecked)
		{
			Player renameLocal = client.getLocalPlayer();
			long renameHash = client.getAccountHash();
			LocalClogCache renameCache = captureCache();
			if (renameLocal != null && renameLocal.getName() != null && renameHash != -1 && renameCache != null)
			{
				renameChecked = true;
				String renameName = renameLocal.getName();
				long renameEpoch = renameCache.currentSessionEpoch();
				renameCache.followNameChangeAsync(renameName, renameHash, renameEpoch)
					.thenAccept(settled -> onClogIdentitySettled(
						renameCache, renameName, renameHash, renameEpoch, settled));
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
				// A world with no known game still shows the main log, as it always has.
				LocalClogCache cache = captureCache() != null ? captureCache() : localClogCache;
				if (cache.setActivePlayer(name))
				{
					sessionState.markAutoLookupStarted();
					localCaCache.setActivePlayer(name);
					// The login transition often fires before the player's name is
					// readable. This tick path waits for identity arbitration too,
					// so a resident name-slot file can never become the self view.
					panel.setSyncArrowHasData(cache.hasFirstPartyDataFor(name));
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

		LocalClogCache cache = captureCache();
		if (cache != walkCache)
		{
			manualClogSync.reset();
			walkCache = cache;
		}
		if (cache != null)
		{
			manualClogSync.onGameTick(client, clogIndex, cache,
				chatNotifier, liveClogSync::resetFirstSyncWarning,
				panel::onBulkCaptureComplete);
		}

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
		if (event.getGroupId() == CLOG_INTERFACE)
		{
			// Request a full Search walk after the player's log has initialized.
			// Ordinary visible-category scripts cannot establish a full capture.
			manualClogSync.onCollectionLogOpened(client);
		}

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
		ScriptEvent script = event.getScriptEvent();
		if (script == null)
		{
			return;
		}

		// Native Search can run without a MenuOptionClicked event. Observe its
		// opening callback before it emits the full obtained-item stream.
		if (isCollectionLogSearchScript(event.getScriptId(), script))
		{
			onCollectionLogSearch();
		}

		manualClogSync.captureScriptArguments(client, event.getScriptId(),
			script.getArguments(), client.getTickCount());
	}

	private boolean isCollectionLogSearchScript(int scriptId, ScriptEvent script)
	{
		if (scriptId != CLOG_SEARCH_TOGGLE_SCRIPT)
		{
			return false;
		}
		Widget source = script.getSource();
		Object[] args = script.getArguments();
		if (source == null || source.getId() >>> 16 != CLOG_INTERFACE || source.isHidden()
			|| args == null || args.length < 2 || !(args[1] instanceof Integer) || (int) args[1] != 0)
		{
			return false;
		}
		// 4084 calls toggle procedure 4085: an already visible Search or a
		// force-close argument closes it. Neither is a new full-log capture.
		Widget search = client.getWidget(CLOG_INTERFACE, CLOG_SEARCH_CONTAINER);
		return search != null && search.isSelfHidden();
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		String pluginName = event.getPlugin().getClass().getSimpleName();
		if (pluginName.equals("ResourcePacksPlugin"))
		{
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
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
				publication.scheduleSync(0, true);
			}
			else
			{
				if (config.characterModel())
				{
					configManager.unsetConfiguration("killclog", "characterModel");
				}
				panel.setCharacterPublishEnabled(false);
				publication.cancelCharacterPublish();
				publication.cancelSync();
			}
		}
		else if ("silentAutomaticSync".equals(event.getKey()))
		{
			panel.refreshSyncFeedbackSettings();
		}
		else if ("characterModel".equals(event.getKey()))
		{
			if (config.characterModel() && !config.killclogSync())
			{
				configManager.unsetConfiguration("killclog", "characterModel");
			}
			boolean enabled = publication.characterPublishingEnabled();
			panel.setCharacterPublishEnabled(enabled);
			if (!enabled)
			{
				publication.cancelCharacterPublish();
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
		if (isCollectionLogSearchClick(event.getMenuOption(), event.getMenuEntry().getParam1()))
		{
			// Arm before the game's Search action runs script 4100 for every
			// obtained entry. Opening the interface itself is too early: its
			// visible category can run the same script and is not a full log.
			onCollectionLogSearch();
		}
		lookupMenu.handlePlayerLookup(event, config, this::openPanelAndLookup);
	}

	private void onCollectionLogSearch()
	{
		clogIndex.ensureParsed(client, itemManager);
		LocalClogCache cache = captureCache();
		if (cache != null)
		{
			manualClogSync.onCollectionLogSearch(client, clogIndex, cache, chatNotifier);
		}
	}

	static boolean isCollectionLogSearchClick(String option, int widgetId)
	{
		return option != null
			&& "Search".equalsIgnoreCase(Text.removeTags(option).trim())
			&& widgetId >>> 16 == CLOG_INTERFACE;
	}

	private AccountType getLocalAccountType()
	{
		return AccountType.fromRuneLiteVarbit(client.getVarbitValue(VarbitID.IRONMAN));
	}

	/** The panel's status row speaks for the publication flow; the coordinator already lands on the EDT. */
	private PublicationCoordinator.Feedback panelFeedback()
	{
		return new PublicationCoordinator.Feedback()
		{
			@Override
			public void showSyncProgress(boolean manual, String text, boolean autoClear)
			{
				panel.showSyncProgress(manual, text, autoClear);
			}

			@Override
			public void showSyncResult(boolean manual, boolean ok, String message)
			{
				panel.showSyncResult(manual, ok, message);
			}

			@Override
			public void showCharacterPublishStatus(String text, boolean ok, boolean autoClear, String failureMessage)
			{
				panel.showCharacterPublishStatus(text, ok, autoClear, failureMessage);
			}
		};
	}

	private BufferedImage getIcon()
	{
		return KillClogIcons.pluginIconOrCollectionLog(itemManager);
	}
}
