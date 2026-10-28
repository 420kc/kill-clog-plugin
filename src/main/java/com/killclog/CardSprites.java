package com.killclog;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

/**
 * The game sprites every card wears, read from the game on the client thread and kept here finished for
 * NativeTooltip to draw: a resource pack's or UI theme's replacement where one is set, else the game's own.
 */
final class CardSprites
{
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
	static volatile BufferedImage parchmentBg;

	// Native 9-slice iron border sprites.
	static volatile BufferedImage cornerTL, cornerTR, cornerBL, cornerBR;
	static volatile BufferedImage edgeTop, edgeBottom, edgeLeft, edgeRight;
	static volatile boolean spritesLoaded;

	// A resource pack's own scrollbar, thumb and track: a scrolled card's rail and the summaries' bars wear its
	// colors. Without a pack the rail keeps the card's orange.
	private static final int SPRITE_SCROLL_THUMB = 790;
	private static final int SPRITE_SCROLL_TRACK = 792;
	@Nullable
	static volatile Color packThumb;
	@Nullable
	static volatile Color packTrack;

	// Every sprite a card wears, and the status row's character icon: what a pack or theme replacing one changes.
	private static final int[] WORN = {SPRITE_PARCHMENT, SPRITE_CORNER_TL, SPRITE_CORNER_TR, SPRITE_CORNER_BL,
		SPRITE_CORNER_BR, SPRITE_EDGE_HORIZ, SPRITE_EDGE_VERT, SPRITE_EDGE_BOTTOM, SPRITE_EDGE_LEFT, SPRITE_SCROLL_THUMB,
		SPRITE_SCROLL_TRACK, SpriteID.SideiconsNew.EQUIPMENT};
	// Each worn sprite's replacement at the last look, null for the game's own.
	private static final SpritePixels[] WORN_SEEN = new SpritePixels[WORN.length];

	private CardSprites()
	{
	}

	/**
	 * Whether a pack or theme has replaced, swapped or restored a worn sprite since the last look. A pack applies a
	 * moment after it is chosen (a Hub pack downloads first), so the cards ask every client tick rather than when a
	 * setting changes. Client thread.
	 */
	static boolean changed(Client client)
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
	 * Packs or another UI theme plugin has replaced the same game sprites. Client thread.
	 */
	static void load(Client client, SpriteManager spriteManager)
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
		colors.sort(Comparator.comparingInt(CardSprites::luma));
		return new Color(colors.get((int) (colors.size() * rank)));
	}

	private static int luma(int rgb)
	{
		return (rgb >> 16 & 0xff) * 299 + (rgb >> 8 & 0xff) * 587 + (rgb & 0xff) * 114;
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
}
