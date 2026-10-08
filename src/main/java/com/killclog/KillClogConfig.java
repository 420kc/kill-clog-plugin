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
		position = 2,
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
		keyName = "enableComparison",
		name = "Enable Comparison",
		description = "Show the comparison control beside the player search",
		section = lookupSection,
		position = 1
	)
	default boolean enableComparison()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerMenuLookup",
		name = "Player Menu Lookup",
		description = "Add a lookup option to right-click menus",
		section = lookupSection,
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
		section = lookupSection,
		position = 3
	)
	default MenuLabel menuLabel()
	{
		return MenuLabel.KILL_CLOG;
	}

	@ConfigItem(
		keyName = "showLeaderboardSelector",
		name = "Show Leaderboard Selector",
		description = "Show hiscores leaderboard icons below the panel. Shows that leaderboard's stats and ranks; account detection stays automatic",
		section = lookupSection,
		position = 4
	)
	default boolean showLeaderboardSelector()
	{
		return false;
	}

	@ConfigSection(
		name = "Menu location",
		description = "Which right-click menus show the lookup option",
		position = 3,
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
		name = "Skills",
		description = "Skill placement, virtual levels, and cell colors",
		position = 4,
		closedByDefault = true
	)
	String skillsSection = "skills";

	@ConfigItem(
		keyName = "skillDisplay",
		name = "Skill Location",
		description = "Choose whether the skill cells appear in the main grid or the activity tray",
		section = skillsSection,
		position = 0
	)
	default SkillDisplay skillDisplay()
	{
		return SkillDisplay.FIXED;
	}

	@ConfigItem(
		keyName = "virtualLevels",
		name = "Display Virtual Levels",
		description = "Show XP-derived levels above 99 on skills",
		section = skillsSection,
		position = 1
	)
	default boolean virtualLevels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "skillLevelColor",
		name = "Skill Level Color",
		description = "Default color for ranked skill levels shown in cells",
		section = skillsSection,
		position = 2
	)
	default Color skillLevelColor()
	{
		return new Color(255, 87, 0);
	}

	@ConfigItem(
		keyName = "skillColorMode",
		name = "Skill Color Mode",
		description = "Use Skill Color, mark level 99+, or show overall Skill Clog progress. Clog Progression marks level 99+ while Skill Clogs are off",
		section = skillsSection,
		position = 3
	)
	default SkillColorMode skillColorMode()
	{
		// Skill Clogs are on by default, so their progress is the default; with them off this reads as 99+.
		return SkillColorMode.CLOG_PROGRESSION;
	}

	@ConfigItem(
		keyName = "enableSkillClogs",
		name = "Enable Skill Clogs",
		description = "Show Skill Clog items in skill cards. When off, skills show level, XP, rank, and XP to level only",
		section = skillsSection,
		position = 4
	)
	default boolean enableSkillClogs()
	{
		return true;
	}

	@ConfigSection(
		name = "Card Appearance",
		description = "Appearance and interaction for cards: collection logs, "
			+ "skill summaries, PvM and PvP summaries, and combat achievements",
		position = 1,
		closedByDefault = true
	)
	String modalAppearanceSection = "tooltips";

	@ConfigItem(
		keyName = "tooltipMode",
		name = "Card Activation",
		description = "Hover to preview and click to pin, or use click-to-reveal",
		section = modalAppearanceSection,
		position = 0
	)
	default TooltipMode tooltipMode()
	{
		return TooltipMode.HOVER;
	}

	@ConfigItem(
		keyName = "hoverStyle",
		name = "Cell Hover",
		description = "Visual feedback when hovering a cell. Outline uses the highlighter color, Tint subtly brightens the background.",
		section = modalAppearanceSection,
		position = 1
	)
	default HoverStyle hoverStyle()
	{
		return HoverStyle.TINT;
	}

	@ConfigItem(
		keyName = "wikiItemLinks",
		name = "Wiki Links",
		description = "Open the OSRS Wiki from supported names and sprites on cards",
		section = modalAppearanceSection,
		position = 2
	)
	default boolean wikiItemLinks()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipKc", name = "Show KC / Glory",
		description = "Kill count on boss collection logs; Sol Heredit shows Colosseum Glory instead",
		section = modalAppearanceSection, position = 3)
	default boolean showTooltipKc()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipPb", name = "Show PB",
		description = "Personal best beside the kc, where your client has one recorded",
		section = modalAppearanceSection, position = 4)
	default boolean showTooltipPb()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipRank", name = "Show Rank",
		description = "Hiscore rank line on boss, clue, and rare Collection Log cards",
		section = modalAppearanceSection, position = 5)
	default boolean showTooltipRank()
	{
		return true;
	}

	@ConfigSection(
		name = "Chat",
		description = "Kill Clog messages and emoji rendering",
		position = 5,
		closedByDefault = true
	)
	String chatSection = "chat";

	// keyName is the legacy name; renaming it would reset users' saved setting.
	@ConfigItem(
		keyName = "chatNewClogMessages",
		name = "Kill Clog chat messages",
		description = "New items added to Kill Clog, sync results and failures, and other notices. Setup guidance always shows.",
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
		description = "Show Kill Clog's custom emojis in chat: :killclog:, :rune:, :dragon:, :gilded:, :clog:, and :green:",
		section = chatSection,
		position = 1
	)
	default boolean showChatEmojis()
	{
		return true;
	}

	@ConfigSection(
		name = "Progress Highlighter",
		description = "Color collection-log progress",
		position = 6,
		closedByDefault = true
	)
	String completionistSection = "completionist";

	@ConfigItem(
		keyName = "completionistHighlighter",
		name = "Enable Highlighter",
		description = "Color boss KC by collection-log progression",
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
		description = "Color for complete bosses and optional skill completion at 99 or higher",
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
		description = "Color for bosses missing one item",
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
		description = "Color for bosses with some items",
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
		description = "Color for empty bosses",
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
		name = "Kill Clog Web Sync",
		description = "Collection Log sync and character publishing for your Kill Clog web profile",
		position = 0,
		closedByDefault = true
	)
	String killclogSection = "killclog";

	@ConfigItem(
		keyName = "killclogSync",
		name = "Sync Collection Log",
		description = "Keeps your killclog.com profile up to date with your Collection Log and "
			+ "personal bests. The chalice syncs right away. Nothing is sent until you turn "
			+ "this on, and the opt-out page removes everything.",
		section = killclogSection,
		position = 0
	)
	default boolean killclogSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "automaticSync",
		name = "Automatic sync",
		description = "Also syncs on its own: at login, after new items, and when you open your "
			+ "Collection Log. Off, only the chalice syncs.",
		section = killclogSection,
		position = 1
	)
	default boolean automaticSync()
	{
		return true;
	}

	@ConfigItem(
		keyName = "silentAutomaticSync",
		name = "Quiet automatic sync",
		description = "Hides the panel's progress and success flash for automatic syncs. "
			+ "Failures still show on the chalice; chat has its own setting.",
		section = killclogSection,
		position = 3
	)
	default boolean silentAutomaticSync()
	{
		return true;
	}

	@ConfigItem(
		keyName = "characterModel",
		name = "Publish Character Model",
		description = "Needs Sync Collection Log. Adds a button beside the chalice that publishes "
			+ "your current look and follower when you click it. Main game only.",
		section = killclogSection,
		position = 2
	)
	default boolean characterModel()
	{
		return false;
	}
}
