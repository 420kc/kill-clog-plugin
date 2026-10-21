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
	// The login a push is gathered in.
	private static final Session TESTER = new Session(3, "Tester", 42L);

	private static final Runnable NOTHING = () ->
	{
	};

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
		when(league.servesAccount(TESTER)).thenReturn(true);
		when(league.toFirstPartySyncResult("Tester")).thenReturn(new ClogResult("Tester",
			Map.of("boss", List.of(new ClogResult.ClogItem(1, 1, null))), Map.of("boss", List.of(1)), Map.of(), null, null));
		when(league.commitIfSessionCurrent(eq(TESTER), any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
		KillclogSyncGate gate = new KillclogSyncGate();
		int generation = gate.beginAttempt(false, null, null, -1);

		SyncService.SyncResult result = new SyncService(http, new Gson(), main)
			.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate, generation, league, "demonic-pacts", false,
				() -> assertEquals("it runs before the push goes", 0, sent.size())).get();

		assertTrue(result.message, result.ok);
		assertEquals(1, sent.size());
		assertTrue(sent.get(0).url().toString(), sent.get(0).url().encodedPath().endsWith("/player/Tester/sync/demonic-pacts"));
		verifyNoInteractions(main);
	}

	@Test
	public void aLogNeverReadOrEmptySaysWhichAndWhatToDo() throws Exception
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			throw new AssertionError("nothing to send");
		}).build();
		LocalClogCache main = mock(LocalClogCache.class);
		when(main.servesAccount(TESTER)).thenReturn(true);
		SyncService service = new SyncService(http, new Gson(), main);
		KillclogSyncGate gate = new KillclogSyncGate();

		SyncService.SyncResult unread = service.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate,
			gate.beginAttempt(false, null, null, -1), main, "main", false, NOTHING).get();
		assertEquals("Open your Collection Log to set up Kill Clog automatically.", unread.message);

		when(main.toFirstPartySyncResult("Tester")).thenReturn(new ClogResult("Tester", Map.of(), Map.of(), Map.of(),
			null, null));
		SyncService.SyncResult empty = service.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate,
			gate.beginAttempt(false, null, null, -1), main, "main", false, NOTHING).get();
		assertEquals("Your Collection Log is empty: Kill Clog syncs from your first item.", empty.message);
	}
}
