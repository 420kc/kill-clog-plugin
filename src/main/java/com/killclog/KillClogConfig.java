package com.killclog;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("killclog")
public interface KillClogConfig extends Config
{
    @ConfigItem(
        keyName = "defaultPlayer",
        name = "Default Player",
        description = "Player name to look up on startup (leave blank to use logged-in character)"
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
            + "Players without a TempleOSRS profile will show KC only.</html>"
    )
    default boolean showCollectionLog()
    {
        return true;
    }

    @ConfigItem(
        keyName = "playerMenuLookup",
        name = "Player Menu Lookup",
        description = "Add 'Kill Clog Lookup' to right-click menu on players"
    )
    default boolean playerMenuLookup()
    {
        return true;
    }
}
