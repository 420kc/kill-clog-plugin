package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * A card's body as one list of parts. Each part measures itself, paints itself and registers its own
 * hover and click areas, so a card states its layout once: its size and its painting can't disagree,
 * and a new row is one more part.
 */
final class CardBody
{
	/** One piece of a body: its width and height, and how it paints at a given y. */
	interface Part
	{
		int width(Ctx c);

		int height(Ctx c);

		void paint(Ctx c, int y);
	}

	/** Paints one part at y. */
	interface Painter
	{
		void paint(Ctx c, int y);
	}

	/** Paints a clickable row at y, white while hovered. */
	interface RowPainter
	{
		void paint(Ctx c, int y, boolean hovered);
	}

	/** What a part sees: its card, the small and bold fonts, and while painting the surface and width. */
	static final class Ctx
	{
		final TitleTooltip card;
		final FontMetrics fm;
		final FontMetrics bfm;
		final int available;
		final Graphics2D g;
		final int w;
		final List<TooltipItemHover.HitBox> hits = new ArrayList<>();

		private Ctx(TitleTooltip card, FontMetrics fm, FontMetrics bfm, int available, Graphics2D g, int w)
		{
			this.card = card;
			this.fm = fm;
			this.bfm = bfm;
			this.available = available;
			this.g = g;
			this.w = w;
		}

		int inset()
		{
			return NativeTooltip.getInset();
		}
	}

	private final List<Part> parts = new ArrayList<>();

	CardBody add(Part part)
	{
		parts.add(part);
		return this;
	}

	/** The body's size: as wide as its widest part, as tall as its parts together. */
	Dimension size(TitleTooltip card, int available)
	{
		Ctx c = new Ctx(card, card.getFontMetrics(FontManager.getRunescapeSmallFont()),
			card.getFontMetrics(FontManager.getRunescapeBoldFont()), available, null, 0);
		int width = 0;
		int height = 0;
		for (Part part : parts)
		{
			width = Math.max(width, part.width(c));
			height += part.height(c);
		}
		return new Dimension(width, height);
	}

