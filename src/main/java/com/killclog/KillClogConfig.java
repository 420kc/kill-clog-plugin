package com.killclog;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("killclog")
public interface KillClogConfig extends Config
{
    @ConfigItem(
        keyName = "autoLookupOnLogin",
        name = "Auto-Lookup on Login",
        description = "Automatically look up your stats when you log in",
        position = 0
    )
    default boolean autoLookupOnLogin()
    {
        return true;
    }

    @ConfigItem(
        keyName = "clogSource",
        name = "Collection Log Source",
        description = "<html>Where to load collection log data from.<br><br>"
            + "<b>TempleOSRS</b> — Fetch from TempleOSRS. Requires synced profile.<br>"
            + "<b>Local</b> — Browse your in-game collection log to build a local cache.<br>"
            + "<b>Both</b> — Use TempleOSRS data and build a local cache.<br><br>"
            + "<b>To sync with TempleOSRS:</b><br>"
            + "1. Install the 'TempleOSRS' plugin from the Plugin Hub<br>"
            + "2. Open your collection log in-game<br>"
            + "3. Click the sync button in the top-right corner</html>",
        position = 1
    )
    default ClogSource clogSource()
    {
        return ClogSource.BOTH;
    }

    @ConfigItem(
        keyName = "playerMenuLookup",
        name = "Player Menu Lookup",
        description = "Add 'Kill Clog' to right-click menu on players",
        position = 3
    )
    default boolean playerMenuLookup()
    {
        return true;
    }

    @ConfigItem(
        keyName = "infoBarColor",
        name = "Info Bar",
        description = "Color of player name and stats in the info bar",
        position = 4
    )
    default Color infoBarColor()
    {
        return new Color(198, 198, 198);
    }

    @ConfigItem(
        keyName = "tooltipMode",
        name = "Tooltip Activation",
        description = "How tooltips are triggered on boss and activity cells",
        position = 5
    )
    default TooltipMode tooltipMode()
    {
        return TooltipMode.HOVER;
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
        keyName = "highlighterKeybind",
        name = "Toggle Keybind",
        description = "Shortcut key to toggle the Completionist's Highlighter",
        section = "completionist",
        position = 1
    )
    default Keybind highlighterKeybind()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "activitiesExpanded",
        name = "",
        description = "",
        hidden = true
    )
    default boolean activitiesExpanded()
    {
        return false;
    }



    @ConfigItem(
        keyName = "completedClogColor",
        name = "Completed",
        description = "Color for bosses with all collection log items obtained",
        section = "completionist",
        position = 2
    )
    default Color completedClogColor()
    {
        return new Color(9, 175, 208);
    }

    @ConfigItem(
        keyName = "missing1Color",
        name = "1 Away",
        description = "Color for bosses missing exactly one collection log item",
        section = "completionist",
        position = 3
    )
    default Color missing1Color()
    {
        return new Color(120, 200, 220);
    }

    @ConfigItem(
        keyName = "inProgressClogColor",
        name = "In Progress",
        description = "Color for bosses with some collection log items obtained",
        section = "completionist",
        position = 4
    )
    default Color inProgressClogColor()
    {
        return new Color(255, 250, 0);
    }

    @ConfigItem(
        keyName = "emptyClogColor",
        name = "Empty",
        description = "Color for bosses with kills but no collection log items obtained",
        section = "completionist",
        position = 5
    )
    default Color emptyClogColor()
    {
        return new Color(168, 181, 42);
    }
}
