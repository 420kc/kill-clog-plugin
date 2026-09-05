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
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Fetches the TempleOSRS collection-log provider lane and item names from OSRS Wiki.
 * Category definitions and item names are loaded once per session and reused.
 */
@Slf4j
@Singleton
public class ClogService
{
	private static final String TEMPLE_CATEGORIES_URL =
		"https://templeosrs.com/api/collection-log/categories.php";
	private static final String TEMPLE_PLAYER_URL =
		"https://templeosrs.com/api/collection-log/player_collection_log.php";
	private static final String TEMPLE_STATS_URL =
		"https://templeosrs.com/api/player_stats.php";
	private static final String WIKI_MAPPING_URL =
		"https://prices.runescape.wiki/api/v1/osrs/mapping";

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

	/** Read-only view for catalog-parity tests. */
	/* package */ static Map<String, String> bossCategoryOverrides()
	{
		return Collections.unmodifiableMap(BOSS_CATEGORY_OVERRIDES);
	}

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalClogCache localClogCache;

	// Cached data loaded once per session.
	private volatile Map<String, List<Integer>> cachedCategories;
	private volatile Map<Integer, String> cachedItemNames;

	// In-flight futures prevent duplicate HTTP requests from concurrent callers.
	private volatile CompletableFuture<Map<String, List<Integer>>> categoriesFlight;
	private volatile CompletableFuture<Map<Integer, String>> namesFlight;

	// Failure cooldown skips TempleOSRS briefly after transient errors.
	private static final long TEMPLE_FAILURE_TTL_MS = 3 * 60 * 1000;
	private final Map<String, Long> templeFailures = new java.util.concurrent.ConcurrentHashMap<>();

	// Freshness gate skips TempleOSRS when the cache is still hot.
	private static final long CLOG_TTL_MS = 5 * 60 * 1000; // 5 minutes
	private final Map<String, Long> clogFetchTimes = new java.util.concurrent.ConcurrentHashMap<>();

