package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
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
	public void wikiPageUrlEncodesBossPageNames()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_Sire",
			TooltipItemLink.wikiPageUrl("Abyssal Sire"));
		assertEquals("https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare",
			TooltipItemLink.wikiPageUrl("Phosani's Nightmare"));
	}

	@Test
	public void linkedTitleHoverTurnsWhiteOnlyWhenWikiLinksAreEnabled()
	{
		ImgTooltip tooltip = spriteTooltip(2);
		tooltip.setTitle("Abyssal Sire");
		tooltip.setTitleWikiPage("Abyssal Sire");

		moveMouse(tooltip, NativeTooltip.getInset() + 1, NativeTooltip.getInset() + 1);
		assertEquals(Color.WHITE, tooltip.titleColor());

		tooltip.setWikiLinksEnabled(false);
		moveMouse(tooltip, NativeTooltip.getInset() + 1, NativeTooltip.getInset() + 1);
		assertEquals(NativeTooltip.OSRS_ORANGE, tooltip.titleColor());
	}

	@Test
	public void bossWikiPageUsesCanonicalOverrides()
	{
		assertEquals("Barrows", PanelData.bossWikiPage(net.runelite.client.hiscore.HiscoreSkill.BARROWS_CHESTS));
		assertEquals("The Mimic", PanelData.bossWikiPage(net.runelite.client.hiscore.HiscoreSkill.MIMIC));
		assertEquals("Abyssal Sire", PanelData.bossWikiPage(net.runelite.client.hiscore.HiscoreSkill.ABYSSAL_SIRE));
	}

	@Test
	public void clogSummarySourceBadgeRevealsProvenanceInlineWithoutResizing()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		tooltip.setClogSources(true, true, false);

		Dimension idle = tooltip.getPreferredSize();
		assertEquals("Temple + RP", tooltip.sourceLine());

		tooltip.setSize(idle);
		moveMouse(tooltip, tooltip.getWidth() - NativeTooltip.getInset() - 1,
			NativeTooltip.getInset() + 1);
		assertTrue(tooltip.isTitleCornerHovered());
		// The reveal shares the title line; the tooltip must not grow for it.
		assertEquals(idle, tooltip.getPreferredSize());

		tooltip.setClogSources(true, false, false);
		assertEquals("TempleOSRS", tooltip.sourceLine());
		tooltip.setClogSources(false, true, false);
		assertEquals("RuneProfile", tooltip.sourceLine());
		tooltip.setClogSources(false, false, true);
		assertEquals("Kill Clog Sync", tooltip.sourceLine());
		tooltip.setClogSources(true, true, true);
		assertEquals("Kill Clog + Temple + RP", tooltip.sourceLine());
	}

	@Test
	public void unknownDenominatorRendersAsQuestionMarkOnEverySurface()
	{
		// A source that only reports obtained items has no category
		// denominator (totalItems = -1); every header formatter must render
		// that as "?" rather than claiming a total.
		assertEquals("12/?", TitleTooltip.progressCountText(12, -1));
		assertEquals("12/?", TitleTooltip.progressCountTextOrDash(12, -1));
		assertEquals("--", TitleTooltip.progressCountTextOrDash(-1, -1));
		assertEquals("12/100", TitleTooltip.progressCountText(12, 100));
	}

	@Test
	public void compareSurfaceRoutesPaintAndSizingThroughTheSharedFormatter()
	{
		// Counting spy: if either production path (painting or sizing)
		// reverts to a surface-local raw formatter, its seam count stays
		// flat and this test fails. Asserting the seam values alone cannot
		// prove the surfaces actually route through them.
		class SeamCountingTooltip extends CompareImgTooltip
		{
			int blueSeamCalls;
			int redSeamCalls;

			@Override
			String blueObtainedText()
			{
				blueSeamCalls++;
				return super.blueObtainedText();
			}

			@Override
			String redObtainedText()
			{
				redSeamCalls++;
				return super.redObtainedText();
			}
		}

		SeamCountingTooltip tip = new SeamCountingTooltip();
		tip.setShowSpriteGrids(false);
		tip.setTitle("Zulrah");
		tip.setBluePlayer("Blue", 12, -1, 0, false);
		tip.setRedPlayer("Red", -1, 10, 0, false);

		Dimension size = tip.getPreferredSize();
		assertTrue("sizing must route through the shared-formatter seams",
			tip.blueSeamCalls > 0 && tip.redSeamCalls > 0);

		int sizedBlue = tip.blueSeamCalls;
		int sizedRed = tip.redSeamCalls;
		tip.setSize(size);
		BufferedImage canvas = new BufferedImage(
			Math.max(size.width, 1), Math.max(size.height, 1), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = canvas.createGraphics();
		try
		{
			tip.paint(g2);
		}
		finally
		{
			g2.dispose();
		}
		assertTrue("painting must route through the shared-formatter seams",
			tip.blueSeamCalls > sizedBlue && tip.redSeamCalls > sizedRed);

		// And the seams themselves speak the shared formatter's language.
		assertEquals("12/?", tip.blueObtainedText());
		assertEquals("--", tip.redObtainedText());
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
		unranked.setData(clueHiscore(Collections.emptyMap()), true);

		Map<String, Integer> ranks = new HashMap<>();
		ranks.put("Clue Scrolls (all)", 1_234_567);
		ClueSummaryTooltip ranked = new ClueSummaryTooltip();
		ranked.setData(clueHiscore(ranks), true);

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

	@Test
	public void compareSpriteCountUsesEachPlayersObtainedCount()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		counts.put(995, 42);

		assertEquals(42, CompareImgTooltip.spriteCountForItem(995,
			Collections.singleton(995), counts));
		assertEquals(1, CompareImgTooltip.spriteCountForItem(995,
			Collections.emptySet(), counts));
		assertEquals(1, CompareImgTooltip.spriteCountForItem(995,
			Collections.singleton(995), Collections.emptyMap()));
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

	private static void moveMouse(TitleTooltip tooltip, int x, int y)
	{
		MouseEvent event = new MouseEvent(tooltip, MouseEvent.MOUSE_MOVED,
			System.currentTimeMillis(), 0, x, y, 0, false);
		for (MouseMotionListener listener : tooltip.getMouseMotionListeners())
		{
			listener.mouseMoved(event);
		}
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
