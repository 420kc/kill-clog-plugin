package com.killclog;

import java.util.EnumMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Immutable Combat Achievement lookup result from per-tier task counts.
 *
 * <p>Tier math runs against the current catalog: live task counts from
 * {@link CaCatalog} when captured, else the fallback constants. Completed
 * counts never revoke, so a provider snapshot taken before a Combat
 * Achievement release still carries true counts; only the denominators move.
 * Computing against the live catalog therefore reproduces the game's own
 * demotion semantics on release day, before the player has re-synced.
 */
final class CombatAchievementResult
{
	private final Map<CombatAchievementTier, Integer> completed;
	private final Map<CombatAchievementTier, Integer> providerTotal;
	private final Map<CombatAchievementTier, Integer> currentTotals;
	private final boolean liveCatalog;
	@Getter(AccessLevel.PACKAGE)
	private final int totalPoints;
	// Highest unlocked tier, or null if below Easy.
	@Getter(AccessLevel.PACKAGE)
	private final CombatAchievementTier tier;

	private CombatAchievementResult(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> providerTotal,
									Map<CombatAchievementTier, Integer> liveTotals)
	{
		this.completed = completed;
		this.providerTotal = providerTotal;
		this.liveCatalog = liveTotals != null && !liveTotals.isEmpty();

		Map<CombatAchievementTier, Integer> totals = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier loopTier : CombatAchievementTier.values())
		{
			Integer live = liveCatalog ? liveTotals.get(loopTier) : null;
			totals.put(loopTier, live != null ? live : loopTier.totalTasks());
		}
		this.currentTotals = totals;

		int points = 0;
		for (Map.Entry<CombatAchievementTier, Integer> entry : completed.entrySet())
		{
			points += entry.getValue() * entry.getKey().pointsPerTask();
		}
		this.totalPoints = points;
		this.tier = isAllComplete()
			? CombatAchievementTier.GRANDMASTER
			: CombatAchievementTier.highestForPoints(points, currentTotals);
	}

	/** True when every task in every tier is completed against the current catalog. */
	boolean isAllComplete()
	{
		for (CombatAchievementTier loopTier : CombatAchievementTier.values())
		{
			int done = completed.getOrDefault(loopTier, 0);
			int need = currentTotals.getOrDefault(loopTier, loopTier.totalTasks());
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
		return of(completed, total, null);
	}

	/**
	 * Build with the live catalog's task counts. When present they own both the
	 * tier math and the displayed denominators, because provider snapshots and
	 * the fallback constants can trail a Combat Achievement release.
	 */
	static CombatAchievementResult of(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> total,
									Map<CombatAchievementTier, Integer> liveTotals)
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
		return new CombatAchievementResult(c, t, liveTotals);
	}

	/**
	 * This result recomputed against the given live totals; returns this
	 * instance when the math already used them. Cached results are built once
	 * and served for their TTL, so a verdict computed before a catalog capture
	 * (or across a threshold change) heals on read instead of surviving stale.
	 */
	CombatAchievementResult rebasedOn(Map<CombatAchievementTier, Integer> liveTotals)
	{
		if (liveTotals == null || liveTotals.isEmpty())
		{
			return this;
		}
		if (liveCatalog && currentTotals.equals(liveTotals))
		{
			return this;
		}
		return new CombatAchievementResult(completed, providerTotal, liveTotals);
	}

	int getCompleted(CombatAchievementTier tier)
	{
		return completed.getOrDefault(tier, 0);
	}

	int getTotal(CombatAchievementTier tier)
	{
		return liveCatalog
			? currentTotals.getOrDefault(tier, 0)
			: providerTotal.getOrDefault(tier, 0);
	}

	/** PvM Summary reward sprite for the held tier, or null when no tier is held. */
	CombatAchievementReward getReward()
	{
		return tier == null ? null : tier.reward();
	}
}
