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
		tooltip.setClogSources(true, true, true, false);
		assertEquals(Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile"),
			tooltip.sourceNames());

		tooltip.setClogSources(false, true, false, false);
		assertEquals(Collections.singletonList("RuneProfile"), tooltip.sourceNames());
		tooltip.setClogSources(false, false, false, false);
		assertTrue(tooltip.sourceNames().isEmpty());
	}

	@Test
	public void clogSummaryCreditsRuneProfileWhenItHoldsAProfileButSentNoLog()
	{
		ClogResult selfCard = new ClogResult("Probe", Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), null, null)
			.withSources(true, false, false).withLocalSource(true);

		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setClogSources(selfCard, false);
		assertEquals(Arrays.asList("Local Collection Log", "TempleOSRS"), tooltip.sourceNames());

		tooltip.setClogSources(selfCard, true);
		assertEquals(Arrays.asList("Local Collection Log", "TempleOSRS", "RuneProfile"),
			tooltip.sourceNames());
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
	public void clogSummaryProviderHoverKeepsTitleWithoutResizing()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		tooltip.setClogSources(true, true, true, false);
		Dimension idle = tooltip.getPreferredSize();
		tooltip.setSize(idle);

		Graphics2D graphics = new BufferedImage(
			idle.width, idle.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();

		int iconY = idle.height - NativeTooltip.getInset() - 13
			- TitleTooltip.hoverRowHeight(tooltip.getFontMetrics(net.runelite.client.ui.FontManager.getRunescapeSmallFont()));
		int startX = ClogSummaryTooltip.sourceRowStartX(idle.width, 3);
		List<String> names = Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile");
		for (int i = 0; i < names.size(); i++)
		{
			moveMouse(tooltip, startX + i * 18 + 6, iconY + 6);
			assertEquals(names.get(i), tooltip.getHeaderHoverLineText());
			assertEquals(idle, tooltip.getPreferredSize());
		}
	}

	@Test
	public void clogSummaryProvenanceFooterIsAbsentWithoutSyncedSources()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		Dimension withoutSources = tooltip.getPreferredSize();

		tooltip.setClogSources(true, false, false, false);
		assertTrue(tooltip.getPreferredSize().height > withoutSources.height);
		tooltip.setClogSources(false, false, false, false);
		assertEquals(withoutSources, tooltip.getPreferredSize());
	}

	@Test
	public void comparisonClogCardsKeepIndependentProviderHover()
	{
		ClogSummaryTooltip blue = new ClogSummaryTooltip();
		blue.setTitle("Clog Summary");
		blue.setClogSources(true, false, false, false);
		ClogSummaryTooltip red = new ClogSummaryTooltip();
		red.setTitle("Clog Summary");
		red.setClogSources(false, true, false, false);
		SideBySideTooltip pair = new SideBySideTooltip("Blue", blue, "Red", red);
		Dimension size = pair.getPreferredSize();
		pair.setSize(size);

		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		pair.paint(graphics);
		graphics.dispose();

		moveMouse(blue, ClogSummaryTooltip.sourceRowStartX(blue.getWidth(), 1) + 6,
			blue.getHeight() - NativeTooltip.getInset() - 7
			- TitleTooltip.hoverRowHeight(blue.getFontMetrics(net.runelite.client.ui.FontManager.getRunescapeSmallFont())));
		assertEquals("TempleOSRS", blue.getHeaderHoverLineText());
		assertNull(red.getHeaderHoverLineText());

		moveMouse(red, ClogSummaryTooltip.sourceRowStartX(red.getWidth(), 1) + 6,
			red.getHeight() - NativeTooltip.getInset() - 7
			- TitleTooltip.hoverRowHeight(red.getFontMetrics(net.runelite.client.ui.FontManager.getRunescapeSmallFont())));
		assertEquals("RuneProfile", red.getHeaderHoverLineText());
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
	public void pvpRowsWidthGrowsWithLargeScore()
	{
		PvpSummaryRows small = new PvpSummaryRows();
		small.setData(pvpHiscore("Soul Wars Zeal", 42), null);
		PvpSummaryRows large = new PvpSummaryRows();
		large.setData(pvpHiscore("Soul Wars Zeal", 1_234_567), null);

		assertTrue(size(small).width < size(large).width);
		// Five rows whatever the data, so a comparison pair never staggers.
		PvpSummaryRows empty = new PvpSummaryRows();
		empty.setData(null, null);
		assertEquals(5 * NativeTooltip.LINE_HEIGHT, size(empty).height);
		assertEquals(size(empty).height, size(large).height);
	}

	private static java.awt.Dimension size(PvpSummaryRows rows)
	{
		return new CardBody().add(rows.part()).size(new PvmSummaryTooltip(), 0);
	}

	@Test
	public void combatSummaryCarriesThePvpSectionUnderItsOwnTitle()
	{
		PvmSummaryTooltip withoutPvp = new PvmSummaryTooltip();
		withoutPvp.setData(126.1, 999, 50, 60, null, 0);
		assertEquals("Combat Summary", withoutPvp.getTitle());

		PvmSummaryTooltip card = new PvmSummaryTooltip();
		card.setData(126.1, 999, 50, 60, null, 0);
		card.setPvp(pvpHiscore("Soul Wars Zeal", 1_234_567), null, null);
		// The section is always there: five rows even with no PvP data.
		assertEquals(withoutPvp.getPreferredSize().height, card.getPreferredSize().height);

		BufferedImage[] icons = new BufferedImage[5];
		for (int i = 0; i < icons.length; i++)
		{
			icons[i] = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icons[i].createGraphics();
			g.setColor(Color.MAGENTA);
			g.fillRect(0, 0, 13, 13);
			g.dispose();
		}
		card.setPvp(pvpHiscore("Soul Wars Zeal", 1_234_567), null, icons);
		Dimension size = card.getPreferredSize();
		card.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();

		// All five rows are painted, and the last one ends inside the card.
		int x = NativeTooltip.getInset() + 6;
		int painted = 0;
		int last = 0;
		for (int y = 0; y < size.height; y++)
		{
			if (image.getRGB(x, y) == Color.MAGENTA.getRGB())
			{
				painted++;
				last = y;
			}
		}
		assertEquals(13 * icons.length, painted);
		assertTrue(last < size.height - NativeTooltip.getInset());
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
		PvmSummaryTooltip blueSmall = new PvmSummaryTooltip();
		blueSmall.setData(126, 999, 50, 60, null, 0);
		PvmSummaryTooltip redSmall = new PvmSummaryTooltip();
		redSmall.setData(126, 999, 50, 60, null, 0);
		SideBySideTooltip small = new SideBySideTooltip("Blue", blueSmall, "Red", redSmall);

		PvmSummaryTooltip blueLarge = new PvmSummaryTooltip();
		blueLarge.setData(126, 1_234_567_890, 50, 60, null, 0);
		PvmSummaryTooltip redLarge = new PvmSummaryTooltip();
		redLarge.setData(126, 999, 50, 60, null, 0);
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
			Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), null);
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

	@Test
	public void aGridCardWithNeitherItemsNorANoticeIsItsHeaderAlone()
	{
		ImgTooltip header = new ImgTooltip(5);
		header.setTitle("Easy");
		header.setObtained(79, 131);
		header.setNotice(null);
		ImgTooltip notice = new ImgTooltip(5);
		notice.setTitle("Easy");
		notice.setObtained(79, 131);
		// No divider and no notice: the title and its count, inside the card's own edges.
		assertEquals(NativeTooltip.getInset() * 2 + header.getHeaderHeight(), header.getPreferredSize().height);
		assertTrue(header.getPreferredSize().height < notice.getPreferredSize().height);
	}

}
