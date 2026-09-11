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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalCaCacheTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void finalSaveDrainsAfterDisableAndNewSessionCannotOvertakeIt() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		CapturingScheduledExecutorService writer = new CapturingScheduledExecutorService();
		LocalCaCache cache = new LocalCaCache(new Gson(), writer, directory);
		cache.setActivePlayer("Tester");
		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 1));
		cache.shutdown();
		assertFalse(cache.isActivePlayer("Tester"));
		writer.runQueued();
		assertEquals(1, reload(directory).getTotalPoints());

		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 2));
		cache.shutdown();
		cache.setActivePlayer("Tester");
		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 3));
		writer.runQueued();
		assertEquals(3, reload(directory).getTotalPoints());
		assertFalse(new File(directory, "tester.json.tmp").exists());
	}

	@Test
	public void failedTemporaryWritePreservesPreviousCaRecord() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		AtomicBoolean fail = new AtomicBoolean();
		LocalCaCache cache = new LocalCaCache(writingGson(fail, () ->
		{
		}), new InlineScheduledExecutorService(), directory);
		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 1));
		File file = new File(directory, "tester.json");
		byte[] valid = Files.readAllBytes(file.toPath());
		fail.set(true);
		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 2));
		assertArrayEquals(valid, Files.readAllBytes(file.toPath()));
		assertEquals(1, reload(directory).getTotalPoints());
		assertEquals(0, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length);
		fail.set(false);
		cache.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 2));
		assertEquals(2, reload(directory).getTotalPoints());
	}

	@Test
	public void overlappingClientsNeverShareAnIncompleteTemporaryFile() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		LocalCaCache second = new LocalCaCache(writingGson(new AtomicBoolean(), () ->
			assertEquals(2, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length)),
			new InlineScheduledExecutorService(), directory);
		LocalCaCache first = new LocalCaCache(writingGson(new AtomicBoolean(), () ->
			second.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 2))),
			new InlineScheduledExecutorService(), directory);
		first.cacheResult("Tester", Map.of(CombatAchievementTier.EASY, 1));
		assertEquals(1, reload(directory).getTotalPoints());
		assertEquals(0, directory.listFiles((dir, name) -> name.endsWith(".tmp")).length);
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

	private static CombatAchievementResult reload(File directory)
	{
		return new LocalCaCache(new Gson(), new InlineScheduledExecutorService(), directory).getCached("Tester");
	}
}
