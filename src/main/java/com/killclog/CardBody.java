package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
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
		Ctx c = new Ctx(card, g.getFontMetrics(), g.getFontMetrics(FontManager.getRunescapeBoldFont()), 0, g, w);
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
	 * A centered row of item sprites, unobtained ones dimmed and quantities in the corner; each sprite
	 * hover-names and wiki-links like the grids do.
	 */
	static Part sprites(int section, BufferedImage[] sprites, int[] ids, String[] names, int[] counts, int size, int pad)
	{
		int count = Math.min(sprites.length, counts.length);
		return row(size, c -> count * size + (count - 1) * pad, (c, y) ->
		{
			Graphics2D g = c.g;
			int startX = c.inset() + (c.w - 2 * c.inset() - (count * size + (count - 1) * pad)) / 2;
			for (int i = 0; i < count; i++)
			{
				int sx = startX + i * (size + pad);
				boolean obtained = counts[i] > 0;
				c.hits.add(new TooltipItemHover.HitBox(section, ids[i], names[i], new Rectangle(sx, y, size, size), obtained, 1));
				if (sprites[i] == null)
				{
					continue;
				}
				g.setComposite(obtained ? AlphaComposite.SrcOver : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g.drawImage(sprites[i], sx, y, null);
				g.setComposite(AlphaComposite.SrcOver);
				if (counts[i] > 1)
				{
					String quantity = String.valueOf(counts[i]);
					g.setColor(Color.BLACK);
					g.drawString(quantity, sx + 1, y + c.fm.getAscent() + 1);
					g.setColor(TitleTooltip.CLOG_YELLOW);
					g.drawString(quantity, sx, y + c.fm.getAscent());
				}
			}
		});
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

		ClickRows(JComponent card, ObjIntConsumer<MouseEvent> onPress)
		{
			this.card = card;
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					hover(at(e.getY()));
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

		/** Hovers whatever row now sits under y, as when a list scrolls beneath a still mouse. */
		void moved(int y)
		{
			hover(at(y));
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
