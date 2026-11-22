package com.killclog;

import java.util.*;
import java.util.regex.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * Reads personal bests from the POH Adventure Log's Counters scroll.
 *
 * The log opens in one of two menu interfaces, and the player's interface-style setting decides which: the original
 * ({@code MENU}) or the newer ({@code MENU_NEW}). RuneLite's own reader watches only the original, so a player on the
 * newer style has no bests beyond what kill messages recorded since install. This reads both.
 *
 * Times go to Kill Clog's own profile store, never RuneLite's "personalbest" keys, which stay RuneLite's to write,
 * and the sync sends them as source "advlog".
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class AdvLogPbs
{
	/* package */ static final String CONFIG_GROUP = "killclog";

	/**
	 * The harvest's timing, in the two stages RuneLite's Chat Commands uses: a menu load names the log's owner and the
	 * Counters scroll load triggers the parse a tick later, once its children are populated. The owner
	 * is scoped to one POH visit, so a friend's log can never linger and gate a later harvest.
	 */
	static final class Watch
	{
		private boolean titleLoaded;
		private boolean countersLoaded;
		private String owner;

		/** Both menu interfaces are watched: the interface-style setting decides which hosts the log. */
		void onWidgetLoaded(int groupId)
		{
			if (groupId == InterfaceID.MENU_NEW || groupId == InterfaceID.MENU)
			{
				titleLoaded = true;
			}
			else if (groupId == InterfaceID.JOURNALSCROLL)
			{
				countersLoaded = true;
			}
		}

		void dropOwner()
		{
			owner = null;
		}

		void onTick(Client client, ConfigManager configManager)
		{
			if (titleLoaded)
			{
				titleLoaded = false;
				// The new menu hosts more than the Adventure Log; another title leaves the owner as-is.
				String read = readOwner(client);
				if (read != null)
				{
					owner = read;
				}
			}
			if (countersLoaded)
			{
				countersLoaded = false;
				net.runelite.api.Player local = client.getLocalPlayer();
				if (local != null && sameName(local.getName(), owner))
				{
					new AdvLogPbs(configManager).harvest(client);
				}
				else
				{
					// Someone else's house, or the title never resolved: "not yours", not "found nothing".
					log.debug("adventure log counters skipped: owner '{}' is not the local player", owner);
				}
			}
		}
	}
	/* package */ static final String KEY_PREFIX = "advlogpb.";

	private static final Pattern TITLE_PATTERN = Pattern.compile("The Exploits of (.+)");
	// Chat Commands' pattern for an Adventure Log PB line, as it writes it: an "Overall time" or "(former)" line
	// matches nothing, and neither does "-", a boss with no time.
	private static final String TEAM_SIZES = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";
	private static final Pattern PB_PATTERN = Pattern.compile(
		"Fastest (?:kill|run|Room time)(?: - \\(Team size: \\(?" + TEAM_SIZES + "\\)\\)?)?: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	private final ConfigManager configManager;

	/**
	 * The Adventure Log's owner, or null when the loaded menu isn't an Adventure Log. Both menu interfaces are read (the
	 * class note); the unused one costs a null widget. The title sits at a different child index in each, among null
	 * and non-text children, so both are scanned for the first matching line.
	 */
	static String readOwner(Client client)
	{
		String owner = readOwnerFrom(client, InterfaceID.MenuNew.TITLE);
		if (owner != null)
		{
			log.debug("adventure log owner '{}' read from the newer menu interface", owner);
			return owner;
		}
		owner = readOwnerFrom(client, InterfaceID.Menu.LJ_LAYER2);
		if (owner != null)
		{
			log.debug("adventure log owner '{}' read from the original menu interface", owner);
		}
		return owner;
	}

	/** Scan one container (its own text, then its children) for the title. */
	private static String readOwnerFrom(Client client, int componentId)
	{
		Widget container = client.getWidget(componentId);
		if (container == null)
		{
			return null;
		}
		String own = matchOwner(container.getText());
		if (own != null)
		{
			return own;
		}
		Widget[] children = container.getChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			if (child == null)
			{
				continue;
			}
			String found = matchOwner(child.getText());
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	/* package */ static String matchOwner(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		Matcher matcher = TITLE_PATTERN.matcher(Text.removeTags(raw));
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * Whether the log is the local player's, by their names as names compare everywhere here; Chat Commands compares
	 * the raw text, tags and non-breaking spaces included.
	 */
	static boolean sameName(String localName, String owner)
	{
		return localName != null && owner != null
			&& Text.standardize(localName).equals(Text.standardize(owner));
	}

	/**
	 * Reads the Counters scroll into PB keys and their seconds. Lines arrive with their tags stripped. It walks as Chat
	 * Commands does (a section's title, then its PB lines until a blank one) and names team sizes as Chat Commands
	 * stores them ("1 player" is solo, "2 player" is "2 players").
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

	/** A scroll section title as the reader names its boss ({@link PersonalBests#encounter}). */
	/* package */ static String canonicalSection(String title)
	{
		return PersonalBests.encounter(title);
	}

	/** Chat Commands' time parser: mm:ss(.f) and h:mm:ss(.f). */
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
	 * Read the loaded Counters scroll and keep every PB it holds, the faster of it and the one already kept. Returns
	 * how many were new or faster. Client thread (widgets and config).
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
		Map<String, Double> parsed = parseCounters(text);
		int recorded = 0;
		for (Map.Entry<String, Double> entry : parsed.entrySet())
		{
			if (record(entry.getKey(), entry.getValue()))
			{
				recorded++;
			}
		}
		// Both counts, always: a second visit rightly records nothing (the time kept is already as fast), and a bare
		// zero would look the same as a scroll that was never read.
		log.debug("adventure log harvest: {} lines, parsed {} pbs, {} new or improved",
			text.length, parsed.size(), recorded);
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
}