	/** Paints every part down from startY in the small font; returns the item hit boxes they laid down. */
	List<TooltipItemHover.HitBox> paint(TitleTooltip card, Graphics2D g, int w, int startY)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		Ctx c = new Ctx(card, g.getFontMetrics(), g.getFontMetrics(FontManager.getRunescapeBoldFont()),
			w - 2 * NativeTooltip.getInset(), g, w);
		int y = startY;
		for (Part part : parts)
		{
			part.paint(c, y);
			y += part.height(c);
		}
		return c.hits;
	}

	// ---- Parts --------------------------------------------------------------------------------

	static Part part(ToIntFunction<Ctx> width, ToIntFunction<Ctx> height, Painter painter)
	{
		return new Part()
		{
			@Override
			public int width(Ctx c)
			{
				return width.applyAsInt(c);
			}

			@Override
			public int height(Ctx c)
			{
				return height.applyAsInt(c);
			}

			@Override
			public void paint(Ctx c, int y)
			{
				painter.paint(c, y);
			}
		};
	}

	/** One line of the given height painted by hand, measured by width. */
	static Part row(int height, ToIntFunction<Ctx> width, Painter painter)
	{
		return part(width, c -> height, painter);
	}

	static Part gap(int height)
	{
		return part(c -> 0, c -> height, (c, y) ->
		{
		});
	}

	/** Holds the body at least this wide without taking any room. */
	static Part minWidth(ToIntFunction<Ctx> width)
	{
		return part(width, c -> 0, (c, y) ->
		{
		});
	}

	/** An orange label with its value right after it. */
	static Part line(String label, String value, Color color)
	{
		return row(NativeTooltip.LINE_HEIGHT, c -> c.fm.stringWidth(label + value),
			(c, y) -> TitleTooltip.drawLabelValue(c.g, c.fm, c.inset(), y + c.fm.getAscent(), label, value, color));
	}

	static Part line(String label, String value)
	{
		return line(label, value, Color.WHITE);
	}

	static Part text(String text, Color color, boolean measured)
	{
		return row(NativeTooltip.LINE_HEIGHT, c -> measured ? c.fm.stringWidth(text) : 0, (c, y) ->
		{
			c.g.setColor(color);
			c.g.drawString(text, c.inset(), y + c.fm.getAscent());
		});
	}

	static Part subheader(String text)
	{
		return row(TitleTooltip.SUBHEADER_HEIGHT, c -> c.bfm.stringWidth(text), (c, y) -> c.card.paintSubheader(c.g, y, text));
	}

	static Part separator(int pad)
	{
		return row(TitleTooltip.separatorHeight(pad), c -> 0, (c, y) -> c.card.paintSeparator(c.g, c.w, y, pad));
	}

	private static final int BAND_PAD = 4;

	/**
	 * The line a card names its hovered item on, above its sprites between a divider above and one below, so
	 * the name has the card's full width and its own place while the sprites scroll under it.
	 */
	static Part hoverBand()
	{
		return band(BAND_PAD);
	}

	/** The same band straight under the header, the header's own divider the one above it. */
	static Part headerHoverBand()
	{
		return band(-1 - TitleTooltip.SEPARATOR_GAP);
	}

	/** A band whose top divider sits {@code above} the part's top; a negative one is the divider already there. */
	private static Part band(int above)
	{
		return part(c -> 0, c -> above + 1 + TitleTooltip.hoverRowHeight(c.fm) + 1 + BAND_PAD, (c, y) ->
		{
			int top = y + above;
			int height = TitleTooltip.hoverRowHeight(c.fm);
			c.g.setColor(TitleTooltip.SEPARATOR_COLOR);
			if (above >= 0)
			{
				c.g.drawLine(c.inset(), top, c.w - c.inset() - 1, top);
			}
			c.g.drawLine(c.inset(), top + height + 1, c.w - c.inset() - 1, top + height + 1);
			c.card.paintHeaderHoverLine(c.g, c.fm, c.w, top + 1 + c.fm.getAscent());
		});
	}

	/** The hovered item's name under its section's sprites, measured for every name it may show. */
	static Part hoverLine(int section, String... names)
	{
		return part(c ->
		{
			int width = 0;
			for (String name : names)
			{
				width = Math.max(width, c.fm.stringWidth(name));
			}
			return width;
		}, c -> TitleTooltip.hoverRowHeight(c.fm), (c, y) -> c.card.paintSectionHoverLine(c.g, c.fm, c.w, y, section));
	}

	/**
	 * A centered row of full-size item sprites, a grid one row high: unobtained ones dimmed, quantities in the
	 * corner, each hover-naming and wiki-linking like the grids do.
	 */
	static Part sprites(int section, @Nullable TooltipItemSprites sprites, int[] ids, String[] names, int[] counts,
		int pad)
	{
		Map<Integer, String> named = new HashMap<>();
		Map<Integer, Integer> held = new HashMap<>();
		Set<Integer> obtained = new HashSet<>();
		for (int i = 0; i < ids.length; i++)
		{
			named.put(ids[i], names[i]);
			held.put(ids[i], counts[i]);
			if (counts[i] > 0)
			{
				obtained.add(ids[i]);
			}
		}
		int width = ids.length * (GRID_SPRITE + pad) - pad;
		return row(GRID_SPRITE, c -> width, (c, y) -> c.hits.addAll(TooltipItemSprites.paintGrid(c.g, sprites, named,
			section, TooltipData.itemList(ids), obtained, held, c.inset() + (c.w - 2 * c.inset() - width) / 2, y,
			ids.length, GRID_SPRITE, GRID_SPRITE + pad)));
	}

	/** One row that opens something: it answers hover in white and a left press with its key. */
	static Part clickRow(ClickRows rows, int key, ToIntFunction<Ctx> width, RowPainter painter)
	{
		return row(NativeTooltip.LINE_HEIGHT, width, (c, y) ->
		{
			rows.span(y, key);
			painter.paint(c, y, rows.hovered == key);
		});
	}

	static final int RAIL_WIDTH = 3;
	static final int RAIL_GAP = 4;
	// Every scrolled card shows the same height: 24 lines, the length of a long Collection Log tab.
	static final int WINDOW = 24 * NativeTooltip.LINE_HEIGHT;

	/** The thin rail beside a scrolled window: its track, and the thumb where the window sits. */
	static void rail(Ctx c, int y, int track, int shown, int total, int offset, int range)
	{
		int x = c.w - c.inset() - RAIL_WIDTH;
		int thumb = Math.max(NativeTooltip.LINE_HEIGHT, track * shown / total);
		c.g.setColor(TitleTooltip.SEPARATOR_COLOR);
		c.g.fillRect(x, y, RAIL_WIDTH, track);
		c.g.setColor(NativeTooltip.OSRS_ORANGE);
		c.g.fillRect(x, y + (track - thumb) * offset / range, RAIL_WIDTH, thumb);
	}

	/**
	 * A part shown through a window no taller than {@code window}: the mouse wheel moves it a step at a
	 * time beside a rail, and only what shows can be hovered or pressed. A part that fits paints as it is.
	 */
	static Part scroll(Scroll scroll, Part inner)
	{
		return part(c -> inner.width(c) + (scroll.scrolls(inner.height(c)) ? RAIL_GAP + RAIL_WIDTH : 0),
			c -> scroll.scrolls(inner.height(c)) ? scroll.window : inner.height(c), (c, y) ->
			{
				if (!scroll.scrolls(inner.height(c)))
				{
					scroll.range = 0;
					inner.paint(c, y);
					return;
				}
				Ctx view = new Ctx(c.card, c.fm, c.bfm, c.available - RAIL_GAP - RAIL_WIDTH, c.g, c.w - RAIL_GAP - RAIL_WIDTH);
				int height = inner.height(view);
				scroll.range = Math.max(0, height - scroll.window);
				scroll.offset = Math.min(scroll.offset, scroll.range);
				Shape clip = c.g.getClip();
				c.g.clipRect(0, y, c.w, scroll.window);
				inner.paint(view, y - scroll.offset);
				c.g.setClip(clip);
				Rectangle window = new Rectangle(0, y, c.w, scroll.window);
				for (TooltipItemHover.HitBox hit : view.hits)
				{
					TooltipItemHover.HitBox shown = hit.within(window);
					if (shown != null)
					{
						c.hits.add(shown);
					}
				}
				rail(c, y, scroll.window, scroll.window, height, scroll.offset, scroll.range);
			});
	}

	/** Where a scrolled part's window sits; the card's mouse wheel moves it. */
	static final class Scroll
	{
		private final TitleTooltip card;
		private final int window;
		private final int step;
		private int offset;
		private int range;
		private double wheel;
		@Nullable
		private Scroll partner;

		Scroll(TitleTooltip card, int window, int step)
		{
			this.card = card;
			this.window = window;
			this.step = step;
			card.addMouseWheelListener(this::wheel);
		}

		/** Keeps two players' sides at the same place as either one scrolls. */
		void scrollWith(Scroll other)
		{
			partner = other;
			other.partner = this;
		}

		int offset()
		{
			return offset;
		}

		/** Only a part that would move at least a notch scrolls; one that overflows by less shows whole. */
		boolean scrolls(int height)
		{
			return height > window + step;
		}

		/** For a part that draws its own window, like a list: how far it can move; returns where it is. */
		int follow(int range)
		{
			this.range = range;
			offset = Math.min(offset, range);
			return offset;
		}

		void wheel(MouseWheelEvent e)
		{
			if (range <= 0)
			{
				return;
			}
			wheel += e.getPreciseWheelRotation();
			int notches = (int) wheel;
			wheel -= notches;
			moveTo(offset + notches * step);
			if (partner != null)
			{
				partner.moveTo(offset);
			}
			e.consume();
		}

		private void moveTo(int next)
		{
			next = Math.max(0, Math.min(range, next));
			if (next != offset)
			{
				offset = next;
				card.itemHover.rehoverAfterPaint();
				card.repaint();
			}
		}
	}

	// Full-size item sprites a gap apart; a scrolled grid's window holds whole rows of them.
	static final int GRID_SPRITE = 32;
	static final int GRID_GAP = 4;
	static final int GRID_CELL = GRID_SPRITE + GRID_GAP;
	static final int GRID_WINDOW = WINDOW / GRID_CELL * GRID_CELL - GRID_GAP;

	/**
	 * Full-size item sprites centered in at least {@code minCols} columns, or as many more as the card's
	 * width already holds: unobtained ones dimmed, quantities in the corner, each hover-naming and wiki-linking.
	 */
	static Part grid(int minCols, @Nullable TooltipItemSprites sprites, @Nullable Map<Integer, String> names,
		List<Integer> ids, Set<Integer> obtained, Map<Integer, Integer> counts)
	{
		int count = ids.size();
		return part(c -> gridColumns(minCols, count, c.available) * GRID_CELL - GRID_GAP,
			c -> (count + gridColumns(minCols, count, c.available) - 1) / gridColumns(minCols, count, c.available)
				* GRID_CELL - GRID_GAP, (c, y) ->
			{
				int cols = gridColumns(minCols, count, c.available);
				int width = cols * GRID_CELL - GRID_GAP;
				c.hits.addAll(TooltipItemSprites.paintGrid(c.g, sprites, names, 0, ids, obtained, counts,
					c.inset() + (c.w - 2 * c.inset() - width) / 2, y, cols, GRID_SPRITE, GRID_CELL));
			});
	}

	/** A grid's own columns: the minimum asked for, no fewer than four even for a shorter list. */
	static int gridColumns(int minCols, int count)
	{
		return Math.min(Math.max(minCols, 1), Math.max(4, Math.max(count, 1)));
	}

	/** The grid's own columns, or more when a long title already pays for the width. */
	private static int gridColumns(int minCols, int count, int available)
	{
		int cols = gridColumns(minCols, count);
		int fit = (available + GRID_GAP) / GRID_CELL;
		return available > 0 && fit > cols ? Math.min(fit, count) : cols;
	}

	/**
	 * A table of icon, label, a right-aligned score column, then a progress column and a rank column,
	 * each as wide as its widest entry. Progress and rank show only where the caller gives them.
	 */
	static Part table(BufferedImage[] icons, String[] labels, int[] scores, int[] obtained, int[] total,
		int[] ranks, int iconSize, int iconGap, int colGap)
	{
		ToIntFunction<FontMetrics> scoreRight = fm ->
		{
			int labelCol = 0;
			for (String label : labels)
			{
				labelCol = Math.max(labelCol, fm.stringWidth(label));
			}
			return iconSize + iconGap + labelCol + colGap + TitleTooltip.widestValue(fm, scores, TitleTooltip::scoreText);
		};
		ToIntFunction<FontMetrics> progressCol = fm ->
		{
			int width = 0;
			for (int i = 0; i < labels.length; i++)
			{
				if (obtained[i] >= 0)
				{
					width = Math.max(width, TitleTooltip.wrappedProgressCountWidth(fm, obtained[i], total[i]));
				}
			}
			return width;
		};
		ToIntFunction<FontMetrics> rankCol = fm ->
		{
			int width = 0;
			for (int i = 0; ranks != null && i < labels.length; i++)
			{
				if (ranks[i] > 0)
				{
					width = Math.max(width, 1 + fm.stringWidth(TitleTooltip.rankTailText(ranks[i])));
				}
			}
			return width;
		};
		return part(c -> scoreRight.applyAsInt(c.fm) + progressCol.applyAsInt(c.fm) + rankCol.applyAsInt(c.fm),
			c -> NativeTooltip.LINE_HEIGHT * labels.length, (c, y) ->
			{
				Graphics2D g = c.g;
				FontMetrics fm = c.fm;
				int x = c.inset();
				int right = x + scoreRight.applyAsInt(fm);
				int rankX = right + progressCol.applyAsInt(fm);
				for (int i = 0; i < labels.length; i++)
				{
					int textY = y + fm.getAscent();
					BufferedImage icon = icons != null && i < icons.length ? icons[i] : null;
					if (icon != null)
					{
						g.drawImage(icon, x, y + (NativeTooltip.LINE_HEIGHT - iconSize) / 2, null);
					}
					g.setColor(NativeTooltip.OSRS_ORANGE);
					g.drawString(labels[i], x + iconSize + iconGap, textY);
					g.setColor(Color.WHITE);
					TitleTooltip.drawRightAligned(g, fm, TitleTooltip.scoreText(scores[i]), right, textY);
					if (obtained[i] >= 0)
					{
						TitleTooltip.paintWrappedProgressCount(g, fm, right, textY, obtained[i], total[i]);
					}
					if (ranks != null && ranks[i] > 0)
					{
						TitleTooltip.drawLabelValue(g, fm, rankX + 1, textY, " #", TitleTooltip.grouped(ranks[i]));
					}
					y += NativeTooltip.LINE_HEIGHT;
				}
			});
	}

	/**
	 * Rows a card opens things from. Each paint lays the rows' spans down again; a hovered row repaints
	 * white, and a left press hands its key to the card.
	 */
	static final class ClickRows
	{
		private final JComponent card;
		private final List<int[]> spans = new ArrayList<>();
		private int hovered = -1;
		private int lastY = -1;

		ClickRows(JComponent card, ObjIntConsumer<MouseEvent> onPress)
		{
			this.card = card;
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					lastY = e.getY();
					hover(at(lastY));
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					int key = at(e.getY());
					if (key >= 0 && e.getButton() == MouseEvent.BUTTON1)
					{
						onPress.accept(e, key);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					lastY = -1;
					hover(-1);
				}
			};
			card.addMouseMotionListener(mouse);
			card.addMouseListener(mouse);
		}

		void span(int top, int key)
		{
			spans.removeIf(s -> s[1] == key);
			spans.add(new int[]{top, key});
		}

		/** Forgets every row: a scrolled list lays down only the rows it shows. */
		void clear()
		{
			spans.clear();
		}

		/** Hovers whatever row now sits under the still mouse, as after a list scrolls beneath it. */
		void rehover()
		{
			hover(at(lastY));
		}

		/** The row under a y coordinate, or -1. Rows are full width. */
		int at(int y)
		{
			for (int[] s : spans)
			{
				if (y >= s[0] && y < s[0] + NativeTooltip.LINE_HEIGHT)
				{
					return s[1];
				}
			}
			return -1;
		}

		int hovered()
		{
			return hovered;
		}

		private void hover(int key)
		{
			if (hovered != key)
			{
				hovered = key;
				card.repaint();
			}
		}
	}
}
