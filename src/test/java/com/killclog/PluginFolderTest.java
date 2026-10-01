package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PluginFolderTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testsNeverSeeTheRealRuneliteFolder()
	{
		assertTrue(net.runelite.client.RuneLite.RUNELITE_DIR.getPath()
			.replace(File.separatorChar, '/').contains("/build/test-home/"));
	}

	@Test
	public void onlyAConfirmedMissingFileCountsAsAbsent() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Files.writeString(new File(dir, "tester.json").toPath(), "{}");
		assertFalse(LocalClogCache.absent(TestFolders.folder(dir).join("tester.json")));
		assertTrue(LocalClogCache.absent(TestFolders.folder(dir).join("nobody.json")));
		// Same answer as Files.notExists, which the 2.4 checks used.
		for (String path : new String[]{"tester.json", "nobody.json", "tester.json/inside.json", "."})
		{
			assertEquals(path, Files.notExists(dir.toPath().resolve(path)),
				LocalClogCache.absent(TestFolders.folder(dir).join(path)));
		}
	}

	@Test
	public void windowsDeviceNamesTakeASafeFileName()
	{
		assertEquals("+con.json", LocalClogCache.fileName("Con"));
		assertEquals("+com1.json", LocalClogCache.fileName("COM1"));
		assertEquals("+lpt9.json", LocalClogCache.fileName("lpt9"));
		assertEquals("com10.json", LocalClogCache.fileName("Com10"));
		assertEquals("cone.json", LocalClogCache.fileName("Cone"));
		assertEquals("sir_con.json", LocalClogCache.fileName("Sir Con"));
	}

	@Test
	public void aDeviceNamedLookupSavesAndLoads() throws Exception
	{
		File logs = temporaryFolder.newFolder();
		LocalClogCache cache = LocalClogCacheTest.onDisk(logs);
		assertTrue(cache.activate("Main", 77L));
		cache.cacheResult(LocalClogCacheTest.clog("Con", LocalClogCacheTest.categoryItems("hats", 1),
			LocalClogCacheTest.obtainedItems("hats", 1)));
		assertTrue(new File(logs, "lookups/+con.json").isFile());
		LocalClogCache reloaded = LocalClogCacheTest.onDisk(logs);
		assertTrue(reloaded.activate("Main", 77L));
		assertTrue(reloaded.hasDataFor("Con"));
	}

	@Test
	public void aSessionWithoutAFolderStaysInMemory()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() ->
		{
			throw new java.io.IOException("refused");
		});
		assertNull(cache.folder().join());
		assertTrue(cache.activate("Tester", 77L));
		cache.cacheFirstPartyResult(LocalClogCacheTest.clog("Tester", LocalClogCacheTest.categoryItems("hats", 1, 2),
			LocalClogCacheTest.obtainedItems("hats", 1)));
		assertTrue(cache.hasFirstPartyDataFor("Tester"));
		assertFalse("a lookup reads no disk", cache.hasDataFor("Someone Else"));

		LocalCaCache ca = new LocalCaCache(new Gson(), new InlineScheduledExecutorService());
		ca.open(null);
		ca.setActivePlayer("Tester");
		ca.cacheResult("Tester", 77L, Map.of(CombatAchievementTier.EASY, 1));
		assertEquals(1, ca.getCached("Tester").getTotalPoints());
	}
}
