package com.killclog;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TitleTooltipTest
{
	@Test
	public void scoreTextGroupsThousandsAndDashesAbsent()
	{
		assertEquals("--", TitleTooltip.scoreText(0));
		assertEquals("--", TitleTooltip.scoreText(-1));
		assertEquals("42", TitleTooltip.scoreText(42));
		assertEquals("1,234,567", TitleTooltip.scoreText(1_234_567));
	}

	@Test
	public void rankTailTextIsHashPrefixedAndGrouped()
	{
		assertEquals(" #1,234,567", TitleTooltip.rankTailText(1_234_567));
	}

	@Test
	public void completionColorUsesOsrsStoplight()
	{
		assertEquals(Color.RED, TitleTooltip.completionColor(0, 5));
		assertEquals(Color.YELLOW, TitleTooltip.completionColor(1, 5));
		assertEquals(Color.GREEN, TitleTooltip.completionColor(5, 5));
	}

	@Test
	public void completionColorKeepsUnknownProgressNeutral()
	{
		assertEquals(Color.YELLOW, TitleTooltip.completionColor(-1, 5));
		assertEquals(Color.YELLOW, TitleTooltip.completionColor(0, 0));
	}

	@Test
	public void standardSpriteTooltipsShrinkShortCatalogsToFourColumns()
	{
		ImgTooltip shortCatalog = spriteTooltip(2);
		ImgTooltip fiveItemCatalog = spriteTooltip(5);

		assertTrue(shortCatalog.getPreferredSize().width < fiveItemCatalog.getPreferredSize().width);
	}

	@Test
	public void clueSummaryWidthUsesActualRankTail()
	{
		ClueSummaryTooltip unranked = new ClueSummaryTooltip();
		unranked.setData(clueHiscore(Collections.emptyMap()));

		Map<String, Integer> ranks = new HashMap<>();
		ranks.put("Clue Scrolls (all)", 1_234_567);
		ClueSummaryTooltip ranked = new ClueSummaryTooltip();
		ranked.setData(clueHiscore(ranks));

		assertTrue(unranked.getPreferredSize().width < ranked.getPreferredSize().width);
	}

	@Test
	public void pvpSummaryWidthGrowsWithLargeScore()
	{
		PvpSummaryTooltip small = new PvpSummaryTooltip();
		small.setData(pvpHiscore("Soul Wars Zeal", 42), null);

		PvpSummaryTooltip large = new PvpSummaryTooltip();
		large.setData(pvpHiscore("Soul Wars Zeal", 1_234_567), null);

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
	}

	@Test
	public void pvmSummaryWidthGrowsWithLargeTotalKills()
	{
		PvmSummaryTooltip small = new PvmSummaryTooltip();
		small.setData(126, 999, 50, 60, null, 0);

		// Far past the old "999,999" placeholder ceiling: the width must track the
		// real total-kills string, not a capped guess.
		PvmSummaryTooltip large = new PvmSummaryTooltip();
		large.setData(126, 1_234_567_890, 50, 60, null, 0);

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
	}

	@Test
	public void compareClueWidthGrowsWithLargeScore()
	{
		CompareClueSummaryTooltip small = new CompareClueSummaryTooltip();
		small.setBlueData("Blue", pvpHiscore("Clue Scrolls (all)", 42));
		small.setRedData("Red", pvpHiscore("Clue Scrolls (all)", 42));

		CompareClueSummaryTooltip large = new CompareClueSummaryTooltip();
		large.setBlueData("Blue", pvpHiscore("Clue Scrolls (all)", 1_234_567));
		large.setRedData("Red", pvpHiscore("Clue Scrolls (all)", 42));

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
	}

	@Test
	public void comparePvpWidthGrowsWithLongValue()
	{
		ComparePvpSummaryTooltip small = new ComparePvpSummaryTooltip();
		small.setBlueData("Blue", pvpHiscore("Soul Wars Zeal", 42), null);
		small.setRedData("Red", pvpHiscore("Soul Wars Zeal", 42), null);

		ComparePvpSummaryTooltip large = new ComparePvpSummaryTooltip();
		large.setBlueData("Blue", pvpHiscore("Soul Wars Zeal", 1_234_567), null);
		large.setRedData("Red", pvpHiscore("Soul Wars Zeal", 42), null);

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
	}

	@Test
	public void comparePvmWidthGrowsWithLongValue()
	{
		// The value column is measured from real content (here the most-killed
		// line), not a placeholder, so a long value widens the tooltip.
		ComparePvmSummaryTooltip small = new ComparePvmSummaryTooltip();
		small.setBlueData("Blue", 126, 999, 50, 60, "Rat", 5);
		small.setRedData("Red", 126, 999, 50, 60, "Rat", 5);

		ComparePvmSummaryTooltip large = new ComparePvmSummaryTooltip();
		large.setBlueData("Blue", 126, 999, 50, 60, "Dagannoth Supreme Prime Rex", 1_234_567);
		large.setRedData("Red", 126, 999, 50, 60, "Rat", 5);

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
	}

	private static HiscoreResult pvpHiscore(String activity, int score)
	{
		Map<String, Integer> scores = new HashMap<>();
		scores.put(activity, score);
		return new HiscoreResult(AccountType.REGULAR,
			Collections.emptyMap(), Collections.emptyMap(), scores, Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}

	private static ImgTooltip spriteTooltip(int itemCount)
	{
		ImgTooltip tooltip = new ImgTooltip(5);
		tooltip.setTitle("A");
		tooltip.setObtained(0, itemCount);
		tooltip.setItems(itemCount,
			itemCount == 2 ? Arrays.asList(1, 2) : Arrays.asList(1, 2, 3, 4, 5),
			Collections.emptySet(), Collections.emptyMap(), null);
		return tooltip;
	}

	private static HiscoreResult clueHiscore(Map<String, Integer> ranks)
	{
		Map<String, Integer> scores = new HashMap<>();
		scores.put("Clue Scrolls (all)", 42);
		return new HiscoreResult(AccountType.REGULAR,
			Collections.emptyMap(), Collections.emptyMap(), scores, ranks,
			Collections.emptyMap(), 0, 0, 0, -1);
	}
}
