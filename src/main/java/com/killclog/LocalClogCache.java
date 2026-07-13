package com.killclog;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Multi-account, disk-backed collection log cache.
 *
 * <p>Stores per-player clog data in {@code ~/.runelite/kill-clog/} as JSON files.
 * Populated via bulk capture when the player opens their collection log in-game.
 * Persists across client restarts. Any captured account remains available.
 *
 * <p>Disk writes are dispatched to a single background thread to avoid blocking the
 * game client thread.
 *
 * <p>Note: the in-memory player map is not evicted. In practice the number of distinct
 * looked-up players per session is small, so unbounded growth is not a concern.
 */
@Slf4j
@Singleton
public class LocalClogCache
{
	private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "kill-clog");

	private final Map<String, PlayerClogData> players = new ConcurrentHashMap<>();
	private final Gson gson;
	private volatile String activePlayer;

	/**
	 * Disk I/O uses a single-threaded executor and per-player coalesce window.
	 * Bursts of category navigation collapse to one write per player.
	 * Volatile so shutdown() can swap the reference visibly to concurrent submitters.
	 */
	private static final long DEBOUNCE_MS = 500;
	private volatile ScheduledExecutorService diskWriter;
	private final Map<String, Runnable> pendingByPlayer = new ConcurrentHashMap<>();

	private static ScheduledExecutorService newDiskWriter()
	{
		return Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "kill-clog-disk");
			t.setDaemon(true);
			return t;
		});
	}

	private static String cacheKey(String playerName)
	{
		return playerName.toLowerCase(Locale.ROOT);
	}

	/**
	 * Submit a disk write for a player, coalescing bursts within DEBOUNCE_MS into a single write.
	 * The latest snapshot wins. Rejections during executor swap are swallowed; the next capture re-saves.
	 */
	private void submitDiskWrite(String playerName, Runnable task)
	{
		String key = cacheKey(playerName);
		boolean wasFirst = pendingByPlayer.put(key, task) == null;
		if (!wasFirst)
		{
			return;
		}
		try
		{
			diskWriter.schedule(() ->
			{
				Runnable latest = pendingByPlayer.remove(key);
				if (latest != null)
				{
					latest.run();
				}
			}, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException ignored)
		{
			if (pendingByPlayer.remove(key, task))
			{
				try
				{
					diskWriter.execute(task);
					return;
				}
				catch (RejectedExecutionException retryIgnored)
				{
					// Leave it unsaved; the next capture will resave.
				}
			}
			log.debug("Disk write rejected (executor shutting down)");
		}
	}

	@Inject
	public LocalClogCache(Gson gson)
	{
		this(gson, newDiskWriter());
	}

	LocalClogCache(Gson gson, ScheduledExecutorService diskWriter)
	{
		this.gson = gson;
		this.diskWriter = diskWriter;
	}

	/**
	 * Replace the disk writer before shutdown. LocalClogCache is a singleton,
	 * so the next startUp() needs a live executor.
	 */
	public void shutdown()
	{
		ScheduledExecutorService old = diskWriter;
		ScheduledExecutorService fresh = newDiskWriter();
		diskWriter = fresh;

		// Move pending debounced writes to the replacement writer without double-running them.
		for (String key : new ArrayList<>(pendingByPlayer.keySet()))
		{
			Runnable t = pendingByPlayer.remove(key);
			if (t != null)
			{
				try
				{
					fresh.execute(t);
				}
				catch (RejectedExecutionException ignored)
				{
					t.run();
				}
			}
		}

		// Don't await termination. Would block RuneLite's plugin-shutdown thread.
		// Pending tasks were drained to the fresh executor above; in-flight tasks
		// on `old` complete on their own background threads.
		old.shutdown();
	}

	public void setActivePlayer(String name)
	{
		if (name == null)
		{
			activePlayer = null;
			return;
		}

		activePlayer = name;
		String key = cacheKey(name);

		if (!players.containsKey(key))
		{
			PlayerClogData loaded = loadFromDisk(name);
			if (loaded != null)
			{
				players.put(key, loaded);
				log.debug("Loaded persistent clog cache for '{}' ({} categories)",
					name, loaded.categories.size());
			}
		}

		log.debug("Active clog player set to: {}", name);
	}

	public boolean isActivePlayer(String name)
	{
		return activePlayer != null && name != null
			&& activePlayer.equalsIgnoreCase(name);
	}

	public void cacheResult(ClogResult result)
	{
		if (result == null || result.getPlayerName() == null)
		{
			return;
		}

		String name = result.getPlayerName();
		String key = cacheKey(name);

		// Preserve varp-sourced totals if they are higher than public providers report.
		PlayerClogData existing = players.get(key);

		PlayerClogData data = existing != null ? shallowCopy(existing) : new PlayerClogData();
		data.playerName = name;
		data.lastUpdated = Instant.now().toString();
		data.uniqueObtained = result.getUniqueObtained();
		data.uniqueTotal = result.getUniqueTotal();
		if (existing != null)
		{
			if (existing.uniqueObtained > data.uniqueObtained)
			{
				data.uniqueObtained = existing.uniqueObtained;
			}
			if (existing.uniqueTotal > data.uniqueTotal)
			{
				data.uniqueTotal = existing.uniqueTotal;
			}
		}
		if (result.getLastChanged() != null)
		{
			data.lastChanged = result.getLastChanged();
		}
		if (result.getProviderAccountType() != null)
		{
			data.providerAccountType = result.getProviderAccountType();
		}
		data.obtained = data.obtained != null
			? new ConcurrentHashMap<>(data.obtained)
			: new ConcurrentHashMap<>();
		data.categories = data.categories != null
			? new ConcurrentHashMap<>(data.categories)
			: new ConcurrentHashMap<>();

		for (Map.Entry<String, List<ClogResult.ClogItem>> entry
			: result.getObtainedItems().entrySet())
		{
			String cat = entry.getKey();
			data.obtained.put(cat, new ArrayList<>(entry.getValue()));
		}
		for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
		{
			data.categories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}

		players.put(key, data);
		final PlayerClogData snapshot = shallowCopy(data);
		submitDiskWrite(name, () -> saveToDisk(name, snapshot));
		log.debug("Cached clog data for '{}' ({} categories)", name, data.obtained.size());
	}

	public void mergeCategory(String playerName, String categoryKey,
		List<Integer> allItems, List<ClogResult.ClogItem> obtained)
	{
		if (playerName == null)
		{
			return;
		}

		String key = cacheKey(playerName);
		PlayerClogData data = players.get(key);
		if (data == null)
		{
			return;
		}

		data.categories.put(categoryKey, new ArrayList<>(allItems));
		data.obtained.put(categoryKey, new ArrayList<>(obtained));

		final PlayerClogData snapshot = shallowCopy(data);
		submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot));
		log.debug("Merged category '{}' for '{}': {}/{} obtained",
			categoryKey, playerName, obtained.size(), allItems.size());
	}

	public boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems)
	{
		if (playerName == null || categoryKeys == null || categoryItems == null)
		{
			return false;
		}

		String key = cacheKey(playerName);
		PlayerClogData data = players.get(key);
		if (data == null)
		{
			return false;
		}
		if (data.categories == null)
		{
			data.categories = new ConcurrentHashMap<>();
		}
		if (data.obtained == null)
		{
			data.obtained = new ConcurrentHashMap<>();
		}

		// Whether this item is a brand-new unique, judged across every page
		// BEFORE the merge: shared items (clue rares on several pages) only
		// count once, and the game's own counter stays authoritative at sync.
		boolean newUnique = data.uniqueObtained > 0 && !obtainedAnywhere(data, itemId);

		boolean changed = false;
		for (String categoryKey : categoryKeys)
		{
			List<Integer> allItems = categoryItems.get(categoryKey);
			if (allItems == null || !allItems.contains(itemId))
			{
				continue;
			}

			data.categories.put(categoryKey, new ArrayList<>(allItems));
			List<ClogResult.ClogItem> obtained = new ArrayList<>(
				data.obtained.getOrDefault(categoryKey, Collections.emptyList()));
			boolean alreadyObtained = false;
			for (ClogResult.ClogItem item : obtained)
			{
				if (item.getId() == itemId)
				{
					alreadyObtained = true;
					break;
				}
			}
			if (!alreadyObtained)
			{
				obtained.add(new ClogResult.ClogItem(itemId, 1, null));
				data.obtained.put(categoryKey, obtained);
				changed = true;
			}
		}

		if (changed)
		{
			if (newUnique)
			{
				// The sidebar total reads this scalar; without the bump a live
				// unlock shows on its page but the total sits stale until the
				// next chalice sync.
				data.uniqueObtained++;
			}
			data.lastUpdated = Instant.now().toString();
			final PlayerClogData snapshot = shallowCopy(data);
			submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot));
			log.debug("Merged live clog item {} for '{}'", itemId, playerName);
		}
		return changed;
	}

	private static boolean obtainedAnywhere(PlayerClogData data, int itemId)
	{
		for (List<ClogResult.ClogItem> items : data.obtained.values())
		{
			for (ClogResult.ClogItem item : items)
			{
				if (item.getId() == itemId)
				{
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasObtainedItem(String playerName, int itemId, List<String> categoryKeys)
	{
		if (playerName == null || categoryKeys == null)
		{
			return false;
		}

		PlayerClogData data = players.get(cacheKey(playerName));
		if (data == null || data.obtained == null)
		{
			return false;
		}

		for (String categoryKey : categoryKeys)
		{
			List<ClogResult.ClogItem> obtained = data.obtained.get(categoryKey);
			if (obtained == null)
			{
				continue;
			}
			for (ClogResult.ClogItem item : obtained)
			{
				if (item.getId() == itemId)
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Live-unlock totals update: raises only. The unlock-time varp read can
	 * lag the chat message by a tick, and lowering here would revert the
	 * unique bump the merge just made. Chalice sync stays the downward
	 * authority.
	 */
	public void updateTotalsUpward(String playerName, int obtained, int total)
	{
		PlayerClogData data = playerName != null ? players.get(cacheKey(playerName)) : null;
		if (data == null)
		{
			return;
		}
		updateTotals(playerName,
			obtained > data.uniqueObtained ? obtained : -1,
			total > data.uniqueTotal ? total : -1);
	}

	public void updateTotals(String playerName, int obtained, int total)
	{
		if (playerName == null)
		{
			return;
		}

		String key = cacheKey(playerName);
		PlayerClogData data = players.get(key);
		if (data == null)
		{
			return;
		}

		boolean changed = false;
		if (obtained > 0 && obtained != data.uniqueObtained)
		{
			data.uniqueObtained = obtained;
			changed = true;
		}
		if (total > 0 && total != data.uniqueTotal)
		{
			data.uniqueTotal = total;
			changed = true;
		}

		if (changed)
		{
			final PlayerClogData snapshot = shallowCopy(data);
			submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot));
			log.debug("Updated clog totals for '{}': {}/{}", playerName, obtained, total);
		}
	}

	public boolean hasDataFor(String playerName)
	{
		if (playerName == null)
		{
			return false;
		}

		String key = cacheKey(playerName);
		if (players.containsKey(key))
		{
			return true;
		}

		PlayerClogData loaded = loadFromDisk(playerName);
		if (loaded != null)
		{
			players.put(key, loaded);
			log.debug("Lazy-loaded persistent clog cache for '{}' ({} categories)",
				playerName, loaded.categories.size());
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

		PlayerClogData data = players.get(cacheKey(playerName));
		if (data == null)
		{
			return null;
		}

		// Defensive copies: callers may mutate their maps.
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

		ClogResult result = new ClogResult(
			data.playerName,
			obtainedCopy,
			categoriesCopy,
			itemNames != null ? itemNames : new HashMap<>(),
			data.lastChanged,
			data.providerAccountType
		);
		if (data.uniqueObtained > 0)
		{
			result.setUniqueObtained(data.uniqueObtained);
		}
		if (data.uniqueTotal > 0)
		{
			result.setUniqueTotal(data.uniqueTotal);
		}
		return result;
	}

	// Disk I/O, always on the diskWriter thread.

	private void saveToDisk(String playerName, PlayerClogData data)
	{
		try
		{
			if (!CACHE_DIR.exists())
			{
				CACHE_DIR.mkdirs();
			}
			File file = getCacheFile(playerName);
			try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
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
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			PlayerClogData data = gson.fromJson(reader, PlayerClogData.class);
			if (data != null && data.categories != null && !data.categories.isEmpty())
			{
				// Gson deserializes to plain maps; wrap in ConcurrentHashMap
				// so mergeCategory() and EDT reads can't collide.
				data.categories = new ConcurrentHashMap<>(data.categories);
				data.obtained = data.obtained != null
					? new ConcurrentHashMap<>(data.obtained)
					: new ConcurrentHashMap<>();
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
		String sanitized = cacheKey(playerName)
			.replace(' ', '_')
			.replaceAll("[^a-z0-9_-]", "");
		return new File(CACHE_DIR, sanitized + ".json");
	}

	/** Shallow copy sufficient for async disk write. */
	private static PlayerClogData shallowCopy(PlayerClogData src)
	{
		PlayerClogData copy = new PlayerClogData();
		copy.playerName = src.playerName;
		copy.lastUpdated = src.lastUpdated;
		copy.lastChanged = src.lastChanged;
		copy.providerAccountType = src.providerAccountType;
		copy.uniqueObtained = src.uniqueObtained;
		copy.uniqueTotal = src.uniqueTotal;
		copy.categories = src.categories != null ? new HashMap<>(src.categories) : new HashMap<>();
		copy.obtained = src.obtained != null ? new HashMap<>(src.obtained) : new HashMap<>();
		return copy;
	}

	private static class PlayerClogData
	{
		String playerName;
		String lastUpdated;
		String lastChanged;
		AccountType providerAccountType;
		int uniqueObtained = -1;
		int uniqueTotal = -1;
		Map<String, List<Integer>> categories;
		Map<String, List<ClogResult.ClogItem>> obtained;
	}
}
