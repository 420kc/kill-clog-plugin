package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kill Clog",
	description = "Boss log overhaul with clog tooltips and completion colors",
	tags = {"boss", "kc", "kill count", "collection log", "pvm", "hiscore", "ironman"}
)
public class KillClogPlugin extends Plugin
{
	private static final String MENU_OPTION = "Kill Clog";

	// Bulk clog capture — script + widget + enum IDs
	private static final int CLOG_SETUP_SCRIPT = 7797;
	private static final int CLOG_ITEM_SCRIPT = 4100;
	private static final int CLOG_SEARCH_WIDGET = 40697932;  // (621 << 16) | 76
	private static final int ENUM_CLOG_TABS = 2102;
	private static final int PARAM_SUBTAB_ENUM = 683;
	private static final int PARAM_CATEGORY_NAME = 689;
	private static final int PARAM_CATEGORY_ITEMS = 690;

	@Inject
	private Client client;

	@Inject
	private KillClogConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KillClogPanel panel;

	@Inject
	private KeyManager keyManager;

	@Inject
	private Provider<MenuManager> menuManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private NameAutocompleter nameAutocompleter;

	@Inject
	private LocalClogCache localClogCache;

	private NavigationButton navButton;
	private boolean pendingAutoLookup;

	// Bulk clog capture state (client thread only)
	private Map<String, List<Integer>> enumCategoryMap;
	private Map<Integer, String> itemToCategoryKey;
	private boolean enumsParsed;
	private boolean bulkCaptureActive;
	private int bulkFinalizeTickCount = -1;
	private final List<ClogResult.ClogItem> bulkObtained = new ArrayList<>();

