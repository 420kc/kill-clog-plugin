package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

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
	private final Map<String, CaData> players = new ConcurrentHashMap<>();
	private final Gson gson;
	// ca/ in the plugin's folder; null keeps captures in memory for the session.
	@Nullable
	private volatile Filepath folder;
	private volatile String activePlayer;
	private final ExecutorService diskWriter;

	// Plugin-owned live catalog, set at startup like the panel's ClogIndex.
	@Nullable private volatile CaCatalog caCatalog;

	@Inject
	public LocalCaCache(Gson gson)
	{
		this(gson, newDiskWriter(), null);
	}

	LocalCaCache(Gson gson, ExecutorService diskWriter, @Nullable Filepath folder)
	{
		this.gson = gson;
		this.diskWriter = diskWriter;
		this.folder = folder;
	}

	void useFolder(@Nullable Filepath folder)
	{
		this.folder = folder;
	}

	public void setCaCatalog(@Nullable CaCatalog caCatalog)
	{
		this.caCatalog = caCatalog;
	}

	private static ExecutorService newDiskWriter()
	{
		// One queue across enable/disable cycles. The daemon exits when idle,
		// so a disabled singleton does not retain a worker thread.
		return new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(), r ->
		{
			Thread t = new Thread(r, "kill-clog-ca-disk");
			t.setDaemon(true);
			return t;
		});
	}

	/** Accepted writes drain in order, including across an immediate re-enable. */
	public void shutdown()
	{
		activePlayer = null;
		caCatalog = null;
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
	public synchronized void cacheResult(String name, Map<CombatAchievementTier, Integer> completed)
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
		if (existing != null && !existing.saveFailed && completedByTier.equals(existing.completed))
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
			data.saveFailed = true;
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
		CaCatalog catalog = caCatalog;
		return CombatAchievementResult.of(completed, totals,
			catalog != null ? catalog.totals() : null);
	}

	// Disk I/O, always on the diskWriter thread.

	private void saveToDisk(String playerName, CaData data)
	{
		Filepath folder = this.folder;
		Filepath tmp = null;
		try
		{
			if (folder == null)
			{
				throw new IOException("no data folder this session");
			}
			folder.createDirectories();
			Filepath file = folder.join(LocalClogCache.fileName(playerName));
			// Separate clients may save the same player concurrently. Each write
			// must finish its own bytes before replacing the shared final file.
			tmp = folder.createTempFile(file.getFileName() + ".", ".tmp");
			try (BufferedWriter writer = tmp.openBufferedWriter())
			{
				gson.toJson(data, writer);
			}
			LocalClogCache.atomicMove(tmp, file);
		}
		catch (IOException | JsonIOException e)
		{
			data.saveFailed = true;
			log.warn("Failed to save CA cache for '{}': {}", playerName, e.getMessage());
		}
		finally
		{
			if (tmp != null)
			{
				try
				{
					tmp.deleteIfExists();
				}
				catch (IOException e)
				{
					log.debug("Could not remove CA temporary file: {}", e.getMessage());
				}
			}
		}
	}

	private CaData loadFromDisk(String playerName)
	{
		Filepath folder = this.folder;
		Filepath file = folder == null ? null : folder.join(LocalClogCache.fileName(playerName));
		if (file == null || !file.exists())
		{
			return null;
		}
		try (BufferedReader reader = file.openBufferedReader())
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

	static class CaData
	{
		transient volatile boolean saveFailed;
		String playerName;
		String lastUpdated;
		Map<String, Integer> completed;
	}
}
