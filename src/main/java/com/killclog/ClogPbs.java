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
 * The game sends a page's numbers in the client cycle it draws the page or the one before, and sends nothing for a
 * page with no record or for a value the varp already holds. Two rows clicked inside one tick are answered
 * together: both pages draw in the same cycle or a cycle apart, after both pages' numbers are in, and the game's
 * own header shows the second page's numbers on the first. Nothing in the timing says whose numbers are whose
 * then, so a page reads only when it is the only page newly drawn for QUIET cycles either side. Its value is the
 * line's one landing in the cycle it drew or the cycle before, still the line's last when the wait ends. A page
 * that got no value, a redraw, a value that lands at any other moment, and two values for one line take nothing:
 * otherwise one page's time is filed under the page beside it.
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
	// The client cycles that must separate two newly drawn pages for either to be read. Pages answered together draw
	// at most one cycle apart; pages clicked in different ticks about thirty.
	/* package */ static final int QUIET = 5;
	// A page still waiting after this many cycles was never settled by a tick (a logout): its numbers are dropped.
	private static final int STALE = 100;

	private final ConfigManager configManager;
	// Hands a page count read off the player's own page (label, count) to their log.
	private final BiConsumer<String, Integer> countRead;
	// The player's own count for a label, as their log holds it; null for none.
	private final Function<String, Integer> heldCount;
	private String shownPage;
	// Each line's last value as it landed: {cycle, value, the cycle of the landing before it}.
	private final int[][] landed = new int[VARPS.length][3];
	// The cycle the last new page drew in, and its readings, kept once it has stood alone for QUIET cycles.
	private int drawnAt = NONE;
	private final List<Runnable> waiting = new ArrayList<>();
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
		int cycle = client.getGameCycle();
		settle(cycle);
		String page = shownPage(client);
		if (page == null || page.equals(shownPage))
		{
			return;
		}
		String before = shownPage;
		shownPage = page;
		Integer[] drawnBefore = drawnCounts.clone();
		for (int slot = 0; slot < drawnCounts.length; slot++)
		{
			drawnCounts[slot] = client.getVarpValue(VARPS[COUNTS + slot]);
		}
		// A page drawn within QUIET cycles of the last one costs both their readings: that one's wait ends here.
		waiting.clear();
		boolean alone = cycle - drawnAt > QUIET;
		drawnAt = cycle;
		if (!alone)
		{
			return;
		}
		boolean timed = !UNSETTLED.contains(page.toLowerCase(Locale.ROOT));
		String key = ClogService.bossToCategory(page);
		for (Widget line : client.getWidget(InterfaceID.Collection.HEADER_TEXT).getDynamicChildren())
		{
			String text = Text.removeTags(line.getText());
			PageCounts.Line count = PageCounts.line(key, text);
			if (count != null)
			{
				read(COUNTS + count.slot, cycle, value ->
				{
					if (!countBefore(before, count.slot, value, drawnBefore))
					{
						countRead.accept(count.label, value);
					}
				});
			}
			else if (timed && text.startsWith("Personal Best Corrupted:"))
			{
				read(1, cycle, ticks -> record(AdvLogPbs.canonicalSection("The Corrupted Gauntlet"), ticks, before));
			}
			else if (timed && text.startsWith("Personal Best:"))
			{
				read(0, cycle, ticks -> record(AdvLogPbs.canonicalSection(page), ticks, before));
			}
		}
	}

	/**
	 * A line's value for the page drawn in this cycle, to keep once the page has stood alone: the line's only
	 * landing in this cycle or the one before, and only while it is still the line's last.
	 */
	private void read(int slot, int cycle, IntConsumer keep)
	{
		int[] line = landed[slot];
		int at = line[0];
		int value = line[1];
		if (at >= cycle - 1 && line[2] < cycle - 1)
		{
			waiting.add(() ->
			{
				if (line[0] == at)
				{
					keep.accept(value);
				}
			});
		}
	}

	/** Keeps the last new page's numbers once it has stood alone for QUIET cycles; each game tick and each draw. */
	void settle(int cycle)
	{
		int age = cycle - drawnAt;
		if (age > QUIET)
		{
			if (age < STALE)
			{
				waiting.forEach(Runnable::run);
			}
			waiting.clear();
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

	/** A varp changed (onVarbitChanged); client thread. A line's value landed, for a page the log draws about now. */
	void varpChanged(Client client, int varpId)
	{
		for (int slot = 0; slot < VARPS.length; slot++)
		{
			if (VARPS[slot] == varpId)
			{
				// A page whose wait is over keeps its numbers before this one lands: it is a later page's.
				settle(client.getGameCycle());
				int[] line = landed[slot];
				line[2] = line[0];
				line[0] = client.getGameCycle();
				line[1] = client.getVarpValue(varpId);
			}
		}
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
