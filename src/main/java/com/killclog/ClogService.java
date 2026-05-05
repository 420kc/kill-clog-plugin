package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches collection log data from TempleOSRS and item names from OSRS Wiki.
 * Caches category definitions and item names (loaded once, reused across lookups).
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

	// Boss name -> TempleOSRS category key overrides
	private static final Map<String, String> BOSS_CATEGORY_OVERRIDES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	static
	{
		// Wilderness bosses with combined clog categories
		BOSS_CATEGORY_OVERRIDES.put("Artio", "callisto_and_artio");
		BOSS_CATEGORY_OVERRIDES.put("Callisto", "callisto_and_artio");
		BOSS_CATEGORY_OVERRIDES.put("Cal'varion", "vetion_and_calvarion");
		BOSS_CATEGORY_OVERRIDES.put("Vet'ion", "vetion_and_calvarion");
		BOSS_CATEGORY_OVERRIDES.put("Venenatis", "venenatis_and_spindel");
		BOSS_CATEGORY_OVERRIDES.put("Spindel", "venenatis_and_spindel");
		// Dagannoth Kings share one clog category
		BOSS_CATEGORY_OVERRIDES.put("Dagannoth Prime", "dagannoth_kings");
		BOSS_CATEGORY_OVERRIDES.put("Dagannoth Rex", "dagannoth_kings");
		BOSS_CATEGORY_OVERRIDES.put("Dagannoth Supreme", "dagannoth_kings");
		// GWD
		BOSS_CATEGORY_OVERRIDES.put("Kree'Arra", "kree_arra");
		BOSS_CATEGORY_OVERRIDES.put("K'ril Tsutsaroth", "kril_tsutsaroth");
		// Raids - hard/expert modes share base clog
		BOSS_CATEGORY_OVERRIDES.put("Chambers of Xeric: Challenge Mode", "chambers_of_xeric");
		BOSS_CATEGORY_OVERRIDES.put("Theatre of Blood: Hard Mode", "theatre_of_blood");
		BOSS_CATEGORY_OVERRIDES.put("Tombs of Amascut: Expert Mode", "tombs_of_amascut");
		// Fight Caves / Inferno
		BOSS_CATEGORY_OVERRIDES.put("TzTok-Jad", "the_fight_caves");
		BOSS_CATEGORY_OVERRIDES.put("TzKal-Zuk", "the_inferno");
		// Colosseum
		BOSS_CATEGORY_OVERRIDES.put("Sol Heredit", "fortis_colosseum");
		// Nightmare - both versions share one clog
		BOSS_CATEGORY_OVERRIDES.put("Nightmare", "the_nightmare");
		BOSS_CATEGORY_OVERRIDES.put("Phosani's Nightmare", "the_nightmare");
		// Gauntlet - both versions share one clog
		BOSS_CATEGORY_OVERRIDES.put("The Corrupted Gauntlet", "the_gauntlet");
		// Names that don't auto-convert cleanly
		BOSS_CATEGORY_OVERRIDES.put("The Hueycoatl", "hueycoatl");
		BOSS_CATEGORY_OVERRIDES.put("The Royal Titans", "royal_titans");
		BOSS_CATEGORY_OVERRIDES.put("Lunar Chests", "moons_of_peril");
	}

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalClogCache localClogCache;

	// Cached data (loaded once per session)
	private volatile Map<String, List<Integer>> cachedCategories;
	private volatile Map<Integer, String> cachedItemNames;

	// In-flight futures — prevents duplicate HTTP requests from concurrent callers
	private volatile CompletableFuture<Map<String, List<Integer>>> categoriesFlight;
	private volatile CompletableFuture<Map<Integer, String>> namesFlight;

	// Failure cooldown gate — skip Temple if we failed within TEMPLE_FAILURE_TTL_MS (also cleared on login)
	private static final long TEMPLE_FAILURE_TTL_MS = 3 * 60 * 1000; // 3 minutes — transient blips recover, sustained outages still respected
	private final Map<String, Long> templeFailures = new java.util.concurrent.ConcurrentHashMap<>();

	// Freshness gate — skip Temple if we fetched successfully within this window
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
	 * Look up collection log data for a player.
	 * Active player with widget-read data is served from cache (authoritative).
	 * Everyone else: try Temple, cache the result, fall back to persistent cache.
	 */
	public CompletableFuture<ClogResult> lookup(String playerName)
	{
		// Active player: local cache is authoritative — never fall back to Temple
		if (localClogCache.isActivePlayer(playerName))
		{
			if (localClogCache.hasDataFor(playerName))
			{
				log.debug("Using local clog cache for active player: {}", playerName);
				return fetchItemNames().thenApply(names ->
					localClogCache.toClogResult(playerName, names != null ? names : new HashMap<>()));
			}
			// No local cache yet — return null so panel shows sync prompt
			return CompletableFuture.completedFuture(null);
		}

		// Fresh data — skip Temple if we fetched recently
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

		// Skip Temple if it failed for this player within the cooldown window
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

		// Temple for everyone else, local cache as fallback
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
					// Clog endpoint returns game_mode 0 for GIMs —
					// prefer stats endpoint when clog has no useful type
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

				// Temple failed — remember timestamp and fall back to local cache
				templeFailures.put(normalizedName, System.currentTimeMillis());
				if (localClogCache.hasDataFor(playerName))
				{
					log.debug("Temple unavailable, using cached data for: {}", playerName);
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
	 * Parse Temple's game_mode string into our AccountType.
	 * Returns null if the mode is unrecognized or absent.
	 */
	private static AccountType parseGameMode(String gameMode)
	{
		if (gameMode == null) return null;
		switch (gameMode.toLowerCase())
		{
			case "1":  // Regular ironman
			case "ironman":
				return AccountType.IRONMAN;
			case "2":  // Hardcore ironman
			case "hardcore ironman":
				return AccountType.HARDCORE_IRONMAN;
			case "3":  // Ultimate ironman
			case "ultimate ironman":
				return AccountType.ULTIMATE_IRONMAN;
			case "4":  // Group ironman
			case "group ironman":
				return AccountType.GROUP_IRONMAN;
			case "5":  // Hardcore group ironman
			case "hardcore group ironman":
				return AccountType.HARDCORE_GROUP_IRONMAN;
			default:
				return null;
		}
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
	 * Fetch account type from Temple stats endpoint.
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
	 * Lightweight fallback when clog data is unavailable.
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
		log.debug("HTTP GET: {}", url);
		CompletableFuture<String> future = new CompletableFuture<>();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", "kill-clog-RuneLite-Plugin/1.0 (https://github.com/420kc/kill-clog-plugin)")
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("HTTP GET failed for {}: {}", url, e.getMessage());
				future.complete(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						future.complete(null);
						return;
					}
					future.complete(body.string());
				}
				catch (IOException e)
				{
					log.debug("Failed to read response for {}: {}", url, e.getMessage());
					future.complete(null);
				}
			}
		});

		return future;
	}
}
