package com.killclog;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
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
    description = "Boss kill count tracker with collection log tooltips and parallelized account type detection",
    tags = {"boss", "kc", "kill count", "collection log", "pvm", "hiscore", "ironman"}
)
public class KillClogPlugin extends Plugin
{
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

        String defaultPlayer = config.defaultPlayer();
        if (!defaultPlayer.isEmpty())
        {
            SwingUtilities.invokeLater(() ->
            {
                panel.setPlayerName(defaultPlayer);
                panel.doLookup();
            });
        }

        log.info("Kill Clog plugin started");
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        keyManager.unregisterKeyListener(highlighterHotkey);
        SwingUtilities.invokeLater(() -> panel.shutdown());
        log.info("Kill Clog plugin stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            if (config.defaultPlayer().isEmpty())
            {
                Player local = client.getLocalPlayer();
                if (local != null && local.getName() != null)
                {
                    String name = local.getName();
                    SwingUtilities.invokeLater(() -> panel.setPlayerName(name));
                }
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
        SwingUtilities.invokeLater(() -> panel.onConfigChanged(event.getKey()));
    }

    /**
     * Right-click player menu: "Kill Clog Lookup"
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        if (!config.playerMenuLookup())
        {
            return;
        }
        MenuEntry[] entries = event.getMenuEntries();
        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.WALK
                || entry.getType() == MenuAction.PLAYER_FIRST_OPTION
                || entry.getType() == MenuAction.PLAYER_SECOND_OPTION
                || entry.getType() == MenuAction.PLAYER_THIRD_OPTION
                || entry.getType() == MenuAction.PLAYER_FOURTH_OPTION
                || entry.getType() == MenuAction.PLAYER_FIFTH_OPTION
                || entry.getType() == MenuAction.PLAYER_SIXTH_OPTION
                || entry.getType() == MenuAction.PLAYER_SEVENTH_OPTION
                || entry.getType() == MenuAction.PLAYER_EIGHTH_OPTION)
            {
                String target = entry.getTarget();
                if (target != null && !target.isEmpty())
                {
                    String playerName = Text.removeTags(target).trim();
                    if (!playerName.isEmpty())
                    {
                        addLookupMenuEntry(playerName);
                        return;
                    }
                }
            }
        }
    }

    private void addLookupMenuEntry(String playerName)
    {
        client.getMenu().createMenuEntry(1)
            .setOption("<col=ffffff>Kill Clog</col> Lookup")
            .setTarget("<col=ffffff>" + playerName + "</col>")
            .setType(MenuAction.RUNELITE)
            .onClick(e -> SwingUtilities.invokeLater(() ->
            {
                panel.setPlayerName(playerName);
                panel.doLookup();
            }));
    }

    private BufferedImage getIcon()
    {
        return ImageUtil.loadImageResource(getClass(), "icon.png");
    }
}
