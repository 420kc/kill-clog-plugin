package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure record math for the local store: no state, no I/O, just the
 * provenance rules for {@link PlayerClogData} and the merge that joins two
 * proven copies of one account's log.
 */
final class ClogRecords
{
	private ClogRecords()
	{
	}

	static boolean hasFirstPartyMarks(PlayerClogData data)
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

	/** Only an explicit completed Collection Log Search counts; marks alone never do. */
	static boolean hasCompletedFirstPartySetup(PlayerClogData data)
	{
		return data != null && Boolean.TRUE.equals(data.firstPartySetupComplete);
	}

	/**
	 * Two proven copies of one account's log become one, provenance first. Per
	 * category and item: a record marked first-party in that category beats an
	 * unmarked one whichever copy holds it, equal provenance keeps the base, and
	 * a mark only ever travels with the record it was earned on. Nothing is
	 * summed and nothing is discarded; the losing copy's file is archived.
	 */
	static PlayerClogData mergeOwn(PlayerClogData base, PlayerClogData other, String ownerHash)
	{
		base.categories = base.categories != null ? new HashMap<>(base.categories) : new HashMap<>();
		base.obtained = base.obtained != null ? new HashMap<>(base.obtained) : new HashMap<>();
		base.firstPartyByCategory = base.firstPartyByCategory != null
			? new HashMap<>(base.firstPartyByCategory) : new HashMap<>();
		if (other.categories != null)
		{
			for (Map.Entry<String, List<Integer>> e : other.categories.entrySet())
			{
				base.categories.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		if (other.obtained != null)
		{
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : other.obtained.entrySet())
			{
				String category = e.getKey();
				List<ClogResult.ClogItem> items = new ArrayList<>(
					base.obtained.getOrDefault(category, Collections.emptyList()));
				List<Integer> marks = new ArrayList<>(
					base.firstPartyByCategory.getOrDefault(category, Collections.emptyList()));
				List<Integer> theirMarks = other.firstPartyByCategory != null
					? other.firstPartyByCategory.getOrDefault(category, Collections.emptyList())
					: Collections.emptyList();
				for (ClogResult.ClogItem item : e.getValue())
				{
					int id = item.getId();
					int at = indexOf(items, id);
					boolean theirsMarked = theirMarks.contains(id);
					if (at < 0 || (theirsMarked && !marks.contains(id)))
					{
						if (at < 0)
						{
							items.add(item);
						}
						else
						{
							items.set(at, item);
						}
						if (theirsMarked && !marks.contains(id))
						{
							marks.add(id);
						}
					}
				}
				base.obtained.put(category, items);
				base.firstPartyByCategory.put(category, marks);
			}
		}
		if (other.pendingUnlocks != null)
		{
			List<PendingClogUnlock> pending = base.pendingUnlocks != null
				? new ArrayList<>(base.pendingUnlocks) : new ArrayList<>();
			for (PendingClogUnlock event : other.pendingUnlocks)
			{
				if (event != null && ownerHash.equals(event.ownerHash) && !pending.contains(event))
				{
					pending.add(event);
				}
			}
			while (pending.size() > 32)
			{
				pending.remove(0);
			}
			base.pendingUnlocks = pending;
		}
		base.uniqueObtained = Math.max(base.uniqueObtained, other.uniqueObtained);
		base.uniqueTotal = Math.max(base.uniqueTotal, other.uniqueTotal);
		if (base.lastChanged == null
			|| (other.lastChanged != null && other.lastChanged.compareTo(base.lastChanged) > 0))
		{
			base.lastChanged = other.lastChanged;
		}
		if (base.providerAccountType == null)
		{
			base.providerAccountType = other.providerAccountType;
		}
		base.firstPartySetupComplete = hasCompletedFirstPartySetup(base) || hasCompletedFirstPartySetup(other);
		return base;
	}

	private static int indexOf(List<ClogResult.ClogItem> items, int id)
	{
		for (int i = 0; i < items.size(); i++)
		{
			if (items.get(i).getId() == id)
			{
				return i;
			}
		}
		return -1;
	}
}
