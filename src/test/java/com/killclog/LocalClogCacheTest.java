package com.killclog;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalClogCacheTest
{
	@Test
	public void testProviderAccountTypeSurvivesCachedRender() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		ClogResult original = new ClogResult(
			"Rng Shango",
			Collections.emptyMap(),
			Collections.emptyMap(),
			new HashMap<>(),
			"2026-05-28 03:21:32",
			AccountType.GROUP_IRONMAN);

		cache.cacheResult(original);
		ClogResult cached = cache.toClogResult("Rng Shango", Collections.emptyMap());

		assertNotNull(cached);
		assertEquals(AccountType.GROUP_IRONMAN, cached.getProviderAccountType());
	}

	@Test
	public void testPartialProviderResultPreservesCachedCategories() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheResult(clog(
			"Fast 07",
			categoryItems("vetion", 1, 2, 3),
			obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45",
			AccountType.GROUP_IRONMAN));
		cache.cacheResult(clog(
			"Fast 07",
			categoryItems("venenatis", 4, 5, 6),
			obtainedItems("venenatis", 4)));

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
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("magus", itemList(1, 2, 3));
		categories.put("all_pets", itemList(2, 4));

		cache.cacheResult(clog(
			"Fast 07",
			categories,
			obtainedItems("magus", 1)));

		boolean changed = cache.mergeObtainedItem(
			"Fast 07",
			2,
			itemListAsStrings("magus", "all_pets"),
			categories);

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertTrue(changed);
		assertNotNull(cached);
		assertEquals(2, cached.getObtainedItems().get("magus").size());
		assertEquals(1, cached.getObtainedItems().get("all_pets").size());
		assertEquals(2, cached.getObtainedItems().get("all_pets").get(0).getId());
	}

	@Test
	public void testMergeObtainedItemIsIdempotent() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Map<String, List<Integer>> categories = categoryItems("magus", 1, 2, 3);

		cache.cacheResult(clog(
			"Fast 07",
			categories,
			obtainedItems("magus", 1, 2)));

		boolean changed = cache.mergeObtainedItem("Fast 07", 2,
			itemListAsStrings("magus"), categories);

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertFalse(changed);
		assertNotNull(cached);
		assertEquals(2, cached.getObtainedItems().get("magus").size());
	}

	@Test
	public void testHasObtainedItemChecksMappedCategories() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheResult(clog(
			"Fast 07",
			categoryItems("magus", 1, 2, 3),
			obtainedItems("magus", 1, 2)));

		assertTrue(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 3, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("venenatis")));
	}

	@Test
	public void testMergeObtainedItemBumpsUniqueTotalOnceForNewUniques() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("magus", itemList(1, 2, 3));
		categories.put("all_pets", itemList(2, 4));

		ClogResult synced = clog("Fast 07", categories, obtainedItems("magus", 1));
		synced.setUniqueObtained(5);
		cache.cacheResult(synced);

		// A brand-new unique bumps the sidebar total immediately.
		cache.mergeObtainedItem("Fast 07", 4, itemListAsStrings("all_pets"), categories);
		assertEquals(6, cache.toClogResult("Fast 07", Collections.emptyMap()).getUniqueObtained());

		// Merging the same unlock again changes nothing.
		cache.mergeObtainedItem("Fast 07", 4, itemListAsStrings("all_pets"), categories);
		assertEquals(6, cache.toClogResult("Fast 07", Collections.emptyMap()).getUniqueObtained());

		// A new unique on one page counts once...
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus"), categories);
		assertEquals(7, cache.toClogResult("Fast 07", Collections.emptyMap()).getUniqueObtained());

		// ...and landing on its second page later is not another unique.
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("all_pets"), categories);
		assertEquals(7, cache.toClogResult("Fast 07", Collections.emptyMap()).getUniqueObtained());
	}

	@Test
	public void testMergeObtainedItemRequiresExistingCache() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		assertFalse(cache.mergeObtainedItem(
			"Fast 07",
			2,
			itemListAsStrings("magus"),
			categoryItems("magus", 1, 2, 3)));
	}

	@Test
	public void testCategoryResyncPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));

		// Live unlock lands with kill provenance and a date.
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		// A later chalice capture rebuilds the page with bare items; the
		// wholesale replace must not cost the drop its provenance.
		List<ClogResult.ClogItem> bare = new ArrayList<>();
		bare.add(new ClogResult.ClogItem(1, 1, null));
		bare.add(new ClogResult.ClogItem(2, 1, null));
		cache.mergeCategory("Fast 07", "vorkath", itemList(1, 2, 3), bare);

		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertNotNull(survived);
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
		assertNotNull(survived.getDate());
	}

	@Test
	public void testProviderRefreshPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		// A provider refresh arrives bare except for its own date on the item.
		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		List<ClogResult.ClogItem> items = new ArrayList<>();
		items.add(new ClogResult.ClogItem(1, 1, null));
		items.add(new ClogResult.ClogItem(2, 1, "2026-07-19 10:00:00"));
		providerObtained.put("vorkath", items);
		cache.cacheResult(clog("Fast 07", categories, providerObtained));

		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertNotNull(survived);
		// The live unlock marked item 2, so its record is inviolable: the
		// provider refresh can neither replace its client-observed date nor
		// its provenance. (Date healing for unmarked records rides
		// mergeProviderDates, which only fills gaps.)
		assertNotNull(survived.getDate());
		assertNotEquals("2026-07-19 10:00:00", survived.getDate());
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
	}

	@Test
	public void testOverlayRacingLiveUnlocksNeverDropsItems() throws Exception
	{
		// The provider-date overlay lands on an HTTP completion thread while
		// live unlocks merge from the client thread. Serialized mutation must
		// never let the overlay's copy/modify/put overwrite a concurrent merge.
		for (int round = 0; round < 25; round++)
		{
			LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
			List<Integer> all = new ArrayList<>();
			for (int i = 1; i <= 260; i++)
			{
				all.add(i);
			}
			Map<String, List<Integer>> categories = new HashMap<>();
			categories.put("vorkath", all);

			// Seed 200 undated obtained items so the overlay pass has a wide
			// copy/modify/put window of real work.
			Map<String, List<ClogResult.ClogItem>> seeded = new HashMap<>();
			List<ClogResult.ClogItem> seedItems = new ArrayList<>();
			for (int i = 1; i <= 200; i++)
			{
				seedItems.add(new ClogResult.ClogItem(i, 1, null));
			}
			seeded.put("vorkath", seedItems);
			cache.cacheResult(clog("Fast 07", categories, seeded));

			Map<String, List<ClogResult.ClogItem>> providerDates = new HashMap<>();
			List<ClogResult.ClogItem> dated = new ArrayList<>();
			for (int i = 1; i <= 200; i++)
			{
				dated.add(new ClogResult.ClogItem(i, 1, "2026-07-19 10:00:00"));
			}
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

			List<ClogResult.ClogItem> after = cache.toClogResult("Fast 07", Collections.emptyMap())
				.getObtainedItems().get("vorkath");
			assertEquals("round " + round, 260, after.size());
		}
	}

	@Test
	public void testNewestObtainedDateSkipsBareItems() throws Exception
	{
		// A file written before live unlocks bumped lastChanged carries items
		// newer than its stamp; the load-time heal reads the newest dated item
		// and must ignore undated ones entirely.
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		List<ClogResult.ClogItem> slayer = new ArrayList<>();
		slayer.add(new ClogResult.ClogItem(1, 1, "2026-07-12 17:18:29"));
		slayer.add(new ClogResult.ClogItem(2, 1, "2026-07-17 02:02:05"));
		obtained.put("slayer", slayer);
		List<ClogResult.ClogItem> bare = new ArrayList<>();
		bare.add(new ClogResult.ClogItem(3, 1, null));
		obtained.put("brutus", bare);

		assertEquals("2026-07-17 02:02:05", LocalClogCache.newestObtainedDate(obtained));
		assertNull(LocalClogCache.newestObtainedDate(new HashMap<>()));
		assertNull(LocalClogCache.newestObtainedDate(
			Collections.singletonMap("brutus", bare)));
	}

	// First-party marking: the sync payload's provenance boundary.

	@Test
	public void testProviderResultsNeverEnterTheSyncPayload() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);

		// A provider snapshot (pre-login lookup, cross-character search)
		// lands in the display cache but must never ride a push.
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2)));
		assertEquals("provider writes never fire the sync trigger", 0, notified[0]);

		ClogResult display = cache.toClogResult("Zezima", Collections.emptyMap());
		assertEquals("display cache keeps provider items", 2,
			display.getObtainedItems().get("zulrah").size());

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertNotNull(payload);
		assertTrue("the sync payload carries none of it",
			payload.getObtainedItems().isEmpty());

		// A live unlock marks exactly that item; the payload carries it and
		// still excludes the provider-cached pair.
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"),
			categoryItems("zulrah", 1, 2, 3));
		assertEquals(1, notified[0]);
		ClogResult after = cache.toFirstPartySyncResult("Zezima");
		assertEquals(1, after.getObtainedItems().get("zulrah").size());
		assertEquals(3, after.getObtainedItems().get("zulrah").get(0).getId());
	}

	@Test
	public void testBulkCaptureMarksEverythingAndFiresTheTrigger() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);

		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2)));

		assertEquals("the chalice walk schedules a push like any capture", 1, notified[0]);
		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertEquals(2, payload.getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testProviderRefreshCannotReplaceOrRemoveMarkedRecords() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		// Capture first: item 1 lands with live provenance (qty 1, kc 420).
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			new HashMap<>()));
		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("zulrah"),
			categoryItems("zulrah", 1, 2, 3), 420, "Zulrah");

		// Provider refresh second: same item with a provider quantity of 99,
		// plus a provider-only item 2 - and the provider list could just as
		// well have DROPPED item 1 entirely.
		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("zulrah", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"),
			new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), providerObtained));

		// The payload still carries exactly the captured record, untouched.
		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertEquals(1, payload.getObtainedItems().get("zulrah").size());
		ClogResult.ClogItem kept = payload.getObtainedItems().get("zulrah").get(0);
		assertEquals(1, kept.getId());
		assertEquals("client-observed quantity survives the refresh", 1, kept.getCount());
		assertEquals("provenance survives the refresh", 420, kept.getObtainedAtKc());

		// A stale provider list without item 1 cannot evict the mark either.
		Map<String, List<ClogResult.ClogItem>> staleObtained = new HashMap<>();
		staleObtained.put("zulrah", new ArrayList<>(List.of(
			new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), staleObtained));
		ClogResult afterStale = cache.toFirstPartySyncResult("Zezima");
		assertEquals(1, afterStale.getObtainedItems().get("zulrah").size());
		assertEquals(1, afterStale.getObtainedItems().get("zulrah").get(0).getId());

		// The display cache still shows the provider-only item beside it.
		ClogResult display = cache.toClogResult("Zezima", Collections.emptyMap());
		assertEquals(2, display.getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testCrossCategoryProviderRecordCannotRideACaptureMark() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		// Provider caches item 1 under category B with a provider quantity
		// FIRST (pre-login lookup); the client then captures the same item
		// under category A. The mark is earned in A only - B's provider
		// record must not become payload-eligible through it.
		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("clue_b", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"))));
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("clue_b", itemList(1, 5));
		categories.put("boss_a", itemList(1, 6));
		cache.cacheResult(clog("Zezima", categories, providerObtained));

		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("boss_a"), categories, 420, "Boss A");

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertNull("the provider-only category ships nothing",
			payload.getObtainedItems().get("clue_b"));
		assertEquals(1, payload.getObtainedItems().get("boss_a").size());
		assertEquals("only the captured record ships, at its captured quantity",
			1, payload.getObtainedItems().get("boss_a").get(0).getCount());

		// Display still shows both, untouched.
		ClogResult display = cache.toClogResult("Zezima", Collections.emptyMap());
		assertEquals(99, display.getObtainedItems().get("clue_b").get(0).getCount());
	}

	@Test
	public void testEmptyFirstCaptureCannotBirthALegacyStore() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		// A fresh account's first walk captures nothing - the entry is born
		// through the first-party lane with zero marks. It must still be a
		// MARKED (empty) store, not a legacy null-sentinel one.
		cache.cacheFirstPartyResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3),
			new HashMap<>()));

		// A later provider write lands in the display cache...
		cache.cacheResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2)));

		// ...but the payload ships nothing: no capture ever observed these.
		ClogResult payload = cache.toFirstPartySyncResult("Newbie");
		assertNotNull(payload);
		assertTrue("provider items cannot ride a zero-capture store",
			payload.getObtainedItems().isEmpty());

		ClogResult display = cache.toClogResult("Newbie", Collections.emptyMap());
		assertEquals(2, display.getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testProviderLookupDoesNotRevokeLegacyGrandfather() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2)));

		// Simulate a legacy pre-marking store: marker null, items present.
		java.lang.reflect.Field playersField = LocalClogCache.class.getDeclaredField("players");
		playersField.setAccessible(true);
		Object data = ((Map<?, ?>) playersField.get(cache)).get("zezima");
		java.lang.reflect.Field marker = data.getClass().getDeclaredField("firstPartyByCategory");
		marker.setAccessible(true);
		marker.set(data, null);

		// A provider lookup of the same name must not flip the marker: the
		// legacy store keeps its grandfather rights (and its pre-marking
		// merge semantics, where a provider list replaces the category -
		// the documented one-time legacy tradeoff).
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 3)));
		// cacheResult replaces the stored object (shallowCopy + put), so the
		// assertion must read the CURRENT map entry, not the stale reference.
		Object stored = ((Map<?, ?>) playersField.get(cache)).get("zezima");
		assertNull("legacy marker survives provider writes", marker.get(stored));

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertEquals("legacy store still ships whole, under legacy merge semantics", 1,
			payload.getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testLegacyMarklessStoreGrandfathersOnFirstCapture() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2)));

		// Simulate a legacy pre-marking store file: marker null, items present.
		java.lang.reflect.Field playersField = LocalClogCache.class.getDeclaredField("players");
		playersField.setAccessible(true);
		Object data = ((Map<?, ?>) playersField.get(cache)).get("zezima");
		java.lang.reflect.Field marker = data.getClass().getDeclaredField("firstPartyByCategory");
		marker.setAccessible(true);
		marker.set(data, null);

		// A markless store predates marking and still ships whole...
		ClogResult legacy = cache.toFirstPartySyncResult("Zezima");
		assertEquals(2, legacy.getObtainedItems().get("zulrah").size());

		// ...and the first capture grandfathers everything, then marks on.
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"),
			categoryItems("zulrah", 1, 2, 3));
		ClogResult after = cache.toFirstPartySyncResult("Zezima");
		assertEquals(3, after.getObtainedItems().get("zulrah").size());
	}

	private static ClogResult.ClogItem obtainedItem(LocalClogCache cache,
		String playerName, String category, int itemId)
	{
		List<ClogResult.ClogItem> items = cache.toClogResult(playerName, Collections.emptyMap())
			.getObtainedItems().get(category);
		for (ClogResult.ClogItem item : items)
		{
			if (item.getId() == itemId)
			{
				return item;
			}
		}
		return null;
	}

	// ── rename continuity (2.0.4): the local half of the server's migration ──

	@Test
	public void testFollowNameChangeMigratesTheAccountsData()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.seedIdentityForTest(new HashMap<>());
		cache.cacheResult(clog(
			"Old Name",
			categoryItems("vetion", 1, 2, 3),
			obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45",
			AccountType.IRONMAN));

		assertNull("first sight of the account records the mapping, no move",
			cache.followNameChange("Old Name", 42L));
		assertNull("same name again is a no-op",
			cache.followNameChange("Old Name", 42L));

		String previous = cache.followNameChange("New Name", 42L);
		assertEquals("Old Name", previous);
		ClogResult migrated = cache.toClogResult("New Name", Collections.emptyMap());
		assertNotNull("the data followed the account", migrated);
		assertEquals(2, migrated.getObtainedItems().get("vetion").size());
		assertTrue("sync sees local data under the new name", cache.hasDataFor("New Name"));
		assertNull("the old name no longer serves this account's data",
			cache.toClogResult("Old Name", Collections.emptyMap()));
	}

	@Test
	public void testFollowNameChangeOwnDataOutranksStaleLookupCopy()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.seedIdentityForTest(new HashMap<>());
		// The account's own months of captures, under its old name...
		cache.cacheResult(clog(
			"Old Name",
			categoryItems("vetion", 1, 2, 3),
			obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45",
			AccountType.IRONMAN));
		assertNull(cache.followNameChange("Old Name", 42L));
		// ...and a stale lookup-cache copy of the NEW name's previous owner.
		cache.cacheResult(clog(
			"New Name",
			categoryItems("venenatis", 9),
			obtainedItems("venenatis", 9)));

		assertEquals("Old Name", cache.followNameChange("New Name", 42L));
		ClogResult served = cache.toClogResult("New Name", Collections.emptyMap());
		assertNotNull(served);
		assertNotNull("own data won the destination", served.getObtainedItems().get("vetion"));
		assertNull("another player's lookup copy is never mixed into this account's log",
			served.getObtainedItems().get("venenatis"));
	}

	@Test
	public void testFollowNameChangeMergesPostCrashOwnCaptures()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.seedIdentityForTest(new HashMap<>());
		// Months of history under the old name...
		cache.cacheResult(clog(
			"Old Name",
			categoryItems("vetion", 1, 2, 3),
			obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45",
			AccountType.IRONMAN));
		assertNull(cache.followNameChange("Old Name", 42L));
		// ...and post-crash FIRST-PARTY captures under the new name (only the
		// logged-in account's own client marks first-party, which is the
		// proof the destination is the same account, not a lookup copy).
		Map<String, List<Integer>> cats = categoryItems("venenatis", 9);
		cache.cacheResult(clog("New Name", cats, obtainedItems("venenatis")));
		cache.mergeObtainedItem("New Name", 9, itemListAsStrings("venenatis"), cats);

		assertEquals("Old Name", cache.followNameChange("New Name", 42L));
		ClogResult served = cache.toClogResult("New Name", Collections.emptyMap());
		assertNotNull(served);
		assertNotNull("the old history survived the heal", served.getObtainedItems().get("vetion"));
		assertEquals("the old history is intact", 2, served.getObtainedItems().get("vetion").size());
		assertNotNull("the post-crash captures survived too", served.getObtainedItems().get("venenatis"));
	}

	@Test
	public void testRenameNoticeSurvivesWhicheverPathMigratesFirst()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.seedIdentityForTest(new HashMap<>());
		cache.cacheResult(clog(
			"Old Name",
			categoryItems("vetion", 1),
			obtainedItems("vetion", 1)));
		assertNull(cache.followNameChange("Old Name", 42L));

		// The sync pre-flight migrates first and discards the return value...
		assertEquals("Old Name", cache.followNameChange("New Name", 42L));
		// ...the plugin latch's own call is now a no-op...
		assertNull(cache.followNameChange("New Name", 42L));
		// ...but the notice waited for the latch, exactly once.
		assertEquals("Old Name", cache.consumeRenameNotice());
		assertNull("one line per migration, never two", cache.consumeRenameNotice());
	}

	@Test
	public void testFollowNameChangeIgnoresUnknownIdentity()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.seedIdentityForTest(new HashMap<>());
		cache.cacheResult(clog(
			"Bystander",
			categoryItems("vetion", 1),
			obtainedItems("vetion", 1)));

		assertNull("no hash on file, nothing to follow", cache.followNameChange("Someone", 7L));
		assertNull("an invalid hash never records", cache.followNameChange("Someone", -1L));
		assertNotNull("bystanders are untouched",
			cache.toClogResult("Bystander", Collections.emptyMap()));
	}

	private static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained)
	{
		return clog(playerName, categories, obtained, null, null);
	}

	private static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained, String lastChanged, AccountType accountType)
	{
		return new ClogResult(
			playerName,
			obtained,
			categories,
			new HashMap<>(),
			lastChanged,
			accountType);
	}

	private static Map<String, List<Integer>> categoryItems(String category, int... itemIds)
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

	private static Map<String, List<ClogResult.ClogItem>> obtainedItems(String category, int... itemIds)
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

	private static final class NoopScheduledExecutorService extends ScheduledThreadPoolExecutor
	{
		NoopScheduledExecutorService()
		{
			super(1, r ->
			{
				Thread t = new Thread(r, "kill-clog-test-disk");
				t.setDaemon(true);
				return t;
			});
			shutdown();
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			return new CompletedScheduledFuture();
		}
	}

	private static final class CompletedScheduledFuture implements ScheduledFuture<Object>
	{
		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return false;
		}

		@Override
		public boolean isCancelled()
		{
			return false;
		}

		@Override
		public boolean isDone()
		{
			return true;
		}

		@Override
		public Object get()
		{
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
		{
			return null;
		}
	}

	@Test
	public void testFirstPartyPresenceIsPayloadAware() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());

		// Provider-cached data for the player's own name is not a payload.
		cache.cacheResult(clog(
			"Zezima",
			categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2),
			"2026-08-01 00:00:00",
			AccountType.REGULAR));
		assertFalse(cache.hasFirstPartyDataFor("Zezima"));

		// A first-party capture with real items is.
		cache.cacheFirstPartyResult(clog(
			"Zezima",
			categoryItems("zulrah", 1, 2, 3),
			obtainedItems("zulrah", 1, 2),
			"2026-08-01 00:00:00",
			AccountType.REGULAR));
		assertTrue(cache.hasFirstPartyDataFor("Zezima"));

		// The active-player variant follows setActivePlayer.
		assertFalse(cache.hasFirstPartyDataForActive());
		cache.setActivePlayer("Zezima");
		assertTrue(cache.hasFirstPartyDataForActive());
	}

	@Test
	public void testEmptyFirstCaptureIsNotAPayload() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheFirstPartyResult(clog(
			"Fresh Acct",
			new HashMap<>(),
			new HashMap<>(),
			"2026-08-01 00:00:00",
			AccountType.REGULAR));
		assertFalse("an empty first walk must not read as a sendable payload",
			cache.hasFirstPartyDataFor("Fresh Acct"));
	}
}
