package com.killclog;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import lombok.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The game's own tile, as its Combat Achievements screen draws a boss (Dylan, 2026-11-07): a framed box with a
 * bracket in each corner, its name, a sprite, its count in the Collection Log's red, yellow and green, and a bar in
 * four parts. Cards lay them out three across; a tab's pages, two across, wider and without a sprite, their names on
 * up to two lines. A tile opens what it names.
 */
final class GameTile
{
	static final int WIDTH = 84;
	static final int HEIGHT = 78;
	static final int GAP = 4;
	static final int COLS = 3;
	static final int PAGE_WIDTH = (COLS * WIDTH - GAP) / 2;
	static final int PAGE_HEIGHT = 62;
	private static final int PAGE_COUNT_TOP = 34;
	private static final int NAME_LINE = 13;
	private static final int NAME_PAD = 4;
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
		// The sprite drawn faint, as an item not yet held is.
		boolean faint;
		// The item the sprite is, which the card's hover band names while the tile is under the pointer (-1, none).
		int itemId = -1;
		String itemName;
		// A known count's color in place of the log's red, yellow and green, its name wearing it too: a page's
		// progression color while the highlighter is on, as the boss list's rows do (null, the log's).
		@Nullable
		Color wear;

		Tile faint(boolean faint)
		{
			this.faint = faint;
			return this;
		}

		// The hiscores' count for what the tile stands for (completions, kills), shown in white while its log
		// progress is unknown, so a lookup with no synced log still reads (Dylan, 2026-11-07); 0 or less, none.
		int score;
		// Its own count line in white, the bar still the log's: Most Killed's kills over that boss's log.
		@Nullable
		String countText;

		Tile wear(@Nullable Color wear)
		{
			this.wear = wear;
			return this;
		}

		Tile score(int score)
		{
			this.score = score;
			return this;
		}

		Tile count(String text)
		{
			countText = text;
			return this;
		}

		Tile item(int id, String name)
		{
			itemId = id;
			itemName = name;
			return this;
		}
	}

	/** The tiles three across, or two or four two across and wider so none sits alone; a press opens a tile's key. */
	static CardBody.Part grid(CardBody.ClickRows clicks, List<Tile> tiles)
	{
		return tiles.size() == 2 || tiles.size() == 4 ? grid(clicks, tiles, 2, PAGE_WIDTH, HEIGHT)
			: grid(clicks, tiles, COLS, WIDTH, HEIGHT);
	}

	/** A tab's pages, two across, as the game's Combat Achievements tiles its bosses. */
	static CardBody.Part pages(CardBody.ClickRows clicks, List<Tile> tiles)
	{
		return grid(clicks, tiles, 2, PAGE_WIDTH, PAGE_HEIGHT);
	}

	private static CardBody.Part grid(CardBody.ClickRows clicks, List<Tile> tiles, int cols, int w, int h)
	{
		int rows = (tiles.size() + cols - 1) / cols;
		int width = Math.min(tiles.size(), cols) * (w + GAP) - GAP;
		return CardBody.part(c -> width, c -> rows * (h + GAP) - GAP, (c, y) ->
		{
			int left = (c.w - width) / 2;
			for (int i = 0; i < tiles.size(); i++)
			{
				Tile tile = tiles.get(i);
				Rectangle box = new Rectangle(left + i % cols * (w + GAP), y + i / cols * (h + GAP), w, h);
				paint(c, box, tile, tile.key >= 0 && clicks.boxShown(c, box, tile.key));
				if (tile.itemName != null)
				{
					// Green while held, red while faint, as the card's other items read in the band.
					c.hits.add(new TooltipItemHover.HitBox(1, tile.itemId, tile.itemName, box, !tile.faint));
				}
			}
		});
	}

	private static void paint(CardBody.Ctx c, Rectangle box, Tile tile, boolean hovered)
	{
		Graphics2D g = c.g;
		frame(g, hovered ? HOVERED : FRAME, box);
		boolean known = tile.obtained >= 0;
		boolean complete = known && tile.obtained >= tile.total;
		Color color = !known ? TitleTooltip.MUTED_GRAY
			: tile.wear != null ? tile.wear : TitleTooltip.completionColor(tile.obtained, tile.total);
		int center = box.x + box.width / 2;
		boolean page = box.height == PAGE_HEIGHT;
		Font font = g.getFont();

		// The name in bold. A page's, with no sprite under it, breaks at a space onto two lines where it must, and takes
		// the regular font only where even that won't fit; a sprite's tile keeps its short name on one line.
		int room = box.width - 2 * NAME_PAD;
		FontMetrics nfm = c.bfm;
		String[] lines = !page || nfm.stringWidth(tile.name) <= room ? new String[]{tile.name} : split(tile.name, nfm);
		if (page && Math.max(nfm.stringWidth(lines[0]), nfm.stringWidth(lines[lines.length - 1])) > room)
		{
			nfm = g.getFontMetrics(COUNT_FONT);
			lines = split(tile.name, nfm);
		}
		g.setFont(nfm.getFont());
		g.setColor(complete || known && tile.wear != null ? color : NativeTooltip.labelColor);
		int nameY = box.y + NAME_TOP + nfm.getAscent() + (page && lines.length == 1 ? NAME_LINE / 2 : 0);
		for (String line : lines)
		{
			g.drawString(line, center - nfm.stringWidth(line) / 2, nameY);
			nameY += NAME_LINE;
		}
		if (tile.sprite != null)
		{
			Composite solid = g.getComposite();
			g.setComposite(tile.faint ? AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f) : solid);
			g.drawImage(tile.sprite, center - tile.sprite.getWidth(null) / 2, box.y + SPRITE_TOP, null);
			g.setComposite(solid);
		}
		g.setFont(COUNT_FONT);
		FontMetrics fm = g.getFontMetrics();
		boolean scored = tile.countText != null || !known && tile.score > 0;
		String count = tile.countText != null ? tile.countText : scored ? TitleTooltip.grouped(tile.score)
			: tile.total <= 0 ? "" : known ? TitleTooltip.progressCountText(tile.obtained, tile.total)
			: TitleTooltip.progressPlaceholderText(tile.total);
		g.setColor(scored ? NativeTooltip.valueColor : color);
		g.drawString(count, center - fm.stringWidth(count) / 2, box.y + (page ? PAGE_COUNT_TOP : COUNT_TOP) + fm.getAscent());
		g.setFont(font);

		int x = box.x + BAR_PAD;
		int barY = box.y + box.height - BAR_PAD - BAR_HEIGHT;
		int width = box.width - 2 * BAR_PAD;
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

	/** A name broken at the space that leaves its longer half shortest. */
	private static String[] split(String name, FontMetrics fm)
	{
		String[] best = {name};
		int widest = Integer.MAX_VALUE;
		for (int at = name.indexOf(' '); at > 0; at = name.indexOf(' ', at + 1))
		{
			String[] halves = {name.substring(0, at), name.substring(at + 1)};
			int width = Math.max(fm.stringWidth(halves[0]), fm.stringWidth(halves[1]));
			if (width < widest)
			{
				widest = width;
				best = halves;
			}
		}
		return best;
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
