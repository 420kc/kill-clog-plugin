package com.killclog;

import java.util.Collections;
import java.util.Map;

/**
 * Parsed hiscore data for a player.
 */
public class HiscoreResult
{
	private final AccountType accountType;
	private final HiscoreTable hiscoreTable;
	private final Map<String, Integer> bossKills;
	private final Map<String, Integer> bossRanks;
	private final Map<String, Integer> activityScores;
	private final Map<String, Integer> activityRanks;
	private final Map<String, Integer> skillLevels;
	private final Map<String, Integer> skillRanks;
	private final Map<String, Long> skillXps;
	private final int totalLevel;
	private final long totalXp;
	private final int combatLevel;
	private final int overallRank;

	public HiscoreResult(AccountType accountType, Map<String, Integer> bossKills,
		Map<String, Integer> bossRanks, Map<String, Integer> activityScores,
		Map<String, Integer> activityRanks, Map<String, Integer> skillLevels,
		int totalLevel, long totalXp, int combatLevel, int overallRank)
	{
		this(accountType, HiscoreTable.STANDARD, bossKills, bossRanks, activityScores,
			activityRanks, skillLevels, Collections.emptyMap(), Collections.emptyMap(),
			totalLevel, totalXp, combatLevel, overallRank);
	}

	public HiscoreResult(AccountType accountType, HiscoreTable hiscoreTable,
		Map<String, Integer> bossKills, Map<String, Integer> bossRanks,
		Map<String, Integer> activityScores, Map<String, Integer> activityRanks,
		Map<String, Integer> skillLevels, int totalLevel, long totalXp,
		int combatLevel, int overallRank)
	{
		this(accountType, hiscoreTable, bossKills, bossRanks, activityScores,
			activityRanks, skillLevels, Collections.emptyMap(), Collections.emptyMap(),
			totalLevel, totalXp, combatLevel, overallRank);
	}

	public HiscoreResult(AccountType accountType, HiscoreTable hiscoreTable,
		Map<String, Integer> bossKills, Map<String, Integer> bossRanks,
		Map<String, Integer> activityScores, Map<String, Integer> activityRanks,
		Map<String, Integer> skillLevels, Map<String, Integer> skillRanks,
		Map<String, Long> skillXps, int totalLevel, long totalXp,
		int combatLevel, int overallRank)
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
		this.combatLevel = combatLevel;
		this.overallRank = overallRank;
	}

	public AccountType getAccountType()
	{
		return accountType;
	}

	HiscoreTable getHiscoreTable()
	{
		return hiscoreTable;
	}

	public Map<String, Integer> getBossKills()
	{
		return bossKills;
	}

	public int getTotalLevel()
	{
		return totalLevel;
	}

	public long getTotalXp()
	{
		return totalXp;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getKc(String bossName)
	{
		return bossKills.getOrDefault(bossName, -1);
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

	public int getOverallRank()
	{
		return overallRank;
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
