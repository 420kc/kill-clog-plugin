package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ClogSummaryTooltipTest
{
	private static final int ICON = 13;
	private static final int ICON_STEP = 16;
	private static final int BAR_TRACK = new Color(40, 35, 28).getRGB();
	private static final int YELLOW = new Color(255, 255, 0).getRGB();

	@Test
	public void theTierLadderReadsAReachedTierGreenAndTheNextOneRed()
	{
		// 1,150 of 1,700: rune is reached, dragon is not.
		ClogSummaryTooltip tip = painted(card(1150, 1700));
		Dimension idle = tip.getPreferredSize();
		int y = ladderY(tip);

		move(tip, ladderX(tip, 6) + 6, y);
		assertEquals("Rune: 1,100-1,199", tip.getHeaderHoverLineText());
		assertEquals(new Color(0, 255, 0), tip.getHeaderHoverLineColor());

		move(tip, ladderX(tip, 7) + 6, y);
		assertEquals("Dragon: 1,200-1,524 (50 more)", tip.getHeaderHoverLineText());
		assertEquals(new Color(255, 0, 0), tip.getHeaderHoverLineColor());

		move(tip, ladderX(tip, 8) + 6, y);
		assertEquals("Gilded: 1,525+ (375 more)", tip.getHeaderHoverLineText());

		// The readout row is always reserved, so hovering never resizes the card.
		assertEquals(idle, tip.getPreferredSize());
		move(tip, 1, 1);
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void landingExactlyOnATierReachesIt()
	{
		ClogSummaryTooltip tip = painted(card(1100, 1700));
		move(tip, ladderX(tip, 6) + 6, ladderY(tip));
		assertEquals("Rune: 1,100-1,199", tip.getHeaderHoverLineText());
		assertEquals(new Color(0, 255, 0), tip.getHeaderHoverLineColor());
	}

	@Test
	public void unreachedTiersAreDimmedAndReachedOnesAreNot()
	{
		Map<String, BufferedImage> icons = new LinkedHashMap<>();
		for (String tier : ClogHelper.CLOG_TIERS)
		{
			icons.put(tier, tile(ICON, ICON, Color.MAGENTA));
		}
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setTierData(1150, 1700, icons, null);
		BufferedImage image = paint(tip);
		int y = ladderY(tip);
		assertEquals(Color.MAGENTA.getRGB(), image.getRGB(ladderX(tip, 0) + 6, y));
		assertEquals(Color.MAGENTA.getRGB(), image.getRGB(ladderX(tip, 6) + 6, y));
		assertNotEquals(Color.MAGENTA.getRGB(), image.getRGB(ladderX(tip, 7) + 6, y));
		assertNotEquals(Color.MAGENTA.getRGB(), image.getRGB(ladderX(tip, 8) + 6, y));
	}

	@Test
	public void theCurrentTierSpriteIsAskedForByTier()
	{
		ItemManager items = mock(ItemManager.class);
		new ClogSummaryTooltip().setTierData(1150, 1700, null, items);
		verify(items).getImage(PanelData.CLOG_TIER_ITEM_IDS[6]);

		items = mock(ItemManager.class);
		new ClogSummaryTooltip().setTierData(100, 1700, null, items);
		verify(items).getImage(PanelData.CLOG_TIER_ITEM_IDS[0]);

		// Below bronze there is no tier to show.
		items = mock(ItemManager.class);
		new ClogSummaryTooltip().setTierData(99, 1700, null, items);
		verifyNoInteractions(items);
	}

	@Test
	public void theTierSpriteFillsInOnceItLoads() throws Exception
	{
		AtomicInteger repaints = new AtomicInteger();
		ClogSummaryTooltip tip = new ClogSummaryTooltip()
		{
			@Override
			public void repaint()
			{
				repaints.incrementAndGet();
			}
		};
		ItemManager items = mock(ItemManager.class);
		AsyncBufferedImage sprite = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		when(items.getImage(PanelData.CLOG_TIER_ITEM_IDS[6])).thenReturn(sprite);
		tip.setTierData(1150, 1700, null, items);
		assertEquals(0, count(paint(tip), Color.MAGENTA.getRGB()));

		// The client thread fills the shared image in and announces it.
		int before = repaints.get();
		Thread loader = new Thread(() ->
		{
			Graphics2D g = sprite.createGraphics();
			g.setColor(Color.MAGENTA);
			g.fillRect(0, 0, 36, 32);
			g.dispose();
			sprite.loaded();
		});
		loader.start();
		loader.join();
		assertEquals(1, repaints.get() - before);
		assertEquals(36 * 32, count(paint(tip), Color.MAGENTA.getRGB()));
	}

	@Test
	public void theCurrentTierSitsInTheHeaderCornerClearOfTheTitle() throws ReflectiveOperationException
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		tip.setRank(4321);
		Dimension bare = tip.getPreferredSize();
		Field field = ClogSummaryTooltip.class.getDeclaredField("tierSprite");
		field.setAccessible(true);
		// An item image at its native size.
		field.set(tip, tile(36, 32, Color.MAGENTA));

		// A decoration, not a row: the card is the same size with it.
		assertEquals(bare, tip.getPreferredSize());
		BufferedImage image = paint(tip);

		int inset = NativeTooltip.getInset();
		int left = bare.width;
		int right = 0;
		int top = bare.height;
		int bottom = 0;
		for (int y = 0; y < bare.height; y++)
		{
			for (int x = 0; x < bare.width; x++)
			{
				if (image.getRGB(x, y) == Color.MAGENTA.getRGB())
				{
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		assertEquals(bare.width - inset - 36, left);
		assertEquals(bare.width - inset - 1, right);
		// Centred in the header, above its rule, and right of the title on its line.
		assertEquals(inset + (tip.getHeaderHeight() - 32) / 2, top);
		assertTrue(bottom < inset + tip.getHeaderHeight());
		int titleWidth = tip.getFontMetrics(tip.getTitleFont()).stringWidth("Collection Log");
		assertTrue(inset + titleWidth < left);
	}

	@Test
	public void theHeaderCountsATotalAndTheCompletionNeverPassesOneHundred() throws ReflectiveOperationException
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		Field label = TitleTooltip.class.getDeclaredField("subtitleLabel");
		label.setAccessible(true);
		assertEquals("Total: ", label.get(tip));
		assertEquals("67.6%", tip.completionText());

		ClogSummaryTooltip preview = new ClogSummaryTooltip();
		preview.setObtainedPlaceholder(1700);
		assertEquals("Total: ", label.get(preview));

		// A provider can count more than the catalog it sent.
		assertEquals("100.0%", card(1800, 1700).completionText());
		assertEquals("0.0%", card(0, 1700).completionText());
	}

	@Test
	public void everyBarFillsToItsOwnShare()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{1, 2});
		tabs.put("Raids", new int[]{0, 79});
		tabs.put("Clues", new int[]{470, 470});
		tip.setTabs(tabs);
		List<int[]> bars = bars(paint(tip));

		// The completion bar, then one per tab, all inside the card.
		assertEquals(4, bars.size());
		int width = tip.getWidth() - 2 * NativeTooltip.getInset();
		assertEquals(width * 1150 / 1700, bars.get(0)[0]);
		assertEquals(width / 2, bars.get(1)[0]);
		assertEquals(0, bars.get(2)[0]);
		// A complete tab is green end to end: no yellow and no track left.
		assertEquals(0, bars.get(3)[0]);
		assertEquals(0, bars.get(3)[1]);
		int contentBottom = tip.getHeight() - NativeTooltip.getInset() - 1;
		assertTrue(bars.get(3)[2] <= contentBottom);
	}

	@Test
	public void everyTabAddsARowAndACardWithoutTabsHasNone()
	{
		ClogSummaryTooltip bare = card(1150, 1700);
		assertEquals(1, bars(paint(bare)).size());
		ClogSummaryTooltip withTabs = card(1150, 1700);
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{290, 381});
		tabs.put("Raids", new int[]{60, 79});
		withTabs.setTabs(tabs);
		int twoTabs = withTabs.getPreferredSize().height;
		assertTrue(twoTabs > bare.getPreferredSize().height);

		tabs.put("Clues", new int[]{0, 470});
		int perTab = withTabs.getPreferredSize().height - twoTabs;
		assertTrue(perTab > NativeTooltip.LINE_HEIGHT);
		tabs.put("Minigames", new int[]{120, 120});
		assertEquals(twoTabs + 2 * perTab, withTabs.getPreferredSize().height);
		assertEquals(5, bars(paint(withTabs)).size());
	}

	@Test
	public void theLastUpdateSitsInTheFooterWithTheSources()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		tip.setClogSources(true, false, false, false);
		int sourcesOnly = tip.getPreferredSize().height;
		tip.setSyncData("2 hours ago", false);
		// One more line under the same rule, not a second section.
		assertEquals(sourcesOnly + NativeTooltip.LINE_HEIGHT, tip.getPreferredSize().height);
		painted(tip);
	}

	@Test
	public void aCardWithNoTotalsPaintsNoLadder()
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setTitle("Collection Log");
		assertTrue(bars(paint(tip)).isEmpty());
		move(tip, tip.getWidth() / 2, tip.getHeight() - 4);
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void aTabRowOpensItsPagesOnceSomethingCanOpenThem()
	{
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{1, 2});
		tabs.put("Raids", new int[]{0, 3});
		ClogSummaryTooltip tip = card(1, 5);
		tip.setTabs(tabs);
		Dimension reading = tip.getPreferredSize();
		painted(tip);
		for (int y = 0; y < tip.getHeight(); y++)
		{
			assertEquals(-1, tip.tabAt(y));
		}

		List<String> opened = new ArrayList<>();
		tip.setOnOpenTab((press, tab) -> opened.add(tab));
		// Clickable rows take no more room than the lines they replace.
		assertEquals(reading, tip.getPreferredSize());
		painted(tip);
		int raids = -1;
		for (int y = tip.getHeight() - 1; y >= 0; y--)
		{
			raids = tip.tabAt(y) == 1 ? y : raids;
		}
		for (java.awt.event.MouseListener listener : tip.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(tip, MouseEvent.MOUSE_PRESSED, 0, 0, 5, raids + 2, 1, false,
				MouseEvent.BUTTON1));
		}
		assertEquals(List.of("Raids"), opened);
	}

	private static ClogSummaryTooltip card(int obtained, int total)
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setTierData(obtained, total, null, null);
		return tip;
	}

	private static ClogSummaryTooltip painted(ClogSummaryTooltip tip)
	{
		paint(tip);
		return tip;
	}

	private static BufferedImage paint(ClogSummaryTooltip tip)
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tip.paint(graphics);
		graphics.dispose();
		return image;
	}

	/**
	 * Every fill bar on the card, top to bottom: {yellow pixels, track pixels,
	 * bottom row}, read from the bar's first row. A bar is any run of rows
	 * that holds track or spans the card in one fill colour.
	 */
	private static List<int[]> bars(BufferedImage image)
	{
		int inset = NativeTooltip.getInset();
		int width = image.getWidth() - 2 * inset;
		List<int[]> bars = new ArrayList<>();
		boolean inBar = false;
		for (int y = 0; y < image.getHeight(); y++)
		{
			int yellow = 0;
			int track = 0;
			int first = image.getRGB(inset, y);
			int sameAsFirst = 0;
			for (int x = inset; x < inset + width; x++)
			{
				int rgb = image.getRGB(x, y);
				yellow += rgb == YELLOW ? 1 : 0;
				track += rgb == BAR_TRACK ? 1 : 0;
				sameAsFirst += rgb == first ? 1 : 0;
			}
			boolean green = sameAsFirst == width && first == new Color(0, 255, 0).getRGB();
			boolean bar = track > 0 || green || yellow == width;
			if (bar && !inBar)
			{
				bars.add(new int[]{yellow, track, y});
			}
			else if (bar)
			{
				bars.get(bars.size() - 1)[2] = y;
			}
			inBar = bar;
		}
		return bars;
	}

	private static int count(BufferedImage image, int rgb)
	{
		int found = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				found += image.getRGB(x, y) == rgb ? 1 : 0;
			}
		}
		return found;
	}

	private static BufferedImage tile(int width, int height, Color color)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, width, height);
		g.dispose();
		return image;
	}

	/** The ladder is the last thing on a card with no tabs, items or sources. */
	private static int ladderY(ClogSummaryTooltip tip)
	{
		// The ladder stands under the completion, above the band that names its tiers: found as a pointer would.
		for (int y = 0; y < tip.getHeight(); y++)
		{
			move(tip, ladderX(tip, 0) + 6, y);
			if (tip.getHeaderHoverLineText() != null)
			{
				move(tip, 1, 1);
				return y + ICON / 2;
			}
		}
		throw new AssertionError("no tier ladder painted");
	}

	private static int ladderX(ClogSummaryTooltip tip, int tier)
	{
		int width = ClogHelper.CLOG_TIERS.length * ICON_STEP - (ICON_STEP - ICON);
		return (tip.getWidth() - width) / 2 + tier * ICON_STEP;
	}

	private static void move(ClogSummaryTooltip tip, int x, int y)
	{
		for (MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(new MouseEvent(tip, MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
		}
	}

	@Test
	public void aShortShelfStartsAtTheLeft() throws Exception
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		java.lang.reflect.Field rare = ClogSummaryTooltip.class.getDeclaredField("rare");
		rare.setAccessible(true);
		rare.set(tip, new ClogSummaryTooltip.Shelf(null, new int[]{20590}, new String[]{"Stale baguette"},
			new int[]{1}, null));
		painted(tip);
		java.lang.reflect.Field hits = TooltipItemHover.class.getDeclaredField("hitBoxes");
		hits.setAccessible(true);
		java.lang.reflect.Field bounds = TooltipItemHover.HitBox.class.getDeclaredField("bounds");
		bounds.setAccessible(true);
		int left = Integer.MAX_VALUE;
		for (Object hit : (List<?>) hits.get(tip.itemHover))
		{
			java.awt.Rectangle box = (java.awt.Rectangle) bounds.get(hit);
			if (box.width == CardBody.GRID_SPRITE)
			{
				left = Math.min(left, box.x);
			}
		}
		assertEquals(NativeTooltip.getInset(), left);
	}
}
