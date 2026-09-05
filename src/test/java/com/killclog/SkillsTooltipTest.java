package com.killclog;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SkillsTooltipTest
{
	@Test
	public void totalXpFillsTheIdleXpRowInBothSummaryModes()
	{
		HiscoreResult blue = hiscores(24_000_000L);
		HiscoreResult red = hiscores(4_800_000_000L);

		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(blue);
		assertEquals("24,000,000", solo.displayedXpText());

		SkillsTooltip redSide = new SkillsTooltip();
		redSide.setData(red);
		assertEquals("4,800,000,000", redSide.displayedXpText());
	}

	@Test
	public void missingHiscoresKeepTheIdleXpRowEmpty()
	{
		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(null);
		assertEquals("--", solo.displayedXpText());
	}

	private static HiscoreResult hiscores(long totalXp)
	{
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), 2_376, totalXp, 126, 1);
	}
}
