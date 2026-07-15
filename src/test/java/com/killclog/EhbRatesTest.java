package com.killclog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * EHB rates resource integrity and computation. Rate values are read from the
 * bundled TempleOSRS table rather than hardcoded, so a rates refresh cannot
 * silently break the suite.
 */
public class EhbRatesTest
{
	private static HiscoreResult result(AccountType type, Map<String, Integer> kcs)
	{
		return new HiscoreResult(type, HiscoreTable.STANDARD, kcs, new HashMap<>(),
			new HashMap<>(), new HashMap<>(), new HashMap<>(), 0, 0, 0, -1);
	}

	/** A boss Temple rates 0 for mains but positive for ironmen, or null. */
	private static String mainZeroIronPositiveBoss()
	{
		for (Map.Entry<String, double[]> e : EhbRates.rates().entrySet())
		{
			if (e.getValue()[0] == 0 && e.getValue()[1] > 0)
			{
				return e.getKey();
			}
		}
		return null;
	}

	@Test
	public void testEveryHiscoreBossHasRates()
	{
		Map<String, double[]> rates = EhbRates.rates();
		for (String boss : HiscoreService.bossNames())
		{
			double[] pair = rates.get(boss);
			assertNotNull("No EHB rates entry for " + boss, pair);
			assertEquals(2, pair.length);
			assertTrue(boss + " main rate negative", pair[0] >= 0);
			assertTrue(boss + " ironman rate negative", pair[1] >= 0);
		}
	}

	@Test
	public void testNoUnknownBossesInRates()
	{
		Set<String> known = new HashSet<>(Arrays.asList(HiscoreService.bossNames()));
		for (String boss : EhbRates.rates().keySet())
		{
			assertTrue("Rates entry is not a hiscore boss: " + boss, known.contains(boss));
		}
	}

	@Test
	public void testComputeSumsKcOverRate()
	{
		double vorkRate = EhbRates.rates().get("Vorkath")[0];
		double zulrahRate = EhbRates.rates().get("Zulrah")[0];
		assertTrue(vorkRate > 0 && zulrahRate > 0);

		Map<String, Integer> kcs = new HashMap<>();
		kcs.put("Vorkath", 340);
		kcs.put("Zulrah", 92);
		double expected = 340 / vorkRate + 92 / zulrahRate;
		assertEquals(expected,
			EhbRates.compute(result(AccountType.REGULAR, kcs), null), 1e-9);
	}

	@Test
	public void testZeroRateBossAwardsNothingOnMainButCountsForIronman()
	{
		String split = mainZeroIronPositiveBoss();
		if (split == null)
		{
			return;
		}
		Map<String, Integer> kcs = new HashMap<>();
		kcs.put(split, 1000);
		assertEquals(0, EhbRates.compute(result(AccountType.REGULAR, kcs), null), 1e-9);
		double imRate = EhbRates.rates().get(split)[1];
		assertEquals(1000 / imRate,
			EhbRates.compute(result(AccountType.IRONMAN, kcs), null), 1e-9);
	}

	@Test
	public void testResolvedGroupIronmanOutranksRegularHiscoreTag()
	{
		// GIMs only appear on the regular hiscores: the result arrives tagged
		// REGULAR and the provider-resolved type must still pick the ironman
		// column, or a GIM's Barrows-shaped hours silently vanish.
		String split = mainZeroIronPositiveBoss();
		if (split == null)
		{
			return;
		}
		Map<String, Integer> kcs = new HashMap<>();
		kcs.put(split, 1000);
		HiscoreResult regularTagged = result(AccountType.REGULAR, kcs);
		double imRate = EhbRates.rates().get(split)[1];
		assertEquals(1000 / imRate,
			EhbRates.compute(regularTagged, AccountType.GROUP_IRONMAN), 1e-9);
	}

	@Test
	public void testEveryIronmanModeUsesIronmanRates()
	{
		Map<String, Integer> kcs = new HashMap<>();
		kcs.put("Vorkath", 340);
		HiscoreResult regularTagged = result(AccountType.REGULAR, kcs);
		double im = EhbRates.compute(regularTagged, AccountType.IRONMAN);
		for (AccountType type : AccountType.values())
		{
			if (type == AccountType.REGULAR)
			{
				continue;
			}
			assertEquals("Ironman mode " + type + " diverged from ironman rates",
				im, EhbRates.compute(regularTagged, type), 1e-9);
		}
	}

	@Test
	public void testUnrankedKcAwardsNothing()
	{
		Map<String, Integer> kcs = new HashMap<>();
		kcs.put("Vorkath", -1);
		assertEquals(0, EhbRates.compute(result(AccountType.REGULAR, kcs), null), 1e-9);
	}

	@Test
	public void testNullResultIsAbsent()
	{
		assertEquals(-1, EhbRates.compute(null, null), 1e-9);
	}
}
