package com.killclog;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
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
		"combat achievements", "lookup", "player lookup", "hiscores replacement", "leaderboard", "profile",
		"ehb", "pets", "raids", "clues", "stats"},
	internalName = "kill-clog",
	legacyDataDirectory = "kill-clog"
)
public class KillClogPlugin extends Plugin
{
	private static final int CLOG_SEARCH_TOGGLE_SCRIPT = 4084;
	private static final String RUNEPROFILE_PLUGIN_NAME = "RuneProfile";

	/** Config keys whose changes require rebuilding the right-click lookup menu entry. */
	private static final java.util.Set<String> MENU_CONFIG_KEYS = java.util.Set.of(
		"playerMenuLookup", "menuLabel", "menuOnPlayers", "menuOnFriendsList",
		"menuOnIgnoreList", "menuOnClanList", "menuOnGuestClanList",
		"menuOnChatChannels", "menuOnChat", "menuOnPrivateMessages", "menuOnGroupIronman");

	@Inject
	private Client client;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private KillClogConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	// RuneLite injects every installed plugin, turned on or not, before it decides whether to start it: the panel
	// is built in startUp, so a plugin left off builds nothing and asks no site for anything.
	@Inject
	private Provider<KillClogPanel> panelProvider;
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
	private SpriteManager spriteManager;

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
	private PlayerClans playerClans;
	// Who is logged in, decided in this one place; the stores, the panel and the publication flow follow it.
	private final SessionTracker sessions = new SessionTracker();
	@Inject
	private Gson gson;
	// Opens a League's own store; a test seam.
	private java.util.function.Function<String, LocalClogCache> leagueCacheFactory = id ->
	{
		Filepath folder = localClogCache.folder().getNow(null);
		return new LocalClogCache(gson, folder == null ? null : folder.join("leagues", id));
	};
	// The store the collection log walk started with; a different one means start over.
	private LocalClogCache walkCache;
	private final WorldSession world = new WorldSession(
		() -> client.getGameState() == GameState.LOGGED_IN
			? GameMode.of(client.getWorldType(), killclogService.activeLeague()) : null,
		() -> killclogService.activeLeague(), () -> localClogCache, id -> leagueCacheFactory.apply(id),
		store ->
		{
			store.setFirstPartyChangedListener(() -> firstPartyChanged(store));
			store.serve(sessions.current());
		});
	private final AdvLogPbs.Watch advLog = new AdvLogPbs.Watch();
	// The account takes up its own log once per login and per store, latched when the open store says so (its folder
	// and the account's log arrive on later ticks than the login).
	private boolean accountActivated;
	// Whether this login has been on the main game: the main store keeps it from then on (serveMainStore).
	private boolean mainReached;

	private final ClogSessionState sessionState = new ClogSessionState();
	private final ClogIndex clogIndex = new ClogIndex();
	private final LiveClogSync liveClogSync = new LiveClogSync();
	private final ManualClogSync manualClogSync = new ManualClogSync();
	private final LocalCaReader localCaReader = new LocalCaReader();
	private final CaCatalog caCatalog = new CaCatalog();
	private final ClogLookupMenu lookupMenu = new ClogLookupMenu();
	private PublicationCoordinator publication;
	private SyncSettings syncSettings;
	private ClogPbs clogPbs;
	private KillLog killLog;

	@Provides
	KillClogConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KillClogConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Saved settings take their current shape before anything reads them, the panel first.
		ConfigMigrations.run(configManager);
		// Built once: RuneLite reuses this instance across disable and enable, and the publication flow keeps it.
		if (panel == null)
		{
			panel = panelProvider.get();
			clientThread.invokeLater(() -> CardSprites.load(client, spriteManager));
		}
		// RuneLite moves the 2.4 folder in on first use. The store opens it on its
		// own disk thread; a folder that cannot open keeps this session in memory.
		localClogCache.open(this::getPluginDirectory);
		localClogCache.folder().thenAccept(folder -> localCaCache.open(folder == null ? null : folder.join("ca")));

