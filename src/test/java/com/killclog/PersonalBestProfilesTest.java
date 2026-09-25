package com.killclog;

import java.util.List;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import org.junit.Test;
import static org.junit.Assert.*;

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
		assertEquals(0, vanilla.bestSecondsAcrossProfiles(List.of(), "Zulrah"), 0);
		assertTrue(vanilla.variantSecondsAcrossProfiles(List.of(), "Zulrah").isEmpty());
		assertTrue(new AdvLogPbs(null).variantSecondsAcrossProfiles(List.of(), "Zulrah").isEmpty());
	}

	private static RuneScapeProfile profile(String name, long hash, String key)
	{
		return new RuneScapeProfile(name, RuneScapeProfileType.STANDARD, hash, key);
	}
}
