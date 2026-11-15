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
import net.runelite.client.hiscore.HiscoreSkill;
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
		// A page's section of the Collection log article keeps its '#'.
		assertEquals("https://oldschool.runescape.wiki/w/Collection_log#Champion%27s_Challenge",
			TooltipItemLink.wikiPageUrl("Collection log#Champion's Challenge"));
		// A clue tier's card opens its scroll's article; every Clue Summary rare row has a page too.
		assertEquals("Clue scroll (beginner)", PanelData.clueWikiPage(HiscoreSkill.CLUE_SCROLL_BEGINNER));
		assertEquals("Master clues completed: ", Cells.cluesCompletedLabel(HiscoreSkill.CLUE_SCROLL_MASTER));
	}

	@Test
	public void linkedTitleHoverTurnsWhiteOnlyWhenWikiLinksAreEnabled()
	{
		ImgTooltip tooltip = spriteTooltip(2);
		tooltip.setTitle("Abyssal Sire");
		tooltip.setTitleWikiPage("Abyssal Sire");

		// The title answers where it was last painted, and the move's repaint lights it.
		paint(tooltip);
		moveMouse(tooltip, NativeTooltip.getInset() + 1, NativeTooltip.getInset() + 1);
		paint(tooltip);
		assertEquals(Color.WHITE, tooltip.titleColor());

		tooltip.setLinks(CardLinks.OFF);
		moveMouse(tooltip, NativeTooltip.getInset() + 1, NativeTooltip.getInset() + 1);
		paint(tooltip);
		assertEquals(NativeTooltip.OSRS_ORANGE, tooltip.titleColor());
	}

	@Test
	public void aShiftClickOnlyTitleLightsWhileShiftIsHeldOverIt()
	{
		ImgTooltip tooltip = spriteTooltip(2);
		tooltip.setTitle("Abyssal Sire");
		tooltip.setTitleWikiPage("Abyssal Sire");
		tooltip.setLinks(CardLinks.SHIFT);
		int x = NativeTooltip.getInset() + 1;
		int y = NativeTooltip.getInset() + 1;
		paint(tooltip);
		moveMouse(tooltip, x, y);
		paint(tooltip);
		assertEquals("a plain hover: shift-click only opens nothing", NativeTooltip.OSRS_ORANGE, tooltip.titleColor());
		moveMouse(tooltip, x + 1, y, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
		paint(tooltip);
		assertEquals("shift held over it: it lights", Color.WHITE, tooltip.titleColor());
		moveMouse(tooltip, x + 2, y);
		paint(tooltip);
		assertEquals("shift let go: unlit again", NativeTooltip.OSRS_ORANGE, tooltip.titleColor());
	}

	@Test
	public void aRowRepaintsWhenShiftGoesDownOrUpOverIt()
	{
		// 11/15 review: rows repainted only when the hovered row changed, so shift pressed over a shift-click-only title
		// left it unlit until the mouse left it, and let go left it lit though a plain click opens nothing.
		java.util.concurrent.atomic.AtomicInteger repaints = new java.util.concurrent.atomic.AtomicInteger();
		JPanel card = new JPanel()
		{
			@Override
			public void repaint()
			{
				repaints.incrementAndGet();
			}
		};
		CardBody.ClickRows rows = new CardBody.ClickRows(card, (e, key) ->
		{
		});
		rows.lit(new Rectangle(0, 0, 50, 10), 1);
		moveMouse(card, 5, 5);
		int before = repaints.get();
		moveMouse(card, 6, 5, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
		assertTrue("shift down over the row repaints it", repaints.get() > before);
		assertTrue(rows.shift);
		before = repaints.get();
		moveMouse(card, 7, 5);
		assertTrue("shift up over the row repaints it", repaints.get() > before);
		before = repaints.get();
		moveMouse(card, 8, 5);
		assertEquals("a plain move along the row doesn't", before, repaints.get());
		moveMouse(card, 60, 5, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
		before = repaints.get();
		moveMouse(card, 61, 5);
		assertEquals("shift off any row repaints nothing", before, repaints.get());
	}

	private static void paint(JComponent card)
	{
		card.setSize(card.getPreferredSize());
		java.awt.Graphics2D g = new java.awt.image.BufferedImage(card.getWidth(), card.getHeight(),
			java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
		card.paint(g);
		g.dispose();
	}

	@Test
	public void everyCardLeadsWithItsObtainedLine() throws Exception
	{
		// 11/14: the Collection Log for a player with no synced log read its rank first; every card leads with Obtained.
		ClogSummaryTooltip clog = new ClogSummaryTooltip();
		clog.setTitle("Collection Log");
		clog.setRank(1878);
		clog.setObtained(1231, 1734);
		java.lang.reflect.Method lines = TitleTooltip.class.getDeclaredMethod("headerLines");
		lines.setAccessible(true);
		List<?> header = (List<?>) lines.invoke(clog);
		assertEquals("Obtained: ", TestViews.field(header.get(0), "label"));
		assertEquals("Rank: ", TestViews.field(header.get(1), "label"));
	}

	@Test
	public void aCardSizedForItsTitleDrawsItWhole()
	{
		// 11/14: painting's antialiased bold ran 1 to 3 px wider than the card measured it, which cut "Collection Log" and
		// "Experience & Efficiency" to an ellipsis on their own cards. Drawn at its own size, a title matches the same
		// card drawn wider.
		SkillsTooltip xp = new SkillsTooltip();
		xp.setData(null, null, false);
		ClogSummaryTooltip clog = new ClogSummaryTooltip();
		clog.setTitle("Collection Log");
		clog.setObtained(-1, 1734);
		int inset = NativeTooltip.getInset();
		for (TitleTooltip card : new TitleTooltip[]{xp, clog})
		{
			java.awt.Dimension size = card.getPreferredSize();
			java.awt.image.BufferedImage own = draw(card, size.width, size.height);
			java.awt.image.BufferedImage wide = draw(card, size.width + 40, size.height);
			for (int y = inset; y < inset + 20; y++)
			{
				for (int x = inset; x < size.width - inset; x++)
				{
					assertEquals(card.getClass().getSimpleName() + " at " + x + "," + y, wide.getRGB(x, y), own.getRGB(x, y));
				}
			}
		}
	}

	private static java.awt.image.BufferedImage draw(JComponent card, int width, int height)
	{
		card.setSize(width, height);
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		card.paint(g);
		g.dispose();
		return image;
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
		tooltip.showSources(CardSources.of(true, true, true, false, null, ""));
		assertEquals(Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile"),
			TestViews.sourceNames(tooltip));

		tooltip.showSources(CardSources.of(false, true, false, false, null, ""));
		assertEquals(Collections.singletonList("RuneProfile"), TestViews.sourceNames(tooltip));
		tooltip.showSources(CardSources.of(false, false, false, false, null, ""));
		assertTrue(TestViews.sourceNames(tooltip).isEmpty());
	}

	@Test
	public void clogSummaryCreditsRuneProfileWhenItHoldsAProfileButSentNoLog()
	{
		ClogResult selfCard = new ClogResult("Probe", Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), null, null)
			.withSources(true, false, false).withLocalSource(true);

		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setClogSources(selfCard, false, false, false);
		assertEquals(Arrays.asList("Local Collection Log", "TempleOSRS"), TestViews.sourceNames(tooltip));

		tooltip.setClogSources(selfCard, false, true, false);
		assertEquals(Arrays.asList("Local Collection Log", "TempleOSRS", "RuneProfile"),
			TestViews.sourceNames(tooltip));
	}

	@Test
	public void clogSummaryNamesEverySiteThatHoldsThePlayerWhicheverLogItShows()
	{
		// Your own log, read from the game: Temple's and RuneProfile's requests were skipped and nothing came back from
		// them, yet all three hold you, and your synced log is Kill Clog's (Dylan, 2026-10-12).
		ClogResult selfCard = new ClogResult("Probe", Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), null, null).withLocalSource(true);

		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setClogSources(selfCard, true, true, true);
		assertEquals(Arrays.asList("Kill Clog", "TempleOSRS", "RuneProfile"), TestViews.sourceNames(tooltip));

		tooltip.setClogSources(selfCard, false, false, false);
		assertEquals(Collections.singletonList("Local Collection Log"), TestViews.sourceNames(tooltip));
	}

	@Test
	public void clogSummarySourcesRowWidthCountsEachIconAndTheGapsBetween()
	{
		// Left-aligned under the last update, with no Sources line (Dylan 11/14).
		assertEquals(0, CardSources.width(0));
		assertEquals(13, CardSources.width(1));
		assertEquals(13 * 3 + 5 * 2, CardSources.width(3));
	}

	@Test
	public void clogSummaryProviderHoverKeepsTitleWithoutResizing()
	{
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setTitle("Clog Summary");
		tooltip.showSources(CardSources.of(true, true, true, false, null, ""));
		Dimension idle = tooltip.getPreferredSize();
		tooltip.setSize(idle);

		Graphics2D graphics = new BufferedImage(
			idle.width, idle.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();

		int iconY = idle.height - NativeTooltip.getInset() - 13;
		int startX = NativeTooltip.getInset();
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

		tooltip.showSources(CardSources.of(true, false, false, false, null, ""));
		assertTrue(tooltip.getPreferredSize().height > withoutSources.height);
		tooltip.showSources(CardSources.of(false, false, false, false, null, ""));
		assertEquals(withoutSources, tooltip.getPreferredSize());
	}

	@Test
	public void comparisonClogCardsKeepIndependentProviderHover()
	{
		ClogSummaryTooltip blue = new ClogSummaryTooltip();
		blue.setTitle("Clog Summary");
		blue.showSources(CardSources.of(true, false, false, false, null, ""));
		ClogSummaryTooltip red = new ClogSummaryTooltip();
		red.setTitle("Clog Summary");
		red.showSources(CardSources.of(false, true, false, false, null, ""));
		SideBySideTooltip pair = new SideBySideTooltip("Blue", blue, "Red", red);
		Dimension size = pair.getPreferredSize();
		pair.setSize(size);

		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		pair.paint(graphics);
		graphics.dispose();

		moveMouse(blue, NativeTooltip.getInset() + 6,
			blue.getHeight() - NativeTooltip.getInset() - 7);
		assertEquals("TempleOSRS", blue.getHeaderHoverLineText());
		assertNull(red.getHeaderHoverLineText());

		moveMouse(red, NativeTooltip.getInset() + 6,
			red.getHeight() - NativeTooltip.getInset() - 7);
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
		// PvP closes the card, under the scroll: wheel down to it.
		for (java.awt.event.MouseWheelListener listener : card.getMouseWheelListeners())
		{
			listener.mouseWheelMoved(new java.awt.event.MouseWheelEvent(card, java.awt.event.MouseEvent.MOUSE_WHEEL,
				0, 0, 10, 100, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 40));
		}
		card.paint(graphics);
		graphics.dispose();

		// All five tiles carry their icon whole, and the last one ends inside the card.
		int painted = 0;
		int last = 0;
		for (int y = 0; y < size.height; y++)
		{
			for (int x = 0; x < size.width; x++)
			{
				if (image.getRGB(x, y) == Color.MAGENTA.getRGB())
				{
					painted++;
					last = y;
				}
			}
		}
		assertEquals(13 * 13 * icons.length, painted);
		assertTrue(last < size.height - NativeTooltip.getInset());
	}

	@Test
	public void pvmSummaryWidthHoldsWhateverTheCountsAndTheMostKilledName()
	{
		PvmSummaryTooltip small = new PvmSummaryTooltip();
		small.setData(126, 999, 50, 60, null, 0);

		// Far past the old "999,999" placeholder ceiling: the width tracks the real
		// strings, not a capped guess. The raid tiles are wider than any total kills,
		// so a Most Killed line longer than them is what widens it.
		PvmSummaryTooltip large = new PvmSummaryTooltip();
		large.setData(126, 1_234_567_890, 50, 60, "Thermonuclear Smoke Devil".repeat(2), 1_234_567);

		// 2.8.0: the tiles set the width and Most Killed's name rides the band, so neither moves the card.
		assertEquals(small.getPreferredSize().width, large.getPreferredSize().width);
		PvmSummaryTooltip largestTotal = new PvmSummaryTooltip();
		largestTotal.setData(126, Integer.MAX_VALUE, 50, 60, null, 0);
		assertTrue(small.getPreferredSize().width <= largestTotal.getPreferredSize().width);
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
		blueLarge.setData(126, 1_234_567_890, 50, 60, "Thermonuclear Smoke Devil".repeat(2), 1_234_567);
		PvmSummaryTooltip redLarge = new PvmSummaryTooltip();
		redLarge.setData(126, 999, 50, 60, null, 0);
		SideBySideTooltip large = new SideBySideTooltip("Blue", blueLarge, "Red", redLarge);

		// The pair is its two cards plus the same chrome, whatever each card holds.
		assertEquals(small.getPreferredSize().width - blueSmall.getPreferredSize().width - redSmall.getPreferredSize().width,
			large.getPreferredSize().width - blueLarge.getPreferredSize().width - redLarge.getPreferredSize().width);
		// Doublewide by construction: both children plus card chrome.
		assertTrue(small.getPreferredSize().width
			> blueSmall.getPreferredSize().width + redSmall.getPreferredSize().width);
	}

	@Test
	public void itemHoverCarriesWhetherTheHoveredPlayerHoldsTheItem()
	{
		JPanel component = new JPanel();
		TooltipItemHover hover = new TooltipItemHover(component);
		hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
			1, 995, "Coins", new Rectangle(5, 5, 15, 15), true)));

		moveMouse(component, 6, 6);

		assertEquals("Coins", hover.hoveredItemName());
		assertTrue(hover.hoveredItemObtained());

		hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
			1, 995, "Coins", new Rectangle(5, 5, 15, 15), false)));
		moveMouse(component, 6, 6);
		assertFalse(hover.hoveredItemObtained());
	}

	private static HiscoreResult pvpHiscore(String activity, int score)
	{
		Map<String, Integer> scores = new HashMap<>();
		scores.put(activity, score);
		return TestViews.hiscore(AccountType.REGULAR,
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
		moveMouse(tooltip, x, y, 0);
	}

	private static void moveMouse(JComponent tooltip, int x, int y, int modifiers)
	{
		MouseEvent event = new MouseEvent(tooltip, MouseEvent.MOUSE_MOVED,
			System.currentTimeMillis(), modifiers, x, y, 0, false);
		for (MouseMotionListener listener : tooltip.getMouseMotionListeners())
		{
			listener.mouseMoved(event);
		}
	}
	@Test
	public void aPacksScrollbarColorIsItsBodyNotItsBevel()
	{
		// A 16 by 5 thumb: light bevels at the sides, its body in between, a transparent corner.
		java.awt.image.BufferedImage thumb = new java.awt.image.BufferedImage(16, 5,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16; x++)
		{
			for (int y = 0; y < 5; y++)
			{
				thumb.setRGB(x, y, x < 3 || x > 12 ? 0xffd0c0a0 : 0xff4d4233);
			}
		}
		thumb.setRGB(8, 0, 0);
		assertEquals(new Color(0x4d, 0x42, 0x33), CardSprites.bodyColor(thumb));
		assertNull(CardSprites.bodyColor(null));
		// The rail's thumb takes the bevel's light; a dark flat thumb is lifted until it reads.
		assertEquals(new Color(0xd0, 0xc0, 0xa0), CardSprites.thumbColor(thumb));
		java.awt.image.BufferedImage flat = new java.awt.image.BufferedImage(16, 5,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16; x++)
		{
			for (int y = 0; y < 5; y++)
			{
				flat.setRGB(x, y, 0xff3f3f3f);
			}
		}
		assertEquals(new Color(191, 191, 191), CardSprites.thumbColor(flat));
	}
}
