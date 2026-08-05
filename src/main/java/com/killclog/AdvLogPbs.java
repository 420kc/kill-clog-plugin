package com.killclog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * Harvests personal bests from the POH Adventure Log's Counters scroll.
 * Vanilla's own harvest has been dead since the Adventure Log menu moved to
 * the new menu interface (it still watches the old one), so live kill-timer
 * messages are the only pbs RuneLite records today; this reads the scroll the
 * way vanilla intended and backfills every boss the player has not killed
 * since installing. Times land in Kill Clog's OWN rs-profile store - never
 * vanilla's "personalbest" namespace, which stays vanilla's to write - and
 * the sync payload tags them "advlog".
 */
final class AdvLogPbs
{
	/* package */ static final String CONFIG_GROUP = "killclog";
	/* package */ static final String KEY_PREFIX = "advlogpb.";

	private static final Pattern TITLE_PATTERN = Pattern.compile("The Exploits of (.+)");
	// Vanilla's adventure-log pb line shape, verbatim: "Overall time" and
	// "(former)" lines intentionally fall through, and "-" (no time) never
	// matches the time group.
	private static final String TEAM_SIZES = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";
	private static final Pattern PB_PATTERN = Pattern.compile(
		"Fastest (?:kill|run|Room time)(?: - \\(Team size: \\(?" + TEAM_SIZES + "\\)\\)?)?: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	/**
	 * Scroll section titles whose stored pb key differs beyond the article and
	 * colon shapes {@link PersonalBests#keyCandidates} already bridges. Values
	 * are vanilla's own store names (lowercased), so a scroll harvest and a
	 * live kill-timer message land on the same key.
	 */
	private static final Map<String, String> SCROLL_RENAMES = buildScrollRenames();

	private static Map<String, String> buildScrollRenames()
	{
		Map<String, String> renames = new LinkedHashMap<>();
		renames.put("tzhaar fight cave", "tztok-jad");
		renames.put("inferno", "tzkal-zuk");
		renames.put("fortis colosseum", "sol heredit");
		renames.put("lunar chests", "lunar chest");
		renames.put("doom", "doom of mokhaiotl");
		renames.put("chambers of xeric - challenge mode", "chambers of xeric challenge mode");
		renames.put("theatre of blood - hard mode", "theatre of blood hard mode");
		renames.put("theatre of blood - entry mode", "theatre of blood entry mode");
		renames.put("theatre of blood - story mode", "theatre of blood entry mode");
		renames.put("tombs of amascut - entry", "tombs of amascut entry mode");
		renames.put("tombs of amascut - expert", "tombs of amascut expert mode");
		return renames;
	}

	private final ConfigManager configManager;

	AdvLogPbs(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/**
	 * The Adventure Log owner from the new menu interface's title layer, or
	 * null when the loaded menu is not an Adventure Log. The title text is a
	 * dynamic child surrounded by null and non-text children, so the scan
	 * takes the first match.
	 */
	static String readOwner(Client client)
	{
		Widget title = client.getWidget(InterfaceID.MenuNew.TITLE);
		if (title == null || title.getChildren() == null)
		{
			return null;
		}
		for (Widget child : title.getChildren())
		{
			if (child == null || child.getText() == null)
			{
				continue;
			}
			Matcher matcher = TITLE_PATTERN.matcher(Text.removeTags(child.getText()));
			if (matcher.find())
			{
				return matcher.group(1);
			}
		}
		return null;
	}

	/** Owner gate with the nbsp/tag hygiene vanilla's raw compare lacks. */
	static boolean sameName(String localName, String owner)
	{
		return localName != null && owner != null
			&& Text.standardize(localName).equals(Text.standardize(owner));
	}

	/**
	 * Parse the Counters scroll into variant pb keys -> seconds. Lines arrive
	 * tag-stripped; the walk is vanilla's own (section title, then pb lines
	 * until a blank), with team sizes normalized exactly the way vanilla
	 * stores them ("1 player" -> solo, "2 player" -> "2 players").
	 */
	/* package */ static Map<String, Double> parseCounters(String[] text)
	{
		Map<String, Double> out = new LinkedHashMap<>();
		for (int i = 0; i < text.length; ++i)
		{
			String boss = canonicalSection(text[i]);
			for (i = i + 1; i < text.length; ++i)
			{
				String line = text[i];
				if (line.isEmpty())
				{
					break;
				}
				Matcher matcher = PB_PATTERN.matcher(line);
				if (!matcher.find())
				{
					continue;
				}
				double seconds = timeToSeconds(matcher.group("time"));
				String teamSize = matcher.group("teamsize");
				String key = boss;
				if (teamSize != null)
				{
					if (teamSize.equals("1 player"))
					{
						teamSize = "Solo";
					}
					else if (teamSize.endsWith("player"))
					{
						teamSize = teamSize + "s";
					}
					key = boss + " " + teamSize.toLowerCase(Locale.ROOT);
				}
				// Duplicate sections exist in real scrolls; keep the fastest.
				Double prev = out.get(key);
				if (seconds > 0 && (prev == null || seconds < prev))
				{
					out.put(key, seconds);
				}
			}
		}
		return out;
	}

	/**
	 * A scroll section title in the pb store's key space: lowercase, leading
	 * article dropped (vanilla's store names do - "The Nightmare" lives under
	 * "nightmare"), the mode-raid separator collapsed, true renames applied.
	 * Whatever shape survives, the gather probes both article forms via
	 * {@link PersonalBests#keyCandidates}, so the lanes converge either way.
	 */
	/* package */ static String canonicalSection(String title)
	{
		String key = title.toLowerCase(Locale.ROOT).trim();
		String renamed = SCROLL_RENAMES.get(key);
		if (renamed != null)
		{
			return renamed;
		}
		if (key.startsWith("the "))
		{
			key = key.substring("the ".length());
		}
		return key
			.replace(": challenge mode", " challenge mode")
			.replace(": hard mode", " hard mode")
			.replace(": expert mode", " expert mode")
			.replace(": entry mode", " entry mode");
	}

	/** Vanilla's time parser: mm:ss(.f) and h:mm:ss(.f). */
	/* package */ static double timeToSeconds(String time)
	{
		String[] s = time.split(":");
		if (s.length == 2)
		{
			return Integer.parseInt(s[0]) * 60 + Double.parseDouble(s[1]);
		}
		else if (s.length == 3)
		{
			return Integer.parseInt(s[0]) * 60 * 60 + Integer.parseInt(s[1]) * 60 + Double.parseDouble(s[2]);
		}
		return Double.parseDouble(time);
	}

	/**
	 * Read the loaded Counters scroll and persist every parsed pb, min-wins
	 * per key. Returns how many keys were recorded or improved. Client thread
	 * (widget + config access).
	 */
	int harvest(Client client)
	{
		Widget parent = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
		if (parent == null || parent.getStaticChildren() == null)
		{
			return 0;
		}
		Widget[] children = parent.getStaticChildren();
		String[] text = new String[children.length];
		for (int i = 0; i < children.length; i++)
		{
			String raw = children[i] != null ? children[i].getText() : null;
			text[i] = raw != null ? Text.removeTags(raw) : "";
		}
		int recorded = 0;
		for (Map.Entry<String, Double> entry : parseCounters(text).entrySet())
		{
			if (record(entry.getKey(), entry.getValue()))
			{
				recorded++;
			}
		}
		return recorded;
	}

	/** Min-wins write into the plugin's own store; true when it changed. */
	private boolean record(String key, double seconds)
	{
		Double prev = configManager.getRSProfileConfiguration(
			CONFIG_GROUP, KEY_PREFIX + key, double.class);
		if (prev != null && prev > 0 && prev <= seconds)
		{
			return false;
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_PREFIX + key, seconds);
		return true;
	}

	/**
	 * Every advlog-harvested variant for a panel boss, canonical-keyed - the
	 * same fragment sweep and candidate merge the vanilla-store gather uses.
	 */
	Map<String, Double> variantSecondsAcrossProfiles(List<String> profileKeys, String panelBossName)
	{
		if (profileKeys.isEmpty())
		{
			return PersonalBests.variantSeconds(key -> configManager.getRSProfileConfiguration(
				CONFIG_GROUP, KEY_PREFIX + key, double.class), panelBossName);
		}
		return PersonalBests.variantSeconds(key ->
		{
			Double best = null;
			for (String profileKey : profileKeys)
			{
				Double pb = configManager.getConfiguration(
					CONFIG_GROUP, profileKey, KEY_PREFIX + key, (java.lang.reflect.Type) double.class);
				if (pb != null && pb > 0 && (best == null || pb < best))
				{
					best = pb;
				}
			}
			return best;
		}, panelBossName);
	}
}
