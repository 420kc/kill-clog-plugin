package com.killclog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parsed collection log data for a player from the active provider.
 */
public class ClogResult
{
	/** Canonical player name with best-known capitalization from the provider */
	private final String playerName;
	/** category key -> list of obtained items with counts */
	private final Map<String, List<ClogItem>> obtainedItems;
	/** category key -> all item IDs in that category */
	private final Map<String, List<Integer>> categoryItems;
	/** item id -> display name, concurrent: written from client thread, read from EDT. */
	private final Map<Integer, String> itemNames;
	/** When the player last synced clog data, or null for providers without it */
	private final String lastChanged;
	/** Account type reported by the active provider, or null if unknown */
	private final AccountType providerAccountType;
	/** Game-reported unique obtained count (varp 2943), or -1 if unavailable */
	private int uniqueObtained = -1;
	/** Game-reported total clog slots (varp 2944), or -1 if unavailable */
	private int uniqueTotal = -1;

	public ClogResult(
		String playerName,
		Map<String, List<ClogItem>> obtainedItems,
		Map<String, List<Integer>> categoryItems,
		Map<Integer, String> itemNames,
		String lastChanged,
		AccountType providerAccountType)
	{
		this.playerName = playerName;
		this.obtainedItems = obtainedItems;
		this.categoryItems = categoryItems;
		this.itemNames = new ConcurrentHashMap<>();
		if (itemNames != null)
		{
			this.itemNames.putAll(itemNames);
		}
		this.lastChanged = lastChanged;
		this.providerAccountType = providerAccountType;
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

	public AccountType getProviderAccountType()
	{
		return providerAccountType;
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
		return itemNames.containsKey(id);
	}

	public void markItemResolved(int id)
	{
		itemNames.putIfAbsent(id, "Item " + id);
	}

	public void markItemResolved(int id, String name)
	{
		if (name == null || name.isBlank() || "null".equalsIgnoreCase(name))
		{
			return;
		}
		itemNames.put(id, name);
	}

	public String getItemName(int id)
	{
		return itemNames.get(id);
	}

	/**
	 * Compare two clog results and return whichever represents the most
	 * recent sync. Collection log items only accumulate, so the result
	 * with more obtained items is fresher. When counts are close (within 5),
	 * prefer TempleOSRS for its richer sync timestamp.
	 */
	public static ClogResult pickFreshest(ClogResult temple, ClogResult rp)
	{
		if (temple == null)
		{
			return rp;
		}
		if (rp == null)
		{
			return temple;
		}

		int templeCount = obtainedCount(temple);
		int rpCount = obtainedCount(rp);

		// RuneProfile has significantly more items, so it is clearly fresher.
		if (rpCount > templeCount + 5)
		{
			return rp.withFallbackAccountTypeFrom(temple);
		}
		// TempleOSRS wins ties and near-ties because it has lastChanged.
		return temple;
	}

	private ClogResult withFallbackAccountTypeFrom(ClogResult fallback)
	{
		if (fallback.providerAccountType == null || providerAccountType != null)
		{
			return this;
		}

		ClogResult merged = new ClogResult(
			playerName,
			obtainedItems,
			categoryItems,
			null,
			lastChanged,
			fallback.providerAccountType
		);
		merged.itemNames.putAll(itemNames);
		merged.uniqueObtained = uniqueObtained;
		merged.uniqueTotal = uniqueTotal;
		return merged;
	}

	private static int obtainedCount(ClogResult result)
	{
		if (result.uniqueObtained >= 0)
		{
			return result.uniqueObtained;
		}
		int count = 0;
		for (List<ClogItem> items : result.obtainedItems.values())
		{
			count += items.size();
		}
		return count;
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
