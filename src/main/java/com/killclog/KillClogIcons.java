package com.killclog;

import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;

final class KillClogIcons
{
	private static final String ICON_RESOURCE = "/com/killclog/icon.png";

	private KillClogIcons()
	{
	}

	static BufferedImage pluginIcon()
	{
		return loadImageResource(ICON_RESOURCE);
	}

	static BufferedImage pluginIconOrCollectionLog(ItemManager itemManager)
	{
		BufferedImage icon = pluginIcon();
		return icon != null ? icon : itemManager.getImage(PanelData.COLLECTION_LOG_ITEM_ID, 1, false);
	}

	static BufferedImage resizedPluginIcon(int width, int height, ItemManager itemManager)
	{
		BufferedImage icon = pluginIconOrCollectionLog(itemManager);
		return icon != null ? ImageUtil.resizeImage(icon, width, height) : null;
	}

	private static BufferedImage loadImageResource(String resource)
	{
		try
		{
			return ImageUtil.loadImageResource(KillClogIcons.class, resource);
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
	}
}
