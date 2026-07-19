package com.killclog;

import java.util.Locale;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the personal bests RuneLite's own chat commands plugin records on the
 * player's profile: group "personalbest", key = lowercase boss name (with
 * optional team-size suffix), value = seconds. Vanilla writes them from
 * kill-timer chat messages and adventure log reads, so they exist for the
 * local player only; other players never resolve.
 */
final class PersonalBests
{
	/**
	 * Team-size suffixes vanilla appends to raid pb keys. The tooltip shows
	 * the fastest across sizes: one line, the true personal best.
	 */
	private static final String[] TEAM_SUFFIXES = {
		"", " solo", " 1 player", " 2 players", " 3 players", " 4 players",
		" 5 players", " 6 players", " 7 players", " 8 players",
	};

	private final ConfigManager configManager;

	PersonalBests(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Formatted fastest time for a panel boss, or null when none recorded. */
	String pbText(String panelBossName)
	{
		double best = 0;
		for (String base : keyCandidates(panelBossName))
		{
			for (String suffix : TEAM_SUFFIXES)
			{
				Double pb = configManager.getRSProfileConfiguration(
					"personalbest", base + suffix, double.class);
				if (pb != null && pb > 0 && (best == 0 || pb < best))
				{
					best = pb;
				}
			}
		}
		return best > 0 ? formatSeconds(best) : null;
	}

	/**
	 * Panel display names differ from vanilla's stored keys in punctuation for
	 * the mode raids and in the "The " prefix for some bosses, so both shapes
	 * are tried.
	 */
	/* package */ static String[] keyCandidates(String panelBossName)
	{
		String key = panelBossName.toLowerCase(Locale.ROOT)
			.replace(": challenge mode", " challenge mode")
			.replace(": hard mode", " hard mode")
			.replace(": expert mode", " expert mode")
			.replace(": entry mode", " entry mode");
		if (key.startsWith("the "))
		{
			return new String[]{key, key.substring("the ".length())};
		}
		return new String[]{key};
	}

	/** Same rendering vanilla uses for !pb, trailing precision only when real. */
	/* package */ static String formatSeconds(double seconds)
	{
		int hours = (int) (Math.floor(seconds) / 3600);
		int minutes = (int) (Math.floor(seconds / 60) % 60);
		double secs = seconds % 60;

		String prefix = hours > 0
			? String.format(Locale.US, "%d:%02d:", hours, minutes)
			: String.format(Locale.US, "%d:", minutes);
		return prefix + (Math.floor(secs) == secs
			? String.format(Locale.US, "%02d", (int) secs)
			: String.format(Locale.US, "%05.2f", secs));
	}
}
