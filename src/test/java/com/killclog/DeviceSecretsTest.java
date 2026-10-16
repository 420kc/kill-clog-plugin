package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class DeviceSecretsTest
{
	private static final String GROUP = "killclog-profile-appearance";

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	/** A config that behaves like RuneLite's: what's set reads back until it's unset. */
	private static ConfigManager config(Map<String, String> values)
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration(eq(GROUP), anyString(), eq(String.class)))
			.thenAnswer(call -> values.get(call.<String>getArgument(1)));
		doAnswer(call -> values.put(call.getArgument(1), call.getArgument(2)))
			.when(config).setConfiguration(eq(GROUP), anyString(), anyString());
		doAnswer(call -> values.remove(call.<String>getArgument(1)))
			.when(config).unsetConfiguration(eq(GROUP), anyString());
		return config;
	}

	@Test
	public void anEarlierReleasesSecretMovesOutOfSyncedConfigIntoTheFile() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Map<String, String> synced = new HashMap<>(Map.of("1.production.deviceSecret", "s3cret"));
		DeviceSecrets secrets = new DeviceSecrets(config(synced), GROUP, LocalClogCacheTest.onDisk(dir), new Gson());
		assertEquals("s3cret", secrets.read("1.production.deviceSecret"));
		assertFalse("it no longer travels with profile sync", synced.containsKey("1.production.deviceSecret"));
		assertTrue(Files.readString(new File(dir, DeviceSecrets.FILE).toPath()).contains("s3cret"));
		// A fresh start reads it from the file alone.
		DeviceSecrets again = new DeviceSecrets(config(synced), GROUP, LocalClogCacheTest.onDisk(dir), new Gson());
		assertEquals("s3cret", again.read("1.production.deviceSecret"));
	}

	@Test
	public void clearingASecretRemovesItEverywhere() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Map<String, String> synced = new HashMap<>();
		DeviceSecrets secrets = new DeviceSecrets(config(synced), GROUP, LocalClogCacheTest.onDisk(dir), new Gson());
		secrets.write("1.production.deviceSecret", "s3cret");
		assertTrue(synced.isEmpty());
		secrets.write("1.production.deviceSecret", null);
		assertNull(secrets.read("1.production.deviceSecret"));
		assertFalse(Files.readString(new File(dir, DeviceSecrets.FILE).toPath()).contains("s3cret"));
	}

	@Test
	public void aSessionWithoutADataFolderKeepsUsingConfig()
	{
		Map<String, String> synced = new HashMap<>();
		LocalClogCache memoryOnly = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		memoryOnly.open(() -> null);
		DeviceSecrets secrets = new DeviceSecrets(config(synced), GROUP, memoryOnly, new Gson());
		secrets.write("1.production.deviceSecret", "s3cret");
		assertEquals("s3cret", synced.get("1.production.deviceSecret"));
		assertEquals("s3cret", secrets.read("1.production.deviceSecret"));
	}
}
