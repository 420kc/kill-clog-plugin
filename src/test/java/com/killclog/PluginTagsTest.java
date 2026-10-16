package com.killclog;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PluginTagsTest
{
	@Test
	public void theInstalledListAndThePluginHubSearchTheSameTags() throws Exception
	{
		// The Plugin Hub searches runelite-plugin.properties; a player's installed list searches the descriptor.
		Properties hub = new Properties();
		try (InputStreamReader in = new InputStreamReader(new FileInputStream("runelite-plugin.properties"),
			StandardCharsets.UTF_8))
		{
			hub.load(in);
		}
		List<String> hubTags = Arrays.asList(hub.getProperty("tags").split(","));
		List<String> descriptorTags = Arrays.asList(KillClogPlugin.class.getAnnotation(PluginDescriptor.class).tags());
		assertEquals(hubTags, descriptorTags);
	}
}
