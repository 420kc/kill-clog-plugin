package com.killclog;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The stores' file names and their one way to read, write and list a file. Own logs are account-keyed; 2.4's
 * name-keyed files, which 2.5 to 2.6.5 moved on first start, are left where they are and never read.
 */
@Slf4j
final class StoreFiles
{
	static final String LOOKUPS = "lookups";

	private StoreFiles()
	{
	}

	/** An account's own-log file: its hash as 16 hex digits, longer than any character name. */
	static String ownFileName(String hashKey)
	{
		return String.format("%016x", Long.parseLong(hashKey)) + ".json";
	}

	static void write(Gson gson, Filepath folder, Filepath dest, Object data) throws IOException
	{
		Filepath tmp = folder.createTempFile(".write-", ".tmp");
		try
		{
			// On the disk before it takes the file's place: a power cut then leaves the old log or the new one, never
			// an empty file where a log was.
			ByteBuffer json = ByteBuffer.wrap(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
			try (FileChannel channel = tmp.openFileChannel(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
			{
				while (json.hasRemaining())
				{
					channel.write(json);
				}
				channel.force(true);
			}
			LocalClogCache.atomicMove(tmp, dest);
		}
		finally
		{
			tmp.deleteIfExists();
		}
	}

	static <T> T read(Gson gson, Filepath file, Class<T> type)
	{
		try (BufferedReader reader = file.openBufferedReader())
		{
			return gson.fromJson(reader, type);
		}
		catch (Exception e)
		{
			log.warn("Unreadable Kill Clog file '{}': {}", file.getFileName(), e.getMessage());
			return null;
		}
	}

	/** The files directly in a folder; the store lists its own folder the same way. */
	static List<Filepath> list(Filepath dir) throws IOException
	{
		try (Stream<Filepath> walk = dir.walk(1))
		{
			return walk.filter(Filepath::isFile).collect(Collectors.toList());
		}
	}
}
