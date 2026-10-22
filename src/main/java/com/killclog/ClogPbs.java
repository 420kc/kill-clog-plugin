package com.killclog;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * The Collection Log's Personal Best line, the game's own record, read as the player browses their log. The server
 * sends each page's PB to a varp in game ticks, exact whatever the player's timing setting, and the page header
 * shows it. Only the player's own log is read, only when the page has the line, and the time replaces whatever was
 * kept for it: it is the game's current record. The sync sends it as source "clog".
 *
 * The server sends a page's time to the varp just before it draws the page, a client cycle ahead, and 0 for a page
 * with no record; a value the varp already holds isn't sent again (10/22, logged click by click).
 * So a page takes the time only when it is newly on screen and the varp changed just before it drew: a page that got
 * no value, a redraw of the page on screen, and a value that lands at any other moment take nothing. 2.7.1 let a
 * page with no record wait for a value, so the next page's, landing a cycle before that page drew, was filed under it
 * (tribrd's Gauntlet 13:27.6 as their Sol Heredit, the page before), and a redraw took the varp as it stood. Missing a
 * reading costs nothing (the kills still say the record); two pages with the same time, or a page opened again, read
 * nothing on that visit.
 *
 * Times read this way are kept under their own keys ("pagepb."): the ones 2.6.3 and earlier kept ("clogpb.") may be
 * another page's, so they are never read or sent again, and each account's are cleared the first time it reads a
 * page. A page shows its time again when it is next opened.
 */
final class ClogPbs
{
	/* package */ static final String KEY_PREFIX = "pagepb.";
	/* package */ static final String OLD_PREFIX = "clogpb.";

	// Pages whose one line doesn't yet say which encounter it times (the Nightmare's or Phosani's; which delve).
	private static final Set<String> UNSETTLED = Set.of("the nightmare", "doom of mokhaiotl");
	// The page's own line, and the Gauntlet page's second one for the Corrupted Gauntlet.
	private static final int[] VARPS = {VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT,
		VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2};
	// How many client cycles a page's time can land before its draw: one in every logged click.
	/* package */ static final int ARRIVAL_CYCLES = 5;

	private final ConfigManager configManager;
	private String shownPage;
	// The client cycle each line's varp last changed, until a draw uses it; -1 when no draw may.
	private final int[] arrived = {-1, -1};
	private final java.util.Set<String> cleared = new java.util.HashSet<>();

	ClogPbs(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The log redrew a page's header (script 2731); client thread. */
	void pageDrawn(Client client)
	{
		// Whatever this draw does, a value that landed before it is used up: no later draw takes it.
		boolean[] sent = new boolean[VARPS.length];
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			int since = client.getGameCycle() - arrived[slot];
			sent[slot] = arrived[slot] >= 0 && since >= 0 && since <= ARRIVAL_CYCLES;
			arrived[slot] = -1;
		}
		String page = shownPage(client);
		if (page == null)
		{
			return;
		}
		boolean newPage = !page.equals(shownPage);
		shownPage = page;
		if (!newPage || UNSETTLED.contains(page.toLowerCase(Locale.ROOT)))
		{
			return;
		}
		for (Widget line : client.getWidget(InterfaceID.Collection.HEADER_TEXT).getDynamicChildren())
		{
			String text = Text.removeTags(line.getText());
			if (text.startsWith("Personal Best Corrupted:") && sent[1])
			{
				record(AdvLogPbs.canonicalSection("The Corrupted Gauntlet"), client.getVarpValue(VARPS[1]));
			}
			else if (text.startsWith("Personal Best:") && sent[0])
			{
				record(AdvLogPbs.canonicalSection(page), client.getVarpValue(VARPS[0]));
			}
		}
	}

	/** A varp changed (onVarbitChanged); client thread. A PB time, for the page the log draws next. */
	void varpChanged(Client client, int varpId)
	{
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			if (VARPS[slot] == varpId)
			{
				arrived[slot] = client.getGameCycle();
			}
		}
	}

	/** The page the log's header shows, when it is the player's own log; null otherwise. */
	private static String shownPage(Client client)
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget[] lines = header != null ? header.getDynamicChildren() : null;
		if (lines == null || lines.length < 2 || client.getLocalPlayer() == null || ManualClogSync.isHostLog(client))
		{
			return null;
		}
		String page = Text.removeTags(lines[0].getText()).trim();
		return page.isEmpty() ? null : page;
	}

	private void record(String key, int ticks)
	{
		if (ticks > 0)
		{
			clearOldTimes();
			configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, ticks * 6 / 10.0);
		}
	}

	/** The account's times from before the page check, once a session: they are never read, so they only go. */
	private void clearOldTimes()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return;
		}
		profile = profile.startsWith("rsprofile.") ? profile : "rsprofile." + profile;
		if (!cleared.add(profile))
		{
			return;
		}
		for (String key : configManager.getRSProfileConfigurationKeys(AdvLogPbs.CONFIG_GROUP, profile, OLD_PREFIX))
		{
			configManager.unsetConfiguration(AdvLogPbs.CONFIG_GROUP, profile, key);
		}
	}
}
