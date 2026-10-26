package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.clan.ClanTitle;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.With;

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
	/** TempleOSRS's EHP and EHC for the account's own flavour, or -1 when Temple gave none */
	@Getter
	@Setter
	private double templeEhp = -1;
	@Getter
	@Setter
	private double templeEhc = -1;
	/** The clan and title the player's own Kill Clog sync last named (2.6.3), or null */
	@Getter
	@Setter
	private String syncedClan;
	/** Whether their sync has said at all: then a null clan means "in none", not "unknown" */
	@Getter
	@Setter
	private boolean syncedClanKnown;
	@Getter
	@Setter
	private ClanTitle syncedClanTitle;
	/** True when TempleOSRS returned usable collection-log data for this lookup */
	@Getter
	private boolean fromTemple;
	/** True when RuneProfile returned usable collection-log data for this lookup */
	@Getter
	private boolean fromRuneProfile;
	/** True when the player's own killclog.com sync returned itemized clog data */
	@Getter
	private boolean fromKillclog;
	/** True when the result includes the local player's completed game capture. */
	@Getter
	private boolean fromLocal;

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
		this(source, accountType, source.obtainedItems, source.categoryItems);
	}

	private ClogResult(ClogResult source, AccountType accountType, Map<String, List<ClogItem>> obtainedItems,
		Map<String, List<Integer>> categoryItems)
	{
		this.playerName = source.playerName;
		this.obtainedItems = obtainedItems;
		this.categoryItems = categoryItems;
		this.itemNames = source.itemNames;
		this.lastChanged = source.lastChanged;
		this.providerAccountType = accountType;
		this.uniqueObtained = source.uniqueObtained;
		this.uniqueTotal = source.uniqueTotal;
		this.fromLocal = source.fromLocal;
		this.templeEhp = source.templeEhp;
		this.templeEhc = source.templeEhc;
		this.syncedClan = source.syncedClan;
		this.syncedClanKnown = source.syncedClanKnown;
		this.syncedClanTitle = source.syncedClanTitle;
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

	/**
	 * Stamp a result at the provider boundary without mutating a cached instance.
	 * Fanout combines may receive local fallback data in a provider-shaped slot,
	 * so argument position is never sufficient evidence of provenance.
	 */
	ClogResult withSources(boolean temple, boolean runeProfile, boolean killclog)
	{
		return copyWithProvenanceAndAccountType(
			temple, runeProfile, killclog, providerAccountType);
	}

	/**
	 * This log on the game's own pages: each page the game has lists the game's items and holds every item the log
	 * has, wherever the source filed it, as the game counts an item on every page that lists it. TempleOSRS files
	 * the Ring of 3rd Age under its Mimic and keeps the Rare pages' leftovers on the tier pages; on the game's pages
	 * neither shows. Pages the game doesn't have (Kill Clog's own 3rd Age, Gilded and Mimic, a provider's old page
	 * name) keep what the source sent, and a page the source never knew, no key and none of its items anywhere, stays
	 * unknown rather than empty. With no pages read yet it is this log as sent.
	 */
	ClogResult onPages(Map<String, List<Integer>> pages, IntUnaryOperator canonical)
	{
		if (pages.isEmpty())
		{
			return this;
		}
		Map<Integer, ClogItem> held = new HashMap<>();
		for (List<ClogItem> items : obtainedItems.values())
		{
			for (ClogItem item : items)
			{
				int id = canonical.applyAsInt(item.id);
				ClogItem as = item.withId(id);
				held.merge(id, as, (a, b) -> b.count > a.count || b.count == a.count && a.date == null ? b : a);
			}
		}
		Set<Integer> listed = new HashSet<>(held.keySet());
		for (List<Integer> ids : categoryItems.values())
		{
			for (int id : ids)
			{
				listed.add(canonical.applyAsInt(id));
			}
		}
		Map<String, List<ClogItem>> obtained = new HashMap<>(obtainedItems);
		Map<String, List<Integer>> categories = new HashMap<>(categoryItems);
		pages.forEach((page, ids) ->
		{
			if (!categoryItems.containsKey(page) && ids.stream().noneMatch(id -> listed.contains(canonical.applyAsInt(id))))
			{
				return;
			}
			List<ClogItem> onPage = new ArrayList<>();
			for (int id : ids)
			{
				ClogItem item = held.get(canonical.applyAsInt(id));
				if (item != null)
				{
					onPage.add(item);
				}
			}
			categories.put(page, ids);
			if (onPage.isEmpty())
			{
				obtained.remove(page);
			}
			else
			{
				obtained.put(page, onPage);
			}
		});
		ClogResult copy = new ClogResult(this, providerAccountType, obtained, categories);
		copy.fromTemple = fromTemple;
		copy.fromRuneProfile = fromRuneProfile;
		copy.fromKillclog = fromKillclog;
		return copy;
	}

	ClogResult withLocalSource(boolean local)
	{
		ClogResult copy = withSources(fromTemple, fromRuneProfile, fromKillclog);
		copy.fromLocal = local;
		return copy;
	}

	/** Keeps TempleOSRS's figures and the synced clan when the other side of a combine carried them and this didn't. */
	private ClogResult carryFrom(ClogResult other)
	{
		if (other != null && templeEhp < 0 && templeEhc < 0)
		{
			templeEhp = other.templeEhp;
			templeEhc = other.templeEhc;
		}
		if (other != null && !syncedClanKnown)
		{
			syncedClan = other.syncedClan;
			syncedClanKnown = other.syncedClanKnown;
			syncedClanTitle = other.syncedClanTitle;
		}
		return this;
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
		// Provenance comes from provider-stamped inputs, never argument position:
		// the Temple-shaped leg can legitimately contain unmarked local data.
		ClogResult loser = winner == temple ? rp : temple;
		return winner.copyWithProvenance(either(temple, rp, ClogResult::isFromTemple),
			either(temple, rp, ClogResult::isFromRuneProfile), either(temple, rp, ClogResult::isFromKillclog),
			loser != null ? loser.providerAccountType : null)
			.withLocalSource(either(temple, rp, ClogResult::isFromLocal)).carryFrom(loser);
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
				provider.fromTemple, provider.fromRuneProfile, provider.fromKillclog, null);
		}
		if (provider == null)
		{
			return killclog.copyWithProvenance(
				killclog.fromTemple, killclog.fromRuneProfile, killclog.fromKillclog, null);
		}
		boolean templeSource = either(provider, killclog, ClogResult::isFromTemple);
		boolean runeProfileSource = either(provider, killclog, ClogResult::isFromRuneProfile);
		boolean killclogSource = either(provider, killclog, ClogResult::isFromKillclog);
		// The coverage race compares the same metric on both sides: distinct
		// itemized coverage. The varp counter describes the ACCOUNT, not the
		// payload - a six-item partial sync still carries the account's full
		// unique count, and letting it race on that number replays the shrink
		// bug against a complete provider log.
		if (coverageCount(killclog) >= coverageCount(provider))
		{
			return killclog.copyWithProvenance(
				templeSource, runeProfileSource, killclogSource,
				provider.providerAccountType).withLocalSource(either(provider, killclog, ClogResult::isFromLocal)).carryFrom(provider);
		}
		AccountType firstPartyType = killclog.providerAccountType;
		AccountType accountType = firstPartyType != null && firstPartyType.isGroupIronman()
			? firstPartyType : provider.providerAccountType;
		return provider.copyWithProvenanceAndAccountType(
			templeSource, runeProfileSource, killclogSource, accountType)
			.withLocalSource(either(provider, killclog, ClogResult::isFromLocal)).carryFrom(killclog);
	}

	/** A merge carries a source when either side came from it. */
	private static boolean either(ClogResult first, ClogResult second, Predicate<ClogResult> source)
	{
		return first != null && source.test(first) || second != null && source.test(second);
	}

	/** A page-keyed map with each page's list its own copy, so the copy and the original never share a list. */
	static <T> Map<String, List<T>> copyPages(Map<String, List<T>> pages)
	{
		Map<String, List<T>> copy = new HashMap<>();
		pages.forEach((page, items) -> copy.put(page, new ArrayList<>(items)));
		return copy;
	}

	/** Distinct itemized coverage, deliberately ignoring the varp counter. */
	static int coverageCount(ClogResult result)
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

	static ClogResult chooseFreshest(ClogResult temple, ClogResult rp)
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
		// Distinct ids, not a per-category sum: one item can sit in several
		// categories, and a duplicate-heavy partial result must not outcount
		// a genuinely fuller one.
		return result.uniqueObtained >= 0 ? result.uniqueObtained : coverageCount(result);
	}

	@RequiredArgsConstructor
	@Getter
	@EqualsAndHashCode
	@With
	public static class ClogItem
	{
		private final int id;
		private final int count;
		private final String date;
		// Provenance captured at the live unlock moment, when the vanilla
		// kill-count message landed just before the clog message. 0 / null
		// mean unknown: provider data, full log captures, and pre-capture cache
		// files never carry these.
		private final int obtainedAtKc;
		private final String obtainedFrom;

		public ClogItem(int id, int count, String date)
		{
			this(id, count, date, 0, null);
		}

		/**
		 * This record with what it doesn't know taken from an earlier record of the item: a later reading never
		 * loses an earlier one's date, kill count or source.
		 */
		ClogItem filledFrom(ClogItem prior)
		{
			return prior == null ? this : new ClogItem(id, count, date != null ? date : prior.date,
				obtainedAtKc > 0 ? obtainedAtKc : prior.obtainedAtKc, obtainedFrom != null ? obtainedFrom : prior.obtainedFrom);
		}

	}
}
