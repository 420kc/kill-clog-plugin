package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import net.runelite.api.Experience;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
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

	// Not-synced popup config shared by every clog cell system.

	/**
	 * Configure an ImgTooltip for a player who has not synced any collection log.
	 * Keeps native hiscore rows when the player has any signal (kc or rank);
	 * otherwise shows the muted "Obtained: --/Y" preview. Every category item
	 * draws dimmed either way so wiki links remain explorable.
	 */
	static boolean configureNotSynced(ImgTooltip tip, TooltipData data, ItemManager itemManager,
		boolean showKc, boolean showRank)
	{
		if (data == null || data.allItemIds == null || data.allItemIds.isEmpty())
		{
			return false;
		}
		tip.setTitle(data.name);
		if (data.statValue >= 0 || data.rank > 0)
		{
			if (data.statLabel != null && showKc)
			{
				tip.setInfoLine(data.statLabel, statText(data.statValue), Color.WHITE);
			}
			if (data.rankTracked && showRank)
			{
				tip.setRank(data.rank);
			}
		}
		else
		{
			// No player signal behind this grid: preview the shape and count
			// the slots instead of painting dashes for stats nobody has.
			tip.setObtainedPlaceholder(data.allItemIds.size());
		}
		tip.setItems(data.totalItems, data.allItemIds, Collections.emptySet(),
			Collections.emptyMap(), data.itemNames, itemManager);
		return true;
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

	/**
	 * Obtained trophy items from anywhere in the log, in the order the ids
	 * are listed. Items the player does not own are left out entirely.
	 */
	static List<ClogResult.ClogItem> obtainedSpecialItems(int[] specialIds, ClogResult clogResult)
	{
		List<ClogResult.ClogItem> found = new ArrayList<>();
		if (clogResult == null)
		{
			return found;
		}
		for (int specialId : specialIds)
		{
			for (List<ClogResult.ClogItem> items : clogResult.getObtainedItems().values())
			{
				ClogResult.ClogItem match = null;
				for (ClogResult.ClogItem item : items)
				{
					if (item.getId() == specialId)
					{
						match = item;
						break;
					}
				}
				if (match != null)
				{
					found.add(match);
					break;
				}
			}
		}
		return found;
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

	/**
	 * Position on the tier ladder as display strings, shared by the solo and
	 * comparison Clog Summary tooltips. Fields are null where the ladder has
	 * nothing to say: below bronze there is no current tier, at gilded there
	 * is no next one.
	 */
	static final class TierProgress
	{
		final String tierName;
		final String tierRange;
		final String progressCount;
		final String nextTierName;

		private TierProgress(String tierName, String tierRange,
			String progressCount, String nextTierName)
		{
			this.tierName = tierName;
			this.tierRange = tierRange;
			this.progressCount = progressCount;
			this.nextTierName = nextTierName;
		}
	}

	static TierProgress tierProgress(int obtained, int totalSlots)
	{
		int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
		String currentTier = getClogTierName(obtained, totalSlots);

		if (currentTier == null)
		{
			return new TierProgress(null, null,
				String.valueOf(CLOG_TIER_THRESHOLDS[0] - obtained), "bronze");
		}

		if ("gilded".equals(currentTier))
		{
			return new TierProgress("gilded", gildedThreshold + "+", null, null);
		}

		int tierIndex = -1;
		for (int i = 0; i < CLOG_TIERS.length; i++)
		{
			if (CLOG_TIERS[i].equals(currentTier))
			{
				tierIndex = i;
				break;
			}
		}

		int currentThreshold = CLOG_TIER_THRESHOLDS[tierIndex];
		int nextThreshold;
		String nextTier;
		if (tierIndex + 1 < CLOG_TIER_THRESHOLDS.length)
		{
			nextThreshold = CLOG_TIER_THRESHOLDS[tierIndex + 1];
			nextTier = CLOG_TIERS[tierIndex + 1];
		}
		else
		{
			nextThreshold = gildedThreshold;
			nextTier = "gilded";
		}

		return new TierProgress(currentTier, currentThreshold + "-" + (nextThreshold - 1),
			String.valueOf(nextThreshold - obtained), nextTier);
	}

	// Account helpers. GIM badge state lives in GimBadgeLoader.

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

	private static String statText(int value)
	{
		return value > 0 ? formatKc(value) : "--";
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

	/**
	 * True when RuneLite's core Virtual Levels plugin is switched on. Kill Clog
	 * follows that toggle instead of carrying its own: virtual levels read as
	 * one client-wide preference, and the core plugin's name and storage key
	 * are stable. Null config means the plugin's own enabledByDefault = false.
	 */
	static boolean virtualLevelsEnabled(ConfigManager configManager)
	{
		return Boolean.parseBoolean(
			configManager.getConfiguration("runelite", "virtuallevelsplugin"));
	}

	/**
	 * The level a skill summary cell shows: the hiscore level normally, the
	 * xp-derived virtual level (cap 126) when the Virtual Levels plugin is on.
	 * Unranked skills (no xp) keep the hiscore value either way.
	 */
	static int displayLevel(int hiscoreLevel, long xp, boolean virtualLevels)
	{
		if (!virtualLevels || xp <= 0)
		{
			return hiscoreLevel;
		}
		return Experience.getLevelForXp((int) xp);
	}
}
