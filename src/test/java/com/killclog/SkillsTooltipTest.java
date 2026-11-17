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
		solo.setData(blue, null, false);
		assertEquals("24,000,000", solo.displayedXpText());
		assertEquals(blue.getOverallRank(), solo.displayedRank());

		SkillsTooltip redSide = new SkillsTooltip();
		redSide.setData(red, null, false);
		assertEquals("4,800,000,000", redSide.displayedXpText());
		assertEquals(red.getOverallRank(), redSide.displayedRank());
	}

	@Test
	public void missingHiscoresKeepBothIdleRowsEmpty()
	{
		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(null, null, false);
		assertEquals("--", solo.displayedXpText());
		assertEquals(-1, solo.displayedRank());
	}

	@Test
	public void theCardKeepsItsHeightWithOrWithoutDataAndNothingToHover()
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, 12_345), null, false);
		SkillsTooltip empty = new SkillsTooltip();
		empty.setData(null, null, false);
		// Same height with or without data, so a comparison pair never staggers.
		assertEquals(empty.getPreferredSize().height, tip.getPreferredSize().height);
		// No per-skill readout: the card reserves no hover line under its title.
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void unavailableOverallRankStaysUnknown()
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, -1), null, false);
		assertEquals(-1, tip.displayedRank());
	}

	@Test
	public void toMaxIsTheXpLeftToEveryNinetyNineAndUnknownWhileASkillIsOffTheHiscores()
	{
		java.util.Map<String, Long> xp = new java.util.HashMap<>();
		for (String skill : HiscoreService.skillNames())
		{
			xp.put(skill, 13_034_431L);
		}
		assertEquals(0, SkillsTooltip.xpToMax(withXp(xp)));
		xp.put(HiscoreService.skillNames()[0], 13_034_331L);
		xp.put(HiscoreService.skillNames()[1], 200_000_000L);
		assertEquals("a skill past 99 never pays for another", 100, SkillsTooltip.xpToMax(withXp(xp)));
		xp.remove(HiscoreService.skillNames()[2]);
		assertEquals(-1, SkillsTooltip.xpToMax(withXp(xp)));
		assertEquals(-1, SkillsTooltip.xpToMax(null));
	}

	@Test
	public void templesRowsShowForEitherSideSoAPairLinesUp()
	{
		ClogResult onTemple = new ClogResult("A", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			null, AccountType.IRONMAN);
		onTemple.setTempleEhp(3043.07);
		onTemple.setTempleEhc(3417.36);
		org.junit.Assert.assertTrue(SkillsTooltip.onTemple(onTemple));
		org.junit.Assert.assertFalse(SkillsTooltip.onTemple(null));

		SkillsTooltip withTemple = new SkillsTooltip();
		withTemple.setData(hiscores(24_000_000L, 12_345), onTemple, true);
		SkillsTooltip without = new SkillsTooltip();
		without.setData(hiscores(24_000_000L, 12_345), null, true);
		SkillsTooltip plain = new SkillsTooltip();
		plain.setData(hiscores(24_000_000L, 12_345), null, false);
		assertEquals(withTemple.getPreferredSize().height, without.getPreferredSize().height);
		org.junit.Assert.assertTrue(withTemple.getPreferredSize().height > plain.getPreferredSize().height);
	}

	@Test
	public void efficientHoursBossedIsTheCombatSummarysEhb()
	{
		// Bossed repeats the Combat Summary's EHB, from the same rule, for anyone with hiscores, Temple or not.
		HiscoreResult bosser = TestViews.hiscore(AccountType.IRONMAN, Collections.singletonMap("Zulrah", 1_000),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1);
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(bosser, null, false);
		double ehb = TestViews.field(tip, "ehb");
		org.junit.Assert.assertTrue(ehb > 0);
		assertEquals(LookupQueries.ehb(bosser, null), ehb, 1e-9);
		assertEquals(EhbRates.compute(bosser, AccountType.IRONMAN), ehb, 1e-9);
	}

	@Test
	public void theMaxBarUnderToMaxFillsWithTheShareOfMaxXpNotOfTotalLevel()
	{
		// Every skill at 92: half of 99's XP, though 92 is 93% of 99 as a level (Dylan 11/17: XP, not levels).
		java.util.Map<String, Long> xp = new java.util.HashMap<>();
		for (String skill : HiscoreService.skillNames())
		{
			xp.put(skill, (long) net.runelite.api.Experience.getXpForLevel(92));
		}
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(withXp(xp), null, false);
		java.awt.image.BufferedImage card = TestViews.paint(tip);
		int[] fill = TestViews.longestRun(card, TitleTooltip.CLOG_YELLOW);
		int track = card.getWidth() - 2 * NativeTooltip.getInset();
		double share = (double) net.runelite.api.Experience.getXpForLevel(92)
			/ net.runelite.api.Experience.getXpForLevel(99);
		assertEquals("the fill starts at the card's inset", NativeTooltip.getInset(), fill[0]);
		assertEquals("half the track, the share of max's XP", Math.round(track * share), fill[2]);
		assertEquals("3 px tall", CardBody.BAR_HEIGHT, fill[3]);
		assertEquals(share, SkillsTooltip.maxShare(SkillsTooltip.xpToMax(withXp(xp))), 1e-12);
		// Straight under To max's number, with Efficient Hours after it.
		int number = TestViews.lowestRowAbove(card, NativeTooltip.valueColor, fill[1]);
		org.junit.Assert.assertTrue("the bar sits just under To max, " + (fill[1] - number) + " px below it",
			number > 0 && fill[1] - number <= 8);
		org.junit.Assert.assertTrue("Efficient Hours comes after the bar",
			TestViews.lowestRowAbove(card, NativeTooltip.labelColor, card.getHeight()) > fill[1]);
	}

	@Test
	public void aMaxedPlayersMaxBarIsFullAndGreenAndAnUnknownOneEmpty()
	{
		java.util.Map<String, Long> xp = new java.util.HashMap<>();
		for (String skill : HiscoreService.skillNames())
		{
			xp.put(skill, 13_034_431L);
		}
		SkillsTooltip maxed = new SkillsTooltip();
		maxed.setData(withXp(xp), null, false);
		java.awt.image.BufferedImage card = TestViews.paint(maxed);
		int[] fill = TestViews.longestRun(card, TitleTooltip.CLOG_GREEN);
		assertEquals(NativeTooltip.getInset(), fill[0]);
		assertEquals("the whole track", card.getWidth() - 2 * NativeTooltip.getInset(), fill[2]);
		assertEquals(CardBody.BAR_HEIGHT, fill[3]);
		assertEquals(0, TestViews.longestRun(card, TitleTooltip.CLOG_YELLOW)[2]);

		SkillsTooltip unknown = new SkillsTooltip();
		unknown.setData(null, null, false);
		java.awt.image.BufferedImage empty = TestViews.paint(unknown);
		assertEquals(0, TestViews.longestRun(empty, TitleTooltip.CLOG_YELLOW)[2]);
		assertEquals(0, TestViews.longestRun(empty, TitleTooltip.CLOG_GREEN)[2]);
		assertEquals(0, SkillsTooltip.maxShare(-1), 0);
	}

	private static HiscoreResult withXp(java.util.Map<String, Long> xp)
	{
		return TestViews.hiscore(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), xp, 2_376, 1, 126, 1);
	}

	private static HiscoreResult hiscores(long totalXp, int overallRank)
	{
		return TestViews.hiscore(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.singletonMap("attack", 99),
			Collections.singletonMap("attack", 987), Collections.singletonMap("attack", 13_034_431L),
			2_376, totalXp, 126, overallRank);
	}
}
