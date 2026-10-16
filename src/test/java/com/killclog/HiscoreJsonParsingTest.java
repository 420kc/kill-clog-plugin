package com.killclog;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Name-keyed JSON hiscore parsing. The fixture is the real Jagex response
 * captured on Wyrmscraig launch day (2026-07-29), which bakes in the exact
 * conditions that once blanked positional parsing: Mad Angel newly inserted,
 * the Mimic/Maggot King reorder, and the Calvar'ion respelling.
 */
public class HiscoreJsonParsingTest
{
	private HiscoreService service;
	private String fixture;

	@Before
	public void setUp()
	{
		service = new HiscoreService(null, new Gson());
		fixture = readResource("hiscore-fixture-wyrmscraig.json");
	}

	private String readResource(String name)
	{
		InputStream in = getClass().getResourceAsStream(name);
		assertNotNull("missing test resource " + name, in);
		try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A"))
		{
			return s.next();
		}
	}

	@Test
	public void testDayOneBossParsesWithoutAnyListUpdate()
	{
		// The property the whole change exists for: a boss Jagex added this
		// morning parses correctly with no positional bookkeeping.
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		assertEquals(0, result.getKc("Mad Angel"));
	}

	@Test
	public void testReorderedNeighborsKeyCorrectly()
	{
		// Mimic and Maggot King swapped feed positions in this update, beside the
		// newly inserted Mad Angel; by name the neighbours cannot cross.
		String[] names = {"Lunar Chests", "Mad Angel", "Maggot King", "Mimic", "Nex"};
		com.google.gson.JsonObject root = new Gson().fromJson(fixture, com.google.gson.JsonObject.class);
		for (com.google.gson.JsonElement e : root.getAsJsonArray("activities"))
		{
			com.google.gson.JsonObject row = e.getAsJsonObject();
			int i = java.util.Arrays.asList(names).indexOf(row.get("name").getAsString());
			if (i >= 0)
			{
				row.addProperty("score", 10 * (i + 1));
				row.addProperty("rank", 1000 + i);
			}
		}
		HiscoreResult result = service.parseHiscoreBody(new Gson().toJson(root), AccountType.REGULAR);
		assertEquals(10, result.getKc("Lunar Chests"));
		assertEquals(20, result.getKc("Mad Angel"));
		assertEquals(30, result.getKc("Maggot King"));
		assertEquals(40, result.getKc("Mimic"));
		assertEquals(50, result.getKc("Nex"));
		assertEquals(1001, result.getRank("Mad Angel"));
		assertEquals(1002, result.getRank("Maggot King"));
		assertEquals(1003, result.getRank("Mimic"));
	}

	@Test
	public void testJagexRespellingMapsToInternalKey()
	{
		// The feed says Calvar'ion now; internal key is still Cal'varion.
		// The boundary override keeps every downstream surface keyed the same:
		// present under the internal key, absent under Jagex's new spelling.
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		assertTrue(result.getBossKills().containsKey("Cal'varion"));
		assertFalse(result.getBossKills().containsKey("Calvar'ion"));
	}

	@Test
	public void testOverallAndSkillsParse()
	{
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		assertEquals(2278, result.getTotalLevel());
		assertEquals(4600000000L, result.getTotalXp());
		assertEquals(99, result.getSkillLevel("attack"));
		assertEquals(126, result.getCombatLevel());
	}

	@Test
	public void testActivitiesSplitFromBosses()
	{
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		// A known activity lands in the activity maps, not the boss maps.
		assertTrue(result.getActivityScore("Clue Scrolls (all)") >= -1);
		assertFalse(result.getBossKills().containsKey("Clue Scrolls (all)"));
	}

