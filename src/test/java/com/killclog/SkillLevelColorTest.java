package com.killclog;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SkillLevelColorTest
{
	private static final Color LEVEL_COLOR = new Color(4, 4, 4);
	private static final Color COMPLETION_COLOR = new Color(1, 1, 1);
	private static final Color ONE_AWAY_COLOR = new Color(2, 2, 2);
	private static final Color IN_PROGRESS_COLOR = new Color(3, 3, 3);
	private static final Color EMPTY_COLOR = new Color(5, 5, 5);

	@Test
	public void usesConfiguredLevelColorBelow99()
	{
		KillClogConfig config = config(SkillColorMode.LEVEL_COMPLETION);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(1, true, -1, -1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(50, true, -1, -1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(93, true, -1, -1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(98, true, -1, -1, config));
	}

	@Test
	public void usesCompletionColorAt99AndAbove()
	{
		KillClogConfig config = config(SkillColorMode.LEVEL_COMPLETION);
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(99, true, -1, -1, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(126, true, -1, -1, config));
	}

	@Test
	public void skillColorModeNeverUsesCompletionOrProgressColors()
	{
		KillClogConfig config = config(SkillColorMode.SKILL_COLOR);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(50, true, 1, 4, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, true, 4, 4, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(126, true, 0, 4, config));
	}

	@Test
	public void legacyCompletionPreferenceMapsToEquivalentMode()
	{
		assertEquals(SkillColorMode.LEVEL_COMPLETION,
			SkillColorMode.fromLegacyCompletionColor("true"));
		assertEquals(SkillColorMode.SKILL_COLOR,
			SkillColorMode.fromLegacyCompletionColor("false"));
	}

	@Test
	public void clogProgressionUsesTheFullFourColorLadder()
	{
		KillClogConfig config = config(SkillColorMode.CLOG_PROGRESSION);
		assertEquals(EMPTY_COLOR, SkillLevelColor.forCell(99, true, 0, 4, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(99, true, 1, 4, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(99, true, 3, 4, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(1, true, 4, 4, config));
	}

	@Test
	public void clogProgressionFallsBackWhenItsTotalIsUnavailable()
	{
		KillClogConfig config = config(SkillColorMode.CLOG_PROGRESSION);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, true, -1, -1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, true, 0, 0, config));
	}

	@Test
	public void unsyncedSkillsKeepNormalWhiteNumbers()
	{
		KillClogConfig config = config(SkillColorMode.CLOG_PROGRESSION);
		assertEquals(Cells.KC_COLOR, SkillLevelColor.forCell(50, false, 1, 4, config));
		assertEquals(Cells.KC_COLOR, SkillLevelColor.forCell(99, false, 4, 4, config));
	}

	@Test
	public void defaultsMatchSkillColorContract()
	{
		KillClogConfig defaults = new KillClogConfig()
		{
		};
		assertEquals(new Color(255, 87, 0), defaults.skillLevelColor());
		assertEquals(SkillColorMode.LEVEL_COMPLETION, defaults.skillColorMode());
		assertEquals(SkillDisplay.FIXED, defaults.skillDisplay());
	}

	private static KillClogConfig config(SkillColorMode colorMode)
	{
		return new KillClogConfig()
		{
			@Override
			public Color skillLevelColor()
			{
				return LEVEL_COLOR;
			}

			@Override
			public SkillColorMode skillColorMode()
			{
				return colorMode;
			}

			@Override
			public Color completedClogColor()
			{
				return COMPLETION_COLOR;
			}

			@Override
			public Color missing1Color()
			{
				return ONE_AWAY_COLOR;
			}

			@Override
			public Color inProgressClogColor()
			{
				return IN_PROGRESS_COLOR;
			}

			@Override
			public Color emptyClogColor()
			{
				return EMPTY_COLOR;
			}
		};
	}
}
