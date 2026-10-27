package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import static org.junit.Assert.assertFalse;
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
		KillclogSyncGate<Object> gate = new KillclogSyncGate<>((waiting, later) -> later);
		int generation = gate.begin(null, gate.era(), KillclogSyncGate.DELIBERATE);

		SyncService.SyncResult result = new SyncService(http, new Gson())
			.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate, generation, league, "demonic-pacts", -1, false,
				() -> assertEquals("it runs before the push goes", 0, sent.size())).get();

		assertTrue(result.message, result.ok);
		assertEquals(1, sent.size());
		assertTrue(sent.get(0).url().toString(), sent.get(0).url().encodedPath().endsWith("/player/Tester/sync/demonic-pacts"));
		verifyNoInteractions(main);
	}

	@Test
	public void aHardcoreWorldSyncSaysSoWithItsRun()
	{
		ClogResult clog = new ClogResult("Tester", Map.of("boss", List.of(new ClogResult.ClogItem(1, 1, null))),
			Map.of("boss", List.of(1)), Map.of(), null, null);
		SyncService service = new SyncService(new OkHttpClient(), new Gson());
		// Each push carries its own reading, taken as it gathered for its login (Sol 10/27): -1 off Hardcore Worlds.
		String main = service.requestBody(42L, null, clog, Map.of(), Map.of(), "main", -1);
		assertFalse(main.contains("\"world\""));
		assertEquals("a main-game push is sent as built", main, service.withOverallXp(main, -1));

		String unsettled = service.requestBody(42L, null, clog, Map.of(), Map.of(), "main", 0);
		assertTrue("the world says so before its XP is read", unsettled.contains("\"world\":\"hardcore\""));
		assertEquals("no XP until it's read", unsettled, service.withOverallXp(unsettled, 0));

		String built = service.requestBody(42L, null, clog, Map.of(), Map.of(), "main", 5_000L);
		assertFalse("the XP never makes a push new", built.contains("overall_xp"));
		assertEquals(5_000L, new Gson().fromJson(service.withOverallXp(built, 5_000L), JsonObject.class).get("overall_xp")
			.getAsLong());
		JsonObject first = new Gson().fromJson(built, JsonObject.class);
		assertEquals("hardcore", first.get("world").getAsString());
		assertTrue("the first run names no reset", first.get("run_started_at").isJsonNull());
		assertFalse("the first run names no reset XP", first.has("run_start_xp"));
		clog.setRunStartedAt("2026-11-04T12:00:00Z");
		clog.setRunStartXp(1_154);
		JsonObject reset = new Gson().fromJson(service.requestBody(42L, null, clog, Map.of(), Map.of(), "main", 5_000L),
			JsonObject.class);
		assertEquals("2026-11-04T12:00:00Z", reset.get("run_started_at").getAsString());
		assertEquals(1_154, reset.get("run_start_xp").getAsLong());
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
		SyncService service = new SyncService(http, new Gson());
		KillclogSyncGate<Object> gate = new KillclogSyncGate<>((waiting, later) -> later);

		SyncService.SyncResult unread = service.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate,
			gate.begin(null, gate.era(), KillclogSyncGate.DELIBERATE), main, "main", -1, false, NOTHING).get();
		assertEquals("Open your Collection Log to set up Kill Clog automatically.", unread.message);

		when(main.toFirstPartySyncResult("Tester")).thenReturn(new ClogResult("Tester", Map.of(), Map.of(), Map.of(),
			null, null));
		SyncService.SyncResult empty = service.syncCollectionLog(TESTER, null, Map.of(), Map.of(), gate,
			gate.begin(null, gate.era(), KillclogSyncGate.DELIBERATE), main, "main", -1, false, NOTHING).get();
		assertEquals("Your Collection Log is empty: Kill Clog syncs from your first item.", empty.message);
	}
}