	@Inject
	public ClogService(OkHttpClient httpClient, Gson gson, LocalClogCache localClogCache)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.localClogCache = localClogCache;
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
		Map<Integer, String> names = cachedItemNames;
		return localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>());
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
			if (localClogCache.hasDataFor(playerName))
			{
				log.debug("Using local clog cache for active player: {}", playerName);
				// Dates-only provider overlay before serving: heals undated
				// cache entries (pre-dating live merges, or unlocks while the
				// plugin was off) so the recents shelf stays current. TTL-gated
				// and failure-proof; counts and membership stay chalice-owned.
				return overlayProviderDates(playerName)
					.thenCompose(ignored -> fetchItemNames().thenApply(names ->
						localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>())));
			}
			// No local cache yet, so the panel shows the sync prompt.
			return CompletableFuture.completedFuture(null);
		}

		// Fresh data: skip the TempleOSRS lane if we fetched recently.
		String normalizedName = playerName.toLowerCase();
		Long lastFetch = clogFetchTimes.get(normalizedName);
		if (lastFetch != null && System.currentTimeMillis() - lastFetch < CLOG_TTL_MS
			&& localClogCache.hasDataFor(playerName))
		{
			log.debug("Using fresh cached clog for '{}' ({}s old)",
				playerName, (System.currentTimeMillis() - lastFetch) / 1000);
			return fetchItemNames().thenApply(names ->
				localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>()));
		}

		// Skip TempleOSRS if it failed for this player within the cooldown window.
		Long failedAt = templeFailures.get(normalizedName);
		if (failedAt != null && System.currentTimeMillis() - failedAt < TEMPLE_FAILURE_TTL_MS)
		{
			if (localClogCache.hasDataFor(playerName))
			{
				return fetchItemNames().thenApply(names ->
					localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>()));
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
		CompletableFuture<AccountType> statsFuture =
			fetchStatsAccountType(encoded);

		return CompletableFuture.allOf(playerFuture, categoriesFuture, namesFuture, statsFuture)
			.thenApply(v ->
			{
				PlayerClogData playerData = playerFuture.join();
				Map<String, List<Integer>> categories = categoriesFuture.join();
				Map<Integer, String> names = namesFuture.join();
				AccountType statsType = statsFuture.join();

				if (playerData != null)
				{
					// Clog endpoint returns game_mode 0 for GIMs.
					// Prefer stats endpoint when clog has no useful type.
					AccountType accountType = playerData.accountType;
					if (statsType != null
						&& (accountType == null || accountType == AccountType.REGULAR))
					{
						accountType = statsType;
					}

					ClogResult result = new ClogResult(
						playerData.canonicalName,
						playerData.obtainedItems,
						categories != null ? categories : new HashMap<>(),
						names != null ? names : new HashMap<>(),
						playerData.lastChanged,
						accountType
					);
					localClogCache.cacheResult(result);
					clogFetchTimes.put(normalizedName, System.currentTimeMillis());
					return result;
				}

				// TempleOSRS lane failed. Remember timestamp and fall back to local cache.
				templeFailures.put(normalizedName, System.currentTimeMillis());
				if (localClogCache.hasDataFor(playerName))
				{
					log.debug("TempleOSRS unavailable, using cached data for: {}", playerName);
					return localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>());
				}

				return null;
			});
	}

	private static class PlayerClogData
	{
		final String canonicalName;
		final Map<String, List<ClogResult.ClogItem>> obtainedItems;
		final String lastChanged;
		final AccountType accountType;

		PlayerClogData(String canonicalName, Map<String, List<ClogResult.ClogItem>> obtainedItems,
			String lastChanged, AccountType accountType)
		{
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
	 * fans out; any provider failure resolves false and the lookup proceeds
	 * on local data unchanged.
	 */
	private CompletableFuture<Boolean> overlayProviderDates(String playerName)
	{
		String ttlKey = "dates:" + playerName.toLowerCase();
		Long lastFetch = clogFetchTimes.get(ttlKey);
		if (lastFetch != null && System.currentTimeMillis() - lastFetch < CLOG_TTL_MS)
		{
			return CompletableFuture.completedFuture(false);
		}
		clogFetchTimes.put(ttlKey, System.currentTimeMillis());
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
		return fetchClog(encoded)
			.thenApply(player -> player != null && player.obtainedItems != null
				&& localClogCache.mergeProviderDates(playerName, player.obtainedItems))
			.exceptionally(t ->
			{
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

				return new PlayerClogData(canonicalName, result, lastChanged, accountType);
			}
			catch (Exception e)
			{
				log.debug("Failed to parse player clog: {}", e.getMessage());
				return null;
			}
		});
	}

	/**
	 * Total item count for a category from the cached global definitions.
	 * Returns -1 if categories haven't loaded yet.
	 */
	public int getCategoryItemCount(String category)
	{
		Map<String, List<Integer>> cats = cachedCategories;
		if (cats == null) return -1;
		List<Integer> items = cats.get(category);
		return items != null ? items.size() : -1;
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
	 * Returns a defensive copy.
	 */
	@Nullable
	public List<Integer> getCategoryCatalogIds(String category)
	{
		Map<String, List<Integer>> cats = cachedCategories;
		if (cats == null)
		{
			return null;
		}
		List<Integer> items = cats.get(category);
		return items != null ? new ArrayList<>(items) : null;
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

		Map<String, List<Integer>> categories = new HashMap<>();
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : cats.entrySet())
		{
			categories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
			obtained.put(entry.getKey(), Collections.emptyList());
		}

		Map<Integer, String> names = cachedItemNames != null
			? new HashMap<>(cachedItemNames) : new HashMap<>();
		return new ClogResult(playerName, obtained, categories, names, null, null);
	}

	private CompletableFuture<Map<String, List<Integer>>> fetchCategories()
	{
		if (cachedCategories != null)
		{
			return CompletableFuture.completedFuture(cachedCategories);
		}

		synchronized (this)
		{
			if (cachedCategories != null)
			{
				return CompletableFuture.completedFuture(cachedCategories);
			}
			if (categoriesFlight != null)
			{
				return categoriesFlight;
			}

			categoriesFlight = httpGet(TEMPLE_CATEGORIES_URL).thenApply(json ->
			{
				try
				{
					if (json == null)
					{
						return null;
					}
					JsonObject root = gson.fromJson(json, JsonObject.class);
					Type type = new TypeToken<Map<String, List<Integer>>>()
				{
				}.getType();
					Map<String, List<Integer>> categories = new HashMap<>();
					for (Map.Entry<String, JsonElement> entry : root.entrySet())
					{
						if (!entry.getValue().isJsonObject())
						{
							continue;
						}
						Map<String, List<Integer>> sectionMap = gson.fromJson(entry.getValue(), type);
						if (sectionMap != null)
						{
							categories.putAll(sectionMap);
						}
					}
					if (categories.isEmpty())
					{
						return null;
					}
					cachedCategories = categories;
					return categories;
				}
				catch (Exception e)
				{
					log.debug("Failed to parse clog categories: {}", e.getMessage());
					return null;
				}
				finally
				{
					categoriesFlight = null;
				}
			});

			return categoriesFlight;
		}
	}

	private CompletableFuture<Map<Integer, String>> fetchItemNames()
	{
		if (cachedItemNames != null)
		{
			return CompletableFuture.completedFuture(cachedItemNames);
		}

		synchronized (this)
		{
			if (cachedItemNames != null)
			{
				return CompletableFuture.completedFuture(cachedItemNames);
			}
			if (namesFlight != null)
			{
				return namesFlight;
			}

			namesFlight = httpGet(WIKI_MAPPING_URL).thenApply(json ->
			{
				try
				{
					if (json == null)
					{
						return null;
					}
					JsonArray arr = gson.fromJson(json, JsonArray.class);
					Map<Integer, String> names = new HashMap<>();

					for (JsonElement elem : arr)
					{
						JsonObject obj = elem.getAsJsonObject();
						if (obj.has("id") && obj.has("name"))
						{
							names.put(obj.get("id").getAsInt(), obj.get("name").getAsString());
						}
					}

					cachedItemNames = names;
					return names;
				}
				catch (Exception e)
				{
					log.debug("Failed to parse item names: {}", e.getMessage());
					return null;
				}
				finally
				{
					namesFlight = null;
				}
			});

			return namesFlight;
		}
	}

	/**
	 * Fetch account type from the TempleOSRS stats endpoint.
	 * The clog endpoint reports game_mode 0 for GIMs, but the stats
	 * endpoint exposes a GIM field with the group ID.
	 */
	private CompletableFuture<AccountType> fetchStatsAccountType(String encodedPlayer)
	{
		String url = TEMPLE_STATS_URL + "?player=" + encodedPlayer;
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
				if (data == null)
				{
					return null;
				}
				JsonObject info = data.getAsJsonObject("info");
				if (info == null)
				{
					return null;
				}

				if (info.has("Game mode") && !info.get("Game mode").isJsonNull())
				{
					AccountType type = parseGameMode(info.get("Game mode").getAsString());
					if (type != null && type != AccountType.REGULAR)
					{
						return type;
					}
				}

				if (info.has("GIM") && !info.get("GIM").isJsonNull())
				{
					int gimId = info.get("GIM").getAsInt();
					if (gimId > 0)
					{
						return AccountType.GROUP_IRONMAN;
					}
				}

				return null;
			}
			catch (Exception e)
			{
				log.debug("Failed to parse stats account type: {}", e.getMessage());
				return null;
			}
		});
	}

	/**
	 * Fetch only the canonical player name from TempleOSRS player stats.
	 * Lightweight identity fallback when collection-log data is unavailable.
	 */
	public CompletableFuture<String> lookupRsn(String playerName)
	{
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
		String url = TEMPLE_STATS_URL + "?player=" + encoded;
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
				if (data == null)
				{
					return null;
				}
				JsonObject info = data.getAsJsonObject("info");
				if (info != null && info.has("player_name_with_capitalization"))
				{
					return info.get("player_name_with_capitalization").getAsString();
				}
				return null;
			}
			catch (Exception e)
			{
				log.debug("Failed to parse canonical name: {}", e.getMessage());
				return null;
			}
		});
	}

	private CompletableFuture<String> httpGet(String url)
	{
		return HttpUtil.httpGet(httpClient, url).thenApply(r -> r.body);
	}
}
