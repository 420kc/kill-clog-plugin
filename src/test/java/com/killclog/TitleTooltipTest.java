package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
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
	public void firstTimeSetupUsesTheCollectionLogSearchFlow()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setFirstTimeSetup();

		assertEquals("First Time Setup", tooltip.getTitle());
		Dimension size = tooltip.getPreferredSize();
		assertTrue(size.width <= 300);
		assertTrue(size.height > 3 * 12);

		tooltip.setSize(size);
		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();
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
		assertEquals("--/100", TitleTooltip.progressPlaceholderText(100));
		assertEquals("--/?", TitleTooltip.progressPlaceholderText(-1));
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
	public void sideBySideWidthTracksItsChildren()
	{
		PvpSummaryTooltip blueSmall = new PvpSummaryTooltip();
		blueSmall.setData(pvpHiscore("Soul Wars Zeal", 42), null);
		PvpSummaryTooltip redSmall = new PvpSummaryTooltip();
		redSmall.setData(pvpHiscore("Soul Wars Zeal", 42), null);
		SideBySideTooltip small = new SideBySideTooltip("Blue", blueSmall, "Red", redSmall);

		PvpSummaryTooltip blueLarge = new PvpSummaryTooltip();
		blueLarge.setData(pvpHiscore("Soul Wars Zeal", 1_234_567), null);
		PvpSummaryTooltip redLarge = new PvpSummaryTooltip();
		redLarge.setData(pvpHiscore("Soul Wars Zeal", 42), null);
		SideBySideTooltip large = new SideBySideTooltip("Blue", blueLarge, "Red", redLarge);

		assertTrue(small.getPreferredSize().width < large.getPreferredSize().width);
		// Doublewide by construction: both children plus card chrome.
		assertTrue(small.getPreferredSize().width
			> blueSmall.getPreferredSize().width + redSmall.getPreferredSize().width);
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
	public void duplicateHoverCountIsExactUntilTenThousandThenRoundsToNearestK()
	{
		assertNull(TooltipItemHover.duplicateCountText(1));
		assertEquals("x2", TooltipItemHover.duplicateCountText(2));
		assertEquals("x9,999", TooltipItemHover.duplicateCountText(9_999));
		assertEquals("x10k", TooltipItemHover.duplicateCountText(10_000));
		assertEquals("x10k", TooltipItemHover.duplicateCountText(10_499));
		assertEquals("x11k", TooltipItemHover.duplicateCountText(10_500));
		assertEquals("x1,235k", TooltipItemHover.duplicateCountText(1_234_567));
	}

	@Test
	public void itemHoverCarriesTheHoveredPlayersDuplicateCount()
	{
		JPanel component = new JPanel();
		TooltipItemHover hover = new TooltipItemHover(component);
		hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
			1, 995, "Coins", new Rectangle(5, 5, 15, 15), true, 12_345)));

		moveMouse(component, 6, 6);

		assertEquals("Coins", hover.hoveredItemName());
		assertTrue(hover.hoveredItemObtained());
		assertEquals("x12k", hover.hoveredDuplicateCountText());

		hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
			1, 995, "Coins", new Rectangle(5, 5, 15, 15), false, 12_345)));
		moveMouse(component, 6, 6);
		assertFalse(hover.hoveredItemObtained());
		assertNull(hover.hoveredDuplicateCountText());
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

	private static void moveMouse(JComponent tooltip, int x, int y)
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
