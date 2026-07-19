package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable data holder for sprite tooltip content. */
final class TooltipData
{
	final String name;
	final int rank;
	final int obtainedCount;
	final int totalItems;
	final boolean rankTracked;
	final String statLabel;
	final int statValue;
	final List<Integer> allItemIds;
	final Set<Integer> obtainedIds;
	final Map<Integer, Integer> obtainedCounts;
	final Map<Integer, String> itemNames;
	/** Boss kill count for the KC/PB header line, -1 when unknown. */
	final int kc;
	/** The local player's recorded personal best for the KC/PB header line, null when none. */
	final String pb;

	static List<Integer> itemList(int[] itemIds)
	{
		List<Integer> items = new ArrayList<>(itemIds.length);
		for (int itemId : itemIds)
		{
			items.add(itemId);
		}
		return items;
	}

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts)
	{
		this(name, rank, obtainedCount, totalItems,
			allItemIds, obtainedIds, obtainedCounts, Collections.emptyMap(), true);
	}

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts, boolean rankTracked)
	{
		this(name, rank, obtainedCount, totalItems, allItemIds, obtainedIds,
			obtainedCounts, Collections.emptyMap(), rankTracked);
	}

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts,
		Map<Integer, String> itemNames, boolean rankTracked)
	{
		this(name, rank, obtainedCount, totalItems, allItemIds, obtainedIds,
			obtainedCounts, itemNames, rankTracked, null, -1);
	}

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts,
				Map<Integer, String> itemNames, boolean rankTracked,
				String statLabel, int statValue)
	{
		this(name, rank, obtainedCount, totalItems, allItemIds, obtainedIds,
			obtainedCounts, itemNames, rankTracked, statLabel, statValue, -1, null);
	}

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts,
				Map<Integer, String> itemNames, boolean rankTracked,
				String statLabel, int statValue, int kc, String pb)
	{
		this.name = name;
		this.rank = rank;
		this.obtainedCount = obtainedCount;
		this.totalItems = totalItems;
		this.rankTracked = rankTracked;
		this.statLabel = statLabel;
		this.statValue = statValue;
		this.allItemIds = allItemIds;
		this.obtainedIds = obtainedIds;
		this.obtainedCounts = obtainedCounts;
		this.itemNames = itemNames != null ? itemNames : Collections.emptyMap();
		this.kc = kc;
		this.pb = pb;
	}
}
