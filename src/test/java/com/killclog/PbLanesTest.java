package com.killclog;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Which time a sync sends for one key. The game's records (the adventure log Kill Clog read off the player's own
 * screen, the collection log's line, a kill seen) outrank RuneLite's file; between them the faster is the newer.
 */
public class PbLanesTest
{
	private static final String KEY = "tombs of amascut expert mode 6 players";

	private static String after(SyncService.DetailedPb... lanes)
	{
		Map<String, SyncService.DetailedPb> out = new HashMap<>();
		out.put(KEY, lanes[0]);
		for (int i = 1; i < lanes.length; i++)
		{
			PublicationCoordinator.putGameRecord(out, KEY, lanes[i]);
		}
		return out.get(KEY).seconds + " " + out.get(KEY).source;
	}

	private static SyncService.DetailedPb pb(double seconds, String source)
	{
		return new SyncService.DetailedPb(seconds, source);
	}

	@Test
	public void theAdventureLogTakesRuneLitesTimeEvenWhenSlower()
	{
		// RuneLite's file had a 7-player run filed under 6; the game's own log said 29:58.80.
		assertEquals("1798.8 advlog", after(pb(1634.4, "store"), pb(1798.8, "advlog")));
		assertEquals("101.4 advlog", after(pb(101.0, "store"), pb(101.4, "advlog")));
	}

	@Test
	public void betweenTheGamesRecordsTheFasterWinsAndTheLaterLaneTakesATie()
	{
		assertEquals("88.8 advlog", after(pb(88.8, "advlog"), pb(90.0, "clog")));
		assertEquals("88.8 clog", after(pb(88.8, "advlog"), pb(88.8, "clog")));
		assertEquals("87.6 clog", after(pb(88.8, "advlog"), pb(87.6, "clog")));
		assertEquals("88.8 live", after(pb(88.8, "store"), pb(88.8, "advlog"), pb(88.8, "clog"), pb(88.8, "live")));
	}

	@Test
	public void aWholeSecondLeavesTheSameTimeWithItsTenthsAlone()
	{
		assertEquals("66.4 store", after(pb(66.4, "store"), pb(66.0, "advlog")));
		assertEquals("66.0 advlog", after(pb(66.0, "store"), pb(66.0, "advlog")));
		assertEquals("more than a second apart is another time", "66.0 advlog",
			after(pb(64.8, "store"), pb(66.0, "advlog")));
	}
}
