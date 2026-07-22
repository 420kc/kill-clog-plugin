package com.killclog;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Live Combat Achievement catalog, read from the game's own tier-threshold
 * varbits. The client broadcasts the current points threshold for every tier,
 * and those thresholds encode the whole catalog: each tier's step over the
 * previous threshold is that tier's total points, so its task count is the
 * step divided by its points per task. A Combat Achievement release moves the
 * varbits on day one, so tier math computed from here never trails the game
 * the way the fallback constants in {@link CombatAchievementTier} or a
 * provider snapshot can.
 *
 * <p>Captured on the client thread; published as an immutable map through a
 * volatile field so HTTP-thread consumers (RuneProfile parsing) see a complete
 * snapshot or none.
 */
final class CaCatalog
{
	private volatile Map<CombatAchievementTier, Integer> totals;

	/** True for the tier-threshold varbits this catalog is derived from. */
	static boolean isThresholdVarbit(int id)
	{
		return id == VarbitID.CA_THRESHOLD_EASY
			|| id == VarbitID.CA_THRESHOLD_MEDIUM
			|| id == VarbitID.CA_THRESHOLD_HARD
			|| id == VarbitID.CA_THRESHOLD_ELITE
			|| id == VarbitID.CA_THRESHOLD_MASTER
			|| id == VarbitID.CA_THRESHOLD_GRANDMASTER;
	}

	/**
	 * Read the six threshold varbits and derive per-tier task totals. Keeps the
	 * previous capture when the read is invalid (pre-login zeros or a
	 * mid-update partial state).
	 */
	boolean capture(Client client)
	{
		Map<CombatAchievementTier, Integer> thresholds = new EnumMap<>(CombatAchievementTier.class);
		thresholds.put(CombatAchievementTier.EASY, client.getVarbitValue(VarbitID.CA_THRESHOLD_EASY));
		thresholds.put(CombatAchievementTier.MEDIUM, client.getVarbitValue(VarbitID.CA_THRESHOLD_MEDIUM));
		thresholds.put(CombatAchievementTier.HARD, client.getVarbitValue(VarbitID.CA_THRESHOLD_HARD));
		thresholds.put(CombatAchievementTier.ELITE, client.getVarbitValue(VarbitID.CA_THRESHOLD_ELITE));
		thresholds.put(CombatAchievementTier.MASTER, client.getVarbitValue(VarbitID.CA_THRESHOLD_MASTER));
		thresholds.put(CombatAchievementTier.GRANDMASTER, client.getVarbitValue(VarbitID.CA_THRESHOLD_GRANDMASTER));

		Map<CombatAchievementTier, Integer> derived = deriveTotals(thresholds);
		if (derived == null)
		{
			return false;
		}
		totals = Collections.unmodifiableMap(derived);
		return true;
	}

	/** Current per-tier task totals, or null before a valid capture. */
	Map<CombatAchievementTier, Integer> totals()
	{
		return totals;
	}

	/** Test seam: a catalog pre-published with the given totals. */
	/* package */ static CaCatalog withTotals(Map<CombatAchievementTier, Integer> totals)
	{
		CaCatalog catalog = new CaCatalog();
		catalog.totals = Collections.unmodifiableMap(new EnumMap<>(totals));
		return catalog;
	}

	void clear()
	{
		totals = null;
	}

	/**
	 * Thresholds to per-tier task counts. Each tier's step over the previous
	 * threshold must be positive and divide evenly by its points per task;
	 * anything else is a partial or garbled read and yields null.
	 */
	/* package */ static Map<CombatAchievementTier, Integer> deriveTotals(
		Map<CombatAchievementTier, Integer> thresholds)
	{
		Map<CombatAchievementTier, Integer> derived = new EnumMap<>(CombatAchievementTier.class);
		int prev = 0;
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			Integer threshold = thresholds.get(tier);
			if (threshold == null || threshold <= prev)
			{
				return null;
			}
			int step = threshold - prev;
			if (step % tier.pointsPerTask() != 0)
			{
				return null;
			}
			derived.put(tier, step / tier.pointsPerTask());
			prev = threshold;
		}
		return derived;
	}
}
