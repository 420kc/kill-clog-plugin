package com.killclog;

import java.util.*;
import java.util.function.*;
import javax.annotation.*;
import net.runelite.api.Client;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * The Collection Log's Personal Best line: the game's own record, read as the player browses their own log and sent
 * with a sync as source "clog". The page header shows it. The value comes from a varp in game ticks, exact whatever
 * the player's timing setting, and replaces whatever was kept for that page.
 *
 * The game sends a page's numbers one client cycle before it draws the page, and sends nothing for a page with no
 * record or for a value the varp already holds. Two pages clicked inside one tick are sent a cycle apart and drawn
 * a cycle apart, so the first is drawn after the second's numbers are in, and the game's own header shows them on
 * it. So a page newly on screen takes the value that landed in the cycle before it drew, as it landed, once, and
 * never the varp as it stands at the draw. A page that got no value, a redraw, a value that lands in any other
 * cycle, and two values for one line in one cycle take nothing: otherwise the next page's time is filed under the
 * page before it.
 *
 * As a second check, a page also never takes the time the page
 * shown before it holds, read from its page, its kills or the Adventure Log. It looks one page back, at times
 * already held. Two neighbours with truly the same time read the second on a later visit. A missed reading costs
 * nothing: the kills still say the record.
 *
 * A page count (PageCounts) is read by the same two guards.
 *
 * Times are kept under "pagepb.". The older "clogpb." keys could hold another page's time, so they are never read or
 * sent, and each account's are cleared the first time it reads a page.
 */
final class ClogPbs
{
	/* package */ static final String KEY_PREFIX = "pagepb.";
	/* package */ static final String OLD_PREFIX = "clogpb.";

	// Pages whose Personal Best line doesn't yet say which encounter it times (the Nightmare's or Phosani's; which
	// delve): their times are never read, their counts are.
	private static final Set<String> UNSETTLED = Set.of("the nightmare", "doom of mokhaiotl");
	// The page's own line, and the Gauntlet page's second one for the Corrupted Gauntlet; then the page's three count
	// lines, which PageCounts reads by the same timing rule.
	private static final int[] VARPS = {VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT,
		VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2, VarPlayerID.COLLECTION_CATEGORY_COUNT,
		VarPlayerID.COLLECTION_CATEGORY_COUNT2, VarPlayerID.COLLECTION_CATEGORY_COUNT3};
	private static final int COUNTS = 2;
	private static final int NONE = -1;

	private final ConfigManager configManager;
	// Hands a page count read off the player's own page (label, count) to their log.
	private final BiConsumer<String, Integer> countRead;
	// The player's own count for a label, as their log holds it; null for none.
	private final Function<String, Integer> heldCount;
	private String shownPage;
	// Each line's last two values as they landed, until a page takes one: {cycle, value, the cycle before, its value}.
	private final int[][] landed = new int[VARPS.length][4];
	// Each count line's varp as the page shown last stood when it drew; null before any page.
	private final Integer[] drawnCounts = new Integer[VARPS.length - COUNTS];
	private final java.util.Set<String> cleared = new java.util.HashSet<>();

	ClogPbs(ConfigManager configManager, BiConsumer<String, Integer> countRead, Function<String, Integer> heldCount)
	{
		this.configManager = configManager;
		this.countRead = countRead;
		this.heldCount = heldCount;
		for (int[] line : landed)
		{
			Arrays.fill(line, NONE);
		}
	}

