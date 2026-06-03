package com.killclog;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatAchievementTest
{
	@Test
	public void testHighestForPointsBoundaries()
	{
		// Current OSRS Combat Achievement thresholds.
		assertNull(CombatAchievementTier.highestForPoints(0));
		assertNull(CombatAchievementTier.highestForPoints(40));
		assertEquals(CombatAchievementTier.EASY, CombatAchievementTier.highestForPoints(41));
		assertEquals(CombatAchievementTier.EASY, CombatAchievementTier.highestForPoints(160));
		assertEquals(CombatAchievementTier.MEDIUM, CombatAchievementTier.highestForPoints(161));
		assertEquals(CombatAchievementTier.MEDIUM, CombatAchievementTier.highestForPoints(415));
		assertEquals(CombatAchievementTier.HARD, CombatAchievementTier.highestForPoints(416));
		assertEquals(CombatAchievementTier.ELITE, CombatAchievementTier.highestForPoints(1064));
		assertEquals(CombatAchievementTier.MASTER, CombatAchievementTier.highestForPoints(1904));
		// Grandmaster is completion-gated, never returned by highestForPoints.
		assertEquals(CombatAchievementTier.MASTER, CombatAchievementTier.highestForPoints(99999));
	}

	@Test
	public void testRewardMapping()
	{
		assertEquals(CombatAchievementReward.EASY, CombatAchievementTier.EASY.reward());
		assertEquals(CombatAchievementReward.MEDIUM, CombatAchievementTier.MEDIUM.reward());
		assertEquals(CombatAchievementReward.HARD, CombatAchievementTier.HARD.reward());
		assertEquals(CombatAchievementReward.ELITE, CombatAchievementTier.ELITE.reward());
		assertEquals(CombatAchievementReward.MASTER, CombatAchievementTier.MASTER.reward());
		assertEquals(CombatAchievementReward.GRANDMASTER, CombatAchievementTier.GRANDMASTER.reward());
	}

	@Test
	public void testFromName()
	{
		assertEquals(CombatAchievementTier.EASY, CombatAchievementTier.fromName("Easy"));
		assertEquals(CombatAchievementTier.EASY, CombatAchievementTier.fromName("easy"));
		assertEquals(CombatAchievementTier.GRANDMASTER, CombatAchievementTier.fromName("GRANDMASTER"));
		assertNull(CombatAchievementTier.fromName("bogus"));
		assertNull(CombatAchievementTier.fromName(null));
	}

	@Test
	public void testResultPointsAndTier()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		completed.put(CombatAchievementTier.EASY, 41);
		completed.put(CombatAchievementTier.MEDIUM, 60);
		CombatAchievementResult r = CombatAchievementResult.of(completed, null);
		assertEquals(161, r.getTotalPoints());
		assertEquals(CombatAchievementTier.MEDIUM, r.getTier());
		assertEquals(CombatAchievementReward.MEDIUM, r.getReward());
	}

	@Test
	public void testGrandmasterRequiresFullCompletion()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			total.put(tier, 100);
			completed.put(tier, 99); // one short in every tier
		}
		CombatAchievementResult r = CombatAchievementResult.of(completed, total);
		assertTrue(r.getTotalPoints() > 1904);
		assertEquals(CombatAchievementTier.MASTER, r.getTier());
		assertEquals(CombatAchievementReward.MASTER, r.getReward());
		assertFalse(r.isAllComplete());
	}

	@Test
	public void testGrandmasterRequiresCanonicalTotals()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			completed.put(tier, tier.totalTasks() - 1);
			total.put(tier, tier.totalTasks() - 1);
		}
		CombatAchievementResult r = CombatAchievementResult.of(completed, total);
		assertEquals(CombatAchievementTier.MASTER, r.getTier());
		assertFalse(r.isAllComplete());
	}

	@Test
	public void testGrandmasterWithFullCompletion()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			total.put(tier, tier.totalTasks());
			completed.put(tier, tier.totalTasks());
		}
		CombatAchievementResult r = CombatAchievementResult.of(completed, total);
		assertEquals(CombatAchievementTier.GRANDMASTER, r.getTier());
		assertEquals(CombatAchievementReward.GRANDMASTER, r.getReward());
		assertTrue(r.isAllComplete());
	}

	@Test
	public void testGrandmasterWithCanonicalTotals()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			completed.put(tier, tier.totalTasks());
			total.put(tier, tier.totalTasks());
		}
		CombatAchievementResult r = CombatAchievementResult.of(completed, total);
		assertEquals(2630, r.getTotalPoints());
		assertEquals(CombatAchievementTier.GRANDMASTER, r.getTier());
		assertEquals(CombatAchievementReward.GRANDMASTER, r.getReward());
	}

	@Test
	public void testEmptyResult()
	{
		CombatAchievementResult r = CombatAchievementResult.of(null, null);
		assertEquals(0, r.getTotalPoints());
		assertNull(r.getTier());
		assertNull(r.getReward());
		assertFalse(r.isAllComplete());
	}

	@Test
	public void testResultCountAccessors()
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		completed.put(CombatAchievementTier.HARD, 50);
		total.put(CombatAchievementTier.HARD, 142);
		CombatAchievementResult r = CombatAchievementResult.of(completed, total);
		assertEquals(50, r.getCompleted(CombatAchievementTier.HARD));
		assertEquals(142, r.getTotal(CombatAchievementTier.HARD));
		assertEquals(0, r.getCompleted(CombatAchievementTier.MASTER));
	}
}
