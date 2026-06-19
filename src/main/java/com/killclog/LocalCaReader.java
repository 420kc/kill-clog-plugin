package com.killclog;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarbitID;

final class LocalCaReader
{
	boolean isCaVarbit(int id)
	{
		return id == -1
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER
			|| id == VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER;
	}

	boolean capture(Client client, LocalCaCache localCaCache)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return false;
		}

		String name = local.getName();
		localCaCache.setActivePlayer(name);
		localCaCache.cacheResult(name, completed(client));
		return true;
	}

	private Map<CombatAchievementTier, Integer> completed(Client client)
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		completed.put(CombatAchievementTier.EASY, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY));
		completed.put(CombatAchievementTier.MEDIUM, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM));
		completed.put(CombatAchievementTier.HARD, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD));
		completed.put(CombatAchievementTier.ELITE, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE));
		completed.put(CombatAchievementTier.MASTER, client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER));
		completed.put(CombatAchievementTier.GRANDMASTER,
			client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER));
		return completed;
	}
}
