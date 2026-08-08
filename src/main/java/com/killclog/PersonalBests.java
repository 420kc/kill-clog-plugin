package com.killclog;

import java.util.Locale;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.hiscore.HiscoreSkill;

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
	 * Team-size suffixes vanilla appends to raid pb keys, including the large
	 * CoX buckets and the open-ended "N+" shape the adventure log emits for
	 * old Nightmare records. The tooltip shows the fastest across sizes: one
	 * line, the true personal best.
	 */
	/* package */ static final String[] TEAM_SUFFIXES = {
		"", " solo", " 1 player", " 2 players", " 3 players", " 4 players",
		" 5 players", " 6 players", " 7 players", " 8 players", " 9 players",
		" 10 players", " 11-15 players", " 16-23 players", " 24+ players",
		" 2+ players", " 3+ players", " 4+ players", " 5+ players",
		" 6+ players", " 7+ players", " 8+ players", " 9+ players",
		" 10+ players",
	};

	private final ConfigManager configManager;

	PersonalBests(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Formatted fastest time for a panel boss, or null when none recorded. */
	String pbText(String panelBossName)
	{
		double best = bestSeconds(panelBossName);
		return best > 0 ? formatSeconds(best) : null;
	}

	/** Fastest recorded seconds across team sizes, or 0 when none recorded. */
	double bestSeconds(String panelBossName)
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
		return best;
	}

	/**
	 * Fastest seconds across team sizes AND across rs-profile fragments.
	 * RuneLite splinters one account into many internal profiles over time
	 * (client changes, world types), scattering its personal bests; the true
	 * pb is the minimum over every fragment carrying the player's name.
	 */
	double bestSecondsAcrossProfiles(java.util.List<String> profileKeys, String panelBossName)
	{
		double best = 0;
		for (String profileKey : profileKeys)
		{
			for (String base : keyCandidates(panelBossName))
			{
				for (String suffix : TEAM_SUFFIXES)
				{
					Double pb = configManager.getConfiguration(
						"personalbest", profileKey, base + suffix, (java.lang.reflect.Type) double.class);
					if (pb != null && pb > 0 && (best == 0 || pb < best))
					{
						best = pb;
					}
				}
			}
		}
		return best;
	}

	/** STANDARD-world profile fragments belonging to this RSN. */
	java.util.List<String> standardProfileKeys(String rsn)
	{
		java.util.List<String> profileKeys = new java.util.ArrayList<>();
		if (rsn == null)
		{
			return profileKeys;
		}
		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (rsn.equalsIgnoreCase(profile.getDisplayName())
				&& profile.getType() == RuneScapeProfileType.STANDARD)
			{
				String key = profile.getKey();
				profileKeys.add(key.startsWith("rsprofile.") ? key : "rsprofile." + key);
			}
		}
		return profileKeys;
	}

	/** Number of panel bosses with a recorded PB across this player's STANDARD profiles. */
	int countForPlayer(String rsn, HiscoreSkill[] bosses)
	{
		java.util.List<String> profileKeys = standardProfileKeys(rsn);
		int count = 0;
		for (HiscoreSkill boss : bosses)
		{
			double seconds = profileKeys.isEmpty()
				? bestSeconds(boss.getName())
				: bestSecondsAcrossProfiles(profileKeys, boss.getName());
			if (seconds > 0)
			{
				count++;
			}
		}
		return count;
	}

	/** One stored-key read, abstracted so the variant merge logic is testable. */
	@FunctionalInterface
	interface PbReader
	{
		Double read(String key);
	}

	/**
	 * Every recorded variant for a panel boss: canonical variant key
	 * ("&lt;base&gt;&lt;suffix&gt;") -> fastest seconds for THAT variant. Team sizes
	 * stay split - a ladder ranks solo and team runs as different sports -
	 * while alternate stored spellings ({@link #keyCandidates}) still merge
	 * into the canonical key.
	 */
	/* package */ static java.util.Map<String, Double> variantSeconds(
		PbReader reader, String panelBossName)
	{
		java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
		String[] bases = keyCandidates(panelBossName);
		String canonicalBase = bases[0];
		for (String suffix : TEAM_SUFFIXES)
		{
			double best = 0;
			for (String base : bases)
			{
				Double pb = reader.read(base + suffix);
				if (pb != null && pb > 0 && (best == 0 || pb < best))
				{
					best = pb;
				}
			}
			if (best > 0)
			{
				out.put(canonicalBase + suffix, best);
			}
		}
		return out;
	}

	/** {@link #variantSeconds} over the same fragment sweep the tooltips use. */
	java.util.Map<String, Double> variantSecondsAcrossProfiles(
		java.util.List<String> profileKeys, String panelBossName)
	{
		if (profileKeys.isEmpty())
		{
			return variantSeconds(key -> configManager.getRSProfileConfiguration(
				"personalbest", key, double.class), panelBossName);
		}
		return variantSeconds(key ->
		{
			Double best = null;
			for (String profileKey : profileKeys)
			{
				Double pb = configManager.getConfiguration(
					"personalbest", profileKey, key, (java.lang.reflect.Type) double.class);
				if (pb != null && pb > 0 && (best == null || pb < best))
				{
					best = pb;
				}
			}
			return best;
		}, panelBossName);
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
