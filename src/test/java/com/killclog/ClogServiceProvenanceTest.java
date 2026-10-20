package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogServiceProvenanceTest
{
	@Test
	public void testOnlyProvenFreshTempleCacheCarriesTempleSource() throws Exception
	{
		String player = "Provider Probe";
		LocalClogCache cache = LocalClogCacheTest.ready();
		cache.cacheResult(new ClogResult(player, Collections.emptyMap(),
			Collections.emptyMap(), new HashMap<>(), null, null));

		ClogService freshService = serviceWithWarmCatalog(cache);
		longCache(freshService, "clogFetchTimes").put(player.toLowerCase(),
			System.currentTimeMillis());
		ClogResult provenFresh = freshService.lookup(player).join();
		assertNotNull(provenFresh);
		assertTrue(provenFresh.isFromTemple());

		// A repeat lookup inside the TTL rebuilds from the local cache, and its Temple figures come back with it.
		@SuppressWarnings("unchecked")
		Map<String, double[]> held = TestViews.field(freshService, "lookedUpTempleFigures");
		held.put(player.toLowerCase(), new double[]{812.5, 1204.0});
		ClogResult again = freshService.lookup(player).join();
		assertEquals(812.5, again.getTempleEhp(), 1e-9);
		assertEquals(1204.0, again.getTempleEhc(), 1e-9);
		assertEquals("and a rebuild without a lookup", 812.5, freshService.getCachedResult(player).getTempleEhp(), 1e-9);

		ClogService ambiguousService = serviceWithWarmCatalog(cache);
		longCache(ambiguousService, "templeFailures").put(player.toLowerCase(),
			System.currentTimeMillis());
		ClogResult coldFallback = ambiguousService.lookup(player).join();
		assertNotNull(coldFallback);
		assertFalse(coldFallback.isFromTemple());
		assertFalse(coldFallback.isFromRuneProfile());
		assertFalse(coldFallback.isFromKillclog());
	}

	@Test
	public void testActivePlayerTempleOverlayRetainsSourceDuringTtl() throws Exception
	{
		String player = "Overlay Probe";
		AtomicInteger requests = new AtomicInteger();
		String templeJson = "{\"data\":{\"player_name_with_capitalization\":\"Overlay Probe\","
			+ "\"last_changed\":\"2026-09-06 12:00:00\",\"items\":{\"zulrah\":["
			+ "{\"id\":1,\"count\":99,\"date\":\"2026-09-06 12:00:00\"},"
			+ "{\"id\":2,\"count\":1,\"date\":\"2026-09-06 12:00:00\"}]}}}";
		ClogService service = activeService(player, null, templeJson, requests);
		ClogResult localOnly = service.getCachedResult(player);
		assertTrue(localOnly.isFromLocal());
		assertFalse(localOnly.isFromTemple());
		assertEquals(0, requests.get());

		ClogResult first = service.lookup(player).join();
		assertTrue(first.isFromTemple());
		assertEquals("2026-09-06 12:00:00",
			first.getObtainedItems().get("zulrah").get(0).getDate());

		// The second lookup is TTL-served, but the displayed date still came
		// from the successful Temple overlay and must retain its provenance.
		ClogResult cached = service.lookup(player).join();
		assertTrue(cached.isFromTemple());
		assertEquals(1, requests.get());
		ClogResult quiet = service.getCachedResult(player);
		assertEquals("quiet refresh makes no request", 1, requests.get());
		for (ClogResult result : Arrays.asList(first, cached, quiet))
		{
			assertTrue(result.isFromLocal());
			assertFalse("local capture does not claim a web sync", result.isFromKillclog());
			List<ClogResult.ClogItem> items = result.getObtainedItems().get("zulrah");
			assertEquals("provider-only item is not imported", 1, items.size());
			assertEquals(1, items.get(0).getId());
			assertEquals("local quantity remains authoritative", 1, items.get(0).getCount());
			assertSelfSources(result, Arrays.asList("Local Collection Log", "TempleOSRS"));
		}
	}

	@Test
	public void testActivePlayerUsableTempleResponseIsMarkedWhenDateAlreadyPresent()
		throws Exception
	{
		String player = "Dated Probe";
		String date = "2026-09-06 12:00:00";
		String templeJson = "{\"data\":{\"last_changed\":\"" + date
			+ "\",\"items\":{\"zulrah\":[{\"id\":1,\"count\":1,\"date\":\""
			+ date + "\"}]}}}";

		ClogResult result = activeService(player, date, templeJson, new AtomicInteger())
			.lookup(player).join();

		assertTrue(result.isFromTemple());
	}

	@Test
	public void testActivePlayerUnsyncedTempleShapeStaysUnmarked() throws Exception
	{
		String player = "Unsynced Probe";
		ClogResult result = activeService(player, null,
			"{\"data\":{\"items\":{}}}", new AtomicInteger()).lookup(player).join();

		assertFalse(result.isFromTemple());
		assertFalse(result.isFromRuneProfile());
		assertFalse(result.isFromKillclog());
		assertTrue(result.isFromLocal());
		assertSelfSources(result, Collections.singletonList("Local Collection Log"));
	}

	@Test
	public void yourOwnCardGetsTemplesFiguresWithItsDatesUnderTheSameLimit() throws Exception
	{
		// 2.6.3: your own lookup reads your log from the game and asks Temple only for dates; your stats now ride
		// with them, so the Experience & Efficiency card shows your EHP and EHC as it does anyone else's.
		String player = "Self Probe";
		AtomicInteger clogRequests = new AtomicInteger();
		AtomicInteger statsRequests = new AtomicInteger();
		String templeJson = "{\"data\":{\"game_mode\":1,\"last_changed\":\"2026-09-06 12:00:00\",\"ehc\":3377.9,"
			+ "\"ehc_im\":3417.4,\"items\":{\"zulrah\":[{\"id\":1,\"count\":1,\"date\":\"2026-09-06 12:00:00\"}]}}}";
		ClogService service = activeService(player, null, templeJson,
			"{\"data\":{\"info\":{\"Game mode\":1},\"Overall_ehp\":3043.1}}", clogRequests, statsRequests);

		ClogResult first = service.lookup(player).join();
		assertEquals(3043.1, first.getTempleEhp(), 1e-9);
		assertEquals("an ironman's own rates", 3417.4, first.getTempleEhc(), 1e-9);
		ClogResult again = service.lookup(player).join();
		assertEquals(3043.1, again.getTempleEhp(), 1e-9);
		// A log rebuilt without a lookup (a local capture, the log opened) keeps them too.
		ClogResult refreshed = service.getCachedResult(player);
		assertEquals(3043.1, refreshed.getTempleEhp(), 1e-9);
		assertEquals(3417.4, refreshed.getTempleEhc(), 1e-9);
		assertEquals("one of each per limit", 1, clogRequests.get());
		assertEquals(1, statsRequests.get());

		ClogResult notOnTemple = activeService("Unsynced Self", null, "{\"data\":{\"items\":{}}}",
			"{\"data\":{\"Overall_ehp\":12.0}}", new AtomicInteger(), new AtomicInteger()).lookup("Unsynced Self").join();
		assertEquals("no Temple log, no Temple section", -1, notOnTemple.getTempleEhp(), 0);
		assertFalse(SkillsTooltip.onTemple(notOnTemple));
	}

	private static void assertSelfSources(ClogResult local, List<String> expected)
	{
		ClogResult combined = ClogProviderFanout.lookup(true,
			() -> CompletableFuture.completedFuture(local),
			() ->
			{
				throw new AssertionError("self must not fetch RuneProfile clog");
			},
			() ->
			{
				throw new AssertionError("self must not require a web sync");
			}).join();
		assertTrue(combined.isFromLocal());
		ClogSummaryTooltip tooltip = new ClogSummaryTooltip();
		tooltip.setClogSources(combined, false, false, false);
		assertEquals(expected, TestViews.sourceNames(tooltip));
	}

	private static ClogService serviceWithWarmCatalog(LocalClogCache cache) throws Exception
	{
		ClogService service = new ClogService(null, new Gson(), cache);
		return service;
	}

	private static ClogService activeService(String player, String localDate,
		String templeJson, AtomicInteger requests) throws Exception
	{
		return activeService(player, localDate, templeJson, "{\"data\":{\"info\":{}}}", requests, new AtomicInteger());
	}

	/** {@code requests} counts collection log requests, {@code statsRequests} stats ones; each URL gets its answer. */
	private static ClogService activeService(String player, String localDate, String templeJson, String statsJson,
		AtomicInteger requests, AtomicInteger statsRequests) throws Exception
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("zulrah", Collections.singletonList(
			new ClogResult.ClogItem(1, 1, localDate)));
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("zulrah", Arrays.asList(1, 2, 3));

		LocalClogCache cache = LocalClogCacheTest.ready();
		StoreLogin.activate(cache, player, 1L);
		cache.cacheFirstPartyResult(new ClogResult(player, obtained, categories,
			Collections.emptyMap(), localDate, null));

		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				boolean stats = chain.request().url().encodedPath().endsWith("player_stats.php");
				(stats ? statsRequests : requests).incrementAndGet();
				return new Response.Builder()
					.request(chain.request())
					.protocol(Protocol.HTTP_1_1)
					.code(200)
					.message("OK")
					.body(ResponseBody.create(MediaType.parse("application/json"), stats ? statsJson : templeJson))
					.build();
			})
			.build();
		ClogService service = new ClogService(client, new Gson(), cache);
		return service;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Long> longCache(ClogService service, String fieldName)
		throws Exception
	{
		Field field = ClogService.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Map<String, Long>) field.get(service);
	}
}
