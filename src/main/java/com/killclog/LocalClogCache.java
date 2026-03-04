package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory cache for collection log data read directly from the game client widgets.
 * Populated incrementally as the player browses their collection log in-game.
 */
@Slf4j
@Singleton
public class LocalClogCache
{
    private final ConcurrentHashMap<String, List<ClogResult.ClogItem>> obtainedItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> categoryItems = new ConcurrentHashMap<>();
    private volatile String cachedPlayerName;

    public void setPlayerName(String name)
    {
        if (name == null)
        {
            clear();
            return;
        }
        if (!name.equalsIgnoreCase(cachedPlayerName))
        {
            clear();
            cachedPlayerName = name;
            log.debug("Local clog cache bound to player: {}", name);
        }
    }

    public void putCategory(String categoryKey, List<Integer> allItemIds, List<ClogResult.ClogItem> obtained)
    {
        categoryItems.put(categoryKey, new ArrayList<>(allItemIds));
        obtainedItems.put(categoryKey, new ArrayList<>(obtained));
        log.debug("Cached clog category '{}': {}/{} obtained", categoryKey, obtained.size(), allItemIds.size());
    }

    public boolean hasData()
    {
        return !obtainedItems.isEmpty();
    }

    public boolean isFor(String playerName)
    {
        return cachedPlayerName != null && cachedPlayerName.equalsIgnoreCase(playerName);
    }

    public ClogResult toClogResult(Map<Integer, String> itemNames)
    {
        // Defensive copies
        Map<String, List<ClogResult.ClogItem>> obtainedCopy = new HashMap<>();
        for (Map.Entry<String, List<ClogResult.ClogItem>> entry : obtainedItems.entrySet())
        {
            obtainedCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Map<String, List<Integer>> categoriesCopy = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : categoryItems.entrySet())
        {
            categoriesCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return new ClogResult(
            cachedPlayerName,
            obtainedCopy,
            categoriesCopy,
            itemNames != null ? itemNames : new HashMap<>(),
            null // no lastChanged for local data
        );
    }

    public void clear()
    {
        obtainedItems.clear();
        categoryItems.clear();
        cachedPlayerName = null;
        log.debug("Local clog cache cleared");
    }
}
