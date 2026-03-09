package com.killclog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parsed collection log data for a player from TempleOSRS.
 */
public class ClogResult
{
	/** Canonical player name with correct capitalization from TempleOSRS */
	private final String playerName;
	/** category key -> list of obtained items with counts */
	private final Map<String, List<ClogItem>> obtainedItems;
	/** category key -> all item IDs in that category */
	private final Map<String, List<Integer>> categoryItems;
	/** item IDs whose names have been resolved (concurrent: written from client thread, read from EDT) */
	private final Set<Integer> resolvedItemIds;
	/** When the player last synced clog data to TempleOSRS (from last_changed field) */
	private final String lastChanged;
	/** Game-reported unique obtained count (varp 2943), or -1 if unavailable */
	private int uniqueObtained = -1;
	/** Game-reported total clog slots (varp 2944), or -1 if unavailable */
	private int uniqueTotal = -1;

	public ClogResult(
		String playerName,
		Map<String, List<ClogItem>> obtainedItems,
		Map<String, List<Integer>> categoryItems,
		Map<Integer, String> itemNames,
		String lastChanged)
	{
		this.playerName = playerName;
		this.obtainedItems = obtainedItems;
		this.categoryItems = categoryItems;
		this.resolvedItemIds = ConcurrentHashMap.newKeySet();
		if (itemNames != null)
		{
			resolvedItemIds.addAll(itemNames.keySet());
		}
		this.lastChanged = lastChanged;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public String getLastChanged()
	{
		return lastChanged;
	}

	public Map<String, List<ClogItem>> getObtainedItems()
	{
		return obtainedItems;
	}

	public Map<String, List<Integer>> getCategoryItems()
	{
		return categoryItems;
	}

	public int getUniqueObtained()
	{
		return uniqueObtained;
	}

	public void setUniqueObtained(int count)
	{
		this.uniqueObtained = count;
	}

	public int getUniqueTotal()
	{
		return uniqueTotal;
	}

	public void setUniqueTotal(int count)
	{
		this.uniqueTotal = count;
	}

	public boolean isItemResolved(int id)
	{
		return resolvedItemIds.contains(id);
	}

	public void markItemResolved(int id)
	{
		resolvedItemIds.add(id);
	}

	public static class ClogItem
	{
		private final int id;
		private final int count;
		private final String date;

		public ClogItem(int id, int count, String date)
		{
			this.id = id;
			this.count = count;
			this.date = date;
		}

		public int getId()
		{
			return id;
		}

		public int getCount()
		{
			return count;
		}

		public String getDate()
		{
			return date;
		}
	}
}
