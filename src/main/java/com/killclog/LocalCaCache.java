package com.killclog;

import com.google.gson.Gson;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Multi-account, disk-backed Combat Achievement cache for this PC's own accounts.
 *
 * <p>Self CA is read straight from the game (per-tier completed counts via
 * varbits) and kept per account in {@code ca/<hash>.json}. It is the
 * authoritative source for the logged-in player and needs no RuneProfile;
 * other players come from {@link RuneProfileService}'s API. The folder loads
 * whole on this cache's disk thread when it opens.
 */
@Slf4j
@Singleton
public class LocalCaCache
{
	// Captures by own-log file name, which carries the account hash.
	private final Map<String, CaData> accounts = new ConcurrentHashMap<>();
	private final Gson gson;
	// ca/ in the plugin's folder; null keeps captures in memory for the session.
	@Nullable
	private volatile Filepath folder;
	private volatile String activePlayer;
	// The logged-in account's file, known from its first capture this session.
	private volatile String activeFile;
	private final ExecutorService diskWriter;

	// Plugin-owned live catalog, set at startup like the panel's ClogIndex.
	@Nullable private volatile CaCatalog caCatalog;

	@Inject
	public LocalCaCache(Gson gson)
	{
		this(gson, newDiskWriter());
	}

	LocalCaCache(Gson gson, ExecutorService diskWriter)
	{
		this.gson = gson;
		this.diskWriter = diskWriter;
	}

	/** Load ca/ on the disk thread; saves queued behind it land in the same folder. */
	void open(@Nullable Filepath folder)
	{
		diskWriter.execute(() ->
		{
			if (folder != null && folder.isDirectory())
			{
				try (Stream<Filepath> walk = folder.walk(1))
				{
					walk.filter(f -> f.getFileName().matches("[0-9a-f]{16}\\.json")).forEach(file ->
					{
						CaData data = StoreMigration.read(gson, file, CaData.class);
						if (data != null && data.completed != null && !data.completed.isEmpty())
						{
							accounts.putIfAbsent(file.getFileName(), data);
						}
					});
				}
				catch (IOException | RuntimeException e)
				{
					log.warn("Kill Clog could not read its CA folder: {}", e.getMessage());
				}
			}
			this.folder = folder;
		});
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
		activeFile = null;
		caCatalog = null;
	}

	public void setActivePlayer(String name)
	{
		if (name == null || !isActivePlayer(name))
		{
			activeFile = null;
		}
		activePlayer = name;
	}

	public boolean isActivePlayer(String name)
	{
		return activePlayer != null && name != null && activePlayer.equalsIgnoreCase(name);
	}

	public boolean hasDataFor(String name)
	{
		return active(name) != null;
	}

	@Nullable
	private CaData active(String name)
	{
		String file = activeFile;
		return file != null && isActivePlayer(name) ? accounts.get(file) : null;
	}

	/** Store per-tier completed counts for an account (sourced from game varbits) and persist. */
	public synchronized void cacheResult(String name, long accountHash, Map<CombatAchievementTier, Integer> completed)
	{
		if (name == null || completed == null || accountHash == -1)
		{
			return;
		}
		String file = StoreMigration.ownFileName(Long.toString(accountHash));
		activeFile = file;
		Map<String, Integer> completedByTier = new LinkedHashMap<>();
		for (Map.Entry<CombatAchievementTier, Integer> entry : completed.entrySet())
		{
			completedByTier.put(entry.getKey().name(), entry.getValue());
		}
		CaData existing = accounts.get(file);
		if (existing != null && !existing.saveFailed && completedByTier.equals(existing.completed)
			&& name.equals(existing.playerName))
		{
			return;
		}

		CaData data = new CaData();
		data.playerName = name;
		data.lastUpdated = Instant.now().toString();
		data.completed = completedByTier;
		accounts.put(file, data);
		try
		{
			diskWriter.execute(() ->
			{
				Filepath folder = this.folder;
				try
				{
					if (folder == null)
					{
						throw new IOException("no data folder this session");
					}
					folder.createDirectories();
					StoreMigration.write(gson, folder, folder.join(file), data);
				}
				catch (IOException | RuntimeException e)
				{
					data.saveFailed = true;
					log.warn("Failed to save CA cache for '{}': {}", name, e.getMessage());
				}
			});
		}
		catch (RejectedExecutionException ignored)
		{
			data.saveFailed = true;
			log.debug("CA disk write rejected (executor shutting down)");
		}
	}

	/** Stored CA for the logged-in player as a {@link CombatAchievementResult}, or null if none is held. */
	public CombatAchievementResult getCached(String name)
	{
		CaData data = active(name);
		if (data == null || data.completed == null || data.completed.isEmpty())
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

	static class CaData
	{
		transient volatile boolean saveFailed;
		String playerName;
		String lastUpdated;
		Map<String, Integer> completed;
	}
}
