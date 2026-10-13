package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PersonalBestProfilesTest
{
	@Test
	public void identitySurvivesRenamesAndExcludesReusedNames()
	{
		List<RuneScapeProfile> profiles = List.of(
			profile("Same Name", 77L, "foreign"),
			profile("Old Name", 42L, "old"),
			profile("Same Name", 42L, "rsprofile.current"),
			profile("Same Name", 42L, "current"));
		assertEquals(List.of("rsprofile.old", "rsprofile.current"), PersonalBests.profileKeys(profiles, 42L, "STANDARD"));
		assertEquals(List.of("rsprofile.foreign"), PersonalBests.profileKeys(profiles, 77L, "STANDARD"));
		assertTrue(PersonalBests.profileKeys(profiles, 99L, "STANDARD").isEmpty());
	}

	@Test
	public void onlyProfilesOfTheAskedTypeAreRead()
	{
		for (RuneScapeProfileType type : RuneScapeProfileType.values())
		{
			List<String> keys = PersonalBests.profileKeys(List.of(
				new RuneScapeProfile("Player", type, 42L, "key")), 42L, "STANDARD");
			assertEquals(type.name(), type == RuneScapeProfileType.STANDARD, !keys.isEmpty());
		}
		List<RuneScapeProfile> profiles = List.of(
			new RuneScapeProfile("Player", RuneScapeProfileType.STANDARD, 42L, "main"),
			new RuneScapeProfile("Player", RuneScapeProfileType.RAGING_ECHOES_LEAGUE, 42L, "earlier"),
			new RuneScapeProfile("Player", RuneScapeProfileType.DEMONIC_PACTS_LEAGUE, 42L, "league"),
			new RuneScapeProfile("Player", null, 42L, "unknown"));
		assertEquals(List.of("rsprofile.league"), PersonalBests.profileKeys(profiles, 42L, "DEMONIC_PACTS_LEAGUE"));
		assertTrue("a League RuneLite does not know yet reads no profile",
			PersonalBests.profileKeys(profiles, 42L, null).isEmpty());
	}

	@Test
	public void invalidHashesAndMissingKeysCannotSelectAProfile()
	{
		assertTrue(PersonalBests.profileKeys(List.of(profile("Player", -1L, "old")), -1L, "STANDARD").isEmpty());
		assertTrue(PersonalBests.profileKeys(List.of(profile("Player", 0L, "old")), 0L, "STANDARD").isEmpty());
		assertTrue(PersonalBests.profileKeys(List.of(profile("Player", 42L, null),
			profile("Player", 42L, "")), 42L, "STANDARD").isEmpty());
		// A signed long can be a valid identity; only sentinel values are invalid.
		assertEquals(List.of("rsprofile.negative"),
			PersonalBests.profileKeys(List.of(profile("Player", -2L, "negative")), -2L, "STANDARD"));
	}

	@Test
	public void emptySelectionNeverReadsTheCurrentProfileAsFallback()
	{
		// A null manager deliberately fails if any implicit profile read happens.
		PersonalBests vanilla = new PersonalBests(null);
		assertNull(vanilla.pbText(List.of(), "Zulrah"));
		assertTrue(vanilla.variantSecondsAcrossProfiles("personalbest", "", List.of(), "Zulrah").isEmpty());
		assertTrue(vanilla.variantSecondsAcrossProfiles(AdvLogPbs.CONFIG_GROUP, AdvLogPbs.KEY_PREFIX,
			List.of(), "Zulrah").isEmpty());
	}

	@Test
	public void yourCardShowsTheTimeTheSyncSends()
	{
		// RuneLite's store says 2:00.00, the game's adventure log 2:10.00: the game's record wins on the card as in
		// the sync, so the card and killclog.com never disagree (2.6.3).
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileKey()).thenReturn("main");
		org.mockito.Mockito.doReturn(120.0).when(config).getConfiguration(eq("personalbest"), eq("rsprofile.main"),
			eq("zulrah"), org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class));
		org.mockito.Mockito.doReturn(130.0).when(config).getConfiguration(eq(AdvLogPbs.CONFIG_GROUP), eq("rsprofile.main"),
			eq(AdvLogPbs.KEY_PREFIX + "zulrah"), org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class));
		PersonalBests store = new PersonalBests(config);
		List<String> keys = List.of("rsprofile.main");
		assertEquals(PbRecord.Source.ADVLOG, store.records(keys, "Zulrah").get("zulrah").source);
		assertEquals(PersonalBests.formatSeconds(130.0), store.pbText(keys, "Zulrah"));
	}

	@Test
	public void aTeamSizesTimeSaysWhichAsKillclogComDoes()
	{
		// RuneLite keeps a raid's best at any size beside each size's own: the size names the time.
		Map<String, Double> variants = new LinkedHashMap<>();
		variants.put("chambers of xeric", 1333.8);
		variants.put("chambers of xeric solo", 2942.4);
		variants.put("chambers of xeric 5 players", 1333.8);
		String raid = PersonalBests.labeled(variants, "Chambers of Xeric");
		assertEquals("(5 players) 22:13.80", raid);
		assertEquals("PB (5 players): ", ClogHelper.pbLabel(raid));
		assertEquals("22:13.80", ClogHelper.pbTime(raid));

		String zulrah = PersonalBests.labeled(Map.of("zulrah", 66.0), "Zulrah");
		assertEquals("PB: ", ClogHelper.pbLabel(zulrah));
		assertEquals("1:06", ClogHelper.pbTime(zulrah));
		assertNull(PersonalBests.labeled(Map.of(), "Zulrah"));
	}

	@Test
	public void theProfileRuneLiteChoseSpeaksAndTheOnesItIgnoresFillGaps()
	{
		// One account's two STANDARD fragments as RuneLite left them: the old one has whole seconds from before
		// precise timing and ToA team sizes filed one player off (a real account's, 2026-10-12).
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileKey()).thenReturn("rsprofile.chosen");
		Object[][] stored = {
			{"rsprofile.chosen", "alchemical hydra", 101.4},
			{"rsprofile.old", "alchemical hydra", 101.0},
			{"rsprofile.chosen", "tombs of amascut expert mode 6 players", 1798.8},
			{"rsprofile.old", "tombs of amascut expert mode 6 players", 1634.4},
			{"rsprofile.old", "tombs of amascut expert mode solo", 1651.2},
			{"rsprofile.older", "tombs of amascut expert mode solo", 1700.4}};
		for (Object[] pb : stored)
		{
			when(config.getConfiguration(eq("personalbest"), eq((String) pb[0]), eq((String) pb[1]),
				eq((java.lang.reflect.Type) double.class))).thenReturn((Double) pb[2]);
		}
		PersonalBests bests = new PersonalBests(config);
		List<String> keys = List.of("rsprofile.old", "rsprofile.chosen", "rsprofile.older");

		assertEquals(Map.of("alchemical hydra", 101.4),
			bests.variantSecondsAcrossProfiles("personalbest", "", keys, "Alchemical Hydra"));
		Map<String, Double> toa = bests.variantSecondsAcrossProfiles("personalbest", "", keys, "Tombs of Amascut: Expert Mode");
		assertEquals(Double.valueOf(1798.8), toa.get("tombs of amascut expert mode 6 players"));
		assertEquals("a variant the chosen one lacks: the fastest ignored one", Double.valueOf(1651.2),
			toa.get("tombs of amascut expert mode solo"));

		when(config.getRSProfileKey()).thenReturn(null);
		assertEquals("no chosen profile among them: the fastest of all", Map.of("alchemical hydra", 101.0),
			bests.variantSecondsAcrossProfiles("personalbest", "", keys, "Alchemical Hydra"));
	}

	private static RuneScapeProfile profile(String name, long hash, String key)
	{
		return new RuneScapeProfile(name, RuneScapeProfileType.STANDARD, hash, key);
	}
}
