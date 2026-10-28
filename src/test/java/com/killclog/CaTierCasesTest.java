package com.killclog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Runs cases/ca-tier.tsv, the one copy of the answers for a player's Combat Achievement tier. killclog-api and
 * killclog.com run the same rows, so none of the three can name a different tier for the same counts.
 */
public class CaTierCasesTest
{
	@Test
	public void everySharedCaseHolds() throws Exception
	{
		List<String[]> rows = rows();
		assertFalse("the shared cases are present", rows.isEmpty());
		for (String[] row : rows)
		{
			CombatAchievementTier tier = CombatAchievementResult.of(counts(row[1]), row[2].equals("-") ? null
				: counts(row[2])).getTier();
			assertEquals(row[0], row[3], tier == null ? "none" : tier.name().toLowerCase(Locale.ROOT));
		}
	}

	/** "41,60,0,0,0,0": each tier's count, Easy to Grandmaster. */
	private static Map<CombatAchievementTier, Integer> counts(String cell)
	{
		String[] values = cell.split(",");
		Map<CombatAchievementTier, Integer> counts = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			counts.put(tier, Integer.parseInt(values[tier.ordinal()]));
		}
		return counts;
	}

	private static List<String[]> rows() throws Exception
	{
		List<String[]> rows = new ArrayList<>();
		try (InputStream in = CaTierCasesTest.class.getResourceAsStream("/com/killclog/cases/ca-tier.tsv"))
		{
			assertNotNull("cases/ca-tier.tsv is on the test classpath", in);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] row = line.split("\t", -1);
				assertEquals("four columns: " + line, 4, row.length);
				rows.add(row);
			}
		}
		return rows;
	}
}
