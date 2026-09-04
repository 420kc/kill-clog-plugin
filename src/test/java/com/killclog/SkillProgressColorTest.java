package com.killclog;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SkillProgressColorTest
{
	private static final Color K1 = new Color(1, 1, 1);
	private static final Color K2 = new Color(2, 2, 2);
	private static final Color K3 = new Color(3, 3, 3);
	private static final Color K4 = new Color(4, 4, 4);

	private final KillClogConfig enabled = config(true);

	@Test
	public void colorsEveryProgressionBoundary()
	{
		assertEquals(K4, SkillProgressColor.forLevel(1, enabled));
		assertEquals(K4, SkillProgressColor.forLevel(49, enabled));
		assertEquals(K3, SkillProgressColor.forLevel(50, enabled));
		assertEquals(K3, SkillProgressColor.forLevel(92, enabled));
		assertEquals(K2, SkillProgressColor.forLevel(93, enabled));
		assertEquals(K2, SkillProgressColor.forLevel(98, enabled));
		assertEquals(K1, SkillProgressColor.forLevel(99, enabled));
		assertEquals(K1, SkillProgressColor.forLevel(126, enabled));
	}

	@Test
	public void disabledHighlighterLeavesLevelsWhite()
	{
		assertEquals(Color.WHITE, SkillProgressColor.forLevel(1, config(false)));
		assertEquals(Color.WHITE, SkillProgressColor.forLevel(99, config(false)));
	}

	@Test
	public void skillDisplayDefaultsToTooltip()
	{
		assertEquals(SkillDisplay.TOOLTIP, enabled.skillDisplay());
	}

	private static KillClogConfig config(boolean highlighterEnabled)
	{
		return new KillClogConfig()
		{
			@Override
			public boolean completionistHighlighter()
			{
				return highlighterEnabled;
			}

			@Override
			public Color completedClogColor()
			{
				return K1;
			}

			@Override
			public Color missing1Color()
			{
				return K2;
			}

			@Override
			public Color inProgressClogColor()
			{
				return K3;
			}

			@Override
			public Color emptyClogColor()
			{
				return K4;
			}
		};
	}
}
