package com.killclog;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import lombok.*;
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
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Ctx
	{
		final TitleTooltip card;
		final FontMetrics fm;
		final FontMetrics bfm;
		final int available;
		final Graphics2D g;
		final int w;
		final List<TooltipItemHover.HitBox> hits = new ArrayList<>();
		// What shows of a scrolled part: a row moved out of it can't be pressed.
		@Nullable
		Rectangle window;

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

	/** Each item id's value, as the grids' painter reads names and counts. */
	static <T> Map<Integer, T> byId(int[] ids, IntFunction<T> value)
	{
		Map<Integer, T> out = new HashMap<>();
		for (int i = 0; i < ids.length; i++)
		{
			out.put(ids[i], value.apply(i));
		}
		return out;
	}

	/** The body's parts as one part, as a card scrolls everything under its anchored band. */
	Part asPart()
	{
		return part(c ->
		{
			int width = 0;
			for (Part part : parts)
			{
				width = Math.max(width, part.width(c));
			}
			return width;
		}, c ->
		{
			int height = 0;
			for (Part part : parts)
			{
				height += part.height(c);
			}
			return height;
		}, (c, y) ->
		{
			int top = y;
			for (Part part : parts)
			{
				part.paint(c, top);
				top += part.height(c);
			}
		});
	}

	/** A titled section under the parts before it: room above, not a divider, since its title carries a rule. */
	CardBody titled(String title)
	{
		return add(gap(SECTION_SPACE)).add(section(title));
	}

	/** The body's size: as wide as its widest part, as tall as its parts together. */
	Dimension size(TitleTooltip card, int available)
	{
		Ctx c = new Ctx(card, card.getFontMetrics(FontManager.getRunescapeSmallFont()),
			card.getFontMetrics(FontManager.getRunescapeBoldFont()), available, null, 0);
		Part all = asPart();
		return new Dimension(all.width(c), all.height(c));
	}

	/** Paints every part down from startY in the small font; returns the item hit boxes they laid down. */
	List<TooltipItemHover.HitBox> paint(TitleTooltip card, Graphics2D g, int w, int startY)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		Ctx c = new Ctx(card, g.getFontMetrics(), g.getFontMetrics(FontManager.getRunescapeBoldFont()),
			w - 2 * NativeTooltip.getInset(), g, w);
		asPart().paint(c, startY);
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
		return line(label, value, NativeTooltip.valueColor);
	}

	// A bar's height and the room above it; its track is the card's ground darkened, whatever its color.
	static final int BAR_HEIGHT = 3;
	private static final int BAR_ABOVE = 2;
	private static final Color BAR_TRACK = new Color(0, 0, 0, 140);

	/**
	 * A thin bar across the card under the line it pictures, filled to {@code share} (0 to 1) over a dark track: a
	 * skill's way to its next level, Experience &amp; Efficiency's way to max. It never widens the card.
	 */
	static Part bar(double share, Color fill)
	{
		return row(BAR_ABOVE + BAR_HEIGHT + 1, c -> 0, (c, y) ->
		{
			int width = c.w - 2 * c.inset();
			c.g.setColor(BAR_TRACK);
			c.g.fillRect(c.inset(), y + BAR_ABOVE, width, BAR_HEIGHT);
			c.g.setColor(fill);
			c.g.fillRect(c.inset(), y + BAR_ABOVE, (int) Math.round(width * Math.max(0, Math.min(1, share))), BAR_HEIGHT);
		});
	}

	static Part text(String text, Color color)
	{
		return row(NativeTooltip.LINE_HEIGHT, c -> c.fm.stringWidth(text), (c, y) ->
		{
			c.g.setColor(color);
			c.g.drawString(text, c.inset(), y + c.fm.getAscent());
		});
	}

	// A section's title owns a full-width rule just under it, then room before its rows; sections stand apart by
	// space alone. Every card titles its sections this way, and a skill log titles its headings the same.
	static final int SECTION_SPACE = 8;
	static final int SECTION_RULE = 3;
	static final int SECTION_AFTER = 4;
	private static final int SECTION_TITLE = 16;

	/** A section's bold title with its rule under it, the small font handed back after. */
	static Part section(String title)
	{
		return row(SECTION_TITLE + SECTION_RULE + SECTION_AFTER, c -> c.bfm.stringWidth(title), (c, y) ->
		{
			c.g.setFont(FontManager.getRunescapeBoldFont());
			c.g.setColor(NativeTooltip.labelColor);
			c.g.drawString(title, c.inset(), y + c.bfm.getAscent());
			c.g.setFont(FontManager.getRunescapeSmallFont());
			divider(c.g, y + SECTION_TITLE + 1, c.inset(), c.w);
		});
	}

	/** A divider across the card between its insets: under a section's title, a band's edges, a separator. */
	static void divider(Graphics2D g, int y, int inset, int w)
	{
		g.setColor(NativeTooltip.dividerColor);
		g.drawLine(inset, y, w - inset - 1, y);
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
		return part(c -> 0, c -> above + 1 + bandLine(c) + 1 + BAND_PAD, (c, y) ->
		{
			int top = y + above;
			if (above >= 0)
			{
				divider(c.g, top, c.inset(), c.w);
			}
			divider(c.g, top + bandLine(c) + 1, c.inset(), c.w);
			String name = c.card.getHeaderHoverLineText();
			if (name != null && !name.isEmpty())
			{
				c.g.setColor(c.card.getHeaderHoverLineColor());
				c.g.drawString(TitleTooltip.fitHeaderText(c.fm, name, c.w - 2 * c.inset()), c.inset(),
					top + 3 + c.fm.getAscent());
			}
		});
	}

	/** The band's name line: the name with room above and below it. */
	private static int bandLine(Ctx c)
	{
		return c.fm.getHeight() + 4;
	}

	/**
	 * Full-size item sprites from the left edge in rows of at most {@code perRow}: unobtained ones dimmed, quantities in
	 * the corner, each hover-naming and wiki-linking like the grids do. Captions (a shelf's dates, one row) sit under
	 * their sprites in muted grey, every cell as wide as the widest, its sprite centered over it.
	 */
	static Part sprites(int section, @Nullable TooltipItemSprites sprites, int[] ids, String[] names, int[] counts,
		int pad, int perRow, @Nullable String[] captions)
	{
		Map<Integer, String> named = byId(ids, i -> names[i]);
		Map<Integer, Integer> held = byId(ids, i -> counts[i]);
		Set<Integer> obtained = new HashSet<>();
		for (int i = 0; i < ids.length; i++)
		{
			if (counts[i] > 0)
			{
				obtained.add(ids[i]);
			}
		}
		int cols = Math.min(ids.length, perRow);
		int rows = (ids.length + cols - 1) / cols;
		List<String> shown = new ArrayList<>(captions != null ? Arrays.asList(captions) : Collections.emptyList());
		shown.removeIf(Objects::isNull);
		ToIntFunction<Ctx> cell = c -> shown.stream().mapToInt(c.fm::stringWidth).reduce(GRID_SPRITE, Math::max);
		return part(c -> cols * (cell.applyAsInt(c) + pad) - pad,
			c -> rows * (GRID_SPRITE + pad) - pad + (shown.isEmpty() ? 0 : CAPTION_GAP + c.fm.getHeight()), (c, y) ->
			{
				int w = cell.applyAsInt(c);
				c.hits.addAll(TooltipItemSprites.paintGrid(c.g, sprites, named, section, TooltipData.itemList(ids), obtained,
					held, c.inset() + (w - GRID_SPRITE) / 2, y, cols, GRID_SPRITE, w + pad, null));
				for (int i = 0; !shown.isEmpty() && i < captions.length; i++)
				{
					if (captions[i] != null)
					{
						c.g.setColor(TitleTooltip.MUTED_GRAY);
						c.g.drawString(captions[i], c.inset() + i * (w + pad) + (w - c.fm.stringWidth(captions[i])) / 2,
							y + GRID_SPRITE + CAPTION_GAP + c.fm.getAscent());
					}
				}
			});
	}

	/** One row that opens something: it answers hover in white and a left press with its key. */
	static Part clickRow(ClickRows rows, int key, ToIntFunction<Ctx> width, RowPainter painter)
	{
		return row(NativeTooltip.LINE_HEIGHT, width, (c, y) ->
		{
			painter.paint(c, y, rows.spanShown(c, y, NativeTooltip.LINE_HEIGHT, key));
		});
	}

	static final int RAIL_WIDTH = 3;
	static final int RAIL_GAP = 4;
	// Every scrolled card shows the same height: 24 lines, the length of a long Collection Log tab.
	static final int WINDOW = 24 * NativeTooltip.LINE_HEIGHT;

	/**
	 * The thin rail beside a scrolled window: its track, and the thumb where the window sits, brighter while the
	 * pointer is on it. The scroll keeps where it drew them, for a press or a drag on the rail.
	 */
	static void rail(Ctx c, Scroll scroll, int y, int track, int shown, int total, int offset, int range)
	{
		int x = c.w - c.inset() - RAIL_WIDTH;
		int thumb = Math.max(NativeTooltip.LINE_HEIGHT, track * shown / total);
		int thumbY = y + (track - thumb) * offset / range;
		scroll.rail = new Rectangle(x, y, RAIL_WIDTH, track);
		scroll.thumb = new Rectangle(x, thumbY, RAIL_WIDTH, thumb);
		Color color = NativeTooltip.railThumb();
		c.g.setColor(NativeTooltip.railTrackFor(color));
		c.g.fillRect(x, y, RAIL_WIDTH, track);
		c.g.setColor(scroll.hot ? color.brighter() : color);
		c.g.fillRect(x, thumbY, RAIL_WIDTH, thumb);
	}

	/** Whether a press at this point of a card, in its own pixels, takes its scroll's rail. */
	static boolean onRail(Component card, Point p)
	{
		Scroll scroll = card instanceof TitleTooltip ? ((TitleTooltip) card).scroll() : null;
		return scroll != null && scroll.grabs(p);
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
				Rectangle window = new Rectangle(0, y, c.w, scroll.window);
				view.window = window;
				int height = inner.height(view);
				scroll.range = Math.max(0, height - scroll.window);
				scroll.offset = Math.min(scroll.offset, scroll.range);
				Shape clip = c.g.getClip();
				c.g.clipRect(0, y, c.w, scroll.window);
				inner.paint(view, y - scroll.offset);
				c.g.setClip(clip);
				for (TooltipItemHover.HitBox hit : view.hits)
				{
					TooltipItemHover.HitBox shown = hit.within(window);
					if (shown != null)
					{
						c.hits.add(shown);
					}
				}
				rail(c, scroll, y, scroll.window, scroll.window, height, scroll.offset, scroll.range);
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
		// Where the rail and its thumb last drew, in the card's pixels; a press on either drags the window, for a
		// player without a scroll wheel.
		@Nullable
		private Rectangle rail;
		@Nullable
		private Rectangle thumb;
		private boolean hot;
		// While dragging: how far below the thumb's top the pointer holds it, else -1.
		private int held = -1;
		// While a copy draws the card: every row shows, with no window and no rail.
		private boolean whole;

		Scroll(TitleTooltip card, int window, int step)
		{
			this.card = card;
			this.window = window;
			this.step = step;
			card.addMouseWheelListener(this::wheel);
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.getButton() != MouseEvent.BUTTON1 || !grabs(e.getPoint()))
					{
						// A drag whose release never reached the card (it closed mid-drag) ends here.
						held = -1;
						return;
					}
					// On the thumb it holds where it was taken; on the track the thumb's middle jumps there.
					held = thumb.contains(thumb.x, e.getY()) ? e.getY() - thumb.y : thumb.height / 2;
					drag(e.getY());
					e.consume();
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					if (held >= 0)
					{
						drag(e.getY());
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					held = -1;
					hover(grabs(e.getPoint()));
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					hover(grabs(e.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover(held >= 0);
				}
			};
			card.addMouseListener(mouse);
			card.addMouseMotionListener(mouse);
		}

		/**
		 * Whether a press here takes the rail: the rail, its gap and 4 px of the inset past it, 11 px against the 3 it
		 * draws, and never into the part beside it (a grid's last column ends at the gap).
		 */
		boolean grabs(Point p)
		{
			return range > 0 && rail != null && p.y >= rail.y && p.y < rail.y + rail.height
				&& p.x >= rail.x - RAIL_GAP && p.x < rail.x + RAIL_WIDTH + 4;
		}

		private void drag(int y)
		{
			int travel = rail.height - thumb.height;
			int next = travel <= 0 ? 0 : (int) Math.round((double) (y - held - rail.y) * range / travel);
			moveTo(next);
			if (partner != null)
			{
				partner.moveTo(offset);
			}
		}

		private void hover(boolean on)
		{
			if (on != hot)
			{
				hot = on;
				card.repaint();
			}
		}

		/** Keeps two players' sides at the same place as either one scrolls. */
		void scrollWith(Scroll other)
		{
			partner = other;
			other.partner = this;
		}

		/** Only a part that would move at least a notch scrolls; one that overflows by less shows whole. */
		boolean scrolls(int height)
		{
			return !whole && height > window + step;
		}

		/**
		 * Draws the card with every row showing, however tall, the way a copy holds it, so a copy is never cut off at
		 * the window; then puts the window back where it sat.
		 */
		<T> T unrolled(Supplier<T> draw)
		{
			int keptOffset = offset;
			int keptRange = range;
			whole = true;
			try
			{
				return draw.get();
			}
			finally
			{
				whole = false;
				offset = keptOffset;
				range = keptRange;
			}
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
	// The room between item sprites laid out in a row, and over such a row: a shelf's, the gear's, the superiors'.
	static final int SPRITE_PAD = 6;
	// An item grid's columns before a wider card gives it more: pets, a skill's sections, a log page.
	static final int MIN_COLS = 5;
	private static final int CAPTION_GAP = 1;
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
					c.inset() + (c.w - 2 * c.inset() - width) / 2, y, cols, GRID_SPRITE, GRID_CELL, null));
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
	 * Rows and tiles a card opens things from. Each paint lays their boxes down again (a row's runs the card's
	 * width); a hovered one repaints, and a left press hands its key to the card. A card's tiles each bring what a
	 * press does in place of a key ({@link #opens}), so every paint starts them over.
	 */
	static final class ClickRows
	{
		private final JComponent card;
		private final Map<Integer, Rectangle> boxes = new LinkedHashMap<>();
		private final List<Consumer<MouseEvent>> actions = new ArrayList<>();
		private int hovered = -1;
		private Point pointer;
		// Whether shift was down at the pointer's last move, for a link that opens on a shift-click only.
		boolean shift;

		ClickRows(JComponent card)
		{
			this(card, null);
		}

		ClickRows(JComponent card, @Nullable ObjIntConsumer<MouseEvent> onPress)
		{
			this.card = card;
			ObjIntConsumer<MouseEvent> press = onPress != null ? onPress : (e, key) -> actions.get(key).accept(e);
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					pointer = e.getPoint();
					int key = at(pointer);
					// Shift pressed or let go over a row repaints it: a link that opens on a shift-click only lights with it.
					if (shift != e.isShiftDown() && key >= 0)
					{
						hovered = -1;
					}
					shift = e.isShiftDown();
					hover(key);
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					int key = at(e.getPoint());
					if (key >= 0 && e.getButton() == MouseEvent.BUTTON1 && !onRail(card, e.getPoint()))
					{
						press.accept(e, key);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					pointer = null;
					hover(-1);
				}
			};
			card.addMouseMotionListener(mouse);
			card.addMouseListener(mouse);
		}

		/**
		 * Lays down only what shows of a row, so one scrolled out of view can't be pressed; whether the still
		 * mouse is on it.
		 */
		boolean spanShown(Ctx c, int y, int height, int key)
		{
			return boxShown(c, new Rectangle(0, y, Integer.MAX_VALUE, height), key);
		}

		/** Lays down a tile with what a press on it does; whether the still mouse is on it. */
		boolean opens(Ctx c, Rectangle box, Consumer<MouseEvent> action)
		{
			actions.add(action);
			return boxShown(c, box, actions.size() - 1);
		}

		/** Before a paint lays the tiles down again. */
		void restart()
		{
			actions.clear();
			boxes.clear();
		}

		/** A tile's box, clipped to what shows of a scrolled part as a row is; whether the still mouse is on it. */
		boolean boxShown(Ctx c, Rectangle box, int key)
		{
			return lit(c.window != null ? box.intersection(c.window) : box, key);
		}

		/** Lays down a box that never scrolls, as the header's are; whether the still mouse is on it. */
		boolean lit(Rectangle shown, int key)
		{
			boxes.put(key, shown);
			// What paints lit is what's hovered: a scroll that slides another tile under the still mouse moves the
			// hover with it, so the next move repaints against what shows.
			boolean on = pointer != null && shown.contains(pointer);
			if (on)
			{
				hovered = key;
			}
			else if (hovered == key)
			{
				hovered = -1;
			}
			return on;
		}

		/** The row or tile under a point, or -1. */
		int at(@Nullable Point point)
		{
			for (Map.Entry<Integer, Rectangle> box : boxes.entrySet())
			{
				if (point != null && box.getValue().contains(point))
				{
					return box.getKey();
				}
			}
			return -1;
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
