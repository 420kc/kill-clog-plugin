package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
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
	public void aDeviceNamedPlayerSavesAndLoads() throws Exception
	{
		File ca = temporaryFolder.newFolder();
		LocalCaCache cache = new LocalCaCache(new Gson(), new InlineScheduledExecutorService(), TestFolders.folder(ca));
		cache.setActivePlayer("Con");
		cache.cacheResult("Con", Map.of(CombatAchievementTier.EASY, 4));
		assertTrue(new File(ca, "+con.json").isFile());
		assertEquals(4, new LocalCaCache(new Gson(), new InlineScheduledExecutorService(), TestFolders.folder(ca))
			.getCached("Con").getTotalPoints());

		File logs = temporaryFolder.newFolder();
		PlayerClogData data = new PlayerClogData();
		data.playerName = "Con";
		data.categories = Map.of("hats", List.of(1));
		data.obtained = Map.of("hats", List.of(new ClogResult.ClogItem(1, 1, null)));
		Files.writeString(new File(logs, "+con.json").toPath(), new Gson().toJson(data));
		assertTrue(new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), TestFolders.folder(logs))
			.hasDataFor("Con"));
	}

	@Test
	public void aSessionWithoutAFolderStaysInMemory()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), null);
		assertFalse(cache.followNameChangeForSync("Tester", 77L));
		assertFalse(cache.setActivePlayer("Tester"));
		assertFalse(cache.hasDataFor("Tester"));
		assertFalse("a lookup reads no disk", cache.hasDataFor("Someone Else"));

		LocalCaCache ca = new LocalCaCache(new Gson(), new InlineScheduledExecutorService(), null);
		ca.setActivePlayer("Tester");
		ca.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 1));
		assertEquals(1, ca.getCached("Tester").getTotalPoints());
	}
}
