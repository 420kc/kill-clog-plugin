package com.killclog;

import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Rank selection is independent of the account identity and its current stats. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
enum RankLeaderboard
{
	NORMAL("Normal", "hiscore_oldschool", "normal.png"),
	IRONMAN("Ironman", "hiscore_oldschool_ironman", "ironman.png"),
	HARDCORE("Hardcore Ironman", "hiscore_oldschool_hardcore_ironman", "hardcore_ironman.png"),
	ULTIMATE("Ultimate Ironman", "hiscore_oldschool_ultimate", "ultimate_ironman.png"),
	SKILLER("Skiller", "hiscore_oldschool_skiller", "level_3_skiller.png"),
	PURE("Pure", "hiscore_oldschool_skiller_defence", "pure.png");

	final String label;
	final String endpoint;
	final String icon;

	static RankLeaderboard nativeOf(HiscoreResult result)
	{
		if (result.getHiscoreTable() == HiscoreTable.SKILLER) return SKILLER;
		if (result.getHiscoreTable() == HiscoreTable.ONE_DEFENCE) return PURE;
		return of(result.getAccountType());
	}

	/** A solo Ironman type's own board; every other type, a group one among them, ranks on the normal board. */
	static RankLeaderboard of(@Nullable AccountType type)
	{
		if (type == null) return NORMAL;
		switch (type)
		{
			case IRONMAN: return IRONMAN;
			case HARDCORE_IRONMAN: return HARDCORE;
			case ULTIMATE_IRONMAN: return ULTIMATE;
			default: return NORMAL;
		}
	}
}
