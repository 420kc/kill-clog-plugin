package com.killclog;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 * Pins the bundled search flavor pools to the exact strings the plugin
 * shipped with in 2.3.0, when they lived in SearchMessages itself.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class SearchMessagesCatalogTest
{
	@Test
	public void poolsMatchGoldenTables()
	{
		assertArrayEquals(new String[]{
			"Search party for %s...",
			"Tracking down %s...",
			"On %s's trail...",
			"Searching for %s...",
			"Scouring for %s...",
			"Hot on %s's trail...",
			"APB out on %s..."
		}, SearchMessages.SEARCH);
		assertArrayEquals(new String[]{
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
			"%s is hiding from the hiscores"
		}, SearchMessages.NOT_FOUND);
		assertArrayEquals(new String[]{
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
			"Back to the log mines"
		}, SearchMessages.SELF);
		assertArrayEquals(new String[]{
			"Today's the day. GL %s.",
			"Main character energy",
			"You again? Nice."
		}, SearchMessages.SELF_RARE);
		assertArrayEquals(new String[]{
			"%s the legend",
			"One day this log will be full"
		}, SearchMessages.SELF_ULTRA);
		assertArrayEquals(new String[]{
			"Finding %s a rival...",
			"Scouting for %s...",
			"Pulling rival stats...",
			"Lining up a challenger...",
			"Sizing up the field..."
		}, SearchMessages.COMPARE_SEARCH);
		assertArrayEquals(new String[]{
			"No one showed for %s",
			"%s's rival is MIA",
			"%s wins by default.",
			"%s remains uncontested"
		}, SearchMessages.COMPARE_NOT_FOUND);
		assertArrayEquals(new String[]{
			"%s vs %s. Bold.",
			"%s's rival: %s",
			"Mirror match.",
			"%s challenges... %s?"
		}, SearchMessages.COMPARE_MIRROR);
		assertArrayEquals(new String[]{
			"You vs you. Classic.",
			"%s's rival: %s",
			"Know thyself, %s",
			"The real fight, %s"
		}, SearchMessages.COMPARE_SELF_MIRROR);
		assertArrayEquals(new String[]{
			"Good luck vs %s!",
			"%s vs %s: FIGHT!",
			"Think you can take %s?",
			"%s vs %s",
			"You vs %s. Let's see it."
		}, SearchMessages.COMPARE_SELF_BLUE);
		assertArrayEquals(new String[]{
			"Challenging yourself?",
			"Can't escape you, %s",
			"%s vs %s. Bold.",
			"Nowhere to hide, %s"
		}, SearchMessages.COMPARE_SELF_RED);
	}
}
