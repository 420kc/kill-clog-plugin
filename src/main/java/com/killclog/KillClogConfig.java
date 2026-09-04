package com.killclog;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("killclog")
public interface KillClogConfig extends Config
{
	@ConfigSection(
		name = "Lookup",
		description = "Automatic lookup and player-menu controls",
		position = 0,
		closedByDefault = true
	)
	String lookupSection = "lookup";

	@ConfigItem(
		keyName = "autoLookupOnLogin",
		name = "Auto-Lookup on Login",
		description = "Automatically look up your stats when you log in",
		section = lookupSection,
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
		section = lookupSection,
		position = 1
	)
	default boolean playerMenuLookup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuLabel",
		name = "Menu Label",
		description = "Text shown on the right-click lookup option",
		section = lookupSection,
		position = 2
	)
	default MenuLabel menuLabel()
	{
		return MenuLabel.KILL_CLOG;
	}

	@ConfigSection(
		name = "Menu Locations",
		description = "Which right-click menus show the lookup option",
		position = 1,
		closedByDefault = true
	)
	String menuLocationsSection = "menuLocations";

	@ConfigItem(keyName = "menuOnPlayers", name = "Players",
		description = "Show on in-game player right-click menus",
		section = menuLocationsSection, position = 0)
	default boolean menuOnPlayers()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnFriendsList", name = "Friends List",
		description = "Show on names in the Friends list",
		section = menuLocationsSection, position = 1)
	default boolean menuOnFriendsList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnIgnoreList", name = "Ignore List",
		description = "Show on names in the Ignore list",
		section = menuLocationsSection, position = 2)
	default boolean menuOnIgnoreList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnClanList", name = "Clan List",
		description = "Show on names in your clan member list",
		section = menuLocationsSection, position = 3)
	default boolean menuOnClanList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnGuestClanList", name = "Guest Clan List",
		description = "Show on names in guest clan member lists",
		section = menuLocationsSection, position = 4)
	default boolean menuOnGuestClanList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnChatChannels", name = "Chat Channels",
		description = "Show on names in friends chat and clan chat channels",
		section = menuLocationsSection, position = 5)
	default boolean menuOnChatChannels()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnChat", name = "Public Chat",
		description = "Show on names in the public chatbox",
		section = menuLocationsSection, position = 6)
	default boolean menuOnChat()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnPrivateMessages", name = "Private Messages",
		description = "Show on names in private messages",
		section = menuLocationsSection, position = 7)
	default boolean menuOnPrivateMessages()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnGroupIronman", name = "Group Ironman",
		description = "Show on names in the Group Ironman panel",
		section = menuLocationsSection, position = 8)
	default boolean menuOnGroupIronman()
	{
		return true;
	}

	@ConfigSection(
		name = "Panel Display",
		description = "Choose what the side panel shows and where",
		position = 2
	)
	String panelDisplaySection = "panelDisplay";

	@ConfigItem(
		keyName = "skillDisplay",
		name = "Skill Display",
		description = "Show skills in the Total tooltip, above the boss grid, or above clues in the activity tray",
		section = panelDisplaySection,
		position = 0
	)
	default SkillDisplay skillDisplay()
	{
		return SkillDisplay.TOOLTIP;
	}

	@ConfigItem(
		keyName = "virtualLevels",
		name = "Display Virtual Levels",
		description = "Show XP-derived levels above 99 in skill summaries",
		section = panelDisplaySection,
		position = 1
	)
	default boolean virtualLevels()
	{
		return true;
	}

	@ConfigSection(
		name = "Tooltips",
		description = "Tooltip interaction, links, and stat lines",
		position = 3,
		closedByDefault = true
	)
	String tooltipsSection = "tooltips";

	@ConfigItem(
		keyName = "tooltipMode",
		name = "Tooltip Activation",
		description = "Hover to preview and click to pin, or use click-to-reveal",
		section = tooltipsSection,
		position = 0
	)
	default TooltipMode tooltipMode()
	{
		return TooltipMode.CLICK;
	}

	@ConfigItem(
		keyName = "hoverStyle",
		name = "Cell Hover",
		description = "Visual feedback when hovering a cell. Outline uses the highlighter color, Tint subtly brightens the background.",
		section = tooltipsSection,
		position = 1
	)
	default HoverStyle hoverStyle()
	{
		return HoverStyle.OUTLINE;
	}

	@ConfigItem(
		keyName = "wikiItemLinks",
		name = "Wiki Links",
		description = "Click boss names and item sprites in collection-log tooltips to open the OSRS Wiki",
		section = tooltipsSection,
		position = 2
	)
	default boolean wikiItemLinks()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipKc", name = "Show KC / Glory",
		description = "Kill count on boss tooltips; Sol Heredit shows Colosseum Glory instead",
		section = tooltipsSection, position = 3)
	default boolean showTooltipKc()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipPb", name = "Show PB",
		description = "Personal best beside the kc, where your client has one recorded",
		section = tooltipsSection, position = 4)
	default boolean showTooltipPb()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipRank", name = "Show Rank",
		description = "Hiscore rank line on boss, clue, and rare tooltips",
		section = tooltipsSection, position = 5)
	default boolean showTooltipRank()
	{
		return true;
	}

	@ConfigSection(
		name = "Chat",
		description = "Kill Clog messages and emoji rendering",
		position = 4,
		closedByDefault = true
	)
	String chatSection = "chat";

	// keyName is the legacy name; renaming it would reset users' saved setting.
	@ConfigItem(
		keyName = "chatNewClogMessages",
		name = "Sync chat messages",
		description = "Show Kill Clog sync messages in chat: new drop captures, sync results, and warnings. Setup guidance always shows.",
		section = chatSection,
		position = 0
	)
	default boolean autosyncChatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChatEmojis",
		name = "Show emojis in chat",
		description = "Render :clog:, :rune:, :dragon: and friends as item icons in chat messages",
		section = chatSection,
		position = 1
	)
	default boolean showChatEmojis()
	{
		return true;
	}

	@ConfigSection(
		name = "Progress Highlighter",
		description = "Color collection-log progress and embedded skill levels",
		position = 5,
		closedByDefault = true
	)
	String completionistSection = "completionist";

	@ConfigItem(
		keyName = "completionistHighlighter",
		name = "Enable Highlighter",
		description = "Color boss KC and embedded skill levels by progression",
		section = completionistSection,
		position = 0
	)
	default boolean completionistHighlighter()
	{
		return true;
	}

	@ConfigItem(
		keyName = "infoBarColor",
		name = "Summary Bar Text",
		description = "Colors summary-bar text (RSN, clog count, PvM, total level, and PvP)",
		section = completionistSection,
		position = 2
	)
	default Color infoBarColor()
	{
		return new Color(255, 87, 0);
	}

	@ConfigItem(
		keyName = "completedClogColor",
		name = "Completed",
		description = "Color for complete bosses and skills level 99 or higher",
		section = completionistSection,
		position = 3
	)
	default Color completedClogColor()
	{
		return new Color(78, 240, 21);
	}

	@ConfigItem(
		keyName = "missing1Color",
		name = "1 Away",
		description = "Color for bosses missing one item and skills level 93-98",
		section = completionistSection,
		position = 4
	)
	default Color missing1Color()
	{
		return new Color(202, 255, 0);
	}

	@ConfigItem(
		keyName = "inProgressClogColor",
		name = "In Progress",
		description = "Color for bosses with some items and skills level 50-92",
		section = completionistSection,
		position = 5
	)
	default Color inProgressClogColor()
	{
		return new Color(255, 173, 0);
	}

	@ConfigItem(
		keyName = "emptyClogColor",
		name = "Empty",
		description = "Color for empty bosses and skills level 1-49",
		section = completionistSection,
		position = 6
	)
	default Color emptyClogColor()
	{
		return new Color(255, 87, 0);
	}

	// Persisted UI state, not user settings.

	// Open by default since the hamburger became the boss-view switch. The
	// separator is the tray's toggle in both directions and stays visible
	// while collapsed, so the tray is always recoverable.
	@ConfigItem(keyName = "activitiesExpanded", name = "", description = "", hidden = true)
	default boolean activitiesExpanded()
	{
		return true;
	}

	@ConfigItem(keyName = "bossListView", name = "", description = "", hidden = true)
	default boolean bossListView()
	{
		return false;
	}

	@ConfigItem(keyName = "seenSelfGreeting", name = "", description = "", hidden = true)
	default boolean seenSelfGreeting()
	{
		return false;
	}

	@ConfigSection(
		name = "killclog.com",
		description = "First-party sync with your killclog.com profile",
		position = 6,
		closedByDefault = true
	)
	String killclogSection = "killclog";

	@ConfigItem(
		keyName = "killclogSync",
		name = "Sync Collection Log to Killclog.com",
		description = "Publish your collection log and personal bests to your killclog.com "
			+ "profile. Off by default. Nothing is sent until you turn it on, and the "
			+ "opt-out page removes everything.",
		section = killclogSection,
		position = 0
	)
	default boolean killclogSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "characterModel",
		name = "Character model",
		description = "Show a one-click character publishing button. Requires "
			+ "Killclog.com sync.",
		section = killclogSection,
		position = 1
	)
	default boolean characterModel()
	{
		return false;
	}
}
