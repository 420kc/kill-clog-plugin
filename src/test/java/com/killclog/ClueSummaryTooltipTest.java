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
import static org.junit.Assert.assertFalse;
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
		tiles = tiles.subList(ClueSummaryTooltip.MIMIC - 1, tiles.size() - 1);
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
		Point hardRare = tiles.get(ClueSummaryTooltip.MIMIC - 1 + 2);
		press(tip, hardRare.x + 10, hardRare.y + 10, MouseEvent.BUTTON1);
		assertEquals(List.of(2), opened);

		// A right click and the gap between tiles open nothing.
		press(tip, hardRare.x + 10, hardRare.y + 10, MouseEvent.BUTTON3);
		press(tip, hardRare.x - 2, hardRare.y + 10, MouseEvent.BUTTON1);
		assertEquals(List.of(2), opened);
	}

	@Test
	public void rareTilesOpenNothingUntilSomethingCanOpenThem()
	{
		ClueSummaryTooltip tip = card();
		List<Point> tiles = TestViews.tiles(paint(tip));
		Point rare = tiles.get(ClueSummaryTooltip.MIMIC - 1);
		assertEquals(-1, TestViews.tileAt(tip, "rareRows", rare.x + 10, rare.y + 10));
	}

	@Test
	public void everyTierAndMimicIsATileThatOpensItsCard()
	{
		ClueSummaryTooltip tip = card();
		List<Integer> opened = new ArrayList<>();
		tip.setOnOpenLine((press, line) -> opened.add(line));
		List<Point> tiles = TestViews.tiles(paint(tip));
		// Beginner to Master, then the five rare collections and Mimic last among them.
		assertEquals(ClueSummaryTooltip.MIMIC + ClueSummaryTooltip.RARE_LABELS.length, tiles.size());
		for (int line = 1; line < ClueSummaryTooltip.MIMIC; line++)
		{
			Point tile = tiles.get(line - 1);
			press(tip, tile.x + 10, tile.y + 10, MouseEvent.BUTTON1);
		}
		Point mimic = tiles.get(tiles.size() - 1);
		press(tip, mimic.x + 10, mimic.y + 10, MouseEvent.BUTTON1);
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), opened);
		// The rare collections open nothing of the lines'.
		Point rare = tiles.get(ClueSummaryTooltip.MIMIC - 1);
		press(tip, rare.x + 10, rare.y + 10, MouseEvent.BUTTON1);
		assertEquals(7, opened.size());
	}
	@Test
	public void theHoveredRareTileIsTrackedAndClearedOnExit()
	{
		ClueSummaryTooltip tip = card();
		tip.setOnOpenRare((press, row) ->
		{
		});
		Point raids = TestViews.tiles(paint(tip)).get(ClueSummaryTooltip.MIMIC - 1 + 1);
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
	public void withNoLogATierTileShowsItsCompletionsInWhite()
	{
		// A lookup with no synced log (Dylan, 2026-11-07): the tiles read the hiscores' counts, as 2.7.8's rows did.
		java.util.Map<String, Integer> scores = new java.util.HashMap<>();
		scores.put(net.runelite.client.hiscore.HiscoreSkill.CLUE_SCROLL_HARD.getName(), 300);
		ClueSummaryTooltip scored = new ClueSummaryTooltip();
		scored.setData(TestViews.hiscore(AccountType.REGULAR, java.util.Collections.singletonMap("Mimic", 7),
			java.util.Collections.emptyMap(), scores, java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
			0, 0, 0, -1), false);
		ClueSummaryTooltip bare = new ClueSummaryTooltip();
		bare.setData(null, false);
		BufferedImage withScores = paint(scored);
		BufferedImage without = paint(bare);
		List<Point> tiles = TestViews.tiles(withScores);
		// Beginner to Master, then the five rare collections, then Mimic.
		Point hard = tiles.get(3);
		Point easy = tiles.get(1);
		Point mimic = tiles.get(tiles.size() - 1);
		assertTrue("Hard reads its 300", hasWhite(withScores, hard, GameTile.WIDTH, GameTile.HEIGHT));
		assertTrue("Mimic reads its 7", hasWhite(withScores, mimic, GameTile.WIDTH, GameTile.HEIGHT));
		assertFalse("a tier the hiscores don't rank reads nothing", hasWhite(withScores, easy, GameTile.WIDTH, GameTile.HEIGHT));
		assertFalse(hasWhite(without, hard, GameTile.WIDTH, GameTile.HEIGHT));
		// Once the log is known, the tile reads its log progress instead.
		scored.setProgress(4, new int[]{10, 140});
		assertFalse(hasWhite(paint(scored), hard, GameTile.WIDTH, GameTile.HEIGHT));
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

	/** Whether any pixel of a tile at this top-left is the cards' white, the color a hiscores count is written in. */
	private static boolean hasWhite(BufferedImage image, Point tile, int width, int height)
	{
		int white = NativeTooltip.valueColor.getRGB();
		for (int y = tile.y; y < tile.y + height; y++)
		{
			for (int x = tile.x; x < tile.x + width; x++)
			{
				if (image.getRGB(x, y) == white)
				{
					return true;
				}
			}
		}
		return false;
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
