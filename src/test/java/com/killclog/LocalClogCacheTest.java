package com.killclog;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		// Incoming values win when present, prior metadata fills the gaps.
		assertEquals("2026-07-19 10:00:00", survived.getDate());
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
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
}
