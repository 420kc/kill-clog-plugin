package com.killclog;

import java.awt.Color;
import java.util.*;

/**
 * A skill cell's level colour: a light grey before a sync, else the setting's mode (one skill colour, the skill's
 * Skill Clog progress, or Level Progression).
 */
final class SkillLevelColor
{
	// Level Progression's four tiers, empty to completed: the level each starts at, standard then virtual (Dylan 11/7).
	private static final List<String[]> TIERS = CatalogTsv.rows(SkillLevelColor.class, "level-tiers.tsv", 3);

	private SkillLevelColor()
	{
	}

	static Color forCell(int level, boolean virtual, boolean synced, int obtained, int total,
		KillClogConfig config)
	{
		if (!synced)
		{
			return KillClogColors.KC_COLOR;
		}
		if (config.skillColorMode() == SkillColorMode.SKILL_COLOR)
		{
			return config.skillLevelColor();
		}
		if (usesClogProgress(config))
		{
			return obtained >= 0 && total > 0
				? ClogHelper.clogColor(obtained, total, config)
				: config.skillLevelColor();
		}
		return levelColor(level, virtual, config);
	}

	/** Level Progression: the Progress Highlighter colour of the highest tier the level has reached. */
	static Color levelColor(int level, boolean virtual, KillClogConfig config)
	{
		Color[] colors = {config.emptyClogColor(), config.inProgressClogColor(), config.missing1Color(),
			config.completedClogColor()};
		Color color = colors[0];
		for (int tier = 0; tier < TIERS.size(); tier++)
		{
			if (level >= Integer.parseInt(TIERS.get(tier)[virtual ? 2 : 1]))
			{
				color = colors[tier];
			}
		}
		return color;
	}

	/** Clog Progression needs Skill Clogs on; with them off it reads as Level Progression. */
	static boolean usesClogProgress(KillClogConfig config)
	{
		return config.enableSkillClogs()
			&& config.skillColorMode() == SkillColorMode.CLOG_PROGRESSION;
	}
}
