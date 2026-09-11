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
import java.util.List;
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
	public void clogSummaryProvenanceShowsOnlySyncedSourcesInStableOrder()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		tooltip.setClogSources(true, true, true);
		assertEquals(Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile"),
			tooltip.sourceNames());

		tooltip.setClogSources(false, true, false);
		assertEquals(Collections.singletonList("RuneProfile"), tooltip.sourceNames());
		tooltip.setClogSources(false, false, false);
		assertTrue(tooltip.sourceNames().isEmpty());
	}

	@Test
	public void clogSummaryTierLegendShowsEveryTierAndRevealsItsRange()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		Map<String, BufferedImage> tierIcons = new HashMap<>();
		for (String tier : ClogHelper.CLOG_TIERS)
		{
			BufferedImage icon = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
			Graphics2D iconGraphics = icon.createGraphics();
			iconGraphics.setColor(Color.WHITE);
			iconGraphics.fillRect(0, 0, 13, 13);
			iconGraphics.dispose();
			tierIcons.put(tier, icon);
		}
		tooltip.setTierData(1_150, 1_701, tierIcons);
		BufferedImage currentTier = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D currentGraphics = currentTier.createGraphics();
		currentGraphics.setColor(Color.MAGENTA);
		currentGraphics.fillRect(0, 0, 32, 32);
		currentGraphics.dispose();
		tooltip.setCurrentTierSprite(currentTier);
		assertEquals("1,150/1,701", ClogSummaryTooltip.progressText(1_150, 1_701));
		assertEquals(153, ClogSummaryTooltip.tierLegendWidth());
		assertEquals(32, tooltip.getHeaderDecorationWidth());

		Dimension size = tooltip.getPreferredSize();
		tooltip.setSize(size);
		BufferedImage rendered = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = rendered.createGraphics();
		int bodyY = tooltip.paintHeader(graphics, size.width);
		tooltip.paint(graphics);
		graphics.dispose();

		int runeIndex = 6;
		int tierY = bodyY + NativeTooltip.LINE_HEIGHT;
		int legendX = ClogSummaryTooltip.tierLegendStartX(size.width);
		assertEquals(Color.MAGENTA, new Color(rendered.getRGB(
			size.width - NativeTooltip.getInset() - 16,
			NativeTooltip.getInset() + 16), true));
		assertEquals(TitleTooltip.CLOG_GREEN,
			new Color(rendered.getRGB(legendX + runeIndex * 17, tierY), true));
		Color reached = new Color(rendered.getRGB(legendX + 8, tierY + 8), true);
		Color unreached = new Color(rendered.getRGB(legendX + 7 * 17 + 8, tierY + 8), true);
		assertTrue(reached.getRed() > unreached.getRed());
		moveMouse(tooltip,
			legendX + runeIndex * 17 + 8,
			tierY + 8);
		assertNull(tooltip.getTitleHoverText());
		assertEquals("Rune: 1,100-1,199", tooltip.getHeaderHoverLineText());
	}

	@Test
	public void clogSummaryAlwaysKeepsFiveCanonicalCategoryRows()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		assertCategoryProgress(tooltip.categoryProgress(),
			new int[]{-1, -1, -1, -1, -1}, new int[]{-1, -1, -1, -1, -1});

		tooltip.setCategoryProgress(Arrays.asList(
			new ClogHelper.ClogGroupProgress("Other", 22, 44),
			new ClogHelper.ClogGroupProgress("Bosses", 11, 33)));
		assertCategoryProgress(tooltip.categoryProgress(),
			new int[]{11, -1, -1, -1, 22}, new int[]{33, -1, -1, -1, 44});

		tooltip.setCategoryPreview(tooltip.categoryProgress());
		assertCategoryProgress(tooltip.categoryProgress(),
			new int[]{-1, -1, -1, -1, -1}, new int[]{33, -1, -1, -1, 44});
	}

	@Test
	public void clogSummaryProvenanceRowsAreCenteredForOneTwoAndThreeSources()
	{
		int width = 101;
		for (int count = 1; count <= 3; count++)
		{
			int rowWidth = ClogSummaryTooltip.sourceRowWidth(count);
			int startX = ClogSummaryTooltip.sourceRowStartX(width, count);
			assertTrue(Math.abs(width - (startX * 2 + rowWidth)) <= 1);
		}
	}

	@Test
	public void clogSummaryProviderHoverUsesTheReservedSourceLineWithoutResizing()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		tooltip.setClogSources(true, true, true);
		Dimension idle = tooltip.getPreferredSize();
		tooltip.setSize(idle);

		Graphics2D graphics = new BufferedImage(
			idle.width, idle.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();

		List<String> names = Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile");
		for (String name : names)
		{
			assertTrue(moveMouseToHeaderHover(tooltip, name));
			assertNull(tooltip.getTitleHoverText());
			assertEquals("Clog Summary", tooltip.getTitle());
			assertEquals(idle, tooltip.getPreferredSize());
		}
	}

	@Test
	public void clogSummaryProvenanceFooterIsAbsentWithoutSyncedSources()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		Dimension withoutSources = tooltip.getPreferredSize();

		tooltip.setClogSources(true, false, false);
		assertTrue(tooltip.getPreferredSize().height > withoutSources.height);
		tooltip.setClogSources(false, false, false);
		assertEquals(withoutSources, tooltip.getPreferredSize());
	}

	@Test
	public void comparisonClogCardsKeepIndependentProviderHover()
	{
		ClogSummaryTooltip blue = new ClogSummaryTooltip();
		blue.setTitle("Clog Summary");
		blue.setClogSources(true, false, false);
		ClogSummaryTooltip red = new ClogSummaryTooltip();
		red.setTitle("Clog Summary");
		red.setClogSources(false, true, false);
		SideBySideTooltip pair = new SideBySideTooltip("Blue", blue, "Red", red);
		Dimension size = pair.getPreferredSize();
		pair.setSize(size);

		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		pair.paint(graphics);
		graphics.dispose();

		assertTrue(moveMouseToHeaderHover(blue, "TempleOSRS"));
		assertEquals("TempleOSRS", blue.getHeaderHoverLineText());
		assertNull(red.getHeaderHoverLineText());

		assertTrue(moveMouseToHeaderHover(red, "RuneProfile"));
		assertEquals("RuneProfile", red.getHeaderHoverLineText());
		assertNull(blue.getTitleHoverText());
		assertNull(red.getTitleHoverText());
	}

	@Test
	public void pvmMegarareHoverUsesAFullNameBelowTheSpriteRow()
	{
		PvmSummaryTooltip tooltip = new PvmSummaryTooltip();
		tooltip.setData(126, 0, 0, 0, null, 0);
		Dimension size = tooltip.getPreferredSize();
		tooltip.setSize(size);
		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();

		assertTrue(moveMouseToHeaderHover(tooltip, "Tumeken's Shadow"));
		assertEquals("PvM Summary", tooltip.getTitle());
		assertNull(tooltip.getTitleHoverText());
		assertEquals("Tumeken's Shadow", tooltip.getHeaderHoverLineText());
		assertEquals(size, tooltip.getPreferredSize());
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

	private static void assertCategoryProgress(List<ClogHelper.ClogGroupProgress> progress,
		int[] obtained, int[] totals)
	{
		assertEquals(ClogHelper.CLOG_GROUP_NAMES.length, progress.size());
		for (int i = 0; i < ClogHelper.CLOG_GROUP_NAMES.length; i++)
		{
			assertEquals(ClogHelper.CLOG_GROUP_NAMES[i], progress.get(i).name);
			assertEquals(obtained[i], progress.get(i).obtained);
			assertEquals(totals[i], progress.get(i).total);
		}
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

	private static boolean moveMouseToHeaderHover(TitleTooltip tooltip, String text)
	{
		for (int y = 0; y < tooltip.getHeight(); y++)
		{
			for (int x = 0; x < tooltip.getWidth(); x++)
			{
				moveMouse(tooltip, x, y);
				if (text.equals(tooltip.getHeaderHoverLineText()))
				{
					return true;
				}
			}
		}
		return false;
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
