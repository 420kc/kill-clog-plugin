package com.killclog;

import java.util.Collections;
import java.util.Map;

/**
 * Parsed hiscore data for a player.
 */
public class HiscoreResult
{
    private final AccountType accountType;
    private final Map<String, Integer> bossKills;
    private final Map<String, Integer> bossRanks;
    private final Map<String, Integer> activityScores;
    private final Map<String, Integer> activityRanks;
    private final Map<String, Integer> skillLevels;
    private final int totalLevel;
    private final long totalXp;
    private final int combatLevel;

    public HiscoreResult(AccountType accountType, Map<String, Integer> bossKills,
                         Map<String, Integer> bossRanks, Map<String, Integer> activityScores,
                         Map<String, Integer> activityRanks, Map<String, Integer> skillLevels,
                         int totalLevel, long totalXp, int combatLevel)
    {
        this.accountType = accountType;
        this.bossKills = bossKills != null ? bossKills : Collections.emptyMap();
        this.bossRanks = bossRanks != null ? bossRanks : Collections.emptyMap();
        this.activityScores = activityScores != null ? activityScores : Collections.emptyMap();
        this.activityRanks = activityRanks != null ? activityRanks : Collections.emptyMap();
        this.skillLevels = skillLevels != null ? skillLevels : Collections.emptyMap();
        this.totalLevel = totalLevel;
        this.totalXp = totalXp;
        this.combatLevel = combatLevel;
    }

    public AccountType getAccountType()
    {
        return accountType;
    }

    public Map<String, Integer> getBossKills()
    {
        return bossKills;
    }

    public Map<String, Integer> getBossRanks()
    {
        return bossRanks;
    }

    public Map<String, Integer> getActivityScores()
    {
        return activityScores;
    }

    public Map<String, Integer> getActivityRanks()
    {
        return activityRanks;
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

    public Map<String, Integer> getSkillLevels()
    {
        return skillLevels;
    }

    public int getSkillLevel(String name)
    {
        return skillLevels.getOrDefault(name, -1);
    }
}
