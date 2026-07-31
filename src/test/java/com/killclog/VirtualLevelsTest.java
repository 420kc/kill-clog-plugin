package com.killclog;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Virtual level display math for the skill summaries. Kill Clog follows
 * RuneLite's core Virtual Levels plugin toggle; when it is on, levels come
 * from xp via Experience.getLevelForXp (cap 126), matching what that plugin
 * shows on the vanilla skills tab.
 */
public class VirtualLevelsTest
{
	@Test
	public void offMeansHiscoreLevelUntouched()
	{
		assertEquals(99, ClogHelper.displayLevel(99, 200_000_000L, false));
	}

	@Test
	public void exactly13mXpStaysNinetyNine()
	{
		// 13,034,431 is the level 99 threshold; virtual only grows past it.
		assertEquals(99, ClogHelper.displayLevel(99, 13_034_431L, true));
	}

	@Test
	public void maxXpIsVirtual126()
	{
		assertEquals(126, ClogHelper.displayLevel(99, 200_000_000L, true));
	}

	@Test
	public void midVirtualRangeComputesFromXp()
	{
		// 14,391,160 xp is the level 100 threshold.
		assertEquals(100, ClogHelper.displayLevel(99, 14_391_160L, true));
	}

	@Test
	public void unrankedSkillKeepsHiscoreValue()
	{
		// No xp on record: never invent a virtual level.
		assertEquals(-1, ClogHelper.displayLevel(-1, -1L, true));
		assertEquals(85, ClogHelper.displayLevel(85, 0L, true));
	}

	@Test
	public void belowNinetyNineIsIdenticalEitherWay()
	{
		// getLevelForXp agrees with the hiscore level below 99, so the
		// toggle changes nothing for unmaxed skills.
		long xp70 = 737_627L;
		assertEquals(ClogHelper.displayLevel(70, xp70, false),
			ClogHelper.displayLevel(70, xp70, true));
	}

	@Test
	public void virtualTotalSumsFromXp()
	{
		// All 24 skills at 200m xp: 24 x 126 = 3024 virtual, 2376 real.
		HiscoreResult result = allMaxed();
		assertEquals(2376, ClogHelper.displayTotalLevel(result, false));
		assertEquals(3024, ClogHelper.displayTotalLevel(result, true));
	}

	@Test
	public void virtualTotalNeverDropsBelowRealTotal()
	{
		// A result whose per-skill maps are empty (hiscores hid the skills)
		// still shows at least the real total the overall row carried.
		HiscoreResult result = new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
			java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
			1500, 10_000_000L, 100, 1);
		assertEquals(1500, ClogHelper.displayTotalLevel(result, true));
	}

	private static HiscoreResult allMaxed()
	{
		java.util.Map<String, Integer> levels = new java.util.LinkedHashMap<>();
		java.util.Map<String, Long> xps = new java.util.LinkedHashMap<>();
		for (net.runelite.api.Skill s : net.runelite.api.Skill.values())
		{
			levels.put(s.getName().toLowerCase(), 99);
			xps.put(s.getName().toLowerCase(), 200_000_000L);
		}
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
			levels, java.util.Map.of(), xps,
			2376, 4_800_000_000L, 126, 1);
	}
}
