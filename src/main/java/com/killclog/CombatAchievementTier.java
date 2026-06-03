package com.killclog;

/**
 * Combat Achievement tiers, thresholds, and PvM Summary reward sprites.
 *
 * <p>Easy through Master unlock by points. Grandmaster requires every task in
 * every tier to be complete.
 */
enum CombatAchievementTier
{
	EASY(1, 41, 41),
	MEDIUM(2, 161, 60),
	HARD(3, 416, 85),
	ELITE(4, 1064, 162),
	MASTER(5, 1904, 168),
	GRANDMASTER(6, -1, 121);

	private final int pointsPerTask;
	private final int unlockThreshold;
	private final int totalTasks;

	CombatAchievementTier(int pointsPerTask, int unlockThreshold, int totalTasks)
	{
		this.pointsPerTask = pointsPerTask;
		this.unlockThreshold = unlockThreshold;
		this.totalTasks = totalTasks;
	}

	int pointsPerTask()
	{
		return pointsPerTask;
	}

	int unlockThreshold()
	{
		return unlockThreshold;
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

	/** Highest points-unlocked tier. Grandmaster is checked from completed tasks. */
	static CombatAchievementTier highestForPoints(int totalPoints)
	{
		CombatAchievementTier unlocked = null;
		for (CombatAchievementTier tier : values())
		{
			if (tier == GRANDMASTER)
			{
				continue;
			}
			if (totalPoints >= tier.unlockThreshold)
			{
				unlocked = tier;
			}
		}
		return unlocked;
	}

	/**
	 * Highest tier unlocked, considering both points and task completion.
	 * Grandmaster requires every task in every tier to be completed.
	 */
	static CombatAchievementTier highestForResult(CombatAchievementResult result)
	{
		if (result == null)
		{
			return null;
		}
		if (result.isAllComplete())
		{
			return GRANDMASTER;
		}
		return highestForPoints(result.getTotalPoints());
	}
}
