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
		keyName = "playerMenuLookup",
		name = "Player Menu Lookup",
		description = "Add a lookup option to right-click menus",
		position = 2
	)
	default boolean playerMenuLookup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuLabel",
		name = "Menu Label",
		description = "Text shown on the right-click lookup option",
		position = 3
	)
	default MenuLabel menuLabel()
	{
		return MenuLabel.KILL_CLOG;
	}

	@ConfigSection(
		name = "Menu Locations",
		description = "Which right-click menus show the lookup option",
		position = 4,
		closedByDefault = true
	)
	String menuLocationsSection = "menuLocations";

	@ConfigItem(
		keyName = "menuOnPlayers",
		name = "Players",
		description = "Show on in-game player right-click menus",
		section = "menuLocations",
		position = 0
	)
	default boolean menuOnPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnFriendsList",
		name = "Friends List",
		description = "Show on names in the Friends list",
		section = "menuLocations",
		position = 1
	)
	default boolean menuOnFriendsList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnIgnoreList",
		name = "Ignore List",
		description = "Show on names in the Ignore list",
		section = "menuLocations",
		position = 2
	)
	default boolean menuOnIgnoreList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnClanList",
		name = "Clan List",
		description = "Show on names in your clan member list",
		section = "menuLocations",
		position = 3
	)
	default boolean menuOnClanList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnGuestClanList",
		name = "Guest Clan List",
		description = "Show on names in guest clan member lists",
		section = "menuLocations",
		position = 4
	)
	default boolean menuOnGuestClanList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnChatChannels",
		name = "Chat Channels",
		description = "Show on names in friends chat and clan chat channels",
		section = "menuLocations",
		position = 5
	)
	default boolean menuOnChatChannels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnChat",
		name = "Public Chat",
		description = "Show on names in the public chatbox",
		section = "menuLocations",
		position = 6
	)
	default boolean menuOnChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnPrivateMessages",
		name = "Private Messages",
		description = "Show on names in private messages",
		section = "menuLocations",
		position = 7
	)
	default boolean menuOnPrivateMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuOnGroupIronman",
		name = "Group Ironman",
		description = "Show on names in the Group Ironman panel",
		section = "menuLocations",
		position = 8
	)
	default boolean menuOnGroupIronman()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tooltipMode",
		name = "Tooltip Activation",
		description = "How grid and summary tooltips are triggered (hover or click-to-reveal)",
		position = 5
	)
	default TooltipMode tooltipMode()
	{
		return TooltipMode.CLICK;
	}

	@ConfigItem(
		keyName = "hoverStyle",
		name = "Cell Hover",
		description = "Visual feedback when hovering a cell. Outline uses the highlighter color, Tint subtly brightens the background.",
		position = 6
	)
	default HoverStyle hoverStyle()
	{
		return HoverStyle.OUTLINE;
	}

	@ConfigItem(
		keyName = "wikiItemLinks",
		name = "Wiki Links",
		description = "Click boss names and item sprites in collection-log tooltips to open the OSRS Wiki",
		position = 7
	)
	default boolean wikiItemLinks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatNewClogMessages",
		name = "Autosync chat messages",
		description = "Show Added ... to Kill Clog when autosync updates your local collection log",
		position = 8
	)
	default boolean autosyncChatMessages()
	{
		return true;
	}

	// Progress highlighter.

	@ConfigSection(
		name = "Progress Highlighter",
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
		keyName = "seenSelfGreeting",
		name = "",
		description = "",
		hidden = true
	)
	default boolean seenSelfGreeting()
	{
		return false;
	}

	@ConfigItem(
		keyName = "infoBarColor",
		name = "Summary Bar Text",
		description = "Colors summary-bar text (RSN, clog count, PvM, total level, and PvP)",
		section = "completionist",
		position = 2
	)
	default Color infoBarColor()
	{
		return new Color(255, 87, 0);
	}

	@ConfigItem(
		keyName = "completedClogColor",
		name = "Completed",
		description = "Color for bosses with all collection log items obtained",
		section = "completionist",
		position = 3
	)
	default Color completedClogColor()
	{
		return new Color(78, 240, 21);
	}

	@ConfigItem(
		keyName = "missing1Color",
		name = "1 Away",
		description = "Color for bosses missing exactly one collection log item",
		section = "completionist",
		position = 4
	)
	default Color missing1Color()
	{
		return new Color(202, 255, 0);
	}

	@ConfigItem(
		keyName = "inProgressClogColor",
		name = "In Progress",
		description = "Color for bosses with some collection log items obtained",
		section = "completionist",
		position = 5
	)
	default Color inProgressClogColor()
	{
		return new Color(255, 173, 0);
	}

	@ConfigItem(
		keyName = "emptyClogColor",
		name = "Empty",
		description = "Color for bosses with kills but no collection log items obtained",
		section = "completionist",
		position = 6
	)
	default Color emptyClogColor()
	{
		return new Color(255, 87, 0);
	}
}
