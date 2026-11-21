package com.killclog;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.swing.JToolTip;
import net.runelite.client.util.ColorUtil;

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
	// Every card's labels, values and dividers, as Card Settings's colors set them; the
	// defaults are the game's.
	static Color labelColor = OSRS_ORANGE;
	static Color valueColor = Color.WHITE;
	static Color dividerColor = DIVIDER;
	// Card Settings's background and border: one flat color each in place of the parchment or the iron
	// rivets, else null, the sprites (a resource pack's when one replaces them).
	@Nullable
	private static Color flatBackground;
	@Nullable
	private static Color flatBorder;
	// Card Settings's scroll bar: the thumb's one shade, else null, the pack's thumb or the game's orange.
	@Nullable
	private static Color flatScroll;
	static final Color NOTICE_COLOR = new Color(160, 160, 160);

	// Programmatic fallback border.
	private static final int BORDER_THICKNESS = 3;
	private static final Color BORDER_OUTER = new Color(26, 26, 26);
	static final Color BORDER_MID = new Color(61, 51, 34);
	private static final Color TILE_STONE = new Color(73, 63, 52);
	private static final Color TILE_STONE_LIT = new Color(86, 75, 61);
	private static final Color BORDER_INNER = new Color(84, 72, 53);
	static final Color FALLBACK_BG = new Color(60, 50, 35);

	/**
	 * Card Settings's label, value, divider, background and border colors; one left unset is the game's. A
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

	/**
	 * A tile's fill: the game's stone, lighter under the pointer; with a pack's or Card Settings's colors, the card's
	 * background with its border mixed in, 40% at rest and 75% under the pointer, as the site tints its tiles
	 *.
	 */
	static Color tileFill(boolean hovered)
	{
		Color ground = flatBackground != null ? flatBackground : CardSprites.packBackground;
		Color rim = flatBorder != null ? flatBorder : CardSprites.packBorder;
		if (ground == null && rim == null)
		{
			return hovered ? TILE_STONE_LIT : TILE_STONE;
		}
		return ColorUtil.colorLerp(ground != null ? ground : FALLBACK_BG, rim != null ? rim : BORDER_MID,
			hovered ? 0.75 : 0.4);
	}

	/** The rail's thumb: Card Settings's scroll bar, else the pack's, else the card's orange. */
	static Color railThumb()
	{
		Color thumb = flatScroll != null ? flatScroll : CardSprites.packThumb;
		return thumb != null ? thumb : OSRS_ORANGE;
	}

	/**
	 * The rail's track: the pack's when its thumb is the pack's, else a darker shade of the thumb, so the two never
	 * meet in one colour whatever the dividers are.
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
		if (flatBackground == null && CardSprites.parchmentBg != null)
		{
			tile(g2, CardSprites.parchmentBg, 0, 0, w, h);
			return;
		}
		g2.setColor(flatBackground != null ? flatBackground : FALLBACK_BG);
		g2.fillRect(0, 0, w, h);
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
		BufferedImage bottom = CardSprites.edgeBottom;
		BufferedImage right = CardSprites.edgeRight;
		tile(g2, CardSprites.edgeTop, cw, 0, w - 2 * cw, CardSprites.edgeTop.getHeight());
		tile(g2, bottom, cw, h - bottom.getHeight(), w - 2 * cw, bottom.getHeight());
		tile(g2, CardSprites.edgeLeft, 0, ch, CardSprites.edgeLeft.getWidth(), h - 2 * ch);
		tile(g2, right, w - right.getWidth(), ch, right.getWidth(), h - 2 * ch);
	}

	/** An image repeated over a box from its top left, the last row and column cropped; the paint is put back. */
	private static void tile(Graphics2D g2, BufferedImage image, int x, int y, int w, int h)
	{
		Paint paint = g2.getPaint();
		g2.setPaint(new TexturePaint(image, new Rectangle(x, y, image.getWidth(), image.getHeight())));
		g2.fillRect(x, y, w, h);
		g2.setPaint(paint);
	}
}
