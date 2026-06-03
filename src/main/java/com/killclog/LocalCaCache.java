package com.killclog;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Multi-account, disk-backed Combat Achievement cache.
 *
 * <p>Self CA is read straight from the game (per-tier completed counts via varbits) and
 * persisted here per player in {@code ~/.runelite/kill-clog/ca/} as JSON. This is the
 * authoritative source for the active player. It does not require RuneProfile; other players
 * still come from {@link RuneProfileService}'s API.
 *
 * <p>The on-disk shape is intentionally small and self-contained: player name plus per-tier
 * completed counts.
 */
@Slf4j
@Singleton
public class LocalCaCache
{
	private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "kill-clog/ca");

	private final Map<String, CaData> players = new ConcurrentHashMap<>();
	private final Gson gson;
	private volatile String activePlayer;
	private volatile ExecutorService diskWriter = newDiskWriter();

	@Inject
	public LocalCaCache(Gson gson)
	{
		this.gson = gson;
	}

	private static ExecutorService newDiskWriter()
	{
		return Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "kill-clog-ca-disk");
			t.setDaemon(true);
			return t;
		});
	}

	/** Replace the disk writer before shutdown so the next startUp() has a live executor. */
	public void shutdown()
	{
		ExecutorService old = diskWriter;
		diskWriter = newDiskWriter();
		old.shutdownNow();
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
			CaData loaded = loadFromDisk(name);
			if (loaded != null)
			{
				players.put(key, loaded);
			}
		}
	}

	public boolean isActivePlayer(String name)
	{
		return activePlayer != null && name != null && activePlayer.equalsIgnoreCase(name);
	}

	public boolean hasDataFor(String name)
	{
		if (name == null)
		{
			return false;
		}
		String key = name.toLowerCase();
		if (players.containsKey(key))
		{
			return true;
		}
		CaData loaded = loadFromDisk(name);
		if (loaded != null)
		{
			players.put(key, loaded);
			return true;
		}
		return false;
	}

	/** Store per-tier completed counts for a player (sourced from game varbits) and persist. */
	public void cacheResult(String name, Map<CombatAchievementTier, Integer> completed)
	{
		if (name == null || completed == null)
		{
			return;
		}
		String key = name.toLowerCase();
		Map<String, Integer> completedByTier = new LinkedHashMap<>();
		for (Map.Entry<CombatAchievementTier, Integer> entry : completed.entrySet())
		{
			completedByTier.put(entry.getKey().name(), entry.getValue());
		}
		CaData existing = players.get(key);
		if (existing != null && completedByTier.equals(existing.completed))
		{
			return;
		}

		CaData data = new CaData();
		data.playerName = name;
		data.lastUpdated = Instant.now().toString();
		data.completed = completedByTier;
		players.put(key, data);

		final CaData snapshot = data;
		try
		{
			diskWriter.execute(() -> saveToDisk(name, snapshot));
		}
		catch (RejectedExecutionException ignored)
		{
			log.debug("CA disk write rejected (executor shutting down)");
		}
	}

	/** Stored CA for a player as a {@link CombatAchievementResult}, or null if none is held. */
	public CombatAchievementResult getCached(String name)
	{
		if (name == null)
		{
			return null;
		}
		CaData data = players.get(name.toLowerCase());
		if (data == null)
		{
			data = loadFromDisk(name);
			if (data == null)
			{
				return null;
			}
			players.put(name.toLowerCase(), data);
		}
		if (data.completed == null || data.completed.isEmpty())
		{
			return null;
		}
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		for (Map.Entry<String, Integer> entry : data.completed.entrySet())
		{
			CombatAchievementTier tier = CombatAchievementTier.fromName(entry.getKey());
			if (tier != null && entry.getValue() != null)
			{
				completed.put(tier, entry.getValue());
			}
		}
		if (completed.isEmpty())
		{
			return null;
		}
		Map<CombatAchievementTier, Integer> totals = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		return CombatAchievementResult.of(completed, totals);
	}

	// Disk I/O, always on the diskWriter thread.

	private void saveToDisk(String playerName, CaData data)
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
		}
		catch (IOException e)
		{
			log.warn("Failed to save CA cache for '{}': {}", playerName, e.getMessage());
		}
	}

	private CaData loadFromDisk(String playerName)
	{
		File file = getCacheFile(playerName);
		if (!file.exists())
		{
			return null;
		}
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			CaData data = gson.fromJson(reader, CaData.class);
			if (data != null && data.completed != null && !data.completed.isEmpty())
			{
				return data;
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load CA cache for '{}': {}", playerName, e.getMessage());
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

	private static class CaData
	{
		String playerName;
		String lastUpdated;
		Map<String, Integer> completed;
	}
}
