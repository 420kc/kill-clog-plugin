package com.killclog;

import java.util.*;
import net.runelite.api.Client;

/**
 * Reads your Combat Achievement tier counts from the game's varbits and keeps them for this login ({@link
 * LocalCaCache}).
 */
final class LocalCaReader
{
	/** The logged-in player's tiers as the game counts them now, kept for this login. */
	boolean capture(Client client, Session session, LocalCaCache localCaCache)
	{
		if (!session.loggedIn())
		{
			return false;
		}
		localCaCache.cacheResult(session.getName(), session.getAccountHash(), completed(client));
		return true;
	}

	private Map<CombatAchievementTier, Integer> completed(Client client)
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			completed.put(tier, client.getVarbitValue(tier.completedVarbit));
		}
		return completed;
	}
}
