package com.killclog;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;

final class KillClogIcons
{
	private static final String ICON_RESOURCE = "/com/killclog/icon.png";
	private static final String RUNEPROFILE_SOURCE_RESOURCE =
		"/com/killclog/runeprofile-source.png";
	private static final String TEMPLE_SOURCE_RESOURCE =
		"/com/killclog/temple-source.png";

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

	static BufferedImage killClogSourceIcon(int size)
	{
		return fitToSquare(pluginIcon(), size);
	}

	static BufferedImage runeProfileSourceIcon(int size)
	{
		return fitToSquare(loadImageResource(RUNEPROFILE_SOURCE_RESOURCE), size);
	}

	static BufferedImage templeSourceIcon(int size)
	{
		return fitToSquare(loadImageResource(TEMPLE_SOURCE_RESOURCE), size);
	}

	private static BufferedImage fitToSquare(BufferedImage image, int size)
	{
		if (image == null || size <= 0)
		{
			return null;
		}
		float scale = Math.min(size / (float) image.getWidth(), size / (float) image.getHeight());
		int width = Math.max(1, Math.round(image.getWidth() * scale));
		int height = Math.max(1, Math.round(image.getHeight() * scale));
		BufferedImage scaled = ImageUtil.resizeImage(image, width, height);
		BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = canvas.createGraphics();
		try
		{
			g2.drawImage(scaled, (size - width) / 2, (size - height) / 2, null);
		}
		finally
		{
			g2.dispose();
		}
		return canvas;
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
