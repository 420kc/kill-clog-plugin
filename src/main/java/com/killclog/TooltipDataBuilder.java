package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemManager;

/**
 * Builds {@link TooltipData} objects from a {@link ClogResult} without touching any UI.
 */
final class TooltipDataBuilder
{
	private final ItemManager itemManager;

	TooltipDataBuilder(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * Build TooltipData for a clog category.
	 * Returns null if no item data exists for the category.
	 */
	TooltipData buildTooltipData(String displayName, String category, int rank, ClogResult clogResult)
	{
		return buildTooltipData(displayName, category, rank, -1, null, clogResult);
	}

	/**
	 * Boss-cell variant: carries the hiscore kc and the local player's
	 * recorded pb for the KC/PB header line.
	 */
	TooltipData buildTooltipData(String displayName, String category, int rank, int kc, String pb, ClogResult clogResult)
	{
		if (clogResult == null) return null;

		List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
		List<Integer> allItems = clogResult.getCategoryItems().get(category);

		if ((obtained == null || obtained.isEmpty()) && (allItems == null || allItems.isEmpty()))
		{
			return null;
		}

		Set<Integer> obtainedIds = ClogHelper.getObtainedIds(category, clogResult);
		Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
		if (obtained != null)
		{
			for (ClogResult.ClogItem item : obtained)
			{
				obtainedCounts.put(item.getId(), item.getCount());
			}
		}

		int totalItems = allItems != null ? allItems.size() : obtainedIds.size();
		int obtainedCount = allItems != null
			? ClogHelper.countObtained(allItems, obtainedIds) : obtainedIds.size();

		List<Integer> itemList = allItems != null ? allItems : new ArrayList<>(obtainedIds);
		return TooltipData.builder()
			.name(displayName)
			.rank(rank)
			.obtainedCount(obtainedCount)
			.totalItems(totalItems)
			.allItemIds(itemList)
			.obtainedIds(obtainedIds)
			.obtainedCounts(obtainedCounts)
			.itemNames(itemNamesFor(itemList, clogResult))
			.kc(kc)
			.pb(pb)
			.build();
	}

	TooltipData buildUnsyncedTooltipData(String displayName, String category, int rank,
		String statLabel, int statValue, ClogResult catalog)
	{
		if (catalog == null) return null;
		List<Integer> allItems = catalog.getCategoryItems().get(category);
		if (allItems == null || allItems.isEmpty())
		{
			return null;
		}
		return buildUnsyncedTooltipData(displayName, allItems, rank, statLabel, statValue, true, catalog);
	}

	/**
	 * Build TooltipData for a clue rare category (3rd Age / Gilded).
	 * Returns null if the category has no items.
	 */
	TooltipData buildClueRareData(String name, String clogCategory, ClogResult clogResult)
	{
		if (clogResult == null) return null;
		List<Integer> allItems = clogResult.getCategoryItems().get(clogCategory);
		List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(clogCategory);

		if (allItems == null || allItems.isEmpty())
		{
			allItems = staticClueRareItems(clogCategory);
		}

		if (allItems.isEmpty())
		{
			return null;
		}

		obtained = rareObtainedItems(obtained, allItems, clogResult);
		Set<Integer> obtainedIds = new HashSet<>();
		Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
		if (obtained != null)
		{
			for (ClogResult.ClogItem item : obtained)
			{
				obtainedIds.add(item.getId());
				obtainedCounts.put(item.getId(), item.getCount());
			}
		}

		int obtainedCount = ClogHelper.countObtained(allItems, obtainedIds);
		return TooltipData.builder()
			.name(name)
			.obtainedCount(obtainedCount)
			.totalItems(allItems.size())
			.allItemIds(allItems)
			.obtainedIds(obtainedIds)
			.obtainedCounts(obtainedCounts)
			.itemNames(itemNamesFor(allItems, clogResult))
			.rankTracked(false)
			.build();
	}

	private static List<ClogResult.ClogItem> rareObtainedItems(List<ClogResult.ClogItem> obtained,
		List<Integer> allItems, ClogResult clogResult)
	{
		if (obtained != null && !obtained.isEmpty())
		{
			return obtained;
		}

		Set<Integer> rareIds = new HashSet<>(allItems);
		List<ClogResult.ClogItem> matches = new ArrayList<>();
		for (List<ClogResult.ClogItem> catObtained : clogResult.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : catObtained)
			{
				if (rareIds.contains(item.getId()))
				{
					matches.add(item);
				}
			}
		}
		return matches;
	}

