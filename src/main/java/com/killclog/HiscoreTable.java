package com.killclog;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Which Jagex hiscore table supplied the rank data.
 *
 * This is separate from AccountType. A player can be a regular, ironman, or UIM
 * account type, while pure/skiller and Hardcore Main hiscores are separate specialty tables.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
enum HiscoreTable
{
	STANDARD(null, null, null),
	ONE_DEFENCE("hiscore_oldschool_skiller_defence", "pure.png", "Pure"),
	SKILLER("hiscore_oldschool_skiller", "level_3_skiller.png", "Skiller"),
	// Hardcore Worlds' characters, a main account's mode (RuneLite 1.13.6's HiscoreEndpoint.HARDCORE_MAIN).
	HARDCORE_MAIN("hiscore_oldschool_hardcore_mains", "hardcore_main.png", "Hardcore Main");

	private final String hiscoreKey;
	private final String badgeResource;
	private final String displayName;

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
