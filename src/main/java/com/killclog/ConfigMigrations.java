package com.killclog;

import net.runelite.client.config.ConfigManager;

/** One-time moves of saved settings whose shape changed between releases. */
final class ConfigMigrations
{
	private static final String GROUP = "killclog";

	private ConfigMigrations()
	{
	}

	static void run(ConfigManager configManager)
	{
		skillColorMode(configManager);
		skillLocation(configManager);
		cardLinks(configManager);
		characterCredentials(configManager);
	}

	/** A character request proves its account by its hash since 2.7.0, so the installation credentials kept go. */
	private static void characterCredentials(ConfigManager configManager)
	{
		String group = "killclog-profile-appearance";
		for (String key : configManager.getConfigurationKeys(group + "."))
		{
			configManager.unsetConfiguration(group, key.substring(group.length() + 1));
		}
	}

	/** "Wiki Links" became "Card Links", with a shift-click choice: a player who turned links off keeps them off. */
	private static void cardLinks(ConfigManager configManager)
	{
		String legacy = configManager.getConfiguration(GROUP, "wikiItemLinks");
		if (legacy == null)
		{
			return;
		}
		if ("false".equals(legacy) && configManager.getConfiguration(GROUP, "cardLinks") == null)
		{
			configManager.setConfiguration(GROUP, "cardLinks", CardLinks.OFF);
		}
		configManager.unsetConfiguration(GROUP, "wikiItemLinks");
	}

	private static void skillColorMode(ConfigManager configManager)
	{
		String legacy = configManager.getConfiguration(GROUP, "skillCompletionColor");
		if (legacy == null)
		{
			return;
		}

		if (configManager.getConfiguration(GROUP, "skillColorMode") == null)
		{
			configManager.setConfiguration(GROUP, "skillColorMode",
				SkillColorMode.fromLegacyCompletionColor(legacy));
		}
		configManager.unsetConfiguration(GROUP, "skillCompletionColor");
	}

	/**
	 * "Skill Summary only" hid the skill cells because the summary carried its
	 * own skill grid. The summary is two totals since 2.4.0, so those players
	 * move to the activity tray: still a compact panel, still every skill. A
	 * saved value RuneLite can no longer read would otherwise fall silently to
	 * the main grid.
	 */
	private static void skillLocation(ConfigManager configManager)
	{
		if ("TOOLTIP".equals(configManager.getConfiguration(GROUP, "skillDisplay")))
		{
			configManager.setConfiguration(GROUP, "skillDisplay", SkillDisplay.TRAY);
		}
	}
}
