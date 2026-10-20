package com.killclog;

import com.google.gson.Gson;
import java.io.File;
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

	@Test
	public void aMemoryOnlySessionSavesNothingAndRetriesNothing()
	{
		int[] tasks = {0};
		LocalCaCache ca = new LocalCaCache(new Gson(), new java.util.concurrent.ScheduledThreadPoolExecutor(1)
		{
			@Override
			public void execute(Runnable command)
			{
				tasks[0]++;
				command.run();
			}
		});
		ca.open(null);
		ca.setActivePlayer("Tester");
		ca.cacheResult("Tester", 77L, Map.of(CombatAchievementTier.EASY, 1));
		int afterFirst = tasks[0];
		// The same counts read again are already held: no write queued, so no warning each read.
		ca.cacheResult("Tester", 77L, Map.of(CombatAchievementTier.EASY, 1));
		assertEquals(afterFirst, tasks[0]);
		assertEquals(1, ca.getCached("Tester").getTotalPoints());
		assertFalse("a plain varp change is not a CA total", new LocalCaReader().isCaVarbit(-1));
	}
}
