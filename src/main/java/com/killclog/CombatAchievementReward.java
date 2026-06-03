package com.killclog;

/**
 * Runtime item sprite shown beside a Combat Achievement tier in PvM Summary.
 */
enum CombatAchievementReward
{
	EASY(25926),
	MEDIUM(25928),
	HARD(25930),
	ELITE(25898),
	MASTER(25904),
	GRANDMASTER(25910);

	private final int itemId;

	CombatAchievementReward(int itemId)
	{
		this.itemId = itemId;
	}

	int itemId()
	{
		return itemId;
	}
}
