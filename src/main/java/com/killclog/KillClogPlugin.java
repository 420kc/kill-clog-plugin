package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
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
        localClogCache.shutdown();
        SwingUtilities.invokeLater(() -> panel.shutdown());
        log.debug("Kill Clog plugin stopped");
    }

    private boolean pendingAutoLookup = false;

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
                SwingUtilities.invokeLater(() -> panel.setLoggedInPlayer(name));
            }

            if (config.autoLookupOnLogin())
            {
                pendingAutoLookup = true;
            }
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
                SwingUtilities.invokeLater(() ->
                {
                    panel.setLoggedInPlayer(name);
                    panel.setPlayerName(name);
                    panel.doLookup();
                });
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != 2731)
        {
            return;
        }

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null)
        {
            return;
        }

        localClogCache.setActivePlayer(local.getName());

        Widget header = client.getWidget(40697876);  // collection log category name
        Widget items = client.getWidget(40697893);    // collection log item grid
        if (header == null || items == null)
        {
            return;
        }

        String headerText = header.getText();
        if (headerText == null || headerText.isEmpty())
        {
            return;
        }

        Widget[] children = items.getChildren();
        if (children == null || children.length == 0)
        {
            return;
        }

        String categoryKey = headerText.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "");

        List<Integer> allItemIds = new ArrayList<>();
        List<ClogResult.ClogItem> obtained = new ArrayList<>();

        for (Widget child : children)
        {
            int itemId = child.getItemId();
            if (itemId <= 0)
            {
                continue;
            }
            allItemIds.add(itemId);
            // opacity 0 = obtained, opacity > 0 = not obtained
            if (child.getOpacity() == 0)
            {
                int quantity = child.getItemQuantity();
                obtained.add(new ClogResult.ClogItem(itemId, Math.max(quantity, 1)));
            }
        }

        if (!allItemIds.isEmpty() && config.localClogStorage() != LocalClogMode.OFF)
        {
            localClogCache.putCategory(categoryKey, allItemIds, obtained);
        }
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

    private BufferedImage getIcon()
    {
        return ImageUtil.loadImageResource(getClass(), "icon.png");
    }
}
