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
		assertEquals(CombatAchievementTier.MEDIUM, CombatAchievementTier.highestForPoints(418));
		assertEquals(CombatAchievementTier.HARD, CombatAchievementTier.highestForPoints(419));
		assertEquals(CombatAchievementTier.ELITE, CombatAchievementTier.highestForPoints(1075));
		assertEquals(CombatAchievementTier.MASTER, CombatAchievementTier.highestForPoints(1945));
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
		assertTrue(r.getTotalPoints() > 1945);
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
		assertEquals(2671, r.getTotalPoints());
		assertEquals(CombatAchievementTier.GRANDMASTER, r.getTier());
		assertEquals(CombatAchievementReward.GRANDMASTER, r.getReward());
	}

	@Test
	public void testTierMathFollowsLiveCatalog()
	{
		// The release-day regression: a Grandmaster snapshot synced before a
		// Combat Achievement batch must lose the tier against the game's new
		// catalog, exactly as it does in the client, before the player
		// re-syncs. Completed counts never revoke; only denominators move.
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			completed.put(tier, tier.totalTasks());
			total.put(tier, tier.totalTasks());
		}
		Map<CombatAchievementTier, Integer> live = liveCatalogWithNewBatch();

		CombatAchievementResult r = CombatAchievementResult.of(completed, total, live);
		assertEquals(2671, r.getTotalPoints());
		assertFalse(r.isAllComplete());
		assertEquals(CombatAchievementTier.MASTER, r.getTier());
		// Display denominators follow the live catalog, not the stale snapshot.
		assertEquals(129, r.getTotal(CombatAchievementTier.GRANDMASTER));
		assertEquals(121, r.getCompleted(CombatAchievementTier.GRANDMASTER));
	}

	@Test
	public void testLiveCatalogCompletionReclaimsGrandmaster()
	{
		// A player who has finished the new batch is Grandmaster against the
		// live catalog even while the provider still reports old totals.
		Map<CombatAchievementTier, Integer> live = liveCatalogWithNewBatch();
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			completed.put(tier, live.get(tier));
			total.put(tier, tier.totalTasks());
		}

		CombatAchievementResult r = CombatAchievementResult.of(completed, total, live);
		assertTrue(r.isAllComplete());
		assertEquals(CombatAchievementTier.GRANDMASTER, r.getTier());
	}

	@Test
	public void testRebasedOnHealsStaleVerdicts()
	{
		// A result built before any catalog capture holds a fallback
		// Grandmaster; rebasing against an expanded catalog demotes it with
		// no refetch, and rebasing an already-current result is free.
		Map<CombatAchievementTier, Integer> full = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			full.put(tier, tier.totalTasks());
		}
		CombatAchievementResult stale = CombatAchievementResult.of(full, full);
		assertEquals(CombatAchievementTier.GRANDMASTER, stale.getTier());

		Map<CombatAchievementTier, Integer> live = liveCatalogWithNewBatch();
		CombatAchievementResult healed = stale.rebasedOn(live);
		assertEquals(CombatAchievementTier.MASTER, healed.getTier());
		assertEquals(129, healed.getTotal(CombatAchievementTier.GRANDMASTER));
		assertSame(healed, healed.rebasedOn(live));
		assertSame(stale, stale.rebasedOn(null));
	}

	@Test
	public void testDeriveTotalsFromThresholds()
	{
		// The live thresholds encode the fallback catalog exactly.
		Map<CombatAchievementTier, Integer> thresholds = new EnumMap<>(CombatAchievementTier.class);
		thresholds.put(CombatAchievementTier.EASY, 41);
		thresholds.put(CombatAchievementTier.MEDIUM, 161);
		thresholds.put(CombatAchievementTier.HARD, 419);
		thresholds.put(CombatAchievementTier.ELITE, 1075);
		thresholds.put(CombatAchievementTier.MASTER, 1945);
		thresholds.put(CombatAchievementTier.GRANDMASTER, 2671);

		Map<CombatAchievementTier, Integer> totals = CaCatalog.deriveTotals(thresholds);
		assertNotNull(totals);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			assertEquals(tier.totalTasks(), (int) totals.get(tier));
		}
	}

	@Test
	public void testDeriveTotalsRejectsPartialReads()
	{
		// Pre-login zeros.
		Map<CombatAchievementTier, Integer> zeros = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			zeros.put(tier, 0);
		}
		assertNull(CaCatalog.deriveTotals(zeros));

		// Non-monotonic thresholds.
		Map<CombatAchievementTier, Integer> reversed = new EnumMap<>(CombatAchievementTier.class);
		reversed.put(CombatAchievementTier.EASY, 161);
		reversed.put(CombatAchievementTier.MEDIUM, 41);
		reversed.put(CombatAchievementTier.HARD, 416);
		reversed.put(CombatAchievementTier.ELITE, 1064);
		reversed.put(CombatAchievementTier.MASTER, 1904);
		reversed.put(CombatAchievementTier.GRANDMASTER, 2630);
		assertNull(CaCatalog.deriveTotals(reversed));

		// A step that does not divide by the tier's points per task.
		Map<CombatAchievementTier, Integer> garbled = new EnumMap<>(CombatAchievementTier.class);
		garbled.put(CombatAchievementTier.EASY, 41);
		garbled.put(CombatAchievementTier.MEDIUM, 162);
		garbled.put(CombatAchievementTier.HARD, 416);
		garbled.put(CombatAchievementTier.ELITE, 1064);
		garbled.put(CombatAchievementTier.MASTER, 1904);
		garbled.put(CombatAchievementTier.GRANDMASTER, 2630);
		assertNull(CaCatalog.deriveTotals(garbled));
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

	/** The fallback catalog plus an invented release batch across every tier. */
	private static Map<CombatAchievementTier, Integer> liveCatalogWithNewBatch()
	{
		Map<CombatAchievementTier, Integer> live = new EnumMap<>(CombatAchievementTier.class);
		live.put(CombatAchievementTier.EASY, 42);
		live.put(CombatAchievementTier.MEDIUM, 61);
		live.put(CombatAchievementTier.HARD, 87);
		live.put(CombatAchievementTier.ELITE, 165);
		live.put(CombatAchievementTier.MASTER, 177);
		live.put(CombatAchievementTier.GRANDMASTER, 129);
		return live;
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
