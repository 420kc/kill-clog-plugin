package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;

/** Immutable data holder for sprite tooltip content. */
@Builder(toBuilder = true)
final class TooltipData
{
	final String name;
	@Builder.Default
	final int rank = -1;
	/** -1 means unsynced; the default keeps an omission from faking a synced state. */
	@Builder.Default
	final int obtainedCount = -1;
	final int totalItems;
	@Builder.Default
	final boolean rankTracked = true;
	final String statLabel;
	@Builder.Default
	final int statValue = -1;
	final List<Integer> allItemIds;
	final Set<Integer> obtainedIds;
	final Map<Integer, Integer> obtainedCounts;
	@Builder.Default
	final Map<Integer, String> itemNames = Collections.emptyMap();
	/** Boss kill count for the KC/PB header line, -1 when unknown. */
	@Builder.Default
	final int kc = -1;
	/** The local player's recorded personal best for the KC/PB header line, null when none. */
	final String pb;

	/** This data with the sprite grid stripped: header lines only. */
	TooltipData withoutGrid()
	{
		return toBuilder().allItemIds(null).obtainedIds(null).obtainedCounts(null).build();
	}

	static List<Integer> itemList(int[] itemIds)
	{
		List<Integer> items = new ArrayList<>(itemIds.length);
		for (int itemId : itemIds)
		{
			items.add(itemId);
		}
		return items;
	}
}
