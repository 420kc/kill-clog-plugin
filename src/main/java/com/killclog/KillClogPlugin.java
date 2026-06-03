package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kill Clog",
	description = "PvM/Clog-Focused HiScores Replacement",
	tags = {"boss", "kc", "kill count", "collection log", "pvm", "hiscore", "ironman"}
)
public class KillClogPlugin extends Plugin
{
	private String menuOption = "Kill Clog";

	// OSRS player right-click menu reserves indexes 4-7 for plugins (4 slots total)
	private static final int FIRST_PLUGIN_PLAYER_SLOT = 4;
	private static final int LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE = 8;

	// Bulk clog capture IDs.
	private static final int CLOG_ITEM_SCRIPT = 4100;
	private static final int CLOG_SEARCH_WIDGET = 40697932;  // (621 << 16) | 76
	private static final int ENUM_CLOG_TABS = 2102;
	private static final int VARP_CLOG_OBTAINED = 2943;
	private static final int VARP_CLOG_TOTAL = 2944;
	private static final int PARAM_SUBTAB_ENUM = 683;
	private static final int PARAM_CATEGORY_NAME = 689;
	private static final int PARAM_CATEGORY_ITEMS = 690;

	static final int CLOG_INTERFACE = 621;
	private static final int CLOG_HEADER_CHILD = 20;
	private static final int CLOG_ITEMS_CHILD = 37;
	private static final java.util.regex.Pattern OBTAINED_PATTERN =
		java.util.regex.Pattern.compile("(\\d+)/(\\d+)");

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
	private ClogService clogService;

	@Inject
	private RuneProfileService runeProfileService;

	@Inject
	private LocalClogCache localClogCache;

	@Inject
	private LocalCaCache localCaCache;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private KillClogChatCommand kclogCommand;

	private NavigationButton navButton;
	private boolean pendingAutoLookup;
	// VarbitID.IRONMAN can read 0 on the LOGGED_IN tick, so re-read one tick later.
	private boolean pendingAcctTypeRecheck;

	// CA varbits, like account type, are read one tick after LOGGED_IN.
	private boolean pendingCaRead;
	// LOGGED_IN fires on world hops and region loads. Auto-lookup is gated per session.
	private boolean hasAutoLookedUpThisSession;
	private boolean playerMenuSlotWarned;

	// Autosync refreshes self-lookup data when the player sends chat.
	// Incoming PMs and server messages do not prove local player activity.
	private static final long AUTOSYNC_INTERVAL_MS = 5 * 60 * 1000;
	private static final Set<ChatMessageType> AUTOSYNC_CHAT_TYPES = EnumSet.of(
		ChatMessageType.PUBLICCHAT,
		ChatMessageType.FRIENDSCHAT,
		ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.AUTOTYPER
	);
	private long lastAutoSyncMs;

	// Enum-derived clog mappings (parsed once per session)
	private Map<String, List<Integer>> enumCategoryMap;
	private Map<Integer, List<String>> itemToCategoryKeys;
	private boolean enumsParsed;

	// Bulk clog capture state.
	private final BulkCaptureState bulk = new BulkCaptureState();

	private static class BulkCaptureState
	{
		boolean active;
		int finalizeTickCount = -1;
		int clogCount = -1;
		int clogTotal = -1;
		final List<ClogResult.ClogItem> obtained = new ArrayList<>();

		// Buffered category read captured before bulk has cache data to merge into.
		String bufferedCategoryKey;
		String bufferedCategoryName;
		List<Integer> bufferedCategoryItems;
		List<ClogResult.ClogItem> bufferedCategoryObtained;

		void reset()
		{
			active = false;
			finalizeTickCount = -1;
			clogCount = -1;
			clogTotal = -1;
			obtained.clear();
			bufferedCategoryKey = null;
			bufferedCategoryName = null;
			bufferedCategoryItems = null;
			bufferedCategoryObtained = null;
		}
	}

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

