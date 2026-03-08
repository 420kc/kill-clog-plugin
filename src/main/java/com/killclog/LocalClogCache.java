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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Multi-account, disk-backed collection log cache.
 *
 * <p>Stores per-player clog data in {@code ~/.runelite/kill-clog/} as JSON files.
 * Populated via bulk capture when the player opens their collection log in-game.
 * Persists across client restarts — any account ever captured is available permanently.
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

	/** Single-threaded executor for all disk I/O — keeps the client thread unblocked. */
	private final ExecutorService diskWriter = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "kill-clog-disk");
		t.setDaemon(true);
		return t;
	});

	@Inject
	public LocalClogCache(Gson gson)
	{
		this.gson = gson;
	}

	/** Call from plugin shutDown() to flush any pending writes cleanly. */
	public void shutdown()
	{
		diskWriter.shutdown();
	}

	public void setActivePlayer(String name)
	{
		if (name == null)
		{
			activePlayer = null;
			return;
		}

		activePlayer = name;
		String key = name.toLowerCase();

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
		String key = name.toLowerCase();

		PlayerClogData data = new PlayerClogData();
		data.playerName = name;
		data.lastUpdated = Instant.now().toString();
		data.obtained = new ConcurrentHashMap<>();
		data.categories = new ConcurrentHashMap<>();

		for (Map.Entry<String, List<ClogResult.ClogItem>> entry
			: result.getObtainedItems().entrySet())
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
		final PlayerClogData snapshot = shallowCopy(data);
		diskWriter.execute(() -> saveToDisk(name, snapshot));
		log.debug("Cached Temple data for '{}' ({} categories)", name, data.obtained.size());
	}

	public boolean hasDataFor(String playerName)
	{
		if (playerName == null)
		{
			return false;
		}

		String key = playerName.toLowerCase();
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

		PlayerClogData data = players.get(playerName.toLowerCase());
		if (data == null)
		{
			return null;
		}

		// Defensive copies — callers may mutate their maps
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
			null // no lastChanged for local-only data
		);
	}

	// --- Disk I/O (runs on diskWriter thread only) ---

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
		String sanitized = playerName.toLowerCase()
			.replace(' ', '_')
			.replaceAll("[^a-z0-9_-]", "");
		return new File(CACHE_DIR, sanitized + ".json");
	}

	/** Shallow copy sufficient for async disk write — lists are already copied in callers. */
	private static PlayerClogData shallowCopy(PlayerClogData src)
	{
		PlayerClogData copy = new PlayerClogData();
		copy.playerName = src.playerName;
		copy.lastUpdated = src.lastUpdated;
		copy.categories = new HashMap<>(src.categories);
		copy.obtained = new HashMap<>(src.obtained);
		return copy;
	}

	private static class PlayerClogData
	{
		String playerName;
		String lastUpdated;
		Map<String, List<Integer>> categories;
		Map<String, List<ClogResult.ClogItem>> obtained;
	}
}
