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
}
