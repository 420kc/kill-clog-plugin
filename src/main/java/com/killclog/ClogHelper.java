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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.apache.commons.lang3.StringUtils;

/**
 * Pure static utility functions — zero state.
 * Formatting, image manipulation, clog tier logic, account helpers.
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

	// -------------------------------------------------------------------------
	// Clog data helpers
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Clog tier logic
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Account helpers
	// -------------------------------------------------------------------------

	// GIM modicon indices in the game's modicons sprite sheet
	static final int MODICON_GIM = 41;
	static final int MODICON_HCGIM = 42;

	// Cached GIM badge images (loaded from game modicons at runtime)
	private static volatile BufferedImage gimBadge;
	private static volatile BufferedImage hcgimBadge;

	static void setGimBadges(BufferedImage gim, BufferedImage hcgim)
	{
		gimBadge = gim;
		hcgimBadge = hcgim;
	}

	static BufferedImage getGimBadge(AccountType type)
	{
		if (type == AccountType.GROUP_IRONMAN) return gimBadge;
		if (type == AccountType.HARDCORE_GROUP_IRONMAN) return hcgimBadge;
		return null;
	}

	static String accountBadgeResource(AccountType type)
	{
		switch (type)
		{
			case IRONMAN: return "ironman.png";
			case HARDCORE_IRONMAN: return "hardcore_ironman.png";
			case ULTIMATE_IRONMAN: return "ultimate_ironman.png";
			// GIM badges loaded from game modicons — use getGimBadge() instead
			default: return null;
		}
	}

	static String accountLabel(AccountType type)
	{
		switch (type)
		{
			case IRONMAN: return "Ironman";
			case HARDCORE_IRONMAN: return "Hardcore Ironman";
			case ULTIMATE_IRONMAN: return "Ultimate Ironman";
			case GROUP_IRONMAN: return "Group Ironman";
			case HARDCORE_GROUP_IRONMAN: return "Hardcore Group Ironman";
			default: return null;
		}
	}

	// -------------------------------------------------------------------------
	// Formatting
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Image utilities
	// -------------------------------------------------------------------------

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
	 * Preserves hue and alpha — no white wash, just more vivid color.
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

	/** Paints a 12x10 hamburger icon — three 2px-thick horizontal lines on transparent. */
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

	/** Paints a 15x15 circular refresh arrow — nearly full circle with arrowhead. */
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
