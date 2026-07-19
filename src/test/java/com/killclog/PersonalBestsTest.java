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
