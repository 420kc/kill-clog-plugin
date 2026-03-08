package com.killclog;

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
	final List<Integer> allItemIds;
	final Set<Integer> obtainedIds;
	final Map<Integer, Integer> obtainedCounts;

	TooltipData(String name, int rank, int obtainedCount, int totalItems,
				List<Integer> allItemIds, Set<Integer> obtainedIds,
				Map<Integer, Integer> obtainedCounts)
	{
		this.name = name;
		this.rank = rank;
		this.obtainedCount = obtainedCount;
		this.totalItems = totalItems;
		this.allItemIds = allItemIds;
		this.obtainedIds = obtainedIds;
		this.obtainedCounts = obtainedCounts;
	}
}
