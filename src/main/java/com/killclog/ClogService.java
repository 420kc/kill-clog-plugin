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
import java.util.LinkedHashMap;
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
    private static final String WIKI_MAPPING_URL =
        "https://prices.runescape.wiki/api/v1/osrs/mapping";

    private static final Gson GSON = new Gson();

    // Boss name -> TempleOSRS category key overrides
    private static final Map<String, String> BOSS_CATEGORY_OVERRIDES = new LinkedHashMap<>();
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

    // Cached data (loaded once per session)
    private volatile Map<String, List<Integer>> cachedCategories;
    private volatile Map<Integer, String> cachedItemNames;

    @Inject
    public ClogService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
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
        return bossName.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /**
     * Look up collection log data for a player.
     * Fires 3 requests in parallel: player clog, categories (cached), item names (cached).
     */
    public CompletableFuture<ClogResult> lookup(String playerName)
    {
        String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);

        CompletableFuture<Map<String, List<ClogResult.ClogItem>>> playerFuture =
            fetchPlayerClog(encoded);
        CompletableFuture<Map<String, List<Integer>>> categoriesFuture =
            fetchCategories();
        CompletableFuture<Map<Integer, String>> namesFuture =
            fetchItemNames();

        return CompletableFuture.allOf(playerFuture, categoriesFuture, namesFuture)
            .thenApply(v ->
            {
                Map<String, List<ClogResult.ClogItem>> obtained = playerFuture.join();
                Map<String, List<Integer>> categories = categoriesFuture.join();
                Map<Integer, String> names = namesFuture.join();

                if (obtained == null)
                {
                    return null;
                }

                return new ClogResult(
                    obtained,
                    categories != null ? categories : new HashMap<>(),
                    names != null ? names : new HashMap<>()
                );
            });
    }

    private CompletableFuture<Map<String, List<ClogResult.ClogItem>>> fetchPlayerClog(String encodedPlayer)
    {
        String url = TEMPLE_PLAYER_URL + "?player=" + encodedPlayer + "&categories=all";
        return httpGetAsync(url).thenApply(json ->
        {
            if (json == null)
            {
                return null;
            }
            try
            {
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                JsonObject data = root.getAsJsonObject("data");
                if (data == null || !data.has("items"))
                {
                    return null;
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
                        itemList.add(new ClogResult.ClogItem(id, count));
                    }

                    result.put(category, itemList);
                }

                return result;
            }
            catch (Exception e)
            {
                log.debug("Failed to parse player clog: {}", e.getMessage());
                return null;
            }
        });
    }

    private CompletableFuture<Map<String, List<Integer>>> fetchCategories()
    {
        if (cachedCategories != null)
        {
            return CompletableFuture.completedFuture(cachedCategories);
        }

        return httpGetAsync(TEMPLE_CATEGORIES_URL).thenApply(json ->
        {
            if (json == null)
            {
                return null;
            }
            try
            {
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                Type type = new TypeToken<Map<String, List<Integer>>>(){}.getType();
                Map<String, List<Integer>> categories = new HashMap<>();
                for (String section : new String[]{"bosses", "raids", "minigames", "other"})
                {
                    JsonObject sectionObj = root.getAsJsonObject(section);
                    if (sectionObj != null)
                    {
                        Map<String, List<Integer>> sectionMap = GSON.fromJson(sectionObj, type);
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
        });
    }

    private CompletableFuture<Map<Integer, String>> fetchItemNames()
    {
        if (cachedItemNames != null)
        {
            return CompletableFuture.completedFuture(cachedItemNames);
        }

        return httpGetAsync(WIKI_MAPPING_URL).thenApply(json ->
        {
            if (json == null)
            {
                return null;
            }
            try
            {
                JsonArray arr = GSON.fromJson(json, JsonArray.class);
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
        });
    }

    private CompletableFuture<String> httpGetAsync(String url)
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
