package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Every log a lookup shows is laid on the game's own pages: TempleOSRS files the Ring of 3rd Age under its Mimic and
 * keeps the Rare pages' leftovers on the tier pages, while the game lists the ring on Elite and Master and the
 * leftovers on the Rare pages (59 and 49 items, the wiki and the game's cache, 2026-10-12).
 */
public class ClogOnGamePagesTest
{
	private static final int RING_OF_3RD_AGE = 23185;
	private static final int LAVA_DRAGON_MASK = 12371;
	private static final int LIGHT_TUXEDO_JACKET = 25075;

	private static ClogResult templeLog()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("mimic", Collections.singletonList(new ClogResult.ClogItem(RING_OF_3RD_AGE, 1, "2026-09-01")));
		obtained.put("elite_treasure_trails", Collections.singletonList(new ClogResult.ClogItem(LAVA_DRAGON_MASK, 2, null)));
		Map<String, List<Integer>> pages = new HashMap<>();
		pages.put("mimic", Collections.singletonList(RING_OF_3RD_AGE));
		pages.put("elite_treasure_trails", Arrays.asList(LAVA_DRAGON_MASK, LIGHT_TUXEDO_JACKET));
		ClogResult log = new ClogResult("Marni", obtained, pages, null, null, AccountType.REGULAR);
		log.setUniqueObtained(1703);
		return log.withSources(true, false, false);
	}

	private static Map<String, List<Integer>> gamePages()
	{
		Map<String, List<Integer>> pages = new HashMap<>();
		pages.put("elite_treasure_trails", Arrays.asList(RING_OF_3RD_AGE, LIGHT_TUXEDO_JACKET));
		pages.put("elite_treasure_trails_rare", Collections.singletonList(LAVA_DRAGON_MASK));
		return pages;
	}

	@Test
	public void eachGamePageHoldsWhatTheLogHasWhereverTheSourceFiledIt()
	{
		ClogResult laid = templeLog().onPages(gamePages(), id -> id);

		assertEquals(Arrays.asList(RING_OF_3RD_AGE, LIGHT_TUXEDO_JACKET), laid.getCategoryItems().get("elite_treasure_trails"));
		assertArrayEquals(new int[]{1, 2}, ClogHelper.clogCounts("elite_treasure_trails", laid));
		assertArrayEquals(new int[]{1, 1}, ClogHelper.clogCounts("elite_treasure_trails_rare", laid));
		assertEquals(2, laid.getObtainedItems().get("elite_treasure_trails_rare").get(0).getCount());
	}

	@Test
	public void pagesTheGameDoesNotHaveKeepWhatTheSourceSent()
	{
		ClogResult laid = templeLog().onPages(gamePages(), id -> id);

		// Kill Clog's Mimic row reads the ring from here, as before.
		assertArrayEquals(new int[]{1, 1}, ClogHelper.clogCounts("mimic", laid));
	}

	@Test
	public void theLaidLogKeepsItsProvenanceAndCounters()
	{
		ClogResult laid = templeLog().onPages(gamePages(), id -> id);

		assertTrue(laid.isFromTemple());
		assertEquals(1703, laid.getUniqueObtained());
		assertEquals("Marni", laid.getPlayerName());
	}

	@Test
	public void aPageTheSourceNeverKnewStaysUnknownRatherThanEmpty()
	{
		Map<String, List<Integer>> pages = gamePages();
		pages.put("all_pets", Collections.singletonList(13071));

		ClogResult laid = templeLog().onPages(pages, id -> id);

		assertNull("no key and none of its items: not synced, not 0 of 1", laid.getCategoryItems().get("all_pets"));
	}

	@Test
	public void anItemFiledUnderAVariantIdCountsAsTheGamesId()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("elite_treasure_trails", Collections.singletonList(new ClogResult.ClogItem(99999, 1, null)));
		ClogResult log = new ClogResult("Marni", obtained, new HashMap<>(), null, null, null);

		ClogResult laid = log.onPages(gamePages(), id -> id == 99999 ? RING_OF_3RD_AGE : id);

		assertArrayEquals(new int[]{1, 2}, ClogHelper.clogCounts("elite_treasure_trails", laid));
	}

	@Test
	public void aLogSentBeforeTheGameGrewAPageCountsAgainstEverySlotTheGameHas()
	{
		Map<String, List<Integer>> pages = gamePages();
		pages.put("the_fractured_archive", Arrays.asList(30001, 30002, 30003));

		ClogResult laid = templeLog().onPages(pages, id -> id);

		assertEquals("the page its source never knew stays unknown", null, laid.getCategoryItems().get("the_fractured_archive"));
		assertEquals("but its slots count: 3 known and 3 new", 6, ClogHelper.sumClogTotals(laid)[1]);
	}

	@Test
	public void recentShowsOneFormOfASlotWhicheverLogItsDatesComeFrom()
	{
		Map<String, List<ClogResult.ClogItem>> dated = new HashMap<>();
		dated.put("mimic", Arrays.asList(new ClogResult.ClogItem(99999, 1, "2026-10-01 10:00:00"),
			new ClogResult.ClogItem(RING_OF_3RD_AGE, 1, "2026-10-02 10:00:00")));
		ClogResult temple = new ClogResult("Marni", dated, Collections.singletonMap("mimic",
			Arrays.asList(99999, RING_OF_3RD_AGE)), null, null, AccountType.REGULAR).withSources(true, false, false);
		Map<String, List<ClogResult.ClogItem>> undated = new HashMap<>();
		undated.put("elite_treasure_trails", Arrays.asList(new ClogResult.ClogItem(RING_OF_3RD_AGE, 1, null),
			new ClogResult.ClogItem(LIGHT_TUXEDO_JACKET, 1, null), new ClogResult.ClogItem(LAVA_DRAGON_MASK, 1, null)));
		ClogResult runeProfile = new ClogResult("Marni", undated, gamePages(), null, null, AccountType.REGULAR)
			.withSources(false, true, false);

		ClogResult shown = ClogResult.pickFullerProvider(temple, runeProfile)
			.onPages(gamePages(), id -> id == 99999 ? RING_OF_3RD_AGE : id);

		List<ClogResult.ClogItem> recent = LookupQueries.getRecentItems(shown, 4, id -> id == 99999 ? RING_OF_3RD_AGE : id);
		assertEquals(1, recent.size());
		assertEquals(RING_OF_3RD_AGE, recent.get(0).getId());
	}

	@Test
	public void beforeTheClientHasReadTheLogTheLogIsShownAsSent()
	{
		ClogResult log = templeLog();
		assertSame(log, log.onPages(Collections.emptyMap(), id -> id));
		assertSame(log, new ClogIndex().laid(log));
		assertNull(new ClogIndex().laid(null));
	}

	@Test
	public void theGamesIndexLaysALogOnItsPages()
	{
		ClogIndex index = new ClogIndex();
		index.publishForTest(gamePages(), Collections.emptyMap());

		ClogResult laid = index.laid(templeLog());

		assertArrayEquals(new int[]{1, 2}, ClogHelper.clogCounts("elite_treasure_trails", laid));
	}
}
