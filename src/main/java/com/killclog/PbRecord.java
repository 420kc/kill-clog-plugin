package com.killclog;

import java.util.Locale;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * One variant-keyed PB, where this client saw it and, from a kill Kill Clog saw, what the game showed at that kill.
 * Which record wins for a key is decided here and nowhere else.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PbRecord
{
	/** Where a time came from. RuneLite's file is the one that isn't the game's own record. */
	enum Source
	{
		STORE, ADVLOG, CLOG, LIVE;

		String wire()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}

	final double seconds;
	final Source source;
	Integer team;
	Integer scale;
	String timer;
	Integer kc;

	/** A kill's labels as KillLog keeps them beside its time. */
	static String tag(double seconds, String team, String scale, String timer, String kc)
	{
		return seconds + "|" + team + "|" + scale + "|" + timer + "|" + kc;
	}

	/** This live time with the labels a kept tag gives it, when the tag is for this same time and reads. */
	static PbRecord live(double seconds, @Nullable String tag)
	{
		PbRecord pb = new PbRecord(seconds, Source.LIVE);
		String[] parts = tag != null ? tag.split("\\|", -1) : new String[0];
		try
		{
			if (parts.length == 5 && Double.parseDouble(parts[0]) == seconds)
			{
				pb.team = number(parts[1]);
				pb.scale = number(parts[2]);
				pb.timer = parts[3];
				pb.kc = number(parts[4]);
			}
		}
		catch (NumberFormatException e)
		{
			// A label that doesn't read is skipped: the time still syncs, unlabeled.
			return new PbRecord(seconds, Source.LIVE);
		}
		return pb;
	}

	boolean labeled()
	{
		return timer != null;
	}

	/**
	 * The record that wins a key. The collection log's line is the game's current record, read from the server
	 * exactly, so it wins whenever this client has read it: an older, faster time elsewhere can't outlast a record
	 * the game has since changed. RuneLite's file fills only what nothing has, the profile RuneLite chose first. A
	 * game record replaces the file's even when slower, except that a whole second leaves a file time it rounds from
	 * alone: the same time with its tenths, shown without them. Between game records, faster wins and the later takes a tie.
	 */
	static PbRecord pick(@Nullable PbRecord kept, PbRecord seen)
	{
		if (kept == null)
		{
			return seen;
		}
		if ((kept.source == Source.CLOG) != (seen.source == Source.CLOG))
		{
			return kept.source == Source.CLOG ? kept : seen;
		}
		if (seen.source == Source.STORE)
		{
			return kept;
		}
		if (kept.source == Source.STORE)
		{
			return sameTimeInTenths(kept.seconds, seen.seconds) ? kept : seen;
		}
		return seen.seconds <= kept.seconds ? seen : kept;
	}

	/**
	 * Whether a whole second is this tenths time as the game shows it with precise timing off: rounded to the nearest
	 * second, half up (its formatter, client script 2410), so a time on the 0.6 s tick grid is never more than 0.4 s
	 * from what it shows. "0:32" is 31.8 or 32.4; 32.6 shows as "0:33".
	 */
	private static boolean sameTimeInTenths(double tenths, double whole)
	{
		return whole == Math.rint(whole) && tenths != Math.rint(tenths) && Math.abs(tenths - whole) < 0.45;
	}

	@Nullable
	private static Integer number(String text)
	{
		return text.isEmpty() ? null : Integer.valueOf(text);
	}
}
