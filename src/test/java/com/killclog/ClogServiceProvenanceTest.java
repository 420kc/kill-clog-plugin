package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheResult(new ClogResult(player, Collections.emptyMap(),
			Collections.emptyMap(), new HashMap<>(), null, null));

		ClogService freshService = serviceWithWarmCatalog(cache);
		longCache(freshService, "clogFetchTimes").put(player.toLowerCase(),
			System.currentTimeMillis());
		ClogResult provenFresh = freshService.lookup(player).join();
		assertNotNull(provenFresh);
		assertTrue(provenFresh.isFromTemple());

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
			+ "{\"id\":1,\"count\":1,\"date\":\"2026-09-06 12:00:00\"}]}}}";
		ClogService service = activeService(player, null, templeJson, requests);

		ClogResult first = service.lookup(player).join();
		assertTrue(first.isFromTemple());
		assertEquals("2026-09-06 12:00:00",
			first.getObtainedItems().get("zulrah").get(0).getDate());

		// The second lookup is TTL-served, but the displayed date still came
		// from the successful Temple overlay and must retain its provenance.
		ClogResult cached = service.lookup(player).join();
		assertTrue(cached.isFromTemple());
		assertEquals(1, requests.get());
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
	}

	private static ClogService serviceWithWarmCatalog(LocalClogCache cache) throws Exception
	{
		ClogService service = new ClogService(null, new Gson(), cache);
		Field names = ClogService.class.getDeclaredField("cachedItemNames");
		names.setAccessible(true);
		names.set(service, Collections.emptyMap());
		return service;
	}

	private static ClogService activeService(String player, String localDate,
		String templeJson, AtomicInteger requests) throws Exception
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("zulrah", Collections.singletonList(
			new ClogResult.ClogItem(1, 1, localDate)));
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("zulrah", Arrays.asList(1, 2, 3));

		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		cache.cacheResult(new ClogResult(player, obtained, categories,
			Collections.emptyMap(), localDate, null));
		Field activePlayer = LocalClogCache.class.getDeclaredField("activePlayer");
		activePlayer.setAccessible(true);
		activePlayer.set(cache, player);

		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				requests.incrementAndGet();
				return new Response.Builder()
					.request(chain.request())
					.protocol(Protocol.HTTP_1_1)
					.code(200)
					.message("OK")
					.body(ResponseBody.create(MediaType.parse("application/json"), templeJson))
					.build();
			})
			.build();
		ClogService service = new ClogService(client, new Gson(), cache);
		Field names = ClogService.class.getDeclaredField("cachedItemNames");
		names.setAccessible(true);
		names.set(service, Collections.emptyMap());
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
