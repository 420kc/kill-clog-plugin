package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Multi-account, disk-backed collection log cache.
 * Stores per-player clog data in ~/.runelite/kill-clog/ as JSON files.
 * Populated incrementally as any logged-in player browses their collection log in-game.
 * Persists across client restarts — any account ever browsed is available permanently.
 */
@Slf4j
@Singleton
public class LocalClogCache
{
    private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "kill-clog");

    private final Map<String, PlayerClogData> players = new ConcurrentHashMap<>();
    private final Gson gson;
    private volatile String activePlayerName;

    @Inject
    public LocalClogCache(Gson gson)
    {
        this.gson = gson;
    }

    public void setActivePlayer(String name)
    {
        if (name == null)
        {
            activePlayerName = null;
            return;
        }

        activePlayerName = name;
        String key = name.toLowerCase();

        if (!players.containsKey(key))
        {
            PlayerClogData loaded = loadFromDisk(name);
            if (loaded != null)
            {
                players.put(key, loaded);
                log.debug("Loaded persistent clog cache for '{}' ({} categories)", name, loaded.categories.size());
            }
        }

        log.debug("Active clog player set to: {}", name);
    }

    public void putCategory(String categoryKey, List<Integer> allItemIds, List<ClogResult.ClogItem> obtained)
    {
        if (activePlayerName == null)
        {
            return;
        }

        String key = activePlayerName.toLowerCase();
        PlayerClogData data = players.computeIfAbsent(key, k ->
        {
            PlayerClogData d = new PlayerClogData();
            d.playerName = activePlayerName;
            d.categories = new HashMap<>();
            d.obtained = new HashMap<>();
            return d;
        });

        // Ensure canonical name is set (may have been loaded from disk with different casing)
        data.playerName = activePlayerName;
        data.lastUpdated = Instant.now().toString();
        data.categories.put(categoryKey, new ArrayList<>(allItemIds));
        data.obtained.put(categoryKey, new ArrayList<>(obtained));

        log.debug("Cached clog category '{}' for '{}': {}/{} obtained",
            categoryKey, activePlayerName, obtained.size(), allItemIds.size());

        saveToDisk(activePlayerName, data);
    }

    public boolean isActivePlayer(String name)
    {
        return activePlayerName != null && name != null
            && activePlayerName.equalsIgnoreCase(name);
    }

    public void cacheResult(ClogResult result)
    {
        if (result == null || result.getPlayerName() == null)
        {
            return;
        }

        String name = result.getPlayerName();
        String key = name.toLowerCase();

        PlayerClogData data = new PlayerClogData();
        data.playerName = name;
        data.lastUpdated = Instant.now().toString();
        data.obtained = new HashMap<>();
        data.categories = new HashMap<>();

        for (Map.Entry<String, List<ClogResult.ClogItem>> entry : result.getObtainedItems().entrySet())
        {
            String cat = entry.getKey();
            data.obtained.put(cat, new ArrayList<>(entry.getValue()));

            List<Integer> catItems = result.getCategoryItems().get(cat);
            if (catItems != null)
            {
                data.categories.put(cat, new ArrayList<>(catItems));
            }
        }

        players.put(key, data);
        saveToDisk(name, data);
        log.debug("Cached Temple data for '{}' ({} categories)", name, data.obtained.size());
    }

    public boolean hasDataFor(String playerName)
    {
        if (playerName == null)
        {
            return false;
        }

        String key = playerName.toLowerCase();

        // Check memory first
        if (players.containsKey(key))
        {
            return true;
        }

        // Try loading from disk
        PlayerClogData loaded = loadFromDisk(playerName);
        if (loaded != null)
        {
            players.put(key, loaded);
            log.debug("Lazy-loaded persistent clog cache for '{}' ({} categories)", playerName, loaded.categories.size());
            return true;
        }

        return false;
    }

    public ClogResult toClogResult(String playerName, Map<Integer, String> itemNames)
    {
        if (playerName == null)
        {
            return null;
        }

        String key = playerName.toLowerCase();
        PlayerClogData data = players.get(key);
        if (data == null)
        {
            return null;
        }

        // Defensive copies
        Map<String, List<ClogResult.ClogItem>> obtainedCopy = new HashMap<>();
        for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
        {
            obtainedCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Map<String, List<Integer>> categoriesCopy = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : data.categories.entrySet())
        {
            categoriesCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return new ClogResult(
            data.playerName,
            obtainedCopy,
            categoriesCopy,
            itemNames != null ? itemNames : new HashMap<>(),
            null // no lastChanged for local data
        );
    }

    private void saveToDisk(String playerName, PlayerClogData data)
    {
        try
        {
            if (!CACHE_DIR.exists())
            {
                CACHE_DIR.mkdirs();
            }

            File file = getCacheFile(playerName);
            try (FileWriter writer = new FileWriter(file))
            {
                gson.toJson(data, writer);
            }
            log.debug("Saved clog cache to disk: {}", file.getName());
        }
        catch (IOException e)
        {
            log.warn("Failed to save clog cache for '{}': {}", playerName, e.getMessage());
        }
    }

    private PlayerClogData loadFromDisk(String playerName)
    {
        File file = getCacheFile(playerName);
        if (!file.exists())
        {
            return null;
        }

        try (FileReader reader = new FileReader(file))
        {
            PlayerClogData data = gson.fromJson(reader, PlayerClogData.class);
            if (data != null && data.categories != null && !data.categories.isEmpty())
            {
                return data;
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load clog cache for '{}': {}", playerName, e.getMessage());
        }

        return null;
    }

    private File getCacheFile(String playerName)
    {
        // Sanitize: lowercase, replace spaces with underscores, strip non-alphanumeric
        String sanitized = playerName.toLowerCase()
            .replace(' ', '_')
            .replaceAll("[^a-z0-9_-]", "");
        return new File(CACHE_DIR, sanitized + ".json");
    }

    private static class PlayerClogData
    {
        String playerName;
        String lastUpdated;
        Map<String, List<Integer>> categories;
        Map<String, List<ClogResult.ClogItem>> obtained;
    }
}
