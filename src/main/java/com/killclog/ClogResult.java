package com.killclog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Parsed collection log data for a player from the active provider.
 */
public class ClogResult
{
	/** Canonical player name with best-known capitalization from the provider */
	@Getter
	private final String playerName;
	/** category key -> list of obtained items with counts */
	@Getter
	private final Map<String, List<ClogItem>> obtainedItems;
	/** category key -> all item IDs in that category */
	@Getter
	private final Map<String, List<Integer>> categoryItems;
	/** item id -> display name, concurrent: written from client thread, read from EDT. */
	private final Map<Integer, String> itemNames;
	/** When the player last synced clog data, or null for providers without it */
	@Getter
	private final String lastChanged;
	/** Account type reported by the active provider, or null if unknown */
	@Getter
	private final AccountType providerAccountType;
	/** Game-reported unique obtained count (varp 2943), or -1 if unavailable */
	@Getter
	@Setter
	private int uniqueObtained = -1;
	/** Game-reported total clog slots (varp 2944), or -1 if unavailable */
	@Getter
	@Setter
	private int uniqueTotal = -1;
	/** True when TempleOSRS returned data that fed this result */
	@Getter
	private boolean fromTemple;
	/** True when RuneProfile returned data that fed this result */
	@Getter
	private boolean fromRuneProfile;
	/** True when the player's own killclog.com sync returned data that fed this result */
	@Getter
	private boolean fromKillclog;

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

	/**
	 * Shallow combine copy: shares the item/category maps with the source
	 * (they are read-only after construction, except itemNames, whose shared
	 * concurrent map deliberately keeps the name-resolution cache warm across
	 * lookups). Only identity, account type, and provenance flags are per-copy.
	 */
	private ClogResult(ClogResult source, AccountType accountType)
	{
		this.playerName = source.playerName;
		this.obtainedItems = source.obtainedItems;
		this.categoryItems = source.categoryItems;
		this.itemNames = source.itemNames;
		this.lastChanged = source.lastChanged;
		this.providerAccountType = accountType;
		this.uniqueObtained = source.uniqueObtained;
		this.uniqueTotal = source.uniqueTotal;
	}

	/**
	 * Combine result carrying this data with per-combine provenance. Cached
	 * provider/killclog instances are shared across overlapping lookups whose
	 * legs can resolve differently (timeouts, cancellations), so combines
	 * must NEVER mutate them -- every combine returns its own copy.
	 */
	private ClogResult copyWithProvenance(boolean temple, boolean runeProfile, boolean killclog,
		AccountType fallbackType)
	{
		AccountType accountType = providerAccountType != null ? providerAccountType : fallbackType;
		return copyWithProvenanceAndAccountType(temple, runeProfile, killclog, accountType);
	}

	private ClogResult copyWithProvenanceAndAccountType(boolean temple, boolean runeProfile,
		boolean killclog, AccountType accountType)
	{
		ClogResult copy = new ClogResult(this, accountType);
		copy.fromTemple = temple;
		copy.fromRuneProfile = runeProfile;
		copy.fromKillclog = killclog;
		return copy;
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
		ClogResult winner = chooseFreshest(temple, rp);
		if (winner == null)
		{
			return null;
		}
		// Provenance tracks which providers returned non-null data, stamped
		// on a copy so the shared cached instances are never mutated.
		ClogResult loser = winner == temple ? rp : temple;
		return winner.copyWithProvenance(temple != null, rp != null, false,
			loser != null ? loser.providerAccountType : null);
	}

	/**
	 * Combine the provider winner with the player's own killclog.com sync.
	 * The rule is the website's, not {@link #pickFreshest}'s recency lean:
	 * the FULLEST result leads, and ties prefer first-party. A partial sync
	 * (six items from a fresh install) must never shrink a profile below
	 * what providers prove; a complete sync must never lose to a stale
	 * provider snapshot.
	 */
	public static ClogResult pickFullest(ClogResult provider, ClogResult killclog)
	{
		if (provider == null && killclog == null)
		{
			return null;
		}
		if (killclog == null)
		{
			// Copied even though the composed caller hands us pickFreshest's
			// own copy: this method must be safe on cached instances too.
			return provider.copyWithProvenance(
				provider.fromTemple, provider.fromRuneProfile, false, null);
		}
		if (provider == null)
		{
			return killclog.copyWithProvenance(false, false, true, null);
		}
		// The coverage race compares the same metric on both sides: distinct
		// itemized coverage. The varp counter describes the ACCOUNT, not the
		// payload - a six-item partial sync still carries the account's full
		// unique count, and letting it race on that number replays the shrink
		// bug against a complete provider log.
		if (coverageCount(killclog) >= coverageCount(provider))
		{
			return killclog.copyWithProvenance(
				provider.fromTemple, provider.fromRuneProfile, true,
				provider.providerAccountType);
		}
		AccountType firstPartyType = killclog.providerAccountType;
		AccountType accountType = firstPartyType != null && firstPartyType.isGroupIronman()
			? firstPartyType : provider.providerAccountType;
		return provider.copyWithProvenanceAndAccountType(
			provider.fromTemple, provider.fromRuneProfile, true, accountType);
	}

	/** Distinct itemized coverage, deliberately ignoring the varp counter. */
	private static int coverageCount(ClogResult result)
	{
		java.util.Set<Integer> distinct = new java.util.HashSet<>();
		for (List<ClogItem> items : result.obtainedItems.values())
		{
			for (ClogItem item : items)
			{
				distinct.add(item.getId());
			}
		}
		return distinct.size();
	}

	private static ClogResult chooseFreshest(ClogResult temple, ClogResult rp)
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
			return rp;
		}
		// TempleOSRS wins ties and near-ties because it has lastChanged.
		return temple;
	}

	private static int obtainedCount(ClogResult result)
	{
		if (result.uniqueObtained >= 0)
		{
			return result.uniqueObtained;
		}
		// Distinct ids, not a per-category sum: one item can sit in several
		// categories, and a duplicate-heavy partial result must not outcount
		// a genuinely fuller one.
		java.util.Set<Integer> distinct = new java.util.HashSet<>();
		for (List<ClogItem> items : result.obtainedItems.values())
		{
			for (ClogItem item : items)
			{
				distinct.add(item.getId());
			}
		}
		return distinct.size();
	}

	public static class ClogItem
	{
		private final int id;
		private final int count;
		private final String date;
		// Provenance captured at the live unlock moment, when the vanilla
		// kill-count message landed just before the clog message. 0 / null
		// mean unknown: provider data, chalice syncs, and pre-capture cache
		// files never carry these.
		private final int obtainedAtKc;
		private final String obtainedFrom;

		public ClogItem(int id, int count, String date)
		{
			this(id, count, date, 0, null);
		}

		public ClogItem(int id, int count, String date, int obtainedAtKc, String obtainedFrom)
		{
			this.id = id;
			this.count = count;
			this.date = date;
			this.obtainedAtKc = obtainedAtKc;
			this.obtainedFrom = obtainedFrom;
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

		public int getObtainedAtKc()
		{
			return obtainedAtKc;
		}

		public String getObtainedFrom()
		{
			return obtainedFrom;
		}
	}
}
