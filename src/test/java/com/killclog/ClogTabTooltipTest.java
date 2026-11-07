package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClogTabTooltipTest
{
	private static final int LINE = NativeTooltip.LINE_HEIGHT;
	// A notch of the wheel: a row of tiles.
	private static final int ROW = GameTile.PAGE_HEIGHT + GameTile.GAP;

	@Test
	public void aLongTabScrollsARowOfTilesANotchAndStopsAtItsEnds()
	{
		ClogTabTooltip card = painted(card(57));
		Point first = firstTile(card);
		assertEquals(0, TestViews.tileAt(card, "pageRows", first.x + 10, first.y + 10));
		assertEquals(1, TestViews.tileAt(card, "pageRows", first.x + GameTile.PAGE_WIDTH + GameTile.GAP + 10, first.y + 10));

		wheel(card, first.y + 10, 1);
		assertEquals(ROW, card.scroll().offset());
		// The mouse stayed put, so once the card repaints the page that scrolled under it is the one a press opens.
		painted(card);
		List<Integer> opened = new ArrayList<>();
		card.setOnOpenPage((press, page) -> opened.add(page));
		press(card, first.x + 10, first.y + 10);
		assertEquals(Arrays.asList(2), opened);

		wheel(card, first.y + 10, 50);
		assertEquals(range(card), card.scroll().offset());
		wheel(card, first.y + 10, -50);
		assertEquals(0, card.scroll().offset());
	}

	@Test
	public void theRailScrollsTheTabOnAPressOrADragAndOpensNoPage()
	{
		// For a player without a scroll wheel (2.7.3): the rail is a scroll bar.
		ClogTabTooltip card = painted(card(57));
		Point first = firstTile(card);
		int top = first.y;
		int rail = card.getWidth() - NativeTooltip.getInset() - CardBody.RAIL_WIDTH;
		int bottom = top + CardBody.WINDOW - 2;
		List<Integer> opened = new ArrayList<>();
		card.setOnOpenPage((press, page) -> opened.add(page));
		assertTrue("the grab zone takes the rail's gap too", CardBody.onRail(card, new Point(rail - CardBody.RAIL_GAP, top + 5)));
		assertTrue("and stops there", !CardBody.onRail(card, new Point(rail - CardBody.RAIL_GAP - 1, top + 5)));
		assertTrue(!CardBody.onRail(card, new Point(rail / 2, top + 5)));

		// A press low on the track jumps the window there, and opens no page under it.
		press(card, rail, bottom);
		release(card, rail, bottom);
		assertEquals(range(card), card.scroll().offset());
		assertEquals(new ArrayList<>(), opened);

		// The thumb, taken where it sits, follows the pointer back to the top and stops there.
		painted(card);
		press(card, rail, bottom - 2);
		drag(card, rail, -100);
		assertEquals(0, card.scroll().offset());
		release(card, rail, -100);
		assertEquals(new ArrayList<>(), opened);

		// A press on a tile still opens its page.
		painted(card);
		press(card, first.x + 10, first.y + 10);
		assertEquals(1, opened.size());

		// A pinned card stays open through a press on its rail, and closes on one anywhere else.
		assertTrue(TooltipController.onCardRail(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0, 0, rail, top + 5, 1,
			false, MouseEvent.BUTTON1)));
		assertTrue(!TooltipController.onCardRail(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0, 0, rail / 2, top + 5,
			1, false, MouseEvent.BUTTON1)));
	}

	@Test
	public void aShortTabShowsWholeAndALongOneScrollsSevenPixelsWiderForItsRail()
	{
		ClogTabTooltip whole = painted(card(4));
		wheel(whole, firstTile(whole).y, 3);
		assertEquals(0, whole.scroll().offset());
		assertEquals(4, TestViews.tiles(image(whole), GameTile.PAGE_WIDTH, GameTile.PAGE_HEIGHT).size());
		assertEquals(whole.getPreferredSize().width + 7, card(57).getPreferredSize().width);
	}

	@Test
	public void aDragOnOneSideOfAComparisonMovesTheOtherAndTheThumbLightsUnderThePointer()
	{
		ClogTabTooltip blue = painted(card(57));
		ClogTabTooltip red = painted(card(57));
		TitleTooltip.scrollTogether(blue, red);
		int top = firstTile(blue).y;
		int rail = blue.getWidth() - NativeTooltip.getInset() - CardBody.RAIL_WIDTH;
		press(blue, rail, top + CardBody.WINDOW - 2);
		release(blue, rail, top + CardBody.WINDOW - 2);
		assertEquals(range(blue), blue.scroll().offset());
		assertEquals("the other side follows the drag", blue.scroll().offset(), red.scroll().offset());

		// The thumb, now at the bottom, draws brighter while the pointer is on the rail.
		int y = top + CardBody.WINDOW - 4;
		move(blue, rail / 2, y);
		int dim = pixel(blue, rail + 1, y);
		move(blue, rail, y);
		assertTrue("brighter under the pointer", brightness(pixel(blue, rail + 1, y)) > brightness(dim));
	}

	@Test
	public void aGridCardsRailNeverReachesItsLastColumn() throws Exception
	{
		ImgTooltip grid = new ImgTooltip(4);
		grid.setTitle("Sixty");
		List<Integer> ids = new ArrayList<>();
		for (int i = 1; i <= 60; i++)
		{
			ids.add(i);
		}
		grid.setObtained(0, 60);
		grid.setItems(60, ids, java.util.Collections.emptySet(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), null);
		java.awt.Dimension size = grid.getPreferredSize();
		grid.setSize(size);
		Graphics2D graphics = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		grid.paint(graphics);
		graphics.dispose();
		java.lang.reflect.Field field = TooltipItemHover.class.getDeclaredField("hitBoxes");
		field.setAccessible(true);
		java.lang.reflect.Field bounds = TooltipItemHover.HitBox.class.getDeclaredField("bounds");
		bounds.setAccessible(true);
		List<?> boxes = (List<?>) field.get(grid.itemHover);
		java.lang.reflect.Field range = CardBody.Scroll.class.getDeclaredField("range");
		range.setAccessible(true);
		assertTrue("the grid scrolls, so it has a rail to stay clear of", (int) range.get(grid.scroll()) > 0 && !boxes.isEmpty());
		for (Object box : boxes)
		{
			java.awt.Rectangle r = (java.awt.Rectangle) bounds.get(box);
			for (int x = r.x; x < r.x + r.width; x++)
			{
				assertTrue("an item's pixel is never the rail's", !CardBody.onRail(grid, new Point(x, r.y + r.height / 2)));
			}
		}
	}

	private static void move(ClogTabTooltip card, int x, int y)
	{
		for (java.awt.event.MouseMotionListener listener : card.getMouseMotionListeners())
		{
			listener.mouseMoved(new MouseEvent(card, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
		}
	}

	private static int pixel(ClogTabTooltip card, int x, int y)
	{
		BufferedImage image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();
		return image.getRGB(x, y);
	}

	private static int brightness(int rgb)
	{
		return ((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff);
	}

	@Test
	public void aComparisonsTwoSidesStayOnTheSamePages()
	{
		ClogTabTooltip blue = painted(card(57));
		ClogTabTooltip red = painted(card(57));
		TitleTooltip.scrollTogether(blue, red);
		wheel(blue, firstTile(blue).y, 2);
		assertEquals(2 * ROW, red.scroll().offset());
		wheel(red, firstTile(red).y, -1);
		assertEquals(ROW, blue.scroll().offset());
	}

	@Test
	public void aCopyHoldsEveryPageAndTheCardOnScreenStaysWhereItWas()
	{
		ClogTabTooltip card = painted(card(57));
		Point first = firstTile(card);
		wheel(card, first.y + 5, 1);
		painted(card);
		Dimension shown = card.getSize();
		int range = range(card);
		BufferedImage copy = TooltipController.cardImage(card);
		// Every page at full height in place of the window, and no rail beside them.
		assertEquals(shown.height + range, copy.getHeight());
		assertEquals(shown.width - CardBody.RAIL_GAP - CardBody.RAIL_WIDTH, copy.getWidth());
		assertEquals(shown, card.getSize());
		assertEquals(ROW, card.scroll().offset());
		assertEquals("the page under the pointer is still the one in view", 2,
			TestViews.tileAt(card, "pageRows", first.x + 10, first.y + 10));
		wheel(card, first.y + 5, 1);
		assertEquals("and the wheel still moves it", 2 * ROW, card.scroll().offset());
	}

	@Test
	public void aCopyOfAComparisonHoldsBothCardsWhole()
	{
		ClogTabTooltip blue = card(57);
		ClogTabTooltip red = card(57);
		SideBySideTooltip pair = new SideBySideTooltip("Blue", blue, "Red", red);
		Dimension shown = pair.getPreferredSize();
		pair.setSize(shown);
		pair.doLayout();
		Graphics2D graphics = new BufferedImage(shown.width, shown.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		pair.paint(graphics);
		graphics.dispose();
		BufferedImage copy = TooltipController.cardImage(pair);
		assertEquals(shown.height + range(blue), copy.getHeight());
		assertEquals(shown, pair.getSize());
		assertEquals("each card on screen keeps its window", shown.height, pair.getPreferredSize().height);
	}

	@Test
	public void aCopyOfAScrolledGridHoldsEveryRow()
	{
		ImgTooltip grid = new ImgTooltip(4);
		grid.setTitle("Sixty");
		List<Integer> ids = new ArrayList<>();
		for (int i = 1; i <= 60; i++)
		{
			ids.add(i);
		}
		grid.setObtained(0, 60);
		grid.setItems(60, ids, java.util.Collections.emptySet(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), null);
		Dimension shown = grid.getPreferredSize();
		grid.setSize(shown);
		Graphics2D graphics = new BufferedImage(shown.width, shown.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		grid.paint(graphics);
		graphics.dispose();
		assertTrue("the grid scrolls", CardBody.onRail(grid, new Point(shown.width - NativeTooltip.getInset() - 1,
			shown.height / 2)));
		BufferedImage copy = TooltipController.cardImage(grid);
		assertTrue("the copy runs past the window", copy.getHeight() > shown.height + CardBody.GRID_CELL);
		assertEquals(shown, grid.getSize());
		assertTrue("the card on screen still scrolls", CardBody.onRail(grid, new Point(shown.width - NativeTooltip.getInset() - 1,
			shown.height / 2)));
	}

	@Test
	public void theWayBackLeadsTheCardAboveItsTitle()
	{
		ClogTabTooltip bare = card(5);
		ClogTabTooltip back = card(5);
		int[] backs = new int[1];
		back.setBack("< Collection Log", press -> backs[0]++);
		// One line more, and nothing else.
		assertEquals(bare.getPreferredSize().height + LINE, back.getPreferredSize().height);

		painted(back);
		List<Integer> opened = new ArrayList<>();
		back.setOnOpenPage((press, page) -> opened.add(page));
		int row = -1;
		for (int y = 0; y < back.getHeight() && row < 0; y++)
		{
			row = back.onBackRow(y) ? y : -1;
		}
		assertEquals("the way back is the card's first line", NativeTooltip.getInset(), row);
		press(back, row + 2);
		assertEquals(1, backs[0]);
		assertTrue(opened.isEmpty());
		press(back, firstTile(back).x + 10, firstTile(back).y + 10);
		assertEquals(1, backs[0]);
		assertEquals(Arrays.asList(0), opened);
	}

	@Test
	public void pageTilesWearTheLogsRedYellowAndGreen()
	{
		ClogTabTooltip card = new ClogTabTooltip();
		card.setTab("Bosses", new int[]{22, 50}, 50);
		card.setPages(new String[]{"Zulrah", "Vorkath", "Obor"}, new int[]{10, 3, 0}, new int[]{10, 10, 10});
		java.util.Set<Integer> shown = colors(card);
		for (java.awt.Color color : new java.awt.Color[]{TitleTooltip.CLOG_GREEN, TitleTooltip.CLOG_YELLOW, TitleTooltip.CLOG_RED})
		{
			assertTrue(color.toString(), shown.contains(color.getRGB()));
		}
	}

	private static java.util.Set<Integer> colors(ClogTabTooltip card)
	{
		Dimension size = card.getPreferredSize();
		card.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();
		java.util.Set<Integer> colors = new java.util.HashSet<>();
		for (int y = 0; y < size.height; y++)
		{
			for (int x = 0; x < size.width; x++)
			{
				colors.add(image.getRGB(x, y));
			}
		}
		return colors;
	}

	private static ClogTabTooltip card(int pages)
	{
		String[] names = new String[pages];
		int[] obtained = new int[pages];
		int[] total = new int[pages];
		Arrays.fill(names, "Thermonuclear Smoke Devil");
		Arrays.fill(total, 9);
		ClogTabTooltip card = new ClogTabTooltip();
		card.setTab("Bosses", new int[]{12, 34}, 34);
		card.setPages(names, obtained, total);
		// Pages answer a press once something can open them.
		card.setOnOpenPage((press, page) ->
		{
		});
		return card;
	}

	private static ClogTabTooltip painted(ClogTabTooltip card)
	{
		Dimension size = card.getPreferredSize();
		card.setSize(size);
		Graphics2D graphics = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		card.paint(graphics);
		graphics.dispose();
		return card;
	}
	/** The top-left of the first page tile in view. */
	private static Point firstTile(ClogTabTooltip card)
	{
		return TestViews.tiles(image(card), GameTile.PAGE_WIDTH, GameTile.PAGE_HEIGHT).get(0);
	}

	private static BufferedImage image(ClogTabTooltip card)
	{
		BufferedImage image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();
		return image;
	}

	/** How far the tab's tiles can scroll. */
	private static int range(ClogTabTooltip card)
	{
		return TestViews.field(card.scroll(), "range");
	}

	private static void wheel(ClogTabTooltip card, int y, int notches)
	{
		for (MouseWheelListener listener : card.getMouseWheelListeners())
		{
			listener.mouseWheelMoved(new MouseWheelEvent(card, MouseEvent.MOUSE_WHEEL, 0, 0, 5, y, 0, false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches));
		}
	}

	private static void press(ClogTabTooltip card, int y)
	{
		press(card, 5, y);
	}

	private static void press(ClogTabTooltip card, int x, int y)
	{
		for (MouseListener listener : card.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
		}
	}

	private static void drag(ClogTabTooltip card, int x, int y)
	{
		for (MouseMotionListener listener : card.getMouseMotionListeners())
		{
			listener.mouseDragged(new MouseEvent(card, MouseEvent.MOUSE_DRAGGED, 0, 0, x, y, 0, false, MouseEvent.BUTTON1));
		}
	}

	private static void release(ClogTabTooltip card, int x, int y)
	{
		for (MouseListener listener : card.getMouseListeners())
		{
			listener.mouseReleased(new MouseEvent(card, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
		}
	}
}
