package com.killclog;

import java.awt.Color;

final class SkillLevelColor
{
	private SkillLevelColor()
	{
	}

	static Color forLevel(int level, KillClogConfig config)
	{
		if (level >= 99 && config.useSkillCompletionColor())
		{
			return config.completedClogColor();
		}
		return config.skillLevelColor();
	}
}
