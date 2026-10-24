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

	@Test
	public void aLongTabShowsTwentyFourPagesAndTheWheelMovesThemThreeAtATime()
	{
		ClogTabTooltip card = painted(card(57));
		int top = firstRowY(card);
		assertEquals(0, TestViews.rowAt(card, "pageRows", top));
		assertEquals(23, TestViews.rowAt(card, "pageRows", top + 23 * LINE));
		assertEquals(-1, TestViews.rowAt(card, "pageRows", top + 24 * LINE));

		wheel(card, top + 5, 1);
		assertEquals(3, offset(card));
		// The mouse stayed put, so once the card repaints the page that scrolled under it is the one a press opens.
		painted(card);
		List<Integer> opened = new ArrayList<>();
		card.setOnOpenPage((press, page) -> opened.add(page));
		press(card, top + 5);
		assertEquals(Arrays.asList(3), opened);

		// The list stops at its ends.
		wheel(card, top + 5, 50);
		assertEquals(57 - ClogTabTooltip.VISIBLE_ROWS, offset(card));
		wheel(card, top + 5, -50);
		assertEquals(0, offset(card));
	}

	@Test
	public void theRailScrollsTheTabOnAPressOrADragAndOpensNoPage()
	{
		// For a player without a scroll wheel (2.7.3): the rail is a scroll bar.
		ClogTabTooltip card = painted(card(57));
		int top = firstRowY(card);
		int rail = card.getWidth() - NativeTooltip.getInset() - CardBody.RAIL_WIDTH;
		int bottom = top + ClogTabTooltip.VISIBLE_ROWS * LINE - 2;
		List<Integer> opened = new ArrayList<>();
		card.setOnOpenPage((press, page) -> opened.add(page));
		assertTrue("the grab zone takes the rail's gap too", CardBody.onRail(card, new Point(rail - CardBody.RAIL_GAP, top + 5)));
		assertTrue("and stops there", !CardBody.onRail(card, new Point(rail - CardBody.RAIL_GAP - 1, top + 5)));
		assertTrue(!CardBody.onRail(card, new Point(rail / 2, top + 5)));

		// A press low on the track jumps the window there, and opens no page under it.
		press(card, rail, bottom);
		release(card, rail, bottom);
		assertEquals(57 - ClogTabTooltip.VISIBLE_ROWS, offset(card));
		assertEquals(new ArrayList<>(), opened);

		// The thumb, taken where it sits, follows the pointer back to the top and stops there.
		painted(card);
		press(card, rail, bottom - 2);
		drag(card, rail, -100);
		assertEquals(0, offset(card));
		release(card, rail, -100);
		// The thumb moved half its travel is the list moved half its way, however the pointer wanders sideways.
		painted(card);
		int track = ClogTabTooltip.VISIBLE_ROWS * LINE;
		int travel = track - track * ClogTabTooltip.VISIBLE_ROWS / 57;
		press(card, rail, top + 5);
		drag(card, rail + 40, top + 5 + travel / 2);
		release(card, rail + 40, top + 5 + travel / 2);
		assertEquals((57 - ClogTabTooltip.VISIBLE_ROWS) / 2, offset(card));
		assertEquals(new ArrayList<>(), opened);

		// A press on a row still opens its page.
		painted(card);
		press(card, rail / 2, top + 5);
		assertEquals(1, opened.size());

		// A pinned card stays open through a press on its rail, and closes on one anywhere else.
		assertTrue(TooltipController.onCardRail(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0, 0, rail, top + 5, 1,
			false, MouseEvent.BUTTON1)));
		assertTrue(!TooltipController.onCardRail(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0, 0, rail / 2, top + 5,
			1, false, MouseEvent.BUTTON1)));
	}

	@Test
	public void aTabWithinANotchOfTheWindowShowsWholeAndALongerOneScrolls()
	{
		int notch = 3;
		ClogTabTooltip whole = painted(card(ClogTabTooltip.VISIBLE_ROWS + notch));
		wheel(whole, firstRowY(whole), 3);
		assertEquals(0, offset(whole));
		int last = ClogTabTooltip.VISIBLE_ROWS + notch - 1;
		assertEquals("every page shows", last, TestViews.rowAt(whole, "pageRows", firstRowY(whole) + last * LINE));
		// One page more and the tab scrolls: the window's rows, seven pixels wider for the rail.
		ClogTabTooltip scrolls = card(ClogTabTooltip.VISIBLE_ROWS + notch + 1);
		assertEquals(whole.getPreferredSize().width + 7, scrolls.getPreferredSize().width);
		assertEquals(whole.getPreferredSize().height - notch * LINE, scrolls.getPreferredSize().height);
	}

	@Test
	public void aDragOnOneSideOfAComparisonMovesTheOtherAndTheThumbLightsUnderThePointer()
	{
		ClogTabTooltip blue = painted(card(57));
		ClogTabTooltip red = painted(card(57));
		TitleTooltip.scrollTogether(blue, red);
		int top = firstRowY(blue);
		int rail = blue.getWidth() - NativeTooltip.getInset() - CardBody.RAIL_WIDTH;
		press(blue, rail, top + ClogTabTooltip.VISIBLE_ROWS * LINE - 2);
		release(blue, rail, top + ClogTabTooltip.VISIBLE_ROWS * LINE - 2);
		assertEquals(57 - ClogTabTooltip.VISIBLE_ROWS, offset(blue));
		assertEquals("the other side follows the drag", offset(blue), offset(red));

		// The thumb, now at the bottom, draws brighter while the pointer is on the rail.
		int y = top + ClogTabTooltip.VISIBLE_ROWS * LINE - 4;
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
		assertTrue("the grid scrolls", grid.scroll().offset() == 0 && !boxes.isEmpty());
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
		wheel(blue, firstRowY(blue), 2);
		assertEquals(6, offset(red));
		wheel(red, firstRowY(red), -1);
		assertEquals(3, offset(blue));
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
		press(back, firstRowY(back) + 2);
		assertEquals(1, backs[0]);
		assertEquals(Arrays.asList(0), opened);
	}

	@Test
	public void pageRowsWearTheirProgressionColorsWhenTheHighlighterIsOn()
	{
		java.awt.Color done = new java.awt.Color(1, 2, 3);
		java.awt.Color oneLeft = new java.awt.Color(4, 5, 6);
		java.awt.Color going = new java.awt.Color(7, 8, 9);
		java.awt.Color none = new java.awt.Color(10, 11, 12);
		KillClogConfig progress = new KillClogConfig()
		{
			@Override
			public java.awt.Color completedClogColor()
			{
				return done;
			}

			@Override
			public java.awt.Color missing1Color()
			{
				return oneLeft;
			}

			@Override
			public java.awt.Color inProgressClogColor()
			{
				return going;
			}

			@Override
			public java.awt.Color emptyClogColor()
			{
				return none;
			}
		};
		String[] names = {"Zulrah", "Vorkath", "Giant Mole", "Obor", "Bryophyta"};
		int[] obtained = {10, 9, 3, 0, -1};
		int[] total = {10, 10, 10, 10, 10};
		ClogTabTooltip colored = new ClogTabTooltip();
		colored.setTab("Bosses", new int[]{22, 50}, 50);
		colored.setPages(names, obtained, total, progress);
		ClogTabTooltip plain = new ClogTabTooltip();
		plain.setTab("Bosses", new int[]{22, 50}, 50);
		plain.setPages(names, obtained, total, null);

		java.util.Set<Integer> shown = colors(colored);
		for (java.awt.Color color : new java.awt.Color[]{done, oneLeft, going, none})
		{
			assertTrue(color.toString(), shown.contains(color.getRGB()));
			assertTrue(color.toString(), !colors(plain).contains(color.getRGB()));
		}
		// The empty page's count follows its name: the log's red only shows with the highlighter off.
		assertTrue(colors(plain).contains(TitleTooltip.CLOG_RED.getRGB()));
		assertTrue(!shown.contains(TitleTooltip.CLOG_RED.getRGB()));
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
		card.setPages(names, obtained, total, null);
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

	/** The first y that answers as the first page in view, found the way a pointer would. */
	private static int firstRowY(ClogTabTooltip card)
	{
		for (int y = 0; y < card.getHeight(); y++)
		{
			if (TestViews.rowAt(card, "pageRows", y) == offset(card))
			{
				return y;
			}
		}
		throw new AssertionError("no pages painted");
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

	/** The first page in view. */
	private static int offset(ClogTabTooltip card)
	{
		return card.scroll().offset() / NativeTooltip.LINE_HEIGHT;
	}
}
