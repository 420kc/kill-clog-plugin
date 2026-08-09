package com.killclog;

import static org.junit.Assert.assertEquals;

import java.awt.Dimension;
import org.junit.Test;

public class SummaryTooltipTest
{
	@Test
	public void templeEhpAddsOneSinglePlayerRowOnlyWhenAvailable()
	{
		SummaryTooltip base = summary();
		SummaryTooltip temple = summary();
		temple.setTempleEhp(3003.8553);
		SummaryTooltip missing = summary();
		missing.setTempleEhp(-1);

		Dimension baseSize = base.getContentSize(400);
		assertEquals(baseSize.height + NativeTooltip.LINE_HEIGHT,
			temple.getContentSize(400).height);
		assertEquals(baseSize.height, missing.getContentSize(400).height);
	}

	@Test
	public void comparisonTempleEhpIsIndependentPerPlayer()
	{
		ComparePlayerSummaryTooltip base = comparison();
		ComparePlayerSummaryTooltip blueTemple = comparison();
		blueTemple.setBlueTempleEhp(3003.8553);
		ComparePlayerSummaryTooltip bothTemple = comparison();
		bothTemple.setBlueTempleEhp(3003.8553);
		bothTemple.setRedTempleEhp(1803.6);

		Dimension baseSize = base.getContentSize(400);
		assertEquals(baseSize.height + NativeTooltip.LINE_HEIGHT,
			blueTemple.getContentSize(400).height);
		assertEquals(baseSize.height + NativeTooltip.LINE_HEIGHT * 2,
			bothTemple.getContentSize(400).height);
	}

	private static SummaryTooltip summary()
	{
		SummaryTooltip tooltip = new SummaryTooltip();
		tooltip.setData("420 kc", 420, null, null, "Ironman", null);
		return tooltip;
	}

	private static ComparePlayerSummaryTooltip comparison()
	{
		ComparePlayerSummaryTooltip tooltip = new ComparePlayerSummaryTooltip();
		tooltip.setBlueData("420 kc", 420, null, "Ironman", null);
		tooltip.setRedData("CBC", 69, null, "Normal", null);
		return tooltip;
	}
}
