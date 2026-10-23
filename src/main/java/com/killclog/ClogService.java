package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Fetches the TempleOSRS collection-log provider lane and item names from OSRS Wiki.
 * Category definitions and item names are loaded once per session and reused.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ClogService
{
	private static final String TEMPLE_CATEGORIES_URL =
		"https://templeosrs.com/api/collection-log/categories.php";
	private static final String TEMPLE_PLAYER_URL =
		"https://templeosrs.com/api/collection-log/player_collection_log.php";
	private static final String TEMPLE_STATS_URL =
		"https://templeosrs.com/api/player_stats.php";

	// Boss name to TempleOSRS category key overrides, from the bundled
	// catalog; the parity test pins its exact contents.
	private static final Map<String, String> BOSS_CATEGORY_OVERRIDES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	static
	{
		for (String[] row : CatalogTsv.rows(ClogService.class, "clog-category-overrides.tsv", 2))
		{
			BOSS_CATEGORY_OVERRIDES.put(row[0], row[1]);
		}
	}

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalClogCache localClogCache;

	// Cached data loaded once per session.
	private volatile Map<String, List<Integer>> cachedCategories;
	// Item names come from the game cache, through the log's own index (the plugin hands it over at start).
	private volatile ClogIndex clogIndex;

	// In-flight futures prevent duplicate HTTP requests from concurrent callers.
	private final Map<String, CompletableFuture<Map<String, List<Integer>>>> categoriesFlight = new ConcurrentHashMap<>();

	// Failure cooldown skips TempleOSRS briefly after transient errors.
	private static final long TEMPLE_FAILURE_TTL_MS = 3 * 60 * 1000;
	private final Map<String, Long> templeFailures = new ConcurrentHashMap<>();

	// Freshness gate skips TempleOSRS when the cache is still hot.
	private static final long CLOG_TTL_MS = 5 * 60 * 1000; // 5 minutes
	// How long your own log waits for TempleOSRS's dates before it shows without them; inside every source's 7 s.
	static long ownLogDatesWaitMs = 2000;
	private final Map<String, Long> clogFetchTimes = new ConcurrentHashMap<>();
	// A date-overlay attempt is recorded before HTTP; keep successful Temple
	// proof separately so the active-player result stays attributed during TTL.
	private final Map<String, Long> templeOverlaySuccessTimes =
		new ConcurrentHashMap<>();
	// Your own Temple EHP and EHC, from the date overlay's answers: your log is the game's, Temple adds only these.
	private final Map<String, double[]> ownTempleFigures = new ConcurrentHashMap<>();
	// Anyone else's, from their last Temple answer: a repeat lookup inside the TTL rebuilds from the local cache,
	// which keeps the log but not these. Pruned with clogFetchTimes.
	private final Map<String, double[]> lookedUpTempleFigures = new ConcurrentHashMap<>();
	// Players TempleOSRS answered with a log in the last day: the Collection Log card credits Temple even when this
	// lookup's own request was skipped, as for your own log, or timed out.
	private static final long TEMPLE_HOLDS_TTL_MS = 24 * 60 * 60 * 1000;
	private final Map<String, Long> templeHolds = new ConcurrentHashMap<>();

	/** Whether TempleOSRS has answered with this player's log in the last day. */
	boolean onTemple(@Nullable String playerName)
	{
		Long at = playerName != null ? templeHolds.get(RsnInputPolicy.key(playerName)) : null;
		return at != null && System.currentTimeMillis() - at < TEMPLE_HOLDS_TTL_MS;
	}

	private void heldByTemple(String normalizedName)
	{
		templeHolds.put(normalizedName, System.currentTimeMillis());
		HttpUtil.prune(templeHolds, TEMPLE_HOLDS_TTL_MS);
	}

	/** Clear all failure cooldowns immediately (called on login). TTL handles auto-expiry otherwise. */
	public void clearTempleFailures()
	{
		templeFailures.clear();
	}

	/**
	 * Convert a boss name to its TempleOSRS collection log category key.
	 */
	public static String bossToCategory(String bossName)
	{
		String override = BOSS_CATEGORY_OVERRIDES.get(bossName);
		if (override != null)
		{
			return override;
		}
		return bossName.toLowerCase().replace("'", "")
			.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	/**
	 * Get a cached ClogResult immediately without any API calls.
	 * Returns null if no local cache exists. Used for SWR rendering.
	 */
	public ClogResult getCachedResult(String playerName)
	{
		if (!localClogCache.hasDataFor(playerName))
		{
			return null;
		}
		if (localClogCache.isActivePlayer(playerName)
			&& !localClogCache.hasCompletedFirstPartySetupFor(playerName))
		{
			return null;
		}
		Map<Integer, String> names = itemNames();
		ClogResult result = logFrom(localClogCache, playerName, names);
		// Temple's EHP and EHC ride along as a lookup attaches them, under the same freshness, so a log rebuilt
		// here (a local capture, the log opened) keeps the Experience & Efficiency card's figures.
		String key = RsnInputPolicy.key(playerName);
		if (result != null && localClogCache.isActivePlayer(playerName))
		{
			result = result.withLocalSource(true);
			Long successfulAt = templeOverlaySuccessTimes.get(key);
			if (successfulAt != null && System.currentTimeMillis() - successfulAt < CLOG_TTL_MS)
			{
				result = markTempleSource(result);
				setTempleFigures(result, ownTempleFigures.get(key));
			}
		}
		else
		{
			Long lastFetch = clogFetchTimes.get(key);
			if (lastFetch != null && System.currentTimeMillis() - lastFetch < CLOG_TTL_MS)
			{
				setTempleFigures(result, lookedUpTempleFigures.get(key));
			}
		}
		return result;
	}

	/** A player's log from a store, its items named with the names loaded so far. */
	@Nullable
	private static ClogResult logFrom(LocalClogCache cache, String playerName, @Nullable Map<Integer, String> names)
	{
		return cache.toClogResult(playerName, names != null ? names : new HashMap<>());
	}

	/** A League's own local log for the active player, once item names have loaded. */
	CompletableFuture<ClogResult> lookupLocal(LocalClogCache cache, String playerName)
	{
		return fetchItemNames().thenApply(names -> localResult(cache, playerName));
	}

	/** A League's own local log for the active player, now. League logs never take provider data. */
	@Nullable
	ClogResult localResult(LocalClogCache cache, String playerName)
	{
		if (!cache.isActivePlayer(playerName) || !cache.hasDataFor(playerName))
		{
			return null;
		}
		Map<Integer, String> names = itemNames();
		ClogResult result = logFrom(cache, playerName, names);
		return result != null ? result.withLocalSource(true) : null;
	}

	/** Whether the main store serves this name's own log: the logged-in player, once their login reaches the main game. */
	boolean keepsOwnLog(String playerName)
	{
		return localClogCache.isActivePlayer(playerName);
	}

	/**
	 * Look up this service's TempleOSRS collection-log lane for a player.
	 * Active player with widget-read data is served from cache (authoritative).
	 * Public lookup orchestration races this with RuneProfile and picks the fresher result.
	 */
	public CompletableFuture<ClogResult> lookup(String playerName)
	{
		// Active player: local cache is authoritative.
		if (localClogCache.isActivePlayer(playerName))
		{
			if (localClogCache.hasDataFor(playerName)
				&& localClogCache.hasCompletedFirstPartySetupFor(playerName))
			{
				log.debug("Using local clog cache for active player: {}", playerName);
				// Dates-only provider overlay before serving: heals undated
				// cache entries (pre-dating live merges, or unlocks while the
				// plugin was off) so the recents shelf stays current. TTL-gated
				// and failure-proof; counts and membership stay chalice-owned.
				// Your own log never waits long for the dates: past a short wait it serves without the overlay, which
				// still lands for the next look (Dylan, 10/23: a date check longer than every source's 7 s timed the
				// whole log out at login, and the panel asked for setup over a saved log).
				return overlayProviderDates(playerName).copy()
					.completeOnTimeout(false, ownLogDatesWaitMs, TimeUnit.MILLISECONDS)
					.thenCompose(fromTemple -> fetchItemNames().thenApply(names ->
					{
						ClogResult result = logFrom(localClogCache, playerName, names);
						if (result != null)
						{
							result = result.withLocalSource(true);
						}
						setTempleFigures(result, fromTemple ? ownTempleFigures.get(RsnInputPolicy.key(playerName)) : null);
						return fromTemple ? markTempleSource(result) : result;
					}));
			}
			// No local cache yet, so the panel shows the sync prompt.
			return CompletableFuture.completedFuture(null);
		}

		// Fresh data: skip the TempleOSRS lane if we fetched recently.
		String normalizedName = RsnInputPolicy.key(playerName);
		Long lastFetch = clogFetchTimes.get(normalizedName);
		if (lastFetch != null && System.currentTimeMillis() - lastFetch < CLOG_TTL_MS
			&& localClogCache.hasDataFor(playerName))
		{
			log.debug("Using fresh cached clog for '{}' ({}s old)",
				playerName, (System.currentTimeMillis() - lastFetch) / 1000);
			return fetchItemNames().thenApply(names ->
			{
				ClogResult result = logFrom(localClogCache, playerName, names);
				setTempleFigures(result, lookedUpTempleFigures.get(normalizedName));
				return markTempleSource(result);
			});
		}

		// Skip TempleOSRS if it failed for this player within the cooldown window.
		Long failedAt = templeFailures.get(normalizedName);
		if (failedAt != null && System.currentTimeMillis() - failedAt < TEMPLE_FAILURE_TTL_MS)
		{
			if (localClogCache.hasDataFor(playerName))
			{
				return fetchItemNames().thenApply(names ->
					logFrom(localClogCache, playerName, names));
			}
			return CompletableFuture.completedFuture(null);
		}

		// Build the TempleOSRS lane, with local cache as fallback.
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);

		CompletableFuture<PlayerClogData> playerFuture =
			fetchClog(encoded);
		CompletableFuture<Map<String, List<Integer>>> categoriesFuture =
			fetchCategories();
		CompletableFuture<Map<Integer, String>> namesFuture =
			fetchItemNames();
		CompletableFuture<TempleStats> statsFuture =
			fetchStats(encoded);

		return CompletableFuture.allOf(playerFuture, categoriesFuture, namesFuture, statsFuture)
			.thenApply(v ->
			{
				PlayerClogData playerData = playerFuture.join();
				Map<String, List<Integer>> categories = categoriesFuture.join();
				Map<Integer, String> names = namesFuture.join();
				TempleStats stats = statsFuture.join();

				if (playerData != null)
				{
					AccountType accountType = templeType(playerData, stats);

					ClogResult result = new ClogResult(
						playerData.canonicalName,
						playerData.obtainedItems,
						categories != null ? categories : new HashMap<>(),
						names != null ? names : new HashMap<>(),
						playerData.lastChanged,
						accountType
					).withSources(true, false, false);
					double[] figures = templeFigures(playerData, stats);
					setTempleFigures(result, figures);
					localClogCache.cacheResult(result);
					heldByTemple(normalizedName);
					clogFetchTimes.put(normalizedName, System.currentTimeMillis());
					HttpUtil.prune(clogFetchTimes, CLOG_TTL_MS);
					lookedUpTempleFigures.put(normalizedName, figures);
					lookedUpTempleFigures.keySet().retainAll(clogFetchTimes.keySet());
					return result;
				}

				// TempleOSRS lane failed. Remember timestamp and fall back to local cache.
				templeFailures.put(normalizedName, System.currentTimeMillis());
				HttpUtil.prune(templeFailures, TEMPLE_FAILURE_TTL_MS);
				if (localClogCache.hasDataFor(playerName))
				{
					log.debug("TempleOSRS unavailable, using cached data for: {}", playerName);
					return logFrom(localClogCache, playerName, names);
				}

				return null;
			});
	}

	/** The clog endpoint reports game_mode 0 for GIMs: the stats endpoint's type wins when the clog's says nothing more. */
	private static AccountType templeType(PlayerClogData player, @Nullable TempleStats stats)
	{
		AccountType type = player.accountType;
		return stats != null && stats.accountType != null && (type == null || type == AccountType.REGULAR)
			? stats.accountType : type;
	}

	/** Temple's EHP and EHC for the account's flavour, from its stats and collection log answers. */
	private static double[] templeFigures(PlayerClogData player, @Nullable TempleStats stats)
	{
		return new double[]{stats != null ? stats.ehp : -1, TempleStats.ehc(player.figures, templeType(player, stats))};
	}

	private static void setTempleFigures(@Nullable ClogResult result, @Nullable double[] figures)
	{
		if (result != null && figures != null)
		{
			result.setTempleEhp(figures[0]);
			result.setTempleEhc(figures[1]);
		}
	}

	private static ClogResult markTempleSource(ClogResult result)
	{
		return result != null ? result.withSources(true, false, false) : null;
	}

	private static class PlayerClogData
	{
		final String canonicalName;
		final Map<String, List<ClogResult.ClogItem>> obtainedItems;
		final String lastChanged;
		final AccountType accountType;
		/** The answer's data, for its figures (EHC). */
		final JsonObject figures;

		PlayerClogData(String canonicalName, Map<String, List<ClogResult.ClogItem>> obtainedItems,
			String lastChanged, AccountType accountType, JsonObject figures)
		{
			this.figures = figures;
			this.canonicalName = canonicalName;
			this.obtainedItems = obtainedItems;
			this.lastChanged = lastChanged;
			this.accountType = accountType;
		}
	}

	/**
	 * Parse TempleOSRS game_mode into our AccountType.
	 * Returns null if the mode is unrecognized or absent.
	 */
	private static AccountType parseGameMode(String gameMode)
	{
		return AccountType.fromTempleGameMode(gameMode);
	}

	/**
	 * Fetch the provider's dated view of the active player and stamp missing
	 * dates onto the local cache. Shares the clog TTL so a self-search never
	 * fans out. The return value records a usable Temple collection-log
	 * response, even when its dates were already present locally; failures and
	 * Temple's unsynced shape resolve false so local data remains unmarked.
	 */
	private CompletableFuture<Boolean> overlayProviderDates(String playerName)
	{
		String normalizedName = RsnInputPolicy.key(playerName);
		String ttlKey = "dates:" + normalizedName;
		long now = System.currentTimeMillis();
		Long lastFetch = clogFetchTimes.get(ttlKey);
		if (lastFetch != null && now - lastFetch < CLOG_TTL_MS)
		{
			Long successfulAt = templeOverlaySuccessTimes.get(normalizedName);
			return CompletableFuture.completedFuture(successfulAt != null
				&& now - successfulAt < CLOG_TTL_MS);
		}
		clogFetchTimes.put(ttlKey, now);
		HttpUtil.prune(clogFetchTimes, CLOG_TTL_MS);
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
		// Your stats ride with the dates, under the same limit, for the Experience & Efficiency card's EHP.
		CompletableFuture<TempleStats> stats = fetchStats(encoded).exceptionally(t -> null);
		return fetchClog(encoded)
			.thenCombine(stats, (player, yourStats) ->
			{
				if (player == null)
				{
					templeOverlaySuccessTimes.remove(normalizedName);
					ownTempleFigures.remove(normalizedName);
					return false;
				}
				ownTempleFigures.put(normalizedName, templeFigures(player, yourStats));
				if (player.obtainedItems != null)
				{
					localClogCache.mergeProviderDates(playerName, player.obtainedItems);
				}
				templeOverlaySuccessTimes.put(normalizedName, System.currentTimeMillis());
				HttpUtil.prune(templeOverlaySuccessTimes, CLOG_TTL_MS);
				heldByTemple(normalizedName);
				return true;
			})
			.exceptionally(t ->
			{
				templeOverlaySuccessTimes.remove(normalizedName);
				ownTempleFigures.remove(normalizedName);
				log.debug("Provider date overlay failed for '{}'", playerName, t);
				return false;
			});
	}

	private CompletableFuture<PlayerClogData> fetchClog(String encodedPlayer)
	{
		String url = TEMPLE_PLAYER_URL + "?player=" + encodedPlayer + "&categories=all";
		return httpGet(url).thenApply(json ->
		{
			if (json == null)
			{
				return null;
			}
			try
			{
				JsonObject root = gson.fromJson(json, JsonObject.class);
				JsonObject data = root.getAsJsonObject("data");
				if (data == null || !data.has("items"))
				{
					return null;
				}

				String canonicalName = null;
				if (data.has("player_name_with_capitalization"))
				{
					canonicalName = data.get("player_name_with_capitalization").getAsString();
				}

				String lastChanged = null;
				if (data.has("last_changed"))
				{
					lastChanged = data.get("last_changed").getAsString();
				}

				AccountType accountType = null;
				if (data.has("game_mode") && !data.get("game_mode").isJsonNull())
				{
					accountType = parseGameMode(data.get("game_mode").getAsString());
				}

				JsonObject itemsObj = data.getAsJsonObject("items");
				Map<String, List<ClogResult.ClogItem>> result = new HashMap<>();

				for (Map.Entry<String, JsonElement> entry : itemsObj.entrySet())
				{
					String category = entry.getKey();
					JsonArray items = entry.getValue().getAsJsonArray();
					List<ClogResult.ClogItem> itemList = new ArrayList<>();

					for (JsonElement item : items)
					{
						JsonObject obj = item.getAsJsonObject();
						int id = obj.get("id").getAsInt();
						int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
						String date = obj.has("date") ? obj.get("date").getAsString() : null;
						itemList.add(new ClogResult.ClogItem(id, count, date));
					}

					result.put(category, itemList);
				}

				// A player TempleOSRS tracks from hiscores but who never synced a
				// collection log returns zero obtained items and no last_changed.
				// Treat that as no clog data, not a real 0% profile: return null so
				// the provider fanout can fall back to RuneProfile, and the panel
				// shows the unknown state instead of painting every boss as empty.
				int obtainedTotal = 0;
				for (List<ClogResult.ClogItem> categoryItems : result.values())
				{
					obtainedTotal += categoryItems.size();
				}
				if (obtainedTotal == 0 && lastChanged == null)
				{
					return null;
				}

				return new PlayerClogData(canonicalName, result, lastChanged, accountType, data);
			}
			catch (Exception e)
			{
				log.debug("Failed to parse player clog: {}", e.getMessage());
				return null;
			}
		});
	}

	/** True once the global category catalog has loaded. */
	public boolean hasCatalog()
	{
		return cachedCategories != null;
	}

	/** Every category key in the loaded catalog, or empty when it hasn't loaded. */
	public java.util.Set<String> getCatalogCategoryKeys()
	{
		Map<String, List<Integer>> cats = cachedCategories;
		return cats != null ? new java.util.HashSet<>(cats.keySet()) : java.util.Collections.emptySet();
	}

	/**
	 * Canonical item ids for a category from the cached global definitions,
	 * or null when the catalog hasn't loaded or the category is unknown.
	 * The catalog's own list, shared by every result like the game index's pages: never changed in place.
	 */
	@Nullable
	public List<Integer> getCategoryCatalogIds(String category)
	{
		Map<String, List<Integer>> cats = cachedCategories;
		if (cats == null)
		{
			return null;
		}
		return cats.get(category);
	}

	/**
	 * Prefetch the category and item-name catalog so the panel can preview
	 * the log's shape before any player has been looked up.
	 */
	public CompletableFuture<Void> warmCatalog()
	{
		return CompletableFuture.allOf(fetchCategories(), fetchItemNames());
	}

	@Nullable
	public ClogResult getCatalogResult(String playerName)
	{
		Map<String, List<Integer>> cats = cachedCategories;
		if (cats == null)
		{
			return null;
		}

		// Every page, none of it obtained.
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		cats.keySet().forEach(page -> obtained.put(page, Collections.emptyList()));
		return new ClogResult(playerName, obtained, ClogResult.copyPages(cats), new HashMap<>(itemNames()), null, null);
	}

	private CompletableFuture<Map<String, List<Integer>>> fetchCategories()
	{
		return fetchOnce(() -> cachedCategories, categoriesFlight, TEMPLE_CATEGORIES_URL, json ->
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			Type type = new TypeToken<Map<String, List<Integer>>>()
			{
			}.getType();
			Map<String, List<Integer>> categories = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : root.entrySet())
			{
				if (entry.getValue().isJsonObject())
				{
					Map<String, List<Integer>> sectionMap = gson.fromJson(entry.getValue(), type);
					if (sectionMap != null)
					{
						categories.putAll(sectionMap);
					}
				}
			}
			return categories.isEmpty() ? null : categories;
		}, categories -> cachedCategories = categories);
	}

	void setClogIndex(ClogIndex clogIndex)
	{
		this.clogIndex = clogIndex;
	}

	/** The game cache's names for every log item, now; empty before the catalog is parsed. */
	private Map<Integer, String> itemNames()
	{
		ClogIndex index = clogIndex;
		return index != null ? index.itemNames() : Collections.emptyMap();
	}

	/** The names as a lookup's next step takes them: there is nothing to wait for, so it is already done. */
	private CompletableFuture<Map<Integer, String>> fetchItemNames()
	{
		return CompletableFuture.completedFuture(itemNames());
	}

	/**
	 * A file every lookup shares, fetched once at a time: what parse makes of it is kept, and a failure (no answer,
	 * or a parse that throws or gives null) leaves nothing kept, so the next ask fetches again. Rechecked inside the
	 * flight so a fetch that just landed is never repeated.
	 */
	private <T> CompletableFuture<T> fetchOnce(Supplier<T> held, Map<String, CompletableFuture<T>> flight, String url,
		Function<String, T> parse, Consumer<T> keep)
	{
		T now = held.get();
		return now != null ? CompletableFuture.completedFuture(now) : HttpUtil.singleFlightLookup(flight, "", () ->
		{
			T landed = held.get();
			return landed != null ? CompletableFuture.completedFuture(landed) : httpGet(url).thenApply(json ->
			{
				try
				{
					T value = json != null ? parse.apply(json) : null;
					if (value != null)
					{
						keep.accept(value);
					}
					return value;
				}
				catch (Exception e)
				{
					log.debug("Failed to parse {}: {}", url, e.getMessage());
					return null;
				}
			});
		});
	}

	/**
	 * Fetch only the canonical player name from TempleOSRS player stats.
	 * Lightweight identity fallback when collection-log data is unavailable.
	 */
	public CompletableFuture<String> lookupRsn(String playerName)
	{
		return fetchStats(URLEncoder.encode(playerName, StandardCharsets.UTF_8))
			.thenApply(stats -> stats != null ? stats.canonicalName : null);
	}

	/**
	 * One player's TempleOSRS stats: the account type (the clog endpoint reports game_mode 0 for GIMs, the stats
	 * endpoint names the group), the name and EHP. Null when the request or the response comes up empty.
	 */
	private CompletableFuture<TempleStats> fetchStats(String encodedPlayer)
	{
		return httpGet(TEMPLE_STATS_URL + "?player=" + encodedPlayer).thenApply(json ->
		{
			if (json == null)
			{
				return null;
			}
			try
			{
				JsonObject data = gson.fromJson(json, JsonObject.class).getAsJsonObject("data");
				return data != null ? new TempleStats(data) : null;
			}
			catch (Exception e)
			{
				log.debug("Failed to parse TempleOSRS stats: {}", e.getMessage());
				return null;
			}
		});
	}

	private CompletableFuture<String> httpGet(String url)
	{
		return HttpUtil.httpGet(httpClient, url).thenApply(r -> r.body);
	}
}
