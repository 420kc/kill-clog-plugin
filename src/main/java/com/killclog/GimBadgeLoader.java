package com.killclog;

import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;

final class GimBadgeLoader
{
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

		BufferedImage gim = imageAt(modIcons, ClogHelper.MODICON_GIM);
		BufferedImage hcgim = imageAt(modIcons, ClogHelper.MODICON_HCGIM);
		BufferedImage unrankedGim = imageAt(modIcons, ClogHelper.MODICON_UNRANKED_GIM);
		ClogHelper.setGimBadges(gim, hcgim, unrankedGim);
	}

	private static BufferedImage imageAt(IndexedSprite[] modIcons, int index)
	{
		return modIcons.length > index ? ClogHelper.indexedSpriteToImage(modIcons[index]) : null;
	}
}
