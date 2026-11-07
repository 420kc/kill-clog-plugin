package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClueSummaryTooltipTest
{
	@Test
	public void rareTilesAnswerOnlyWhereTheyWerePainted()
	{
		ClueSummaryTooltip tip = card();
		tip.setOnOpenRare((press, row) ->
		{
		});
		List<Point> tiles = TestViews.tiles(paint(tip));
		// The five rare collections, three across.
		assertEquals(ClueSummaryTooltip.RARE_LABELS.length, tiles.size());
		assertEquals(tiles.get(0).x, tiles.get(3).x);
		for (int row = 0; row < tiles.size(); row++)
		{
			Point tile = tiles.get(row);
			assertEquals(row, TestViews.tileAt(tip, "rareRows", tile.x, tile.y));
			assertEquals(row, TestViews.tileAt(tip, "rareRows", tile.x + GameTile.WIDTH - 1, tile.y + GameTile.HEIGHT - 1));
		}
		// Not above the section, and not in the gap between two tiles.
		assertEquals(-1, TestViews.tileAt(tip, "rareRows", tiles.get(0).x, tiles.get(0).y - 1));
		assertEquals(-1, TestViews.tileAt(tip, "rareRows", tiles.get(0).x + GameTile.WIDTH + 1, tiles.get(0).y + 10));
	}

	@Test
	public void pressingARareTileOpensThatCollectionAndNothingElseDoes()
	{
		ClueSummaryTooltip tip = card();
		List<Integer> opened = new ArrayList<>();
		tip.setOnOpenRare((press, row) -> opened.add(row));

		// Before the first paint there is no rare section to press.
		press(tip, 5, 200, MouseEvent.BUTTON1);
		assertTrue(opened.isEmpty());

		List<Point> tiles = TestViews.tiles(paint(tip));
		Point hardRare = tiles.get(2);
		press(tip, hardRare.x + 10, hardRare.y + 10, MouseEvent.BUTTON1);
		assertEquals(List.of(2), opened);

		// A tier line opens its own card, not a rare one; a right click and the gap between tiles open nothing.
		press(tip, 5, hardRare.y - 40, MouseEvent.BUTTON1);
		press(tip, hardRare.x + 10, hardRare.y + 10, MouseEvent.BUTTON3);
		press(tip, hardRare.x - 2, hardRare.y + 10, MouseEvent.BUTTON1);
		assertEquals(List.of(2), opened);
	}

	@Test
	public void rareTilesOpenNothingUntilSomethingCanOpenThem()
	{
		ClueSummaryTooltip tip = card();
		List<Point> tiles = TestViews.tiles(paint(tip));
		assertEquals(-1, TestViews.tileAt(tip, "rareRows", tiles.get(0).x + 10, tiles.get(0).y + 10));
	}

	@Test
	public void everyTierAndMimicOpensItsCardFromTheSummaryButAllDoesNot()
	{
		ClueSummaryTooltip tip = card();
		paint(tip);
		List<Integer> opened = new ArrayList<>();
		tip.setOnOpenLine((press, line) -> opened.add(line));
		int beginner = lineY(tip, 1);
		for (int line = 1; line <= ClueSummaryTooltip.MIMIC; line++)
		{
			press(tip, 5, lineY(tip, line) + 3, MouseEvent.BUTTON1);
		}
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), opened);
		// All sums the tiers: there's no one log for it to open.
		assertEquals(-1, TestViews.rowAt(tip, "lineRows", beginner - NativeTooltip.LINE_HEIGHT + 3));
	}

	private static int lineY(ClueSummaryTooltip tip, int line)
	{
		for (int y = 0; y < tip.getHeight(); y++)
		{
			if (TestViews.rowAt(tip, "lineRows", y) == line)
			{
				return y;
			}
		}
		throw new AssertionError("no line " + line + " painted");
	}

	@Test
	public void theHoveredRareTileIsTrackedAndClearedOnExit()
	{
		ClueSummaryTooltip tip = card();
		tip.setOnOpenRare((press, row) ->
		{
		});
		Point raids = TestViews.tiles(paint(tip)).get(1);
		move(tip, raids.x + 10, raids.y + 10);
		assertEquals(1, TestViews.hoveredRow(tip, "rareRows"));
		move(tip, 5, 2);
		assertEquals(-1, TestViews.hoveredRow(tip, "rareRows"));
		move(tip, raids.x + 10, raids.y + 10);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(event(tip, MouseEvent.MOUSE_EXITED, 5, 2, MouseEvent.NOBUTTON));
		}
		assertEquals(-1, TestViews.hoveredRow(tip, "rareRows"));
	}

	@Test
	public void progressNeverChangesTheCardsHeight()
	{
		ClueSummaryTooltip bare = new ClueSummaryTooltip();
		bare.setData(null, false);
		ClueSummaryTooltip withProgress = card();
		// Same rows and tiles either way, so a comparison pair never staggers.
		assertEquals(bare.getPreferredSize().height, withProgress.getPreferredSize().height);
		assertTrue(bare.getPreferredSize().width <= withProgress.getPreferredSize().width);

		// Counts cleared again (an unsynced player): back to the bare width.
		for (int line = 1; line <= 7; line++)
		{
			withProgress.setProgress(line, null);
		}
		for (int row = 0; row < ClueSummaryTooltip.RARE_LABELS.length; row++)
		{
			withProgress.setRare(row, null);
		}
		assertEquals(bare.getPreferredSize(), withProgress.getPreferredSize());
	}

	private static ClueSummaryTooltip card()
	{
		ClueSummaryTooltip tip = new ClueSummaryTooltip();
		tip.setData(null, false);
		for (int line = 1; line <= 6; line++)
		{
			tip.setProgress(line, new int[]{line * 10, 140});
		}
		tip.setProgress(7, new int[]{1, 1});
		for (int row = 0; row < ClueSummaryTooltip.RARE_LABELS.length; row++)
		{
			tip.setRare(row, new int[]{row, 38});
		}
		return tip;
	}

	private static BufferedImage paint(ClueSummaryTooltip tip)
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tip.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static void press(ClueSummaryTooltip tip, int x, int y, int button)
	{
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mousePressed(event(tip, MouseEvent.MOUSE_PRESSED, x, y, button));
		}
	}

	private static void move(ClueSummaryTooltip tip, int x, int y)
	{
		for (MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(event(tip, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON));
		}
	}

	private static MouseEvent event(ClueSummaryTooltip tip, int id, int x, int y, int button)
	{
		return new MouseEvent(tip, id, System.currentTimeMillis(), 0, x, y, 1, false, button);
	}
}
