package com.killclog;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** team-suffixes.tsv holds vanilla's raid size suffixes exactly, leading spaces and the empty one included. */
public class TeamSuffixesCatalogTest
{
	@Test
	public void theSuffixesAreVanillasInOrder()
	{
		assertArrayEquals(new String[]{"", " solo", " 1 player", " 2 players", " 3 players", " 4 players", " 5 players", " 6 players", " 7 players", " 8 players", " 9 players", " 10 players", " 11-15 players", " 16-23 players", " 24+ players", " 2+ players", " 3+ players", " 4+ players", " 5+ players", " 6+ players", " 7+ players", " 8+ players", " 9+ players", " 10+ players"}, PersonalBests.TEAM_SUFFIXES);
	}
}
