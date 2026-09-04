package com.killclog;

import java.util.Locale;

final class ColosseumGlory
{
	static final String BOSS_NAME = "Sol Heredit";
	static final String ACTIVITY_NAME = "Colosseum Glory";
	static final String LABEL = "Glory: ";

	private ColosseumGlory()
	{
	}

	static boolean replacesKc(String bossName)
	{
		return BOSS_NAME.equals(bossName);
	}

	static int score(HiscoreResult result)
	{
		return result != null ? result.getActivityScore(ACTIVITY_NAME) : -1;
	}

	static boolean isVisible(int glory)
	{
		return glory > 0;
	}

	static String format(int glory)
	{
		return isVisible(glory) ? String.format(Locale.US, "%,d", glory) : "--";
	}
}
