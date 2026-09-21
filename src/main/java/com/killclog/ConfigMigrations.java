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
