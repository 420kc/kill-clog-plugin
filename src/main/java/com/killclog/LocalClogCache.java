package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import java.util.function.BooleanSupplier;
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
	private static final File DEFAULT_CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "kill-clog");

	/** Instance field so tests can point the whole disk lane at a temp dir
	 *  and actually exercise migration, parking, and recovery on real files. */
	private final File cacheDir;

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

	// ── rename continuity (local half; the server migrates its copy on the
	// next sync) ──────────────────────────────────────────────────────────
	//
	// The identity file remembers which cache file this account's hash last
	// wrote. Without it, a name change strands months of captures under the
	// old file: the panel shows the sync prompt like a fresh install, and
	// SyncService stops at "no local collection log" before a single packet
	// reaches the server's own migration machinery.
	//
	// Dot-prefixed: sanitized player keys never contain a dot, so no player
	// name (not even 'Identity') can ever collide with these files.

	private volatile Map<String, String> identityByHash;
	private volatile String pendingRenameNotice;
	// The logged-in account's hash, recorded by followNameChange: the anchor
	// for the first-party save guard below.
	private volatile String activeHashKey;

	private File identityFile()
	{
		return new File(cacheDir, ".kill-clog-identity.json");
	}

	private File sidecarFile(String hashKey, String key)
	{
		return new File(cacheDir, ".displaced-" + hashKey + "-" + getCacheFile(key).getName());
	}

	/**
	 * Follow the logged-in account onto its current name. When the hash last
	 * wrote a DIFFERENT cache file, that data migrates to the new name.
	 *
	 * Destination handling: post-crash first-party captures under the new
	 * name (same account, provably - only this client's own logged-in
	 * captures mark firstPartyByCategory) are UNIONED, destination winning
	 * per-item; a pure provider lookup copy of the name's previous owner is
	 * replaced, never mixed into this account's log.
	 *
	 * Durability: memory is authoritative for the session. Disk follows on
	 * the writer thread DIRECTLY (never through the latest-write-wins
	 * debounce, which a same-name capture inside the window would silently
	 * replace), and every destructive step gates on verified success: no
	 * checked write, no old-file delete; no delete, no identity stamp. Any
	 * failure or crash leaves the old file and the old on-disk mapping
	 * together, and the next login re-runs the migration from disk.
	 *
	 * @return the previous display name when a migration happened, else null.
	 */
	public synchronized String followNameChange(String currentRsn, long accountHash)
	{
		if (currentRsn == null || currentRsn.isBlank() || accountHash == -1)
		{
			return null;
		}
		Map<String, String> identity = loadIdentity();
		String hashKey = Long.toString(accountHash);
		activeHashKey = hashKey;
		String currentKey = cacheKey(currentRsn);
		String previousKey = identity.get(hashKey);
		if (previousKey == null)
		{
			identity.put(hashKey, currentKey);
			persistIdentityEntry(hashKey, currentKey);
			return null;
		}
		boolean sameKey = previousKey.equals(currentKey);
		if (sameKey && !sidecarFile(hashKey, currentKey).exists())
		{
			// Steady state. The sidecar check covers the account that lost a
			// displacement and later RETOOK the same name: its parked history
			// must recover even though no rename ever happened.
			return null;
		}

		// Ownership decisions see BOTH truths: this session's in-memory
		// mappings (ours may not have flushed yet), overlaid with a FRESH
		// disk read for every OTHER hash - another client on this machine
		// may have written mappings after this process last looked, and its
		// disk entries outrank our stale cache of them. Stamps only exist on
		// disk, which is fine: self entries are excluded from claim ranking.
		IdentityView diskView = readIdentityView();
		Map<String, String> freshIdentity = new HashMap<>(identity);
		for (Map.Entry<String, String> e : diskView.names.entrySet())
		{
			if (!e.getKey().equals(hashKey))
			{
				freshIdentity.put(e.getKey(), e.getValue());
			}
		}

		// Source recovery, sidecar first: if a previous displacement parked
		// this account's data, the sidecar is its canonical local copy - the
		// live file under the old key belongs to whoever owns that name NOW.
		PlayerClogData source = null;
		File sidecar = sidecarFile(hashKey, previousKey);
		boolean sourceFromSidecar = false;
		if (sidecar.exists())
		{
			source = readRecordFile(sidecar);
			sourceFromSidecar = source != null;
		}
		if (sameKey && !sourceFromSidecar)
		{
			return null; // unreadable sidecar: touch nothing, retry next login
		}
		boolean sourceFromLiveFile = false;
		if (source == null)
		{
			// The live file is only OURS to migrate when no OTHER account's
			// current name claims that key.
			if (newestClaimant(freshIdentity, diskView.stamps, previousKey, hashKey) == null)
			{
				source = players.remove(previousKey);
				if (source == null)
				{
					source = loadFromDisk(previousKey);
				}
				sourceFromLiveFile = source != null;
			}
		}
		identity.put(hashKey, currentKey);
		if (source == null)
		{
			// Nothing recoverable under the old name: mapping updates, no move.
			persistIdentityEntry(hashKey, currentKey);
			return null;
		}
		String previousDisplay = source.playerName != null ? source.playerName : previousKey;

		PlayerClogData dest = players.get(currentKey);
		if (dest == null)
		{
			dest = loadFromDisk(currentKey);
		}
		// First-party marks prove a LOCAL account captured the destination
		// data - but on a shared machine that could be a DIFFERENT local
		// account that owned this name before transferring it. The FRESH
		// identity map knows: another hash still claiming this key means the
		// data is theirs. Their latest in-memory state is snapshotted here
		// and written back CHECKED inside the migration task before the park;
		// any failure along that chain aborts the disk migration whole. All
		// disk work stays on the writer thread, whose FIFO order guarantees
		// an already-in-flight debounced save for them lands first.
		String otherHash = newestClaimant(freshIdentity, diskView.stamps, currentKey, hashKey);
		// An already-existing sidecar for the claimant means the displacement
		// happened before (and possibly crashed mid-migration): their
		// canonical copy is safe, and whatever sits at the live slot is our
		// own half-written file or a regenerable lookup cache - merge or
		// replace it, never park it over their sidecar.
		boolean displaced = otherHash != null && dest != null
			&& !sidecarFile(otherHash, currentKey).exists();
		PlayerClogData displacedCopy = null;
		if (displaced)
		{
			Runnable unflushed = pendingByPlayer.remove(currentKey);
			PlayerClogData displacedLatest = players.remove(currentKey);
			if (unflushed != null && displacedLatest != null)
			{
				displacedCopy = shallowCopy(displacedLatest);
			}
			dest = null;
		}
		else
		{
			// Any queued pre-merge snapshot would overwrite the merged file
			// after the migration writes it; every capture in it already
			// lives in the merged memory the migration itself persists.
			pendingByPlayer.remove(currentKey);
		}
		if (!sourceFromSidecar)
		{
			// Only when the old key's live file was OURS: if we recovered from
			// a sidecar, the live slot (and any pending write for it) belongs
			// to whoever holds that name now.
			pendingByPlayer.remove(previousKey);
		}

		PlayerClogData merged = hasFirstPartyMarks(dest)
			? mergeForMigration(dest, source)
			: source;
		merged.playerName = currentRsn;
		players.put(currentKey, merged);
		if (!sameKey)
		{
			pendingRenameNotice = previousDisplay;
		}

		PlayerClogData copy = shallowCopy(merged);
		boolean consumedSidecar = sourceFromSidecar;
		boolean liveSource = sourceFromLiveFile;
		PlayerClogData displacedToFlush = displacedCopy;
		try
		{
			diskWriter.execute(() -> withIdentityLock(() ->
				migrateOnDisk(currentRsn, currentKey, hashKey, previousKey, displaced,
					otherHash, consumedSidecar, sidecar, liveSource, copy, displacedToFlush)));
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: memory served this session; disk re-heals next login.
		}
		log.debug("Rename continuity: '{}' -> '{}'", previousKey, currentKey);
		return sameKey ? null : previousDisplay;
	}

	/**
	 * The migration's disk half. Runs on the writer thread UNDER the held
	 * identity lock: the decision that queued it was a snapshot, so ownership
	 * is revalidated here first - the lock holds every other client's
	 * migrations and identity writes until this one finishes. Any failure
	 * returns false before the identity stamp, and the next login re-heals
	 * from whatever state disk was left in.
	 */
	private boolean migrateOnDisk(String currentRsn, String currentKey, String hashKey,
		String oldKey, boolean parkFirst, String parkHash, boolean consumedSidecar,
		File consumedSidecarFile, boolean sourceFromLiveFile, PlayerClogData copy,
		PlayerClogData displacedToFlush)
	{
		IdentityView now = readIdentityView();
		String claimNow = newestClaimant(now.names, now.stamps, currentKey, hashKey);
		if (sourceFromLiveFile && newestClaimant(now.names, now.stamps, oldKey, hashKey) != null)
		{
			// A live-file source was only ours while nobody else's current
			// name claimed the old key. A claim that appeared since the
			// decision means the bytes we copied may be theirs - abort whole.
			return false;
		}
		if (parkFirst)
		{
			// Park under whoever the identity file says owns the slot NOW;
			// the decision-time hash is the fallback.
			String parkUnder = claimNow != null ? claimNow : parkHash;
			if (sidecarFile(parkUnder, currentKey).exists())
			{
				// Their canonical copy is already parked (a crashed earlier
				// migration got that far): the live slot is residue, and
				// parking it again would bury their real data. Fall through
				// to the checked write.
				log.debug("Displacement already parked for '{}', skipping park", currentKey);
			}
			else
			{
				if (displacedToFlush != null)
				{
					String displacedName = displacedToFlush.playerName != null
						? displacedToFlush.playerName : currentRsn;
					if (!saveToDiskChecked(displacedName, displacedToFlush))
					{
						return false; // their latest capture moves or nothing does
					}
				}
				if (!parkDisplacedFileNow(currentKey, parkUnder))
				{
					return false; // never bury another account's canonical copy
				}
			}
		}
		else if (claimNow != null && getCacheFile(currentKey).exists()
			&& !sidecarFile(claimNow, currentKey).exists())
		{
			// A foreign claim appeared after the decision and its holder's
			// data may be the live file: not ours to overwrite. When the
			// slot is empty, or their copy is already parked, writing ours
			// buries nothing - which also lets a park-then-crash retry
			// finish instead of wedging forever on the stale claim.
			return false;
		}
		if (!saveToDiskChecked(currentRsn, copy))
		{
			return false; // old file + old mapping stay: next login re-heals
		}
		if (sourceFromLiveFile)
		{
			// Revalidated above: still unclaimed, so the old file was our
			// source and is ours to remove.
			File old = getCacheFile(oldKey);
			if (old.exists() && !old.delete())
			{
				return false; // never stamp a migration that left data behind
			}
		}
		if (consumedSidecar && consumedSidecarFile.exists()
			&& !consumedSidecarFile.delete())
		{
			return false; // sidecar must not survive as a stale second copy
		}
		// Stamp inside the SAME held lock - we hold it already, and this
		// channel's lock is not reentrant. The stamp is what makes our claim
		// the newest; a failed write must not report the migration complete.
		now.names.put(hashKey, currentKey);
		now.stamps.put(hashKey, System.currentTimeMillis());
		return saveIdentity(now);
	}

	/**
	 * The hash whose CURRENT name claims this cache key - the NEWEST claim by
	 * stamp when several stale entries compete, hash order breaking exact
	 * ties - or null when nobody (except an excluded self) claims it. Pass a
	 * selfHash to skip our own entries, so a stale self-mapping can never
	 * masquerade as a foreign claim or shadow one; pass null to rank every
	 * claimant.
	 */
	private static String newestClaimant(Map<String, String> names, Map<String, Long> stamps,
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

	private PlayerClogData readRecordFile(File file)
	{
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			return gson.fromJson(reader, PlayerClogData.class);
		}
		catch (Exception e)
		{
			log.warn("Failed to read '{}': {}", file.getName(), e.getMessage());
			return null;
		}
	}

	/**
	 * One chat line per migration, consumed by the plugin's login latch
	 * regardless of whether the sync path or the latch itself triggered the
	 * move first.
	 */
	public synchronized String consumeRenameNotice()
	{
		String notice = pendingRenameNotice;
		pendingRenameNotice = null;
		return notice;
	}

	/**
	 * The rename check off the calling thread: it reads the identity file and
	 * can parse cache files, which is too much for a game tick. The chat
	 * notice arrives later via consumeRenameNotice polling.
	 */
	public void followNameChangeAsync(String currentRsn, long accountHash)
	{
		try
		{
			diskWriter.execute(() -> followNameChange(currentRsn, accountHash));
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: the next login re-checks.
		}
	}

	/**
	 * Move another account's file out of the destination slot without
	 * destroying it: their own next login recovers from the sidecar (the
	 * sidecar-first source rule above), and their server copy migrates
	 * regardless. Runs INSIDE the migration disk task; a failure here aborts
	 * the whole disk migration so their canonical copy is never buried.
	 */
	private boolean parkDisplacedFileNow(String key, String otherHash)
	{
		File file = getCacheFile(key);
		if (!file.exists())
		{
			return true; // nothing on disk to protect
		}
		File parked = sidecarFile(otherHash, key);
		try
		{
			atomicMove(file, parked);
			return true;
		}
		catch (IOException e)
		{
			log.warn("Could not park displaced cache file '{}': {}", file.getName(), e.getMessage());
			return false;
		}
	}

	private static boolean hasFirstPartyMarks(PlayerClogData data)
	{
		if (data == null || data.firstPartyByCategory == null)
		{
			return false;
		}
		for (List<Integer> ids : data.firstPartyByCategory.values())
		{
			if (ids != null && !ids.isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Union for the post-crash heal: destination (the newer writing) wins
	 * per-item and per-category conflicts; everything the source alone knows
	 * is carried over. Nothing is discarded.
	 */
	private static PlayerClogData mergeForMigration(PlayerClogData dest, PlayerClogData source)
	{
		// A legacy source (null marks) is wholly first-party by definition -
		// the class contract grandfathers it at first capture. Materialize
		// that grandfather EXPLICITLY before the mark union, or the merged
		// file's marks will not cover the legacy items and the sync filter
		// would silently drop the migrated history from every future push.
		if (source.firstPartyByCategory == null && source.obtained != null)
		{
			Map<String, List<Integer>> grandfathered = new ConcurrentHashMap<>();
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : source.obtained.entrySet())
			{
				List<Integer> ids = new ArrayList<>();
				for (ClogResult.ClogItem item : e.getValue())
				{
					ids.add(item.getId());
				}
				grandfathered.put(e.getKey(), ids);
			}
			source.firstPartyByCategory = grandfathered;
		}
		if (source.categories != null)
		{
			if (dest.categories == null)
			{
				dest.categories = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<Integer>> e : source.categories.entrySet())
			{
				dest.categories.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		if (source.obtained != null)
		{
			if (dest.obtained == null)
			{
				dest.obtained = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<ClogResult.ClogItem>> e : source.obtained.entrySet())
			{
				List<ClogResult.ClogItem> existing = dest.obtained.get(e.getKey());
				if (existing == null)
				{
					dest.obtained.put(e.getKey(), e.getValue());
					continue;
				}
				for (ClogResult.ClogItem item : e.getValue())
				{
					boolean present = false;
					for (ClogResult.ClogItem have : existing)
					{
						if (have.getId() == item.getId())
						{
							present = true;
							break;
						}
					}
					if (!present)
					{
						existing.add(item);
					}
				}
			}
		}
		if (source.firstPartyByCategory != null)
		{
			if (dest.firstPartyByCategory == null)
			{
				dest.firstPartyByCategory = new ConcurrentHashMap<>();
			}
			for (Map.Entry<String, List<Integer>> e : source.firstPartyByCategory.entrySet())
			{
				dest.firstPartyByCategory.merge(e.getKey(), e.getValue(), (a, b) ->
				{
					List<Integer> union = new ArrayList<>(a);
					for (Integer id : b)
					{
						if (!union.contains(id))
						{
							union.add(id);
						}
					}
					return union;
				});
			}
		}
		dest.uniqueObtained = Math.max(dest.uniqueObtained, source.uniqueObtained);
		dest.uniqueTotal = Math.max(dest.uniqueTotal, source.uniqueTotal);
		if (dest.lastChanged == null
			|| (source.lastChanged != null && source.lastChanged.compareTo(dest.lastChanged) > 0))
		{
			dest.lastChanged = source.lastChanged;
		}
		if (dest.providerAccountType == null)
		{
			dest.providerAccountType = source.providerAccountType;
		}
		return dest;
	}

	/** Checked write: temp file + atomic move, so a partial write can never
	 *  pass for success and authorize the old file's deletion. */
	private boolean saveToDiskChecked(String playerName, PlayerClogData data)
	{
		try
		{
			if (!cacheDir.exists())
			{
				cacheDir.mkdirs();
			}
			File file = getCacheFile(playerName);
			File tmp = new File(cacheDir, file.getName() + ".tmp");
			try (BufferedWriter writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
			{
				gson.toJson(data, writer);
			}
			atomicMove(tmp, file);
			return true;
		}
		catch (IOException e)
		{
			log.warn("Checked cache write failed for '{}': {}", playerName, e.getMessage());
			return false;
		}
	}

	private void persistIdentityEntry(String hashKey, String key)
	{
		try
		{
			diskWriter.execute(() -> persistIdentityEntryOnWriter(hashKey, key));
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: the mapping re-records on the next login.
		}
	}

	/**
	 * On the writer thread, under an OS-level file lock: fresh read,
	 * single-entry overlay, atomic write. The in-process writer thread
	 * serializes THIS client; the FileLock serializes ACROSS clients, so two
	 * JVMs sharing the machine can never interleave read-modify-write and
	 * erase each other's mappings, and the atomic move means no reader ever
	 * sees partial JSON.
	 */
	private void persistIdentityEntryOnWriter(String hashKey, String key)
	{
		withIdentityLock(() ->
		{
			IdentityView disk = readIdentityView();
			disk.names.put(hashKey, key);
			disk.stamps.put(hashKey, System.currentTimeMillis());
			return saveIdentity(disk);
		});
	}

	/**
	 * Runs the action under the cross-process identity FileLock (creating the
	 * cache dir if needed). NOT reentrant: the action must never take the
	 * lock again. Returns the action's result, or false when the lock could
	 * not be taken.
	 */
	private boolean withIdentityLock(BooleanSupplier action)
	{
		File lockFile = new File(cacheDir, ".kill-clog-identity.lock");
		try
		{
			if (!cacheDir.exists())
			{
				cacheDir.mkdirs();
			}
			try (FileChannel channel = FileChannel.open(lockFile.toPath(),
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

	/** Test hook: model a pre-marking legacy store file (marks null). */
	void nullifyFirstPartyMarksForTest(String rsn)
	{
		PlayerClogData data = players.get(cacheKey(rsn));
		if (data != null)
		{
			data.firstPartyByCategory = null;
		}
	}

	/** Test hook: preload the identity map so tests never touch the real file. */
	void seedIdentityForTest(Map<String, String> seed)
	{
		identityByHash = new ConcurrentHashMap<>(seed);
	}

	private Map<String, String> loadIdentity()
	{
		Map<String, String> identity = identityByHash;
		if (identity != null)
		{
			return identity;
		}
		identity = new ConcurrentHashMap<>(readIdentityFile());
		identityByHash = identity;
		return identity;
	}

	/**
	 * Identity file, format v2: hash-to-current-name plus a per-hash stamp of
	 * when that hash last asserted its name. Stale entries are NEVER cleared
	 * when a name changes hands - a displaced account's entry is its own
	 * recovery pointer - so several hashes can claim one name at once, and
	 * the stamps are what order those claims: the newest claimant is the live
	 * owner. A legacy v1 file (a bare hash-to-name map) lifts with stamp 0.
	 */
	private static final class IdentityView
	{
		private final Map<String, String> names = new HashMap<>();
		private final Map<String, Long> stamps = new HashMap<>();
	}

	private Map<String, String> readIdentityFile()
	{
		return readIdentityView().names;
	}

	private IdentityView readIdentityView()
	{
		IdentityView view = new IdentityView();
		if (!identityFile().exists())
		{
			return view;
		}
		try (BufferedReader reader = Files.newBufferedReader(identityFile().toPath(), StandardCharsets.UTF_8))
		{
			JsonObject root = gson.fromJson(reader, JsonObject.class);
			if (root == null)
			{
				return view;
			}
			JsonObject names = root.has("names") && root.get("names").isJsonObject()
				? root.getAsJsonObject("names") : null;
			if (names == null)
			{
				// legacy v1: the root IS the name map
				readIdentityNames(root, view.names);
				return view;
			}
			readIdentityNames(names, view.names);
			if (root.has("stamps") && root.get("stamps").isJsonObject())
			{
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("stamps").entrySet())
				{
					if (e.getValue().isJsonPrimitive())
					{
						view.stamps.put(e.getKey(), e.getValue().getAsLong());
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load rename identity file: {}", e.getMessage());
		}
		return view;
	}

	private static void readIdentityNames(JsonObject source, Map<String, String> into)
	{
		for (Map.Entry<String, JsonElement> e : source.entrySet())
		{
			if (e.getValue().isJsonPrimitive())
			{
				into.put(e.getKey(), e.getValue().getAsString());
			}
		}
	}

	private boolean saveIdentity(IdentityView view)
	{
		try
		{
			if (!cacheDir.exists())
			{
				cacheDir.mkdirs();
			}
			Map<String, Object> root = new HashMap<>();
			root.put("version", 2);
			root.put("names", view.names);
			root.put("stamps", view.stamps);
			File tmp = new File(cacheDir, identityFile().getName() + ".tmp");
			try (BufferedWriter writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
			{
				gson.toJson(root, writer);
			}
			atomicMove(tmp, identityFile());
			return true;
		}
		catch (IOException e)
		{
			log.warn("Failed to save rename identity file: {}", e.getMessage());
			return false;
		}
	}

	/** Genuinely atomic where the filesystem allows it; plain replace as the
	 *  documented fallback (some filesystems refuse ATOMIC_MOVE). */
	private static void atomicMove(File from, File to) throws IOException
	{
		try
		{
			Files.move(from.toPath(), to.toPath(),
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
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
		this(gson, diskWriter, DEFAULT_CACHE_DIR);
	}

	LocalClogCache(Gson gson, ScheduledExecutorService diskWriter, File cacheDir)
	{
		this.gson = gson;
		this.diskWriter = diskWriter;
		this.cacheDir = cacheDir;
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

	public synchronized void setActivePlayer(String name)
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

	/**
	 * Provider-lane write (Temple/RuneProfile snapshots for looked-up names).
	 * Never marks first-party and never fires the sync trigger: provider data
	 * lands in the display cache but can never ride a killclog.com push.
	 */
	public synchronized void cacheResult(ClogResult result)
	{
		cacheResult(result, false);
	}

	/**
	 * First-party bulk-capture landing (the chalice walk). Marks every
	 * obtained item as client-observed and fires the sync trigger - the
	 * largest payload of all must schedule a push like any other capture.
	 */
	public synchronized void cacheFirstPartyResult(ClogResult result)
	{
		cacheResult(result, true);
	}

	private synchronized void cacheResult(ClogResult result, boolean firstParty)
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
		if (existing == null)
		{
			// Every genuinely NEW entry starts explicitly marked-empty,
			// whichever lane creates it: null is reserved for stores loaded
			// from legacy pre-marking disk files. Without this, a
			// zero-obtained first-party capture (fresh account, empty walk)
			// would birth a null-marker store that later provider writes
			// treat as legacy and ship wholesale.
			data.firstPartyByCategory = new HashMap<>();
		}
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
		// Upward-only, like the totals above: a provider snapshot must not
		// drag the last-updated notice behind a live merge stamped moments ago.
		bumpLastChanged(data, result.getLastChanged());
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
			List<ClogResult.ClogItem> merged;
			if (firstParty || data.firstPartyByCategory == null)
			{
				// Capture landings, and legacy null-marker stores (whose
				// whole content is implicitly first-party), merge as before.
				merged = preserveItemMetadata(entry.getValue(), data.obtained.get(cat));
			}
			else
			{
				// Provider lane over a marked store: first-party RECORDS are
				// inviolable, not just their ids. A provider refresh must
				// neither replace a marked record (its quantity and
				// provenance are client-observed truth) nor remove one that
				// a stale provider list no longer carries - either would
				// launder provider content through a surviving mark.
				merged = mergeProviderIntoMarked(entry.getValue(),
					data.obtained.get(cat), categoryMarks(data, cat));
			}
			data.obtained.put(cat, merged);
		}
		for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
		{
			data.categories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}

		if (firstParty)
		{
			for (Map.Entry<String, List<ClogResult.ClogItem>> entry
				: result.getObtainedItems().entrySet())
			{
				for (ClogResult.ClogItem item : entry.getValue())
				{
					markFirstParty(data, entry.getKey(), item.getId());
				}
			}
		}
		// (New-entry marker initialization happens at entry creation above;
		// an EXISTING null marker is a legacy store and keeps its grandfather
		// rights - a provider lookup must not silently revoke them.)

		players.put(key, data);
		final PlayerClogData snapshot = shallowCopy(data);
		submitDiskWrite(name, () -> saveToDisk(name, snapshot));
		log.debug("Cached clog data for '{}' ({} categories)", name, data.obtained.size());
		if (firstParty)
		{
			notifyFirstPartyChanged();
		}
	}

	/**
	 * Mark an item as client-observed in one category. A null marker map
	 * means a legacy pre-marking store built by this client's own captures:
	 * grandfather everything obtained at that moment before adding the new
	 * mark.
	 */
	private static void markFirstParty(PlayerClogData data, String categoryKey, int itemId)
	{
		if (data.firstPartyByCategory == null)
		{
			Map<String, List<Integer>> grandfathered = new HashMap<>();
			if (data.obtained != null)
			{
				for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
				{
					List<Integer> ids = new ArrayList<>();
					for (ClogResult.ClogItem item : entry.getValue())
					{
						if (!ids.contains(item.getId()))
						{
							ids.add(item.getId());
						}
					}
					grandfathered.put(entry.getKey(), ids);
				}
			}
			data.firstPartyByCategory = grandfathered;
		}
		List<Integer> marks = data.firstPartyByCategory.computeIfAbsent(categoryKey,
			ignored -> new ArrayList<>());
		if (!marks.contains(itemId))
		{
			marks.add(itemId);
		}
	}

	private static List<Integer> categoryMarks(PlayerClogData data, String categoryKey)
	{
		if (data.firstPartyByCategory == null)
		{
			return null;
		}
		List<Integer> marks = data.firstPartyByCategory.get(categoryKey);
		return marks != null ? marks : Collections.emptyList();
	}

	public synchronized void mergeCategory(String playerName, String categoryKey,
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
		data.obtained.put(categoryKey,
			preserveItemMetadata(obtained, data.obtained.get(categoryKey)));
		for (ClogResult.ClogItem item : obtained)
		{
			markFirstParty(data, categoryKey, item.getId());
		}

		final PlayerClogData snapshot = shallowCopy(data);
		submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot));
		log.debug("Merged category '{}' for '{}': {}/{} obtained",
			categoryKey, playerName, obtained.size(), allItems.size());
		if (!obtained.isEmpty())
		{
			notifyFirstPartyChanged();
		}
	}

	// Fires after any in-client observation lands (bulk page capture, live
	// unlock), whatever path delivered it - the killclog.com sync trigger
	// lives here at the data seam so no capture route can be forgotten.
	// The listener only schedules a debounced task; it must stay cheap and
	// must not call back into this cache.
	private volatile Runnable firstPartyChangedListener;

	public void setFirstPartyChangedListener(Runnable listener)
	{
		this.firstPartyChangedListener = listener;
	}

	private void notifyFirstPartyChanged()
	{
		Runnable listener = firstPartyChangedListener;
		if (listener != null)
		{
			listener.run();
		}
	}

	/**
	 * Provider merge over a marked store: existing records for MARKED ids are
	 * kept verbatim and cannot be removed; incoming provider records for
	 * marked ids are dropped entirely (marked content only ever enters via
	 * capture paths). Unmarked records keep the old provider-merge semantics.
	 */
	private static List<ClogResult.ClogItem> mergeProviderIntoMarked(
		List<ClogResult.ClogItem> incoming, List<ClogResult.ClogItem> existing,
		List<Integer> marks)
	{
		List<ClogResult.ClogItem> keptMarked = new ArrayList<>();
		List<ClogResult.ClogItem> unmarkedExisting = new ArrayList<>();
		if (existing != null)
		{
			for (ClogResult.ClogItem item : existing)
			{
				if (marks.contains(item.getId()))
				{
					keptMarked.add(item);
				}
				else
				{
					unmarkedExisting.add(item);
				}
			}
		}
		List<ClogResult.ClogItem> unmarkedIncoming = new ArrayList<>();
		if (incoming != null)
		{
			for (ClogResult.ClogItem item : incoming)
			{
				if (!marks.contains(item.getId()))
				{
					unmarkedIncoming.add(item);
				}
			}
		}
		List<ClogResult.ClogItem> merged = new ArrayList<>(keptMarked);
		merged.addAll(preserveItemMetadata(unmarkedIncoming, unmarkedExisting));
		return merged;
	}

	/**
	 * Replace an obtained list while carrying forward per-item metadata the
	 * incoming entries lack. Widget captures and provider results rebuild
	 * items bare (no date, no obtained-at kc), and a resync must never cost
	 * a drop its provenance. Incoming values win whenever present.
	 */
	private static List<ClogResult.ClogItem> preserveItemMetadata(
		List<ClogResult.ClogItem> incoming, List<ClogResult.ClogItem> existing)
	{
		if (incoming == null)
		{
			return new ArrayList<>();
		}
		if (existing == null || existing.isEmpty())
		{
			return new ArrayList<>(incoming);
		}
		Map<Integer, ClogResult.ClogItem> priorById = new HashMap<>();
		for (ClogResult.ClogItem item : existing)
		{
			priorById.putIfAbsent(item.getId(), item);
		}
		List<ClogResult.ClogItem> merged = new ArrayList<>(incoming.size());
		for (ClogResult.ClogItem item : incoming)
		{
			ClogResult.ClogItem prior = priorById.get(item.getId());
			if (prior == null)
			{
				merged.add(item);
				continue;
			}
			String date = item.getDate() != null ? item.getDate() : prior.getDate();
			int kc = item.getObtainedAtKc() > 0 ? item.getObtainedAtKc() : prior.getObtainedAtKc();
			String from = item.getObtainedFrom() != null ? item.getObtainedFrom() : prior.getObtainedFrom();
			merged.add(new ClogResult.ClogItem(item.getId(), item.getCount(), date, kc, from));
		}
		return merged;
	}

	public boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems)
	{
		return mergeObtainedItem(playerName, itemId, categoryKeys, categoryItems, 0, null);
	}

	public synchronized boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems,
		int obtainedAtKc, String obtainedFrom)
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
				// Dated at the moment it happens: undated items are invisible
				// to the recents shelf, which is how a fresh drop could go
				// missing while months-old provider dates still showed.
				// Format matches the provider date strings so sorting and
				// display stay uniform.
				String unlockDate = liveUnlockDate();
				ClogResult.ClogItem unlocked = new ClogResult.ClogItem(itemId, 1, unlockDate,
					obtainedAtKc, obtainedFrom);
				obtained.add(unlocked);
				data.obtained.put(categoryKey, obtained);
				markFirstParty(data, categoryKey, itemId);
				// The summary's last-updated notice reads lastChanged; a live
				// unlock is exactly such a change.
				bumpLastChanged(data, unlockDate);
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
			notifyFirstPartyChanged();
		}
		return changed;
	}

	private static final DateTimeFormatter LIVE_UNLOCK_DATE_FMT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static String liveUnlockDate()
	{
		return LocalDateTime.now(ZoneOffset.UTC).format(LIVE_UNLOCK_DATE_FMT);
	}

	// Upward-only. Every date here shares the yyyy-MM-dd HH:mm:ss shape, so
	// string order is chronological order.
	private static void bumpLastChanged(PlayerClogData data, String date)
	{
		if (date != null && (data.lastChanged == null || date.compareTo(data.lastChanged) > 0))
		{
			data.lastChanged = date;
		}
	}

	/** Newest obtained-item date across all categories, or null when none carry one. */
	/* package */ static String newestObtainedDate(Map<String, List<ClogResult.ClogItem>> obtained)
	{
		String newest = null;
		for (List<ClogResult.ClogItem> items : obtained.values())
		{
			for (ClogResult.ClogItem item : items)
			{
				String date = item.getDate();
				if (date != null && (newest == null || date.compareTo(newest) > 0))
				{
					newest = date;
				}
			}
		}
		return newest;
	}

	/**
	 * Overlay provider dates onto cached items that have none. Membership and
	 * counts never change here: the chalice and live merges own those. This
	 * heals the recents shelf for items merged before live-unlock dating
	 * existed, or obtained while the plugin was off.
	 */
	public synchronized boolean mergeProviderDates(String playerName,
		Map<String, List<ClogResult.ClogItem>> providerItems)
	{
		if (playerName == null || providerItems == null || providerItems.isEmpty())
		{
			return false;
		}
		PlayerClogData data = players.get(cacheKey(playerName));
		if (data == null || data.obtained == null)
		{
			return false;
		}

		Map<Integer, String> providerDates = new HashMap<>();
		for (List<ClogResult.ClogItem> items : providerItems.values())
		{
			for (ClogResult.ClogItem item : items)
			{
				if (item.getDate() != null)
				{
					providerDates.putIfAbsent(item.getId(), item.getDate());
				}
			}
		}
		if (providerDates.isEmpty())
		{
			return false;
		}

		boolean changed = false;
		String newestApplied = null;
		for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
		{
			List<ClogResult.ClogItem> items = new ArrayList<>(entry.getValue());
			boolean listChanged = false;
			for (int i = 0; i < items.size(); i++)
			{
				ClogResult.ClogItem item = items.get(i);
				String date = item.getDate() == null ? providerDates.get(item.getId()) : null;
				if (date != null)
				{
					items.set(i, new ClogResult.ClogItem(item.getId(), item.getCount(), date,
						item.getObtainedAtKc(), item.getObtainedFrom()));
					listChanged = true;
					if (newestApplied == null || date.compareTo(newestApplied) > 0)
					{
						newestApplied = date;
					}
				}
			}
			if (listChanged)
			{
				data.obtained.put(entry.getKey(), items);
				changed = true;
			}
		}

		if (changed)
		{
			// A healed date can outrank the notice's current stamp; the shelf
			// and the last-updated line must tell the same story.
			bumpLastChanged(data, newestApplied);
			data.lastUpdated = Instant.now().toString();
			final PlayerClogData snapshot = shallowCopy(data);
			submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot));
			log.debug("Merged provider dates into local clog cache for '{}'", playerName);
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

	public synchronized boolean hasObtainedItem(String playerName, int itemId, List<String> categoryKeys)
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
	 * The obtained item carrying obtained-at-kc provenance for this player, or
	 * null when the item is unobtained or its kc was never captured. Provenance
	 * is recorded from live unlocks on this client, so only accounts played
	 * here can resolve.
	 */
	public synchronized ClogResult.ClogItem provenancedItem(String playerName, List<Integer> itemIds)
	{
		if (playerName == null || itemIds == null)
		{
			return null;
		}
		PlayerClogData data = players.get(cacheKey(playerName));
		if (data == null || data.obtained == null)
		{
			return null;
		}
		for (List<ClogResult.ClogItem> obtained : data.obtained.values())
		{
			for (ClogResult.ClogItem item : obtained)
			{
				if (item.getObtainedAtKc() > 0 && itemIds.contains(item.getId()))
				{
					return item;
				}
			}
		}
		return null;
	}

	/**
	 * Live-unlock totals update: raises only. The unlock-time varp read can
	 * lag the chat message by a tick, and lowering here would revert the
	 * unique bump the merge just made. Chalice sync stays the downward
	 * authority.
	 */
	public synchronized boolean updateTotalsUpward(String playerName, int obtained, int total)
	{
		PlayerClogData data = playerName != null ? players.get(cacheKey(playerName)) : null;
		if (data == null)
		{
			return false;
		}
		// Zero is no-signal (updateTotals ignores it); without this guard a
		// partial cache with -1 totals would report 0 > -1 as a change and
		// trigger a redundant full panel lookup.
		int risenObtained = obtained > 0 && obtained > data.uniqueObtained ? obtained : -1;
		int risenTotal = total > 0 && total > data.uniqueTotal ? total : -1;
		if (risenObtained < 0 && risenTotal < 0)
		{
			return false;
		}
		updateTotals(playerName, risenObtained, risenTotal);
		return true;
	}

	public synchronized void updateTotals(String playerName, int obtained, int total)
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

	public synchronized boolean hasDataFor(String playerName)
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

	public synchronized ClogResult toClogResult(String playerName, Map<Integer, String> itemNames)
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

	/**
	 * Whether this player's store holds anything the sync payload would
	 * actually carry: at least one obtained record its own category observed
	 * first-hand, or a legacy markless store (which ships whole). Provider
	 * caches and empty first walks both answer false - the sync chalice and
	 * the automatic sync triggers key off THIS, never off mere cache
	 * presence.
	 */
	public synchronized boolean hasFirstPartyDataFor(String playerName)
	{
		if (playerName == null)
		{
			return false;
		}
		PlayerClogData data = players.get(cacheKey(playerName));
		if (data == null || data.obtained == null || data.obtained.isEmpty())
		{
			return false;
		}
		if (data.firstPartyByCategory == null)
		{
			return true;
		}
		for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
		{
			List<Integer> marks = data.firstPartyByCategory.get(entry.getKey());
			if (marks == null || marks.isEmpty())
			{
				continue;
			}
			for (ClogResult.ClogItem item : entry.getValue())
			{
				if (marks.contains(item.getId()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** {@link #hasFirstPartyDataFor} for the logged-in player. */
	public boolean hasFirstPartyDataForActive()
	{
		return hasFirstPartyDataFor(activePlayer);
	}

	/**
	 * The sync payload's view of the store: obtained items filtered to what
	 * this client observed first-hand. Provider-cached items (looked-up names,
	 * pre-login lookups) are structurally excluded, so the payload can only
	 * carry data the etiquette canon lets it claim. A legacy pre-marking store
	 * (null marker) is treated as all-capture: those files were built by this
	 * client's own walks, and requiring a re-walk would discard proof the
	 * player already earned.
	 */
	public synchronized ClogResult toFirstPartySyncResult(String playerName)
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

		Map<String, List<ClogResult.ClogItem>> obtainedCopy = new HashMap<>();
		for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
		{
			List<Integer> marks = categoryMarks(data, entry.getKey());
			List<ClogResult.ClogItem> kept = new ArrayList<>();
			for (ClogResult.ClogItem item : entry.getValue())
			{
				// Marks are category-scoped: a record ships only when THIS
				// category observed it, so a provider record of the same id
				// in another category can never ride a mark earned elsewhere.
				if (marks == null || marks.contains(item.getId()))
				{
					kept.add(item);
				}
			}
			if (!kept.isEmpty())
			{
				obtainedCopy.put(entry.getKey(), kept);
			}
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
			new HashMap<>(),
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
		// First-party bytes under a name this machine's identity file has
		// since awarded to a newer claimant would land on the new owner's
		// slot - our own JVM only finds out at its next rename check, so the
		// check happens here, under the same lock migrations hold. Lookup
		// caches (no marks) are regenerable and skip the guard.
		if (hasFirstPartyMarks(data) && activeHashKey != null)
		{
			String self = activeHashKey;
			String key = cacheKey(playerName);
			boolean allowed = withIdentityLock(() ->
			{
				IdentityView disk = readIdentityView();
				String winner = newestClaimant(disk.names, disk.stamps, key, null);
				return winner == null || winner.equals(self);
			});
			if (!allowed)
			{
				log.debug("Skipped save for '{}': the name now belongs to another local account",
					playerName);
				return;
			}
		}
		try
		{
			if (!cacheDir.exists())
			{
				cacheDir.mkdirs();
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
				// Files written before live unlocks bumped lastChanged can hold
				// items newer than the stamp; heal on load so the last-updated
				// notice never trails the shelf.
				bumpLastChanged(data, newestObtainedDate(data.obtained));
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
		return new File(cacheDir, sanitized + ".json");
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
		if (src.firstPartyByCategory != null)
		{
			copy.firstPartyByCategory = new HashMap<>();
			for (Map.Entry<String, List<Integer>> entry : src.firstPartyByCategory.entrySet())
			{
				copy.firstPartyByCategory.put(entry.getKey(), new ArrayList<>(entry.getValue()));
			}
		}
		else
		{
			copy.firstPartyByCategory = null;
		}
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
		/**
		 * Per-category item ids this CLIENT observed first-hand (bulk
		 * capture, page capture, live unlock). The sync payload ships only
		 * records marked IN THEIR OWN CATEGORY - a global id mark would let
		 * a provider record of the same item in another category launder
		 * through (multi-category items are routine: clue rares span pages).
		 * Null means a legacy pre-marking store file: those were built by
		 * this client's own captures, so the store ships whole and the first
		 * capture grandfathers everything obtained at that moment. Live
		 * provider writes initialize the field EMPTY instead, which never
		 * grandfathers.
		 */
		Map<String, List<Integer>> firstPartyByCategory;
	}
}
