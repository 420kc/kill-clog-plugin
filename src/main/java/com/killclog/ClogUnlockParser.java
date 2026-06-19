package com.killclog;

import java.util.Locale;
import net.runelite.client.util.Text;

final class ClogUnlockParser
{
	private static final String COLLECTION_LOG_UNLOCK_PREFIX =
		"New item added to your collection log:";

	private ClogUnlockParser()
	{
	}

	static String parseItemName(String message)
	{
		if (message == null)
		{
			return null;
		}

		String cleaned = Text.removeTags(message).replace((char) 160, ' ').trim();
		String lower = cleaned.toLowerCase(Locale.ROOT);
		String prefix = COLLECTION_LOG_UNLOCK_PREFIX.toLowerCase(Locale.ROOT);
		int prefixIndex = lower.indexOf(prefix);
		if (prefixIndex < 0)
		{
			return null;
		}

		String itemName = cleaned.substring(prefixIndex + COLLECTION_LOG_UNLOCK_PREFIX.length()).trim();
		while (itemName.endsWith("."))
		{
			itemName = itemName.substring(0, itemName.length() - 1).trim();
		}
		return itemName.isEmpty() ? null : itemName;
	}

	static String normalizeItemName(String itemName)
	{
		if (itemName == null)
		{
			return "";
		}
		return Text.removeTags(itemName)
			.replace((char) 160, ' ')
			.trim()
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ");
	}
}
