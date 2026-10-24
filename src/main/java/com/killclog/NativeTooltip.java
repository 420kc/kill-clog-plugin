package com.killclog;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JToolTip;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

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

	// Sprite IDs for game interface elements.
	private static final int SPRITE_PARCHMENT = 297;
	private static final int SPRITE_CORNER_TL = 310;
	private static final int SPRITE_CORNER_TR = 311;
	private static final int SPRITE_CORNER_BL = 312;
	private static final int SPRITE_CORNER_BR = 313;
	private static final int SPRITE_EDGE_HORIZ = 314;
	private static final int SPRITE_EDGE_VERT = 315;
	private static final int SPRITE_EDGE_BOTTOM = 173;
	private static final int SPRITE_EDGE_LEFT = 172;

	// Tiled parchment background from game (sprite 297 = TRADEBACKING)
	private static volatile BufferedImage parchmentBg;

	// Native 9-slice iron border sprites.
	private static volatile BufferedImage cornerTL, cornerTR, cornerBL, cornerBR;
	private static volatile BufferedImage edgeTop, edgeBottom, edgeLeft, edgeRight;
	private static volatile boolean spritesLoaded;

	// A resource pack's own scrollbar, thumb and track: a scrolled card's rail and the summaries' bars wear its
	// colors. Without a pack the rail keeps the card's orange.
	private static final int SPRITE_SCROLL_THUMB = 790;
	private static final int SPRITE_SCROLL_TRACK = 792;
	@Nullable
	private static volatile Color packThumb;
	@Nullable
	private static volatile Color packTrack;

	// Every sprite a card wears, and the status row's character icon: what a pack or theme replacing one changes.
	private static final int[] WORN = {SPRITE_PARCHMENT, SPRITE_CORNER_TL, SPRITE_CORNER_TR, SPRITE_CORNER_BL,
		SPRITE_CORNER_BR, SPRITE_EDGE_HORIZ, SPRITE_EDGE_VERT, SPRITE_EDGE_BOTTOM, SPRITE_EDGE_LEFT, SPRITE_SCROLL_THUMB,
		SPRITE_SCROLL_TRACK, SpriteID.SideiconsNew.EQUIPMENT};
	// Each worn sprite's replacement at the last look, null for the game's own.
	private static final SpritePixels[] WORN_SEEN = new SpritePixels[WORN.length];

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

	/**
	 * Whether a pack or theme has replaced, swapped or restored a worn sprite since the last look. A pack applies a
	 * moment after it is chosen (a Hub pack downloads first), so the cards ask every client tick rather than when a
	 * setting changes. Client thread.
	 */
	static boolean wornSpritesChanged(Client client)
	{
		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		boolean changed = false;
		for (int i = 0; i < WORN.length; i++)
		{
			SpritePixels now = overrides.get(WORN[i]);
			if (now != WORN_SEEN[i])
			{
				WORN_SEEN[i] = now;
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Load border sprites, preferring client sprite overrides when Resource
	 * Packs or another UI theme plugin has replaced the same game sprites.
	 */
	public static void loadSprites(Client client, SpriteManager spriteManager)
	{
		packThumb = thumbColor(getOverrideSprite(client, SPRITE_SCROLL_THUMB));
		packTrack = bodyColor(getOverrideSprite(client, SPRITE_SCROLL_TRACK));
		loadSprite(client, spriteManager, SPRITE_PARCHMENT, img -> parchmentBg = img);
		loadSprite(client, spriteManager, SPRITE_CORNER_TL, img ->
		{
			cornerTL = img;
			cornerBL = optionalOverride(client, SPRITE_CORNER_BL, flip(img, true));
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_CORNER_TR, img ->
		{
			cornerTR = img;
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_CORNER_BR, img ->
		{
			cornerBR = img;
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_EDGE_HORIZ, img ->
		{
			edgeTop = trimTransparentPadding(img);
			edgeBottom = optionalTrimmedOverride(client, SPRITE_EDGE_BOTTOM, flip(edgeTop, true));
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_EDGE_VERT, img ->
		{
			edgeRight = trimTransparentPadding(img);
			edgeLeft = optionalTrimmedOverride(client, SPRITE_EDGE_LEFT, flip(edgeRight, false));
			checkSprites();
		});
	}

	private static void loadSprite(Client client, SpriteManager spriteManager,
		int spriteId, Consumer<BufferedImage> consumer)
	{
		BufferedImage override = getOverrideSprite(client, spriteId);
		if (override != null)
		{
			consumer.accept(override);
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, consumer);
	}

	private static BufferedImage getOverrideSprite(Client client, int spriteId)
	{
		if (client == null)
		{
			return null;
		}
		SpritePixels spritePixels = client.getSpriteOverrides().get(spriteId);
		return spritePixels != null ? spritePixels.toBufferedImage() : null;
	}

	/** A scrollbar sprite's body: its middling color clear of the bevel at its sides. */
	@Nullable
	static Color bodyColor(@Nullable BufferedImage image)
	{
		return ranked(image, true, 0.5);
	}

	/**
	 * A pack's thumb lit enough to read at the rail's three pixels: the hue of its bevel's light, never dimmer than
	 * three quarters bright. A game thumb sixteen pixels wide reads by its bevel; a rail has none.
	 */
	@Nullable
	static Color thumbColor(@Nullable BufferedImage image)
	{
		Color light = ranked(image, false, 0.9);
		if (light == null)
		{
			return null;
		}
		float[] hsb = Color.RGBtoHSB(light.getRed(), light.getGreen(), light.getBlue(), null);
		return Color.getHSBColor(hsb[0], hsb[1], Math.max(hsb[2], 0.75f));
	}

	/** The opaque color at a rank from dark to light, over the whole sprite or its middle half. */
	@Nullable
	private static Color ranked(@Nullable BufferedImage image, boolean middle, double rank)
	{
		if (image == null)
		{
			return null;
		}
		int w = image.getWidth();
		List<Integer> colors = new ArrayList<>();
		for (int x = middle ? w / 4 : 0; x < (middle ? Math.max(w / 4 + 1, w * 3 / 4) : w); x++)
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) > 200)
				{
					colors.add(argb & 0xffffff);
				}
			}
		}
		if (colors.isEmpty())
		{
			return null;
		}
		colors.sort(Comparator.comparingInt(NativeTooltip::luma));
		return new Color(colors.get((int) (colors.size() * rank)));
	}

	private static int luma(int rgb)
	{
		return (rgb >> 16 & 0xff) * 299 + (rgb >> 8 & 0xff) * 587 + (rgb & 0xff) * 114;
	}

	/** The rail's thumb: Card Appearance's scroll bar, else the pack's, else the card's orange. */
	static Color railThumb()
	{
		Color thumb = flatScroll != null ? flatScroll : packThumb;
		return thumb != null ? thumb : OSRS_ORANGE;
	}

	/**
	 * The rail's track: the pack's when its thumb is the pack's, else a darker shade of the thumb, so the two never
	 * meet in one colour whatever the dividers are (Dylan 10/24: orange dividers put an orange track under the orange
	 * thumb).
	 */
	static Color railTrackFor(Color thumb)
	{
		Color track = packTrack;
		if (flatScroll == null && packThumb != null && track != null)
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
		Color track = packTrack;
		return track != null ? track : fallback;
	}

	private static BufferedImage optionalOverride(Client client, int spriteId, BufferedImage fallback)
	{
		BufferedImage override = getOverrideSprite(client, spriteId);
		return override != null ? override : fallback;
	}

	private static BufferedImage optionalTrimmedOverride(Client client, int spriteId, BufferedImage fallback)
	{
		return trimTransparentPadding(optionalOverride(client, spriteId, fallback));
	}

	private static void checkSprites()
	{
		spritesLoaded = cornerTL != null && cornerTR != null
			&& cornerBL != null && cornerBR != null
			&& edgeTop != null && edgeBottom != null
			&& edgeLeft != null && edgeRight != null;
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
		else if (parchmentBg != null)
		{
			int tw = parchmentBg.getWidth();
			int th = parchmentBg.getHeight();
			for (int y = 0; y < h; y += th)
			{
				for (int x = 0; x < w; x += tw)
				{
					g2.drawImage(parchmentBg, x, y, null);
				}
			}
		}
		else
		{
			g2.setColor(FALLBACK_BG);
			g2.fillRect(0, 0, w, h);
		}
	}

	private static BufferedImage trimTransparentPadding(BufferedImage src)
	{
		if (src == null) return null;
		int w = src.getWidth();
		int h = src.getHeight();
		int minX = w;
		int minY = h;
		int maxX = -1;
		int maxY = -1;

		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				if ((src.getRGB(x, y) >>> 24) != 0)
				{
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}

		if (maxX < minX || maxY < minY)
		{
			return src;
		}

		return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/** A mirrored copy: top to bottom when vertical, else left to right. */
	static BufferedImage flip(@Nullable BufferedImage src, boolean vertical)
	{
		return src == null ? null : ImageUtil.flipImage(src, !vertical, vertical);
	}

	private static void paintBorder(Graphics2D g2, int w, int h)
	{
		// A flat border keeps the frame's three pixels, so nothing inside the card moves: a dark outer line, then
		// the color.
		if (!spritesLoaded || flatBorder != null)
		{
			g2.setColor(BORDER_OUTER);
			g2.drawRect(0, 0, w - 1, h - 1);
			g2.setColor(flatBorder != null ? flatBorder : BORDER_MID);
			g2.drawRect(1, 1, w - 3, h - 3);
			g2.setColor(flatBorder != null ? flatBorder : BORDER_INNER);
			g2.drawRect(2, 2, w - 5, h - 5);
			return;
		}

		int cw = cornerTL.getWidth();
		int ch = cornerTL.getHeight();

		// Corners
		g2.drawImage(cornerTL, 0, 0, null);
		g2.drawImage(cornerTR, w - cornerTR.getWidth(), 0, null);
		g2.drawImage(cornerBL, 0, h - cornerBL.getHeight(), null);
		g2.drawImage(cornerBR, w - cornerBR.getWidth(), h - cornerBR.getHeight(), null);

		// Edges between the corners: top, bottom, left, right.
		tileEdge(g2, edgeTop, 0, 0, cw, w - cw, true);
		tileEdge(g2, edgeBottom, 0, h - edgeBottom.getHeight(), cw, w - cw, true);
		tileEdge(g2, edgeLeft, 0, 0, ch, h - ch, false);
		tileEdge(g2, edgeRight, w - edgeRight.getWidth(), 0, ch, h - ch, false);
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
