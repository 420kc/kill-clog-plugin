package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The one-time move from 2.4's name-keyed files to account-keyed own logs.
 *
 * <p>A file is an own log only with proof: its owner stamp, plus first-party
 * marks or a completed Collection Log Search. Proven copies of one account
 * merge provenance first ({@link ClogRecords#mergeOwn}); pure provider copies
 * become lookups; everything else is archived in {@code legacy/}, never
 * deleted and never read. It runs under a file lock until the layout marker
 * exists, so two clients take turns and a crash resumes on the next start.
 */
@Slf4j
final class StoreMigration
{
	static final String MARKER = ".kill-clog-layout";
	static final String LOOKUPS = "lookups";
	static final String LEGACY = "legacy";
	private static final String LOCK = ".migration.lock";
	private static final String LEDGER = ".kill-clog-identity.json";
	private static final String SIDECAR = ".displaced-";

	private StoreMigration()
	{
	}

	/**
	 * True once the store is in the account layout; false leaves it for the next
	 * start. The file lock orders clients; this monitor orders stores in one client.
	 */
	static synchronized boolean run(Gson gson, Filepath folder)
	{
		try
		{
			folder.createDirectories();
			if (folder.join(MARKER).exists())
			{
				return true;
			}
			try (FileChannel channel = folder.join(LOCK).openFileChannel(
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				FileLock ignored = channel.lock())
			{
				if (!folder.join(MARKER).exists())
				{
					migrate(gson, folder);
					folder.join(MARKER).write("2");
				}
			}
			return true;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Kill Clog will finish moving its files on the next start: {}", e.getMessage());
			return false;
		}
	}

	/** An account's own-log file: its hash as 16 hex digits, longer than any character name. */
	static String ownFileName(String hashKey)
	{
		return String.format("%016x", Long.parseLong(hashKey)) + ".json";
	}

	private static final class Candidate
	{
		final Filepath file;
		final PlayerClogData data;

		Candidate(Filepath file, PlayerClogData data)
		{
			this.file = file;
			this.data = data;
		}
	}

	private static void migrate(Gson gson, Filepath folder) throws IOException
	{
		Map<String, String> ledger = ledgerNames(gson, folder.join(LEDGER));
		Map<String, List<Candidate>> own = new LinkedHashMap<>();
		for (Filepath file : list(folder))
		{
			String name = file.getFileName();
			if (!name.endsWith(".json") || (name.startsWith(".") && !name.startsWith(SIDECAR))
				|| name.matches("[0-9a-f]{16}\\.json"))
			{
				continue;
			}
			PlayerClogData data = read(gson, file, PlayerClogData.class);
			String hash = data != null ? provenOwner(name, data) : null;
			if (hash != null)
			{
				own.computeIfAbsent(hash, h -> new ArrayList<>()).add(new Candidate(file, data));
			}
			else if (data == null || ClogRecords.hasFirstPartyMarks(data)
				|| Boolean.TRUE.equals(data.firstPartySetupComplete) || data.firstPartyByCategory == null)
			{
				archive(folder, file, LEGACY);
			}
			else
			{
				toLookups(gson, folder, file, data);
			}
		}
		for (Map.Entry<String, List<Candidate>> entry : own.entrySet())
		{
			mergeOwn(gson, folder, entry.getKey(), entry.getValue(), ledger.get(entry.getKey()));
		}
		Filepath ca = folder.join("ca");
		if (ca.isDirectory())
		{
			for (Filepath file : list(ca))
			{
				if (file.getFileName().endsWith(".json") && !file.getFileName().matches("[0-9a-f]{16}\\.json"))
				{
					archive(folder, file, LEGACY, "ca");
				}
			}
		}
		Filepath ledgerFile = folder.join(LEDGER);
		if (ledgerFile.exists())
		{
			archive(folder, ledgerFile, LEGACY);
		}
	}

	/** The stamped hash, when first-party evidence backs it and a sidecar's name agrees. */
	private static String provenOwner(String fileName, PlayerClogData data)
	{
		String hash = data.ownerHash;
		if (hash == null || !hash.matches("-?\\d{1,19}")
			|| fileName.startsWith(SIDECAR) && !fileName.startsWith(SIDECAR + hash + "-"))
		{
			return null;
		}
		boolean evidence = ClogRecords.hasFirstPartyMarks(data)
			|| Boolean.TRUE.equals(data.firstPartySetupComplete);
		return evidence ? hash : null;
	}

	private static void mergeOwn(Gson gson, Filepath folder, String hash, List<Candidate> candidates,
		String ledgerName) throws IOException
	{
		Filepath dest = folder.join(ownFileName(hash));
		PlayerClogData base = null;
		if (!LocalClogCache.absent(dest))
		{
			// An interrupted earlier run: trust it only when it is whole and this account's.
			PlayerClogData existing = read(gson, dest, PlayerClogData.class);
			if (existing != null && hash.equals(existing.ownerHash)
				&& (ClogRecords.hasFirstPartyMarks(existing) || ClogRecords.hasCompletedFirstPartySetup(existing)))
			{
				base = existing;
			}
			else
			{
				LocalClogCache.atomicMove(dest, folder.join(".unreadable-" + ownFileName(hash) + "-"
					+ System.currentTimeMillis() + ".json"));
			}
		}
		List<Candidate> ordered = new ArrayList<>(candidates);
		// Without an earlier result, the base is what 2.4 serves for this account:
		// its ledger name, then its parked copy, then the newest.
		String served = ledgerName != null ? LocalClogCache.fileName(ledgerName) : null;
		ordered.sort(Comparator.<Candidate, Boolean>comparing(c -> !c.file.getFileName().equals(served))
			.thenComparing(c -> !c.file.getFileName().startsWith(SIDECAR))
			.thenComparing(c -> c.data.lastUpdated, Comparator.nullsLast(Comparator.reverseOrder())));
		for (Candidate candidate : ordered)
		{
			base = base == null ? candidate.data : ClogRecords.mergeOwn(base, candidate.data, hash);
		}
		base.ownerHash = hash;
		base.firstPartySetupComplete = ClogRecords.hasCompletedFirstPartySetup(base);
		if (base.firstPartyByCategory == null)
		{
			base.firstPartyByCategory = new HashMap<>();
		}
		write(gson, folder, dest, base);
		for (Candidate candidate : candidates)
		{
			archive(folder, candidate.file, LEGACY);
		}
	}

	private static void toLookups(Gson gson, Filepath folder, Filepath file, PlayerClogData data) throws IOException
	{
		Filepath dest = folder.join(LOOKUPS, safeName(file.getFileName()));
		if (!LocalClogCache.absent(dest))
		{
			PlayerClogData there = read(gson, dest, PlayerClogData.class);
			if (there != null && Objects.compare(there.lastUpdated, data.lastUpdated,
				Comparator.nullsFirst(Comparator.naturalOrder())) >= 0)
			{
				// Both are regenerable provider copies; the newer one stays.
				file.deleteIfExists();
				return;
			}
		}
		folder.join(LOOKUPS).createDirectories();
		LocalClogCache.atomicMove(file, dest);
	}

	private static void archive(Filepath folder, Filepath file, String... dir) throws IOException
	{
		Filepath target = folder.join(dir[0], java.util.Arrays.copyOfRange(dir, 1, dir.length));
		target.createDirectories();
		Filepath dest = target.join(safeName(file.getFileName()));
		if (dest.exists())
		{
			dest = target.join(System.currentTimeMillis() + "-" + safeName(file.getFileName()));
		}
		LocalClogCache.atomicMove(file, dest);
	}

	/** Off Windows a 2.4 file can carry a Windows device name, which RuneLite refuses to create. */
	private static String safeName(String fileName)
	{
		String stem = fileName.substring(0, fileName.length() - ".json".length());
		return stem.matches("con|prn|aux|nul|(com|lpt)[1-9]") ? "+" + fileName : fileName;
	}

	static void write(Gson gson, Filepath folder, Filepath dest, Object data) throws IOException
	{
		Filepath tmp = folder.createTempFile(".write-", ".tmp");
		try
		{
			try (BufferedWriter writer = tmp.openBufferedWriter())
			{
				gson.toJson(data, writer);
			}
			LocalClogCache.atomicMove(tmp, dest);
		}
		finally
		{
			tmp.deleteIfExists();
		}
	}

	static <T> T read(Gson gson, Filepath file, Class<T> type)
	{
		try (BufferedReader reader = file.openBufferedReader())
		{
			return gson.fromJson(reader, type);
		}
		catch (Exception e)
		{
			log.warn("Unreadable Kill Clog file '{}': {}", file.getFileName(), e.getMessage());
			return null;
		}
	}

	/** 2.4's ledger names (hash to name key), v2 or the v1 bare map; empty when unreadable. */
	private static Map<String, String> ledgerNames(Gson gson, Filepath file)
	{
		if (!file.exists())
		{
			return Collections.emptyMap();
		}
		try (BufferedReader reader = file.openBufferedReader())
		{
			JsonObject root = gson.fromJson(reader, JsonObject.class);
			JsonObject names = root.has("names") && root.get("names").isJsonObject()
				? root.getAsJsonObject("names") : root;
			Map<String, String> out = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : names.entrySet())
			{
				if (e.getValue().isJsonPrimitive())
				{
					out.put(e.getKey(), e.getValue().getAsString());
				}
			}
			return out;
		}
		catch (Exception e)
		{
			return Collections.emptyMap();
		}
	}

	private static List<Filepath> list(Filepath dir) throws IOException
	{
		try (Stream<Filepath> walk = dir.walk(1))
		{
			return walk.filter(Filepath::isFile).collect(Collectors.toList());
		}
	}
}
