package com.killclog;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("killclog")
public interface KillClogConfig extends Config
{
    @ConfigItem(
        keyName = "defaultPlayer",
        name = "Default Player",
        description = "Player name to look up on startup (leave blank to use logged-in character)",
        position = 0
    )
    default String defaultPlayer()
    {
        return "";
    }

    @ConfigItem(
        keyName = "showCollectionLog",
        name = "Show Collection Log",
        description = "<html>Fetch collection log from TempleOSRS and show in tooltips.<br><br>"
            + "<b>To sync your collection log:</b><br>"
            + "1. Install the 'TempleOSRS' plugin from the Plugin Hub<br>"
            + "2. Open your collection log in-game<br>"
            + "3. Click the sync button in the top-right corner<br><br>"
            + "Players without a TempleOSRS profile will show KC only.</html>",
        position = 1
    )
    default boolean showCollectionLog()
    {
        return true;
    }

    @ConfigItem(
        keyName = "playerMenuLookup",
        name = "Player Menu Lookup",
        description = "Add 'Kill Clog Lookup' to right-click menu on players",
        position = 2
    )
    default boolean playerMenuLookup()
    {
        return true;
    }

    @ConfigItem(
        keyName = "statusBarColor",
        name = "Status Bar",
        description = "Color of player name and kill count in the status bar",
        position = 3
    )
    default Color statusBarColor()
    {
        return new Color(198, 198, 198);
    }

    @ConfigItem(
        keyName = "notFoundColor",
        name = "Player Not on Hiscores",
        description = "Color of the message shown when a player cannot be found",
        position = 4
    )
    default Color notFoundColor()
    {
        return new Color(160, 160, 160);
    }

    // --- Completionist's Highlighter ---

    @ConfigSection(
        name = "Completionist's Highlighter",
        description = "Color KC numbers based on collection log completion",
        position = 10
    )
    String completionistSection = "completionist";

    @ConfigItem(
        keyName = "completionistHighlighter",
        name = "Enable Highlighter",
        description = "Color boss KC numbers based on collection log completion status",
        section = "completionist",
        position = 0
    )
    default boolean completionistHighlighter()
    {
        return true;
    }

    @ConfigItem(
        keyName = "completedClogColor",
        name = "Completed",
        description = "Color for bosses with all collection log items obtained",
        section = "completionist",
        position = 1
    )
    default Color completedClogColor()
    {
        return new Color(76, 175, 110);
    }

    @ConfigItem(
        keyName = "inProgressClogColor",
        name = "In Progress",
        description = "Color for bosses with some collection log items obtained",
        section = "completionist",
        position = 2
    )
    default Color inProgressClogColor()
    {
        return new Color(200, 170, 60);
    }

    @ConfigItem(
        keyName = "emptyClogColor",
        name = "Empty",
        description = "Color for bosses with kills but no collection log items obtained",
        section = "completionist",
        position = 3
    )
    default Color emptyClogColor()
    {
        return new Color(192, 80, 80);
    }
}
