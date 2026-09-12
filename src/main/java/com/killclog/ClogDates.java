package com.killclog;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

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
				? LocalDateTime.parse(value, LOCAL).toInstant(ZoneOffset.UTC) : Instant.parse(value);
			return time.isBefore(EARLIEST) || time.isAfter(Instant.now().plusSeconds(300))
				? null : time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
	}

	static String local(String value)
	{
		String iso = iso(value);
		return iso == null ? null : LOCAL.format(Instant.parse(iso).atOffset(ZoneOffset.UTC));
	}
}