	private final HotkeyListener highlighterHotkey = new HotkeyListener(() -> config.highlighterKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			SwingUtilities.invokeLater(() -> panel.toggleHighlighter());
		}
	};

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
		panel.setPluginManager(pluginManager);
		panel.setNameAutocompleter(nameAutocompleter);
		keyManager.registerKeyListener(highlighterHotkey);

		if (config.playerMenuLookup())
		{
			menuManager.get().addPlayerMenuItem(MENU_OPTION);
		}

		log.debug("Kill Clog plugin started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		keyManager.unregisterKeyListener(highlighterHotkey);
		menuManager.get().removePlayerMenuItem(MENU_OPTION);
		SwingUtilities.invokeLater(() -> panel.shutdown());
		localClogCache.shutdown();
		resetBulkCapture();
		enumsParsed = false;
		log.debug("Kill Clog plugin stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Always track logged-in player for local clog cache
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				String name = local.getName();
				localClogCache.setActivePlayer(name);
				AccountType acctType = mapAccountType(client.getAccountType());
				SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			}

			if (!enumsParsed)
			{
				parseClogEnums();
			}

			if (config.autoLookupOnLogin())
			{
				pendingAutoLookup = true;
			}
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			resetBulkCapture();
			enumsParsed = false;
		}
	}

	@Subscribe
	public void onGameTick(net.runelite.api.events.GameTick event)
	{
		if (pendingAutoLookup)
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				pendingAutoLookup = false;
				String name = local.getName();
				localClogCache.setActivePlayer(name);
				AccountType acctType = mapAccountType(client.getAccountType());
				SwingUtilities.invokeLater(() ->
				{
					panel.setLoggedInPlayer(name, acctType);
					panel.setPlayerName(name);
					panel.doLookup();
				});
			}
		}

		if (bulkCaptureActive && bulkFinalizeTickCount > 0
			&& client.getTickCount() >= bulkFinalizeTickCount)
		{
			finalizeBulkCapture();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == CLOG_SETUP_SCRIPT)
		{
			triggerBulkCapture();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != CLOG_ITEM_SCRIPT || !bulkCaptureActive)
		{
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		if (intStackSize < 2)
		{
			return;
		}
		int itemId = intStack[intStackSize - 2];
		int count = intStack[intStackSize - 1];

		bulkObtained.add(new ClogResult.ClogItem(itemId, count, null));
		bulkFinalizeTickCount = client.getTickCount() + 3;
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
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

		if (event.getKey().equals("playerMenuLookup"))
		{
			if (config.playerMenuLookup())
			{
				menuManager.get().addPlayerMenuItem(MENU_OPTION);
			}
			else
			{
				menuManager.get().removePlayerMenuItem(MENU_OPTION);
			}
		}

		SwingUtilities.invokeLater(() -> panel.onConfigChanged(event.getKey()));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER
			&& event.getMenuOption().equals(MENU_OPTION))
		{
			Player player = event.getMenuEntry().getPlayer();
			if (player == null || player.getName() == null)
			{
				return;
			}

			String name = Text.toJagexName(player.getName());
			SwingUtilities.invokeLater(() ->
			{
				panel.setPlayerName(name);
				panel.doLookup();
			});
		}
	}

	// --- Bulk clog capture ---

	private void parseClogEnums()
	{
		try
		{
			enumCategoryMap = new HashMap<>();
			itemToCategoryKey = new HashMap<>();

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

					String categoryKey = name.toLowerCase()
						.replaceAll("[^a-z0-9]+", "_")
						.replaceAll("^_|_$", "");

					EnumComposition itemsEnum = client.getEnum(itemsEnumId);
					List<Integer> itemIds = new ArrayList<>();
					for (int itemKey : itemsEnum.getKeys())
					{
						int itemId = itemsEnum.getIntValue(itemKey);
						itemIds.add(itemId);
						itemToCategoryKey.put(itemId, categoryKey);
					}

					enumCategoryMap.put(categoryKey, itemIds);
				}
			}

			enumsParsed = true;
			log.info("Parsed clog enums: {} categories, {} items",
				enumCategoryMap.size(), itemToCategoryKey.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to parse clog enums", e);
			enumsParsed = false;
		}
	}

	private void triggerBulkCapture()
	{
		if (!enumsParsed || bulkCaptureActive)
		{
			return;
		}

		if (config.clogSource() == ClogSource.TEMPLE)
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

		bulkObtained.clear();
		bulkCaptureActive = true;
		bulkFinalizeTickCount = -1;

		client.menuAction(-1, CLOG_SEARCH_WIDGET, MenuAction.CC_OP, 1, -1, "Search", null);
		client.menuAction(-1, CLOG_SEARCH_WIDGET, MenuAction.CC_OP, 1, -1, "Back", null);

		log.debug("Triggered bulk clog capture");
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

		// Group obtained items by category
		Map<String, List<ClogResult.ClogItem>> obtainedByCategory = new HashMap<>();

		// Initialize ALL categories (even empty) so cacheResult stores them
		for (String cat : enumCategoryMap.keySet())
		{
			obtainedByCategory.put(cat, new ArrayList<>());
		}

		for (ClogResult.ClogItem item : bulkObtained)
		{
			String cat = itemToCategoryKey.get(item.getId());
			if (cat != null)
			{
				obtainedByCategory.get(cat).add(item);
			}
		}

		// Copy category item lists from enum data
		Map<String, List<Integer>> categoryItemsCopy = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : enumCategoryMap.entrySet())
		{
			categoryItemsCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}

		ClogResult result = new ClogResult(name, obtainedByCategory, categoryItemsCopy,
			new HashMap<>(), null);
		localClogCache.cacheResult(result);

		int total = bulkObtained.size();
		log.info("Bulk clog capture complete: {} items across {} categories for '{}'",
			total, enumCategoryMap.size(), name);

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=4caf6e>Kill Clog:</col> Collection log captured — " + total
				+ " items. Open the Kill Clog panel to view.",
			null);

		resetBulkCapture();

		SwingUtilities.invokeLater(() -> panel.onBulkCaptureComplete(name));
	}

	private void resetBulkCapture()
	{
		bulkCaptureActive = false;
		bulkFinalizeTickCount = -1;
		bulkObtained.clear();
	}

	private AccountType mapAccountType(net.runelite.api.vars.AccountType rlType)
	{
		if (rlType == null)
		{
			return AccountType.REGULAR;
		}
		switch (rlType)
		{
			case IRONMAN:
				return AccountType.IRONMAN;
			case ULTIMATE_IRONMAN:
				return AccountType.ULTIMATE_IRONMAN;
			case HARDCORE_IRONMAN:
				return AccountType.HARDCORE_IRONMAN;
			default:
				return AccountType.REGULAR;
		}
	}

	private BufferedImage getIcon()
	{
		return ImageUtil.loadImageResource(getClass(), "icon.png");
	}
}
