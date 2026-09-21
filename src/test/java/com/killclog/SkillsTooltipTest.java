package com.killclog;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SkillsTooltipTest
{
	@Test
	public void totalXpAndOverallRankFillIdleRowsInBothSummaryModes()
	{
		HiscoreResult blue = hiscores(24_000_000L, 12_345);
		HiscoreResult red = hiscores(4_800_000_000L, 42);

		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(blue);
		assertEquals("24,000,000", solo.displayedXpText());
		assertEquals(blue.getOverallRank(), solo.displayedRank());

		SkillsTooltip redSide = new SkillsTooltip();
		redSide.setData(red);
		assertEquals("4,800,000,000", redSide.displayedXpText());
		assertEquals(red.getOverallRank(), redSide.displayedRank());
	}

	@Test
	public void missingHiscoresKeepBothIdleRowsEmpty()
	{
		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(null);
		assertEquals("--", solo.displayedXpText());
		assertEquals(-1, solo.displayedRank());
	}

	@Test
	public void theCardIsTwoRowsAndNothingToHover()
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, 12_345));
		SkillsTooltip empty = new SkillsTooltip();
		empty.setData(null);
		// Same height with or without data, so a comparison pair never staggers.
		assertEquals(empty.getPreferredSize().height, tip.getPreferredSize().height);
		// No per-skill readout: the card reserves no hover line under its title.
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void unavailableOverallRankStaysUnknown()
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, -1));
		assertEquals(-1, tip.displayedRank());
	}

	private static HiscoreResult hiscores(long totalXp, int overallRank)
	{
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.singletonMap("attack", 99),
			Collections.singletonMap("attack", 987), Collections.singletonMap("attack", 13_034_431L),
			2_376, totalXp, 126, overallRank);
	}
}