		menuOption = config.menuLabel().getLabel();
		setPlayerMenuItemEnabled(config.playerMenuLookup());

		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND, kclogCommand::handle);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_MISSING, kclogCommand::handleMissing);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_THIRD_AGE, kclogCommand::handleThirdAge);
		chatCommandManager.registerCommandAsync(KillClogChatCommand.COMMAND_GILDED, kclogCommand::handleGilded);

		// If installed mid-session, run login init on the client thread
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				Player local = client.getLocalPlayer();
				if (local != null && local.getName() != null)
				{
					String name = local.getName();
					AccountType acctType = getLocalAccountType();
					localClogCache.setActivePlayer(name);
					localCaCache.setActivePlayer(name);
					SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
					pendingAcctTypeRecheck = true;
					pendingCaRead = true;
				}

				nameAutocompleter.refreshClientSnapshot();
				loadGimBadges();

				if (!enumsParsed)
				{
					parseClogEnums();
				}

				if (config.autoLookupOnLogin() && !hasAutoLookedUpThisSession)
				{
					pendingAutoLookup = true;
				}

				warnIfPlayerMenuSlotUnavailable();
			});
		}

		log.debug("Kill Clog plugin started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(clogButtonOverlay);
		mouseManager.unregisterMouseListener(clogButtonOverlay);
		setPlayerMenuItemEnabled(false);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_MISSING);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_THIRD_AGE);
		chatCommandManager.unregisterCommand(KillClogChatCommand.COMMAND_GILDED);
		kclogCommand.clear();
		SwingUtilities.invokeLater(() -> panel.shutdown());
		localClogCache.shutdown();
		localCaCache.shutdown();
		resetBulkCapture();
		enumsParsed = false;
		playerMenuSlotWarned = false;
		pendingAutoLookup = false;
		pendingAcctTypeRecheck = false;
		pendingCaRead = false;
		hasAutoLookedUpThisSession = false;
		nameAutocompleter.clearClientSnapshot();
		log.debug("Kill Clog plugin stopped");
	}

	private void setPlayerMenuItemEnabled(boolean enabled)
	{
		if (enabled && config.menuOnPlayers())
		{
			menuManager.get().addPlayerMenuItem(menuOption);
		}
		else
		{
			menuManager.get().removePlayerMenuItem(menuOption);
		}
	}

	/**
	 * OSRS reserves only 4 plugin slots (indexes 4-7) on the player right-click menu.
	 * If other plugins claim them all first, our addPlayerMenuItem call silently no-ops.
	 * Warn once per session so the user knows to use the side panel or chat right-click instead.
	 */
	private void warnIfPlayerMenuSlotUnavailable()
	{
		if (playerMenuSlotWarned || !config.playerMenuLookup() || !config.menuOnPlayers())
		{
			return;
		}

		String[] options = client.getPlayerOptions();
		if (options == null || options.length < LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE)
		{
			return;
		}
		for (int i = FIRST_PLUGIN_PLAYER_SLOT; i < LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE; i++)
		{
			if (menuOption.equals(options[i]))
			{
				return;
			}
		}

		playerMenuSlotWarned = true;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=4caf6e>Kill Clog:</col> Right-click menu is full. "
				+ "Use the side panel or right-click names in chat instead.",
			null);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clogService.clearTempleFailures();
			runeProfileService.clearFailures();
			pendingAcctTypeRecheck = true;
			pendingCaRead = true;
			nameAutocompleter.refreshClientSnapshot();

			// Always track logged-in player for local clog cache
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				String name = local.getName();
				localClogCache.setActivePlayer(name);
				localCaCache.setActivePlayer(name);
				AccountType acctType = getLocalAccountType();
				SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			}

			loadGimBadges();

			if (!enumsParsed)
			{
				parseClogEnums();
			}

			if (config.autoLookupOnLogin() && !hasAutoLookedUpThisSession)
			{
				pendingAutoLookup = true;
			}

			warnIfPlayerMenuSlotUnavailable();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			resetBulkCapture();
			enumsParsed = false;
			hasAutoLookedUpThisSession = false;
			nameAutocompleter.clearClientSnapshot();
		}
	}

	// Autosync self lookups after local player chat, but only while self is displayed.
	// Service caches keep refreshes cheap; the interval gate is about UI churn.
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!AUTOSYNC_CHAT_TYPES.contains(event.getType()))
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		String localName = local.getName();
		String sender = event.getName();
		if (sender == null || !sender.equalsIgnoreCase(localName))
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastAutoSyncMs < AUTOSYNC_INTERVAL_MS)
		{
			return;
		}

		String displayed = panel.getDisplayedRsn();
		if (displayed == null || !displayed.equalsIgnoreCase(localName))
		{
			return;
		}

		lastAutoSyncMs = now;
		SwingUtilities.invokeLater(() ->
		{
			panel.setPlayerName(localName);
			panel.doLookup();
		});
	}

	// Keep local CA current when a task completes mid-session.
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (id == -1 || id == VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER)
		{
			pendingCaRead = true;
		}
	}

	/** Read per-tier CA completed counts from game varbits and persist them for the active player. */
	private boolean captureLocalCa()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return false;
		}
		localCaCache.setActivePlayer(local.getName());
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		completed.put(CombatAchievementTier.EASY, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY));
		completed.put(CombatAchievementTier.MEDIUM, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM));
		completed.put(CombatAchievementTier.HARD, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD));
		completed.put(CombatAchievementTier.ELITE, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE));
		completed.put(CombatAchievementTier.MASTER, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER));
		completed.put(CombatAchievementTier.GRANDMASTER, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER));
		localCaCache.cacheResult(local.getName(), completed);
		return true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		nameAutocompleter.refreshClientSnapshot();

		if (pendingAcctTypeRecheck)
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				pendingAcctTypeRecheck = false;
				String name = local.getName();
				AccountType acctType = getLocalAccountType();
				SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			}
		}

		if (pendingCaRead)
		{
			pendingCaRead = !captureLocalCa();
		}

		if (pendingAutoLookup)
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				pendingAutoLookup = false;
				hasAutoLookedUpThisSession = true;
				String name = local.getName();
				localClogCache.setActivePlayer(name);
				localCaCache.setActivePlayer(name);
				captureLocalCa();
				AccountType acctType = getLocalAccountType();
				SwingUtilities.invokeLater(() ->
				{
					panel.setLoggedInPlayer(name, acctType);

					// Do not overwrite the user's research. LOGGED_IN fires on
					// every world hop, so skip auto-lookup when they're viewing someone else
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

		if (bulk.active && bulk.finalizeTickCount > 0
			&& client.getTickCount() >= bulk.finalizeTickCount)
		{
			finalizeBulkCapture();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != CLOG_ITEM_SCRIPT || !bulk.active)
		{
			return;
		}

		if (event.getScriptEvent() == null || event.getScriptEvent().getArguments() == null)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args.length < 3)
		{
			return;
		}

		if (!(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}
		int itemId = (int) args[1];
		int count = (int) args[2];

		bulk.obtained.add(new ClogResult.ClogItem(itemId, count, null));
		bulk.finalizeTickCount = client.getTickCount() + 3;
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		// String check; FourTwentyKcPlugin lives in a separate plugin.
		if (event.getPlugin().getClass().getSimpleName().equals("FourTwentyKcPlugin"))
		{
			SwingUtilities.invokeLater(() -> panel.setFourTwentyVisible(event.isLoaded()));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("killclog"))
		{
			return;
		}

		if (event.getKey().equals("playerMenuLookup")
			|| event.getKey().equals("menuLabel")
			|| event.getKey().equals("menuOnPlayers")
			|| event.getKey().equals("menuOnFriendsList")
			|| event.getKey().equals("menuOnIgnoreList")
			|| event.getKey().equals("menuOnClanList")
			|| event.getKey().equals("menuOnGuestClanList")
			|| event.getKey().equals("menuOnChatChannels")
			|| event.getKey().equals("menuOnChat")
			|| event.getKey().equals("menuOnPrivateMessages")
			|| event.getKey().equals("menuOnGroupIronman"))
		{
			// Refresh the menu option when its label changes.
			menuManager.get().removePlayerMenuItem(menuOption);
			menuOption = config.menuLabel().getLabel();
			setPlayerMenuItemEnabled(config.playerMenuLookup());
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

	// Right-click menu option strings used to filter Kill Clog lookup entries.
	private static final String OPT_ADD_FRIEND = "Add friend";
	private static final String OPT_ADD_IGNORE = "Add ignore";
	private static final String OPT_REMOVE_FRIEND = "Remove friend";
	private static final String OPT_REMOVE_IGNORE = "Remove ignore";
	private static final String OPT_DELETE = "Delete";
	private static final String OPT_MESSAGE = "Message";

	private boolean isLookupEligible(int groupId, int componentId, String option)
	{
		// Clan player lists live in two different interfaces -- match by componentId, not groupId
		if (componentId == InterfaceID.ClansSidepanel.PLAYERLIST)
		{
			return config.menuOnClanList()
				&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
		}
		if (componentId == InterfaceID.ClansGuestSidepanel.PLAYERLIST)
		{
			return config.menuOnGuestClanList()
				&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
		}
		switch (groupId)
		{
			case InterfaceID.FRIENDS:
				return config.menuOnFriendsList() && OPT_DELETE.equals(option);
			case InterfaceID.IGNORE:
				return config.menuOnIgnoreList() && OPT_DELETE.equals(option);
			case InterfaceID.CHATCHANNEL_CURRENT:
				return config.menuOnChatChannels()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
			case InterfaceID.CHATBOX:
				return config.menuOnChat()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_MESSAGE.equals(option));
			case InterfaceID.PM_CHAT:
				return config.menuOnPrivateMessages()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_MESSAGE.equals(option));
			case InterfaceID.GIM_SIDEPANEL:
				return config.menuOnGroupIronman()
					&& (OPT_ADD_FRIEND.equals(option) || OPT_REMOVE_FRIEND.equals(option) || OPT_REMOVE_IGNORE.equals(option));
			default:
				return false;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.playerMenuLookup())
		{
			return;
		}

		if (event.getType() != MenuAction.CC_OP.getId()
			&& event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())
		{
			return;
		}

		int componentId = event.getActionParam1();
		int groupId = WidgetUtil.componentToInterface(componentId);

		if (isLookupEligible(groupId, componentId, event.getOption()))
		{
			String target = event.getTarget();
			if (target == null)
			{
				return;
			}
			String name = Text.toJagexName(Text.removeTags(target).trim());
			client.getMenu().createMenuEntry(-2)
				.setOption(menuOption)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(event.getIdentifier())
				.onClick(e -> openPanelAndLookup(name));
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER
			&& event.getMenuOption().equals(menuOption)
			&& config.menuOnPlayers())
		{
			Player player = event.getMenuEntry().getPlayer();
			if (player == null || player.getName() == null)
			{
				return;
			}

			String name = Text.toJagexName(player.getName());
			openPanelAndLookup(name);
		}
	}

	// Bulk clog capture.

	private void parseClogEnums()
	{
		try
		{
			enumCategoryMap = new HashMap<>();
			itemToCategoryKeys = new HashMap<>();

			EnumComposition tabs = client.getEnum(ENUM_CLOG_TABS);

			for (int tabKey : tabs.getKeys())
			{
				int tabStructId = tabs.getIntValue(tabKey);
				StructComposition tabStruct = client.getStructComposition(tabStructId);
				int subtabEnumId = tabStruct.getIntValue(PARAM_SUBTAB_ENUM);

				EnumComposition subtabs = client.getEnum(subtabEnumId);
				for (int subKey : subtabs.getKeys())
				{
					int catStructId = subtabs.getIntValue(subKey);
					StructComposition catStruct = client.getStructComposition(catStructId);

					String name = catStruct.getStringValue(PARAM_CATEGORY_NAME);
					int itemsEnumId = catStruct.getIntValue(PARAM_CATEGORY_ITEMS);

					if (name == null || itemsEnumId <= 0)
					{
						continue;
					}

					String categoryKey = ClogService.bossToCategory(name);

					EnumComposition itemsEnum = client.getEnum(itemsEnumId);
					List<Integer> itemIds = new ArrayList<>();
					for (int itemKey : itemsEnum.getKeys())
					{
						int itemId = itemsEnum.getIntValue(itemKey);
						itemIds.add(itemId);
						itemToCategoryKeys.computeIfAbsent(itemId, k -> new ArrayList<>())
							.add(categoryKey);
					}

					enumCategoryMap.put(categoryKey, itemIds);
				}
			}

			// Synthetic categories not in game cache
			injectSynthetic("mimic", new int[]{PanelData.THIRD_AGE_RING_ITEM_ID});
			injectSynthetic(PanelData.CLOG_THIRD_AGE, PanelData.THIRD_AGE_ITEMS);
			injectSynthetic(PanelData.CLOG_GILDED, PanelData.GILDED_ITEMS);

			enumsParsed = true;
			log.debug("Parsed clog enums: {} categories, {} items",
				enumCategoryMap.size(), itemToCategoryKeys.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to parse clog enums", e);
			enumsParsed = false;
		}
	}

	private void injectSynthetic(String key, int[] itemIds)
	{
		List<Integer> ids = new ArrayList<>(itemIds.length);
		for (int id : itemIds)
		{
			ids.add(id);
			itemToCategoryKeys.computeIfAbsent(id, k -> new ArrayList<>()).add(key);
		}
		enumCategoryMap.put(key, ids);
	}

	private void triggerBulkCapture()
	{
		if (!enumsParsed || bulk.active)
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		// Skip if local cache already exists for this player
		if (localClogCache.hasDataFor(local.getName()))
		{
			return;
		}

		bulk.obtained.clear();
		bulk.active = true;
		bulk.finalizeTickCount = -1;
		bulk.clogCount = client.getVarpValue(VARP_CLOG_OBTAINED);
		bulk.clogTotal = client.getVarpValue(VARP_CLOG_TOTAL);

		client.menuAction(-1, CLOG_SEARCH_WIDGET, MenuAction.CC_OP, 1, -1, "Search", null);
		client.menuAction(-1, CLOG_SEARCH_WIDGET, MenuAction.CC_OP, 1, -1, "Back", null);

		log.debug("Triggered bulk clog capture (game reports {} obtained)", bulk.clogCount);
	}

	private void finalizeBulkCapture()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			resetBulkCapture();
			return;
		}

		String name = local.getName();
		localClogCache.setActivePlayer(name);

		// Group obtained items by category.
		Map<String, List<ClogResult.ClogItem>> obtainedByCategory = new HashMap<>();

		// Include empty categories so cacheResult stores the full catalog.
		for (String cat : enumCategoryMap.keySet())
		{
			obtainedByCategory.put(cat, new ArrayList<>());
		}

		for (ClogResult.ClogItem item : bulk.obtained)
		{
			List<String> cats = itemToCategoryKeys.get(item.getId());
			if (cats != null)
			{
				for (String cat : cats)
				{
					obtainedByCategory.get(cat).add(item);
				}
			}
		}

		// Copy category item lists from enum data
		Map<String, List<Integer>> categoryItemsCopy = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : enumCategoryMap.entrySet())
		{
			categoryItemsCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}

		// Guard against partial captures (e.g., player closed clog mid-sync)
		if (bulk.clogCount > 0 && bulk.obtained.size() < bulk.clogCount / 2)
		{
			log.warn("Bulk capture incomplete: got {} of {} items, discarding",
				bulk.obtained.size(), bulk.clogCount);
			resetBulkCapture();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=4caf6e>Kill Clog:</col> Sync interrupted - open the collection log and try again.",
				null);
			return;
		}

		ClogResult result = new ClogResult(name, obtainedByCategory, categoryItemsCopy,
			new HashMap<>(), null, null);
		if (bulk.clogCount > 0)
		{
			result.setUniqueObtained(bulk.clogCount);
		}
		if (bulk.clogTotal > 0)
		{
			result.setUniqueTotal(bulk.clogTotal);
		}
		localClogCache.cacheResult(result);

		// Prefer the Jagex de-duped count (varp 2943) over the raw streamed count.
		// Some items appear in multiple clog categories, so bulk.obtained.size() can
		// exceed the player's actual unique obtained count.
		int displayCount = bulk.clogCount > 0 ? bulk.clogCount : bulk.obtained.size();
		log.debug("Bulk clog capture complete: {} items across {} categories for '{}' ({} streamed)",
			displayCount, enumCategoryMap.size(), name, bulk.obtained.size());

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=4caf6e>Kill Clog:</col> " + displayCount + " items synced to Kill Clog",
			null);

		// Merge the buffered category before reset (captured when clog first opened)
		String bufKey = bulk.bufferedCategoryKey;
		String bufName = bulk.bufferedCategoryName;
		List<Integer> bufItems = bulk.bufferedCategoryItems;
		List<ClogResult.ClogItem> bufObtained = bulk.bufferedCategoryObtained;
		resetBulkCapture();

		if (bufKey != null && bufItems != null)
		{
			localClogCache.mergeCategory(name, bufKey, bufItems, bufObtained);
			int buffObt = bufObtained != null ? bufObtained.size() : 0;
			int buffTotal = bufItems.size();
			log.debug("Merged buffered category '{}': {}/{} obtained",
				bufKey, buffObt, buffTotal);

			String displayName = bufName != null ? bufName : bufKey;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=4caf6e>Kill Clog:</col> Updated " + displayName
					+ " - " + buffObt + "/" + buffTotal + " items",
				null);
		}

		clogButtonOverlay.flashGreen();
		SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(name));
	}

	// Sync button.

	/**
	 * Called by ClogButtonOverlay on click.
	 * First click: buffers visible category + triggers bulk capture.
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

			captureVisibleCategory();

			if (!localClogCache.hasDataFor(local.getName()))
			{
				triggerBulkCapture();
			}
		});
	}

	private void captureVisibleCategory()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		// 621:20 getDynamicChildren(): [0]=category name, [1]="Obtained: x/y", [2]="Boss kills: n"
		Widget header = client.getWidget(CLOG_INTERFACE, CLOG_HEADER_CHILD);
		if (header == null)
		{
			return;
		}
		Widget[] headerKids = header.getDynamicChildren();
		if (headerKids == null || headerKids.length < 2)
		{
			return;
		}

		String headerText = (headerKids[0] != null) ? headerKids[0].getText() : null;
		if (headerText == null || headerText.isEmpty())
		{
			return;
		}

		String categoryName = Text.removeTags(headerText);
		String categoryKey = ClogService.bossToCategory(categoryName);

		// Parse "Obtained: x/y" from dynamic child [1]
		int obtainedCount = -1;
		int totalCount = -1;
		if (headerKids.length >= 2 && headerKids[1] != null)
		{
			String obtainedText = Text.removeTags(headerKids[1].getText());
			if (obtainedText != null)
			{
				// Format: "Obtained: 6/9" or similar
				java.util.regex.Matcher m = OBTAINED_PATTERN.matcher(obtainedText);
				if (m.find())
				{
					obtainedCount = Integer.parseInt(m.group(1));
					totalCount = Integer.parseInt(m.group(2));
				}
			}
		}

		// Read items from widget
		Widget items = client.getWidget(CLOG_INTERFACE, CLOG_ITEMS_CHILD);
		if (items == null)
		{
			return;
		}

		Widget[] children = items.getChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		List<Integer> allItemIds = new ArrayList<>();
		List<ClogResult.ClogItem> obtained = new ArrayList<>();

		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}
			allItemIds.add(itemId);
			if (child.getOpacity() == 0)
			{
				int qty = child.getItemQuantity();
				obtained.add(new ClogResult.ClogItem(itemId, Math.max(qty, 1), null));
			}
		}

		// Infer untradeables: if obtained count from header > items we can see,
		// the missing ones are untradeable items not visible in the widget
		if (obtainedCount > obtained.size() && obtainedCount <= allItemIds.size())
		{
			Set<Integer> obtainedIds = new HashSet<>();
			for (ClogResult.ClogItem oi : obtained)
			{
				obtainedIds.add(oi.getId());
			}

			int missing = obtainedCount - obtained.size();
			for (int itemId : allItemIds)
			{
				if (!obtainedIds.contains(itemId))
				{
					obtained.add(new ClogResult.ClogItem(itemId, 1, null));
					obtainedIds.add(itemId);
					missing--;
					if (missing <= 0)
					{
						break;
					}
				}
			}
		}

		if (allItemIds.isEmpty())
		{
			return;
		}

		String name = local.getName();
		if (localClogCache.hasDataFor(name))
		{
			localClogCache.mergeCategory(name, categoryKey, allItemIds, obtained);

			// Re-read global clog totals from live varps (catches game updates + new items)
			int liveObtained = client.getVarpValue(VARP_CLOG_OBTAINED);
			int liveTotal = client.getVarpValue(VARP_CLOG_TOTAL);
			if (liveObtained > 0 || liveTotal > 0)
			{
				localClogCache.updateTotals(name, liveObtained, liveTotal);
			}

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=4caf6e>Kill Clog:</col> Captured " + categoryName
					+ " - " + obtained.size() + "/" + allItemIds.size() + " obtained",
				null);
			SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(name));
		}
		else
		{
			bulk.bufferedCategoryKey = categoryKey;
			bulk.bufferedCategoryName = categoryName;
			bulk.bufferedCategoryItems = new ArrayList<>(allItemIds);
			bulk.bufferedCategoryObtained = new ArrayList<>(obtained);

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=4caf6e>Kill Clog:</col> Buffered " + categoryName
					+ " - " + obtained.size() + "/" + allItemIds.size()
					+ " obtained (will merge after sync)",
				null);
		}
	}

	private void resetBulkCapture()
	{
		bulk.reset();
	}

	private AccountType getLocalAccountType()
	{
		return mapAccountType(client.getVarbitValue(VarbitID.IRONMAN));
	}

	private AccountType mapAccountType(int varbitValue)
	{
		return AccountType.fromRuneLiteVarbit(varbitValue);
	}

	private void loadGimBadges()
	{
		net.runelite.api.IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null) return;
		BufferedImage gim = modIcons.length > ClogHelper.MODICON_GIM
			? ClogHelper.indexedSpriteToImage(modIcons[ClogHelper.MODICON_GIM]) : null;
		BufferedImage hcgim = modIcons.length > ClogHelper.MODICON_HCGIM
			? ClogHelper.indexedSpriteToImage(modIcons[ClogHelper.MODICON_HCGIM]) : null;
		BufferedImage unrankedGim = modIcons.length > ClogHelper.MODICON_UNRANKED_GIM
			? ClogHelper.indexedSpriteToImage(modIcons[ClogHelper.MODICON_UNRANKED_GIM]) : null;
		ClogHelper.setGimBadges(gim, hcgim, unrankedGim);
	}

	private BufferedImage getIcon()
	{
		return ImageUtil.loadImageResource(getClass(), "icon.png");
	}
}
