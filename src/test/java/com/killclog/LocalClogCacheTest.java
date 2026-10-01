package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalClogCacheTest
{
	private static final long HASH = 77L;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	// ── readiness and the account ──

	@Test
	public void nothingServesOrCapturesUntilTheFolderHasLoaded() throws Exception
	{
		CapturingScheduledExecutorService writer = new CapturingScheduledExecutorService();
		LocalClogCache cache = new LocalClogCache(new Gson(), writer);
		cache.open(() -> null);
		assertFalse("the store says no until its load lands", cache.activate("Tester", HASH));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1), obtainedItems("zulrah", 1)));
		assertFalse(cache.hasDataFor("Zezima"));
		writer.runQueued();
		assertTrue(cache.activate("Tester", HASH));
		assertTrue(cache.folder().isDone());
		assertNull("a memory-only session has no folder", cache.folder().join());
	}

	@Test
	public void capturesNeedTheActivatedAccount() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		assertFalse(cache.setActivePlayer("Tester"));
		cache.cacheFirstPartyResult(clog("Tester", categoryItems("zulrah", 1, 2), obtainedItems("zulrah", 1)));
		assertFalse("a capture before activation waits, as it waited for identity", cache.hasDataFor("Tester"));
		cache.onSessionEnded();
		assertFalse("and it landed nowhere else either", cache.hasDataFor("Tester"));
		assertTrue(cache.activate("Tester", HASH));
		assertTrue(cache.setActivePlayer("Tester"));
		cache.cacheFirstPartyResult(clog("Tester", categoryItems("zulrah", 1, 2), obtainedItems("zulrah", 1)));
		assertTrue(cache.hasFirstPartyDataFor("Tester"));
		assertTrue(cache.hasFirstPartyDataForActive());
		assertFalse("another name is another account", cache.setActivePlayer("Someone"));
		assertFalse(cache.activate("Someone", -1L));
	}

	@Test
	public void logsAreFiledByAccountAndFollowANameChange() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(cache.activate("Old Name", HASH));
		cache.cacheFirstPartyResult(clog("Old Name", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		File own = ownFile(dir, HASH);
		assertTrue(own.exists());
		PlayerClogData saved = new Gson().fromJson(Files.readString(own.toPath()), PlayerClogData.class);
		assertEquals("77", saved.ownerHash);
		assertEquals("Old Name", saved.playerName);

		cache.onSessionEnded();
		assertTrue(cache.activate("New Name", HASH));
		assertEquals("Old Name", cache.consumeRenameNotice());
		assertNull(cache.consumeRenameNotice());
		assertTrue(cache.hasFirstPartyDataFor("New Name"));
		assertEquals("New Name", new Gson().fromJson(Files.readString(own.toPath()), PlayerClogData.class).playerName);
		assertEquals("one file, renamed inside", 1, dir.list((d, n) -> n.endsWith(".json")).length);

		LocalClogCache restarted = onDisk(dir);
		assertTrue(restarted.activate("New Name", HASH));
		assertNull("no change, no notice", restarted.consumeRenameNotice());
		assertTrue(restarted.hasFirstPartyDataFor("New Name"));
	}

	@Test
	public void theSyncPreflightNeedsThisSessionAccountAndName() throws Exception
	{
		LocalClogCache cache = memory("Tester");
		long epoch = cache.currentSessionEpoch();
		assertTrue(cache.servesAccount("Tester", HASH, epoch));
		assertFalse(cache.servesAccount("Tester", 78L, epoch));
		assertFalse(cache.servesAccount("Someone", HASH, epoch));
		cache.onSessionEnded();
		assertFalse(cache.servesAccount("Tester", HASH, epoch));
		assertTrue(cache.activate("Tester", HASH));
		assertFalse("a dead session's epoch never serves", cache.servesAccount("Tester", HASH, epoch));
		assertTrue(cache.servesAccount("Tester", HASH, cache.currentSessionEpoch()));
	}

	@Test
	public void aQueuedSaveStaysInTheAccountThatMadeIt() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		CapturingScheduledDebounceService writer = new CapturingScheduledDebounceService();
		LocalClogCache cache = new LocalClogCache(new Gson(), writer);
		cache.open(() -> TestFolders.folder(dir));
		assertTrue(cache.activate("Shared", HASH));
		cache.cacheFirstPartyResult(clog("Shared", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		cache.onSessionEnded();
		assertTrue(cache.activate("Shared", 78L));
		writer.runQueued();
		assertTrue(ownFile(dir, HASH).exists());
		assertFalse("the next account's file is not written by the last one's capture", ownFile(dir, 78L).exists());
		assertFalse(cache.hasDataFor("Shared"));
	}

	@Test
	public void anUnreadableOwnLogIsKeptAsideAndSetupCanRetry() throws Exception
	{
		for (String damaged : List.of("", "{broken"))
		{
			File dir = temporaryFolder.newFolder();
			Files.writeString(new File(dir, StoreMigration.MARKER).toPath(), "2");
			Files.writeString(ownFile(dir, HASH).toPath(), damaged);
			LocalClogCache cache = onDisk(dir);
			assertTrue(cache.activate("Tester", HASH));
			assertFalse(cache.hasDataFor("Tester"));
			File[] preserved = dir.listFiles((folder, name) -> name.startsWith(".unreadable-"));
			assertEquals(1, preserved.length);
			assertEquals(damaged, Files.readString(preserved[0].toPath()));
			cache.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 1), obtainedItems("hats", 1)));
			LocalClogCache restarted = onDisk(dir);
			assertTrue(restarted.activate("Tester", HASH));
			assertNotNull(restarted.toFirstPartySyncResult("Tester"));
		}
	}

	// ── lookups and local alts ──

	@Test
	public void lookupsNeverEnterAnOwnLogAndAltsPreviewTheirOwn() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(cache.activate("Alt", 78L));
		cache.cacheFirstPartyResult(clog("Alt", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		cache.onSessionEnded();
		assertTrue(cache.activate("Main", HASH));

		assertTrue("a local alt previews its own log", cache.hasFirstPartyDataFor("Alt"));
		cache.cacheResult(clog("Alt", categoryItems("hats", 1, 2), obtainedItems("hats", 1, 2)));
		assertEquals("a fresh lookup then serves", 2,
			cache.toClogResult("Alt", Collections.emptyMap()).getObtainedItems().get("hats").size());
		PlayerClogData alt = new Gson().fromJson(Files.readString(ownFile(dir, 78L).toPath()), PlayerClogData.class);
		assertEquals("the alt's own log is untouched", 1, alt.obtained.get("hats").size());
		assertTrue(new File(dir, "lookups/alt.json").exists());
		assertNull("lookups never ship", cache.toFirstPartySyncResult("Alt"));
	}

	@Test
	public void aNameTwoLocalAccountsCarryPreviewsNothing() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		for (long hash : new long[]{78L, 79L})
		{
			assertTrue(cache.activate("Traded", hash));
			cache.cacheFirstPartyResult(clog("Traded", categoryItems("hats", 1), obtainedItems("hats", 1)));
			cache.onSessionEnded();
		}
		LocalClogCache restarted = onDisk(dir);
		assertTrue(restarted.activate("Main", HASH));
		assertFalse(restarted.hasDataFor("Traded"));
		assertTrue("the account itself still gets its own", restarted.activate("Traded", 79L));
		assertTrue(restarted.hasFirstPartyDataFor("Traded"));
	}

	@Test
	public void lookupsAreCappedAndTheNewestReload() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(cache.activate("Main", HASH));
		for (int i = 0; i < 260; i++)
		{
			cache.cacheResult(clog("Player " + i, categoryItems("hats", 1), obtainedItems("hats", 1)));
			new File(dir, "lookups/player_" + i + ".json").setLastModified(1_000_000L + i * 1000L);
		}
		assertFalse("the oldest fell out of memory", cache.hasDataFor("Player 0"));
		assertTrue(cache.hasDataFor("Player 259"));
		LocalClogCache restarted = onDisk(dir);
		assertTrue(restarted.activate("Main", HASH));
		assertTrue(restarted.hasDataFor("Player 259"));
		assertTrue(restarted.hasDataFor("Player 4"));
		assertFalse("only the newest 256 load", restarted.hasDataFor("Player 3"));
	}

	// ── capture, merge and provenance ──

	@Test
	public void pendingHatDateSurvivesRestartAndOnlyReconcilesForItsOwner() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Gson gson = new Gson();
		LocalClogCache cache = onDisk(dir);
		assertTrue(cache.activate("Tester", HASH));
		Map<String, List<Integer>> cats = categoryItems("hats", 2978, 2991, 2992);
		cache.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		String date = ClogDates.local(java.time.Instant.now().minusSeconds(60).toString());
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992), date);
		cache.rememberPendingUnlock("Tester", List.of(2992, 2991), date);
		PlayerClogData saved = gson.fromJson(Files.readString(ownFile(dir, HASH).toPath()), PlayerClogData.class);
		assertEquals(1, saved.pendingUnlocks.size());
		LocalClogCache restarted = onDisk(dir);
		assertTrue(restarted.activate("Tester", HASH));
		restarted.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978, 2991)));
		ClogResult result = restarted.toFirstPartySyncResult("Tester");
		assertEquals(date, LookupQueries.getRecentItems(result, 5).get(0).getDate());
		assertEquals(2991, LookupQueries.getRecentItems(result, 5).get(0).getId());
		restarted.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978, 2991)));
		assertEquals(date, LookupQueries.getRecentItems(restarted.toFirstPartySyncResult("Tester"), 5).get(0).getDate());
		Map<String, List<ClogResult.ClogItem>> wrongOwner = obtainedItems("hats", 2991);
		PendingClogUnlock.reconcile(saved.pendingUnlocks, wrongOwner, "other-owner");
		assertNull(wrongOwner.get("hats").get(0).getDate());
	}

	@Test
	public void pendingDatesRequireACompletedLocalBaseline() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Gson gson = new Gson();
		LocalClogCache cache = onDisk(dir);
		assertTrue(cache.activate("Tester", HASH));
		Map<String, List<Integer>> cats = categoryItems("hats", 2978, 2991, 2992);
		cache.cacheResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992));
		assertFalse("a provider copy makes no own log to hold it", ownFile(dir, HASH).exists());
		cache.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992));
		PlayerClogData saved = gson.fromJson(Files.readString(ownFile(dir, HASH).toPath()), PlayerClogData.class);
		assertEquals(1, saved.pendingUnlocks.size());
	}

	@Test
	public void shutdownFlushAndNewSessionShareTheExistingWriterQueue() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		ScheduledThreadPoolExecutor writer = new ScheduledThreadPoolExecutor(1);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try
		{
			LocalClogCache cache = new LocalClogCache(new Gson(), writer);
			cache.open(() -> TestFolders.folder(directory));
			cache.folder().get(3, TimeUnit.SECONDS);
			writer.execute(() ->
			{
				entered.countDown();
				try
				{
					assertTrue(release.await(3, TimeUnit.SECONDS));
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new AssertionError(e);
				}
			});
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			cache.cacheResult(clog("Tester", categoryItems("magus", 1, 2), obtainedItems("magus", 1)));
			cache.shutdown();
			assertFalse(writer.isShutdown());
			cache.cacheResult(clog("Tester", categoryItems("magus", 1, 2), obtainedItems("magus", 1, 2)));
			cache.shutdown();
			assertFalse(new File(directory, "lookups/tester.json").exists());
			release.countDown();
			writer.submit(() -> null).get(3, TimeUnit.SECONDS);
			LocalClogCache reloaded = onDisk(directory);
			assertTrue(reloaded.hasDataFor("Tester"));
			assertEquals(2, reloaded.toClogResult("Tester", Collections.emptyMap()).getObtainedItems().get("magus").size());
		}
		finally
		{
			release.countDown();
			writer.shutdownNow();
			assertTrue(writer.awaitTermination(3, TimeUnit.SECONDS));
		}
	}

	@Test
	public void testProviderAccountTypeSurvivesCachedRender() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(new ClogResult("Rng Shango", Collections.emptyMap(), Collections.emptyMap(),
			new HashMap<>(), "2026-05-28 03:21:32", AccountType.GROUP_IRONMAN));
		ClogResult cached = cache.toClogResult("Rng Shango", Collections.emptyMap());
		assertNotNull(cached);
		assertEquals(AccountType.GROUP_IRONMAN, cached.getProviderAccountType());
	}

	@Test
	public void testPartialProviderResultPreservesCachedCategories() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(clog("Fast 07", categoryItems("vetion", 1, 2, 3), obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45", AccountType.GROUP_IRONMAN));
		cache.cacheResult(clog("Fast 07", categoryItems("venenatis", 4, 5, 6), obtainedItems("venenatis", 4)));

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertNotNull(cached);
		assertEquals(2, cached.getCategoryItems().size());
		assertEquals(2, cached.getObtainedItems().size());
		assertEquals(3, cached.getCategoryItems().get("vetion").size());
		assertEquals(3, cached.getCategoryItems().get("venenatis").size());
		assertEquals(2, cached.getObtainedItems().get("vetion").size());
		assertEquals(1, cached.getObtainedItems().get("venenatis").size());
		assertEquals("2026-06-03 01:23:45", cached.getLastChanged());
		assertEquals(AccountType.GROUP_IRONMAN, cached.getProviderAccountType());
	}

	@Test
	public void testMergeObtainedItemAddsItemToMappedCategories() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("magus", itemList(1, 2, 3));
		categories.put("all_pets", itemList(2, 4));
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("magus", 1)));

		boolean changed = cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus", "all_pets"), categories);

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertTrue(changed);
		assertEquals(2, cached.getObtainedItems().get("magus").size());
		assertEquals(1, cached.getObtainedItems().get("all_pets").size());
		assertEquals(2, cached.getObtainedItems().get("all_pets").get(0).getId());
	}

	@Test
	public void testMergeObtainedItemIsIdempotent() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("magus", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("magus", 1, 2)));
		assertFalse(cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus"), categories));
		assertEquals(2, cache.toClogResult("Fast 07", Collections.emptyMap()).getObtainedItems().get("magus").size());
	}

	@Test
	public void testHasObtainedItemChecksMappedCategories() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(clog("Fast 07", categoryItems("magus", 1, 2, 3), obtainedItems("magus", 1, 2)));
		assertTrue(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 3, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("venenatis")));
	}

	@Test
	public void liveUnlockTotalsFollowGameCountersInEitherEventOrder() throws Exception
	{
		for (int initial : List.of(0, 5))
		{
			for (boolean counterFirst : List.of(false, true))
			{
				File dir = temporaryFolder.newFolder();
				LocalClogCache cache = onDisk(dir);
				assertTrue(cache.activate("Tester", HASH));
				Map<String, List<Integer>> categories = Map.of("hats", itemList(1, 2), "other", itemList(1));
				ClogResult baseline = clog("Tester", categories, obtainedItems("hats"));
				baseline.setUniqueObtained(initial);
				cache.cacheFirstPartyResult(baseline);
				if (counterFirst) cache.updateTotalsUpward("Tester", initial + 1, 100);
				cache.mergeObtainedItem("Tester", 1, List.of("hats", "other"), categories, 42, "Boss");
				if (!counterFirst) cache.updateTotalsUpward("Tester", initial + 1, 100);
				// A duplicate notification and a lagging counter cannot add another slot.
				cache.mergeObtainedItem("Tester", 1, List.of("hats", "other"), categories);
				cache.updateTotalsUpward("Tester", initial, 100);
				ClogResult payload = cache.toFirstPartySyncResult("Tester");
				assertEquals(initial + 1, payload.getUniqueObtained());
				assertNotNull(payload.getObtainedItems().get("hats").get(0).getDate());
				assertEquals(42, payload.getObtainedItems().get("hats").get(0).getObtainedAtKc());
				cache.mergeObtainedItem("Tester", 2, List.of("hats"), categories);
				cache.updateTotalsUpward("Tester", initial + 2, 100);
				LocalClogCache restarted = onDisk(dir);
				assertTrue(restarted.activate("Tester", HASH));
				assertEquals(initial + 2, restarted.toFirstPartySyncResult("Tester").getUniqueObtained());
			}
		}
	}

	@Test
	public void testMergeObtainedItemRequiresExistingCache() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		assertFalse(cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus"), categoryItems("magus", 1, 2, 3)));
	}

	@Test
	public void testCategoryResyncPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		// A later chalice capture rebuilds the log with bare items; the
		// wholesale replace must not cost the drop its provenance.
		Map<String, List<ClogResult.ClogItem>> bare = new HashMap<>();
		bare.put("vorkath", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, null))));
		cache.cacheFirstPartyResult(clog("Fast 07", categories, bare));

		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
		assertNotNull(survived.getDate());
	}

	@Test
	public void testProviderRefreshPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("vorkath", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, "2026-07-19 10:00:00"))));
		cache.cacheResult(clog("Fast 07", categories, providerObtained));

		// The live unlock marked item 2, so its record is inviolable.
		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertNotNull(survived.getDate());
		assertNotEquals("2026-07-19 10:00:00", survived.getDate());
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
	}

	@Test
	public void testOverlayRacingLiveUnlocksNeverDropsItems() throws Exception
	{
		// The provider-date overlay lands on an HTTP completion thread while
		// live unlocks merge from the client thread; neither may lose the other.
		for (int round = 0; round < 25; round++)
		{
			LocalClogCache cache = memory("Fast 07");
			List<Integer> all = new ArrayList<>();
			for (int i = 1; i <= 260; i++)
			{
				all.add(i);
			}
			Map<String, List<Integer>> categories = new HashMap<>();
			categories.put("vorkath", all);
			Map<String, List<ClogResult.ClogItem>> seeded = new HashMap<>();
			List<ClogResult.ClogItem> seedItems = new ArrayList<>();
			List<ClogResult.ClogItem> dated = new ArrayList<>();
			for (int i = 1; i <= 200; i++)
			{
				seedItems.add(new ClogResult.ClogItem(i, 1, null));
				dated.add(new ClogResult.ClogItem(i, 1, "2026-07-19 10:00:00"));
			}
			seeded.put("vorkath", seedItems);
			cache.cacheFirstPartyResult(clog("Fast 07", categories, seeded));
			Map<String, List<ClogResult.ClogItem>> providerDates = new HashMap<>();
			providerDates.put("vorkath", dated);

			CountDownLatch start = new CountDownLatch(1);
			Thread overlay = new Thread(() ->
			{
				try
				{
					start.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return;
				}
				cache.mergeProviderDates("Fast 07", providerDates);
			});
			overlay.start();
			start.countDown();
			for (int id = 201; id <= 260; id++)
			{
				cache.mergeObtainedItem("Fast 07", id, itemListAsStrings("vorkath"), categories, id, "Vorkath");
			}
			overlay.join();
			assertEquals("round " + round, 260,
				cache.toClogResult("Fast 07", Collections.emptyMap()).getObtainedItems().get("vorkath").size());
		}
	}

	@Test
	public void testNewestObtainedDateSkipsBareItems() throws Exception
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("slayer", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, "2026-07-12 17:18:29"),
			new ClogResult.ClogItem(2, 1, "2026-07-17 02:02:05"))));
		List<ClogResult.ClogItem> bare = new ArrayList<>(List.of(new ClogResult.ClogItem(3, 1, null)));
		obtained.put("brutus", bare);
		assertEquals("2026-07-17 02:02:05", LocalClogCache.newestObtainedDate(obtained));
		assertNull(LocalClogCache.newestObtainedDate(new HashMap<>()));
		assertNull(LocalClogCache.newestObtainedDate(Collections.singletonMap("brutus", bare)));
	}

	// First-party marking: the sync payload's provenance boundary.

	@Test
	public void providerResultsNeverEnterTheLoggedInPlayersOwnLog() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("provider writes never fire the sync trigger", 0, notified[0]);
		assertFalse("they make no own log", cache.hasDataFor("Zezima"));
		assertNull(cache.toFirstPartySyncResult("Zezima"));
		assertFalse("and a live unlock needs the account's own log", cache.mergeObtainedItem("Zezima", 3,
			itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3)));

		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1)));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("a later provider copy changes nothing", 1,
			cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3));
		assertEquals(2, notified[0]);
		assertEquals(2, cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
		assertEquals(2, cache.toClogResult("Zezima", Collections.emptyMap()).getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testBulkCaptureMarksEverythingAndFiresTheTrigger() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("the chalice walk schedules a push like any capture", 1, notified[0]);
		assertEquals(2, cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
	}

	@Test
	public void providerRefreshesCannotTouchACapturedRecord() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), new HashMap<>()));
		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3), 420, "Zulrah");

		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("zulrah", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"),
			new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), providerObtained));
		Map<String, List<ClogResult.ClogItem>> staleObtained = new HashMap<>();
		staleObtained.put("zulrah", new ArrayList<>(List.of(new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), staleObtained));

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertEquals(1, payload.getObtainedItems().get("zulrah").size());
		ClogResult.ClogItem kept = payload.getObtainedItems().get("zulrah").get(0);
		assertEquals(1, kept.getId());
		assertEquals("client-observed quantity survives", 1, kept.getCount());
		assertEquals("provenance survives", 420, kept.getObtainedAtKc());
		assertEquals("the panel shows the own log", 1,
			cache.toClogResult("Zezima", Collections.emptyMap()).getObtainedItems().get("zulrah").size());
	}

	@Test
	public void aCaptureInOneCategoryCarriesNoProviderRecordFromAnother() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("clue_b", itemList(1, 5));
		categories.put("boss_a", itemList(1, 6));
		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("clue_b", new ArrayList<>(List.of(new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categories, providerObtained));
		cache.cacheFirstPartyResult(clog("Zezima", categories, new HashMap<>()));
		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("boss_a"), categories, 420, "Boss A");

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertNull("the provider-only category ships nothing", payload.getObtainedItems().get("clue_b"));
		assertEquals(1, payload.getObtainedItems().get("boss_a").get(0).getCount());
	}

	@Test
	public void testEmptyFirstCaptureShipsNoProviderItems() throws Exception
	{
		LocalClogCache cache = memory("Newbie");
		cache.cacheFirstPartyResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3), new HashMap<>()));
		cache.cacheResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		ClogResult payload = cache.toFirstPartySyncResult("Newbie");
		assertNotNull(payload);
		assertTrue("provider items cannot ride a zero-capture log", payload.getObtainedItems().isEmpty());
		assertFalse(cache.hasFirstPartyDataFor("Newbie"));
	}

	@Test
	public void testFirstPartyPresenceIsPayloadAware() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertFalse(cache.hasFirstPartyDataFor("Zezima"));
		assertFalse(cache.hasCompletedFirstPartySetupFor("Zezima"));

		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), new HashMap<>(),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertTrue("an empty walk completes setup", cache.hasCompletedFirstPartySetupFor("Zezima"));
		assertFalse("but carries nothing", cache.hasFirstPartyDataFor("Zezima"));
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3));
		assertTrue(cache.hasFirstPartyDataFor("Zezima"));
		assertTrue(cache.hasFirstPartyDataForActive());
	}

	@Test
	public void testEmptyFirstCaptureIsNotAPayload() throws Exception
	{
		LocalClogCache cache = memory("Fresh Acct");
		cache.cacheFirstPartyResult(clog("Fresh Acct", new HashMap<>(), new HashMap<>(),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertTrue("an empty first walk still completes local setup", cache.hasDataFor("Fresh Acct"));
		assertTrue(cache.hasCompletedFirstPartySetupFor("Fresh Acct"));
		assertNotNull(cache.toClogResult("Fresh Acct", new HashMap<>()));
		assertFalse("an empty first walk must not read as a sendable payload", cache.hasFirstPartyDataFor("Fresh Acct"));
	}

	@Test
	public void testSessionEndAndRequestCommitHaveOneAtomicOrder() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		long epoch = cache.currentSessionEpoch();
		CountDownLatch commitEntered = new CountDownLatch(1);
		CountDownLatch releaseCommit = new CountDownLatch(1);
		CountDownLatch logoutStarted = new CountDownLatch(1);
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try
		{
			Future<String> committed = workers.submit(() -> cache.commitIfSessionCurrent(epoch, () ->
			{
				commitEntered.countDown();
				try
				{
					releaseCommit.await(1, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				return "enqueued";
			}));
			assertTrue(commitEntered.await(1, TimeUnit.SECONDS));
			Future<?> logout = workers.submit(() ->
			{
				logoutStarted.countDown();
				cache.onSessionEnded();
			});
			assertTrue(logoutStarted.await(1, TimeUnit.SECONDS));
			assertFalse("logout waits behind an already-committed enqueue", logout.isDone());
			releaseCommit.countDown();
			assertEquals("enqueued", committed.get(1, TimeUnit.SECONDS));
			logout.get(1, TimeUnit.SECONDS);
			assertNull("the ended epoch cannot enqueue another request", cache.commitIfSessionCurrent(epoch, () -> "late"));
		}
		finally
		{
			releaseCommit.countDown();
			workers.shutdownNow();
		}
	}

	// ── helpers ──

	private static LocalClogCache memory(String player)
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		assertTrue(cache.activate(player, HASH));
		return cache;
	}

	/** Open, in memory, nobody logged in. */
	static LocalClogCache ready()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		return cache;
	}

	static LocalClogCache onDisk(File dir)
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> TestFolders.folder(dir));
		return cache;
	}

	static File ownFile(File dir, long hash)
	{
		return new File(dir, StoreMigration.ownFileName(Long.toString(hash)));
	}

	private static ClogResult.ClogItem obtainedItem(LocalClogCache cache, String playerName, String category, int itemId)
	{
		for (ClogResult.ClogItem item : cache.toClogResult(playerName, Collections.emptyMap()).getObtainedItems().get(category))
		{
			if (item.getId() == itemId)
			{
				return item;
			}
		}
		return null;
	}

	static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained)
	{
		return clog(playerName, categories, obtained, null, null);
	}

	private static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained, String lastChanged, AccountType accountType)
	{
		return new ClogResult(playerName, obtained, categories, new HashMap<>(), lastChanged, accountType);
	}

	static Map<String, List<Integer>> categoryItems(String category, int... itemIds)
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put(category, itemList(itemIds));
		return categories;
	}

	private static List<Integer> itemList(int... itemIds)
	{
		List<Integer> items = new ArrayList<>();
		for (int itemId : itemIds)
		{
			items.add(itemId);
		}
		return items;
	}

	private static List<String> itemListAsStrings(String... items)
	{
		List<String> result = new ArrayList<>();
		Collections.addAll(result, items);
		return result;
	}

	static Map<String, List<ClogResult.ClogItem>> obtainedItems(String category, int... itemIds)
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		List<ClogResult.ClogItem> items = new ArrayList<>();
		for (int itemId : itemIds)
		{
			items.add(new ClogResult.ClogItem(itemId, 1, null));
		}
		obtained.put(category, items);
		return obtained;
	}
}
