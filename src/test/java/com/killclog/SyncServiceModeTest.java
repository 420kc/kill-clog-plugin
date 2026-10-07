package com.killclog;

import com.google.gson.Gson;
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
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A mode's sync reads, fences and posts through that mode's own store and path. No network. */
public class SyncServiceModeTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void aLeagueSyncUsesOnlyTheLeagueStoreAndPath() throws Exception
	{
		List<Request> sent = new ArrayList<>();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
				.body(ResponseBody.create(MediaType.get("application/json"), "{}")).build();
		}).build();
		LocalClogCache main = mock(LocalClogCache.class);
		LocalClogCache league = mock(LocalClogCache.class);
		when(league.servesAccount("Tester", 42L, 3L)).thenReturn(true);
		when(league.toFirstPartySyncResult("Tester")).thenReturn(new ClogResult("Tester",
			Map.of("boss", List.of(new ClogResult.ClogItem(1, 1, null))), Map.of("boss", List.of(1)), Map.of(), null, null));
		when(league.commitIfSessionCurrent(eq(3L), any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
		KillclogSyncGate gate = new KillclogSyncGate();
		int generation = gate.beginAttempt();

		SyncService.SyncResult result = new SyncService(http, new Gson(), main)
			.syncCollectionLog("Tester", 42L, null, Map.of(), Map.of(), 3L, gate, generation, league, "demonic-pacts", false).get();

		assertTrue(result.message, result.ok);
		assertEquals(1, sent.size());
		assertTrue(sent.get(0).url().toString(), sent.get(0).url().encodedPath().endsWith("/player/Tester/sync/demonic-pacts"));
		verifyNoInteractions(main);
	}
}
