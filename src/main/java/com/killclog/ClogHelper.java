package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import net.runelite.api.IconID;
import net.runelite.api.IndexedSprite;
import org.apache.commons.lang3.StringUtils;

/**
 * Static formatting, image manipulation, clog tier, and account helpers.
 */
final class ClogHelper
{
	static final String[] CLOG_TIERS = {
		"bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "gilded"
	};

	static final int[] CLOG_TIER_THRESHOLDS = {100, 300, 500, 700, 900, 1000, 1100, 1200};

	private ClogHelper()
	{
	}

	// Clog data helpers.

	static Set<Integer> getObtainedIds(String category, ClogResult clogResult)
	{
		if (clogResult == null) return new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
		if (obtained != null)
		{
			for (ClogResult.ClogItem item : obtained) ids.add(item.getId());
		}
		return ids;
	}

	static int countObtained(List<Integer> allItems, Set<Integer> obtainedIds)
	{
		int count = 0;
		for (int id : allItems) if (obtainedIds.contains(id)) count++;
		return count;
	}

	static int[] clogCounts(String category, ClogResult clogResult)
	{
		if (clogResult == null) return null;
		List<Integer> items = clogResult.getCategoryItems().get(category);
		if (items == null || items.isEmpty()) return null;
		Set<Integer> obtained = getObtainedIds(category, clogResult);
		return new int[]{countObtained(items, obtained), items.size()};
	}

	static int[] sumClogTotals(ClogResult result)
	{
		Set<Integer> allItems = new HashSet<>();
		Set<Integer> allObtained = new HashSet<>();
		for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
		{
			allItems.addAll(entry.getValue());
			List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(entry.getKey());
			if (obtained != null)
			{
				for (ClogResult.ClogItem item : obtained) allObtained.add(item.getId());
			}
		}
		int obtained = result.getUniqueObtained() > 0
			? result.getUniqueObtained() : allObtained.size();
		int total = result.getUniqueTotal() > 0
			? result.getUniqueTotal() : allItems.size();
		return new int[]{obtained, total};
	}

	// Clog tier logic.

	static Color clogColor(int obtained, int total, KillClogConfig config)
	{
		if (obtained == total) return config.completedClogColor();
		if (obtained == total - 1 && total > 1) return config.missing1Color();
		if (obtained == 0) return config.emptyClogColor();
		return config.inProgressClogColor();
	}

	static String getClogTierName(int obtained, int totalSlots)
	{
		int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
		if (obtained >= gildedThreshold) return "gilded";
		for (int i = CLOG_TIER_THRESHOLDS.length - 1; i >= 0; i--)
		{
			if (obtained >= CLOG_TIER_THRESHOLDS[i]) return CLOG_TIERS[i];
		}
		return null;
	}

	// Account helpers.

	static final int MODICON_GIM = IconID.GROUP_IRONMAN.getIndex();
	static final int MODICON_HCGIM = IconID.HARDCORE_GROUP_IRONMAN.getIndex();
	static final int MODICON_UNRANKED_GIM = IconID.UNRANKED_GROUP_IRONMAN.getIndex();

	// Cached GIM badge images (loaded from game modicons at runtime)
	private static volatile BufferedImage gimBadge;
	private static volatile BufferedImage hcgimBadge;
	private static volatile BufferedImage unrankedGimBadge;

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

	static String accountBadgeResource(AccountType type)
	{
		switch (type)
		{
			case IRONMAN: return "ironman.png";
			case HARDCORE_IRONMAN: return "hardcore_ironman.png";
			case ULTIMATE_IRONMAN: return "ultimate_ironman.png";
			// GIM badges come from game modicons through AccountBadgeResolver.
			default: return null;
		}
	}

	static String accountLabel(AccountType type)
	{
		return type != null && type != AccountType.REGULAR ? type.displayName() : null;
	}

	// Formatting.

	static String pad(String text)
	{
		return StringUtils.leftPad(text, 4);
	}

	static String formatKc(int kc)
	{
		if (kc >= 1_000_000) return kc / 1_000_000 + "m";
		if (kc >= 10_000) return kc / 1_000 + "k";
		return String.valueOf(kc);
	}

	// Image utilities.

