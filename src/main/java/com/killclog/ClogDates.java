package com.killclog;

import java.time.*;
import java.time.format.*;
import java.util.*;

/** Acquisition dates are UTC; a sync receipt is never an acquisition date. */
final class ClogDates
{
	private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
		.withResolverStyle(ResolverStyle.STRICT);
	private static final Instant EARLIEST = Instant.parse("2013-02-22T00:00:00Z");

	static String iso(String value)
	{
		if (value == null) return null;
		try
		{
			Instant time = value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
				? LocalDateTime.parse(value, LOCAL).toInstant(ZoneOffset.UTC) : OffsetDateTime.parse(value).toInstant();
			return time.isBefore(EARLIEST) || time.isAfter(Instant.now().plusSeconds(300))
				? null : time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
		}
		catch (RuntimeException ignored)
		{
			// A date in neither shape is no date at all: a wrong acquisition date is worse than none.
			return null;
		}
	}

	// TempleOSRS dates an item when it first hears of it, so everything its first import or a catch-up brings (its
	// plugin off for a while, then on) shares that one second, never when any of them came (Dylan 11/7: 1,143 of 420
	// kc's items carry Temple's first import, and turning its plugin back on dated 12 at once, two of which then led
	// his Recent as today's). Five or more items sharing a second is that shape.
	static final int CHECK_BATCH = 5;

	/** The moments (UTC ISO) a provider gave five or more different items: imports and catch-ups, not acquisitions. */
	static Set<String> checkStamps(Map<String, List<ClogResult.ClogItem>> items)
	{
		Map<String, Set<Integer>> byMoment = new HashMap<>();
		for (List<ClogResult.ClogItem> list : items.values())
		{
			for (ClogResult.ClogItem item : list)
			{
				String iso = iso(item.getDate());
				if (iso != null)
				{
					byMoment.computeIfAbsent(iso, k -> new HashSet<>()).add(item.getId());
				}
			}
		}
		Set<String> stamps = new HashSet<>();
		byMoment.forEach((moment, ids) ->
		{
			if (ids.size() >= CHECK_BATCH)
			{
				stamps.add(moment);
			}
		});
		return stamps;
	}

	/** Whether a date is one of a provider's import or catch-up stamps. */
	static boolean isCheckStamp(String date, Set<String> stamps)
	{
		String iso = iso(date);
		return iso != null && stamps.contains(iso);
	}

	static String local(String value)
	{
		String iso = iso(value);
		return iso == null ? null : LOCAL.format(Instant.parse(iso).atOffset(ZoneOffset.UTC));
	}

	/** A stored UTC date on the player's own clock: an evening unlock keeps its evening's day. */
	static LocalDateTime here(String value)
	{
		String iso = iso(value);
		return iso == null ? null : LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault());
	}
}
