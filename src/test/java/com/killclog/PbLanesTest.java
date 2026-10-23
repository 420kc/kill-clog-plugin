package com.killclog;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Which time a sync sends for one key. The collection log's line is the game's current record and wins whenever
 * it was read. The game's other records (the adventure log Kill Clog read off the player's own screen, a kill seen)
 * outrank RuneLite's file; between them the faster is the newer.
 */
public class PbLanesTest
{
	private static final String KEY = "tombs of amascut expert mode 6 players";

	private static String after(PbRecord... lanes)
	{
		Map<String, PbRecord> out = new HashMap<>();
		out.put(KEY, lanes[0]);
		for (int i = 1; i < lanes.length; i++)
		{
			out.merge(KEY, lanes[i], PbRecord::pick);
		}
		return out.get(KEY).seconds + " " + out.get(KEY).source.wire();
	}

	private static PbRecord pb(double seconds, String source)
	{
		return new PbRecord(seconds, PbRecord.Source.valueOf(source.toUpperCase(java.util.Locale.ROOT)));
	}

	@Test
	public void theAdventureLogTakesRuneLitesTimeEvenWhenSlower()
	{
		// RuneLite's file had a 7-player run filed under 6; the game's own log said 29:58.80.
		assertEquals("1798.8 advlog", after(pb(1634.4, "store"), pb(1798.8, "advlog")));
		assertEquals("101.4 advlog", after(pb(101.0, "store"), pb(101.4, "advlog")));
	}

	@Test
	public void theCollectionLogsLineIsTheGamesCurrentRecordAndWinsWheneverItWasRead()
	{
		// A record the game has since changed (a reset, a rework) can't outlast the line the server sends now.
		assertEquals("90.0 clog", after(pb(88.8, "advlog"), pb(90.0, "clog")));
		assertEquals("90.0 clog", after(pb(90.0, "clog"), pb(87.6, "live")));
		assertEquals("90.0 clog", after(pb(88.8, "store"), pb(90.0, "clog"), pb(88.2, "advlog")));
		assertEquals("87.6 clog", after(pb(88.8, "advlog"), pb(87.6, "clog")));
	}

	@Test
	public void betweenTheOtherGameRecordsTheFasterWinsAndTheLaterLaneTakesATie()
	{
		assertEquals("88.2 live", after(pb(88.8, "advlog"), pb(88.2, "live")));
		assertEquals("88.8 advlog", after(pb(88.8, "advlog"), pb(90.0, "live")));
		assertEquals("88.8 live", after(pb(88.8, "store"), pb(88.8, "advlog"), pb(88.8, "live")));
	}

	@Test
	public void aWholeSecondTheGameStatedConfirmsTheTimeWithTenthsItRoundsFrom()
	{
		// Precise timing off: the game says 1:06 of a record RuneLite's file keeps as 1:06.40. One record, the game's.
		assertEquals("66.4 advlog", after(pb(66.4, "store"), pb(66.0, "advlog")));
		assertEquals("whichever lane comes first", "66.4 advlog", after(pb(66.0, "advlog"), pb(66.4, "store")));
		assertEquals("66.0 advlog", after(pb(66.0, "store"), pb(66.0, "advlog")));
		assertEquals("more than a second apart is another time", "66.0 advlog",
			after(pb(64.8, "store"), pb(66.0, "advlog")));
		// The game rounds to the nearest second: 65.6 shows as 1:06, while 66.6 shows as 1:07 and 65.4 as 1:05.
		assertEquals("65.6 advlog", after(pb(65.6, "store"), pb(66.0, "advlog")));
		assertEquals("66.0 advlog", after(pb(66.6, "store"), pb(66.0, "advlog")));
		assertEquals("66.0 advlog", after(pb(65.4, "store"), pb(66.0, "advlog")));
		assertEquals("a file time no game record states stays the file's", "66.4 store",
			after(pb(66.4, "store"), pb(66.4, "store")));
	}

	@Test
	public void betweenGameRecordsTheSameRecordToldTwoWaysKeepsItsTenths()
	{
		// The adventure log read 1:19.20; a kill with precise timing off said 1:19. The kill's word, the log's tenths,
		// whichever came first; and the same the other way round, a kill's tenths and the log's whole second.
		assertEquals("79.2 live", after(pb(79.2, "advlog"), pb(79.0, "live")));
		assertEquals("79.2 live", after(pb(79.0, "live"), pb(79.2, "advlog")));
		assertEquals("79.2 live", after(pb(79.0, "advlog"), pb(79.2, "live")));
		assertEquals("79.2 live", after(pb(79.2, "live"), pb(79.0, "advlog")));
		// A kill's labels ride along at the exact time.
		PbRecord kill = pb(79.0, "live");
		kill.kc = 412;
		kill.timer = "kill";
		Map<String, PbRecord> out = new HashMap<>();
		out.put(KEY, pb(79.2, "store"));
		out.merge(KEY, kill, PbRecord::pick);
		assertEquals(79.2, out.get(KEY).seconds, 0);
		assertEquals(PbRecord.Source.LIVE, out.get(KEY).source);
		assertEquals(Integer.valueOf(412), out.get(KEY).kc);
		assertEquals("kill", out.get(KEY).timer);
		// The kill seen before the adventure log's tenths keeps its labels too (QA, 10/23).
		PbRecord first = pb(79.0, "live");
		first.kc = 412;
		first.timer = "kill";
		PbRecord picked = PbRecord.pick(first, pb(79.2, "advlog"));
		assertEquals(79.2, picked.seconds, 0);
		assertEquals(PbRecord.Source.LIVE, picked.source);
		assertEquals(Integer.valueOf(412), picked.kc);
		assertEquals("kill", picked.timer);
	}
}
