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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The machine-local rename identity ledger: which account hash currently
 * claims which cache-file name, and when each claim was last asserted.
 *
 * Format v2: names plus a per-hash stamp of when that hash last asserted its
 * current name. Stale entries are NEVER cleared when a name changes hands - a
 * displaced account's entry is its own recovery pointer - so several hashes
 * can claim one name at once, and the stamps are what order those claims: the
 * newest claimant is the live owner. A legacy v1 file (a bare hash-to-name
 * map) lifts with stamp 0.
 */
@Slf4j
class IdentityLedger
{
	static final class View
	{
		final Map<String, String> names = new HashMap<>();
		final Map<String, Long> stamps = new HashMap<>();
		boolean readable = true;
	}

	// One machine, one clock: a stamp this far ahead cannot be legitimate.
	private static final long STAMP_FUTURE_TOLERANCE_MS = 365L * 24 * 60 * 60 * 1000;

	static final String FILE = ".kill-clog-identity.json";
	static final String LOCK = ".kill-clog-identity.lock";

	private final Gson gson;
	// Null for a session whose folder could not be opened: nothing is read,
	// saved or locked, so every claim waits for the next start.
	@Nullable
	private final Filepath folder;

	IdentityLedger(Gson gson, @Nullable Filepath folder)
	{
		this.gson = gson;
		this.folder = folder;
	}

	View read()
	{
		View view = new View();
		if (folder == null)
		{
			view.readable = false;
			return view;
		}
		Filepath file = folder.join(IdentityLedger.FILE);
		if (!file.exists())
		{
			return view;
		}
		try (BufferedReader reader = file.openBufferedReader())
		{
			JsonObject root = gson.fromJson(reader, JsonObject.class);
			if (root == null)
			{
				throw new IOException("Empty identity ledger");
			}
			if ((root.has("version") || root.has("names"))
				&& (!root.has("names") || !root.get("names").isJsonObject()))
			{
				throw new IOException("Invalid identity names");
			}
			JsonObject names = root.has("names") && root.get("names").isJsonObject()
				? root.getAsJsonObject("names") : null;
			if (names == null)
			{
				// legacy v1: the root IS the name map
				readNames(root, view.names);
				return view;
			}
			readNames(names, view.names);
			if (root.has("stamps") && !root.get("stamps").isJsonObject())
			{
				throw new IOException("Invalid identity stamps");
			}
			if (root.has("stamps") && root.get("stamps").isJsonObject())
			{
				// A stamp meaningfully in the future is corrupt (one machine,
				// one clock): demote it to 0 so it loses all authority, or a
				// forged/damaged MAX-ish value would outrank every legitimate
				// claim forever.
				long ceiling = System.currentTimeMillis() + STAMP_FUTURE_TOLERANCE_MS;
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("stamps").entrySet())
				{
					if (e.getValue().isJsonPrimitive())
					{
						long stamp = e.getValue().getAsLong();
						view.stamps.put(e.getKey(), stamp > ceiling ? 0L : stamp);
					}
				}
			}
		}
		catch (Exception e)
		{
			view.readable = false;
			view.names.clear();
			view.stamps.clear();
			log.warn("Failed to load rename identity file: {}", e.getMessage());
		}
		return view;
	}

	private static void readNames(JsonObject source, Map<String, String> into)
	{
		for (Map.Entry<String, JsonElement> e : source.entrySet())
		{
			if (!e.getValue().isJsonPrimitive())
			{
				throw new IllegalArgumentException("Invalid identity name");
			}
			into.put(e.getKey(), e.getValue().getAsString());
		}
	}

	boolean save(View view)
	{
		if (!view.readable)
		{
			return false;
		}
		try
		{
			folder.createDirectories();
			Map<String, Object> root = new HashMap<>();
			root.put("version", 2);
			root.put("names", view.names);
			root.put("stamps", view.stamps);
			Filepath tmp = folder.join(IdentityLedger.FILE + ".tmp");
			try (BufferedWriter writer = tmp.openBufferedWriter())
			{
				gson.toJson(root, writer);
			}
			LocalClogCache.atomicMove(tmp, folder.join(IdentityLedger.FILE));
			return true;
		}
		catch (IOException e)
		{
			log.warn("Failed to save rename identity file: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Runs the action under the cross-process identity FileLock (creating the
	 * cache dir if needed). NOT reentrant: the action must never take the
	 * lock again. Returns the action's result, or false when the lock could
	 * not be taken.
	 */
	boolean withLock(BooleanSupplier action)
	{
		if (folder == null)
		{
			return false;
		}
		try
		{
			folder.createDirectories();
			try (FileChannel channel = folder.join(IdentityLedger.LOCK).openFileChannel(
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				FileLock lock = channel.lock())
			{
				return action.getAsBoolean();
			}
		}
		catch (Exception e)
		{
			log.warn("Identity-locked operation failed (re-heals next login): {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Strictly newer than every existing claim on the key - the identity
	 * lock serializes writers, so insertion order IS assertion order and
	 * same-millisecond ties cannot exist - and never behind the wall clock.
	 */
	static long nextStamp(View view, String key)
	{
		long stamp = System.currentTimeMillis();
		for (Map.Entry<String, String> e : view.names.entrySet())
		{
			if (key.equals(e.getValue()))
			{
				long prior = view.stamps.getOrDefault(e.getKey(), 0L);
				// Saturate instead of overflowing: a corrupt MAX_VALUE stamp
				// must never leave the prior claimant permanently newer.
				long bumped = prior >= Long.MAX_VALUE - 1 ? Long.MAX_VALUE : prior + 1;
				stamp = Math.max(stamp, bumped);
			}
		}
		return stamp;
	}

	/**
	 * The hash whose CURRENT name claims this cache key - the NEWEST claim by
	 * stamp when several stale entries compete, hash order breaking exact
	 * ties - or null when nobody (except an excluded self) claims it. Pass a
	 * selfHash to skip our own entries, so a stale self-mapping can never
	 * masquerade as a foreign claim or shadow one; pass null to rank every
	 * claimant.
	 */
	static String newestClaimant(Map<String, String> names, Map<String, Long> stamps,
		String key, String selfHash)
	{
		String best = null;
		long bestAt = -1;
		for (Map.Entry<String, String> e : names.entrySet())
		{
			if (!key.equals(e.getValue()) || e.getKey().equals(selfHash))
			{
				continue;
			}
			long at = stamps.getOrDefault(e.getKey(), 0L);
			if (at > bestAt || (at == bestAt && best != null && e.getKey().compareTo(best) > 0))
			{
				best = e.getKey();
				bestAt = at;
			}
		}
		return best;
	}
}
