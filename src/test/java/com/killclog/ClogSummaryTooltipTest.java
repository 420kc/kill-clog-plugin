package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
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
	private static final int GREEN = new Color(0, 255, 0).getRGB();
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
		verify(items).getImage(ClogHelper.CLOG_TIER_ITEM_IDS[6]);

		items = mock(ItemManager.class);
		new ClogSummaryTooltip().setTierData(100, 1700, null, items);
		verify(items).getImage(ClogHelper.CLOG_TIER_ITEM_IDS[0]);

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
		when(items.getImage(ClogHelper.CLOG_TIER_ITEM_IDS[6])).thenReturn(sprite);
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
		// The card redraws on the Swing thread.
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
		});
		assertEquals(1, repaints.get() - before);
		assertEquals(36 * 32, count(paint(tip), Color.MAGENTA.getRGB()));
	}

	@Test
	public void theHeaderNamesTheTierAtItsRight()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		assertEquals("Rune", tip.tierName());
		assertNull(card(99, 1700).tierName());
		// On the title's line and clear of it: the card holds both.
		int title = tip.getFontMetrics(tip.getTitleFont()).stringWidth("Collection Log");
		int word = tip.getFontMetrics(FontManager.getRunescapeFont()).stringWidth("Rune");
		assertTrue(tip.getPreferredSize().width >= 2 * NativeTooltip.getInset() + title + word);
	}

	@Test
	public void theTotalLeadsTheTilesThreeAcrossEachBarItsOwnShare()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		assertEquals(1, TestViews.tiles(paint(tip)).size());
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{1, 2});
		tabs.put("Raids", new int[]{0, 79});
		tabs.put("Clues", new int[]{470, 470});
		tabs.put("Minigames", new int[]{1, 261});
		tabs.put("Other", new int[]{1, 530});
		tip.setTabs(tabs, null);
		BufferedImage image = paint(tip);
		List<Point> tiles = TestViews.tiles(image);

		// The Total, Bosses and Raids, then Clues, Minigames and Other under them.
		assertEquals(6, tiles.size());
		assertEquals(tiles.get(0).y, tiles.get(2).y);
		assertEquals(tiles.get(0).x, tiles.get(3).x);
		assertEquals(GameTile.HEIGHT + GameTile.GAP, tiles.get(3).y - tiles.get(0).y);
		assertEquals(GameTile.WIDTH + GameTile.GAP, tiles.get(1).x - tiles.get(0).x);
		// 1,150 of 1,700 fills more than 1 of 2; none of 79 fills nothing; a complete tab is green end to end.
		assertTrue(barPixels(image, tiles.get(0), YELLOW) > barPixels(image, tiles.get(1), YELLOW));
		assertTrue(barPixels(image, tiles.get(1), YELLOW) > 0);
		assertEquals(0, barPixels(image, tiles.get(2), YELLOW) + barPixels(image, tiles.get(2), GREEN));
		assertEquals(0, barPixels(image, tiles.get(3), YELLOW));
		assertTrue(barPixels(image, tiles.get(3), GREEN) > GameTile.WIDTH / 2);
	}

	@Test
	public void everyThreeTilesAddARow()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{290, 381});
		tabs.put("Raids", new int[]{60, 79});
		tip.setTabs(tabs, null);
		int oneRow = tip.getPreferredSize().height;
		tabs.put("Clues", new int[]{0, 470});
		assertEquals(oneRow + GameTile.HEIGHT + GameTile.GAP, tip.getPreferredSize().height);
		tabs.put("Minigames", new int[]{120, 120});
		tabs.put("Other", new int[]{1, 530});
		assertEquals(oneRow + GameTile.HEIGHT + GameTile.GAP, tip.getPreferredSize().height);
		assertEquals(6, TestViews.tiles(paint(tip)).size());
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
		assertTrue(TestViews.tiles(paint(tip)).isEmpty());
		move(tip, tip.getWidth() / 2, tip.getHeight() - 4);
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void aTabTileOpensItsPagesOnceSomethingCanOpenThem()
	{
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{1, 2});
		tabs.put("Raids", new int[]{0, 3});
		ClogSummaryTooltip tip = card(1, 5);
		tip.setTabs(tabs, null);
		Dimension reading = tip.getPreferredSize();
		List<Point> tiles = TestViews.tiles(paint(tip));
		Point raids = tiles.get(2);
		for (Point tile : tiles)
		{
			assertEquals(-1, TestViews.tileAt(tip, "tabRows", tile.x + 10, tile.y + 10));
		}

		List<String> opened = new ArrayList<>();
		tip.setOnOpenTab((press, tab) -> opened.add(tab));
		// Pressable tiles take no more room than the ones that only read.
		assertEquals(reading, tip.getPreferredSize());
		painted(tip);
		// The Total opens nothing; a tab's tile opens its pages, and lightens under the mouse.
		assertEquals(-1, TestViews.tileAt(tip, "tabRows", tiles.get(0).x + 10, tiles.get(0).y + 10));
		assertEquals(1, TestViews.tileAt(tip, "tabRows", raids.x + 10, raids.y + 10));
		int idle = paint(tip).getRGB(raids.x + 10, raids.y + 40);
		Point total = tiles.get(0);
		int totalIdle = paint(tip).getRGB(total.x + 10, total.y + 40);
		move(tip, total.x + 10, total.y + 10);
		assertEquals("the Total opens nothing, so it never answers the hover", totalIdle,
			paint(tip).getRGB(total.x + 10, total.y + 40));
		move(tip, raids.x + 10, raids.y + 10);
		assertNotEquals(idle, paint(tip).getRGB(raids.x + 10, raids.y + 40));
		for (java.awt.event.MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(new MouseEvent(tip, MouseEvent.MOUSE_EXITED, 0, 0, raids.x + 10, raids.y + 10, 0, false));
		}
		assertEquals("off the card, a repaint (a sprite loading late) lights nothing", idle,
			paint(tip).getRGB(raids.x + 10, raids.y + 40));
		move(tip, raids.x + 10, raids.y + 10);
		for (java.awt.event.MouseListener listener : tip.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(tip, MouseEvent.MOUSE_PRESSED, 0, 0, raids.x + 10, raids.y + 10, 1,
				false, MouseEvent.BUTTON1));
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

	/** How many pixels of a tile's bar, across its middle row, wear a colour. */
	private static int barPixels(BufferedImage image, Point tile, int rgb)
	{
		int count = 0;
		for (int x = tile.x; x < tile.x + GameTile.WIDTH; x++)
		{
			count += image.getRGB(x, tile.y + GameTile.HEIGHT - 9) == rgb ? 1 : 0;
		}
		return count;
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
