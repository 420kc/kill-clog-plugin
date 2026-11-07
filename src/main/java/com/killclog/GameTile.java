package com.killclog;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The game's own tile, as its Combat Achievements screen draws a boss (Dylan, 2026-11-07): a framed box with a
 * bracket in each corner, its name, a sprite, its count in the Collection Log's red, yellow and green, and a bar in
 * four parts. Cards lay them out three across, and a tile opens what it names.
 */
final class GameTile
{
	static final int WIDTH = 84;
	static final int HEIGHT = 78;
	static final int GAP = 4;
	static final int COLS = 3;
	// The frame keeps its corners, brackets and all, and stretches between them; a hovered tile's is a shade lighter.
	private static final int FRAME_EDGE = 7;
	private static final BufferedImage FRAME = ImageUtil.loadImageResource(GameTile.class, "game-tile.png");
	private static final BufferedImage HOVERED = ImageUtil.loadImageResource(GameTile.class, "game-tile-hover.png");
	private static final Font COUNT_FONT = FontManager.getRunescapeFont();
	private static final int NAME_TOP = 4;
	private static final int SPRITE_TOP = 17;
	private static final int COUNT_TOP = 50;
	private static final int BAR_PARTS = 4;
	private static final int BAR_HEIGHT = 8;
	private static final int BAR_PAD = 5;

	private GameTile()
	{
	}

	/** One tile: its name, sprite and count (a negative count is unknown), and what a press opens (-1, nothing). */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Tile
	{
		final String name;
		@Nullable
		final Image sprite;
		final int obtained;
		final int total;
		final int key;
	}

	/** The tiles three across, centered; one under the mouse lightens, and a press opens its key. */
	static CardBody.Part grid(CardBody.ClickRows clicks, List<Tile> tiles)
	{
		int cols = Math.min(tiles.size(), COLS);
		int rows = (tiles.size() + COLS - 1) / COLS;
		int width = cols * (WIDTH + GAP) - GAP;
		return CardBody.part(c -> width, c -> rows * (HEIGHT + GAP) - GAP, (c, y) ->
		{
			int left = (c.w - width) / 2;
			for (int i = 0; i < tiles.size(); i++)
			{
				Tile tile = tiles.get(i);
				Rectangle box = new Rectangle(left + i % COLS * (WIDTH + GAP), y + i / COLS * (HEIGHT + GAP), WIDTH, HEIGHT);
				paint(c, box, tile, tile.key >= 0 && clicks.boxShown(c, box, tile.key));
			}
		});
	}

	private static void paint(CardBody.Ctx c, Rectangle box, Tile tile, boolean hovered)
	{
		Graphics2D g = c.g;
		frame(g, hovered ? HOVERED : FRAME, box);
		boolean known = tile.obtained >= 0;
		boolean complete = known && tile.obtained >= tile.total;
		Color color = known ? TitleTooltip.completionColor(tile.obtained, tile.total) : TitleTooltip.MUTED_GRAY;
		int center = box.x + WIDTH / 2;
		Font font = g.getFont();

		g.setFont(c.bfm.getFont());
		g.setColor(complete ? color : NativeTooltip.labelColor);
		g.drawString(tile.name, center - c.bfm.stringWidth(tile.name) / 2, box.y + NAME_TOP + c.bfm.getAscent());
		if (tile.sprite != null)
		{
			g.drawImage(tile.sprite, center - tile.sprite.getWidth(null) / 2, box.y + SPRITE_TOP, null);
		}
		g.setFont(COUNT_FONT);
		FontMetrics fm = g.getFontMetrics();
		String count = known ? TitleTooltip.progressCountText(tile.obtained, tile.total)
			: TitleTooltip.progressPlaceholderText(tile.total);
		g.setColor(color);
		g.drawString(count, center - fm.stringWidth(count) / 2, box.y + COUNT_TOP + fm.getAscent());
		g.setFont(font);

		int x = box.x + BAR_PAD;
		int barY = box.y + HEIGHT - BAR_PAD - BAR_HEIGHT;
		int width = WIDTH - 2 * BAR_PAD;
		int lit = known && tile.total > 0 ? width * Math.min(tile.obtained, tile.total) / tile.total : 0;
		for (int part = 0; part < BAR_PARTS; part++)
		{
			int from = x + width * part / BAR_PARTS;
			int to = x + width * (part + 1) / BAR_PARTS;
			g.setColor(Color.BLACK);
			g.drawRect(from, barY, to - from - 1, BAR_HEIGHT - 1);
			g.setColor(NativeTooltip.railTrack(ClogSummaryTooltip.BAR_TRACK));
			g.fillRect(from + 1, barY + 1, to - from - 2, BAR_HEIGHT - 2);
			int filled = Math.min(x + lit, to - 1) - from - 1;
			if (filled > 0)
			{
				g.setColor(color);
				g.fillRect(from + 1, barY + 1, filled, BAR_HEIGHT - 2);
			}
		}
	}

	/** The frame at the box's size: its corners as drawn, its edges and middle stretched between them. */
	private static void frame(Graphics2D g, BufferedImage image, Rectangle box)
	{
		int[] dx = {box.x, box.x + FRAME_EDGE, box.x + box.width - FRAME_EDGE, box.x + box.width};
		int[] dy = {box.y, box.y + FRAME_EDGE, box.y + box.height - FRAME_EDGE, box.y + box.height};
		int[] sx = {0, FRAME_EDGE, image.getWidth() - FRAME_EDGE, image.getWidth()};
		int[] sy = {0, FRAME_EDGE, image.getHeight() - FRAME_EDGE, image.getHeight()};
		for (int i = 0; i < 9; i++)
		{
			int col = i % 3;
			int row = i / 3;
			g.drawImage(image, dx[col], dy[row], dx[col + 1], dy[row + 1], sx[col], sy[row], sx[col + 1], sy[row + 1], null);
		}
	}
}
