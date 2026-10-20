package com.killclog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;

/**
 * A player's personal bests from the four places this client keeps them: RuneLite's own store ("personalbest",
 * which its chat commands write) and Kill Clog's three lanes, the adventure log, the collection log's line and the
 * kills seen. Each key is a boss's name with an optional team size, each value seconds; they exist for the local
 * player only.
 *
 * <p>The keys a profile holds are listed, never guessed, so a team size or a spelling nobody predicted is still
 * read. Every lane's spelling of a boss reaches one name ({@link #encounter}), so a time any lane filed is read
 * under its boss. The sync names a time as it always has ({@link #syncBase}), so the server's keys never move.
 */
final class PersonalBests
{
	// A team size, as RuneLite and the adventure log append one: " solo", " 3 players", " 24+ players", " 11-15 players".
	private static final Pattern VARIANT = Pattern.compile("^(.+?)( (?:solo|\\d+(?:\\+|-\\d+)? players?))?$");
	private static final Pattern DIGITS = Pattern.compile("\\d+");
	// Spellings that differ beyond case, a mode's colon and a leading "The", each to the name every lane reaches.
	private static final Map<String, String> ALIASES = new HashMap<>();

	static
	{
		for (String[] row : CatalogTsv.rows(PersonalBests.class, "pb-names.tsv", 2))
		{
			ALIASES.put(row[0], row[1]);
		}
	}

	/** Where each lane keeps its times, and what the sync calls the lane. */
	private enum Lane
	{
		STORE("personalbest", "", PbRecord.Source.STORE),
		ADVLOG(AdvLogPbs.CONFIG_GROUP, AdvLogPbs.KEY_PREFIX, PbRecord.Source.ADVLOG),
		CLOG(AdvLogPbs.CONFIG_GROUP, ClogPbs.KEY_PREFIX, PbRecord.Source.CLOG),
		LIVE(AdvLogPbs.CONFIG_GROUP, KillLog.KEY_PREFIX, PbRecord.Source.LIVE);

		final String group;
		final String prefix;
		final PbRecord.Source source;

		Lane(String group, String prefix, PbRecord.Source source)
		{
			this.group = group;
			this.prefix = prefix;
			this.source = source;
		}
	}

	/** One kept time: its lane, the profile and key it sits under (the lane's prefix off), and its team size. */
	private static final class Kept
	{
		final Lane lane;
		final String profile;
		final String key;
		final String variant;
		final double seconds;

		Kept(Lane lane, String profile, String key, String variant, double seconds)
		{
			this.lane = lane;
			this.profile = profile;
			this.key = key;
			this.variant = variant;
			this.seconds = seconds;
		}
	}

	// The plain key first, then solo, then team sizes by players: on a tie, the later variant names the time.
	private static final Comparator<String> VARIANT_ORDER = Comparator.comparingInt(PersonalBests::players)
		.thenComparing(Comparator.naturalOrder());

	@Nullable
	private final ConfigManager configManager;
	private final List<String> profileKeys;
	// Every time the profiles hold, by boss; read once, on the first ask.
	private Map<String, List<Kept>> kept;
	@Nullable
	private String chosen;