	static BufferedImage iconToImage(ImageIcon icon)
	{
		if (icon == null) return null;
		BufferedImage img = new BufferedImage(
			icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		icon.paintIcon(null, g, 0, 0);
		g.dispose();
		return img;
	}

	static BufferedImage createDimmedImage(ImageIcon icon)
	{
		BufferedImage original = iconToImage(icon);
		BufferedImage dimmed = new BufferedImage(
			original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = dimmed.createGraphics();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
		g2.drawImage(original, 0, 0, null);
		g2.dispose();
		return dimmed;
	}

	/**
	 * Boosts RGB channels by a multiplier (e.g. 1.10 = 10% brighter).
	 * Preserves hue and alpha while increasing color intensity.
	 */
	static BufferedImage createBoostedImage(ImageIcon icon, float factor)
	{
		BufferedImage src = iconToImage(icon);
		int[] pixels = src.getRGB(0, 0, src.getWidth(), src.getHeight(), null, 0, src.getWidth());
		for (int i = 0; i < pixels.length; i++)
		{
			int a = (pixels[i] >> 24) & 0xFF;
			int r = Math.min(255, (int) (((pixels[i] >> 16) & 0xFF) * factor));
			int gr = Math.min(255, (int) (((pixels[i] >> 8) & 0xFF) * factor));
			int b = Math.min(255, (int) ((pixels[i] & 0xFF) * factor));
			pixels[i] = (a << 24) | (r << 16) | (gr << 8) | b;
		}
		src.setRGB(0, 0, src.getWidth(), src.getHeight(), pixels, 0, src.getWidth());
		return src;
	}

	/** Paints a 12x10 hamburger icon. */
	static BufferedImage makeHamburgerIcon(Color barColor)
	{
		int w = 12, h = 10;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(barColor);
		g.fillRect(1, 0, w - 2, 2);   // top line
		g.fillRect(1, 4, w - 2, 2);   // middle line (1px gap)
		g.fillRect(1, 8, w - 2, 2);   // bottom line (1px gap)
		g.dispose();
		return img;
	}

	/** Paints a 15x15 split-color magnifying glass. */
	static BufferedImage makeCompareIcon(Color left, Color right, float brightness)
	{
		int s = 15;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new java.awt.BasicStroke(1.5f));
		// Lens circle: center (7,5), radius 4
		int cx = 7, cy = 5, r = 4;
		// Left half: blue.
		g.setClip(0, 0, cx, s);
		g.setColor(brighten(left, brightness));
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
		// Blue handle.
		g.drawLine(cx - 3, cy + 3, cx - 6, cy + 6);
		// Right half: red.
		g.setClip(cx, 0, s, s);
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
		int s = 15;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new java.awt.BasicStroke(1.5f));
		g.setColor(brighten(color, brightness));
		int cx = 7, cy = 5, r = 4;
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
		g.drawLine(cx + 3, cy + 3, cx + 6, cy + 6);
		g.dispose();
		return img;
	}

	private static Color brighten(Color c, float factor)
	{
		int r = Math.min(255, (int) (c.getRed() * factor));
		int g = Math.min(255, (int) (c.getGreen() * factor));
		int b = Math.min(255, (int) (c.getBlue() * factor));
		return new Color(r, g, b, c.getAlpha());
	}

	/** Paints a 15x15 circular refresh arrow. */
	static BufferedImage makeRefreshIcon(Color color)
	{
		int s = 15;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.setStroke(new java.awt.BasicStroke(1.5f));
		// Arc: nearly full circle (300 degrees), gap at top-right
		g.drawArc(2, 2, 10, 10, 30, 300);
		// Arrowhead at the end of the arc (top-right area)
		int ax = 11, ay = 3;
		g.drawLine(ax, ay, ax - 3, ay);
		g.drawLine(ax, ay, ax, ay + 3);
		g.dispose();
		return img;
	}

	static void styleSearchBar(Container container)
	{
		for (Component c : container.getComponents())
		{
			if (c instanceof JButton)
			{
				JButton btn = (JButton) c;
				btn.setOpaque(false);
				btn.setContentAreaFilled(false);
				btn.setBorderPainted(false);
			}
			else if (c instanceof Container)
			{
				styleSearchBar((Container) c);
			}
		}
	}
}
