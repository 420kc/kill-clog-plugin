package com.killclog;

import javax.annotation.Nullable;

/**
 * Barbarian Assault's High-level Gambles, which the game's own Collection Log page shows above its items, as the
 * Colosseum's Glory leads Sol Heredit's card. No provider has the count: the player's client reads it, their sync
 * carries it, and killclog.com serves it to everyone else.
 */
final class HighGambles
{
	static final String CATEGORY = "barbarian_assault";
	static final String LABEL = "High-level Gambles: ";
	/** Its name in the sync's score map. */
	static final String ACTIVITY = "Barbarian Assault High-level Gambles";
	/** Its field in killclog.com's reply. */
	static final String FIELD = "high_gambles";
	// killclog.com's ceiling for a synced score; anything past it is no count.
	private static final int MAX = 10_000_000;

	private HighGambles()
	{
	}

	static int plausible(int gambles)
	{
		return gambles >= 0 && gambles <= MAX ? gambles : -1;
	}

	/** A player's count, or -1 when their log doesn't carry it. */
	static int of(@Nullable ClogResult clog)
	{
		return clog != null ? clog.getHighGambles() : -1;
	}

	/** The Barbarian Assault page's card leads with the count, as the game's page does, once the log carries it. */
	@Nullable
	static TooltipData onPage(String page, @Nullable TooltipData data, @Nullable ClogResult clog)
	{
		int gambles = of(clog);
		return data != null && CATEGORY.equals(page) && gambles >= 0
			? data.toBuilder().kcLabel(LABEL).kc(gambles).build() : data;
	}
}
