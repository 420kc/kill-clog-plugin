package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure record math for the local cache's rename-continuity machinery: no
 * state, no I/O, just the merge and provenance rules for
 * {@link LocalClogCache.PlayerClogData}.
 */
final class ClogRecords
{
	private ClogRecords()
	{
	}

	static boolean hasFirstPartyMarks(LocalClogCache.PlayerClogData data)
	{
		if (data == null || data.firstPartyByCategory == null)
		{
			return false;
		}
		for (List<Integer> ids : data.firstPartyByCategory.values())
		{
			if (ids != null && !ids.isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Union for the post-crash heal: destination (the newer writing) wins
	 * per-item and per-category conflicts; everything the source alone knows
	 * is carried over. Nothing is discarded.
	 */
	static LocalClogCache.PlayerClogData mergeForMigration(
		LocalClogCache.PlayerClogData dest, LocalClogCache.PlayerClogData source)
	{
		// A legacy source (null marks) is wholly first-party by definition -
		// the class contract grandfathers it at first capture. Materialize
		// that grandfather EXPLICITLY before the mark union, or the merged
		// file's marks will not cover the legacy items and the sync filter
		// would silently drop the migrated history from every future push.
		if (source.firstPartyByCategory == null && source.obtained != null)
		{
			Map<String, List<Integer>> grandfathered = new ConcurrentHashMap<>();
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : source.obtained.entrySet())
			{
				List<Integer> ids = new ArrayList<>();
				for (ClogResult.ClogItem item : e.getValue())
				{
					ids.add(item.getId());
				}
				grandfathered.put(e.getKey(), ids);
			}
			source.firstPartyByCategory = grandfathered;
		}
		if (source.categories != null)
		{
			if (dest.categories == null)
			{
				dest.categories = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<Integer>> e : source.categories.entrySet())
			{
				dest.categories.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		if (source.obtained != null)
		{
			if (dest.obtained == null)
			{
				dest.obtained = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : source.obtained.entrySet())
			{
				List<ClogResult.ClogItem> existing = dest.obtained.get(e.getKey());
				if (existing == null)
				{
					dest.obtained.put(e.getKey(), e.getValue());
					continue;
				}
				for (ClogResult.ClogItem item : e.getValue())
				{
					boolean present = false;
					for (ClogResult.ClogItem have : existing)
					{
						if (have.getId() == item.getId())
						{
							present = true;
							break;
						}
					}
					if (!present)
					{
						existing.add(item);
					}
				}
			}
		}
		if (source.firstPartyByCategory != null)
		{
			if (dest.firstPartyByCategory == null)
			{
				dest.firstPartyByCategory = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<Integer>> e : source.firstPartyByCategory.entrySet())
			{
				dest.firstPartyByCategory.merge(e.getKey(), e.getValue(), (a, b) ->
				{
					List<Integer> union = new ArrayList<>(a);
					for (Integer id : b)
					{
						if (!union.contains(id))
						{
							union.add(id);
						}
					}
					return union;
				});
			}
		}
		dest.uniqueObtained = Math.max(dest.uniqueObtained, source.uniqueObtained);
		dest.uniqueTotal = Math.max(dest.uniqueTotal, source.uniqueTotal);
		if (dest.lastChanged == null
			|| (source.lastChanged != null && source.lastChanged.compareTo(dest.lastChanged) > 0))
		{
			dest.lastChanged = source.lastChanged;
		}
		if (dest.providerAccountType == null)
		{
			dest.providerAccountType = source.providerAccountType;
		}
		return dest;
	}
}
