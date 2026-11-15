package com.killclog;

import javax.annotation.Nullable;

/**
 * Sol Heredit's card leads with the player's Colosseum Glory, as the game's hiscores show it, once they have any;
 * kills otherwise. Nothing on them should send a player back there (Dylan, 2026-10-12).
 */
final class ColosseumGlory
{
	static final String BOSS_NAME = "Sol Heredit";
	static final String ACTIVITY_NAME = "Colosseum Glory";
	private static final String LABEL = "Glory: ";

	private ColosseumGlory()
	{
	}

	/**
	 * Sol Heredit's card with the player's Glory where its kills lead, on either side of a comparison, synced log or
	 * not: the count every card's lead line reads, so no card paints it its own way.
	 */
	@Nullable
	static TooltipData onCard(String boss, @Nullable TooltipData data, @Nullable HiscoreResult result)
	{
		int glory = result != null ? result.getActivityScore(ACTIVITY_NAME) : -1;
		return data == null || glory <= 0 || !BOSS_NAME.equals(boss) ? data
			: data.toBuilder().kcLabel(LABEL).kc(glory).statLabel(LABEL).statValue(glory).build();
	}
}