	/** The log redrew a page's header (script 2731); client thread. */
	void pageDrawn(Client client)
	{
		String page = shownPage(client);
		if (page == null || page.equals(shownPage))
		{
			return;
		}
		String before = shownPage;
		shownPage = page;
		// What each line was sent for this page: the varps themselves may already hold the next page's.
		int[] sent = new int[VARPS.length];
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			sent[slot] = sentIn(slot, client.getGameCycle() - 1);
		}
		Integer[] drawnBefore = drawnCounts.clone();
		for (int slot = 0; slot < drawnCounts.length; slot++)
		{
			int own = sent[COUNTS + slot];
			drawnCounts[slot] = own != NONE ? own : client.getVarpValue(VARPS[COUNTS + slot]);
		}
		boolean timed = !UNSETTLED.contains(page.toLowerCase(Locale.ROOT));
		String key = ClogService.bossToCategory(page);
		for (Widget line : client.getWidget(InterfaceID.Collection.HEADER_TEXT).getDynamicChildren())
		{
			String text = Text.removeTags(line.getText());
			PageCounts.Line count = PageCounts.line(key, text);
			if (count != null && sent[COUNTS + count.slot] != NONE)
			{
				int value = sent[COUNTS + count.slot];
				if (!countBefore(before, count.slot, value, drawnBefore))
				{
					countRead.accept(count.label, value);
				}
			}
			else if (timed && text.startsWith("Personal Best Corrupted:"))
			{
				record(AdvLogPbs.canonicalSection("The Corrupted Gauntlet"), sent[1], before);
			}
			else if (timed && text.startsWith("Personal Best:"))
			{
				record(AdvLogPbs.canonicalSection(page), sent[0], before);
			}
		}
	}

	/**
	 * Whether the page shown before holds this time, from its page, its kills or the Adventure Log, the same record
	 * shown without its tenths (precise timing off) included.
	 */
	private boolean heldBefore(@Nullable String before, double seconds)
	{
		if (before == null)
		{
			return false;
		}
		String key = AdvLogPbs.canonicalSection(before);
		for (String prefix : new String[]{KEY_PREFIX, KillLog.KEY_PREFIX, AdvLogPbs.KEY_PREFIX})
		{
			Double held = configManager.getRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, prefix + key, double.class);
			if (held != null && PbRecord.sameRecord(held, seconds))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a count is the page shown before's, as the PB guard asks of a time: the number that line's varp stood at
	 * when that page drew, or that page's own count in the same line as the player's log holds it.
	 */
	private boolean countBefore(@Nullable String before, int slot, int value, Integer[] drawnBefore)
	{
		if (drawnBefore[slot] != null && drawnBefore[slot] == value)
		{
			return true;
		}
		String key = before != null ? ClogService.bossToCategory(before) : null;
		for (PageCounts.Line line : PageCounts.LINES)
		{
			if (line.page.equals(key) && line.slot == slot && Objects.equals(heldCount.apply(line.label), value))
			{
				return true;
			}
		}
		return false;
	}

	/** A varp changed (onVarbitChanged); client thread. A line's value, for the page the log draws next cycle. */
	void varpChanged(Client client, int varpId)
	{
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			int[] line = landed[slot];
			if (VARPS[slot] != varpId)
			{
				continue;
			}
			if (line[0] == client.getGameCycle())
			{
				// A second value in one cycle: nothing says which page each was sent for.
				line[1] = NONE;
				continue;
			}
			line[2] = line[0];
			line[3] = line[1];
			line[0] = client.getGameCycle();
			line[1] = client.getVarpValue(varpId);
		}
	}

	/** The value a line was sent in a cycle, for one page only; NONE when nothing landed then, or two values did. */
	private int sentIn(int slot, int cycle)
	{
		int[] line = landed[slot];
		for (int at = 0; at < line.length; at += 2)
		{
			if (line[at] == cycle)
			{
				line[at] = NONE;
				return line[at + 1];
			}
		}
		return NONE;
	}

	/** The page the log's header shows, when it is the player's own log; null otherwise. */
	private static String shownPage(Client client)
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget[] lines = header != null ? header.getDynamicChildren() : null;
		if (lines == null || lines.length < 2 || client.getLocalPlayer() == null || ClogWalk.isHostLog(client))
		{
			return null;
		}
		String page = Text.removeTags(lines[0].getText()).trim();
		return page.isEmpty() ? null : page;
	}

	private void record(String key, int ticks, @Nullable String before)
	{
		double seconds = ticks * 6 / 10.0;
		if (ticks > 0 && !heldBefore(before, seconds))
		{
			clearOldTimes();
			configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, seconds);
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