	private static List<Integer> staticClueRareItems(String clogCategory)
	{
		if (PanelData.CLOG_THIRD_AGE.equals(clogCategory))
		{
			return TooltipData.itemList(PanelData.THIRD_AGE_ITEMS);
		}
		if (PanelData.CLOG_GILDED.equals(clogCategory))
		{
			return TooltipData.itemList(PanelData.GILDED_ITEMS);
		}
		return new ArrayList<>();
	}

	/**
	 * Build TooltipData for a custom rare category (Hard/Elite/Master Rare).
	 * Scans obtained items across all collection-log categories.
	 */
	TooltipData buildCustomRareData(String name, int[] itemIds, ClogResult clogResult)
	{
		if (clogResult == null) return null;
		Set<Integer> allObtainedGlobal = new HashSet<>();
		Map<Integer, Integer> allCountsGlobal = new HashMap<>();
		for (List<ClogResult.ClogItem> catObtained : clogResult.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : catObtained)
			{
				allObtainedGlobal.add(item.getId());
				allCountsGlobal.merge(item.getId(), item.getCount(), Integer::max);
			}
		}

		List<Integer> allItemsList = new ArrayList<>();
		Set<Integer> obtainedIds = new HashSet<>();
		Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
		for (int id : itemIds)
		{
			allItemsList.add(id);
			if (allObtainedGlobal.contains(id))
			{
				obtainedIds.add(id);
				obtainedCounts.put(id, allCountsGlobal.getOrDefault(id, 1));
			}
		}

		int obtainedCount = obtainedIds.size();
		return TooltipData.builder()
			.name(name)
			.obtainedCount(obtainedCount)
			.totalItems(allItemsList.size())
			.allItemIds(allItemsList)
			.obtainedIds(obtainedIds)
			.obtainedCounts(obtainedCounts)
			.itemNames(itemNamesFor(allItemsList, clogResult))
			.rankTracked(false)
			.build();
	}

	TooltipData buildUnsyncedItemData(String name, int[] itemIds, ClogResult catalog)
	{
		return buildUnsyncedItemData(name, TooltipData.itemList(itemIds), catalog);
	}

	TooltipData buildUnsyncedItemData(String name, List<Integer> itemIds, ClogResult catalog)
	{
		if (catalog == null || itemIds == null || itemIds.isEmpty())
		{
			return null;
		}
		return buildUnsyncedTooltipData(name, itemIds, -1, null, -1, false, catalog);
	}

	private TooltipData buildUnsyncedTooltipData(String displayName, List<Integer> itemIds,
		int rank, String statLabel, int statValue, boolean rankTracked, ClogResult catalog)
	{
		return TooltipData.builder()
			.name(displayName)
			.rank(rank)
			.totalItems(itemIds.size())
			.allItemIds(itemIds)
			.obtainedIds(new HashSet<>())
			.obtainedCounts(new LinkedHashMap<>())
			.itemNames(itemNamesFor(itemIds, catalog))
			.rankTracked(rankTracked)
			.statLabel(statLabel)
			.statValue(statValue)
			.build();
	}

	private static Map<Integer, String> itemNamesFor(List<Integer> itemIds, ClogResult clogResult)
	{
		Map<Integer, String> names = new LinkedHashMap<>();
		for (int itemId : itemIds)
		{
			String name = clogResult.getItemName(itemId);
			if (name != null)
			{
				names.put(itemId, name);
			}
		}
		return names;
	}

	/** Trigger async loads for all tooltip items. */
	void preloadItemImages(TooltipData data)
	{
		for (int itemId : data.allItemIds)
		{
			int count = data.obtainedIds.contains(itemId)
				? data.obtainedCounts.getOrDefault(itemId, 1) : 1;
			itemManager.getImage(itemId, count, false);
		}
	}
}
