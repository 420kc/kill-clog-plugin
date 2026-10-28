package com.killclog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;

/**
 * Kill Clog's own icons, bundled or drawn: the plugin icon, the three source icons (Kill Clog, RuneProfile,
 * TempleOSRS) and the search row's magnifiers.
 */
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

	/** Paints a 15x15 split-color magnifying glass. */
	static BufferedImage makeCompareIcon(Color left, Color right, float brightness)
	{
		BufferedImage img = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = lens(img);
		// Lens circle: center (7,5), radius 4
		int cx = 7, cy = 5, r = 4;
		// Left half: blue.
		g.setClip(0, 0, cx, 15);
		g.setColor(brighten(left, brightness));
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
		// Blue handle.
		g.drawLine(cx - 3, cy + 3, cx - 6, cy + 6);
		// Right half: red.
		g.setClip(cx, 0, 15, 15);
		g.setColor(brighten(right, brightness));
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
		// Red handle.
		g.drawLine(cx + 3, cy + 3, cx + 6, cy + 6);
		g.setClip(null);
		g.dispose();
		return img;
	}

	/** Paints a 15x15 single-color magnifying glass for compact icon buttons. */
	static BufferedImage makeSearchIcon(Color color, float brightness)
	{
		BufferedImage img = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = lens(img);
		g.setColor(brighten(color, brightness));
		int cx = 7, cy = 5, r = 4;
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
		g.drawLine(cx + 3, cy + 3, cx + 6, cy + 6);
		g.dispose();
		return img;
	}

	/** A magnifying glass's canvas: antialiased, with its 1.5 px stroke. */
	private static Graphics2D lens(BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new java.awt.BasicStroke(1.5f));
		return g;
	}

	private static Color brighten(Color c, float factor)
	{
		int r = Math.min(255, (int) (c.getRed() * factor));
		int g = Math.min(255, (int) (c.getGreen() * factor));
		int b = Math.min(255, (int) (c.getBlue() * factor));
		return new Color(r, g, b, c.getAlpha());
	}
}
