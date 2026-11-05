package com.killclog;

/**
 * Barbarian Assault's High-level Gambles, which the game's own Collection Log page shows above its items, as the
 * Colosseum's Glory leads Sol Heredit's card. No provider has the count: the player's client reads it, their sync
 * carries it, and killclog.com serves it to everyone else.
 */
final class HighGambles
{
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
}
