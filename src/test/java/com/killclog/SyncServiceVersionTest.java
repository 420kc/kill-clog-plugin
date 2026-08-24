package com.killclog;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SyncServiceVersionTest
{
	@Test
	public void syncPayloadVersionMatchesPluginReleaseMetadata() throws Exception
	{
		Properties properties = new Properties();
		try (InputStream input = new FileInputStream("runelite-plugin.properties"))
		{
			properties.load(input);
		}
		assertEquals(properties.getProperty("version"), SyncService.CLIENT_VERSION);
	}
}
