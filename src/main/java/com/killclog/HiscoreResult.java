package com.killclog;

import java.util.Collections;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Parsed hiscore data for a player.
 */
public class HiscoreResult
{
	@Getter
	private final AccountType accountType;
	@Getter(AccessLevel.PACKAGE)
	private final HiscoreTable hiscoreTable;
	@Getter
	private final Map<String, Integer> bossKills;
	private final Map<String, Integer> bossRanks;
	private final Map<String, Integer> activityScores;
	private final Map<String, Integer> activityRanks;
	private final Map<String, Integer> skillLevels;
	private final Map<String, Integer> skillRanks;
	private final Map<String, Long> skillXps;
	@Getter
	private final int totalLevel;
	@Getter
	private final long totalXp;
	/** Unrounded, so the Combat Summary can show what vanilla calls Exact Combat Level. */
	@Getter
	private final double combatLevelExact;
	@Getter
	private final int overallRank;
	@Getter(AccessLevel.PACKAGE)
	private boolean rankDataAvailable = true;

	/** Another leaderboard's row that trails this player's stats, like a Hardcore's after the death. */
	@Getter(AccessLevel.PACKAGE)
	private boolean frozen;

	/**
	 * Whether this lookup's own answers make the account an Ironman now: true when its regular and
	 * Ironman rows matched or RuneLite named the type, false when the Ironman row trailed, and null
	 * when either board didn't answer. It stays with the lookup, so later caches can't change it.
	 */
	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private Boolean ironmanNow;

	/** Whether this lookup's Hardcore row trails its Ironman row: the account died as a Hardcore, then played on as an
	 *  Ironman. False when level, null when either board didn't answer. */
	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private Boolean diedAsHardcore;

	public HiscoreResult(AccountType accountType, HiscoreTable hiscoreTable,
		Map<String, Integer> bossKills, Map<String, Integer> bossRanks,
		Map<String, Integer> activityScores, Map<String, Integer> activityRanks,
		Map<String, Integer> skillLevels, Map<String, Integer> skillRanks,
		Map<String, Long> skillXps, int totalLevel, long totalXp,
		double combatLevel, int overallRank)
	{
		this.accountType = accountType;
		this.hiscoreTable = hiscoreTable != null ? hiscoreTable : HiscoreTable.STANDARD;
		this.bossKills = bossKills != null ? bossKills : Collections.emptyMap();
		this.bossRanks = bossRanks != null ? bossRanks : Collections.emptyMap();
		this.activityScores = activityScores != null ? activityScores : Collections.emptyMap();
		this.activityRanks = activityRanks != null ? activityRanks : Collections.emptyMap();
		this.skillLevels = skillLevels != null ? skillLevels : Collections.emptyMap();
		this.skillRanks = skillRanks != null ? skillRanks : Collections.emptyMap();
		this.skillXps = skillXps != null ? skillXps : Collections.emptyMap();
		this.totalLevel = totalLevel;
		this.totalXp = totalXp;
		this.combatLevelExact = combatLevel;
		this.overallRank = overallRank;
	}

	/** The whole combat level every cell and comparison uses. */
	public int getCombatLevel()
	{
		return (int) Math.floor(combatLevelExact);
	}

	public int getKc(String bossName)
	{
		return bossKills.getOrDefault(bossName, -1);
	}

	/** Whether the hiscores carry this boss's row at all, ranked or not: a new boss's is there from Jagex's update. */
	boolean hasBossRow(String bossName)
	{
		return bossKills.containsKey(bossName);
	}

	/** Another leaderboard's whole row under this player's own type and table, so a Hardcore
	 *  who died reads their Hardcore stats as they stood at the death. */
	HiscoreResult withRow(HiscoreResult row)
	{
		HiscoreResult view = new HiscoreResult(accountType, hiscoreTable, row.bossKills, row.bossRanks,
			row.activityScores, row.activityRanks, row.skillLevels, row.skillRanks, row.skillXps,
			row.totalLevel, row.totalXp, row.combatLevelExact, row.overallRank);
		view.rankDataAvailable = row.rankDataAvailable;
		view.frozen = row.totalXp < totalXp;
		view.ironmanNow = ironmanNow;
		view.diedAsHardcore = diedAsHardcore;
		return view;
	}

	/** A display-only projection. A missing table blanks ranks, never stats or identity. */
	HiscoreResult withRanks(HiscoreResult ranks)
	{
		HiscoreResult view = new HiscoreResult(accountType, hiscoreTable, bossKills,
			ranks != null ? ranks.bossRanks : Collections.emptyMap(), activityScores,
			ranks != null ? ranks.activityRanks : Collections.emptyMap(), skillLevels,
			ranks != null ? ranks.skillRanks : Collections.emptyMap(), skillXps,
			totalLevel, totalXp, combatLevelExact, ranks != null ? ranks.overallRank : -1);
		view.rankDataAvailable = ranks != null && ranks.rankDataAvailable;
		view.ironmanNow = ironmanNow;
		view.diedAsHardcore = diedAsHardcore;
		return view;
	}

	public int getRank(String bossName)
	{
		return bossRanks.getOrDefault(bossName, -1);
	}

	public int getActivityScore(String name)
	{
		return activityScores.getOrDefault(name, -1);
	}

	public int getActivityRank(String name)
	{
		return activityRanks.getOrDefault(name, -1);
	}

	public int getSkillLevel(String name)
	{
		return skillLevels.getOrDefault(name, -1);
	}

	public int getSkillRank(String name)
	{
		return skillRanks.getOrDefault(name, -1);
	}

	public long getSkillXp(String name)
	{
		return skillXps.getOrDefault(name, -1L);
	}
}
