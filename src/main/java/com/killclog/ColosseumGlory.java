package com.killclog;


/**
 * Sol Heredit's card leads with the player's Colosseum Glory, as the game's hiscores show it, once they have any;
 * kills otherwise.
 */
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

	/**
	 * Whether a boss card leads with Glory instead of kills: Sol Heredit, once the player has Glory, on either side
	 * of a comparison, synced log or not, as the game's own hiscores show it.
	 */
	static boolean leadsCard(String bossName, HiscoreResult result)
	{
		return replacesKc(bossName) && isVisible(score(result));
	}

	static boolean isVisible(int glory)
	{
		return glory > 0;
	}

	static String format(int glory)
	{
		return isVisible(glory) ? TitleTooltip.grouped(glory) : "--";
	}

	static boolean hasHeaderScore(int glory, int kc)
	{
		return isVisible(glory) || kc >= 0;
	}

	static String headerLabel(int glory)
	{
		return isVisible(glory) ? LABEL : "KC: ";
	}

	static String headerValue(int glory, int kc)
	{
		return isVisible(glory) ? format(glory) : ClogHelper.formatKc(kc);
	}
}