	@Test
	public void testUnknownFutureBossIsHeldQuietly()
	{
		String body = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
			+ "\"activities\":[{\"id\":999,\"name\":\"Boss That Does Not Exist Yet\",\"rank\":50,\"score\":420}]}";
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);
		assertEquals(-1, result.getKc("Boss That Does Not Exist Yet"));
		assertTrue(result.getBossKills().isEmpty());
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testUnknownOrPartialRowIsSkippedNotFatal()
	{
		// A row Kill Clog doesn't know, or one missing its name, rank or score, says nothing about its
		// neighbours: it is skipped and the rest of the answer stands.
		String body = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
			+ "\"activities\":["
			+ "{\"id\":998,\"name\":\"Boss That Does Not Exist Yet\"},"
			+ "{\"id\":999,\"name\":\"Another New Boss\",\"rank\":null,\"score\":\"n/a\"},"
			+ "{\"id\":1,\"rank\":5,\"score\":9},"
			+ "{\"id\":2,\"name\":\"Zulrah\",\"score\":77},"
			+ "{\"id\":3,\"name\":\"Clue Scrolls (all)\",\"rank\":12},"
			+ "\"not a row\","
			+ "{\"id\":4,\"name\":\"Vorkath\",\"rank\":40,\"score\":300},"
			+ "{\"id\":5,\"name\":\"LMS - Rank\",\"rank\":8,\"score\":600}]}";
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);
		assertEquals(2277, result.getTotalLevel());
		assertEquals(300, result.getKc("Vorkath"));
		assertEquals(40, result.getRank("Vorkath"));
		assertEquals(600, result.getActivityScore("LMS - Rank"));
		assertEquals(8, result.getActivityRank("LMS - Rank"));
		// A known row missing a number is left out whole, never half-written.
		assertFalse(result.hasBossRow("Zulrah"));
		assertEquals(-1, result.getRank("Zulrah"));
		assertEquals(-1, result.getActivityScore("Clue Scrolls (all)"));
		assertEquals(-1, result.getActivityRank("Clue Scrolls (all)"));
		assertEquals(1, result.getBossKills().size());
	}

	@Test
	public void theFracturedArchiveRowIsKeptBeforeTheLayoutListsIt()
	{
		// Jagex adds the raid's row on launch day; a release whose layout doesn't list it yet keeps it under the
		// raid's own name, by the guess or the name without "The", ranked or not.
		for (String name : new String[]{PanelData.TFA_HISCORE, "Fractured Archive", "the fractured archive"})
		{
			String body = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
				+ "\"activities\":[{\"id\":999,\"name\":\"" + name + "\",\"rank\":50,\"score\":12}]}";
			assertEquals(name, 12, service.parseHiscoreBody(body, AccountType.REGULAR).getKc(PanelData.TFA_HISCORE));
		}
		String unranked = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
			+ "\"activities\":[{\"id\":999,\"name\":\"" + PanelData.TFA_HISCORE + "\",\"rank\":-1,\"score\":-1}]}";
		assertTrue(service.parseHiscoreBody(unranked, AccountType.REGULAR).hasBossRow(PanelData.TFA_HISCORE));
		assertFalse(service.parseHiscoreBody(fixture, AccountType.REGULAR).hasBossRow(PanelData.TFA_HISCORE));

		// A mode or boss of its own that also names the archive never stands in for the raid's completions.
		String modes = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
			+ "\"activities\":[{\"id\":999,\"name\":\"The Fractured Archive\",\"rank\":50,\"score\":50},"
			+ "{\"id\":1000,\"name\":\"The Fractured Archive: Challenge Mode\",\"rank\":9,\"score\":3}]}";
		assertEquals(50, service.parseHiscoreBody(modes, AccountType.REGULAR).getKc(PanelData.TFA_HISCORE));
		String modeAlone = "{\"skills\":[{\"id\":0,\"name\":\"Overall\",\"rank\":1,\"level\":2277,\"xp\":4600000000}],"
			+ "\"activities\":[{\"id\":1000,\"name\":\"The Fractured Archive: Challenge Mode\",\"rank\":9,\"score\":3}]}";
		assertFalse(service.parseHiscoreBody(modeAlone, AccountType.REGULAR).hasBossRow(PanelData.TFA_HISCORE));
	}

	@Test
	public void testExtractTotalXpReadsJsonBodies()
	{
		// Account-type detection compares total xp across the four endpoint
		// variants: a reader that returns -1 for all four would misidentify
		// every ironman as a regular account.
		assertEquals(4_600_000_000L, service.extractTotalXp(fixture));
	}

	@Test
	public void testRowOrderIsIrrelevant()
	{
		// Shuffle a boss row to the front of the activities array: the parse
		// must be identical because nothing is positional anymore.
		Gson gson = new Gson();
		com.google.gson.JsonObject root = gson.fromJson(fixture, com.google.gson.JsonObject.class);
		com.google.gson.JsonArray acts = root.getAsJsonArray("activities");
		com.google.gson.JsonArray reversed = new com.google.gson.JsonArray();
		for (int i = acts.size() - 1; i >= 0; i--)
		{
			reversed.add(acts.get(i));
		}
		root.add("activities", reversed);

		HiscoreResult straight = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		HiscoreResult shuffled = service.parseHiscoreBody(gson.toJson(root), AccountType.REGULAR);
		assertEquals(straight.getBossKills(), shuffled.getBossKills());
		assertEquals(straight.getKc("Zulrah"), shuffled.getKc("Zulrah"));
	}
}
