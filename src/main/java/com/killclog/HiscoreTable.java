package com.killclog;

/**
 * Which Jagex hiscore table supplied the rank data.
 *
 * This is separate from AccountType. A player can be a regular, ironman, or UIM
 * account type, while pure/skiller hiscores are separate specialty tables.
 */
enum HiscoreTable
{
	STANDARD(null, null, null),
	ONE_DEFENCE("hiscore_oldschool_skiller_defence", "pure.png", "Defense Pure"),
	SKILLER("hiscore_oldschool_skiller", "level_3_skiller.png", "Skiller");

	private final String hiscoreKey;
	private final String badgeResource;
	private final String displayName;

	HiscoreTable(String hiscoreKey, String badgeResource, String displayName)
	{
		this.hiscoreKey = hiscoreKey;
		this.badgeResource = badgeResource;
		this.displayName = displayName;
	}

	String hiscoreKey()
	{
		return hiscoreKey;
	}

	boolean isSpecial()
	{
		return hiscoreKey != null;
	}

	String badgeResource()
	{
		return badgeResource;
	}

	String displayName()
	{
		return displayName;
	}
}
