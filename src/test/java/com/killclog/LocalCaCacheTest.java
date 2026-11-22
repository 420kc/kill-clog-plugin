package com.killclog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalCaCacheTest
{
	private static final long HASH = 77L;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void finalSaveDrainsAfterDisableAndNewSessionCannotOvertakeIt() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		CapturingScheduledExecutorService writer = new CapturingScheduledExecutorService();
		LocalCaCache cache = new LocalCaCache(new Gson(), writer);
		cache.open(TestFolders.folder(directory));
		cache.serve(new Session(1, "Tester", 1L));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		cache.shutdown();
		assertFalse(cache.isActivePlayer("Tester"));
		writer.runQueued();
		assertEquals(1, easy(directory));

		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));
		cache.shutdown();
		cache.serve(new Session(1, "Tester", 1L));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 3));
		writer.runQueued();
		assertEquals(3, easy(directory));
		assertEquals(0, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length);
	}

	@Test
	public void capturesServeOnlyTheLoggedInAccountAndSurviveARestart() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		LocalCaCache cache = open(new Gson(), directory);
		cache.serve(new Session(1, "Tester", 1L));
		assertNull("nothing until this session's account is known", cache.getCached("Tester"));
		cache.cacheResult("Tester", -1L, Map.of(CombatAchievementTier.EASY, 1));
		assertNull(cache.getCached("Tester"));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertTrue(cache.hasDataFor("Tester"));
		assertFalse(cache.hasDataFor("Someone"));
		cache.serve(new Session(1, "Someone", 1L));
		assertNull("another player never reads this account's counts", cache.getCached("Tester"));

		LocalCaCache restarted = open(new Gson(), directory);
		restarted.serve(new Session(1, "Tester", 1L));
		restarted.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertTrue(restarted.hasDataFor("Tester"));
	}

	@Test
	public void lastSessionsReadingStandsAtLoginUntilThisSessionsCapture() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		LocalCaCache before = open(new Gson(), directory);
		before.serve(new Session(1, "Tester", HASH));
		before.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 4));

		// The client starts again. A login is known some seconds before the game's tiers can be read.
		LocalCaCache cache = open(new Gson(), directory);
		Session login = new Session(2, "Tester", HASH);
		cache.serve(login);
		assertTrue("the card has last session's tiers at once", cache.hasDataFor("Tester"));
		assertEquals(4, TestViews.completed(cache.getCached("Tester"), CombatAchievementTier.EASY));
		assertNull("a sync still sends only what this login read", cache.thisLogin(login));
		// Until the game is read this login (it never is on a League world), a fuller sync stands over that reading.
		CombatAchievementResult sync = CombatAchievementResult.of(Map.of(CombatAchievementTier.EASY, 30),
			Map.of(CombatAchievementTier.EASY, 33));
		assertSame(sync, CombatAchievementResult.shown(cache.getCached("Tester"), sync));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 5));
		assertNotSame("the game, read this login, stands over any sync", sync,
			CombatAchievementResult.shown(cache.getCached("Tester"), sync));
		assertEquals(5, TestViews.completed(cache.getCached("Tester"), CombatAchievementTier.EASY));
		assertNotNull(cache.thisLogin(login));

		// A logout serves no one; the next login has them again; another account under the name reads none of it.
		cache.serve(Session.none(3));
		assertFalse(cache.hasDataFor("Tester"));
		cache.serve(new Session(4, "Tester", HASH));
		assertEquals(5, TestViews.completed(cache.getCached("Tester"), CombatAchievementTier.EASY));
		assertSame("read by an earlier login of this run, so not yet this login's word from the game", sync,
			CombatAchievementResult.shown(cache.getCached("Tester"), sync));
		cache.serve(new Session(5, "Tester", HASH + 1));
		assertFalse(cache.hasDataFor("Tester"));
	}

	@Test
	public void theLoggedInNameTypedAnotherWayStillReadsItsCas() throws Exception
	{
		LocalCaCache cache = open(new Gson(), temporaryFolder.newFolder());
		cache.serve(new Session(1, "My\u00A0Name", HASH));
		cache.cacheResult("My\u00A0Name", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertNotNull(cache.getCached("my_name"));
		assertTrue(cache.hasDataFor("My-Name"));
		cache.serve(new Session(1, "My Name", HASH));
		assertNotNull("the same account keeps its record", cache.getCached("My\u00A0Name"));
	}

	@Test
	public void failedTemporaryWritePreservesPreviousCaRecord() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		AtomicBoolean fail = new AtomicBoolean();
		LocalCaCache cache = open(writingGson(fail, () ->
		{
		}), directory);
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		File file = caFile(directory);
		byte[] valid = Files.readAllBytes(file.toPath());
		fail.set(true);
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));
		assertArrayEquals(valid, Files.readAllBytes(file.toPath()));
		assertEquals(1, easy(directory));
		assertEquals(0, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length);
		fail.set(false);
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));
		assertEquals(2, easy(directory));
	}

	@Test
	public void overlappingClientsNeverShareAnIncompleteTemporaryFile() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		LocalCaCache second = open(writingGson(new AtomicBoolean(), () ->
			assertEquals(2, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length)), directory);
		LocalCaCache first = open(writingGson(new AtomicBoolean(), () ->
			second.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2))), directory);
		first.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertEquals(1, easy(directory));
		assertEquals(0, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length);
	}

	@Test
	public void aCaptureWhileTheFolderLoadsIsNewerAndStays() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		open(new Gson(), directory).cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		LocalCaCache twoTasks = new LocalCaCache(new Gson(), new InlineScheduledExecutorService());
		twoTasks.open(null);
		twoTasks.serve(new Session(1, "Tester", HASH));
		twoTasks.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));

		CapturingScheduledExecutorService writer = new CapturingScheduledExecutorService();
		LocalCaCache cache = new LocalCaCache(new Gson(), writer);
		cache.open(TestFolders.folder(directory));
		cache.serve(new Session(1, "Tester", HASH));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));
		writer.runQueued();
		assertEquals("the file's older count never replaces it", twoTasks.getCached("Tester").getTotalPoints(),
			cache.getCached("Tester").getTotalPoints());
		assertEquals(2, easy(directory));
	}

	@Test
	public void theInjectorHandsTheStoreAndThePluginOneCatalog() throws Exception
	{
		com.google.inject.Injector injector = com.google.inject.Guice.createInjector();
		LocalCaCache cache = injector.getInstance(LocalCaCache.class);
		CaCatalog catalog = injector.getInstance(CaCatalog.class);
		java.lang.reflect.Field held = LocalCaCache.class.getDeclaredField("caCatalog");
		held.setAccessible(true);
		assertSame("one catalog for every class the plugin's injector builds", catalog, held.get(cache));
		assertSame(catalog, injector.getInstance(CaCatalog.class));
		assertSame(injector.getInstance(ClogIndex.class), injector.getInstance(ClogIndex.class));
	}

	private static LocalCaCache open(Gson gson, File directory)
	{
		LocalCaCache cache = new LocalCaCache(gson, new InlineScheduledExecutorService());
		Filepath folder = TestFolders.folder(directory);
		cache.open(folder);
		return cache;
	}

	private static File caFile(File directory)
	{
		return new File(directory, StoreFiles.ownFileName(Long.toString(HASH)));
	}

	private static int easy(File directory) throws IOException
	{
		LocalCaCache.CaData data = new Gson().fromJson(Files.readString(caFile(directory).toPath()), LocalCaCache.CaData.class);
		return data.completed.get("EASY");
	}

	private static Gson writingGson(AtomicBoolean fail, Runnable duringWrite)
	{
		TypeAdapter<LocalCaCache.CaData> delegate = new Gson().getAdapter(LocalCaCache.CaData.class);
		return new GsonBuilder().registerTypeAdapter(LocalCaCache.CaData.class,
			new TypeAdapter<LocalCaCache.CaData>()
			{
				@Override
				public void write(JsonWriter out, LocalCaCache.CaData value) throws IOException
				{
					duringWrite.run();
					if (fail.get())
					{
						out.beginObject().name("partial").value(true);
						out.flush();
						throw new IOException("injected partial serialization failure");
					}
					delegate.write(out, value);
				}

				@Override
				public LocalCaCache.CaData read(JsonReader in) throws IOException
				{
					return delegate.read(in);
				}
			}).create();
	}
}
