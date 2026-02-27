package com.killclog;

import java.util.Map;

/**
 * Parsed hiscore data for a player.
 */
public class HiscoreResult
{
    private final AccountType accountType;
    private final Map<String, Integer> bossKills;
    private final Map<String, Integer> bossRanks;
    private final int totalLevel;
    private final long totalXp;

    public HiscoreResult(AccountType accountType, Map<String, Integer> bossKills,
                         Map<String, Integer> bossRanks, int totalLevel, long totalXp)
    {
        this.accountType = accountType;
        this.bossKills = bossKills;
        this.bossRanks = bossRanks;
        this.totalLevel = totalLevel;
        this.totalXp = totalXp;
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

    public int getTotalLevel()
    {
        return totalLevel;
    }

    public long getTotalXp()
    {
        return totalXp;
    }

    public int getKc(String bossName)
    {
        return bossKills.getOrDefault(bossName, -1);
    }

    public int getRank(String bossName)
    {
        return bossRanks.getOrDefault(bossName, -1);
    }
}
