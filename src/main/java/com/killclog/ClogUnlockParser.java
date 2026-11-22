package com.killclog;

import java.util.Locale;
import java.util.regex.*;
import lombok.*;
import net.runelite.client.util.Text;

/**
 * Reads the chat lines a live unlock needs: the game's "New item added to your collection log", the clan broadcast
 * that stands in for a player whose notification is popup-only (carrying the game's obtained and total), and the kill-
 * count lines behind "obtained at N kc".
 */
final class ClogUnlockParser
{
	private static final String COLLECTION_LOG_UNLOCK_PREFIX =
		"New item added to your collection log:";

	// The clan broadcast: "<player> received a new collection log item: <item> (1186/1706)". A player whose
	// notification setting is the popup alone never gets the game message, so the broadcast is the only chat line their
	// own unlock makes. Its counts are the log's obtained and total as of the unlock, which the varp can lag behind.
	private static final Pattern CLAN_BROADCAST_UNLOCK = Pattern.compile(
		"^(.+?) received a new collection log item: (.+?)(?: \\((\\d+)/(\\d+)\\))?$");

	// The game's kill-count message, in RuneLite's own pattern: kills, harvests, laps, completions, successes and
	// "subdued", with their colour tags. Each boss's latest one says which kill an item arrived on (LiveClogSync).
	private static final Pattern KILLCOUNT_MESSAGE = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? "
			+ "(?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?"
			+ "<col=[0-9a-f]{6}>(?<kc>[0-9,]+)</col>");

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class KillContext
	{
		final String boss;
		final int kc;

	}

	static KillContext parseKillCount(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = KILLCOUNT_MESSAGE.matcher(message);
		if (!m.find())
		{
			return null;
		}
		try
		{
			int kc = Integer.parseInt(m.group("kc").replace(",", ""));
			String boss = Text.removeTags(m.group("boss")).replace((char) 160, ' ').trim();
			return boss.isEmpty() || kc <= 0 ? null : new KillContext(boss, kc);
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class BroadcastUnlock
	{
		final String playerName;
		final String itemName;
		final int obtained;
		final int total;

	}

	private ClogUnlockParser()
	{
	}

	static BroadcastUnlock parseClanBroadcast(String message)
	{
		if (message == null)
		{
			return null;
		}

		String cleaned = Text.removeTags(message).replace((char) 160, ' ').trim();
		Matcher m = CLAN_BROADCAST_UNLOCK.matcher(cleaned);
		if (!m.matches())
		{
			return null;
		}

		String itemName = m.group(2).trim();
		while (itemName.endsWith("."))
		{
			itemName = itemName.substring(0, itemName.length() - 1).trim();
		}
		if (itemName.isEmpty())
		{
			return null;
		}

		int obtained = -1;
		int total = -1;
		try
		{
			if (m.group(3) != null && m.group(4) != null)
			{
				obtained = Integer.parseInt(m.group(3));
				total = Integer.parseInt(m.group(4));
			}
		}
		catch (NumberFormatException ignored)
		{
		}

		return new BroadcastUnlock(m.group(1).trim(), itemName, obtained, total);
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
