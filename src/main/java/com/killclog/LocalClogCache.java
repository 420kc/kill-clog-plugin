package com.killclog;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
	// next sync). Claim semantics live on IdentityLedger; without this, a
	// name change strands months of captures under the old file and sync
	// dies at "no local collection log". Dot-prefixed control files:
	// sanitized player keys never contain a dot, so no name collides.

	private volatile Map<String, String> identityByHash;
	private final AtomicReference<String> pendingRenameNotice = new AtomicReference<>();
	// The logged-in account's hash: the anchor for the save guard below.
	private volatile String activeHashKey;
	private final IdentityLedger ledger;
	// How long the sync pre-flight waits for the disk verdict.
	private volatile long syncVerdictTimeoutMs = 10_000;
	// Bumped at logout: queued rename checks from a dead session must not run
	// and restore its anchor over the next session's.
	private final AtomicLong sessionEpoch = new AtomicLong();
	// Slots whose ownership arbitration failed: they serve NOTHING until a
	// later arbitration lands - the lazy loader would otherwise pull the very
	// bytes the verdict rejected straight back into memory.
	private final Set<String> unresolvedSlots = ConcurrentHashMap.newKeySet();
	// A hash is allowed to serve a name slot only after that exact pairing's
	// disk verdict succeeded in this session. activeHashKey means arbitration
	// started; it is deliberately not proof that arbitration finished.
	private final Map<String, String> settledOwnerBySlot = new ConcurrentHashMap<>();
	// Same-key sidecar recovery runs once per session per slot: two live
	// clients trading one name must not ping-pong parks forever.
	private final Set<String> recoveredThisSession = ConcurrentHashMap.newKeySet();

	void setSyncVerdictTimeoutForTest(long ms)
	{
		syncVerdictTimeoutMs = ms;
	}

	/**
	 * Logout: the capture anchor and any queued rename checks die with the
	 * session. The per-session recovery latch resets too - the NEXT session
	 * may legitimately need a recovery. Synchronized so the epoch bump and
	 * anchor clear are atomic against any in-flight followNameChange body:
	 * a fenced check either sees the new epoch and no-ops, or completed
	 * fully before the clear.
	 */
	public synchronized void onSessionEnded()
	{
		sessionEpoch.incrementAndGet();
		pendingRenameNotice.set(null);
		activeHashKey = null;
		activePlayer = null;
		settledOwnerBySlot.clear();
		recoveredThisSession.clear();
	}

	/** The current session fence value, captured while the session is live
	 *  and checked before any deferred work acts on its behalf. */
	public long currentSessionEpoch()
	{
		return sessionEpoch.get();
	}

	/**
	 * Commit a non-blocking side effect only while the caller's login session
	 * is still current. The commit runs under the same monitor as
	 * {@link #onSessionEnded()}, so request enqueue and logout have one
	 * ordering: either a live session commits the request, or it never starts.
	 */
	public synchronized <T> T commitIfSessionCurrent(long expectedEpoch, Supplier<T> commit)
	{
		if (sessionEpoch.get() != expectedEpoch)
		{
			return null;
		}
		return commit.get();
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
	public String followNameChange(String currentRsn, long accountHash)
	{
		return followNameChange(currentRsn, accountHash, null, -1);
	}

	/** Completes the verdict future (when given) with the DISK half's result:
	 *  true only once park/write/stamp all landed, false on any abort. */
	private static void settle(CompletableFuture<Boolean> verdict, boolean ok)
	{
		if (verdict != null)
		{
			verdict.complete(ok);
		}
	}

	private synchronized boolean markSlotSettledIfCurrent(String key, String hashKey,
		long expectedEpoch)
	{
		if (sessionEpoch.get() != expectedEpoch || !hashKey.equals(activeHashKey))
		{
			return false;
		}
		settledOwnerBySlot.put(key, hashKey);
		unresolvedSlots.remove(key);
		return true;
	}

	private void beginSlotArbitration(String key)
	{
		settledOwnerBySlot.remove(key);
		unresolvedSlots.add(key);
	}

	private synchronized String followNameChange(String currentRsn, long accountHash,
		CompletableFuture<Boolean> verdict, long expectedEpoch)
	{
		if (expectedEpoch >= 0 && sessionEpoch.get() != expectedEpoch)
		{
			// Dead session's fenced work: checked INSIDE the monitor, atomic
			// against onSessionEnded - no anchor-restoration window remains.
			settle(verdict, false);
			return null;
		}
		long arbitrationEpoch = expectedEpoch >= 0 ? expectedEpoch : sessionEpoch.get();
		if (currentRsn == null || currentRsn.isBlank() || accountHash == -1)
		{
			settle(verdict, true);
			return null;
		}
		Map<String, String> identity = loadIdentity();
		String hashKey = Long.toString(accountHash);
		activeHashKey = hashKey;
		String currentKey = cacheKey(currentRsn);
		String previousKey = identity.get(hashKey);
		if (previousKey == null)
		{
			// First sighting of this hash on this machine. NOT a free pass:
			// the slot's live file may belong to another local account, and
			// adopting it would let this account publish their captures.
			identity.put(hashKey, currentKey);
			beginSlotArbitration(currentKey);
			adoptSlot(currentKey, hashKey, verdict, arbitrationEpoch);
			return null;
		}
		boolean sameKey = previousKey.equals(currentKey);
		if (sameKey && !sidecarFile(hashKey, currentKey).exists())
		{
			// Steady-state candidate - but the cached memory mapping alone
			// proves nothing; the DISK ledger must agree (recorded, under
			// THIS name, nobody outranking). settleSteadySlot re-arbitrates
			// anything less.
			IdentityLedger.View steady = ledger.read();
			String diskName = steady.names.get(hashKey);
			if (diskName == null || currentKey.equals(diskName))
			{
				beginSlotArbitration(currentKey);
				settleSteadySlot(currentKey, hashKey, steady, diskName, verdict,
					arbitrationEpoch);
				return null;
			}
			// Disk maps us to a DIFFERENT name than this session's memory:
			// another client of this account moved it. Disk wins - re-enter
			// the full rename path from the disk's previous name.
			previousKey = diskName;
			sameKey = false;
		}
		if (sameKey && !recoveredThisSession.add(currentKey))
		{
			// The sidecar recovery already ran this session; running it
			// again would let two live clients trading one name ping-pong
			// parks forever. Only its successful disk verdict may settle
			// this duplicate call; "started" alone is not ownership proof.
			boolean settled = hashKey.equals(settledOwnerBySlot.get(currentKey));
			if (settled)
			{
				unresolvedSlots.remove(currentKey);
			}
			else
			{
				unresolvedSlots.add(currentKey);
			}
			settle(verdict, settled);
			return null;
		}
		beginSlotArbitration(currentKey);
		final String fromKey = previousKey;
		final boolean recoverySameKey = sameKey;
		// Ownership decisions see BOTH truths: this session's in-memory
		// mappings (ours may not have flushed yet), overlaid with a FRESH
		// disk read for every OTHER hash - another client on this machine
		// may have written mappings after this process last looked, and its
		// disk entries outrank our stale cache of them. Stamps only exist on
		// disk, which is fine: self entries are excluded from claim ranking.
		IdentityLedger.View diskView = ledger.read();
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
		File sidecar = sidecarFile(hashKey, fromKey);
		boolean sourceFromSidecar = false;
		if (sidecar.exists())
		{
			source = readRecordFile(sidecar);
			sourceFromSidecar = source != null;
		}
		if (recoverySameKey && !sourceFromSidecar)
		{
			// Unreadable sidecar: touch nothing, retry next login. Fail
			// closed for sync - the live slot's provenance is unresolved.
			settle(verdict, false);
			return null;
		}
		boolean sourceFromLiveFile = false;
		if (source == null)
		{
			// The live file is only OURS to migrate when no OTHER account's
			// current name claims that key.
			if (IdentityLedger.newestClaimant(freshIdentity, diskView.stamps, fromKey, hashKey) == null)
			{
				source = players.remove(fromKey);
				if (source == null)
				{
					source = loadFromDisk(fromKey);
				}
				sourceFromLiveFile = source != null;
			}
		}
		identity.put(hashKey, currentKey);
		if (source == null)
		{
			// Nothing recoverable under the old name: mapping updates, no
			// move - but the DESTINATION slot still gets the same adoption
			// arbitration as a first sighting, or a resident account's live
			// file would become this account's serving copy.
			adoptSlot(currentKey, hashKey, verdict, arbitrationEpoch);
			return null;
		}
		String previousDisplay = source.playerName != null ? source.playerName : fromKey;

		MigrationDest d = resolveDestination(currentKey, hashKey, freshIdentity, diskView.stamps);
		if (!sourceFromSidecar)
		{
			// Only when the old key's live file was OURS: if we recovered from
			// a sidecar, the live slot (and any pending write for it) belongs
			// to whoever holds that name now.
			pendingByPlayer.remove(fromKey);
		}

		PlayerClogData merged = ClogRecords.hasFirstPartyMarks(d.dest)
			? ClogRecords.mergeForMigration(d.dest, source)
			: source;
		merged.playerName = currentRsn;
		merged.ownerHash = hashKey;
		players.put(currentKey, merged);

		PlayerClogData copy = shallowCopy(merged);
		boolean consumedSidecar = sourceFromSidecar;
		boolean liveSource = sourceFromLiveFile;
		queueMigrationTask(currentRsn, currentKey, hashKey, fromKey, d, consumedSidecar,
			sidecar, liveSource, copy, recoverySameKey ? null : previousDisplay, verdict,
			arbitrationEpoch);
		log.debug("Rename continuity: '{}' -> '{}'", fromKey, currentKey);
		return recoverySameKey ? null : previousDisplay;
	}

	/** The migration's disk dispatch. The chat notice only fires once the
	 *  DISK half actually succeeded: announcing "your log came along" over a
	 *  failed migration would be a lie the next login quietly retracts. */
	private void queueMigrationTask(String currentRsn, String currentKey, String hashKey,
		String fromKey, MigrationDest d, boolean consumedSidecar, File sidecar,
		boolean liveSource, PlayerClogData copy, String noticeOnSuccess,
		CompletableFuture<Boolean> verdict, long expectedEpoch)
	{
		try
		{
			diskWriter.execute(() ->
			{
				boolean done = ledger.withLock(() ->
					migrateOnDisk(currentRsn, currentKey, hashKey, fromKey, d.displaced,
						d.otherHash, consumedSidecar, sidecar, liveSource, copy, d.displacedCopy));
				boolean settledCurrent = done
					&& markSlotSettledIfCurrent(currentKey, hashKey, expectedEpoch);
				if (settledCurrent)
				{
					if (noticeOnSuccess != null)
					{
						publishRenameNoticeIfSessionCurrent(expectedEpoch, noticeOnSuccess);
					}
				}
				settle(verdict, settledCurrent);
			});
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: memory served this session; disk re-heals next login.
			settle(verdict, false);
		}
	}

	/** Publish atomically against logout, which clears notices and bumps the epoch. */
	private synchronized void publishRenameNoticeIfSessionCurrent(long expectedEpoch,
		String previousName)
	{
		if (sessionEpoch.get() == expectedEpoch)
		{
			pendingRenameNotice.set(previousName);
		}
	}

	/**
	 * The sync pre-flight: returns only after the DISK half of any migration
	 * or adoption reached its locked verdict, so the payload the caller
	 * builds next can never contain bytes the in-lock revalidation rejected.
	 * On failure the slot's memory clears too - fail closed, sync skips, the
	 * next login re-decides with disk truth.
	 */
	public boolean followNameChangeForSync(String currentRsn, long accountHash)
	{
		return followNameChangeForSync(currentRsn, accountHash, sessionEpoch.get());
	}

	/**
	 * The epoch-fenced variant: callers that gathered their state earlier
	 * (the plugin's sync dispatch) pass the fence they captured then, and
	 * the check runs INSIDE the cache monitor - a logout between gather and
	 * this call can never restore the dead session's anchor.
	 */
	public boolean followNameChangeForSync(String currentRsn, long accountHash, long expectedEpoch)
	{
		if (currentRsn == null || currentRsn.isBlank())
		{
			return true;
		}
		CompletableFuture<Boolean> verdict = new CompletableFuture<>();
		followNameChange(currentRsn, accountHash, verdict, expectedEpoch);
		boolean ok;
		try
		{
			ok = verdict.get(syncVerdictTimeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (Exception e)
		{
			ok = false;
		}
		if (!ok)
		{
			if (expectedEpoch >= 0 && sessionEpoch.get() != expectedEpoch)
			{
				// The session ended: there is nobody to serve and nothing
				// unresolved about the slot itself - no quarantine, the next
				// login re-decides fresh.
				return false;
			}
			String key = cacheKey(currentRsn);
			synchronized (this)
			{
				players.remove(key);
				pendingByPlayer.remove(key);
				// Quarantine, not just clear: the lazy loader would pull the
				// rejected bytes straight back from disk on the next panel or
				// lookup ask. A later successful arbitration lifts it.
				unresolvedSlots.add(key);
			}
			// The disk task may land AFTER this timeout - its own lift may
			// even have run BEFORE the add above. This hook runs after both
			// the add and the completion: either order lifts the slot.
			verdict.whenComplete((landed, err) ->
			{
				if (Boolean.TRUE.equals(landed))
				{
					unresolvedSlots.remove(key);
				}
			});
		}
		return ok;
	}

	private static final class MigrationDest
	{
		private PlayerClogData dest;
		private String otherHash;
		private boolean displaced;
		private PlayerClogData displacedCopy;
	}

	/**
	 * Destination and displacement decision. First-party marks prove a LOCAL
	 * account captured the destination data - but on a shared machine that
	 * could be a DIFFERENT local account that owned this name before
	 * transferring it. The FRESH identity map knows: another hash still
	 * claiming this key means the data is theirs. Their latest in-memory
	 * state is snapshotted here and written back CHECKED inside the migration
	 * task before the park; any failure along that chain aborts the disk
	 * migration whole. All disk work stays on the writer thread, whose FIFO
	 * order guarantees an already-in-flight debounced save for them lands
	 * first. An already-existing sidecar for the claimant means the
	 * displacement happened before (and possibly crashed mid-migration):
	 * their canonical copy is safe, and whatever sits at the live slot is our
	 * own half-written file or a regenerable lookup cache - merge or replace
	 * it, never park it over their sidecar.
	 */
	private MigrationDest resolveDestination(String currentKey, String hashKey,
		Map<String, String> freshIdentity, Map<String, Long> stamps)
	{
		MigrationDest d = new MigrationDest();
		d.dest = players.get(currentKey);
		if (d.dest == null)
		{
			d.dest = loadFromDisk(currentKey);
		}
		d.otherHash = IdentityLedger.newestClaimant(freshIdentity, stamps, currentKey, hashKey);
		d.displaced = d.otherHash != null && d.dest != null
			&& !sidecarFile(d.otherHash, currentKey).exists();
		if (d.displaced)
		{
			Runnable unflushed = pendingByPlayer.remove(currentKey);
			PlayerClogData displacedLatest = players.remove(currentKey);
			if (unflushed != null && displacedLatest != null)
			{
				d.displacedCopy = shallowCopy(displacedLatest);
				d.displacedCopy.ownerHash = d.otherHash;
			}
			d.dest = null;
		}
		else
		{
			// Any queued pre-merge snapshot would overwrite the merged file
			// after the migration writes it; every capture in it already
			// lives in the merged memory the migration itself persists.
			pendingByPlayer.remove(currentKey);
		}
		return d;
	}

	/**
	 * Adoption arbitration for a slot this hash is claiming with nothing of
	 * its own to move in: stamp the claim, and if another local account's
	 * live file sits in the slot unparked, park it FIRST - a first-seen (or
	 * empty-handed) account must never adopt, serve, or publish a resident
	 * account's captures. In-memory state for the slot clears immediately;
	 * the disk half revalidates under the identity lock.
	 */
	/** The steady-state slot check, disk-verified on all three counts. */
	private void settleSteadySlot(String currentKey, String hashKey, IdentityLedger.View steady,
		String diskName, CompletableFuture<Boolean> verdict, long expectedEpoch)
	{
		if (diskName == null)
		{
			// Disk never recorded us: an earlier adoption aborted (or is
			// still queued). Re-run the full arbitration - stamping straight
			// through would adopt whatever sits in the slot.
			adoptSlot(currentKey, hashKey, verdict, expectedEpoch);
			return;
		}
		String owner = IdentityLedger.newestClaimant(steady.names, steady.stamps, currentKey, null);
		if (!hashKey.equals(owner))
		{
			// A newer claim landed while this account was away, so the live
			// file is presumed the rival's. We are logged in as this name NOW
			// - game truth - so full adoption arbitration parks their residue
			// and re-stamps us.
			adoptSlot(currentKey, hashKey, verdict, expectedEpoch);
			return;
		}
		if (steady.stamps.getOrDefault(hashKey, 0L) == 0L)
		{
			// A v1-lifted entry (stamp 0) re-asserts once, or any stamped
			// foreign claim would outrank the sitting owner forever.
			persistIdentityEntry(hashKey, currentKey);
		}
		// A steady TRUE is also an arbitration outcome: lift any lingering
		// quarantine rather than serving nothing forever.
		settle(verdict, markSlotSettledIfCurrent(currentKey, hashKey, expectedEpoch));
	}

	private void adoptSlot(String currentKey, String hashKey, CompletableFuture<Boolean> verdict,
		long expectedEpoch)
	{
		IdentityLedger.View diskView = ledger.read();
		String squatter = IdentityLedger.newestClaimant(diskView.names, diskView.stamps, currentKey, hashKey);
		PlayerClogData squatterCopy = null;
		if (squatter != null)
		{
			// Same drain rule as displacement: the resident's queued capture
			// (if this very client made it earlier in the session) reaches
			// the file, checked, before the park.
			Runnable pending = pendingByPlayer.remove(currentKey);
			PlayerClogData latest = players.remove(currentKey);
			if (pending != null && latest != null)
			{
				squatterCopy = shallowCopy(latest);
				squatterCopy.ownerHash = squatter;
			}
		}
		String decisionSquatter = squatter;
		PlayerClogData squatterToFlush = squatterCopy;
		try
		{
			diskWriter.execute(() ->
			{
				boolean ok = ledger.withLock(() ->
					adoptSlotOnDisk(currentKey, hashKey, decisionSquatter, squatterToFlush));
				boolean settledCurrent = ok
					&& markSlotSettledIfCurrent(currentKey, hashKey, expectedEpoch);
				settle(verdict, settledCurrent);
			});
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: the next login re-runs adoption.
			settle(verdict, false);
		}
	}

	private boolean adoptSlotOnDisk(String currentKey, String hashKey,
		String decisionSquatter, PlayerClogData squatterToFlush)
	{
		IdentityLedger.View now = ledger.read();
		String claimNow = IdentityLedger.newestClaimant(now.names, now.stamps, currentKey, hashKey);
		if (!Objects.equals(claimNow, decisionSquatter))
		{
			// The slot's ownership moved between the decision and this task -
			// a claim appeared over what may be our own pre-latch captures,
			// vanished, or changed hands entirely. The snapshot we carry
			// answers a question nobody is asking anymore: abort whole and
			// re-decide next login. (The disk entry stays absent, so the
			// steady-state path re-runs adoption instead of stamping past it.)
			return false;
		}
		if (claimNow != null && !sidecarFile(claimNow, currentKey).exists())
		{
			if (squatterToFlush != null)
			{
				String squatterName = squatterToFlush.playerName != null
					? squatterToFlush.playerName : currentKey;
				if (!saveToDiskChecked(squatterName, squatterToFlush))
				{
					return false; // their latest capture moves or nothing does
				}
			}
			if (!parkDisplacedFileNow(currentKey, claimNow))
			{
				return false; // their file stays put; the next login retries
			}
		}
		now.names.put(hashKey, currentKey);
		now.stamps.put(hashKey, IdentityLedger.nextStamp(now, currentKey));
		return ledger.save(now);
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
		IdentityLedger.View now = ledger.read();
		String claimNow = IdentityLedger.newestClaimant(now.names, now.stamps, currentKey, hashKey);
		if (!Objects.equals(claimNow, parkHash))
		{
			// The destination's claim state moved between the decision and
			// this task - appeared, vanished, or changed hands. Every rule
			// below assumes the decision's view (parkHash is the claimant the
			// decision saw, park or no park); abort whole, the next login
			// re-decides from disk.
			return false;
		}
		if (sourceFromLiveFile && IdentityLedger.newestClaimant(now.names, now.stamps, oldKey, hashKey) != null)
		{
			// A live-file source was only ours while nobody else's current
			// name claimed the old key. A claim that appeared since the
			// decision means the bytes we copied may be theirs - abort whole.
			return false;
		}
		if (parkFirst)
		{
			if (sidecarFile(parkHash, currentKey).exists())
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
				if (!parkDisplacedFileNow(currentKey, parkHash))
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
		now.stamps.put(hashKey, IdentityLedger.nextStamp(now, currentKey));
		return ledger.save(now);
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
	public String consumeRenameNotice()
	{
		// Deliberately NOT synchronized: the game tick polls this every tick
		// and must never wait behind the writer thread's file I/O.
		return pendingRenameNotice.getAndSet(null);
	}

	/**
	 * The rename check off the calling thread: it reads the identity file and
	 * can parse cache files, which is too much for a game tick. The chat
	 * notice arrives later via consumeRenameNotice polling.
	 */
	public CompletableFuture<Boolean> followNameChangeAsync(String currentRsn, long accountHash)
	{
		return followNameChangeAsync(currentRsn, accountHash, sessionEpoch.get());
	}

	public CompletableFuture<Boolean> followNameChangeAsync(String currentRsn, long accountHash,
		long expectedEpoch)
	{
		CompletableFuture<Boolean> verdict = new CompletableFuture<>();
		try
		{
			diskWriter.execute(() ->
				followNameChange(currentRsn, accountHash, verdict, expectedEpoch));
		}
		catch (RejectedExecutionException ignored)
		{
			// Shutdown race: the next login re-checks.
			verdict.complete(false);
		}
		return verdict;
	}

	/**
	 * Move another account's file out of the destination slot without
	 * destroying it: their own next login recovers from the sidecar (the
	 * sidecar-first source rule above), and their server copy migrates
	 * regardless. Runs INSIDE the migration disk task; a failure here aborts
	 * the whole disk migration so their canonical copy is never buried.
	 */
	private boolean parkDisplacedFileNow(String key, String fallbackHash)
	{
		File file = getCacheFile(key);
		if (!file.exists())
		{
			return true; // nothing on disk to protect
		}
		// The file's own provenance stamp beats claim-derived guesses: claims
		// order NAMES, but the bytes belong to whoever wrote them. A file
		// that turns out to be our own (a crashed half-migration) parks under
		// US and the next check's sidecar recovery brings it straight back.
		PlayerClogData resident = readRecordFile(file);
		String owner = resident != null && resident.ownerHash != null
			? resident.ownerHash : fallbackHash;
		File parked = sidecarFile(owner, key);
		if (parked.exists())
		{
			// The owner's canonical copy is already parked; the live file is
			// residue and not worth burying that copy for. Leave it for the
			// caller's checked write to replace.
			return true;
		}
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
		ledger.withLock(() ->
		{
			IdentityLedger.View disk = ledger.read();
			disk.names.put(hashKey, key);
			disk.stamps.put(hashKey, IdentityLedger.nextStamp(disk, key));
			return ledger.save(disk);
		});
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
		identity = new ConcurrentHashMap<>(ledger.read().names);
		identityByHash = identity;
		return identity;
	}


	/** Genuinely atomic where the filesystem allows it; plain replace as the
	 *  documented fallback (some filesystems refuse ATOMIC_MOVE). */
	static void atomicMove(File from, File to) throws IOException
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
		this.ledger = new IdentityLedger(gson, cacheDir);
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

	public synchronized boolean setActivePlayer(String name)
	{
		if (name == null)
		{
			activePlayer = null;
			// The capture anchor dies with the session: a stale hash would
			// authorize the NEXT account's pre-latch saves (or post-logout
			// lookups) against the PREVIOUS account's claims.
			activeHashKey = null;
			return false;
		}

		activePlayer = name;
		String key = cacheKey(name);
		if (activeHashKey == null
			|| !activeHashKey.equals(settledOwnerBySlot.get(key)))
		{
			// The display name arrives before the account hash on login. Keep
			// resident bytes available to the ledger arbitrator, but expose
			// nothing until this exact key/hash pairing's disk verdict succeeds.
			unresolvedSlots.add(key);
		}
		if (unresolvedSlots.contains(key))
		{
			log.debug("Active clog player '{}' is waiting for identity arbitration", name);
			return false;
		}

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
		return true;
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
	 * First-party bulk-capture landing (the Collection Log Search walk). Marks every
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
		if (unresolvedSlots.contains(key))
		{
			return;
		}

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
		submitPlayerSave(name, snapshot);
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
		if (unresolvedSlots.contains(key))
		{
			return;
		}
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
		submitPlayerSave(playerName, snapshot);
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
		if (unresolvedSlots.contains(key))
		{
			return false;
		}
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
			submitPlayerSave(playerName, snapshot);
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
		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return false;
		}
		PlayerClogData data = players.get(key);
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
			submitPlayerSave(playerName, snapshot);
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

		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return false;
		}
		PlayerClogData data = players.get(key);
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
		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return null;
		}
		PlayerClogData data = players.get(key);
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
		String key = playerName != null ? cacheKey(playerName) : null;
		if (key != null && unresolvedSlots.contains(key))
		{
			return false;
		}
		PlayerClogData data = key != null ? players.get(key) : null;
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
		if (unresolvedSlots.contains(key))
		{
			return;
		}
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
			submitPlayerSave(playerName, snapshot);
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
		if (unresolvedSlots.contains(key))
		{
			// Resident memory is private to whoever wrote it until the ledger
			// settles the current login's ownership.
			return false;
		}
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

		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return null;
		}
		PlayerClogData data = players.get(key);
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
		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return false;
		}
		PlayerClogData data = players.get(key);
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
		String key = cacheKey(playerName);
		if (unresolvedSlots.contains(key))
		{
			return null;
		}
		PlayerClogData data = players.get(key);
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

	/**
	 * Queue a debounced player-file save anchored to the account hash active
	 * at CAPTURE time - authorization must not drift to whoever happens to be
	 * logged in when the debounce fires.
	 */
	private void submitPlayerSave(String playerName, PlayerClogData snapshot)
	{
		String anchor = activeHashKey;
		submitDiskWrite(playerName, () -> saveToDisk(playerName, snapshot, anchor));
	}

	private void saveToDisk(String playerName, PlayerClogData data, String anchorHash)
	{
		String key = cacheKey(playerName);
		// Slot ownership and the write are ONE atomic step under the same
		// lock migrations hold - check-then-write with the lock released in
		// between would let a migration land in the gap. A slot nobody
		// claims (a plain lookup cache) writes freely; a claimed slot only
		// accepts writes anchored to the claimant. This guards every save,
		// marked or not: a lookup overwrite is regenerable bytes IN but a
		// claimed first-party file OUT, and legacy null-mark data is wholly
		// first-party by the class contract anyway.
		ledger.withLock(() ->
		{
			IdentityLedger.View disk = ledger.read();
			String winner = IdentityLedger.newestClaimant(disk.names, disk.stamps, key, null);
			if (winner != null && !winner.equals(anchorHash))
			{
				log.debug("Skipped save for '{}': the slot belongs to another local account",
					playerName);
				return true; // suppressed by design, not a failure
			}
			if (anchorHash != null)
			{
				// Stamp the file's provenance: parking decisions trust the
				// bytes' own owner over claim-derived guesses.
				data.ownerHash = anchorHash;
			}
			return writeCacheFile(playerName, data);
		});
	}

	private boolean writeCacheFile(String playerName, PlayerClogData data)
	{
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
			return true;
		}
		catch (IOException e)
		{
			log.warn("Failed to save clog cache for '{}': {}", playerName, e.getMessage());
			return false;
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
		copy.ownerHash = src.ownerHash;
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

}
