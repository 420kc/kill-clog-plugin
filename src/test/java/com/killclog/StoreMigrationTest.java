package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StoreMigrationTest
{
	private static final Gson GSON = new Gson();

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void provenOwnLogsMoveByAccountAndEverythingElseIsKept() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		write(dir, "main.json", own("Main", "77", marked("hats", 1)));
		write(dir, "lookedup.json", provider("Looked Up", "77"));
		write(dir, "stranger.json", provider("Stranger", null));
		write(dir, "oldfriend.json", own("Old Friend", null, marked("hats", 2)));
		write(dir, "legacy.json", legacy("Legacy", "78"));
		Files.writeString(new File(dir, "broken.json").toPath(), "{broken");
		Files.writeString(new File(dir, ".kill-clog-identity.json").toPath(),
			"{\"version\":2,\"names\":{\"77\":\"main\",\"78\":\"legacy\"},\"stamps\":{\"77\":2,\"78\":1}}");
		Files.writeString(new File(dir, ".kill-clog-identity.lock").toPath(), "");
		new File(dir, "ca").mkdirs();
		Files.writeString(new File(dir, "ca/main.json").toPath(), "{\"playerName\":\"Main\",\"completed\":{\"EASY\":3}}");

		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));

		PlayerClogData main = read(LocalClogCacheTest.ownFile(dir, 77L));
		assertEquals("Main", main.playerName);
		assertEquals("77", main.ownerHash);
		assertFalse("a stamp and a lookup's marks are not proof", LocalClogCacheTest.ownFile(dir, 78L).exists());
		assertTrue("a stamped provider copy is a lookup", new File(dir, "lookups/lookedup.json").exists());
		assertTrue(new File(dir, "lookups/stranger.json").exists());
		for (String kept : List.of("main.json", "oldfriend.json", "legacy.json", "broken.json", ".kill-clog-identity.json"))
		{
			assertTrue(kept + " is archived, never deleted", new File(dir, "legacy/" + kept).exists());
		}
		assertTrue(new File(dir, "legacy/ca/main.json").exists());
		assertTrue("the ledger's lock file stays put", new File(dir, ".kill-clog-identity.lock").exists());
		assertTrue(new File(dir, StoreMigration.MARKER).exists());
		assertEquals("only the own log is left at the top", 1, dir.list((d, n) -> n.endsWith(".json")).length);
	}

	@Test
	public void aNullMarkerFileIsNeverCertifiedEvenWhenTheLedgerMapsIt() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		write(dir, "legacy.json", legacy("Legacy", "78"));
		Files.writeString(new File(dir, ".kill-clog-identity.json").toPath(), "{\"78\":\"legacy\"}");
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertFalse(LocalClogCacheTest.ownFile(dir, 78L).exists());
		assertTrue(new File(dir, "legacy/legacy.json").exists());
	}

	@Test
	public void twoProvenCopiesMergeProvenanceFirst() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		// The ledger's current file is the base: provider X (qty 99, unmarked) beside marked Y.
		PlayerClogData base = own("Now", "77", marked("hats", 2));
		base.obtained.get("hats").add(new ClogResult.ClogItem(1, 99, null));
		base.firstPartySetupComplete = null;
		// The older copy captured X first-hand: qty 1, kc 42, its own date.
		PlayerClogData other = own("Before", "77", new HashMap<>());
		other.obtained.put("hats", new ArrayList<>(List.of(new ClogResult.ClogItem(1, 1, "2026-01-01 00:00:00", 42, "Boss"))));
		other.firstPartyByCategory = new HashMap<>(Map.of("hats", new ArrayList<>(List.of(1))));
		other.obtained.put("pets", new ArrayList<>(List.of(new ClogResult.ClogItem(5, 1, null))));
		other.firstPartyByCategory.put("pets", new ArrayList<>());
		other.pendingUnlocks = new ArrayList<>(List.of(
			new PendingClogUnlock(List.of(7, 8), "2026-01-02 00:00:00", "77"),
			new PendingClogUnlock(List.of(9, 10), "2026-01-02 00:00:00", "99")));
		other.firstPartySetupComplete = true;
		write(dir, "now.json", base);
		write(dir, ".displaced-77-before.json", other);
		Files.writeString(new File(dir, ".kill-clog-identity.json").toPath(), "{\"names\":{\"77\":\"now\"}}");

		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));

		PlayerClogData merged = read(LocalClogCacheTest.ownFile(dir, 77L));
		assertEquals("the base's name stays", "Now", merged.playerName);
		ClogResult.ClogItem x = find(merged.obtained.get("hats"), 1);
		assertEquals("a marked record beats an unmarked one whichever file holds it", 1, x.getCount());
		assertEquals(42, x.getObtainedAtKc());
		assertEquals("2026-01-01 00:00:00", x.getDate());
		assertTrue(merged.firstPartyByCategory.get("hats").containsAll(List.of(1, 2)));
		assertNotNull("an item only one copy has carries over", find(merged.obtained.get("pets"), 5));
		assertFalse("its mark only travels if it had one", merged.firstPartyByCategory.get("pets").contains(5));
		assertEquals("only the account's own pending unlocks carry over", 1, merged.pendingUnlocks.size());
		assertEquals("77", merged.pendingUnlocks.get(0).ownerHash);
		assertEquals("an explicit completed setup survives", Boolean.TRUE, merged.firstPartySetupComplete);
		assertTrue(new File(dir, "legacy/now.json").exists());
		assertTrue(new File(dir, "legacy/.displaced-77-before.json").exists());

		// And the payload ships the captured quantity, never the provider's.
		LocalClogCache cache = LocalClogCacheTest.onDisk(dir);
		assertTrue(cache.activate("Now", 77L));
		assertEquals(1, find(cache.toFirstPartySyncResult("Now").getObtainedItems().get("hats"), 1).getCount());
	}

	@Test
	public void equalProvenanceKeepsTheBaseAndMarksNeverMakeSetup() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		PlayerClogData base = own("Base", "77", marked("hats", 1));
		base.obtained.get("hats").set(0, new ClogResult.ClogItem(1, 3, null));
		base.firstPartySetupComplete = null;
		PlayerClogData other = own("Other", "77", marked("hats", 1));
		other.obtained.get("hats").set(0, new ClogResult.ClogItem(1, 5, null));
		other.firstPartySetupComplete = false;
		write(dir, "base.json", base);
		write(dir, "other.json", other);
		Files.writeString(new File(dir, ".kill-clog-identity.json").toPath(), "{\"77\":\"base\"}");
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		PlayerClogData merged = read(LocalClogCacheTest.ownFile(dir, 77L));
		assertEquals(3, find(merged.obtained.get("hats"), 1).getCount());
		assertEquals("marks alone never manufacture a completed Search", Boolean.FALSE, merged.firstPartySetupComplete);
	}

	@Test
	public void anExistingDestinationIsCheckedBeforeItIsTrusted() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		write(dir, "main.json", own("Main", "77", marked("hats", 1)));
		File dest = LocalClogCacheTest.ownFile(dir, 77L);
		Files.writeString(dest.toPath(), "{broken");
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertEquals("Main", read(dest).playerName);
		File[] aside = dir.listFiles((d, n) -> n.startsWith(".unreadable-"));
		assertEquals(1, aside.length);
		assertEquals("{broken", Files.readString(aside[0].toPath()));

		File again = temporaryFolder.newFolder();
		PlayerClogData earlier = own("Main", "77", marked("hats", 1));
		write(again, LocalClogCacheTest.ownFile(again, 77L).getName(), earlier);
		write(again, "main.json", own("Main", "77", marked("hats", 2)));
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(again)));
		PlayerClogData resumed = read(LocalClogCacheTest.ownFile(again, 77L));
		assertNotNull("an earlier run's result is the base", find(resumed.obtained.get("hats"), 1));
		assertNotNull("and the source still merges in", find(resumed.obtained.get("hats"), 2));
	}

	@Test
	public void aSidecarNamedForAnotherAccountIsNotProof() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		write(dir, ".displaced-78-main.json", own("Main", "77", marked("hats", 1)));
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertFalse(LocalClogCacheTest.ownFile(dir, 77L).exists());
		assertTrue(new File(dir, "legacy/.displaced-78-main.json").exists());
	}

	@Test
	public void aDeviceNamedFileFromOffWindowsMovesUnderAPrefix() throws Exception
	{
		// Windows cannot even create these; the Hub builds on Linux, where they exist.
		org.junit.Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
		File dir = temporaryFolder.newFolder();
		write(dir, "aux.json", provider("Aux", null));
		write(dir, "con.json", legacy("Con", null));
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertTrue(new File(dir, "lookups/+aux.json").exists());
		assertTrue(new File(dir, "legacy/+con.json").exists());
	}

	@Test
	public void theNewerLookupWinsAndTheMarkerStopsReruns() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		PlayerClogData newer = provider("Same", null);
		newer.lastUpdated = "2026-09-02T00:00:00Z";
		write(dir, "same.json", newer);
		new File(dir, "lookups").mkdirs();
		PlayerClogData older = provider("Same", null);
		older.lastUpdated = "2026-09-01T00:00:00Z";
		write(new File(dir, "lookups"), "same.json", older);
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertEquals("2026-09-02T00:00:00Z", read(new File(dir, "lookups/same.json")).lastUpdated);

		write(dir, "later.json", provider("Later", null));
		assertTrue(StoreMigration.run(GSON, TestFolders.folder(dir)));
		assertTrue("a marked store never migrates twice", new File(dir, "later.json").exists());
	}

	@Test
	public void twoClientsMigratingAtOnceLoseNothing() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		for (int i = 0; i < 20; i++)
		{
			write(dir, "own" + i + ".json", own("Own " + i, Long.toString(100 + i), marked("hats", i)));
			write(dir, "look" + i + ".json", provider("Look " + i, null));
		}
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try
		{
			List<Future<Boolean>> runs = new ArrayList<>();
			for (int i = 0; i < 2; i++)
			{
				runs.add(pool.submit(() -> StoreMigration.run(GSON, TestFolders.folder(dir))));
			}
			for (Future<Boolean> run : runs)
			{
				assertTrue(run.get());
			}
		}
		finally
		{
			pool.shutdownNow();
		}
		for (int i = 0; i < 20; i++)
		{
			assertEquals("Own " + i, read(LocalClogCacheTest.ownFile(dir, 100 + i)).playerName);
			assertTrue(new File(dir, "lookups/look" + i + ".json").exists());
		}
	}

	// ── helpers ──

	private static PlayerClogData own(String name, String hash, Map<String, List<Integer>> marks)
	{
		PlayerClogData data = new PlayerClogData();
		data.playerName = name;
		data.ownerHash = hash;
		data.lastUpdated = "2026-09-01T00:00:00Z";
		data.categories = new HashMap<>(Map.of("hats", List.of(1, 2, 3)));
		data.obtained = new HashMap<>();
		for (Map.Entry<String, List<Integer>> e : marks.entrySet())
		{
			List<ClogResult.ClogItem> items = new ArrayList<>();
			for (int id : e.getValue())
			{
				items.add(new ClogResult.ClogItem(id, 1, null));
			}
			data.obtained.put(e.getKey(), items);
		}
		data.firstPartyByCategory = new HashMap<>();
		marks.forEach((k, v) -> data.firstPartyByCategory.put(k, new ArrayList<>(v)));
		data.firstPartySetupComplete = true;
		return data;
	}

	private static PlayerClogData provider(String name, String hash)
	{
		PlayerClogData data = own(name, hash, marked("hats", 1));
		data.firstPartyByCategory = new HashMap<>();
		data.firstPartySetupComplete = false;
		return data;
	}

	private static PlayerClogData legacy(String name, String hash)
	{
		PlayerClogData data = own(name, hash, marked("hats", 1));
		data.firstPartyByCategory = null;
		data.firstPartySetupComplete = null;
		return data;
	}

	private static Map<String, List<Integer>> marked(String category, int... ids)
	{
		List<Integer> list = new ArrayList<>();
		Arrays.stream(ids).forEach(list::add);
		return new HashMap<>(Map.of(category, list));
	}

	private static void write(File dir, String name, PlayerClogData data) throws Exception
	{
		Files.writeString(new File(dir, name).toPath(), GSON.toJson(data));
	}

	private static PlayerClogData read(File file) throws Exception
	{
		return GSON.fromJson(Files.readString(file.toPath()), PlayerClogData.class);
	}

	private static ClogResult.ClogItem find(List<ClogResult.ClogItem> items, int id)
	{
		for (ClogResult.ClogItem item : items)
		{
			if (item.getId() == id)
			{
				return item;
			}
		}
		return null;
	}
}
