package com.killclog;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JToolTip;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.client.game.SpriteManager;

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
	static final Color NOTICE_COLOR = new Color(160, 160, 160);

	// Programmatic fallback border.
	private static final int BORDER_THICKNESS = 3;
	private static final Color BORDER_OUTER = new Color(26, 26, 26);
	private static final Color BORDER_MID = new Color(61, 51, 34);
	private static final Color BORDER_INNER = new Color(84, 72, 53);
	private static final Color FALLBACK_BG = new Color(60, 50, 35);

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

	/**
	 * Load border sprites from the game via SpriteManager.
	 * Call once when the client is ready.
	 */
	public static void loadSprites(SpriteManager spriteManager)
	{
		loadSprites(null, spriteManager);
	}

	/**
	 * Load border sprites, preferring client sprite overrides when Resource
	 * Packs or another UI theme plugin has replaced the same game sprites.
	 */
	public static void loadSprites(Client client, SpriteManager spriteManager)
	{
		loadSprite(client, spriteManager, SPRITE_PARCHMENT, img -> parchmentBg = img);
		loadSprite(client, spriteManager, SPRITE_CORNER_TL, img ->
		{
			cornerTL = img;
			cornerBL = optionalOverride(client, SPRITE_CORNER_BL, flipVertical(img));
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
			edgeBottom = optionalTrimmedOverride(client, SPRITE_EDGE_BOTTOM, flipVertical(edgeTop));
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_EDGE_VERT, img ->
		{
			edgeRight = trimTransparentPadding(img);
			edgeLeft = optionalTrimmedOverride(client, SPRITE_EDGE_LEFT, flipHorizontal(edgeRight));
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

		paintFrame(g2, w, h);

		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		paintContent(g2, w, h);

		g2.dispose();
	}

	/** Paint the same parchment and iron frame onto a standalone image. */
	static void paintFrame(Graphics2D g2, int w, int h)
	{
		paintBackground(g2, w, h);
		paintBorder(g2, w, h);
	}

	/** Paint a native 9-slice border around an inset card region. */
	static void paintInsetBorder(Graphics2D g2, int x, int y, int w, int h)
	{
		Graphics2D inset = (Graphics2D) g2.create();
		inset.translate(x, y);
		paintBorder(inset, w, h);
		inset.dispose();
	}

	/** Tile the native bottom-edge sprite as an in-window horizontal divider. */
	static void paintHorizontalDivider(Graphics2D g2, int x, int y, int width)
	{
		if (edgeBottom == null)
		{
			g2.setColor(TitleTooltip.SEPARATOR_COLOR);
			g2.drawLine(x, y, x + width, y);
			return;
		}
		int spriteWidth = edgeBottom.getWidth();
		int spriteHeight = edgeBottom.getHeight();
		int top = y - spriteHeight / 2;
		for (int drawX = x; drawX < x + width; drawX += spriteWidth)
		{
			int drawWidth = Math.min(spriteWidth, x + width - drawX);
			g2.drawImage(edgeBottom, drawX, top, drawX + drawWidth, top + spriteHeight,
				0, 0, drawWidth, spriteHeight, null);
		}
	}

	/** Tile the native left-edge sprite as an in-window vertical divider. */
	static void paintVerticalDivider(Graphics2D g2, int x, int y, int height)
	{
		if (edgeLeft == null)
		{
			g2.setColor(TitleTooltip.SEPARATOR_COLOR);
			g2.drawLine(x, y, x, y + height);
			return;
		}
		int spriteWidth = edgeLeft.getWidth();
		int spriteHeight = edgeLeft.getHeight();
		int left = x - spriteWidth / 2;
		for (int drawY = y; drawY < y + height; drawY += spriteHeight)
		{
			int drawHeight = Math.min(spriteHeight, y + height - drawY);
			g2.drawImage(edgeLeft, left, drawY, left + spriteWidth, drawY + drawHeight,
				0, 0, spriteWidth, drawHeight, null);
		}
	}

	/**
	 * Subclasses paint their content here.
	 * Background, border, and AA hints are already applied.
	 */
	protected abstract void paintContent(Graphics2D g2, int w, int h);

	private static void paintBackground(Graphics2D g2, int w, int h)
	{
		if (parchmentBg != null)
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

	private static BufferedImage flipVertical(BufferedImage src)
	{
		if (src == null) return null;
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = flipped.createGraphics();
		g.drawImage(src, 0, 0, w, h, 0, h, w, 0, null);
		g.dispose();
		return flipped;
	}

	private static BufferedImage flipHorizontal(BufferedImage src)
	{
		if (src == null) return null;
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = flipped.createGraphics();
		g.drawImage(src, 0, 0, w, h, w, 0, 0, h, null);
		g.dispose();
		return flipped;
	}

	private static void paintBorder(Graphics2D g2, int w, int h)
	{
		if (!spritesLoaded)
		{
			g2.setColor(BORDER_OUTER);
			g2.drawRect(0, 0, w - 1, h - 1);
			g2.setColor(BORDER_MID);
			g2.drawRect(1, 1, w - 3, h - 3);
			g2.setColor(BORDER_INNER);
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

		// Top edge
		int tew = edgeTop.getWidth();
		int teh = edgeTop.getHeight();
		for (int x = cw; x < w - cw; x += tew)
		{
			int drawW = Math.min(tew, w - cw - x);
			g2.drawImage(edgeTop, x, 0, x + drawW, teh,
				0, 0, drawW, teh, null);
		}

		// Bottom edge
		int bew = edgeBottom.getWidth();
		int beh = edgeBottom.getHeight();
		for (int x = cw; x < w - cw; x += bew)
		{
			int drawW = Math.min(bew, w - cw - x);
			g2.drawImage(edgeBottom, x, h - beh, x + drawW, h,
				0, 0, drawW, beh, null);
		}

		// Left edge
		int lew = edgeLeft.getWidth();
		int leh = edgeLeft.getHeight();
		for (int y = ch; y < h - ch; y += leh)
		{
			int drawH = Math.min(leh, h - ch - y);
			g2.drawImage(edgeLeft, 0, y, lew, y + drawH,
				0, 0, lew, drawH, null);
		}

		// Right edge
		int rew = edgeRight.getWidth();
		int reh = edgeRight.getHeight();
		for (int y = ch; y < h - ch; y += reh)
		{
			int drawH = Math.min(reh, h - ch - y);
			g2.drawImage(edgeRight, w - rew, y, w, y + drawH,
				0, 0, rew, drawH, null);
		}
	}
}
