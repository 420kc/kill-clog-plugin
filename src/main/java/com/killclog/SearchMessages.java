package com.killclog;

import java.awt.Color;

/** Search status message pools: lookup, comparison, not-found, and self flavor text. */
final class SearchMessages
{
	private SearchMessages()
	{
	}

	static final String INVALID_NAME = "That name didn't seem to take.";

	static final String[] SEARCH = {
		"Search party for %s...",
		"Tracking down %s...",
		"On %s's trail...",
		"Searching for %s...",
		"Scouring for %s...",
		"Hot on %s's trail...",
		"APB out on %s...",
	};

	static final String[] NOT_FOUND = {
		"WANTED: %s",
		"%s has gone AWOL",
		"%s is touching grass",
		"%s? Never heard of 'em.",
		"%s who?",
		"Seen %s? I haven't...",
		"404: %s not found",
		"%s remains at large",
		"Couldn't find %s. Tragic.",
		"%s last seen at Doom. RIP.",
		"%s probably got pk'd.",
		"%s escaped the database",
		"%s left no footprints",
		"%s is off the grid",
		"%s failed the vibe check",
		"No clog trail for %s",
		"%s is hiding from the hiscores",
	};

	static final String[] SELF = {
		"Oh hey it's you again",
		"%s returns for more",
		"Welcome back %s",
		"The usual?",
		"Looking good, %s",
		"Back for more?",
		"Miss me?",
		"%s checks in",
		"The legend returns",
		"%s checks the receipts",
		"Still got it, %s",
		"Let's see the damage",
		"Back to the log mines",
	};

	static final String[] SELF_RARE = {
		"Today's the day. GL %s.",
		"Main character energy",
		"You again? Nice.",
	};

	static final String[] SELF_ULTRA = {
		"%s the legend",
		"One day this log will be full",
	};

	static final Color SELF_COLOR = new Color(0x4c, 0xaf, 0x6e);

	static final String[] COMPARE_SEARCH = {
		"Finding %s a rival...",
		"Scouting for %s...",
		"Pulling rival stats...",
		"Lining up a challenger...",
		"Sizing up the field...",
	};

	static final String[] COMPARE_NOT_FOUND = {
		"No one showed for %s",
		"%s's rival is MIA",
		"%s wins by default.",
		"%s remains uncontested",
	};

	static final String[] COMPARE_MIRROR = {
		"%s vs %s. Bold.",
		"%s's rival: %s",
		"Mirror match.",
		"%s challenges... %s?",
	};

	static final String[] COMPARE_SELF_MIRROR = {
		"You vs you. Classic.",
		"%s's rival: %s",
		"Know thyself, %s",
		"The real fight, %s",
	};

	static final String[] COMPARE_SELF_BLUE = {
		"Good luck vs %s!",
		"%s vs %s: FIGHT!",
		"Think you can take %s?",
		"%s vs %s",
		"You vs %s. Let's see it.",
	};

	static final String[] COMPARE_SELF_RED = {
		"Challenging yourself?",
		"Can't escape you, %s",
		"%s vs %s. Bold.",
		"Nowhere to hide, %s",
	};
}