	/** The times kept on these profiles of one account ({@link #profileKeys}); nothing is read until asked. */
	PersonalBests(@Nullable ConfigManager configManager, List<String> profileKeys)
	{
		this.configManager = configManager;
		this.profileKeys = profileKeys;
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
					keys.add(withPrefix(key));
				}
			}
		}
		return List.copyOf(keys);
	}

	/**
	 * Formatted fastest time for a panel boss across team sizes, each the time {@link #records} picks (the one the
	 * sync sends, so your card and killclog.com agree), or null when none recorded.
	 */
	@Nullable
	String pbText(String bossName)
	{
		Map<String, Double> seconds = new LinkedHashMap<>();
		records(bossName).forEach((key, record) -> seconds.put(key, record.seconds));
		return labeled(seconds, bossName);
	}

	/**
	 * A boss's time for each team size: RuneLite's store, then the game's own records over it (the adventure log, the
	 * collection log's line and the kills seen), as {@link PbRecord#pick} ranks them. In each lane, the profile
	 * RuneLite chose for the account speaks for every team size it has; the ones it ignores (it logs "multiple
	 * matching profiles") fill only the rest, their fastest. The one place the card and the sync choose a PB.
	 */
	Map<String, PbRecord> records(String bossName)
	{
		Map<String, PbRecord> out = new LinkedHashMap<>();
		List<Kept> times = kept().getOrDefault(encounter(bossName), List.of());
		String base = syncBase(bossName);
		for (Lane lane : Lane.values())
		{
			Map<String, Kept> chosenBest = new HashMap<>();
			Map<String, Kept> othersBest = new HashMap<>();
			for (Kept time : times)
			{
				if (time.lane == lane)
				{
					(time.profile.equals(chosen) ? chosenBest : othersBest).merge(time.variant, time,
						(a, b) -> b.seconds < a.seconds ? b : a);
				}
			}
			othersBest.forEach(chosenBest::putIfAbsent);
			chosenBest.keySet().stream().sorted(VARIANT_ORDER).forEach(variant ->
				out.merge(base + variant, record(chosenBest.get(variant)), PbRecord::pick));
		}
		return out;
	}

	/** A looked-up player's synced times for a boss, by team size, from the names they arrived under. */
	static Map<String, Double> variantSeconds(Map<String, Double> stored, String bossName)
	{
		Map<String, Double> best = new HashMap<>();
		String boss = encounter(bossName);
		stored.forEach((key, seconds) ->
		{
			Matcher variant = VARIANT.matcher(key.toLowerCase(Locale.ROOT));
			if (seconds != null && seconds > 0 && variant.matches() && encounter(variant.group(1)).equals(boss))
			{
				best.merge(variant.group(2) != null ? variant.group(2) : "", seconds, Math::min);
			}
		});
		Map<String, Double> out = new LinkedHashMap<>();
		String base = syncBase(bossName);
		best.keySet().stream().sorted(VARIANT_ORDER).forEach(variant -> out.put(base + variant, best.get(variant)));
		return out;
	}

	/**
	 * The fastest variant's time; a team size's carries it, "(5 players) 22:13.80", which the card reads as
	 * killclog.com does, "PB (5 players): 22:13.80" ({@link ClogHelper#pbLabel}). On a tie the team size wins.
	 */
	@Nullable
	static String labeled(Map<String, Double> variants, String bossName)
	{
		Map.Entry<String, Double> best = null;
		for (Map.Entry<String, Double> variant : variants.entrySet())
		{
			best = best == null || variant.getValue() <= best.getValue() ? variant : best;
		}
		if (best == null)
		{
			return null;
		}
		String team = best.getKey().substring(syncBase(bossName).length()).trim();
		String time = formatSeconds(best.getValue());
		return team.isEmpty() ? time : "(" + Character.toUpperCase(team.charAt(0)) + team.substring(1) + ") " + time;
	}

	/**
	 * The one name every lane's spelling of a boss reaches: the panel's ("Theatre of Blood: Hard Mode"), RuneLite's
	 * store ("theatre of blood hard mode"), a kill count message's, an adventure log section's and a collection log
	 * page's. Lowercase, a mode's colon gone, a leading "the" gone, then pb-names.tsv for true renames.
	 */
	static String encounter(String name)
	{
		String key = name.toLowerCase(Locale.ROOT).trim().replace(": ", " ");
		key = ALIASES.getOrDefault(key, key);
		key = key.startsWith("the ") ? key.substring("the ".length()) : key;
		return ALIASES.getOrDefault(key, key);
	}

	/** The name the sync and the server have always filed a boss's times under: lowercase, a mode's colon gone. */
	static String syncBase(String bossName)
	{
		return bossName.toLowerCase(Locale.ROOT)
			.replace(": challenge mode", " challenge mode")
			.replace(": hard mode", " hard mode")
			.replace(": expert mode", " expert mode")
			.replace(": entry mode", " entry mode");
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

	/** Every lane's times on every profile, listed once per lane and profile; any thread. */
	private Map<String, List<Kept>> kept()
	{
		if (kept != null)
		{
			return kept;
		}
		kept = new HashMap<>();
		if (configManager == null || profileKeys.isEmpty())
		{
			return kept;
		}
		chosen = withPrefix(configManager.getRSProfileKey());
		for (Lane lane : Lane.values())
		{
			for (String profile : profileKeys)
			{
				for (String key : configManager.getRSProfileConfigurationKeys(lane.group, profile, lane.prefix))
				{
					Double seconds = configManager.getConfiguration(lane.group, profile, key,
						(java.lang.reflect.Type) double.class);
					Matcher variant = VARIANT.matcher(key.substring(lane.prefix.length()));
					if (seconds != null && seconds > 0 && variant.matches())
					{
						kept.computeIfAbsent(encounter(variant.group(1)), boss -> new ArrayList<>()).add(new Kept(lane,
							profile, key.substring(lane.prefix.length()), variant.group(2) != null ? variant.group(2) : "",
							seconds));
					}
				}
			}
		}
		return kept;
	}

	/** A kept time as the sync sends it; a kill's time with the labels Kill Clog kept beside it. */
	private PbRecord record(Kept time)
	{
		return time.lane != Lane.LIVE ? new PbRecord(time.seconds, time.lane.source)
			: PbRecord.live(time.seconds, configManager.getConfiguration(time.lane.group, time.profile,
				KillLog.TAG_PREFIX + time.key));
	}

	// The plain key first, then solo, then each team size by its players.
	private static int players(String variant)
	{
		if (variant.isEmpty())
		{
			return -2;
		}
		Matcher count = DIGITS.matcher(variant);
		return count.find() ? Integer.parseInt(count.group()) : -1;
	}

	@Nullable
	private static String withPrefix(@Nullable String key)
	{
		return key == null || key.startsWith("rsprofile.") ? key : "rsprofile." + key;
	}
}
