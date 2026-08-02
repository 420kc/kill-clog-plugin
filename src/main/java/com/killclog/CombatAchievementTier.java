package com.killclog;

import java.util.EnumMap;
import java.util.Map;

/**
 * Combat Achievement tiers and PvM Summary reward sprites.
 *
 * <p>Easy through Master unlock by points. Grandmaster requires every task in
 * every tier to be complete. Thresholds are never stored: each tier's
 * threshold is the total points of all tasks in it and below, so they derive
 * from task counts. The task counts here are a fallback snapshot for cold
 * starts; {@link CaCatalog} supplies the live counts from the running game
 * build, which is what keeps tier math honest across a Combat Achievement
 * release.
 */
enum CombatAchievementTier
{
	EASY(1, 41),
	MEDIUM(2, 60),
	HARD(3, 86),
	ELITE(4, 164),
	MASTER(5, 174),
	GRANDMASTER(6, 121);

	private final int pointsPerTask;
	private final int totalTasks;

	CombatAchievementTier(int pointsPerTask, int totalTasks)
	{
		this.pointsPerTask = pointsPerTask;
		this.totalTasks = totalTasks;
	}

	int pointsPerTask()
	{
		return pointsPerTask;
	}

	int totalTasks()
	{
		return totalTasks;
	}

	/** PvM Summary reward sprite for this tier. */
	CombatAchievementReward reward()
	{
		switch (this)
		{
			case EASY:
				return CombatAchievementReward.EASY;
			case MEDIUM:
				return CombatAchievementReward.MEDIUM;
			case HARD:
				return CombatAchievementReward.HARD;
			case ELITE:
				return CombatAchievementReward.ELITE;
			case MASTER:
				return CombatAchievementReward.MASTER;
			case GRANDMASTER:
				return CombatAchievementReward.GRANDMASTER;
			default:
				throw new AssertionError("Unhandled tier: " + this);
		}
	}

	/** Match a RuneProfile tier row name. */
	static CombatAchievementTier fromName(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (CombatAchievementTier tier : values())
		{
			if (tier.name().equalsIgnoreCase(name))
			{
				return tier;
			}
		}
		return null;
	}

	/** Highest points-unlocked tier against the fallback task counts. */
	static CombatAchievementTier highestForPoints(int totalPoints)
	{
		return highestForPoints(totalPoints, fallbackTotals());
	}

	/**
	 * Highest points-unlocked tier against the given task counts, with each
	 * threshold derived as the cumulative points of that tier and below.
	 * Grandmaster is completion-gated and never returned here.
	 */
	static CombatAchievementTier highestForPoints(int totalPoints,
		Map<CombatAchievementTier, Integer> totals)
	{
		CombatAchievementTier unlocked = null;
		int threshold = 0;
		for (CombatAchievementTier tier : values())
		{
			if (tier == GRANDMASTER)
			{
				continue;
			}
			threshold += totals.getOrDefault(tier, tier.totalTasks) * tier.pointsPerTask;
			if (totalPoints >= threshold)
			{
				unlocked = tier;
			}
		}
		return unlocked;
	}

	/** The fallback task counts as a map, for callers that merge with live data. */
	static Map<CombatAchievementTier, Integer> fallbackTotals()
	{
		Map<CombatAchievementTier, Integer> totals = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : values())
		{
			totals.put(tier, tier.totalTasks);
		}
		return totals;
	}
}
