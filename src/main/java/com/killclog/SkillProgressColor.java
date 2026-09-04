package com.killclog;

import java.awt.Color;

final class SkillProgressColor
{
	private SkillProgressColor()
	{
	}

	static Color forLevel(int level, KillClogConfig config)
	{
		if (!config.completionistHighlighter())
		{
			return Color.WHITE;
		}
		if (level >= 99)
		{
			return config.completedClogColor();
		}
		if (level >= 93)
		{
			return config.missing1Color();
		}
		if (level >= 50)
		{
			return config.inProgressClogColor();
		}
		return config.emptyClogColor();
	}
}
