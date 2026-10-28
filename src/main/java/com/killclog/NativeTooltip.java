package com.killclog;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.swing.JToolTip;

/**
 * Base tooltip styled to match native OSRS interface elements.
 * Provides parchment background fill, 9-slice iron border from game sprites,
 * and AA-hinted Graphics2D setup. Subclasses implement content via
 * {@link #paintContent(Graphics2D, int, int)}.
 */
public abstract class NativeTooltip extends JToolTip
{
	static final int MARGIN = 8;
	static final int LINE_HEIGHT = 14;
	static final Color OSRS_ORANGE = new Color(255, 152, 31);
	static final Color DIVIDER = new Color(80, 70, 50);
	// Every card's labels, values and dividers, as Card Appearance's colors set them (Dylan, 2026-10-17); the
	// defaults are the game's.
	static Color labelColor = OSRS_ORANGE;
	static Color valueColor = Color.WHITE;
	static Color dividerColor = DIVIDER;
	// Card Appearance's background and border (2.7.3): one flat color each in place of the parchment or the iron
	// rivets, else null, the sprites (a resource pack's when one replaces them).
	@Nullable
	private static Color flatBackground;
	@Nullable
	private static Color flatBorder;
	// Card Appearance's scroll bar (2.7.3): the thumb's one shade, else null, the pack's thumb or the game's orange.
	@Nullable
	private static Color flatScroll;
	static final Color NOTICE_COLOR = new Color(160, 160, 160);

	// Programmatic fallback border.
	private static final int BORDER_THICKNESS = 3;
	private static final Color BORDER_OUTER = new Color(26, 26, 26);
	static final Color BORDER_MID = new Color(61, 51, 34);
	private static final Color BORDER_INNER = new Color(84, 72, 53);
	static final Color FALLBACK_BG = new Color(60, 50, 35);

	/**
	 * Card Appearance's label, value, divider, background and border colors; one left unset is the game's. A
	 * background or border at its default keeps the sprites, so a resource pack still dresses the cards.
	 */
	static void cardColors(Color label, Color value, Color divider, Color background, Color border, Color scroll)
	{
		flatScroll = scroll == null || scroll.equals(OSRS_ORANGE) ? null : scroll;
		labelColor = label != null ? label : OSRS_ORANGE;
		valueColor = value != null ? value : Color.WHITE;
		dividerColor = divider != null ? divider : DIVIDER;
		flatBackground = background == null || background.equals(FALLBACK_BG) ? null : background;
		flatBorder = border == null || border.equals(BORDER_MID) ? null : border;
	}

	/** The rail's thumb: Card Appearance's scroll bar, else the pack's, else the card's orange. */
	static Color railThumb()
	{
		Color thumb = flatScroll != null ? flatScroll : CardSprites.packThumb;
		return thumb != null ? thumb : OSRS_ORANGE;
	}

	/**
	 * The rail's track: the pack's when its thumb is the pack's, else a darker shade of the thumb, so the two never
	 * meet in one colour whatever the dividers are (Dylan 10/24: orange dividers put an orange track under the orange
	 * thumb).
	 */
	static Color railTrackFor(Color thumb)
	{
		Color track = CardSprites.packTrack;
		if (flatScroll == null && CardSprites.packThumb != null && track != null)
		{
			return track;
		}
		// A thumb too dark to shade darker gets a lighter track instead.
		int lift = thumb.getRed() + thumb.getGreen() + thumb.getBlue() < 90 ? 60 : 0;
		return new Color(Math.min(255, thumb.getRed() * 3 / 10 + lift), Math.min(255, thumb.getGreen() * 3 / 10 + lift),
			Math.min(255, thumb.getBlue() * 3 / 10 + lift));
	}

	/** A track under a rail or a bar: the pack's scrollbar track, else the card's own. */
	static Color railTrack(Color fallback)
	{
		Color track = CardSprites.packTrack;
		return track != null ? track : fallback;
	}

	/** Content inset: border thickness + margin. */
	public static int getInset()
	{
		return BORDER_THICKNESS + MARGIN;
	}

