package com.killclog;

import java.util.*;
import javax.annotation.Nullable;

/**
 * The game's counts that only a Collection Log page shows and no hiscore has: Revenant, Tormented Demon and Demonic
 * Gorilla kills, the Hunter Guild's rumours and the raids' Entry completions (Dylan, 11/6; the lines are
 * page-counts.tsv, so another is a row). {@link ClogPbs} reads them off the page as it opens, by its timing rule,
 * guarded against the page before's number as a PB is, and each reading replaces the last: the game's count only
 * grows, but a reading can still be another page's, and the page's next reading of a new number replaces it where
 * keeping the highest never could (the game sends a count only when it changes, so that can wait for the count to
 * move). The player's sync carries the counts read since killclog.com last took one, and killclog.com serves them,
 * as with {@link HighGambles}.
 */
final class PageCounts
{
	/** Their object in the sync and in killclog.com's reply, label to count. */
	static final String FIELD = "page_counts";
	/** The Hunter Guild's page, whose rumours the Hunter skill card shows too. */
	static final String HUNTER_GUILD = "hunter_guild";

	/** One count line: its page's key, the varp slot that carries it (0 to 2), its label, and a raid card's label. */
	static final class Line
	{
		final String page;
		final int slot;
		final String label;
		@Nullable
		final String card;

		private Line(String[] row)
		{
			page = row[0];
			slot = Integer.parseInt(row[1]) - 1;
			label = row[2];
			card = "-".equals(row[3]) ? null : row[3];
		}
	}

	static final List<Line> LINES = lines();

	private PageCounts()
	{
	}

	private static List<Line> lines()
	{
		List<Line> lines = new ArrayList<>();
		for (String[] row : CatalogTsv.rows(PageCounts.class, "page-counts.tsv", 4))
		{
			lines.add(new Line(row));
		}
		return Collections.unmodifiableList(lines);
	}

	/** A page's own count line, the one its card leads with (not a raid's Entry line), or null for none. */
	@Nullable
	static Line ownLine(String page)
	{
		for (Line line : LINES)
		{
			if (line.page.equals(page) && line.card == null)
			{
				return line;
			}
		}
		return null;
	}

	/** The line a page's header text is, or null when it's none Kill Clog reads. */
	@Nullable
	static Line line(String page, String text)
	{
		for (Line line : LINES)
		{
			if (line.page.equals(page) && text.startsWith(line.label + ":"))
			{
				return line;
			}
		}
		return null;
	}

	/**
	 * A page's card with its count, once the log carries it: a raid's card takes it on its own line under the raid's
	 * (the Combat Summary never does), any other page's card leads with it, as the game's page does.
	 */
	@Nullable
	static TooltipData onCard(String page, @Nullable TooltipData data, @Nullable ClogResult clog)
	{
		if (data == null || clog == null)
		{
			return data;
		}
		for (Line line : LINES)
		{
			Integer count = clog.getPageCounts().get(line.label);
			if (line.page.equals(page) && count != null)
			{
				return line.card != null ? data.toBuilder().entryLabel(line.card).entryKc(count).build()
					: data.toBuilder().kcLabel(line.label + ": ").kc(count).build();
			}
		}
		return data;
	}

	/**
	 * A Collection Log page's own card on either side, leading with the count the game's page shows: the gambles'
	 * (HighGambles) or one of these, from that player's log.
	 */
	@Nullable
	static TooltipData pageCard(String page, @Nullable TooltipData data, @Nullable ClogResult clog)
	{
		return onCard(page, HighGambles.onPage(page, data, clog), clog);
	}
}
