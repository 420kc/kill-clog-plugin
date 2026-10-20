package com.killclog;

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
	public void everyTeamSizeShapeVanillaAndTheAdventureLogWriteIsRead()
	{
		java.util.Map<String, Double> store = new java.util.HashMap<>();
		String[] sizes = {"", " solo", " 1 player", " 2 players", " 10 players", " 11-15 players", " 16-23 players",
			" 24+ players", " 6+ players"};
		for (String size : sizes)
		{
			store.put("chambers of xeric" + size, 1000.0);
		}
		assertEquals(sizes.length, PersonalBests.variantSeconds(store, "Chambers of Xeric").size());
		assertEquals("the plain key first, so a team size names a tie", "chambers of xeric",
			PersonalBests.variantSeconds(store, "Chambers of Xeric").keySet().iterator().next());
	}

	@Test
	public void theFracturedArchiveReadsTheKeyVanillaFilesItUnder()
	{
		// Vanilla's chat commands (99b3c90a) file the raid's time as "fractured archive", no "The", no team size.
		java.util.Map<String, Double> store = java.util.Map.of("fractured archive", 145.8);
		assertEquals(java.util.Map.of("the fractured archive", 145.8),
			PersonalBests.variantSeconds(store, PanelData.TFA_HISCORE));
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
			PersonalBests.variantSeconds(store, "Chambers of Xeric");
		assertEquals(2, cox.size());
		assertEquals(1800.0, cox.get("chambers of xeric solo"), 0.001);
		assertEquals(1500.0, cox.get("chambers of xeric 3 players"), 0.001);
		// No collapsed entry: the split IS the point.
		assertFalse(cox.containsKey("chambers of xeric"));

		java.util.Map<String, Double> gauntlet =
			PersonalBests.variantSeconds(store, "The Corrupted Gauntlet");
		assertEquals(1, gauntlet.size());
		assertEquals(452.5, gauntlet.get("the corrupted gauntlet"), 0.001);
	}

	@Test
	public void everyLanesSpellingReachesOneName()
	{
		assertEquals("chambers of xeric challenge mode", PersonalBests.encounter("Chambers of Xeric: Challenge Mode"));
		assertEquals("theatre of blood hard mode", PersonalBests.encounter("theatre of blood hard mode"));
		assertEquals("corrupted gauntlet", PersonalBests.encounter("The Corrupted Gauntlet"));
		assertEquals("lunar chest", PersonalBests.encounter("Lunar Chests"));
		assertEquals("tztok-jad", PersonalBests.encounter("TzHaar Fight Cave"));
		// The sync's name never moves: the server files every time under it.
		assertEquals("the corrupted gauntlet", PersonalBests.syncBase("The Corrupted Gauntlet"));
		assertEquals("theatre of blood hard mode", PersonalBests.syncBase("Theatre of Blood: Hard Mode"));
	}

	@Test
	public void everyPanelBossReadsTheKeyRuneLiteStoresItUnder() throws Exception
	{
		// RuneLite's chat commands file a boss's time under longBossName's answer for the name, lowercased.
		java.lang.reflect.Method store = net.runelite.client.plugins.chatcommands.ChatCommandsPlugin.class
			.getDeclaredMethod("longBossName", String.class);
		store.setAccessible(true);
		java.util.Set<String> names = new java.util.HashSet<>();
		for (net.runelite.client.hiscore.HiscoreSkill boss : PanelData.BOSSES)
		{
			String key = ((String) store.invoke(null, boss.getName().toLowerCase(java.util.Locale.ROOT)))
				.toLowerCase(java.util.Locale.ROOT);
			assertEquals(boss.getName(), PersonalBests.encounter(boss.getName()), PersonalBests.encounter(key));
			assertTrue("two bosses share a name: " + boss.getName(), names.add(PersonalBests.encounter(boss.getName())));
		}
	}
}
