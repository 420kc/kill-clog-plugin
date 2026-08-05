package com.killclog;

import java.util.Map;
import net.runelite.client.util.Text;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdvLogPbsTest
{
	/**
	 * Vanilla's own Counters fixture (condensed), run through the same
	 * tag-strip the harvest applies. Every assertion below matches what
	 * vanilla's working harvest stored for these exact lines.
	 */
	@Test
	public void parseCountersMatchesVanillaHarvest()
	{
		String[] raw = {
			"Chompy Hunting",
			"Kills: <col=ffffff>1,003</col>",
			"Rank: <col=ffffff>Ogre Expert</col>",
			"",
			"TzHaar Fight Cave",
			"Fastest run: <col=ffffff>33:53</col>",
			"",
			"Inferno",
			"Fastest run: <col=ffffff>2:02:20</col>",
			"",
			"Zulrah",
			"Fastest kill: <col=ffffff>0:47</col>",
			"",
			"Galvek",
			"Fastest kill: <col=ffffff>-</col>",
			"",
			"Nightmare",
			"Fastest kill: <col=ffffff>-</col>",
			"",
			"The Nightmare",
			"Fastest kill - (Team size: 6+ players): <col=ffffff>3:22</col>",
			"",
			"The Nightmare",
			"Fastest kill - (Team size: 6+ players): <col=ffffff>3:22</col>",
			"",
			"Chambers of Xeric",
			"Fastest run - (Team size: Solo): <col=ffffff>28:07</col>",
			"Fastest run - (Team size: 2 players): <col=ffffff>24:40</col>",
			"",
			"Chambers of Xeric - Challenge mode",
			"Fastest run - (Team size: 3 players): <col=ffffff>45:41</col>",
			"",
			"Theatre of Blood",
			"Fastest Room time (former): <col=ffffff>18:45</col>",
			"Fastest Wave time (former): <col=ffffff>22:01</col>",
			"Fastest Room time - (Team size: (1 player): <col=ffffff>1:01:57.00</col>",
			"Fastest Overall time - (Team size: 1 player): <col=ffffff>1:06:40.20</col>",
			"Fastest Room time - (Team size: (2 player): <col=ffffff>22:43.80</col>",
			"Fastest Overall time - (Team size: 2 player): <col=ffffff>27:36.60</col>",
			"",
			"Tombs of Amascut - Entry",
			"Fastest Room time - (Team size: Solo): <col=ffffff>32:53</col>",
			"Fastest Overall time - (Team size: Solo): <col=ffffff>39:06</col>",
			"",
			"Tombs of Amascut - Expert",
			"Fastest Room time - (Team size: Solo): <col=ffffff>37:43</col>",
			"",
			"Tempoross",
			"Fastest run: <col=ffffff>3:54</col>",
			"",
			"Barbarian Assault",
			"High-level gambles: <col=ffffff>0</col>",
			"",
			"Fremennik spirits rested: <col=ffffff>0</col>",
		};
		String[] text = new String[raw.length];
		for (int i = 0; i < raw.length; i++)
		{
			text[i] = Text.removeTags(raw[i]);
		}

		Map<String, Double> pbs = AdvLogPbs.parseCounters(text);

		assertEquals(2033.0, pbs.get("tztok-jad"), 0.001);
		assertEquals(7340.0, pbs.get("tzkal-zuk"), 0.001);
		assertEquals(47.0, pbs.get("zulrah"), 0.001);
		assertEquals(202.0, pbs.get("nightmare 6+ players"), 0.001);
		assertEquals(1687.0, pbs.get("chambers of xeric solo"), 0.001);
		assertEquals(1480.0, pbs.get("chambers of xeric 2 players"), 0.001);
		assertEquals(2741.0, pbs.get("chambers of xeric challenge mode 3 players"), 0.001);
		// Room time is the pb; "1 player" normalizes to solo, "2 player"
		// grows its plural, and the slower Overall/former lines never match.
		assertEquals(3717.0, pbs.get("theatre of blood solo"), 0.001);
		assertEquals(1363.8, pbs.get("theatre of blood 2 players"), 0.001);
		assertEquals(1973.0, pbs.get("tombs of amascut entry mode solo"), 0.001);
		assertEquals(2263.0, pbs.get("tombs of amascut expert mode solo"), 0.001);
		assertEquals(234.0, pbs.get("tempoross"), 0.001);
		// "-" (no time yet) never records.
		assertFalse(pbs.containsKey("galvek"));
		assertFalse(pbs.containsKey("nightmare"));
		// KC-only sections contribute nothing.
		assertFalse(pbs.containsKey("chompy hunting"));
		assertEquals(12, pbs.size());
	}

	@Test
	public void formerAndOverallLinesNeverRecord()
	{
		Map<String, Double> pbs = AdvLogPbs.parseCounters(new String[]{
			"Theatre of Blood",
			"Fastest Room time (former): 18:45",
			"Fastest Overall time - (Team size: 1 player): 1:06:40.20",
		});
		assertTrue(pbs.isEmpty());
	}

	@Test
	public void canonicalSectionMapsScrollTitles()
	{
		// True renames land on vanilla's own store names.
		assertEquals("tztok-jad", AdvLogPbs.canonicalSection("TzHaar Fight Cave"));
		assertEquals("tzkal-zuk", AdvLogPbs.canonicalSection("Inferno"));
		assertEquals("sol heredit", AdvLogPbs.canonicalSection("Fortis Colosseum"));
		assertEquals("lunar chest", AdvLogPbs.canonicalSection("Lunar Chests"));
		assertEquals("doom of mokhaiotl", AdvLogPbs.canonicalSection("Doom"));
		// The scroll's dash separators and the chat colon shape both collapse.
		assertEquals("chambers of xeric challenge mode",
			AdvLogPbs.canonicalSection("Chambers of Xeric - Challenge mode"));
		assertEquals("chambers of xeric challenge mode",
			AdvLogPbs.canonicalSection("Chambers of Xeric: Challenge Mode"));
		assertEquals("tombs of amascut entry mode",
			AdvLogPbs.canonicalSection("Tombs of Amascut - Entry"));
		assertEquals("tombs of amascut expert mode",
			AdvLogPbs.canonicalSection("Tombs of Amascut - Expert"));
		assertEquals("theatre of blood hard mode",
			AdvLogPbs.canonicalSection("Theatre of Blood - Hard Mode"));
		// Leading articles drop the way vanilla's store names do.
		assertEquals("whisperer", AdvLogPbs.canonicalSection("The Whisperer"));
		assertEquals("nightmare", AdvLogPbs.canonicalSection("The Nightmare"));
		assertEquals("corrupted gauntlet", AdvLogPbs.canonicalSection("The Corrupted Gauntlet"));
		// Unknown titles otherwise pass through untouched.
		assertEquals("vardorvis", AdvLogPbs.canonicalSection("Vardorvis"));
	}

	@Test
	public void timeToSecondsParsesEveryScrollShape()
	{
		assertEquals(47.0, AdvLogPbs.timeToSeconds("0:47"), 0.001);
		assertEquals(2033.0, AdvLogPbs.timeToSeconds("33:53"), 0.001);
		assertEquals(7340.0, AdvLogPbs.timeToSeconds("2:02:20"), 0.001);
		assertEquals(1363.8, AdvLogPbs.timeToSeconds("22:43.80"), 0.001);
		assertEquals(3717.0, AdvLogPbs.timeToSeconds("1:01:57.00"), 0.001);
	}

	@Test
	public void ownerGateSurvivesNbspAndRejectsStrangers()
	{
		// The in-game name renders its space as nbsp; the vanilla bug was
		// comparing those raw. Standardized compare heals it.
		assertTrue(AdvLogPbs.sameName("420 kc", "420 kc"));
		assertTrue(AdvLogPbs.sameName("420 kc", "420 kc"));
		assertFalse(AdvLogPbs.sameName("420 kc", "Someone Else"));
		assertFalse(AdvLogPbs.sameName("420 kc", null));
		assertFalse(AdvLogPbs.sameName(null, "420 kc"));
	}
}
