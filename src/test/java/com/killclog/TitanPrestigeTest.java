package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The Titan prestige tier: 200m in every skill the game has. The fixture is
 * the real hiscore record of Fast 07, the first account to reach 4.8b,
 * captured 2026-07-31 - rank 1, 2376, every skill at exactly 200m, Zuk kc 60.
 */
public class TitanPrestigeTest
{
	private HiscoreResult fromFixture()
	{
		InputStream in = getClass().getResourceAsStream("hiscore-fixture-titan.json");
		assertNotNull("missing titan fixture", in);
		JsonObject root = new Gson().fromJson(
			new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);

		Map<String, Integer> skillLevels = new LinkedHashMap<>();
		Map<String, Long> skillXps = new LinkedHashMap<>();
		int totalLevel = -1;
		long totalXp = -1;
		for (JsonElement e : root.getAsJsonArray("skills"))
		{
			JsonObject s = e.getAsJsonObject();
			String name = s.get("name").getAsString();
			if ("Overall".equals(name))
			{
				totalLevel = s.get("level").getAsInt();
				totalXp = s.get("xp").getAsLong();
				continue;
			}
			skillLevels.put(name.toLowerCase(), s.get("level").getAsInt());
			skillXps.put(name.toLowerCase(), s.get("xp").getAsLong());
		}

		Map<String, Integer> bossKills = new LinkedHashMap<>();
		JsonArray acts = root.getAsJsonArray("activities");
		for (JsonElement e : acts)
		{
			JsonObject a = e.getAsJsonObject();
			bossKills.put(a.get("name").getAsString(), a.get("score").getAsInt());
		}

		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			bossKills, Map.of(), Map.of(), Map.of(),
			skillLevels, Map.of(), skillXps,
			totalLevel, totalXp, 126, 1);
	}

	@Test
	public void firstTitanIsTitanInfernal()
	{
		// Fast 07: all 24 at 200m, Zuk kc 60.
		assertEquals("Titan Infernal", LookupQueries.getPrestige(fromFixture()));
	}

	@Test
	public void oneXpShortIsNotTitan()
	{
		HiscoreResult full = fromFixture();
		Map<String, Long> xps = new LinkedHashMap<>();
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Skill s : Skill.values())
		{
			String key = s.getName().toLowerCase();
			xps.put(key, full.getSkillXp(key));
			levels.put(key, full.getSkillLevel(key));
		}
		xps.put("sailing", 199_999_999L);

		HiscoreResult almost = new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Map.of("TzKal-Zuk", 60), Map.of(), Map.of(), Map.of(),
			levels, Map.of(), xps,
			2376, 4_799_999_999L, 126, 2);
		assertEquals("Maxed Infernal", LookupQueries.getPrestige(almost));
	}

	@Test
	public void titanWithoutZukStandsAlone()
	{
		HiscoreResult full = fromFixture();
		Map<String, Long> xps = new LinkedHashMap<>();
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Skill s : Skill.values())
		{
			String key = s.getName().toLowerCase();
			xps.put(key, full.getSkillXp(key));
			levels.put(key, full.getSkillLevel(key));
		}
		HiscoreResult peaceful = new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Map.of("TzKal-Zuk", 0), Map.of(), Map.of(), Map.of(),
			levels, Map.of(), xps,
			2376, 4_800_000_000L, 126, 3);
		assertEquals("Titan", LookupQueries.getPrestige(peaceful));
	}

	@Test
	public void missingSkillXpIsNotTitan()
	{
		// A result with no skill data at all can never be Titan.
		HiscoreResult empty = new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Map.of(), Map.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(),
			2376, 0L, 126, 4);
		assertEquals("Maxed", LookupQueries.getPrestige(empty));
	}
}
