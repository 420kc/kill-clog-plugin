package com.killclog;

import java.awt.Color;

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
			return Cells.KC_COLOR;
		}
		if (config.skillColorMode() == SkillColorMode.SKILL_COLOR)
		{
			return config.skillLevelColor();
		}
		if (config.skillColorMode() == SkillColorMode.CLOG_PROGRESSION)
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
}
