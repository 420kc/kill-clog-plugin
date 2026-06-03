package com.killclog;

import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable Combat Achievement lookup result from per-tier task counts.
 */
final class CombatAchievementResult
{
	private final Map<CombatAchievementTier, Integer> completed;
	private final Map<CombatAchievementTier, Integer> total;
	private final int totalPoints;
	private final CombatAchievementTier tier;

	private CombatAchievementResult(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> total)
	{
		this.completed = completed;
		this.total = total;

		int points = 0;
		for (Map.Entry<CombatAchievementTier, Integer> entry : completed.entrySet())
		{
			points += entry.getValue() * entry.getKey().pointsPerTask();
		}
		this.totalPoints = points;
		this.tier = CombatAchievementTier.highestForResult(this);
	}

	/** True when every task in every tier is completed. */
	boolean isAllComplete()
	{
		for (CombatAchievementTier loopTier : CombatAchievementTier.values())
		{
			int done = completed.getOrDefault(loopTier, 0);
			int need = loopTier.totalTasks();
			if (done < need)
			{
				return false;
			}
		}
		return true;
	}

	/** Build from per-tier completed/total counts; null maps are treated as empty. */
	static CombatAchievementResult of(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> total)
	{
		Map<CombatAchievementTier, Integer> c = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> t = new EnumMap<>(CombatAchievementTier.class);
		if (completed != null)
		{
			c.putAll(completed);
		}
		if (total != null)
		{
			t.putAll(total);
		}
		return new CombatAchievementResult(c, t);
	}

	int getCompleted(CombatAchievementTier tier)
	{
		return completed.getOrDefault(tier, 0);
	}

	int getTotal(CombatAchievementTier tier)
	{
		return total.getOrDefault(tier, 0);
	}

	int getTotalPoints()
	{
		return totalPoints;
	}

	/** Highest unlocked tier, or null if below Easy. */
	CombatAchievementTier getTier()
	{
		return tier;
	}

	/** PvM Summary reward sprite for the held tier, or null when no tier is held. */
	CombatAchievementReward getReward()
	{
		return tier == null ? null : tier.reward();
	}
}
