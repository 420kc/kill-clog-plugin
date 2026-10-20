package com.killclog;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigManager;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/** Stored PBs as RuneLite answers for them: each profile's keys listed by prefix, each read by its key. */
final class StoredPbs
{
	private StoredPbs()
	{
	}

	/** Each row: group, profile, key, seconds. */
	static void stub(ConfigManager config, Object[][] rows)
	{
		doAnswer(call ->
		{
			List<String> keys = new ArrayList<>();
			for (Object[] row : rows)
			{
				if (row[0].equals(call.getArgument(0)) && row[1].equals(call.getArgument(1))
					&& ((String) row[2]).startsWith(call.getArgument(2)))
				{
					keys.add((String) row[2]);
				}
			}
			return keys;
		}).when(config).getRSProfileConfigurationKeys(anyString(), anyString(), anyString());
		for (Object[] row : rows)
		{
			doReturn(row[3]).when(config).getConfiguration(eq((String) row[0]), eq((String) row[1]), eq((String) row[2]),
				eq((Type) double.class));
		}
	}
}
