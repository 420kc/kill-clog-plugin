package com.killclog;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The tile frames ship baked (2.8.2): each is the game's tile edge nine-sliced to a tile's size, its 7 px corners as
 * drawn and its edges and middle stretched between them. The edge (test resources, game-tile-edge.png) stays as their
 * source and as the one killclog.com stretches itself, so a frame and the site's tile can never drift apart.
 */
public class GameTileFramesTest
{
	private static final int EDGE = 7;

	@Test
	public void eachBakedFrameIsTheEdgeNineSlicedToItsSize()
	{
		BufferedImage edge = ImageUtil.loadImageResource(GameTile.class, "game-tile-edge.png");
		assertBaked(edge, "game-tile-frame.png", GameTile.WIDTH, GameTile.HEIGHT);
		assertBaked(edge, "game-tile-frame-wide.png", GameTile.PAGE_WIDTH, GameTile.HEIGHT);
		assertBaked(edge, "game-tile-frame-page.png", GameTile.PAGE_WIDTH, GameTile.PAGE_HEIGHT);
	}

	private static void assertBaked(BufferedImage edge, String file, int width, int height)
	{
		BufferedImage baked = ImageUtil.loadImageResource(GameTile.class, file);
		assertEquals(file + " width", width, baked.getWidth());
		assertEquals(file + " height", height, baked.getHeight());
		BufferedImage sliced = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sliced.createGraphics();
		int[] dx = {0, EDGE, width - EDGE, width};
		int[] dy = {0, EDGE, height - EDGE, height};
		int[] sx = {0, EDGE, edge.getWidth() - EDGE, edge.getWidth()};
		int[] sy = {0, EDGE, edge.getHeight() - EDGE, edge.getHeight()};
		for (int i = 0; i < 9; i++)
		{
			int col = i % 3;
			int row = i / 3;
			g.drawImage(edge, dx[col], dy[row], dx[col + 1], dy[row + 1], sx[col], sy[row], sx[col + 1], sy[row + 1], null);
		}
		g.dispose();
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				assertEquals(file + " at " + x + "," + y, sliced.getRGB(x, y), baked.getRGB(x, y));
			}
		}
	}
}
