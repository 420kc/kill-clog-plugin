package com.killclog;

import java.awt.Color;

/**
 * A skill cell's level colour: a light grey before a sync, else the setting's mode (one skill colour, the skill's
 * Skill Clog progress, or 99+ completion).
 */
final class SkillLevelColor
{
	private SkillLevelColor()
	{
	}

	static Color forCell(int level, boolean synced, int obtained, int total,
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
		if (level >= 99)
		{
			return config.completedClogColor();
		}
		return config.skillLevelColor();
	}

	/** Clog Progression needs Skill Clogs on; with them off it reads as the 99+ default. */
	static boolean usesClogProgress(KillClogConfig config)
	{
		return config.enableSkillClogs()
			&& config.skillColorMode() == SkillColorMode.CLOG_PROGRESSION;
	}
}
