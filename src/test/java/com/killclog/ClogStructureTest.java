package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ClogStructureTest
{
	// What killclog.com serves for its seed, read from the game cache on 2026-10-05.
	private static final String SERVER_SEED_HASH = "6b5868202e703969";

	private static JsonObject seed()
	{
		return new Gson().fromJson(new InputStreamReader(
			ClogStructureTest.class.getResourceAsStream("clog-structure-seed.json"), StandardCharsets.UTF_8),
			JsonObject.class);
	}

	/** A ClogIndex holding exactly what a structure lists, as the client's own parse would. */
	private static ClogIndex indexOf(JsonObject structure)
	{
		Map<String, List<Integer>> items = new HashMap<>();
		Map<String, List<String>> tabs = new LinkedHashMap<>();
		Map<String, String> names = new HashMap<>();
		for (JsonElement tab : structure.getAsJsonArray("tabs"))
		{
			List<String> keys = new ArrayList<>();
			for (JsonElement element : tab.getAsJsonObject().getAsJsonArray("pages"))
			{
				JsonObject page = element.getAsJsonObject();
				String key = page.get("key").getAsString();
				keys.add(key);
				names.put(key, page.get("name").getAsString());
				List<Integer> ids = new ArrayList<>();
				page.getAsJsonArray("items").forEach(id -> ids.add(id.getAsInt()));
				items.put(key, ids);
			}
			tabs.put(tab.getAsJsonObject().get("name").getAsString(), keys);
		}
		ClogIndex index = new ClogIndex();
		index.publishForTest(items, new HashMap<>(), tabs, new HashMap<>(), names);
		return index;
	}

	@Test
	public void theClientsStructureHashesAsTheServerHashesTheSameOne()
	{
		JsonObject seed = seed();
		JsonObject built = ClogStructure.of(indexOf(seed));
		assertNotNull(built);
		assertEquals(seed.get("tabs"), built.get("tabs"));
		// Names with apostrophes (Kree'arra, Vet'ion) hash as the server writes them, unescaped.
		assertEquals(SERVER_SEED_HASH, ClogStructure.hash(new Gson(), built));
	}

	@Test
	public void beforeTheClientHasReadTheLogThereIsNothingToShare()
	{
		assertNull(ClogStructure.of(new ClogIndex()));
	}

	@Test
	public void theSyncSharesTheStructureOnlyWhenTheServerHoldsAnother()
	{
		JsonObject seed = seed();
		SyncService sync = new SyncService(null, new Gson(), null);
		sync.setClogIndex(indexOf(seed));
		// Before any reply names the server's structure, nothing goes.
		assertNull(sync.structureToShare());
		sync.outcome(reply("{\"clog_structure_hash\":\"" + SERVER_SEED_HASH + "\"}"), "Me", 1, 0);
		assertNull(sync.structureToShare());
		sync.outcome(reply("{\"clog_structure_hash\":\"0000000000000000\"}"), "Me", 1, 0);
		JsonObject shared = sync.structureToShare();
		assertNotNull(shared);
		assertEquals(seed.get("tabs"), shared.get("tabs"));
		// A reply without the field (an older server) leaves what it knew.
		sync.outcome(reply("{}"), "Me", 1, 0);
		assertNotNull(sync.structureToShare());
	}

	@Test
	public void aLookupIsMeasuredAgainstTheGamesOwnPagesOnceTheClientHasReadThem()
		throws ReflectiveOperationException
	{
		ClogService provider = new ClogService(null, new Gson(), null);
		Field catalog = ClogService.class.getDeclaredField("cachedCategories");
		catalog.setAccessible(true);
		Map<String, List<Integer>> temple = new HashMap<>();
		temple.put("master_treasure_trails", Arrays.asList(101, 102, 103, 104));
		temple.put("chaos_druids", Arrays.asList(301, 302));
		catalog.set(provider, temple);
		KillclogService service = new KillclogService(null, new Gson(), provider);
		String json = "{\"rsn\":\"420 kc\",\"clog\":{\"items_by_category\":{"
			+ "\"master_treasure_trails\":[{\"item_id\":101,\"quantity\":1}]}}}";

		// Before the client has read the log, the provider catalog stands in.
		assertEquals(4, service.parseProofView("420 kc", json, "420 kc")
			.getCategoryItems().get("master_treasure_trails").size());

		JsonObject game = new JsonObject();
		JsonArray tabs = new JsonArray();
		game.add("tabs", tabs);
		JsonObject tab = new JsonObject();
		tab.addProperty("name", "Clues");
		JsonArray pages = new JsonArray();
		pages.add(page("Master Treasure Trails", "master_treasure_trails", 101, 102, 103));
		pages.add(page("Elder Chaos Druids", "elder_chaos_druids", 301, 302));
		tab.add("pages", pages);
		tabs.add(tab);
		service.setClogIndex(indexOf(game));
		ClogResult result = service.parseProofView("420 kc", json, "420 kc");
		assertEquals(Arrays.asList(101, 102, 103), result.getCategoryItems().get("master_treasure_trails"));
		// The game's own pages, not the provider's: Elder Chaos Druids, never chaos_druids.
		assertEquals(2, result.getCategoryItems().get("elder_chaos_druids").size());
		assertNull(result.getCategoryItems().get("chaos_druids"));
	}

	private static JsonObject page(String name, String key, int... ids)
	{
		JsonObject page = new JsonObject();
		page.addProperty("name", name);
		page.addProperty("key", key);
		JsonArray items = new JsonArray();
		for (int id : ids)
		{
			items.add(id);
		}
		page.add("items", items);
		return page;
	}

	private static HttpUtil.HttpResult reply(String body)
	{
		return new HttpUtil.HttpResult(200, body);
	}
}
