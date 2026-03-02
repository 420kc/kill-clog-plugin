package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
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
        log.debug("Kill Clog plugin stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN && config.autoLookupOnLogin())
        {
            Player local = client.getLocalPlayer();
            if (local != null && local.getName() != null)
            {
                String name = local.getName();
                SwingUtilities.invokeLater(() ->
                {
                    panel.setPlayerName(name);
                    panel.doLookup();
                });
            }
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
