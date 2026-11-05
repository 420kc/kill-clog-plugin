package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HighGamblesTest
{
	@Test
	public void theSyncCarriesTheCountInItsScoreMapOnceRead() throws Exception
	{
		ClogResult log = new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null);
		assertFalse("unread: nothing is sent, so killclog.com keeps what it holds", body(log).has("activities"));
		log.setHighGambles(0);
		assertEquals("a counted zero is sent, as the game's page shows it",
			0, body(log).getAsJsonObject("activities").get(HighGambles.ACTIVITY).getAsInt());
		log.setHighGambles(57);
		assertEquals(57, body(log).getAsJsonObject("activities").get(HighGambles.ACTIVITY).getAsInt());
	}

	@Test
	public void aLookupReadsTheCountTheirSyncSent()
	{
		KillclogService service = new KillclogService(null, new Gson(), null);
		assertEquals(57, service.parseProofView("8lD", "{\"clog\":{\"items_by_category\":{}},\"high_gambles\":57}", "8ld")
			.getHighGambles());
		assertEquals(0, service.parseProofView("x", "{\"clog\":{\"items_by_category\":{}},\"high_gambles\":0}", "x")
			.getHighGambles());
		assertEquals("never sent", -1, service.parseProofView("x", "{\"clog\":{\"items_by_category\":{}}}", "x")
			.getHighGambles());
		assertEquals("past any real count", -1, service.parseProofView("x",
			"{\"clog\":{\"items_by_category\":{}},\"high_gambles\":10000001}", "x").getHighGambles());
	}

	@Test
	public void theCountSurvivesACombineWhicheverLogWins()
	{
		ClogResult synced = new ClogResult("8lD", Map.of(), Map.of(), Map.of(), null, null);
		synced.setHighGambles(57);
		ClogResult provider = new ClogResult("8lD", Map.of("hats", List.of(new ClogResult.ClogItem(2991, 1, null))),
			Map.of(), Map.of(), null, null);
		assertEquals("the provider's fuller log wins the grid and keeps the sync's count",
			57, ClogResult.pickFullest(provider, synced).getHighGambles());
		assertEquals(57, ClogResult.pickFullest(synced, provider).getHighGambles());
		assertEquals(57, ClogResult.pickFullerProvider(provider, synced).getHighGambles());
	}

	private static JsonObject body(ClogResult log) throws Exception
	{
		Method build = SyncService.class.getDeclaredMethod("buildBody", long.class, AccountType.class,
			ClogResult.class, Map.class, Map.class, long.class);
		build.setAccessible(true);
		return (JsonObject) build.invoke(new SyncService(null, new Gson()), 1L, null, log, Map.of(), Map.of(), -1L);
	}
}
