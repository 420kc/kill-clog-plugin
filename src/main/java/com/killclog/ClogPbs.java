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
 * The Collection Log's Personal Best line, the game's own record, read as the player browses their log. On each
 * page draw the server sends that page's PB to a varp in game ticks, exact whatever the player's timing setting,
 * and the page header shows it. Only the player's own log is read, only when the page has the line, and the time
 * replaces whatever was kept for it: it is the game's current record. The sync sends it as source "clog".
 */
final class ClogPbs
{
	/* package */ static final String KEY_PREFIX = "clogpb.";

	// Pages whose one line doesn't yet say which encounter it times (the Nightmare's or Phosani's; which delve).
	private static final Set<String> UNSETTLED = Set.of("the nightmare", "doom of mokhaiotl");

	private final ConfigManager configManager;

	ClogPbs(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The log redrew a page's header (script 2731); client thread. */
	void pageDrawn(Client client)
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget[] lines = header != null ? header.getDynamicChildren() : null;
		if (lines == null || lines.length < 2 || client.getLocalPlayer() == null || ManualClogSync.isHostLog(client))
		{
			return;
		}
		String page = Text.removeTags(lines[0].getText()).trim();
		if (page.isEmpty() || UNSETTLED.contains(page.toLowerCase(Locale.ROOT)))
		{
			return;
		}
		for (Widget line : lines)
		{
			String text = Text.removeTags(line.getText());
			if (text.startsWith("Personal Best Corrupted:"))
			{
				record(AdvLogPbs.canonicalSection("The Corrupted Gauntlet"),
					client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2));
			}
			else if (text.startsWith("Personal Best:"))
			{
				record(AdvLogPbs.canonicalSection(page), client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT));
			}
		}
	}

	private void record(String key, int ticks)
	{
		if (ticks > 0)
		{
			configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, ticks * 6 / 10.0);
		}
	}
}
