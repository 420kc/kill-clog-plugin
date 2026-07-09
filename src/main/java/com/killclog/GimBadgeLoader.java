/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the GIM badge images: loads them from the game's modicons and holds
 * the per-type cache the badge resolver reads. Badge state is game-sprite
 * runtime state, so it lives here rather than in ClogHelper's pure helpers.
 */
package com.killclog;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.IndexedSprite;

final class GimBadgeLoader
{
	private static final int MODICON_GIM = IconID.GROUP_IRONMAN.getIndex();
	private static final int MODICON_HCGIM = IconID.HARDCORE_GROUP_IRONMAN.getIndex();
	private static final int MODICON_UNRANKED_GIM = IconID.UNRANKED_GROUP_IRONMAN.getIndex();

	// Cached GIM badge images (loaded from game modicons at runtime)
	private static volatile BufferedImage gimBadge;
	private static volatile BufferedImage hcgimBadge;
	private static volatile BufferedImage unrankedGimBadge;

	private GimBadgeLoader()
	{
	}

	static void load(Client client)
	{
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null)
		{
			return;
		}

		setGimBadges(
			imageAt(modIcons, MODICON_GIM),
			imageAt(modIcons, MODICON_HCGIM),
			imageAt(modIcons, MODICON_UNRANKED_GIM));
	}

	static void setGimBadges(BufferedImage gim, BufferedImage hcgim, BufferedImage unrankedGim)
	{
		gimBadge = gim;
		hcgimBadge = hcgim;
		unrankedGimBadge = unrankedGim;
	}

	static void setGimBadge(AccountType type, BufferedImage badge)
	{
		switch (type)
		{
			case GROUP_IRONMAN:
				gimBadge = badge;
				break;
			case HARDCORE_GROUP_IRONMAN:
				hcgimBadge = badge;
				break;
			case UNRANKED_GROUP_IRONMAN:
				unrankedGimBadge = badge;
				break;
			default:
				break;
		}
	}

	static BufferedImage getGimBadge(AccountType type)
	{
		if (type == AccountType.GROUP_IRONMAN) return gimBadge;
		if (type == AccountType.HARDCORE_GROUP_IRONMAN) return hcgimBadge;
		if (type == AccountType.UNRANKED_GROUP_IRONMAN) return unrankedGimBadge;
		return null;
	}

	static int gimModiconIndex(AccountType type)
	{
		switch (type)
		{
			case GROUP_IRONMAN: return MODICON_GIM;
			case HARDCORE_GROUP_IRONMAN: return MODICON_HCGIM;
			case UNRANKED_GROUP_IRONMAN: return MODICON_UNRANKED_GIM;
			default: return -1;
		}
	}

	@Nullable
	static BufferedImage indexedSpriteToImage(IndexedSprite sprite)
	{
		if (sprite == null) return null;
		int w = sprite.getWidth();
		int h = sprite.getHeight();
		if (w <= 0 || h <= 0) return null;

		int canvasW = sprite.getOriginalWidth() > 0 ? sprite.getOriginalWidth() : w;
		int canvasH = sprite.getOriginalHeight() > 0 ? sprite.getOriginalHeight() : h;
		BufferedImage img = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
		byte[] pixels = sprite.getPixels();
		int[] palette = sprite.getPalette();
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int idx = pixels[y * w + x] & 0xFF;
				int px = x + sprite.getOffsetX();
				int py = y + sprite.getOffsetY();
				if (px >= 0 && px < canvasW && py >= 0 && py < canvasH)
				{
					img.setRGB(px, py, idx == 0 ? 0 : 0xFF000000 | palette[idx]);
				}
			}
		}
		return img;
	}

	private static BufferedImage imageAt(IndexedSprite[] modIcons, int index)
	{
		return modIcons.length > index ? indexedSpriteToImage(modIcons[index]) : null;
	}
}
