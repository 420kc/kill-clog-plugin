package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure record math for the local store: no state, no I/O. An own log holds
 * only what its account's client captured; a 2.4 file marked which of its
 * records were captured, and only those survive into an own log.
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
	 * A proven copy's first-party core: an own log as it stands; a 2.4 file
	 * cut to its marked records, each judged in its own category, so a mark
	 * never certifies a record it was not earned on. Pending unlocks keep only
	 * the account's own.
	 */
	static PlayerClogData ownCore(PlayerClogData data, String ownerHash)
	{
		Map<String, List<ClogResult.ClogItem>> kept = new HashMap<>();
		if (data.obtained != null)
		{
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : data.obtained.entrySet())
			{
				List<Integer> marks = data.firstPartyByCategory != null
					? data.firstPartyByCategory.get(e.getKey()) : null;
				List<ClogResult.ClogItem> items = new ArrayList<>();
				for (ClogResult.ClogItem item : e.getValue())
				{
					if (data.firstPartyByCategory == null || marks != null && marks.contains(item.getId()))
					{
						items.add(item);
					}
				}
				if (!items.isEmpty())
				{
					kept.put(e.getKey(), items);
				}
			}
		}
		data.obtained = kept;
		data.categories = data.categories != null ? new HashMap<>(data.categories) : new HashMap<>();
		data.firstPartyByCategory = null;
		data.pendingUnlocks = owned(data.pendingUnlocks, ownerHash);
		data.firstPartySetupComplete = hasCompletedFirstPartySetup(data);
		data.ownerHash = ownerHash;
		return data;
	}

	/**
	 * Two cores of one account become one. Every record is first-party, so the
	 * base wins where both hold an item; anything only the other knows (items,
	 * category ids, pending unlocks) carries over. Nothing is summed.
	 */
	static PlayerClogData mergeOwn(PlayerClogData base, PlayerClogData other)
	{
		for (Map.Entry<String, List<Integer>> e : other.categories.entrySet())
		{
			List<Integer> ids = new ArrayList<>(base.categories.getOrDefault(e.getKey(), new ArrayList<>()));
			for (Integer id : e.getValue())
			{
				if (!ids.contains(id))
				{
					ids.add(id);
				}
			}
			base.categories.put(e.getKey(), ids);
		}
		for (Map.Entry<String, List<ClogResult.ClogItem>> e : other.obtained.entrySet())
		{
			List<ClogResult.ClogItem> items = new ArrayList<>(base.obtained.getOrDefault(e.getKey(), new ArrayList<>()));
			for (ClogResult.ClogItem item : e.getValue())
			{
				if (items.stream().noneMatch(have -> have.getId() == item.getId()))
				{
					items.add(item);
				}
			}
			base.obtained.put(e.getKey(), items);
		}
		List<PendingClogUnlock> pending = base.pendingUnlocks != null ? new ArrayList<>(base.pendingUnlocks) : new ArrayList<>();
		for (PendingClogUnlock event : other.pendingUnlocks != null ? other.pendingUnlocks : new ArrayList<PendingClogUnlock>())
		{
			if (!pending.contains(event))
			{
				pending.add(event);
			}
		}
		while (pending.size() > 32)
		{
			pending.remove(0);
		}
		base.pendingUnlocks = pending.isEmpty() ? null : pending;
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
		base.firstPartySetupComplete = base.firstPartySetupComplete || other.firstPartySetupComplete;
		return base;
	}

	private static List<PendingClogUnlock> owned(List<PendingClogUnlock> events, String ownerHash)
	{
		if (events == null)
		{
			return null;
		}
		List<PendingClogUnlock> kept = new ArrayList<>();
		for (PendingClogUnlock event : events)
		{
			if (event != null && ownerHash.equals(event.ownerHash) && !kept.contains(event))
			{
				kept.add(event);
			}
		}
		return kept.isEmpty() ? null : kept;
	}
}
