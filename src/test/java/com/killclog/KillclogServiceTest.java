package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Parsing tests for the killclog.com first-party read leg: sync-index
 * membership, proof-view responses, pb caching, and timestamp conversion.
 * Network behaviour is not exercised.
 */
public class KillclogServiceTest
{
	private KillclogService service;

	@Before
	public void setUp()
	{
		service = new KillclogService(null, new Gson(), null);
	}

	@Test
	public void testParseSyncIndexLowercasesNames()
	{
		Set<String> names = service.parseSyncIndex(
			"{\"schema\":1,\"count\":2,\"names\":[\"420 kc\",\"Fast 07\"]}");
		assertNotNull(names);
		assertEquals(2, names.size());
		assertTrue(names.contains("420 kc"));
		assertTrue(names.contains("fast 07"));
	}

	@Test
	public void testParseSyncIndexRejectsGarbage()
	{
		assertNull(service.parseSyncIndex("{\"schema\":1}"));
		assertNull(service.parseSyncIndex("not json"));
	}

	@Test
	public void testParseProofViewBuildsResultAndCachesPbs()
	{
		String json = "{\"exists\":true,\"rsn\":\"420 kc\","
			+ "\"account_type\":\"hardcore_group_ironman\","
			+ "\"last_changed\":\"2026-08-03T22:15:31.123Z\","
			+ "\"unique_obtained\":1189,\"unique_total\":1561,"
			+ "\"pbs\":{\"Zulrah\":58.2,\"Vorkath\":101.0},"
			+ "\"clog\":{\"total_obtained\":3,\"items_by_category\":{"
			+ "\"zulrah\":[{\"item_id\":12921,\"quantity\":2,\"last_seen_at\":\"2026-08-03T22:15:31.123Z\"}],"
			+ "\"vorkath\":[{\"item_id\":21907,\"quantity\":1},{\"item_id\":22006,\"quantity\":1}]}}}";

		ClogResult result = service.parseProofView("420 kc", json, "420 kc");

		assertNotNull(result);
		assertTrue(result.isFromKillclog());
		assertFalse(result.isFromTemple());
		assertFalse(result.isFromRuneProfile());
		assertEquals("420 kc", result.getPlayerName());
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN, result.getProviderAccountType());
		assertEquals(1189, result.getUniqueObtained());
		assertEquals(1561, result.getUniqueTotal());
		assertEquals("2026-08-03 22:15:31", result.getLastChanged());
		List<ClogResult.ClogItem> zulrah = result.getObtainedItems().get("zulrah");
		assertEquals(1, zulrah.size());
		assertEquals(12921, zulrah.get(0).getId());
		assertEquals(2, zulrah.get(0).getCount());
		// Server item dates are sync-observation times, not unlock dates: dropped.
		assertNull(zulrah.get(0).getDate());
		assertEquals(2, result.getObtainedItems().get("vorkath").size());

