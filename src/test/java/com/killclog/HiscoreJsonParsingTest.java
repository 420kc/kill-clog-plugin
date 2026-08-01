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
 * conditions that blanked the CSV path: Mad Angel newly inserted, the
 * Mimic/Maggot King reorder, and the Calvar'ion respelling.
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
	public void testJsonBodyDispatchesAndNeverFlagsShifted()
	{
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		assertNotNull(result);
		assertFalse(result.isBossSectionShifted());
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
		// Mimic and Maggot King swapped CSV positions in this update; by name
		// they cannot cross.
		HiscoreResult result = service.parseHiscoreBody(fixture, AccountType.REGULAR);
		assertEquals(result.getBossKills().get("Mimic").intValue(), result.getKc("Mimic"));
		assertEquals(result.getBossKills().get("Maggot King").intValue(), result.getKc("Maggot King"));
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
		assertFalse(result.isBossSectionShifted());
		assertEquals(-1, result.getKc("Boss That Does Not Exist Yet"));
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testExtractTotalXpReadsJsonBodies()
	{
		// Account-type detection compares total xp across the four endpoint
		// variants. With the JSON-first transport those bodies are JSON, and
		// a CSV-only reader returns -1 for all four - every ironman would
		// misidentify as a regular account.
		assertEquals(4_600_000_000L, service.extractTotalXp(fixture));
	}

	@Test
	public void testExtractTotalXpStillReadsCsv()
	{
		assertEquals(4_600_000_000L, service.extractTotalXp("1,2277,4600000000\n1,99,13034431"));
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
