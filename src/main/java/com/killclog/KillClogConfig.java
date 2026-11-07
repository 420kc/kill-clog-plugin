package com.killclog;

import java.awt.Color;
import net.runelite.client.config.*;

/**
 * Kill Clog's settings as RuneLite's config panel shows them. A key name is a player's saved setting: renaming one
 * resets it, so an old name stays, and {@link ConfigMigrations} moves a value whose shape changes.
 */
@ConfigGroup("killclog")
public interface KillClogConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "Lookups, comparison, the sidebar icon and chat",
		position = 1,
		closedByDefault = true
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "autoLookupOnLogin",
		name = "Auto-Lookup on Login",
		description = "Look up your own account when you log in",
		section = generalSection,
		position = 0
	)
	default boolean autoLookupOnLogin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableComparison",
		name = "Enable Comparison",
		description = "Show the comparison button beside the search bar, to load a second player",
		section = generalSection,
		position = 1
	)
	default boolean enableComparison()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerMenuLookup",
		name = "Player Menu Lookup",
		description = "Add a Kill Clog lookup to right-click menus on player names",
		section = menuLocationsSection,
		position = 0
	)
	default boolean playerMenuLookup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuLabel",
		name = "Menu Label",
		description = "The option's text. To use Lookup, first turn off the HiScore plugin's Player option and Menu"
			+ " option, which use the same text",
		section = menuLocationsSection,
		position = 1
	)
	default MenuLabel menuLabel()
	{
		return MenuLabel.KILL_CLOG;
	}

	@ConfigItem(
		keyName = "showLeaderboardSelector",
		name = "Show Leaderboard Selector",
		description = "Show the hiscore leaderboard icons under the grid, to see a player's stats and ranks on each "
			+ "board they're on",
		section = generalSection,
		position = 2
	)
	default boolean showLeaderboardSelector()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sidebarPriority",
		name = "Sidebar Priority",
		description = "Where the Kill Clog icon sits in RuneLite's sidebar. Lower numbers sit higher",
		section = generalSection,
		position = 3
	)
	default int sidebarPriority()
	{
		return 6;
	}

	@ConfigSection(
		name = "Right-Click Menu",
		description = "The Kill Clog lookup option and where it shows",
		position = 3,
		closedByDefault = true
	)
	String menuLocationsSection = "menuLocations";

	@ConfigItem(keyName = "menuOnPlayers", name = "Players",
		description = "Show on in-game player right-click menus",
		section = menuLocationsSection, position = 2)
	default boolean menuOnPlayers()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnFriendsList", name = "Friends List",
		description = "Show on names in the Friends list",
		section = menuLocationsSection, position = 3)
	default boolean menuOnFriendsList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnIgnoreList", name = "Ignore List",
		description = "Show on names in the Ignore list",
		section = menuLocationsSection, position = 4)
	default boolean menuOnIgnoreList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnClanList", name = "Clan List",
		description = "Show on names in your clan member list",
		section = menuLocationsSection, position = 5)
	default boolean menuOnClanList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnGuestClanList", name = "Guest Clan List",
		description = "Show on names in guest clan member lists",
		section = menuLocationsSection, position = 6)
	default boolean menuOnGuestClanList()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnChatChannels", name = "Chat Channels",
		description = "Show on names in friends chat and clan chat channels",
		section = menuLocationsSection, position = 7)
	default boolean menuOnChatChannels()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnChat", name = "Public Chat",
		description = "Show on names in the public chatbox",
		section = menuLocationsSection, position = 8)
	default boolean menuOnChat()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnPrivateMessages", name = "Private Messages",
		description = "Show on names in private messages",
		section = menuLocationsSection, position = 9)
	default boolean menuOnPrivateMessages()
	{
		return true;
	}

	@ConfigItem(keyName = "menuOnGroupIronman", name = "Group Ironman",
		description = "Show on names in the Group Ironman panel",
		section = menuLocationsSection, position = 10)
	default boolean menuOnGroupIronman()
	{
		return true;
	}

	@ConfigSection(
		name = "Skills",
		description = "Where skills sit, their Skill Clogs, and how levels are colored",
		position = 4,
		closedByDefault = true
	)
	String skillsSection = "skills";

	@ConfigItem(
		keyName = "skillDisplay",
		name = "Skill Location",
		description = "Show skills in the main grid or in the activity tray",
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
		description = "Show levels above 99 from XP, up to 126. Level Progression then colors by them",
		section = skillsSection,
		position = 2
	)
	default boolean virtualLevels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "skillLevelColor",
		name = "Skill Level Color",
		description = "Every level's color in Skill Color mode, and in Clog Progression for a skill with no Skill Clog",
		section = skillsSection,
		position = 4
	)
	default Color skillLevelColor()
	{
		return new Color(255, 87, 0);
	}

	@ConfigItem(
		keyName = "skillColorMode",
		name = "Skill Color Mode",
		description = "How skill levels are colored: by Skill Clog progress, by level with the highlighter's colors, "
			+ "or in one Skill Level Color. Clog Progression uses Level Progression while Skill Clogs are off",
		section = skillsSection,
		position = 3
	)
	default SkillColorMode skillColorMode()
	{
		// Skill Clogs are on by default, so their progress is the default; with them off this reads as Level Progression.
		return SkillColorMode.CLOG_PROGRESSION;
	}

	@ConfigItem(
		keyName = "enableSkillClogs",
		name = "Enable Skill Clogs",
		description = "Show Skill Clog items in skill cards. When off, skills show level, XP, rank, and XP to level only",
		section = skillsSection,
		position = 1
	)
	default boolean enableSkillClogs()
	{
		return true;
	}

	@ConfigSection(
		name = "Card Settings",
		description = "How cards open, what they show, and their colors",
		position = 2,
		closedByDefault = true
	)
	String modalAppearanceSection = "tooltips";

	@ConfigItem(
		keyName = "tooltipMode",
		name = "Card Activation",
		description = "Hover to preview and click to pin, or click to open",
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
		description = "How a cell reacts under the mouse: Outline draws the highlighter color around it, Tint brightens it, "
			+ "None leaves it",
		section = modalAppearanceSection,
		position = 1
	)
	default HoverStyle hoverStyle()
	{
		return HoverStyle.TINT;
	}

	@ConfigItem(
		keyName = "cardLinks",
		name = "Card Links",
		description = "Open the OSRS Wiki, TempleOSRS, RuneProfile or killclog.com from names and items on cards:"
			+ " on a click, only on a shift-click, or never",
		section = modalAppearanceSection,
		position = 2
	)
	default CardLinks cardLinks()
	{
		return CardLinks.ON;
	}

	@ConfigItem(keyName = "showTooltipKc", name = "Show KC / Glory",
		description = "Show the kill count on boss cards (Colosseum Glory on Sol Heredit's)",
		section = modalAppearanceSection, position = 3)
	default boolean showTooltipKc()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipPb", name = "Show PB",
		description = "Show the personal best beside the kill count, when one is recorded",
		section = modalAppearanceSection, position = 4)
	default boolean showTooltipPb()
	{
		return true;
	}

	@ConfigItem(keyName = "showTooltipRank", name = "Show Rank",
		description = "Show the hiscore rank on boss, clue and rare item cards",
		section = modalAppearanceSection, position = 5)
	default boolean showTooltipRank()
	{
		return true;
	}

	@ConfigItem(keyName = "cardLabelColor", name = "Labels",
		description = "Card titles and labels. The game's orange by default",
		section = modalAppearanceSection, position = 6)
	default Color cardLabelColor()
	{
		return NativeTooltip.OSRS_ORANGE;
	}

	@ConfigItem(keyName = "cardValueColor", name = "Values",
		description = "Card values, and the label under the mouse. White by default",
		section = modalAppearanceSection, position = 7)
	default Color cardValueColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(keyName = "cardDividerColor", name = "Dividers",
		description = "The lines between a card's sections",
		section = modalAppearanceSection, position = 8)
	default Color cardDividerColor()
	{
		return NativeTooltip.DIVIDER;
	}

	@ConfigItem(keyName = "cardBackgroundColor", name = "Background",
		description = "One flat color for card backgrounds. At its default, the parchment or your Resource Pack's. "
			+ "Right-click the name to reset it",
		section = modalAppearanceSection, position = 9)
	default Color cardBackgroundColor()
	{
		return NativeTooltip.FALLBACK_BG;
	}

	@ConfigItem(keyName = "cardBorderColor", name = "Border",
		description = "One flat color for card borders. At its default, the iron rivets or your Resource Pack's. "
			+ "Right-click the name to reset it",
		section = modalAppearanceSection, position = 10)
	default Color cardBorderColor()
	{
		return NativeTooltip.BORDER_MID;
	}

	@ConfigItem(keyName = "cardScrollbarColor", name = "Scroll Bars",
		description = "One color for card scroll bars, their track a darker shade. At its default, your Resource "
			+ "Pack's or the game's orange. Right-click the name to reset it",
		section = modalAppearanceSection, position = 11)
	default Color cardScrollbarColor()
	{
		return NativeTooltip.OSRS_ORANGE;
	}

	// keyName is the legacy name; renaming it would reset users' saved setting.
	@ConfigItem(
		keyName = "chatNewClogMessages",
		name = "Chat Messages",
		description = "Kill Clog's chat lines: new items, sync results and other notices. Setup steps always show",
		section = generalSection,
		position = 4
	)
	default boolean autosyncChatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChatEmojis",
		name = "Show Chat Emojis",
		description = "Show Kill Clog's emojis in chat: :killclog: :rune: :dragon: :gilded: :clog: :green:",
		section = generalSection,
		position = 5
	)
	default boolean showChatEmojis()
	{
		return true;
	}

	@ConfigSection(
		name = "Progress Highlighter",
		description = "Colors for Collection Log progress on bosses, activities, clues and skills, and for levels in "
			+ "Level Progression",
		position = 5,
		closedByDefault = true
	)
	String completionistSection = "completionist";

	@ConfigItem(
		keyName = "completionistHighlighter",
		name = "Enable Highlighter",
		description = "Color boss, activity and clue counts by Collection Log progress, and the summary bar's text. Skills "
			+ "follow Skill Color Mode",
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
		description = "The summary bar's text: name, log count, PvM, total level and PvP",
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
		description = "Complete logs, and level 99 (126 with virtual levels) in Level Progression",
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
		description = "Logs one item from complete, and levels 92 to 98 (120 to 125)",
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
		description = "Logs with some items, and levels 2 to 91 (92 to 119)",
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
		description = "Empty logs, and level 1 (up to 91)",
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
		position = 2
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
		position = 3
	)
	default boolean characterModel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareClan",
		name = "Share Clan",
		description = "Your sync names your clan and title on killclog.com and in other players' Kill Clog. "
			+ "Off, it says you're in none. RuneProfile has its own setting for its profiles.",
		section = killclogSection,
		position = 4
	)
	default boolean shareClan()
	{
		return true;
	}
}
