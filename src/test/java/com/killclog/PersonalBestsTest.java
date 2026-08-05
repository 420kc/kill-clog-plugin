package com.killclog;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersonalBestsTest
{
	@Test
	public void formatMatchesVanillaPbRendering()
	{
		assertEquals("0:58", PersonalBests.formatSeconds(58.0));
		assertEquals("24:51", PersonalBests.formatSeconds(1491.0));
		// Precise timing keeps its fraction; whole seconds never grow one.
		assertEquals("3:52.80", PersonalBests.formatSeconds(232.8));
		assertEquals("1:02:05", PersonalBests.formatSeconds(3725.0));
	}

	@Test
	public void teamSuffixesCoverEveryVanillaSizeBucket()
	{
		// Vanilla's longBossName emits these exact size keys for CoX/CM;
		// missing one means a pb stored there shows absent or slower.
		List<String> suffixes = Arrays.asList(PersonalBests.TEAM_SUFFIXES);
		assertTrue(suffixes.contains(""));
		assertTrue(suffixes.contains(" solo"));
		for (int size = 1; size <= 10; size++)
		{
			assertTrue("missing size " + size,
				suffixes.contains(" " + size + " player" + (size == 1 ? "" : "s")));
		}
		assertTrue(suffixes.contains(" 11-15 players"));
		assertTrue(suffixes.contains(" 16-23 players"));
		assertTrue(suffixes.contains(" 24+ players"));
		// The adventure log emits open-ended sizes for old Nightmare records.
		for (int size = 2; size <= 10; size++)
		{
			assertTrue("missing size " + size + "+",
				suffixes.contains(" " + size + "+ players"));
		}
	}

	@Test
	public void variantSecondsKeepsTeamSizesSplitAndMergesSpellings()
	{
		java.util.Map<String, Double> store = new java.util.HashMap<>();
		store.put("chambers of xeric solo", 1800.0);
		store.put("chambers of xeric 3 players", 1500.0);
		// The alt spelling holds the faster time for the same variant: it
		// must merge into the canonical key, not appear as its own entry.
		store.put("the corrupted gauntlet", 460.0);
		store.put("corrupted gauntlet", 452.5);

		java.util.Map<String, Double> cox =
			PersonalBests.variantSeconds(store::get, "Chambers of Xeric");
		assertEquals(2, cox.size());
		assertEquals(1800.0, cox.get("chambers of xeric solo"), 0.001);
		assertEquals(1500.0, cox.get("chambers of xeric 3 players"), 0.001);
		// No collapsed entry: the split IS the point.
		assertFalse(cox.containsKey("chambers of xeric"));

		java.util.Map<String, Double> gauntlet =
			PersonalBests.variantSeconds(store::get, "The Corrupted Gauntlet");
		assertEquals(1, gauntlet.size());
		assertEquals(452.5, gauntlet.get("the corrupted gauntlet"), 0.001);
	}

	@Test
	public void keyCandidatesMatchVanillaStorageNames()
	{
		// Vanilla stores mode raids without the colon.
		assertArrayEquals(new String[]{"chambers of xeric challenge mode"},
			PersonalBests.keyCandidates("Chambers of Xeric: Challenge Mode"));
		assertArrayEquals(new String[]{"theatre of blood hard mode"},
			PersonalBests.keyCandidates("Theatre of Blood: Hard Mode"));
		// "The" bosses try both shapes; vanilla's names drop the article.
		assertArrayEquals(new String[]{"the corrupted gauntlet", "corrupted gauntlet"},
			PersonalBests.keyCandidates("The Corrupted Gauntlet"));
		assertArrayEquals(new String[]{"zulrah"},
			PersonalBests.keyCandidates("Zulrah"));
	}
}
