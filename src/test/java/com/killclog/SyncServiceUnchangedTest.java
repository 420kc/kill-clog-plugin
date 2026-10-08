package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every loading screen schedules an automatic push. One that would send exactly what killclog.com last took stays
 * home for a day; anything new in it, a click, or a character publish sends. No network.
 */
public class SyncServiceUnchangedTest
{
	private final List<Request> sent = new ArrayList<>();
	private int code = 200;
	private String reply = "{}";
	private LocalClogCache cache;
	private SyncService service;
	private ClogResult log;
	private int sending;

	@Before
	@SuppressWarnings("unchecked")
	public void setUp()
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
				.body(ResponseBody.create(MediaType.get("application/json"), reply)).build();
		}).build();
		cache = mock(LocalClogCache.class);
		when(cache.servesAccount(anyString(), anyLong(), anyLong())).thenReturn(true);
		when(cache.toFirstPartySyncResult(anyString())).thenAnswer(call -> log);
		when(cache.commitIfSessionCurrent(anyLong(), any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
		service = new SyncService(http, new Gson(), cache);
		log = clog(1);
	}

	private static ClogResult clog(int quantity)
	{
		return new ClogResult("Tester", Map.of("boss", List.of(new ClogResult.ClogItem(1, quantity, null))),
			Map.of("boss", List.of(1)), Map.of(), null, null);
	}

	private SyncService.SyncResult push(Map<String, Double> pbs, AccountType type, String mode, boolean always)
		throws Exception
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int generation = gate.beginAttempt();
		SyncService.SyncResult result = service.syncCollectionLog("Tester", 42L, type, pbs, Map.of(), 3L, gate,
			generation, cache, mode, always, () -> sending++).get();
		gate.complete(generation);
		return result;
	}

	private SyncService.SyncResult push() throws Exception
	{
		return push(Map.of(), null, "main", false);
	}

	@Test
	public void anUnchangedAutomaticPushStaysHomeWithoutAWord() throws Exception
	{
		SyncService.SyncResult first = push();
		SyncService.SyncResult second = push();

		assertEquals(1, sent.size());
		// Only the push that went said "syncing...", and the one that stayed home has nothing to flash.
		assertEquals(1, sending);
		assertTrue(first.ok);
		assertTrue(second.ok);
		assertSame(SyncService.UNCHANGED, second);
	}

	@Test
	public void anythingNewInThePushSendsIt() throws Exception
	{
		push();
		log = clog(2);
		push();
		push(Map.of("Zulrah", 60.0), null, "main", false);
		push(Map.of("Zulrah", 60.0), AccountType.IRONMAN, "main", false);
		push(Map.of("Zulrah", 60.0), AccountType.IRONMAN, "demonic-pacts", false);

		assertEquals(5, sent.size());
	}

	@Test
	public void aClickOrACharacterPublishAlwaysSends() throws Exception
	{
		push();
		push(Map.of(), null, "main", true);

		assertEquals(2, sent.size());
	}

	@Test
	public void aFailureOrADryRunIsNeverRemembered() throws Exception
	{
		code = 500;
		push();
		code = 200;
		reply = "{\"dry_run\":true}";
		push();
		reply = "{}";
		push();
		push();

		assertEquals(3, sent.size());
	}

	@Test
	public void anUnchangedPushStillGoesOnceADay() throws Exception
	{
		push();
		Field at = SyncService.class.getDeclaredField("acceptedAt");
		at.setAccessible(true);
		at.setLong(service, System.currentTimeMillis() - SyncService.RESEND_UNCHANGED_MS - 1);
		push();

		assertEquals(2, sent.size());
	}
}
