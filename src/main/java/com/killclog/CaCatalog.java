package com.killclog;

import java.util.*;
import javax.inject.Singleton;
import net.runelite.api.Client;

/**
 * The Combat Achievement tiers as the running game has them, read from its tier-threshold varbits. The client is sent
 * the points each tier needs, and those say the whole catalog: a tier's step over the one below is that tier's total
 * points, so its task count is the step divided by its points per task. A Combat Achievement release moves the varbits
 * that day, so tiers worked out from here never trail the game as the fallback counts in {@link CombatAchievementTier}
 * or a site's older answer can.
 *
 * Read on the client thread and published whole through a volatile field, so a reader on another thread (RuneProfile's
 * parsing) sees one complete reading or none.
 */
@Singleton
final class CaCatalog
{
	private volatile Map<CombatAchievementTier, Integer> totals;

	/**
	 * Read the six threshold varbits and work out each tier's task count. An unusable reading (the zeros before login,
	 * or a half-updated set) keeps the last one.
	 */
	boolean capture(Client client)
	{
		Map<CombatAchievementTier, Integer> thresholds = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			thresholds.put(tier, client.getVarbitValue(tier.thresholdVarbit));
		}

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

	void clear()
	{
		totals = null;
	}

	/**
	 * Thresholds to each tier's task count. A tier's step over the one below must be positive and divide evenly by its
	 * points per task; anything else is a half-read set and gives null.
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
