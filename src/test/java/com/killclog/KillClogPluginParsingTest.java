package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogPluginParsingTest
{
	@Test
	public void testParsesCollectionLogUnlockMessage()
	{
		assertEquals("Magus vestige", KillClogPlugin.parseCollectionLogUnlockName(
			"New item added to your collection log: Magus vestige"));
	}

	@Test
	public void testParsesTaggedCollectionLogUnlockMessage()
	{
		assertEquals("Scurry", KillClogPlugin.parseCollectionLogUnlockName(
			"<col=ff0000>New item added to your collection log: Scurry.</col>"));
	}

	@Test
	public void testIgnoresOtherGameMessages()
	{
		assertNull(KillClogPlugin.parseCollectionLogUnlockName(
			"You have completed an elite Combat Achievement task."));
	}
}
