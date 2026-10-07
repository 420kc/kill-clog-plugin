package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.NoSuchFileException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Multi-account, disk-backed collection log store.
 *
 * <p>Each account that captures on this PC has one own log, {@code <hash>.json},
 * written only by the client logged into that account; other players' lookups
 * live apart in {@code lookups/<name>.json} and can never ship. The folder is
 * opened on this store's disk thread: RuneLite's own move, the one-time
 * {@link StoreMigration}, then every own log and the newest lookups load into
 * memory. Until that finishes the store is not ready and serves nothing; after
 * it, every read and write is memory-only and disk only receives debounced
 * saves.
 */
@Slf4j
@Singleton
public class LocalClogCache
{
	private static final int LOOKUP_CAP = 256;
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
			return size() > LOOKUP_CAP;
		}
	};

	private volatile String activePlayer;
	// The logged-in account, set once its own log is serving.
	private volatile String activeHashKey;
	private final AtomicReference<String> pendingRenameNotice = new AtomicReference<>();
	// Bumped at logout: deferred work from a dead session must not act.
	private final AtomicLong sessionEpoch = new AtomicLong();

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

	LocalClogCache(Gson gson, ScheduledExecutorService diskWriter)
	{
		this.gson = gson;
		this.diskWriter = diskWriter;
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
		return playerName.toLowerCase(Locale.ROOT);
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
			// A failed migration leaves 2.4's files for the next start; what is
			// already in the new layout still loads.
			StoreMigration.run(gson, folder);
			try
			{
				for (Filepath file : list(folder))
				{
					if (!file.getFileName().matches("[0-9a-f]{16}\\.json"))
					{
						continue;
					}
					PlayerClogData data = readRecord(file);
					if (data == null || !file.getFileName().equals(ownFile(data.ownerHash)))
					{
						// Damaged bytes are kept aside, never overwritten by the next save.
						atomicMove(file, folder.join(".unreadable-" + file.getFileName() + "-"
							+ System.currentTimeMillis() + ".json"));
						continue;
					}
					loadedOwn.put(data.ownerHash, data);
				}
				Filepath lookupDir = folder.join(StoreMigration.LOOKUPS);
				if (lookupDir.isDirectory())
				{
					List<Filepath> files = list(lookupDir);
					files.sort(Comparator.comparing(LocalClogCache::modified).reversed());
					for (Filepath file : files.subList(0, Math.min(LOOKUP_CAP, files.size())))
					{
						PlayerClogData data = readRecord(file);
						if (data != null && data.playerName != null)
						{
							loadedLookups.add(0, data);
						}
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
			return StoreMigration.ownFileName(hashKey);
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

	private static List<Filepath> list(Filepath dir) throws IOException
	{
		try (Stream<Filepath> walk = dir.walk(1))
		{
			return walk.filter(Filepath::isFile).collect(Collectors.toList());
		}
	}

	private PlayerClogData readRecord(Filepath file)
	{
		PlayerClogData data = StoreMigration.read(gson, file, PlayerClogData.class);
		if (data == null || data.categories == null)
		{
			return null;
		}
		// Gson builds plain maps; cache writes and EDT reads share these.
		data.categories = new ConcurrentHashMap<>(data.categories);
		data.obtained = data.obtained != null ? new ConcurrentHashMap<>(data.obtained) : new ConcurrentHashMap<>();
		data.firstPartySetupComplete = ClogRecords.hasCompletedFirstPartySetup(data);
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

	/** Logout: the session's account and any queued notice end with it. */
	public synchronized void onSessionEnded()
	{
		sessionEpoch.incrementAndGet();
		pendingRenameNotice.set(null);
		activeHashKey = null;
		activePlayer = null;
	}

	/** The current session fence value, captured while the session is live. */
	public long currentSessionEpoch()
	{
		return sessionEpoch.get();
	}

	/**
	 * Commit a non-blocking side effect only while the caller's login session
	 * is still current, under the same monitor as {@link #onSessionEnded()}.
	 */
	public synchronized <T> T commitIfSessionCurrent(long expectedEpoch, Supplier<T> commit)
	{
		if (sessionEpoch.get() != expectedEpoch)
		{
			return null;
		}
		return commit.get();
	}

	/**
	 * The logged-in account takes up its own log. False until the store is
	 * ready; the caller retries next tick. A different stored name is a name
	 * change: the log follows the account, and one chat line says so.
	 */
	public synchronized boolean activate(String name, long accountHash)
	{
		if (!ready || name == null || accountHash == -1)
		{
			return false;
		}
		activePlayer = name;
		activeHashKey = Long.toString(accountHash);
		PlayerClogData data = own.get(activeHashKey);
		if (data != null && data.playerName != null && !cacheKey(data.playerName).equals(cacheKey(name)))
		{
			pendingRenameNotice.set(data.playerName);
			if (activeHashKey.equals(ownerByName.get(cacheKey(data.playerName))))
			{
				ownerByName.remove(cacheKey(data.playerName));
			}
			data.playerName = name;
			index(name, activeHashKey);
			submitSave(activeHashKey, true, data);
		}
		return true;
	}

	/** One chat line per name change, consumed by the plugin's tick. */
	public String consumeRenameNotice()
	{
		return pendingRenameNotice.getAndSet(null);
	}

	/** The sync pre-flight: this session, this account, its own log serving. */
	public synchronized boolean servesAccount(String rsn, long accountHash, long expectedEpoch)
	{
		return sessionEpoch.get() == expectedEpoch && serving(rsn)
			&& Long.toString(accountHash).equals(activeHashKey);
	}

	/** Flush accepted saves on the same queue; a new session cannot overtake them. */
	public void shutdown()
	{
		diskWriter.execute(() ->
		{
			for (String file : new ArrayList<>(pendingByFile.keySet()))
			{
				Runnable latest = pendingByFile.remove(file);
				if (latest != null)
				{
					latest.run();
				}
			}
		});
	}

	/** A League store that is done for good: flush its saves, then let its writer end. */
	public void close()
	{
		shutdown();
		diskWriter.shutdown();
	}

	/** The player whose captures this store takes; true once their own log is serving. */
	public synchronized boolean setActivePlayer(String name)
	{
		if (name == null || activePlayer == null || !cacheKey(name).equals(cacheKey(activePlayer)))
		{
			// A different player waits for activate() with their account.
			activePlayer = name;
			activeHashKey = null;
		}
		return serving(name);
	}

	public boolean isActivePlayer(String name)
	{
		return RsnInputPolicy.sameName(activePlayer, name);
	}

	private boolean serving(String name)
	{
		return ready && activeHashKey != null && isActivePlayer(name);
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
			return activeHashKey != null ? own.get(activeHashKey) : null;
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
		if (existing == null)
		{
			data.firstPartySetupComplete = false;
		}
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
		data.obtained = !firstParty && data.obtained != null
			? new ConcurrentHashMap<>(data.obtained)
			: new ConcurrentHashMap<>();
		data.categories = !firstParty && data.categories != null
			? new ConcurrentHashMap<>(data.categories)
			: new ConcurrentHashMap<>();

		for (Map.Entry<String, List<ClogResult.ClogItem>> entry
			: result.getObtainedItems().entrySet())
		{
			String cat = entry.getKey();
			data.obtained.put(cat, preserveItemMetadata(entry.getValue(),
				existing != null && existing.obtained != null ? existing.obtained.get(cat) : null));
		}
		for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
		{
			data.categories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}

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
			|| !Objects.equals(data.firstPartySetupComplete, prior.firstPartySetupComplete)
			|| !Objects.equals(data.categories, prior.categories)
			|| prior.obtained == null || !data.obtained.keySet().equals(prior.obtained.keySet()))
		{
			return false;
		}
		for (String category : data.obtained.keySet())
		{
			List<ClogResult.ClogItem> items = data.obtained.get(category);
			List<ClogResult.ClogItem> previous = prior.obtained.get(category);
			if (previous == null || items.size() != previous.size()) return false;
			for (int i = 0; i < items.size(); i++)
			{
				ClogResult.ClogItem item = items.get(i);
				ClogResult.ClogItem old = previous.get(i);
				if (item.getId() != old.getId() || item.getCount() != old.getCount()
					|| item.getObtainedAtKc() != old.getObtainedAtKc()
					|| !Objects.equals(item.getDate(), old.getDate())
					|| !Objects.equals(item.getObtainedFrom(), old.getObtainedFrom())) return false;
			}
		}
		return true;
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

	void rememberPendingUnlock(String playerName, List<Integer> candidates)
	{
		rememberPendingUnlock(playerName, candidates, liveUnlockDate());
	}

	synchronized void rememberPendingUnlock(String playerName, List<Integer> candidates, String date)
	{
		if (playerName == null || candidates == null || candidates.size() < 2 || candidates.size() > 256) return;
		// Missing candidates need a completed local baseline on the serving account.
		PlayerClogData data = serving(playerName) ? own.get(activeHashKey) : null;
		if (data == null || !Boolean.TRUE.equals(data.firstPartySetupComplete)) return;
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

	public boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems)
	{
		return mergeObtainedItem(playerName, itemId, categoryKeys, categoryItems, 0, null);
	}

	public synchronized boolean mergeObtainedItem(String playerName, int itemId,
		List<String> categoryKeys, Map<String, List<Integer>> categoryItems,
		int obtainedAtKc, String obtainedFrom)
	{
		if (playerName == null || categoryKeys == null || categoryItems == null || !serving(playerName))
		{
			return false;
		}
		PlayerClogData data = own.get(activeHashKey);
		if (data == null)
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
		if (playerName == null || providerItems == null || providerItems.isEmpty() || !ready)
		{
			return false;
		}
		boolean self = serving(playerName);
		String slot = self ? activeHashKey : cacheKey(playerName);
		PlayerClogData data = self ? own.get(slot) : lookups.get(slot);
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
			submitSave(slot, self, data);
			log.debug("Merged provider dates into local clog cache for '{}'", playerName);
		}
		return changed;
	}

	public synchronized boolean hasObtainedItem(String playerName, int itemId, List<String> categoryKeys)
	{
		PlayerClogData data = record(playerName);
		if (data == null || data.obtained == null || categoryKeys == null)
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
		if (data == null || data.obtained == null || itemIds == null)
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
		PlayerClogData data = serving(playerName) ? own.get(activeHashKey) : null;
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
		PlayerClogData data = serving(playerName) ? own.get(activeHashKey) : null;
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
			submitSave(activeHashKey, true, data);
			log.debug("Updated clog totals for '{}': {}/{}", playerName, obtained, total);
		}
	}

	public synchronized boolean hasDataFor(String playerName)
	{
		return record(playerName) != null;
	}

	/** Whether this player completed the full local Collection Log Search walk. */
	public synchronized boolean hasCompletedFirstPartySetupFor(String playerName)
	{
		return ClogRecords.hasCompletedFirstPartySetup(record(playerName));
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
		Map<String, List<Integer>> categoriesCopy = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : data.categories.entrySet())
		{
			categoriesCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		ClogResult result = new ClogResult(data.playerName, obtainedCopy, categoriesCopy, itemNames,
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
		return result;
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
		return hasFirstPartyDataFor(activePlayer);
	}

	/**
	 * The sync payload: the serving account's own log, which by construction
	 * holds only what this client captured.
	 */
	public synchronized ClogResult toFirstPartySyncResult(String playerName)
	{
		return serving(playerName) && own.get(activeHashKey) != null
			? result(own.get(activeHashKey), new HashMap<>(), true) : null;
	}

	// Disk I/O, always on the diskWriter thread.

	private String fileKey(String slot, boolean ownLog, String name)
	{
		return ownLog ? StoreMigration.ownFileName(slot) : StoreMigration.LOOKUPS + "/" + fileName(name);
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
		Filepath dest = ownLog ? folder.join(file) : folder.join(StoreMigration.LOOKUPS, fileName(snapshot.playerName));
		submitDiskWrite(file, () ->
		{
			try
			{
				dest.getParent().createDirectories();
				StoreMigration.write(gson, folder, dest, snapshot);
				// An older write must not clear a newer pending snapshot.
				unsavedFiles.remove(file, snapshot);
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Kill Clog could not save '{}': {}", file, e.getMessage());
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
			diskWriter.schedule(() ->
			{
				Runnable latest = pendingByFile.remove(file);
				if (latest != null)
				{
					latest.run();
				}
			}, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException ignored)
		{
			if (pendingByFile.remove(file, task))
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
	 * Files.notExists for a Filepath: true only when the file is confirmed
	 * missing. One that cannot be checked counts as present, so the migration
	 * never writes over a file it could not see.
	 */
	static boolean absent(Filepath file)
	{
		try
		{
			file.getLastModifiedTime();
			return false;
		}
		catch (NoSuchFileException e)
		{
			return true;
		}
		catch (IOException e)
		{
			return false;
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
		copy.categories = src.categories != null ? new HashMap<>(src.categories) : new HashMap<>();
		copy.obtained = src.obtained != null ? new HashMap<>(src.obtained) : new HashMap<>();
		copy.firstPartySetupComplete = src.firstPartySetupComplete;
		copy.pendingUnlocks = src.pendingUnlocks != null ? new ArrayList<>(src.pendingUnlocks) : null;
		return copy;
	}
}
