package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogUnlockHistoryTest
{
	private final String date = ClogDates.local(Instant.now().minusSeconds(120).toString());

	@Test
	public void ambiguousHatResolvesOnceAndSurvivesSerialization()
	{
		PlayerClogData data = new PlayerClogData();
		data.pendingUnlocks = List.of(new PendingClogUnlock(List.of(2991, 2992), date, "owner"));
		data = new Gson().fromJson(new Gson().toJson(data), PlayerClogData.class);
		Map<String, List<ClogResult.ClogItem>> obtained = items(2991);
		obtained.put("second_category", new ArrayList<>(obtained.get("hats")));
		assertTrue(PendingClogUnlock.reconcile(data.pendingUnlocks, obtained, "owner").isEmpty());
		assertEquals(date, obtained.get("hats").get(0).getDate());
		assertEquals(date, obtained.get("second_category").get(0).getDate());
		ClogResult result = new ClogResult("Tester", obtained, Map.of(), Map.of(), null, null);
		assertEquals(1, LookupQueries.getRecentItems(result, 5).size());
		assertEquals(2991, LookupQueries.getRecentItems(result, 5).get(0).getId());
	}

	@Test
	public void multipleCandidatesAndHistoricalImportsNeverInventDates()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = items(2991, 2992);
		PendingClogUnlock.reconcile(List.of(new PendingClogUnlock(List.of(2991, 2992), date, "owner")), obtained, "owner");
		for (ClogResult.ClogItem item : obtained.get("hats")) assertNull(item.getDate());
		PendingClogUnlock.reconcile(null, obtained, "owner");
		assertNull(obtained.get("hats").get(0).getDate());
	}

	@Test
	public void unmatchedEvidenceWaitsAcrossDaysButInvalidDatesCannotReachRecent()
	{
		PendingClogUnlock event = new PendingClogUnlock(List.of(2991, 2992), date, "owner");
		assertEquals(1, PendingClogUnlock.reconcile(List.of(event), items(2978), "owner").size());
		Map<String, List<ClogResult.ClogItem>> obtained = items(2991);
		PendingClogUnlock older = new PendingClogUnlock(List.of(2991, 2992),
			ClogDates.local(Instant.now().minusSeconds(172800).toString()), "owner");
		assertTrue(PendingClogUnlock.reconcile(List.of(older), obtained, "owner").isEmpty());
		assertEquals(older.date, obtained.get("hats").get(0).getDate());
		Map<String, List<ClogResult.ClogItem>> invalid = items(2991);
		PendingClogUnlock.reconcile(List.of(new PendingClogUnlock(List.of(2991, 2992), "bad", "owner")), invalid, "owner");
		assertNull(invalid.get("hats").get(0).getDate());
	}

	@Test
	public void payloadAndProofViewRoundTripAcquisitionDatesOnly() throws Exception
	{
		SyncService service = new SyncService(null, new Gson(), null);
		Method build = SyncService.class.getDeclaredMethod("buildBody", long.class, AccountType.class,
			ClogResult.class, Map.class, Map.class);
		build.setAccessible(true);
		Map<String, List<ClogResult.ClogItem>> obtained = items(2991, 2992);
		obtained.get("hats").set(0, new ClogResult.ClogItem(2991, 1, date));
		ClogResult clog = new ClogResult("Tester", obtained, Map.of(), Map.of(), null, null);
		JsonObject body = (JsonObject) build.invoke(service, 123L, null, clog, Map.of(), Map.of());
		JsonArray payload = body.getAsJsonArray("clog");
		assertEquals(ClogDates.iso(date), payload.get(0).getAsJsonObject().get("obtained_at").getAsString());
		assertFalse(payload.get(1).getAsJsonObject().has("obtained_at"));
		JsonObject categories = new JsonObject();
		categories.add("hats", payload);
		JsonObject wrapper = new JsonObject();
		wrapper.add("items_by_category", categories);
		JsonObject proof = new JsonObject();
		proof.add("clog", wrapper);
		ClogResult parsed = new KillclogService(null, new Gson(), null).parseProofView("Tester", proof.toString(), "tester");
		assertEquals(date, parsed.getObtainedItems().get("hats").get(0).getDate());
		assertNull(parsed.getObtainedItems().get("hats").get(1).getDate());
	}

	@Test
	public void malformedAndFutureDatesAreIgnored()
	{
		assertNull(ClogDates.iso("2026-02-30 12:00:00"));
		assertNull(ClogDates.iso("<html>untrusted"));
		assertNull(ClogDates.iso(Instant.now().plusSeconds(3600).toString()));
	}

	private static Map<String, List<ClogResult.ClogItem>> items(int... ids)
	{
		List<ClogResult.ClogItem> items = new ArrayList<>();
		for (int id : ids) items.add(new ClogResult.ClogItem(id, 1, null));
		Map<String, List<ClogResult.ClogItem>> result = new HashMap<>();
		result.put("hats", items);
		return result;
	}
}