	/**
	 * Hide the whole tooltip a component belongs to. A comparison card is a
	 * child tooltip inside a side-by-side composite; hiding only itself would
	 * leave the composite on screen with an empty half.
	 */
	public static void hideTooltipTree(java.awt.Component component)
	{
		tooltipRoot(component).setVisible(false);
	}

	/** The outermost tooltip holding this component (pinned cards nest side by side). */
	static java.awt.Component tooltipRoot(java.awt.Component component)
	{
		java.awt.Component root = component;
		for (java.awt.Component parent = component; parent != null; parent = parent.getParent())
		{
			if (parent instanceof JToolTip)
			{
				root = parent;
			}
		}
		return root;
	}

	protected NativeTooltip()
	{
		setOpaque(false);
		setBorder(null);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		int w = getWidth();
		int h = getHeight();

		paintBackground(g2, w, h);
		paintBorder(g2, w, h);

		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		paintContent(g2, w, h);

		g2.dispose();
	}

	/**
	 * Subclasses paint their content here.
	 * Background, border, and AA hints are already applied.
	 */
	protected abstract void paintContent(Graphics2D g2, int w, int h);

	private static void paintBackground(Graphics2D g2, int w, int h)
	{
		if (flatBackground != null)
		{
			g2.setColor(flatBackground);
			g2.fillRect(0, 0, w, h);
		}
		else if (CardSprites.parchmentBg != null)
		{
			int tw = CardSprites.parchmentBg.getWidth();
			int th = CardSprites.parchmentBg.getHeight();
			for (int y = 0; y < h; y += th)
			{
				for (int x = 0; x < w; x += tw)
				{
					g2.drawImage(CardSprites.parchmentBg, x, y, null);
				}
			}
		}
		else
		{
			g2.setColor(FALLBACK_BG);
			g2.fillRect(0, 0, w, h);
		}
	}

	private static void paintBorder(Graphics2D g2, int w, int h)
	{
		// A flat border keeps the frame's three pixels, so nothing inside the card moves: a dark outer line, then
		// the color.
		if (!CardSprites.spritesLoaded || flatBorder != null)
		{
			g2.setColor(BORDER_OUTER);
			g2.drawRect(0, 0, w - 1, h - 1);
			g2.setColor(flatBorder != null ? flatBorder : BORDER_MID);
			g2.drawRect(1, 1, w - 3, h - 3);
			g2.setColor(flatBorder != null ? flatBorder : BORDER_INNER);
			g2.drawRect(2, 2, w - 5, h - 5);
			return;
		}

		int cw = CardSprites.cornerTL.getWidth();
		int ch = CardSprites.cornerTL.getHeight();

		// Corners
		g2.drawImage(CardSprites.cornerTL, 0, 0, null);
		g2.drawImage(CardSprites.cornerTR, w - CardSprites.cornerTR.getWidth(), 0, null);
		g2.drawImage(CardSprites.cornerBL, 0, h - CardSprites.cornerBL.getHeight(), null);
		g2.drawImage(CardSprites.cornerBR, w - CardSprites.cornerBR.getWidth(), h - CardSprites.cornerBR.getHeight(), null);

		// Edges between the corners: top, bottom, left, right.
		tileEdge(g2, CardSprites.edgeTop, 0, 0, cw, w - cw, true);
		tileEdge(g2, CardSprites.edgeBottom, 0, h - CardSprites.edgeBottom.getHeight(), cw, w - cw, true);
		tileEdge(g2, CardSprites.edgeLeft, 0, 0, ch, h - ch, false);
		tileEdge(g2, CardSprites.edgeRight, w - CardSprites.edgeRight.getWidth(), 0, ch, h - ch, false);
	}

	/** Tile an edge piece along one side from one corner to the other, cropping the last tile. */
	private static void tileEdge(Graphics2D g2, BufferedImage edge, int x, int y, int from, int to, boolean across)
	{
		int ew = edge.getWidth();
		int eh = edge.getHeight();
		for (int at = from; at < to; at += across ? ew : eh)
		{
			int len = Math.min(across ? ew : eh, to - at);
			if (across)
			{
				g2.drawImage(edge, at, y, at + len, y + eh, 0, 0, len, eh, null);
			}
			else
			{
				g2.drawImage(edge, x, at, x + ew, at + len, 0, 0, ew, len, null);
			}
		}
	}
}
