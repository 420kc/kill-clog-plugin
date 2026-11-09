package com.killclog;

import java.util.Map;
import lombok.*;
import net.runelite.api.gameval.VarbitID;

/**
 * Combat Achievement tiers and their Combat Summary reward sprites.
 *
 * <p>Easy through Master unlock by points. Grandmaster requires every task in
 * every tier to be complete. Thresholds are never stored: each tier's
 * threshold is the total points of all tasks in it and below, so they derive
 * from task counts. The task counts here are a fallback snapshot for cold
 * starts; {@link CaCatalog} supplies the live counts from the running game
 * build, which is what keeps tier math honest across a Combat Achievement
 * release.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
enum CombatAchievementTier
{
	// The game's counts on 2026-10-28 (the OSRS Wiki's tier table, 655 tasks; RuneProfile's live totals agree).
	EASY(1, 41, VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, CombatAchievementReward.EASY),
	MEDIUM(2, 64, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM, CombatAchievementReward.MEDIUM),
	HARD(3, 89, VarbitID.CA_THRESHOLD_HARD, VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD, CombatAchievementReward.HARD),
	ELITE(4, 166, VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE, CombatAchievementReward.ELITE),
	MASTER(5, 173, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER, CombatAchievementReward.MASTER),
	GRANDMASTER(6, 122, VarbitID.CA_THRESHOLD_GRANDMASTER, VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER,
		CombatAchievementReward.GRANDMASTER);

	private final int pointsPerTask;
	private final int totalTasks;
	// The tier's points threshold, as the game broadcasts it (CaCatalog), and the tasks the logged-in player has
	// completed in it (LocalCaReader).
	final int thresholdVarbit;
	final int completedVarbit;
	private final CombatAchievementReward reward;

	/** A varbit that moves a tier's threshold or your completed count: either means Combat Achievements read again. */
	static boolean isCaVarbit(int id)
	{
		for (CombatAchievementTier tier : values())
		{
			if (id == tier.thresholdVarbit || id == tier.completedVarbit)
			{
				return true;
			}
		}
		return false;
	}

	int pointsPerTask()
	{
		return pointsPerTask;
	}

	int totalTasks()
	{
		return totalTasks;
	}

	/** The Combat Summary reward sprite for this tier. */
	CombatAchievementReward reward()
	{
		return reward;
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
}
