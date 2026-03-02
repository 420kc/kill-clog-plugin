package com.killclog;

import java.util.List;
import java.util.Map;
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
    /** item ID -> display name (concurrent: written from client thread, read from EDT) */
    private final ConcurrentHashMap<Integer, String> itemNames;
    /** When the player last synced clog data to TempleOSRS (from last_changed field) */
    private final String lastChanged;

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
        this.itemNames = new ConcurrentHashMap<>(itemNames);
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

    public String getItemName(int id)
    {
        return itemNames.getOrDefault(id, "Item #" + id);
    }

    public boolean hasItemName(int id)
    {
        return itemNames.containsKey(id);
    }

    public void putItemName(int id, String name)
    {
        itemNames.put(id, name);
    }

    public static class ClogItem
    {
        private final int id;
        private final int count;

        public ClogItem(int id, int count)
        {
            this.id = id;
            this.count = count;
        }

        public int getId()
        {
            return id;
        }

        public int getCount()
        {
            return count;
        }
    }
}
