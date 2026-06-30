package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kill Clog",
	description = "HiScores and Collection Log Overhaul",
	tags = {"boss", "kc", "kill count", "collection log", "pvm", "hiscore", "ironman"}
)
public class KillClogPlugin extends Plugin
{
	static final int CLOG_INTERFACE = 621;

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
	private LocalClogCache localClogCache;

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

	private NavigationButton navButton;

	private final ChatAutoLookupGate chatAutoLookup = new ChatAutoLookupGate();
	private final ClogSessionState sessionState = new ClogSessionState();
	private final ClogIndex clogIndex = new ClogIndex();
	private final VisibleClogCategoryReader visibleClogCategoryReader = new VisibleClogCategoryReader();
	private final LiveClogSync liveClogSync = new LiveClogSync();
	private final ManualClogSync manualClogSync = new ManualClogSync();
	private final LocalCaReader localCaReader = new LocalCaReader();
	private final ClogLookupMenu lookupMenu = new ClogLookupMenu();

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

		lookupMenu.start(config, menuManager);

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
		SwingUtilities.invokeLater(() -> panel.shutdown());
		localClogCache.shutdown();
		localCaCache.shutdown();
		manualClogSync.reset();
		clogIndex.clear();
		sessionState.reset();
		liveClogSync.resetFirstSyncWarning();
		nameAutocompleter.clearClientSnapshot();
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
			AccountType acctType = getLocalAccountType();
			localClogCache.setActivePlayer(name);
			localCaCache.setActivePlayer(name);
			SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name, acctType));
			if (!requestLocalReads)
			{
				sessionState.requestLocalReads();
			}
		}

		nameAutocompleter.refreshClientSnapshot();
		GimBadgeLoader.load(client);
		clogIndex.ensureParsed(client, itemManager);
		sessionState.requestAutoLookup(config.autoLookupOnLogin());
		lookupMenu.warnIfPlayerMenuSlotUnavailable(client, config, chatNotifier);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			SwingUtilities.invokeLater(panel::reloadTooltipSprites);
			clogService.clearTempleFailures();
			runeProfileService.clearFailures();
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
		}
	}

	// Live collection-log unlock messages update local cache immediately.
	// Player-sent chat still rate-limits a self lookup refresh for older data paths.
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		chatEmoji.rewrite(event);

		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String unlockName = ClogUnlockParser.parseItemName(event.getMessage());
			if (unlockName != null)
			{
				clientThread.invokeLater(() -> handleCollectionLogUnlock(unlockName));
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

	private void handleCollectionLogUnlock(String itemName)
	{
		liveClogSync.handleUnlock(itemName, client, itemManager, clogIndex,
			localClogCache, chatNotifier, clogButtonOverlay, panel::onBulkCaptureComplete);
	}

	// Keep local CA current when a task completes mid-session.
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (localCaReader.isCaVarbit(event.getVarbitId()))
		{
			sessionState.requestCaRead();
		}
	}

	/** Read per-tier CA completed counts from game varbits and persist them for the active player. */
	private boolean captureLocalCa()
	{
		return localCaReader.capture(client, localCaCache);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		nameAutocompleter.refreshClientSnapshot();

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
				sessionState.markAutoLookupStarted();
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

		manualClogSync.onGameTick(client, clogIndex, localClogCache,
			chatNotifier, clogButtonOverlay, liveClogSync::resetFirstSyncWarning,
			panel::onBulkCaptureComplete);
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
			lookupMenu.refresh(config, menuManager);
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
		return mapAccountType(client.getVarbitValue(VarbitID.IRONMAN));
	}

	private AccountType mapAccountType(int varbitValue)
	{
		return AccountType.fromRuneLiteVarbit(varbitValue);
	}

	private BufferedImage getIcon()
	{
		return ImageUtil.loadImageResource(getClass(), "icon.png");
	}
}
