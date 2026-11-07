package com.killclog;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillLevelColorTest
{
	private static final Color LEVEL_COLOR = new Color(4, 4, 4);
	private static final Color COMPLETION_COLOR = new Color(1, 1, 1);
	private static final Color ONE_AWAY_COLOR = new Color(2, 2, 2);
	private static final Color IN_PROGRESS_COLOR = new Color(3, 3, 3);
	private static final Color EMPTY_COLOR = new Color(5, 5, 5);

	// Level Progression (Dylan 11/7): the four Progress Highlighter colours by level, a step up with virtual levels.
	@Test
	public void levelProgressionClimbsTheFourColorsByStandardLevel()
	{
		KillClogConfig config = config(SkillColorMode.LEVEL_COMPLETION);
		assertEquals(EMPTY_COLOR, SkillLevelColor.forCell(1, false, true, -1, -1, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(2, false, true, -1, -1, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(91, false, true, -1, -1, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(92, false, true, -1, -1, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(98, false, true, -1, -1, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(99, false, true, -1, -1, config));
	}

	@Test
	public void levelProgressionWithVirtualLevelsCountsTowardTwoHundredMillion()
	{
		KillClogConfig config = config(SkillColorMode.LEVEL_COMPLETION);
		assertEquals(EMPTY_COLOR, SkillLevelColor.forCell(1, true, true, -1, -1, config));
		assertEquals(EMPTY_COLOR, SkillLevelColor.forCell(91, true, true, -1, -1, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(92, true, true, -1, -1, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(99, true, true, -1, -1, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(119, true, true, -1, -1, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(120, true, true, -1, -1, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(125, true, true, -1, -1, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(126, true, true, -1, -1, config));
	}

	@Test
	public void levelProgressionNamesItself()
	{
		// The saved key stays LEVEL_COMPLETION, so a player's choice carries over.
		assertEquals("Level Progression", SkillColorMode.LEVEL_COMPLETION.toString());
	}

	@Test
	public void skillColorModeNeverUsesCompletionOrProgressColors()
	{
		KillClogConfig config = config(SkillColorMode.SKILL_COLOR);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(50, false, true, 1, 4, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, false, true, 4, 4, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(126, false, true, 0, 4, config));
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
		assertEquals(EMPTY_COLOR, SkillLevelColor.forCell(99, false, true, 0, 4, config));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(99, false, true, 1, 4, config));
		assertEquals(ONE_AWAY_COLOR, SkillLevelColor.forCell(99, false, true, 3, 4, config));
		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(1, false, true, 4, 4, config));
	}

	@Test
	public void clogProgressionFallsBackWhenItsTotalIsUnavailable()
	{
		KillClogConfig config = config(SkillColorMode.CLOG_PROGRESSION);
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, false, true, -1, -1, config));
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, false, true, 0, 0, config));
	}

	@Test
	public void clogProgressionReadsAsTheLevelDefaultWhileSkillClogsAreOff()
	{
		KillClogConfig on = config(SkillColorMode.CLOG_PROGRESSION);
		KillClogConfig off = config(SkillColorMode.CLOG_PROGRESSION, false);
		assertTrue(SkillLevelColor.usesClogProgress(on));
		assertFalse(SkillLevelColor.usesClogProgress(off));
		assertFalse(SkillLevelColor.usesClogProgress(config(SkillColorMode.LEVEL_COMPLETION)));

		assertEquals(COMPLETION_COLOR, SkillLevelColor.forCell(99, false, true, 1, 4, off));
		assertEquals(IN_PROGRESS_COLOR, SkillLevelColor.forCell(50, false, true, 4, 4, off));
		// The other modes never depended on Skill Clogs.
		assertEquals(LEVEL_COLOR, SkillLevelColor.forCell(99, false, true, 4, 4,
			config(SkillColorMode.SKILL_COLOR, false)));
	}

	@Test
	public void unsyncedSkillsKeepNormalWhiteNumbers()
	{
		KillClogConfig config = config(SkillColorMode.CLOG_PROGRESSION);
		assertEquals(KillClogColors.KC_COLOR, SkillLevelColor.forCell(50, false, false, 1, 4, config));
		assertEquals(KillClogColors.KC_COLOR, SkillLevelColor.forCell(99, false, false, 4, 4, config));
	}

	@Test
	public void defaultsMatchSkillColorContract()
	{
		KillClogConfig defaults = new KillClogConfig()
		{
		};
		assertEquals(new Color(255, 87, 0), defaults.skillLevelColor());
		assertEquals(SkillColorMode.CLOG_PROGRESSION, defaults.skillColorMode());
		assertEquals(SkillDisplay.FIXED, defaults.skillDisplay());
	}

	private static KillClogConfig config(SkillColorMode colorMode)
	{
		return config(colorMode, true);
	}

	private static KillClogConfig config(SkillColorMode colorMode, boolean skillClogs)
	{
		return new KillClogConfig()
		{
			@Override
			public boolean enableSkillClogs()
			{
				return skillClogs;
			}

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
