package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClogUnlockParserTest
{
	@Test
	public void testParsesCollectionLogUnlockMessage()
	{
		assertEquals("Magus vestige", ClogUnlockParser.parseItemName(
			"New item added to your collection log: Magus vestige"));
	}

	@Test
	public void testParsesTaggedCollectionLogUnlockMessage()
	{
		assertEquals("Scurry", ClogUnlockParser.parseItemName(
			"<col=ff0000>New item added to your collection log: Scurry.</col>"));
	}

	@Test
	public void testIgnoresOtherGameMessages()
	{
		assertNull(ClogUnlockParser.parseItemName(
			"You have completed an elite Combat Achievement task."));
	}

	@Test
	public void testParsesClanBroadcastWithCountsAndNbsp()
	{
		// Verbatim shape from the 2026-07-16 client log: rank icon tag,
		// non-breaking space in the RSN, live counts in the tail.
		ClogUnlockParser.BroadcastUnlock unlock = ClogUnlockParser.parseClanBroadcast(
			"<img=2> 420 kc received a new collection log item: Venator tooth (1186/1706)");
		assertNotNull(unlock);
		assertEquals("420 kc", unlock.playerName);
		assertEquals("Venator tooth", unlock.itemName);
		assertEquals(1186, unlock.obtained);
		assertEquals(1706, unlock.total);
	}

	@Test
	public void testParsesClanBroadcastWithoutCounts()
	{
		ClogUnlockParser.BroadcastUnlock unlock = ClogUnlockParser.parseClanBroadcast(
			"Some Player received a new collection log item: Magus vestige");
		assertNotNull(unlock);
		assertEquals("Some Player", unlock.playerName);
		assertEquals("Magus vestige", unlock.itemName);
		assertEquals(-1, unlock.obtained);
		assertEquals(-1, unlock.total);
	}

	@Test
	public void testParsesKillCountMessages()
	{
		ClogUnlockParser.KillContext kc = ClogUnlockParser.parseKillCount(
			"Your Maggot King kill count is: <col=ff0000>421</col>");
		assertNotNull(kc);
		assertEquals("Maggot King", kc.boss);
		assertEquals(421, kc.kc);

		ClogUnlockParser.KillContext raid = ClogUnlockParser.parseKillCount(
			"Your completed <col=ff0000>Chambers of Xeric</col> count is: <col=ff0000>1,234</col>");
		assertNotNull(raid);
		assertEquals("Chambers of Xeric", raid.boss);
		assertEquals(1234, raid.kc);

		assertNull(ClogUnlockParser.parseKillCount(
			"New item added to your collection log: Venator tooth"));
	}

	@Test
	public void testIgnoresOtherClanBroadcasts()
	{
		assertNull(ClogUnlockParser.parseClanBroadcast(
			"Some Player has reached a total level of 2000."));
		assertNull(ClogUnlockParser.parseClanBroadcast(
			"Some Player has received a drop: Dragon pickaxe (1,504,819 coins)."));
	}
}
