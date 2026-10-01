package com.killclog;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;

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

	/** Explicit ownership for publication, from RuneLite profiles of one type; names can change or be reused. */
	static List<String> profileKeys(Iterable<RuneScapeProfile> profiles, long accountHash, @Nullable String type)
	{
		if (accountHash == 0 || accountHash == RuneScapeProfile.ACCOUNT_HASH_INVALID)
		{
			return List.of();
		}
		Set<String> keys = new LinkedHashSet<>();
		for (RuneScapeProfile profile : profiles)
		{
			if (profile.getType() != null && profile.getType().name().equals(type)
				&& profile.getAccountHash() == accountHash)
			{
				String key = profile.getKey();
				if (key != null && !key.isEmpty())
				{
					keys.add(key.startsWith("rsprofile.") ? key : "rsprofile." + key);
				}
			}
		}
		return List.copyOf(keys);
	}

	/** Formatted fastest time for a panel boss across the given profiles, or null when none recorded. */
	String pbText(java.util.List<String> profileKeys, String panelBossName)
	{
		double best = bestSecondsAcrossProfiles(profileKeys, panelBossName);
		return best > 0 ? formatSeconds(best) : null;
	}

	/**
	 * Fastest seconds across team sizes AND across rs-profile fragments.
	 * RuneLite splinters one account into many internal profiles over time
	 * (client changes, world types), scattering its personal bests; the true
	 * pb is the minimum over the explicitly selected account-owned fragments.
	 */
	double bestSecondsAcrossProfiles(java.util.List<String> profileKeys, String panelBossName)
	{
		return variantSecondsAcrossProfiles("personalbest", "", profileKeys, panelBossName)
			.values().stream().min(Double::compare).orElse(0.0);
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
	java.util.Map<String, Double> variantSecondsAcrossProfiles(String group, String prefix,
		java.util.List<String> profileKeys, String panelBossName)
	{
		return variantSeconds(key ->
		{
			Double best = null;
			for (String profileKey : profileKeys)
			{
				Double pb = configManager.getConfiguration(
					group, profileKey, prefix + key, (java.lang.reflect.Type) double.class);
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
