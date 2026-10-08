package com.killclog;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Runtime item sprite leading a Combat Achievement tier in the Combat Summary.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
enum CombatAchievementReward
{
	EASY(25926),
	MEDIUM(25928),
	HARD(25930),
	ELITE(25898),
	MASTER(25904),
	GRANDMASTER(25910);

	private final int itemId;

	int itemId()
	{
		return itemId;
	}
}
