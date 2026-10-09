package com.killclog;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;

/**
 * Reads the personal bests RuneLite's own chat commands plugin records on the
 * player's profile: group "personalbest", key = lowercase boss name (with
 * optional team-size suffix), value = seconds. Vanilla writes them from
 * kill-timer chat messages and adventure log reads, so they exist for the
 * local player only; other players never resolve.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PersonalBests
{
	/**
	 * Team-size suffixes vanilla appends to raid pb keys, including the large
	 * CoX buckets and the open-ended "N+" shape the adventure log emits for
	 * old Nightmare records. The tooltip shows the fastest across sizes: one
	 * line, the true personal best.
	 */
	/* package */ static final String[] TEAM_SUFFIXES =
		CatalogTsv.values(PersonalBests.class, "team-suffixes.tsv", "suffix").toArray(new String[0]);

	private final ConfigManager configManager;

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

	/**
	 * Formatted fastest time for a panel boss across team sizes AND across rs-profile fragments, or null when none
	 * recorded. RuneLite splinters one account into many internal profiles over time (client changes, world types),
	 * scattering its personal bests; the true pb is the minimum over the explicitly selected account-owned fragments.
	 */
	String pbText(java.util.List<String> profileKeys, String panelBossName)
	{
		return labeled(variantSecondsAcrossProfiles("personalbest", "", profileKeys, panelBossName), panelBossName);
	}

	/**
	 * The fastest variant's time; a team size's carries it, "(5 players) 22:13.80", which the card reads as
	 * killclog.com does, "PB (5 players): 22:13.80" ({@link ClogHelper#pbLabel}). On a tie the team size wins.
	 */
	@Nullable
	static String labeled(java.util.Map<String, Double> variants, String panelBossName)
	{
		java.util.Map.Entry<String, Double> best = null;
		for (java.util.Map.Entry<String, Double> variant : variants.entrySet())
		{
			best = best == null || variant.getValue() <= best.getValue() ? variant : best;
		}
		if (best == null)
		{
			return null;
		}
		String team = best.getKey().substring(keyCandidates(panelBossName)[0].length()).trim();
		String time = formatSeconds(best.getValue());
		return team.isEmpty() ? time : "(" + Character.toUpperCase(team.charAt(0)) + team.substring(1) + ") " + time;
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
