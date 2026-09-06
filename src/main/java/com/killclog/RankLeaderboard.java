package com.killclog;

/** Rank selection is independent of the account identity and its current stats. */
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

	RankLeaderboard(String label, String endpoint, String icon)
	{
		this.label = label;
		this.endpoint = endpoint;
		this.icon = icon;
	}

	static RankLeaderboard nativeOf(HiscoreResult result)
	{
		if (result.getHiscoreTable() == HiscoreTable.SKILLER) return SKILLER;
		if (result.getHiscoreTable() == HiscoreTable.ONE_DEFENCE) return PURE;
		if (result.getAccountType() == null) return NORMAL;
		switch (result.getAccountType())
		{
			case IRONMAN: return IRONMAN;
			case HARDCORE_IRONMAN: return HARDCORE;
			case ULTIMATE_IRONMAN: return ULTIMATE;
			default: return NORMAL;
		}
	}
}