		navButton = NavigationButton.builder()
			.tooltip("Kill Clog")
			.icon(getIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		panel.setNameAutocompleter(nameAutocompleter);
		panel.setClogIndex(clogIndex);
		playerClans = new PlayerClans(client, chatIconManager, runeProfileService, world::mainSettled, config::shareClan);
		panel.setClans(playerClans);
		syncService.setClans(playerClans);
		hiscoreService.setHardcoreTable(killclogService::hardcoreHiscores);

		lookupMenu.start(config, menuManager);

		// One coordinator for the life of this plugin instance: RuneLite reuses
		// the instance across disable and enable, and the sync gate, its queued
		// intent and a request still in the air must survive that, or a re-enabled
		// plugin would run a second sync beside the old one.
		if (publication == null)
		{
			publication = new PublicationCoordinator(config, configManager, clientThread, executor,
				sessions, syncService, profileAppearanceService, chatNotifier, panel,
				() -> world.mainSettled() ? getLocalAccountType() : null, world::mode,
				() -> world.hardcoreXp(client.getWorldType(), client::getOverallExperience), world::cacheFor,
				killclogService::leagueProfileType);
			sessions.addListener(this::sessionChanged);
		}
		syncSettings = new SyncSettings(config, configManager);
		clogPbs = new ClogPbs(configManager);
		killLog = new KillLog(configManager);
		syncSettings.enforce();
		panel.setKillclogSyncHandler(publication::manualSync);
		panel.setCharacterPublishHandler(publication::publishCharacter);
		panel.setCharacterDeleteHandler(publication::deleteCharacter);
		panel.setFirstPartyLockedHandler(() -> clientThread.invokeLater(() -> chatNotifier.send(ChatNotice.SYNC_HELP,
			"Kill Clog syncs from your first Collection Log item. Open your Collection Log so Kill Clog can read it:"
				+ " the chalice and the character button unlock once it holds an item.")));
		panel.setSelfPbs(this::selfPbs);
		// The first tick hands the panel and chat this world's game again.
		world.showAgain();
		panel.setSyncArrowEnabled(config.killclogSync());
		panel.setCharacterPublishEnabled(publication.characterPublishingEnabled());
		// The sync trigger lives at the data seam: any path that lands a
		// first-party observation (bulk page capture, Collection Log Search,
		// live unlock) schedules a debounced push.
		localClogCache.setFirstPartyChangedListener(() -> firstPartyChanged(localClogCache));

		kclogCommand.setClogIndex(clogIndex);
		syncService.setClogIndex(clogIndex);
		killclogService.setClogIndex(clogIndex);
		clogService.setClogIndex(clogIndex);
		localCaCache.setCaCatalog(caCatalog);
		runeProfileService.setCaCatalog(caCatalog);
		kclogCommand.commands().forEach(chatCommandManager::registerCommandAsync);

		// If installed mid-session, run login init on the client thread
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				sessions.update(client);
				enterLoggedInState(false);
			});
		}
		else if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			// The game's Collection Log is readable at the login screen: lookups made there open every page.
			clientThread.invokeLater(() ->
			{
				clogIndex.ensureParsed(client, itemManager);
			});
		}

		log.debug("Kill Clog plugin started");
	}

	@Override
	protected void shutDown()
	{
		// The real appearance the game last sent is kept, like the other data, so a re-enable mid-login can publish
		// without a gear change: logout, a hop or a lost connection drops it, and a publish checks it against the
		// worn gear.
		clientToolbar.removeNavigation(navButton);
		lookupMenu.stop(config, menuManager);
		kclogCommand.commands().keySet().forEach(chatCommandManager::unregisterCommand);
		localClogCache.shutdown();
		world.close();
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
		// The login dies with the plugin: a surviving one would carry the old account into whoever logs in next.
		sessions.end();
		log.debug("Kill Clog plugin stopped");
	}

	private void enterLoggedInState(boolean requestLocalReads)
	{
		if (requestLocalReads)
		{
			sessionState.requestLocalReads();
		}

		killclogService.refreshIndex();
		Session session = sessions.current();
		LocalClogCache cache = world.captureCache();
		if (session.loggedIn() && cache != null)
		{
			String name = session.getName();
			AccountType acctType = getLocalAccountType();
			boolean localClogReady = cache.serve(session);
			SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			if (!requestLocalReads)
			{
				sessionState.requestLocalReads();
			}
			if (localClogReady)
			{
				ownLogServing(cache, name);
			}
			else
			{
				panel.setFirstParty(PanelStatusRow.FirstParty.HIDDEN);
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
	 * True-live total bump with no chat dependency: the client pushes the
	 * collection log counts as varps, so any unlock (and the login flood)
	 * moves them regardless of the player's notification settings. Upward
	 * only; full log refresh stays the downward authority. Runs on the client
	 * thread (varbit events and enterLoggedInState both arrive there).
	 */
	private void reconcileClogTotalsFromVarps()
	{
		String name = sessions.current().getName();
		if (!world.mainSettled() || name == null)
		{
			return;
		}
		if (!localClogCache.hasDataFor(name))
		{
			return;
		}
		int obtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (localClogCache.updateTotalsUpward(name, obtained, total))
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

	/**
	 * The store serves this login's own log: the chalice and the character button say whether it holds one, and a log
	 * that does is pushed once. That catch-up closes a hole: a debounced push that fired after logout (or mid world-hop)
	 * aborted with nothing to relaunch it, so a session's last capture stayed unpublished; the server merge no-ops when
	 * nothing changed.
	 */
	private void ownLogServing(LocalClogCache cache, String name)
	{
		boolean hasLocalClog = cache.hasFirstPartyDataFor(name);
		panel.setFirstParty(firstParty(hasLocalClog));
		if (hasLocalClog)
		{
			publication.scheduleAutomaticSync();
		}
	}

	/**
	 * The main store serves this login once it has been on the main game, and while its world's game is still unknown;
	 * a League world, even one learned a few ticks after the login (the running League arrives with the sync index),
	 * leaves it serving no one until a hop reaches the main game. So looking yourself up in Main game stats there reads
	 * the providers until your own main log is here, and a hop from the main game to a League keeps it, as 2.6.6 does.
	 * Asked again whenever the login or its world's game changes.
	 */
	private void serveMainStore()
	{
		String game = world.mode();
		mainReached |= GameMode.MAIN.equals(game);
		Session now = sessions.current();
		localClogCache.serve(mainReached || game == null ? now : Session.none(now.getNumber()));
	}

	/**
	 * Who is logged in changed: the stores take up the new login (or none), the previous account's hiscore row is
	 * refetched next time, and work from the previous login stands down. On the client thread, before anything else
	 * sees the new session.
	 */
	private void sessionChanged(Session previous, Session now)
	{
		mainReached = false;
		serveMainStore();
		world.serve(now);
		localCaCache.serve(now);
		accountActivated = false;
		if (previous.loggedIn())
		{
			hiscoreService.markDirty(previous.getName());
			publication.cancelCharacterPublish();
			publication.cancelSync();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		sessions.update(client);
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			profileAppearanceService.clearOriginalAppearance();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			accountActivated = false;
			reloadCardSprites();
			clogService.clearTempleFailures();
			runeProfileService.clearFailures();
			killclogService.clearFailures();
			enterLoggedInState(true);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			reloadCardSprites();
			// Logged out, your name is anyone's: its lookup reads every site, character and clan included.
			SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(null, null));
			// The sync affordance belongs to the account that just ended. Hiding
			// it also fences a success callback queued during the logout handoff.
			panel.setFirstParty(PanelStatusRow.FirstParty.HIDDEN);
			panel.resetSyncFeedback();
			manualClogSync.reset();
			// Read the game's own structure again at once, for lookups made before the next login.
			clogIndex.clear();
			clogIndex.ensureParsed(client, itemManager);
			sessionState.resetAutoLookupSession();
			liveClogSync.resetFirstSyncWarning();
			nameAutocompleter.clearClientSnapshot();
			playerClans.clearClientSnapshot();
			killLog.reset();
			// The login itself ended in the tracker, first: its stores, its account, its hiscore row and its work.
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
			unsettleWorld();
		}

		// The owner claim is scoped to one POH visit, exactly as vanilla
		// scopes it: any region load or hop drops it, so a friend's log can
		// never linger and gate (or worse, misattribute) a later harvest.
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING)
		{
			advLog.dropOwner();
		}
	}

	// Jagex republishes the local player's hiscore row on logout and world
	// hop; the cached self-row predates it, so the next self-search refetches.
	private void markLocalHiscoresDirty()
	{
		String name = sessions.current().getName();
		if (name != null)
		{
			hiscoreService.markDirty(name);
		}
	}

	// Live collection-log unlock messages update local cache immediately.
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (config.showChatEmojis())
		{
			chatEmoji.rewrite(event);
		}

		// !kc <item name> provenance reveal. Boss arguments stay with the
		// built-in plugin's own "!kc" registration; the handler ignores them.
		LocalClogCache kcCache = world.captureCache();
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

		killLog.chat(client, event.getType(), event.getMessage(), client.getTickCount());

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
			}
		}

		// Players whose notification setting is popup-only never produce the
		// personal game message; their own clan broadcast is the only chat
		// signal of the unlock, and it carries the fresh obtained/total pair.
		if (event.getType() == ChatMessageType.CLAN_MESSAGE)
		{
			ClogUnlockParser.BroadcastUnlock broadcast =
				ClogUnlockParser.parseClanBroadcast(event.getMessage());
			if (broadcast != null && RsnInputPolicy.sameName(broadcast.playerName, sessions.current().getName()))
			{
				clientThread.invokeLater(() -> handleCollectionLogUnlock(
					broadcast.itemName, broadcast.obtained, broadcast.total));
			}
		}
	}

	private void handleCollectionLogUnlock(String itemName, int broadcastObtained, int broadcastTotal)
	{
		LocalClogCache cache = world.captureCache();
		if (cache == null)
		{
			return;
		}
		liveClogSync.handleUnlock(itemName, broadcastObtained, broadcastTotal, client,
			itemManager, clogIndex, cache, chatNotifier,
			panel::onBulkCaptureComplete, world.settled());
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

		if (event.getVarpId() == VarPlayerID.COLLECTION_COUNT || event.getVarpId() == VarPlayerID.COLLECTION_COUNT_MAX)
		{
			reconcileClogTotalsFromVarps();
		}
		clogPbs.varpChanged(client, event.getVarpId());
		if (event.getVarbitId() == VarbitID.IRONMAN)
		{
			unsettleWorld();
		}
	}

	/** Until the world settles again the game hasn't said your clan, so a sync leaves it out (2.6.3). */
	private void unsettleWorld()
	{
		world.unsettle();
		if (playerClans != null)
		{
			playerClans.clearClientSnapshot();
		}
	}

	/** Read per-tier CA completed counts from game varbits and persist them for the active player. */
	private boolean captureLocalCa()
	{
		if (!world.mainSettled())
		{
			return false;
		}
		caCatalog.capture(client);
		return localCaReader.capture(client, sessions.current(), localCaCache);
	}

	private boolean isRuneProfileActive()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(plugin -> RUNEPROFILE_PLUGIN_NAME.equals(plugin.getName())
				&& pluginManager.isPluginActive(plugin));
	}

	/** A logged-in account whose own log has loaded: locked until it holds an item. */
	private static PanelStatusRow.FirstParty firstParty(boolean hasLocalClog)
	{
		return hasLocalClog ? PanelStatusRow.FirstParty.READY : PanelStatusRow.FirstParty.LOCKED;
	}

	private void firstPartyChanged(LocalClogCache cache)
	{
		// A capture only counts once the payload is genuinely non-empty:
		// an empty first walk must neither reveal the chalice nor
		// schedule a push that would fail with nothing to send.
		if (cache.hasFirstPartyDataForActive())
		{
			panel.setFirstParty(PanelStatusRow.FirstParty.READY);
			publication.scheduleAutomaticSync();
		}
	}

	/** A League world reads its League in the panel and chat; every other world, or none, the main game. */
	private void followWorld()
	{
		world.follow((league, leagueLog, active) ->
		{
			serveMainStore();
			kclogCommand.readLeague(league, leagueLog);
			SwingUtilities.invokeLater(() -> panel.followWorld(league, leagueLog, active));
		});
	}

	/**
	 * Your PBs for panel bosses in one game: the main game's profiles, or the League's announced one. RuneLite lists
	 * every saved profile on each ask, so the grid asks once for all its bosses.
	 */
	private java.util.function.UnaryOperator<String> selfPbs(String league)
	{
		java.util.List<String> keys = PersonalBests.profileKeys(configManager.getRSProfiles(), sessions.current().getAccountHash(),
			league == null ? RuneScapeProfileType.STANDARD.name() : killclogService.leagueProfileType(league));
		PersonalBests pbs = new PersonalBests(configManager, keys);
		return pbs::pbText;
	}

	/**
	 * The tick, in the order its steps depend on: who is logged in, the world settling (the counters and CA tiers it
	 * held back), the account taking up its own log, that log's reset and name-change lines, the account type and CA
	 * reads, the automatic lookup, which reads what every step before it decided, then the log walk and the Adventure
	 * Log. One handler on purpose: split across subscribers, the order would be a priority on each of them
	 * (PluginPublicationCharacterizationTest.theTicksStepsRunInTheOrderTheyDependOn).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The names only feed the search box's autofill, so a refresh every three seconds is fresh enough.
		if (client.getTickCount() % 5 == 0)
		{
			nameAutocompleter.refreshClientSnapshot();
		}
		playerClans.refreshClientSnapshot();
		sessions.update(client);
		boolean settledNow = world.tick();
		if (settledNow)
		{
			// Counters and CA tiers read before the world settled were skipped: take them now.
			reconcileClogTotalsFromVarps();
			sessionState.requestCaRead();
		}
		followWorld();
		long hardcoreXp = world.hardcoreXp(client.getWorldType(), client::getOverallExperience);

		// The account takes up its own log, following any name change (the
		// server migrates its own copy on the next sync). Memory only; the
		// store says no until its folder has loaded, and this retries.
		LocalClogCache activeCache = world.captureCache();
		Session session = sessions.current();
		if (!accountActivated && activeCache != null && activeCache.serve(session))
		{
			accountActivated = true;
			ownLogServing(activeCache, session.getName());
		}
		ownLogLines(session, hardcoreXp, settledNow);
		recheckAccountType(session);
		if (sessionState.pendingCaRead())
		{
			sessionState.setPendingCaRead(!captureLocalCa());
		}
		autoLookup(session);
		walkLog();
		advLog.onTick(client, configManager);
	}

	/** A Hardcore Worlds reset starts your log over, and a name change takes it along; each says so in chat. */
	private void ownLogLines(Session session, long hardcoreXp, boolean settledNow)
	{
		String hardcoreName = session.getName();
		if (hardcoreXp > 0 && localClogCache.startOverOnReset(hardcoreName, hardcoreXp, settledNow))
		{
			chatNotifier.send(ChatNotice.SYNC_RESULT, "Kill Clog started your collection log over for your new run.");
			ownLogServing(localClogCache, hardcoreName);
			SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(hardcoreName));
		}
		String previousName = localClogCache.consumeRenameNotice();
		if (previousName != null)
		{
			chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Kill Clog followed your name change from '" + previousName
				+ "' - your collection log came along.");
			String renameName = session.getName();
			if (renameName != null)
			{
				SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(renameName));
			}
		}
	}

	private void recheckAccountType(Session session)
	{
		if (sessionState.pendingAcctTypeRecheck())
		{
			String name = session.getName();
			if (name != null)
			{
				sessionState.clearAcctTypeRecheck();
				AccountType acctType = getLocalAccountType();
				SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			}
		}
	}

	/** Your own profile looks itself up once a login, when nobody else's is showing. */
	private void autoLookup(Session session)
	{
		if (sessionState.pendingAutoLookup())
		{
			String name = session.getName();
			if (name != null)
			{
				// A world with no known game still shows the main log, as it always has.
				LocalClogCache cache = world.captureCache() != null ? world.captureCache() : localClogCache;
				if (cache.serve(session))
				{
					sessionState.markAutoLookupStarted();
					// The login transition often fires before the player's name is
					// readable. This tick path waits for identity arbitration too,
					// so a resident name-slot file can never become the self view.
					panel.setFirstParty(firstParty(cache.hasFirstPartyDataFor(name)));
					captureLocalCa();
					AccountType acctType = getLocalAccountType();
					SwingUtilities.invokeLater(() ->
					{
						panel.setLoggedInPlayer(name, acctType);

						// Do not overwrite the user's research. LOGGED_IN fires on
						// every world hop, so skip auto-lookup when viewing someone else.
						String displayed = panel.getDisplayedRsn();
						if (displayed != null && !RsnInputPolicy.sameName(displayed, name))
						{
							return;
						}

						panel.setPlayerName(name);
						panel.doLookup();
					});
				}
			}
		}
	}

	/** The Collection Log walk, started over when the world's store changes under it. */
	private void walkLog()
	{
		LocalClogCache cache = world.captureCache();
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
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			// Request a full Search walk after the player's log has initialized.
			// Ordinary visible-category scripts cannot establish a full capture.
			manualClogSync.onCollectionLogOpened(client);
		}

		advLog.onWidgetLoaded(event.getGroupId());
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

	// The log drew a page: its Personal Best line, the game's own record, when it has one.
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == net.runelite.api.ScriptID.COLLECTION_DRAW_LIST)
		{
			clogPbs.pageDrawn(client);
		}
	}

	private boolean isCollectionLogSearchScript(int scriptId, ScriptEvent script)
	{
		if (scriptId != CLOG_SEARCH_TOGGLE_SCRIPT)
		{
			return false;
		}
		Widget source = script.getSource();
		Object[] args = script.getArguments();
		if (source == null || source.getId() >>> 16 != InterfaceID.COLLECTION || source.isHidden()
			|| args == null || args.length < 2 || !(args[1] instanceof Integer) || (int) args[1] != 0)
		{
			return false;
		}
		// 4084 calls toggle procedure 4085: an already visible Search or a
		// force-close argument closes it. Neither is a new full-log capture.
		Widget search = client.getWidget(InterfaceID.Collection.CONTENT_SEARCH);
		return search != null && search.isSelfHidden();
	}

	/** A pack or theme that replaced a sprite the cards wear redraws them in it, however long it took to apply. */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (CardSprites.changed(client))
		{
			reloadCardSprites();
		}
	}

	/** The cards' sprites read again (a pack's where one replaces them), then their colours and the character icon. */
	private void reloadCardSprites()
	{
		CardSprites.load(client, spriteManager);
		SwingUtilities.invokeLater(panel::reloadCardLook);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
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
			// The opt-in pushes the log of the login and era it was made in: a logout can land while this runs.
			int optedInEra = publication.syncEra();
			Session optedIn = sessions.current();
			panel.setSyncArrowEnabled(config.killclogSync());
			if (config.killclogSync())
			{
				syncSettings.syncTurnedOn();
				characterPublishFollowsSettings();
				// Opting in mid-session pushes the already-captured log right
				// away; nothing else fires until the next capture or unlock.
				publication.pushNow(optedIn, optedInEra, true);
			}
			else
			{
				syncSettings.enforce();
				characterPublishFollowsSettings();
				publication.cancelSync();
			}
		}
		else if ("silentAutomaticSync".equals(event.getKey()))
		{
			panel.refreshSyncFeedbackSettings();
		}
		else if ("automaticSync".equals(event.getKey()))
		{
			syncSettings.enforce();
			if (!config.automaticSync())
			{
				publication.cancelAutomaticSync();
			}
		}
		else if ("characterModel".equals(event.getKey()))
		{
			syncSettings.enforce();
			characterPublishFollowsSettings();
		}
		else if ("shareClan".equals(event.getKey()) && config.killclogSync())
		{
			// Share Clan takes effect at once, either way: off, killclog.com stops naming the clan now rather than at
			// the next sync, which with Automatic Sync off waits for a press. The push is the opt-in's, on this login.
			publication.pushNow(sessions.current(), publication.syncEra(), true);
		}

		SwingUtilities.invokeLater(() -> panel.onConfigChanged(event.getKey()));
	}

	/** The character button follows sync and the character setting together; turning either off withdraws it. */
	private void characterPublishFollowsSettings()
	{
		boolean enabled = publication.characterPublishingEnabled();
		panel.setCharacterPublishEnabled(enabled);
		if (!enabled)
		{
			publication.cancelCharacterPublish();
		}
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
		LocalClogCache cache = world.captureCache();
		if (cache != null)
		{
			manualClogSync.onCollectionLogSearch(client, clogIndex, cache, chatNotifier);
		}
	}

	static boolean isCollectionLogSearchClick(String option, int widgetId)
	{
		return option != null
			&& "Search".equalsIgnoreCase(Text.removeTags(option).trim())
			&& widgetId >>> 16 == InterfaceID.COLLECTION;
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
