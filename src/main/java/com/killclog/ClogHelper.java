package com.killclog;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.swing.JComponent;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.QuantityFormatter;
import org.apache.commons.lang3.StringUtils;

/**
 * The Collection Log's numbers: what a log holds (counts, the Rare shelf, the totals), its tiers bronze to gilded, and
 * how the panel writes a count. A cell's popup lines live in Cells, account badges in AccountBadgeResolver, icons in
 * KillClogIcons and PanelIconCache, and virtual levels in SkillCellGrid.
 */
final class ClogHelper
{
	// The tiers, bronze to gilded, one row each in clog-tiers.tsv: name, trophy item, and the unique items it starts
	// at (gilded's is "-": it floats with the log, tierThreshold).
	private static final List<String[]> TIER_ROWS = CatalogTsv.rows(ClogHelper.class, "clog-tiers.tsv", 3);
	static final String[] CLOG_TIERS = TIER_ROWS.stream().map(row -> row[0]).toArray(String[]::new);
	static final int[] CLOG_TIER_ITEM_IDS = TIER_ROWS.stream().mapToInt(row -> Integer.parseInt(row[1])).toArray();
	static final int[] CLOG_TIER_THRESHOLDS = TIER_ROWS.stream().filter(row -> !row[2].equals("-"))
		.mapToInt(row -> Integer.parseInt(row[2])).toArray();

	static final String[] CLOG_GROUP_NAMES = {"Bosses", "Raids", "Clues", "Minigames", "Other"};

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

	// The Rare shelf's row, as killclog.com's profile card lays it: more rares wrap to the next.
	static final int RARES_PER_ROW = 6;

	/**
	 * The Collection Log card's Rare shelf, filled as killclog.com's profile card fills it: every trophy, then every
	 * 3rd age piece in the game's order, then every megarare (2.7.2: six at most hid most of a big 3rd age log).
	 */
	static List<ClogResult.ClogItem> rareItems(ClogResult clog)
	{
		List<ClogResult.ClogItem> rares = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (int[] ids : new int[][]{PanelData.SPECIAL_ITEM_IDS, PanelData.THIRD_AGE_ITEMS, PanelData.MEGARARE_ITEM_IDS})
		{
			for (ClogResult.ClogItem item : obtainedSpecialItems(ids, clog))
			{
				if (seen.add(item.getId()))
				{
					rares.add(item);
				}
			}
		}
		return rares;
	}

	static int[] clogCounts(String category, ClogResult clogResult)
	{
		if (clogResult == null) return null;
		List<Integer> items = clogResult.getCategoryItems().get(category);
		if (items == null || items.isEmpty()) return null;
		Set<Integer> obtained = getObtainedIds(category, clogResult);
		return new int[]{countObtained(items, obtained), items.size()};
	}

	/** A hiscore total does not imply knowledge of the player's individual items. */
	static int[] summaryTotals(ClogResult clog, HiscoreResult hiscore, ClogResult catalog)
	{
		if (clog != null) return sumClogTotals(clog);
		int count = hiscore != null ? hiscore.getActivityScore(HiscoreSkill.COLLECTIONS_LOGGED.getName()) : -1;
		int total = catalog != null ? sumClogTotals(catalog)[1] : -1;
		return new int[]{count, total > 0 ? total : -1};
	}

	static int[] sumClogTotals(ClogResult result)
	{
		return sumClogTotals(result, IntUnaryOperator.identity());
	}

	static int[] sumClogTotals(ClogResult result, IntUnaryOperator canonicalizer)
	{
		Set<Integer> allItems = new HashSet<>();
		Set<Integer> allObtained = new HashSet<>();
		for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
		{
			for (int itemId : entry.getValue())
			{
				allItems.add(canonicalizer.applyAsInt(itemId));
			}
			List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(entry.getKey());
			if (obtained != null)
			{
				for (ClogResult.ClogItem item : obtained)
				{
					allObtained.add(canonicalizer.applyAsInt(item.getId()));
				}
			}
		}
		int obtained = result.getUniqueObtained() > 0
			? result.getUniqueObtained() : allObtained.size();
		// The largest total known: the game's slots once the log is on the game's pages (ClogResult.onPages), else the
		// pages the log was sent with, unless the sync knew more. A sync carries the game's total at that moment, so a
		// player who hasn't synced since the log grew kept the old one (1717 against 1734 on 10/27).
		int total = Math.max(result.getUniqueTotal(), allItems.size());
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

	/** Where a tier starts. Gilded, the last, floats at 90% of the log in steps of 25. */
	static int tierThreshold(int tier, int totalSlots)
	{
		return tier < CLOG_TIER_THRESHOLDS.length
			? CLOG_TIER_THRESHOLDS[tier] : (int) (totalSlots * 0.9) / 25 * 25;
	}

	/** The tier a count has reached, as an index into CLOG_TIERS; -1 below bronze. */
	static int tierIndex(int obtained, int totalSlots)
	{
		for (int i = CLOG_TIERS.length - 1; i >= 0; i--)
		{
			if (obtained >= tierThreshold(i, totalSlots)) return i;
		}
		return -1;
	}

	static String getClogTierName(int obtained, int totalSlots)
	{
		int tier = tierIndex(obtained, totalSlots);
		return tier >= 0 ? CLOG_TIERS[tier] : null;
	}

	/**
	 * Every tier's readout, e.g. "Rune: 1,100-1,199"; gilded is open-ended.
	 * A tier still ahead of the player also says how far off it is.
	 */
	static String[] tierLabels(int obtained, int totalSlots)
	{
		String[] labels = new String[CLOG_TIERS.length];
		int tier = tierIndex(obtained, totalSlots);
		for (int i = 0; i < labels.length; i++)
		{
			int start = tierThreshold(i, totalSlots);
			labels[i] = StringUtils.capitalize(CLOG_TIERS[i]) + ": "
				+ QuantityFormatter.formatNumber(start)
				+ (i == labels.length - 1 ? "+"
				: "-" + QuantityFormatter.formatNumber(tierThreshold(i + 1, totalSlots) - 1))
				+ (i > tier
				? " (" + QuantityFormatter.formatNumber(start - obtained) + " more)" : "");
		}
		return labels;
	}

	// Swing text treatment.

	/**
	 * The panel-wide ClearType fix: text antialiasing on any Swing text
	 * component that sets a foreground. The one implementation of the
	 * convention every panel label follows.
	 */
	static void antialias(JComponent component)
	{
		component.putClientProperty(
			java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
			java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

	/** A count as every cell and card shows one: shortened past 10k, or "--" when there is none. */
	static String countText(int count)
	{
		return count > 0 ? formatKc(count) : "--";
	}

}
