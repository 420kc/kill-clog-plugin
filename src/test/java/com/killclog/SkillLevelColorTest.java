package com.killclog;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SkillLevelColorTest
{
	private static final Color LEVEL_COLOR = new Color(4, 4, 4);
	private static final Color COMPLETION_COLOR = new Color(1, 1, 1);

	@Test
	public void usesConfiguredLevelColorBelow99()
	{
		KillClogConfig config = config(true);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(50, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(93, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(98, config));
	}

	@Test
	public void usesCompletionColorAt99AndAbove()
	{
		KillClogConfig config = config(true);
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forLevel(99, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forLevel(126, config));
	}

	@Test
	public void completionColorCanBeDisabled()
	{
		KillClogConfig config = config(false);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(99, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forLevel(126, config));
	}

	@Test
	public void defaultsMatchSkillColorContract()
	{
		KillClogConfig defaults = new KillClogConfig()
		{
		};
		assertEquals(new Color(255, 87, 0), defaults.skillLevelColor());
		assertTrue(defaults.useSkillCompletionColor());
		assertEquals(SkillDisplay.TOOLTIP, defaults.skillDisplay());
	}

	private static KillClogConfig config(boolean useCompletionColor)
	{
		return new KillClogConfig()
		{
			@Override
			public Color skillLevelColor()
			{
				return LEVEL_COLOR;
			}

			@Override
			public boolean useSkillCompletionColor()
			{
				return useCompletionColor;
			}

			@Override
			public Color completedClogColor()
			{
				return COMPLETION_COLOR;
			}
		};
	}
}
