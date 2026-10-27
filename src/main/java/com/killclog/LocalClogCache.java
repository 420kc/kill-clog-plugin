package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Multi-account, disk-backed collection log store.
 *
 * <p>Each account that captures on this PC has one own log, {@code <hash>.json},
 * written only by the client logged into that account; other players' lookups
 * live apart in {@code lookups/<name>.json} and can never ship. The folder is
 * opened on this store's disk thread: RuneLite's own move, then every own log
 * and the newest lookups load into memory ({@link StoreFiles} reads and writes
 * them). Until that finishes the store is not ready and serves nothing; after
 * it, reads and writes are memory-only and disk only receives debounced saves,
 * except that each login reads its account's own log fresh, on the disk thread:
 * another client on this PC may have played that account since this one loaded it.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class LocalClogCache
{
	// Lookups kept, in memory and on disk: an instant repaint of a name looked up again, and a fallback while Temple is
	// down. A lookup fetches fresh either way, so this is a convenience, sized for it.
	private static final int LOOKUP_CAP = 64;
	private static final long DEBOUNCE_MS = 500;
	// A name two local accounts both carry maps to nothing.
	private static final String AMBIGUOUS = "";

	private final Gson gson;
	private final ScheduledExecutorService diskWriter;
	private final CompletableFuture<Filepath> opened = new CompletableFuture<>();
	// The opened folder; null keeps the store in memory.
	@Nullable
	private volatile Filepath logs;
	private volatile boolean ready;

	// Own logs by account hash, and the advisory name index over them.
	private final Map<String, PlayerClogData> own = new HashMap<>();
	private final Map<String, String> ownerByName = new HashMap<>();
	private final Map<String, PlayerClogData> lookups = new LinkedHashMap<String, PlayerClogData>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, PlayerClogData> eldest)
		{
			if (size() <= LOOKUP_CAP)
			{
				return false;
			}
			forgetLookupFile(eldest.getValue().playerName);
			return true;
		}
	};

	// The login this store serves, as the session tracker hands it over.
	private volatile Session session = Session.none(0);
	// The logged-in account, set once its own log is serving.
	private volatile String activeHashKey;
	private final AtomicReference<String> pendingRenameNotice = new AtomicReference<>();
	// The accounts whose own log this session has read fresh from disk, and those being read.
	private final java.util.Set<String> readThisSession = ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> reading = ConcurrentHashMap.newKeySet();

	// Debounced saves by file: bursts of category navigation collapse to one write.
	private final Map<String, Runnable> pendingByFile = new ConcurrentHashMap<>();
	private final Map<String, PlayerClogData> unsavedFiles = new ConcurrentHashMap<>();

	@Setter
	private volatile Runnable firstPartyChangedListener;

	@Inject
	public LocalClogCache(Gson gson)
	{
		this(gson, newDiskWriter());
	}

	/** A League's own store, opened at once in that League's folder. */
	LocalClogCache(Gson gson, @Nullable Filepath folder)
	{
		this(gson, newDiskWriter());
		open(() -> folder);
	}

	private static ScheduledExecutorService newDiskWriter()
	{
		ScheduledThreadPoolExecutor writer = new ScheduledThreadPoolExecutor(1, r ->
		{
			Thread t = new Thread(r, "kill-clog-disk");
			t.setDaemon(true);
			return t;
		});
		writer.setKeepAliveTime(30, TimeUnit.SECONDS);
		writer.allowCoreThreadTimeOut(true);
		return writer;
	}

	private static String cacheKey(String playerName)
	{
		return RsnInputPolicy.key(playerName);
	}

	/**
	 * Resolve the folder and load it, all on the disk thread. A folder that
	 * cannot be opened keeps this session in memory; the next start retries.
	 */
	void open(Callable<Filepath> folder)
	{
		diskWriter.execute(() ->
		{
			Filepath resolved;
			try
			{
				resolved = folder.call();
			}
			catch (Exception e)
			{
				resolved = null;
				log.warn("Kill Clog data folder unavailable this session: {}", e.getMessage());
			}
			load(resolved);
		});
	}

	/** The opened folder, once the store is ready; null for a memory-only session. */
	CompletableFuture<Filepath> folder()
	{
		return opened;
	}

	private void load(@Nullable Filepath folder)
	{
		Map<String, PlayerClogData> loadedOwn = new HashMap<>();
		List<PlayerClogData> loadedLookups = new ArrayList<>();
		if (folder != null)
		{
			try
			{
				// RuneLite makes plugin-data, not this folder: a fresh install's first start has none yet.
				folder.createDirectories();
				for (Filepath file : StoreFiles.list(folder))
				{
					if (!file.getFileName().matches("[0-9a-f]{16}\\.json"))
					{
						continue;
					}
					PlayerClogData data = readRecord(file);
					if (data == null || !file.getFileName().equals(ownFile(data.ownerHash)))
					{
						// Damaged bytes are kept aside, never overwritten by the next save. A second client starting
						// on this folder may move them first; either way the rest still loads, so no account's log is
						// left unread and then saved over.
						setAside(folder, file);
						continue;
					}
					loadedOwn.put(data.ownerHash, data);
				}
				Filepath lookupDir = folder.join(StoreFiles.LOOKUPS);
				if (lookupDir.isDirectory())
				{
					List<Filepath> files = StoreFiles.list(lookupDir);
					files.sort(Comparator.comparing(LocalClogCache::modified).reversed());
					for (Filepath file : files.subList(0, Math.min(LOOKUP_CAP, files.size())))
					{
						PlayerClogData data = readRecord(file);
						if (data != null && data.playerName != null)
						{
							loadedLookups.add(0, data);
						}
					}
					// The cap holds on disk too: every name looked up, or seen running a chat command, left a file
					// for good, about 76 KB each.
					for (Filepath file : files.subList(Math.min(LOOKUP_CAP, files.size()), files.size()))
					{
						file.deleteIfExists();
					}
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Kill Clog could not read its folder: {}", e.getMessage());
			}
		}
		synchronized (this)
		{
			// Anything written while loading is newer and stays.
			loadedOwn.forEach(own::putIfAbsent);
			for (PlayerClogData data : loadedLookups)
			{
				lookups.putIfAbsent(cacheKey(data.playerName), data);
			}
			ownerByName.clear();
			own.forEach((hash, data) -> index(data.playerName, hash));
			logs = folder;
			ready = true;
		}
		opened.complete(folder);
	}

	/** An own log's file name, or null for a stamp that is not an account hash. */
	@Nullable
	static String ownFile(String hashKey)
	{
		try
		{
			return StoreFiles.ownFileName(hashKey);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static FileTime modified(Filepath file)
	{
		try
		{
			return file.getLastModifiedTime();
		}
		catch (IOException e)
		{
			return FileTime.fromMillis(0);
		}
	}

	private PlayerClogData readRecord(Filepath file)
	{
		PlayerClogData data = StoreFiles.read(gson, file, PlayerClogData.class);
		if (data == null || data.categories == null)
		{
			return null;
		}
		// Gson builds plain maps; cache writes and EDT reads share these.
		data.categories = new ConcurrentHashMap<>(data.categories);
		data.obtained = data.obtained != null ? new ConcurrentHashMap<>(data.obtained) : new ConcurrentHashMap<>();
		// Files written before live unlocks bumped lastChanged can hold items
		// newer than the stamp; heal on load so the notice never trails the shelf.
		bumpLastChanged(data, newestObtainedDate(data.obtained));
		return data;
	}

	private void index(String name, String hash)
	{
		if (name != null)
		{
			ownerByName.merge(cacheKey(name), hash, (a, b) -> a.equals(b) ? a : AMBIGUOUS);
		}
	}

	/** An account's log under the name it wears now: a name it wore before no longer points at it. */
	private void reindex(String name, String hash)
	{
		ownerByName.values().removeIf(hash::equals);
		index(name, hash);
	}

	/**
	 * The login this store serves, from the session tracker, and the account taking up its own log: false until the
	 * store is ready and the log has been read fresh for this login, so the plugin asks again each tick. A different
	 * stored name is a name change: the log follows the account, and one chat line says so. A new session ends the
	 * last one first: its account, its fresh reads and any notice still queued.
	 */
	public synchronized boolean serve(Session next)
	{
		if (next.getNumber() != session.getNumber())
		{
			activeHashKey = null;
			readThisSession.clear();
			pendingRenameNotice.set(null);
		}
		session = next;
		if (!ready || !next.loggedIn())
		{
			return false;
		}
		String hash = Long.toString(next.getAccountHash());
		if (logs != null && !readThisSession.contains(hash))
		{
			readFresh(hash);
			return false;
		}
		if (hash.equals(activeHashKey))
		{
			return true;
		}
		String name = next.getName();
		activeHashKey = hash;
		PlayerClogData data = own.get(activeHashKey);
		if (data != null && data.playerName != null && !cacheKey(data.playerName).equals(cacheKey(name)))
		{
			pendingRenameNotice.set(data.playerName);
			data.playerName = name;
			reindex(name, activeHashKey);
			submitSave(activeHashKey, true, data);
		}
		return true;
	}

	/**
	 * Reads an account's own log from disk on the disk thread, behind any save this client still has queued for it, so
	 * the newest copy serves: another client may have played the account since this one loaded the folder, and an
	 * older copy here would save over what it captured, a drop's date and kill count included. A copy this client
	 * has not saved yet is its own newest and stays.
	 */
	private void readFresh(String hash)
	{
		if (!reading.add(hash))
		{
			return;
		}
		long number = session.getNumber();
		Runnable read = () ->
		{
			try
			{
				String file = ownFile(hash);
				Filepath folder = logs;
				Filepath path = folder != null && file != null ? folder.join(file) : null;
				boolean onDisk = path != null && !unsavedFiles.containsKey(file) && path.isFile();
				PlayerClogData data = onDisk ? readRecord(path) : null;
				if (onDisk && (data == null || !hash.equals(data.ownerHash)))
				{
					// Damaged since the folder loaded: kept aside as load() keeps it, and the copy in memory serves.
					setAside(folder, path);
				}
				synchronized (this)
				{
					if (data != null && hash.equals(data.ownerHash) && !unsavedFiles.containsKey(file))
					{
						own.put(hash, data);
						// Renamed on another client: the log names the account as it is now.
						reindex(data.playerName, hash);
					}
				}
			}
			finally
			{
				reading.remove(hash);
				if (session.getNumber() == number)
				{
					readThisSession.add(hash);
				}
			}
		};
		try
		{
			diskWriter.execute(read);
		}
		catch (RejectedExecutionException e)
		{
			// A store closing for good serves what it holds.
			reading.remove(hash);
			readThisSession.add(hash);
		}
	}

	/** One chat line per name change, consumed by the plugin's tick. */
	public String consumeRenameNotice()
	{
		return pendingRenameNotice.getAndSet(null);
	}

	/** The sync pre-flight: this store still serves that login, with its account's own log. */
	public synchronized boolean servesAccount(Session expected)
	{
		return expected.getNumber() == session.getNumber() && serving(expected.getName())
			&& Long.toString(expected.getAccountHash()).equals(activeHashKey);
	}

	/** A side effect that happens only while that login is the one served, under the same monitor as {@link #serve}. */
	public synchronized <T> T commitIfSessionCurrent(Session expected, Supplier<T> commit)
	{
		return expected.getNumber() == session.getNumber() ? commit.get() : null;
	}

	/** Flush accepted saves on the same queue; a new session cannot overtake them. */
	public void shutdown()
	{
		diskWriter.execute(() -> new ArrayList<>(pendingByFile.keySet()).forEach(this::flush));
	}

	/** A League store that is done for good: flush its saves, then let its writer end. */
	public void close()
	{
		shutdown();
		diskWriter.shutdown();
	}

	/** Whether this name is the logged-in player's, whose captures this store takes. */
	public boolean isActivePlayer(String name)
	{
		return RsnInputPolicy.sameName(session.getName(), name);
	}

	/** Whether this name's own log is the one serving: the logged-in player's, taken up for this login. */
	public synchronized boolean serving(String name)
	{
		return ready && activeHashKey != null && isActivePlayer(name);
	}

	/** The serving account's own log, when this name is the one it serves. */
	private PlayerClogData ownLog(String name)
	{
		return serving(name) ? own.get(activeHashKey) : null;
	}

	/** What a read serves: your own log for the logged-in name, else a lookup, else a local alt's own log. */
	private PlayerClogData record(String name)
	{
		if (!ready || name == null)
		{
			return null;
		}
		if (isActivePlayer(name))
		{
			return ownLog(name);
		}
		String key = cacheKey(name);
		PlayerClogData lookup = lookups.get(key);
		if (lookup != null)
		{
			return lookup;
		}
		String hash = ownerByName.get(key);
		return hash != null ? own.get(hash) : null;
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
		if (result == null || result.getPlayerName() == null || !ready)
		{
			return;
		}

		String name = result.getPlayerName();
		if (firstParty && !serving(name))
		{
			// Captures wait for the account's own log, as they always waited for its identity.
			return;
		}
		// Only an account's own captures enter its own log; provider results, the
		// logged-in player's included, stay lookups. That is the whole trust rule.
		Map<String, PlayerClogData> store = firstParty ? own : lookups;
		String slot = firstParty ? activeHashKey : cacheKey(name);

		// Preserve varp-sourced totals if they are higher than public providers report.
		PlayerClogData existing = store.get(slot);

		PlayerClogData data = existing != null ? shallowCopy(existing) : new PlayerClogData();
		data.playerName = name;
		data.lastUpdated = Instant.now().toString();
		data.uniqueObtained = result.getUniqueObtained();
		data.uniqueTotal = result.getUniqueTotal();
		if (firstParty && data.uniqueTotal <= 0 && existing != null)
		{
			// Zero is not a settled catalog size; retain the last known total.
			data.uniqueTotal = existing.uniqueTotal;
		}
		if (existing != null && !firstParty)
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
		// A provider result builds on what the lookup held; a capture replaces it whole.
		boolean builds = !firstParty && existing != null;
		data.obtained = builds ? new ConcurrentHashMap<>(data.obtained) : new ConcurrentHashMap<>();
		data.categories = builds ? new ConcurrentHashMap<>(data.categories) : new ConcurrentHashMap<>();

		for (Map.Entry<String, List<ClogResult.ClogItem>> entry
			: result.getObtainedItems().entrySet())
		{
			String cat = entry.getKey();
			data.obtained.put(cat, preserveItemMetadata(entry.getValue(),
				existing != null ? existing.obtained.get(cat) : null));
		}
		// Page lists are the catalog's, shared and never changed in place.
		data.categories.putAll(result.getCategoryItems());

		if (firstParty)
		{
			data.firstPartySetupComplete = true;
			data.pendingUnlocks = PendingClogUnlock.reconcile(data.pendingUnlocks, data.obtained, activeHashKey);
			bumpLastChanged(data, newestObtainedDate(data.obtained));
		}
		// An unchanged full walk needs neither a disk write nor a web-sync signal.
		if (firstParty && sameCapture(data, existing))
		{
			if (unsavedFiles.containsKey(fileKey(slot, true, name)))
			{
				submitSave(slot, true, existing);
			}
			return;
		}
		store.put(slot, data);
		if (firstParty && existing == null)
		{
			data.ownerHash = slot;
			index(name, slot);
		}
		submitSave(slot, firstParty, data);
		log.debug("Cached clog data for '{}' ({} categories)", name, data.obtained.size());
		if (firstParty)
		{
			notifyFirstPartyChanged();
		}
	}

	private static boolean sameCapture(PlayerClogData data, PlayerClogData prior)
	{
		if (prior == null || data.uniqueObtained != prior.uniqueObtained
			|| data.uniqueTotal != prior.uniqueTotal
			|| !Objects.equals(data.playerName, prior.playerName)
			|| !Objects.equals(data.ownerHash, prior.ownerHash)
			|| data.providerAccountType != prior.providerAccountType
			|| !Objects.equals(data.lastChanged, prior.lastChanged)
			|| !Objects.equals(data.pendingUnlocks, prior.pendingUnlocks)
			|| data.firstPartySetupComplete != prior.firstPartySetupComplete
			|| !Objects.equals(data.categories, prior.categories))
		{
			return false;
		}
		return Objects.equals(data.obtained, prior.obtained);
	}



	// Fires after any in-client observation lands (bulk page capture, live
	// unlock), whatever path delivered it - the killclog.com sync trigger
	// lives here at the data seam so no capture route can be forgotten.
	// The listener only schedules a debounced task; it must stay cheap and
	// must not call back into this cache.
	private void notifyFirstPartyChanged()
	{
		Runnable listener = firstPartyChangedListener;
		if (listener != null)
		{
			listener.run();
		}
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
			merged.add(item.filledFrom(priorById.get(item.getId())));
		}
		return merged;
	}

	void rememberPendingUnlock(String playerName, List<Integer> candidates)
	{
		rememberPendingUnlock(playerName, candidates, liveUnlockDate());
	}

	synchronized void rememberPendingUnlock(String playerName, List<Integer> candidates, String date)
	{
		if (candidates == null || candidates.size() < 2 || candidates.size() > 256) return;
		// Missing candidates need a completed local baseline on the serving account.
		PlayerClogData data = ownLog(playerName);
		if (data == null || !data.firstPartySetupComplete) return;
		String validDate = ClogDates.local(date);
		if (validDate == null) return;
		PendingClogUnlock event = new PendingClogUnlock(candidates, validDate, activeHashKey);
		List<PendingClogUnlock> pending = data.pendingUnlocks != null
			? new ArrayList<>(data.pendingUnlocks) : new ArrayList<>();
		pending.removeIf(prior -> prior == null || !activeHashKey.equals(prior.ownerHash)
			|| ClogDates.iso(prior.date) == null);
		// Personal and clan notifications describe the same still-unresolved unlock.
		for (PendingClogUnlock prior : pending)
		{
			if (prior != null && event.candidates.equals(prior.candidates)) return;
		}
		while (pending.size() >= 32) pending.remove(0);
		pending.add(event);
		data.pendingUnlocks = pending;
		submitSave(activeHashKey, true, data);
	}

	public synchronized boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems,
		int obtainedAtKc, String obtainedFrom)
	{
		PlayerClogData data = ownLog(playerName);
		if (data == null || categoryKeys == null || categoryItems == null)
		{
			return false;
		}

		// Record item history here; game counters update the unique total separately.
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
				// to the recents shelf. Format matches the provider date strings
				// so sorting and display stay uniform.
				String unlockDate = liveUnlockDate();
				obtained.add(new ClogResult.ClogItem(itemId, 1, unlockDate, obtainedAtKc, obtainedFrom));
				data.obtained.put(categoryKey, obtained);
				// The summary's last-updated notice reads lastChanged; a live
				// unlock is exactly such a change.
				bumpLastChanged(data, unlockDate);
				changed = true;
			}
		}

		if (changed)
		{
			data.lastUpdated = Instant.now().toString();
			submitSave(activeHashKey, true, data);
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
		data.lastChanged = later(data.lastChanged, date);
	}

	/** The later of two dates, either of which may be missing. */
	private static String later(String a, String b)
	{
		return a == null || (b != null && b.compareTo(a) > 0) ? b : a;
	}

	/** Newest obtained-item date across all categories, or null when none carry one. */
	/* package */ static String newestObtainedDate(Map<String, List<ClogResult.ClogItem>> obtained)
	{
		String newest = null;
		for (List<ClogResult.ClogItem> items : obtained.values())
		{
			for (ClogResult.ClogItem item : items)
			{
				newest = later(newest, item.getDate());
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
		if (playerName == null || providerItems == null || providerItems.isEmpty() || !ready)
		{
			return false;
		}
		boolean self = serving(playerName);
		String slot = self ? activeHashKey : cacheKey(playerName);
		PlayerClogData data = self ? own.get(slot) : lookups.get(slot);
		if (data == null)
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
					items.set(i, item.withDate(date));
					listChanged = true;
					// A healed date can outrank the notice's current stamp; the shelf
					// and the last-updated line must tell the same story.
					bumpLastChanged(data, date);
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
			data.lastUpdated = Instant.now().toString();
			submitSave(slot, self, data);
			log.debug("Merged provider dates into local clog cache for '{}'", playerName);
		}
		return changed;
	}

	public synchronized boolean hasObtainedItem(String playerName, int itemId, List<String> categoryKeys)
	{
		PlayerClogData data = record(playerName);
		if (data == null || categoryKeys == null)
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
		PlayerClogData data = record(playerName);
		if (data == null || itemIds == null)
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
		PlayerClogData data = ownLog(playerName);
		if (data == null)
		{
			return false;
		}
		// Zero is no signal; without this guard a partial cache with -1 totals
		// would report 0 > -1 as a change and trigger a redundant full panel lookup.
		boolean changed = false;
		if (obtained > 0 && obtained > data.uniqueObtained)
		{
			data.uniqueObtained = obtained;
			changed = true;
		}
		if (total > 0 && total > data.uniqueTotal)
		{
			data.uniqueTotal = total;
			changed = true;
		}
		if (changed)
		{
			submitSave(activeHashKey, true, data);
			log.debug("Updated clog totals for '{}': {}/{}", playerName, data.uniqueObtained, data.uniqueTotal);
		}
		return changed;
	}

	public synchronized boolean hasDataFor(String playerName)
	{
		return record(playerName) != null;
	}

	/** Whether this player completed the full local Collection Log Search walk. */
	public synchronized boolean hasCompletedFirstPartySetupFor(String playerName)
	{
		PlayerClogData data = record(playerName);
		return data != null && data.firstPartySetupComplete;
	}

	public synchronized ClogResult toClogResult(String playerName, Map<Integer, String> itemNames)
	{
		PlayerClogData data = record(playerName);
		return data != null ? result(data, itemNames != null ? itemNames : new HashMap<>(), false) : null;
	}

	/** Defensive copies, since callers may mutate their maps. A sync payload drops empty categories. */
	private static ClogResult result(PlayerClogData data, Map<Integer, String> itemNames, boolean sync)
	{
		Map<String, List<ClogResult.ClogItem>> obtainedCopy = new HashMap<>();
		for (Map.Entry<String, List<ClogResult.ClogItem>> entry : data.obtained.entrySet())
		{
			if (!sync || !entry.getValue().isEmpty())
			{
				obtainedCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
			}
		}
		ClogResult result = new ClogResult(data.playerName, obtainedCopy, ClogResult.copyPages(data.categories), itemNames,
			data.lastChanged, data.providerAccountType);
		// A sync sends a counted zero; the panel shows only a real count.
		if (data.uniqueObtained > (sync ? -1 : 0))
		{
			result.setUniqueObtained(data.uniqueObtained);
		}
		if (data.uniqueTotal > (sync ? -1 : 0))
		{
			result.setUniqueTotal(data.uniqueTotal);
		}
		result.setRunStartedAt(data.runStartedAt);
		result.setRunStartXp(data.runStartXp);
		return result;
	}

	/**
	 * Hardcore Worlds: a reset sends the character back to Tutorial Island with nothing, so its total XP falls. The
	 * own log then starts over, dated, and the game's own Collection Log fills it again; true when it did. Read only
	 * once the world has settled, so a login's stats are all in; {@code save} keeps a higher reading on disk (once a
	 * login), so a reset across a relog is still seen.
	 */
	public synchronized boolean startOverOnReset(String playerName, long overallXp, boolean save)
	{
		PlayerClogData data = ownLog(playerName);
		if (data == null || overallXp <= 0)
		{
			return false;
		}
		boolean reset = overallXp < data.overallXp;
		if (reset)
		{
			PlayerClogData fresh = new PlayerClogData();
			fresh.playerName = data.playerName;
			fresh.ownerHash = data.ownerHash;
			fresh.uniqueTotal = data.uniqueTotal;
			fresh.categories = new HashMap<>();
			fresh.obtained = new HashMap<>();
			fresh.runStartedAt = Instant.now().toString();
			fresh.runStartXp = overallXp;
			fresh.lastUpdated = fresh.runStartedAt;
			own.put(activeHashKey, fresh);
			data = fresh;
		}
		long before = data.overallXp;
		data.overallXp = overallXp;
		// Saved on a reset, once a login on the settle tick when the XP rose, and for the first reading a log gets: a
		// log set up after its world settled kept 0 on disk, so a reset across the next relog went unseen (Sol 10/27).
		if (reset || overallXp > before && (save || before == 0))
		{
			submitSave(activeHashKey, true, data);
		}
		return reset;
	}

	/**
	 * Whether this player's own log holds anything a sync would carry. Lookups
	 * and empty first walks both answer false: the sync chalice and the
	 * automatic sync triggers key off this, never off mere cache presence.
	 */
	public synchronized boolean hasFirstPartyDataFor(String playerName)
	{
		PlayerClogData data = record(playerName);
		return data != null && (isActivePlayer(playerName) || data != lookups.get(cacheKey(playerName)))
			&& data.obtained.values().stream().anyMatch(items -> !items.isEmpty());
	}

	/** {@link #hasFirstPartyDataFor} for the logged-in player. */
	public boolean hasFirstPartyDataForActive()
	{
		return hasFirstPartyDataFor(session.getName());
	}

	/**
	 * The sync payload: the serving account's own log, which by construction
	 * holds only what this client captured.
	 */
	public synchronized ClogResult toFirstPartySyncResult(String playerName)
	{
		PlayerClogData data = ownLog(playerName);
		return data != null ? result(data, new HashMap<>(), true) : null;
	}

	// Disk I/O, always on the diskWriter thread.

	private String fileKey(String slot, boolean ownLog, String name)
	{
		return ownLog ? StoreFiles.ownFileName(slot) : StoreFiles.LOOKUPS + "/" + fileName(name);
	}

	/**
	 * Queue a debounced save of a snapshot. An own log's slot is its account
	 * hash and its file is fixed when queued, so a save can never land in
	 * another account's file whoever is logged in when the debounce fires.
	 */
	private void submitSave(String slot, boolean ownLog, PlayerClogData data)
	{
		Filepath folder = logs;
		PlayerClogData snapshot = shallowCopy(data);
		String file = fileKey(slot, ownLog, snapshot.playerName);
		unsavedFiles.put(file, snapshot);
		if (folder == null)
		{
			return;
		}
		Filepath dest = folder.join(file);
		submitDiskWrite(file, () ->
		{
			try
			{
				dest.getParent().createDirectories();
				StoreFiles.write(gson, folder, dest, snapshot);
				// An older write must not clear a newer pending snapshot.
				unsavedFiles.remove(file, snapshot);
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Kill Clog could not save '{}': {}", file, e.getMessage());
			}
		});
	}

	/** A damaged log, moved aside under a name no load reads, so no save writes over the evidence. */
	private static void setAside(Filepath folder, Filepath file)
	{
		try
		{
			atomicMove(file, folder.join(".unreadable-" + file.getFileName() + "-" + System.currentTimeMillis() + ".json"));
		}
		catch (IOException e)
		{
			log.debug("Kill Clog could not set aside '{}': {}", file.getFileName(), e.getMessage());
		}
	}

	/**
	 * The cap holds on disk within a session too, not only at the next start: a lookup that falls out of memory
	 * takes its file with it, on the disk thread and in place of any save still queued for it.
	 */
	private void forgetLookupFile(String name)
	{
		Filepath folder = logs;
		if (folder == null || name == null)
		{
			return;
		}
		String file = fileKey(null, false, name);
		unsavedFiles.remove(file);
		Filepath dest = folder.join(file);
		submitDiskWrite(file, () ->
		{
			try
			{
				dest.deleteIfExists();
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Kill Clog could not remove '{}': {}", file, e.getMessage());
			}
		});
	}

	/**
	 * Submit a disk write for a file, coalescing bursts within DEBOUNCE_MS into a single write.
	 * The latest snapshot wins, including during the nonblocking shutdown flush.
	 */
	private void submitDiskWrite(String file, Runnable task)
	{
		boolean wasFirst = pendingByFile.put(file, task) == null;
		if (!wasFirst)
		{
			return;
		}
		try
		{
			diskWriter.schedule(() -> flush(file), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException ignored)
		{
			// A writer that has shut down takes nothing more; the file stays unsaved and the next capture resaves it.
			pendingByFile.remove(file, task);
			log.debug("Disk write rejected (executor shutting down)");
		}
	}

	/** Runs a file's latest queued write, if one is still waiting. */
	private void flush(String file)
	{
		Runnable latest = pendingByFile.remove(file);
		if (latest != null)
		{
			latest.run();
		}
	}

	/** Genuinely atomic where the filesystem allows it; plain replace as the
	 *  documented fallback (some filesystems refuse ATOMIC_MOVE). */
	static void atomicMove(Filepath from, Filepath to) throws IOException
	{
		try
		{
			from.moveTo(to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			from.moveTo(to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * A lookup's file name: lowercased, spaces as underscores, [a-z0-9_-] only.
	 * RuneLite refuses Windows device names (con, aux, nul...) with any
	 * extension, so those take a leading '+' that no real name can produce.
	 */
	static String fileName(String playerName)
	{
		String stem = cacheKey(playerName).replace(' ', '_').replaceAll("[^a-z0-9_-]", "");
		return (stem.matches("con|prn|aux|nul|(com|lpt)[1-9]") ? "+" : "") + stem + ".json";
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
		copy.categories = new HashMap<>(src.categories);
		copy.obtained = new HashMap<>(src.obtained);
		copy.firstPartySetupComplete = src.firstPartySetupComplete;
		copy.pendingUnlocks = src.pendingUnlocks != null ? new ArrayList<>(src.pendingUnlocks) : null;
		copy.overallXp = src.overallXp;
		copy.runStartedAt = src.runStartedAt;
		copy.runStartXp = src.runStartXp;
		return copy;
	}
}
