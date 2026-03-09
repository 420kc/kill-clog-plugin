package com.killclog;

import java.awt.Color;

/** Search status message pools — searching, not-found, and self-lookup flavor text. */
final class SearchMessages
{
	private SearchMessages()
	{
	}

	static final String[] SEARCH = {
		"Throwing a search party for %s...",
		"Moving mountains to find %s...",
		"Deliberating on %s's whereabouts...",
		"Searching high and low for %s...",
		"Leaving no stone unturned for %s...",
		"Hot on the trail of %s...",
		"Scouring Gielinor for %s...",
		"Putting out an APB on %s...",
	};

	static final String[] NOT_FOUND = {
		"WANTED: %s",
		"%s has gone AWOL",
		"%s is touching grass",
		"%s? Never heard of 'em.",
		"%s who?",
		"Have you seen %s? I haven't...",
		"404: %s not found",
		"%s remains at large",
		"Couldn't find %s. Tragic.",
		"%s was last seen at Doom. RIP.",
		"%s probably got pk'd.",
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
}
