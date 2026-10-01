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
		cache.setActivePlayer("Tester");
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		cache.shutdown();
		assertFalse(cache.isActivePlayer("Tester"));
		writer.runQueued();
		assertEquals(1, easy(directory));

		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 2));
		cache.shutdown();
		cache.setActivePlayer("Tester");
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
		cache.setActivePlayer("Tester");
		assertNull("nothing until this session's account is known", cache.getCached("Tester"));
		cache.cacheResult("Tester", -1L, Map.of(CombatAchievementTier.EASY, 1));
		assertNull(cache.getCached("Tester"));
		cache.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertTrue(cache.hasDataFor("Tester"));
		assertFalse(cache.hasDataFor("Someone"));
		cache.setActivePlayer("Someone");
		assertNull("another player never reads this account's counts", cache.getCached("Tester"));

		LocalCaCache restarted = open(new Gson(), directory);
		restarted.setActivePlayer("Tester");
		restarted.cacheResult("Tester", HASH, Map.of(CombatAchievementTier.EASY, 1));
		assertTrue(restarted.hasDataFor("Tester"));
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

	private static LocalCaCache open(Gson gson, File directory)
	{
		LocalCaCache cache = new LocalCaCache(gson, new InlineScheduledExecutorService());
		Filepath folder = TestFolders.folder(directory);
		cache.open(folder);
		return cache;
	}

	private static File caFile(File directory)
	{
		return new File(directory, StoreMigration.ownFileName(Long.toString(HASH)));
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
