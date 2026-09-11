package com.killclog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalClogCacheAtomicTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void partialSerializationLeavesTheLastValidFileAndLaterSaveRecovers() throws Exception
	{
		AtomicBoolean fail = new AtomicBoolean();
		TypeAdapter<PlayerClogData> delegate = new Gson().getAdapter(PlayerClogData.class);
		Gson gson = new GsonBuilder().registerTypeAdapter(PlayerClogData.class,
			new TypeAdapter<PlayerClogData>()
			{
				@Override
				public void write(JsonWriter out, PlayerClogData value) throws IOException
				{
					if (fail.get())
					{
						out.beginObject().name("partial").value(true);
						out.flush();
						throw new IOException("injected serialization failure");
					}
					delegate.write(out, value);
				}

				@Override
				public PlayerClogData read(JsonReader in) throws IOException
				{
					return delegate.read(in);
				}
			}).create();
		File directory = temporaryFolder.newFolder();
		LocalClogCache cache = new LocalClogCache(gson, new InlineScheduledExecutorService(), directory);
		cache.cacheFirstPartyResult(result(1));
		File file = new File(directory, "tester.json");
		byte[] valid = Files.readAllBytes(file.toPath());
		fail.set(true);
		cache.cacheFirstPartyResult(result(2));
		assertArrayEquals(valid, Files.readAllBytes(file.toPath()));
		assertEquals(1, reload(directory).getId());
		fail.set(false);
		cache.cacheFirstPartyResult(result(3));
		assertEquals(3, reload(directory).getId());
		assertFalse(new File(directory, "tester.json.tmp").exists());
	}

	@Test
	public void unwritableTemporaryPathPreservesExistingJson() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), directory);
		cache.cacheFirstPartyResult(result(1));
		File file = new File(directory, "tester.json");
		byte[] valid = Files.readAllBytes(file.toPath());
		Files.createDirectory(new File(directory, "tester.json.tmp").toPath());
		cache.cacheFirstPartyResult(result(2));
		assertArrayEquals(valid, Files.readAllBytes(file.toPath()));
		assertEquals(1, reload(directory).getId());
	}

	private static ClogResult.ClogItem reload(File directory)
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), directory);
		assertTrue(cache.hasDataFor("Tester"));
		return cache.toClogResult("Tester", Map.of()).getObtainedItems().get("zulrah").get(0);
	}

	private static ClogResult result(int id)
	{
		return new ClogResult("Tester", Map.of("zulrah", List.of(new ClogResult.ClogItem(id, 1, null))),
			Map.of("zulrah", List.of(1, 2, 3)), Map.of(), null, null);
	}
}
