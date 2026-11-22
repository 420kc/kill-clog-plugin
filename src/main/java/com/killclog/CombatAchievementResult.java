package com.killclog;

import com.google.gson.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;

/**
 * A player's Combat Achievements, from the tasks done in each tier. Never changed once built.
 *
 * Tiers are worked out against the catalog as it is now: the game's own task counts from {@link CaCatalog} once read,
 * else the fallback counts. Tasks done are never taken back, so an answer a site gave before a Combat Achievement
 * release still has true counts and only the totals have moved. Worked out against the game's totals, a player who
 * dropped a tier on release day shows it at once, before they sync again.
 */
final class CombatAchievementResult
{
	private final Map<CombatAchievementTier, Integer> completed;
	private final Map<CombatAchievementTier, Integer> providerTotal;
	private final Map<CombatAchievementTier, Integer> currentTotals;
	private final boolean liveCatalog;
	// Read from this client's game for the logged-in player (LocalCaCache), which no other source replaces.
	private final boolean fromGame;
	@Getter(AccessLevel.PACKAGE)
	private final int totalPoints;
	// Highest unlocked tier, or null if below Easy.
	@Getter(AccessLevel.PACKAGE)
	private final CombatAchievementTier tier;

	private CombatAchievementResult(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> providerTotal,
									Map<CombatAchievementTier, Integer> liveTotals, boolean fromGame)
	{
		this.completed = completed;
		this.providerTotal = providerTotal;
		this.liveCatalog = liveTotals != null && !liveTotals.isEmpty();
		this.fromGame = fromGame;

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

	/**
	 * The points the next tier asks for, which the CA tile's bar fills toward: each tier's threshold is every task's
	 * points in it and below, Grandmaster's every point, so a player with every task done stands at the end.
	 */
	int nextTierPoints()
	{
		int threshold = 0;
		for (CombatAchievementTier loopTier : CombatAchievementTier.values())
		{
			threshold += currentTotals.getOrDefault(loopTier, loopTier.totalTasks()) * loopTier.pointsPerTask();
			if (threshold > totalPoints)
			{
				return threshold;
			}
		}
		return threshold;
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
	 * Built with the game's own task counts. When given, they decide the tiers and are the totals shown, because a
	 * site's answer and the fallback counts can trail a Combat Achievement release.
	 */
	static CombatAchievementResult of(Map<CombatAchievementTier, Integer> completed,
									Map<CombatAchievementTier, Integer> total,
									Map<CombatAchievementTier, Integer> liveTotals)
	{
		return build(completed, total, liveTotals, false);
	}

	/** Your own game's counts for the logged-in player, which no other source replaces (LocalCaCache). */
	static CombatAchievementResult fromGame(Map<CombatAchievementTier, Integer> completed,
		Map<CombatAchievementTier, Integer> total, Map<CombatAchievementTier, Integer> liveTotals)
	{
		return build(completed, total, liveTotals, true);
	}

	private static CombatAchievementResult build(Map<CombatAchievementTier, Integer> completed,
		Map<CombatAchievementTier, Integer> total, Map<CombatAchievementTier, Integer> liveTotals, boolean fromGame)
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
		return new CombatAchievementResult(c, t, liveTotals, fromGame);
	}

	/**
	 * Tier rows as RuneProfile sends them and killclog.com serves a player's sync: each a tier's name, its
	 * completed tasks and the game's count. Null when no row names a tier; a count of the wrong shape throws, so a
	 * garbled answer never reads as none done.
	 */
	@Nullable
	static CombatAchievementResult fromRows(JsonArray rows, @Nullable Map<CombatAchievementTier, Integer> liveTotals)
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);
		for (JsonObject row : Json.objects(rows))
		{
			CombatAchievementTier tier = row.has("name") ? CombatAchievementTier.fromName(row.get("name").getAsString()) : null;
			if (tier != null)
			{
				completed.put(tier, Json.strictInt(row, "completed"));
				total.put(tier, Json.strictInt(row, "total"));
			}
		}
		return completed.isEmpty() ? null : of(completed, total, liveTotals);
	}

	/** The rows your sync sends: every tier's tasks done and the game's count for it. */
	JsonArray toRows()
	{
		JsonArray rows = new JsonArray();
		for (CombatAchievementTier loopTier : CombatAchievementTier.values())
		{
			JsonObject row = new JsonObject();
			row.addProperty("name", loopTier.name().toLowerCase(Locale.ROOT));
			row.addProperty("completed", completed.getOrDefault(loopTier, 0));
			row.addProperty("total", currentTotals.get(loopTier));
			rows.add(row);
		}
		return rows;
	}

	/**
	 * The tiers a card shows: yours from your own game, once this login has read it. Anyone else's, and yours until
	 * then (the reading kept from an earlier login), are the fuller of what was fetched and their own Kill Clog sync,
	 * by points, ties going to the sync, as the fullest log leads, so a sync they stopped never stands over
	 * RuneProfile's later answer. A character's tasks only grow while it plays, so the fuller is the newer; a Hardcore
	 * Worlds reset or a retired task can leave RuneProfile's older reading the fuller until it updates.
	 */
	@Nullable
	static CombatAchievementResult shown(@Nullable CombatAchievementResult fetched, @Nullable CombatAchievementResult synced)
	{
		return fetched != null && (fetched.fromGame || synced == null || fetched.totalPoints > synced.totalPoints)
			? fetched : synced;
	}

	/**
	 * This result worked out again against the game's totals, or itself when it already used them. A cached result is
	 * built once and served until it ages out, so one built before the catalog was read (or across a release) is put
	 * right as it is read rather than served stale.
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
		return new CombatAchievementResult(completed, providerTotal, liveTotals, fromGame);
	}
}
