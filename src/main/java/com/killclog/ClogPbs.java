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
 * A page's time is only taken once the varp holds it. Moving to another page draws its header at once, and the
 * server's value for it can land after that draw, so the varp can still hold the last page's time (10/18: two
 * players' Alchemical Hydra times were their Amoxliatl times, the next page). So a new page takes the varp as it
 * draws only if the varp changed since the last page drew; otherwise it waits for the varp to change while it is
 * on screen, for a couple of ticks. Two pages with the same time read nothing on that visit, which is harmless.
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
	// How long a page waits for its time after drawing: the server's answer lands within a tick or two.
	/* package */ static final int WAIT_TICKS = 2;

	private final ConfigManager configManager;
	private String shownPage;
	private final int[] drawnTicks = {-1, -1};
	private final String[] waiting = new String[VARPS.length];
	private final int[] waitingSince = new int[VARPS.length];
	private final java.util.Set<String> cleared = new java.util.HashSet<>();

	ClogPbs(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The log redrew a page's header (script 2731); client thread. */
	void pageDrawn(Client client)
	{
		String page = shownPage(client);
		if (page == null)
		{
			return;
		}
		boolean newPage = !page.equals(shownPage);
		shownPage = page;
		if (newPage)
		{
			java.util.Arrays.fill(waiting, null);
		}
		if (UNSETTLED.contains(page.toLowerCase(Locale.ROOT)))
		{
			return;
		}
		for (Widget line : client.getWidget(InterfaceID.Collection.HEADER_TEXT).getDynamicChildren())
		{
			String text = Text.removeTags(line.getText());
			if (text.startsWith("Personal Best Corrupted:"))
			{
				line(client, 1, AdvLogPbs.canonicalSection("The Corrupted Gauntlet"), newPage);
			}
			else if (text.startsWith("Personal Best:"))
			{
				line(client, 0, AdvLogPbs.canonicalSection(page), newPage);
			}
		}
	}

	/** A varp changed (onVarbitChanged); client thread. A page waiting for its time takes it, if still on screen. */
	void varpChanged(Client client, int varpId)
	{
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			if (VARPS[slot] != varpId || waiting[slot] == null)
			{
				continue;
			}
			String key = waiting[slot];
			waiting[slot] = null;
			int ticks = client.getVarpValue(varpId);
			drawnTicks[slot] = ticks;
			if (client.getTickCount() - waitingSince[slot] <= WAIT_TICKS && shownPage != null
				&& shownPage.equals(shownPage(client)))
			{
				record(key, ticks);
			}
		}
	}

	/** One PB line of the page just drawn: its time now if the varp is this page's, else wait for it. */
	private void line(Client client, int slot, String key, boolean newPage)
	{
		int ticks = client.getVarpValue(VARPS[slot]);
		boolean ours = ticks > 0 && (!newPage || ticks != drawnTicks[slot]);
		drawnTicks[slot] = ticks;
		if (ours)
		{
			waiting[slot] = null;
			record(key, ticks);
		}
		else
		{
			waiting[slot] = key;
			waitingSince[slot] = client.getTickCount();
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
