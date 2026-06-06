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
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_CORNER_TR, img ->
		{
			cornerTR = img;
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_CORNER_BL, img ->
		{
			cornerBL = img;
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_CORNER_BR, img ->
		{
			cornerBR = img;
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_EDGE_HORIZ, img ->
		{
			edgeTop = img;
			edgeBottom = rotate180(img);
			checkSprites();
		});
		loadSprite(client, spriteManager, SPRITE_EDGE_VERT, img ->
		{
			edgeRight = img;
			edgeLeft = rotate180(img);
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

	private static BufferedImage rotate180(BufferedImage src)
	{
		if (src == null) return null;
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage rotated = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = rotated.createGraphics();
		g.drawImage(src, w, h, 0, 0, 0, 0, w, h, null);
		g.dispose();
		return rotated;
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