		// The same fetch cached the pbs, keyed by panel boss name.
		assertEquals("0:58.20", service.pbText("420 KC", "Zulrah"));
		assertEquals("1:41", service.pbText("420 kc", "Vorkath"));
		assertNull(service.pbText("420 kc", "Kraken"));
		assertNull(service.pbText("Zezima", "Zulrah"));
		assertNull(service.pbText(null, "Zulrah"));
	}

	@Test
	public void testParseProofViewRejectsMalformed()
	{
		assertNull(service.parseProofView("x", "{\"exists\":false}", "x"));
		assertNull(service.parseProofView("x", "not json", "x"));
		assertNull(service.parseProofView("x", "{\"clog\":null}", "x"));
	}

	@Test
	public void testParseProofViewAcceptsValidEmptyLog() throws Exception
	{
		// pbs-only players exist: the server serves a valid EMPTY clog shape
		// for them. Empty is data, not damage - the parse must succeed (no
		// breaker charge), seed catalog denominators, and still cache the pbs.
		ClogService clogService = new ClogService(null, new Gson(), null);
		Field catalog = ClogService.class.getDeclaredField("cachedCategories");
		catalog.setAccessible(true);
		java.util.Map<String, List<Integer>> cats = new java.util.HashMap<>();
		cats.put("zulrah", java.util.Arrays.asList(101, 102, 103));
		catalog.set(clogService, cats);
		KillclogService withCatalog = new KillclogService(null, new Gson(), clogService);

		String json = "{\"rsn\":\"Pbs Only\",\"clog\":{\"items_by_category\":{}},"
			+ "\"pbs\":{\"Zulrah\":58.2}}";
		ClogResult result = withCatalog.parseProofView("Pbs Only", json, "pbs only");

		assertNotNull(result);
		assertFalse(result.isFromKillclog());
		assertFalse(result.isFromTemple());
		assertFalse(result.isFromRuneProfile());
		assertTrue(result.getObtainedItems().isEmpty());
		assertEquals(3, result.getCategoryItems().get("zulrah").size());
		assertEquals("0:58.20", withCatalog.pbText("Pbs Only", "Zulrah"));

		ClogResult temple = new ClogResult("Temple", Collections.singletonMap("zulrah",
			Collections.singletonList(new ClogResult.ClogItem(101, 1, null))), cats,
			Collections.emptyMap(), null, null).withSources(true, false, false);
		ClogResult combined = ClogResult.pickFullest(temple, result);
		assertTrue(combined.isFromTemple());
		assertFalse("PB-only participation must not become clog provenance",
			combined.isFromKillclog());
	}

	@Test
	public void testCatalogSeedsDenominatorsAndEmptyCategories() throws Exception
	{
		ClogService clogService = new ClogService(null, new Gson(), null);
		Field catalog = ClogService.class.getDeclaredField("cachedCategories");
		catalog.setAccessible(true);
		java.util.Map<String, List<Integer>> cats = new java.util.HashMap<>();
		cats.put("zulrah", java.util.Arrays.asList(101, 102, 103));
		cats.put("vorkath", java.util.Arrays.asList(201, 202));
		catalog.set(clogService, cats);
		KillclogService withCatalog = new KillclogService(null, new Gson(), clogService);

		String json = "{\"rsn\":\"420 kc\",\"clog\":{\"items_by_category\":{"
			+ "\"zulrah\":[{\"item_id\":101,\"quantity\":1}]}}}";
		ClogResult result = withCatalog.parseProofView("420 kc", json, "420 kc");

		assertNotNull(result);
		// The logged category gets the full catalog denominator, not its own
		// obtained list...
		assertEquals(3, result.getCategoryItems().get("zulrah").size());
		// ...and the never-logged category is seeded as an honest empty with
		// a full dimmed grid, instead of rendering as generic unsynced.
		assertEquals(2, result.getCategoryItems().get("vorkath").size());
		assertNull(result.getObtainedItems().get("vorkath"));
	}

	@Test
	public void testTempleStyleTimestamp()
	{
		assertEquals("2026-08-03 22:15:31",
			KillclogService.templeStyleTimestamp("2026-08-03T22:15:31.123Z"));
		assertEquals("2026-08-03 22:15:31",
			KillclogService.templeStyleTimestamp("2026-08-03 22:15:31"));
		assertNull(KillclogService.templeStyleTimestamp(null));
	}

	@Test
	public void testParseSkipsInvalidItems()
	{
		// Missing id, zero id, missing quantity, zero quantity: none may
		// fabricate an obtained item into the coverage race.
		String json = "{\"rsn\":\"x\",\"clog\":{\"items_by_category\":{"
			+ "\"zulrah\":[{\"quantity\":2},{\"item_id\":0,\"quantity\":1},{\"item_id\":5},"
			+ "{\"item_id\":6,\"quantity\":0},{\"item_id\":12921,\"quantity\":1}]}}}";
		ClogResult result = service.parseProofView("x", json, "x");
		assertNotNull(result);
		assertEquals("only the fully valid item survives", 1, result.getObtainedItems().get("zulrah").size());
		assertEquals(12921, result.getObtainedItems().get("zulrah").get(0).getId());
	}

	@Test
	public void testImplausibleCountersAreDropped()
	{
		String base = "\"clog\":{\"items_by_category\":{\"zulrah\":[{\"item_id\":1,\"quantity\":1}]}}";
		// Obtained over the ceiling: dropped, plausible total survives.
		ClogResult capped = service.parseProofView("x",
			"{\"unique_obtained\":2000000000,\"unique_total\":1561," + base + "}", "x");
		assertEquals(-1, capped.getUniqueObtained());
		assertEquals(1561, capped.getUniqueTotal());
		// Obtained greater than total: obtained distrusted, total kept.
		ClogResult inverted = service.parseProofView("x",
			"{\"unique_obtained\":1600,\"unique_total\":1561," + base + "}", "x");
		assertEquals(-1, inverted.getUniqueObtained());
		assertEquals(1561, inverted.getUniqueTotal());
		// Obtained WITHOUT a credible total never decides a coverage race.
		ClogResult lone = service.parseProofView("x",
			"{\"unique_obtained\":1189," + base + "}", "x");
		assertEquals(-1, lone.getUniqueObtained());
		assertEquals(-1, lone.getUniqueTotal());
	}

	@Test
	public void testNotFoundAndOptOutEvictEveryFirstPartyTrace()
	{
		String json = "{\"rsn\":\"420 kc\",\"pbs\":{\"Zulrah\":58.2},"
			+ "\"clog\":{\"items_by_category\":{\"zulrah\":[{\"item_id\":1,\"quantity\":1}]}}}";
		assertNotNull(service.onProofViewResponse(200, json, "420 kc", "420 kc"));
		assertNotNull(service.pbText("420 kc", "Zulrah"));

		// 404: purged or never synced. Cached clog AND pbs must vanish.
		assertNull(service.onProofViewResponse(404, null, "420 kc", "420 kc"));
		assertNull(service.pbText("420 kc", "Zulrah"));

		// Re-seed, then 451: opt-out is honored the same way.
		assertNotNull(service.onProofViewResponse(200, json, "420 kc", "420 kc"));
		assertNull(service.onProofViewResponse(451, null, "420 kc", "420 kc"));
		assertNull(service.pbText("420 kc", "Zulrah"));

		// A transient failure, by contrast, keeps serving the stale result.
		assertNotNull(service.onProofViewResponse(200, json, "420 kc", "420 kc"));
		assertNotNull(service.onProofViewResponse(500, null, "420 kc", "420 kc"));
		assertNotNull(service.pbText("420 kc", "Zulrah"));
	}

	@Test
	public void testIndexMissEvictsStaleFirstPartyData() throws Exception
	{
		String json = "{\"rsn\":\"420 kc\",\"pbs\":{\"Zulrah\":58.2},"
			+ "\"clog\":{\"items_by_category\":{\"zulrah\":[{\"item_id\":1,\"quantity\":1}]}}}";
		assertNotNull(service.onProofViewResponse(200, json, "420 kc", "420 kc"));

		// Fresh index that no longer lists the player (opt-out purge upstream).
		Set<String> index = java.util.concurrent.ConcurrentHashMap.newKeySet();
		index.add("someone else");
		setField("syncIndex", index);
		setField("indexFetchedAt", System.currentTimeMillis());
		// Age the cached result past its TTL so the lookup reaches the gate.
		@SuppressWarnings("unchecked")
		java.util.Map<String, Long> fetchTimes =
			(java.util.Map<String, Long>) getField("clogFetchTimes");
		fetchTimes.put("420 kc", System.currentTimeMillis() - 6 * 60 * 1000);

		assertNull(service.lookupClog("420 kc").join());
		assertNull("pbs are evicted with the clog", service.pbText("420 kc", "Zulrah"));
	}

	private void setField(String name, Object value) throws Exception
	{
		Field field = KillclogService.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(service, value);
	}

	private Object getField(String name) throws Exception
	{
		Field field = KillclogService.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(service);
	}
}
