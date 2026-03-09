package com.killclog;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.util.ImageUtil;

/** Pure query helpers on HiscoreResult / ClogResult — no state, no side effects. */
final class LookupQueries
{
	private LookupQueries()
	{
	}

	static int sumBossKills(HiscoreResult result)
	{
		if (result == null) return 0;
		int total = 0;
		for (int kc : result.getBossKills().values())
		{
			if (kc > 0) total += kc;
		}
		return total;
	}

	static int countBossesWithKc(HiscoreResult result)
	{
		if (result == null) return 0;
		int count = 0;
		for (int kc : result.getBossKills().values())
		{
			if (kc > 0) count++;
		}
		return count;
	}

	static String getMostKilledBoss(HiscoreResult result)
	{
		if (result == null) return null;
		String best = null;
		int bestKc = 0;
		for (Map.Entry<String, Integer> entry : result.getBossKills().entrySet())
		{
			if (entry.getValue() > bestKc)
			{
				bestKc = entry.getValue();
				best = entry.getKey();
			}
		}
		return best;
	}

	static int getMostKilledKc(HiscoreResult result)
	{
		if (result == null) return 0;
		int best = 0;
		for (int kc : result.getBossKills().values())
		{
			if (kc > best) best = kc;
		}
		return best;
	}

	static int countBossesCompleted(Map<HiscoreSkill, TooltipData> tooltipDataMap, Set<HiscoreSkill> bossKeys)
	{
		int count = 0;
		for (HiscoreSkill skill : bossKeys)
		{
			TooltipData data = tooltipDataMap.get(skill);
			if (data != null && data.totalItems > 0 && data.obtainedCount >= data.totalItems)
			{
				count++;
			}
		}
		return count;
	}

	static int countBossesWithClog(Map<HiscoreSkill, TooltipData> tooltipDataMap, Set<HiscoreSkill> bossKeys)
	{
		int count = 0;
		for (HiscoreSkill skill : bossKeys)
		{
			TooltipData data = tooltipDataMap.get(skill);
			if (data != null && data.totalItems > 0) count++;
		}
		return count;
	}

	static List<ClogResult.ClogItem> getRecentItems(ClogResult result, int limit)
	{
		List<ClogResult.ClogItem> all = new ArrayList<>();
		if (result == null)
		{
			return all;
		}
		for (List<ClogResult.ClogItem> items : result.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items)
			{
				if (item.getDate() != null)
				{
					all.add(item);
				}
			}
		}
		all.sort((a, b) -> b.getDate().compareTo(a.getDate()));
		List<ClogResult.ClogItem> unique = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (ClogResult.ClogItem item : all)
		{
			if (seen.add(item.getId()) && unique.size() < limit)
			{
				unique.add(item);
			}
		}
		return unique;
	}

	static int getClogItemCount(ClogResult result, String category, int itemId)
	{
		if (result == null) return 0;
		List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(category);
		if (obtained == null) return 0;
		for (ClogResult.ClogItem item : obtained)
		{
			if (item.getId() == itemId) return item.getCount();
		}
		return 0;
	}

	static BufferedImage getAccountBadge(HiscoreResult result)
	{
		if (result == null) return null;
		String resource = ClogHelper.accountBadgeResource(result.getAccountType());
		if (resource == null) return null;
		try
		{
			return ImageUtil.loadImageResource(HiscorePanel.class, resource);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	static String getAccountLabel(HiscoreResult result)
	{
		if (result == null) return null;
		return ClogHelper.accountLabel(result.getAccountType());
	}

	static String getPrestige(HiscoreResult result)
	{
		if (result == null) return null;
		boolean maxed = result.getTotalLevel() >= PanelData.MAX_TOTAL_LEVEL;
		boolean infernal = result.getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return "Maxed Infernal";
		if (maxed) return "Maxed";
		if (infernal) return "Infernal";
		return null;
	}

	static Set<Integer> getObtainedPetIds(ClogResult result)
	{
		if (result == null) return new HashSet<>();
		List<ClogResult.ClogItem> pets = result.getObtainedItems().get("all_pets");
		Set<Integer> ids = new HashSet<>();
		if (pets != null)
		{
			for (ClogResult.ClogItem item : pets)
			{
				ids.add(item.getId());
			}
		}
		return ids;
	}

	static String syncLine(String lastChanged, boolean stale)
	{
		if (lastChanged == null || lastChanged.isEmpty()) return null;
		try
		{
			LocalDateTime syncTime = LocalDateTime.parse(lastChanged,
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			String pattern = stale ? "MMM d ''yy" : "MMM d";
			return syncTime.format(DateTimeFormatter.ofPattern(pattern));
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	static boolean isSyncStale(String lastChanged, int days)
	{
		if (lastChanged == null || lastChanged.isEmpty()) return true;
		try
		{
			LocalDateTime syncTime = LocalDateTime.parse(lastChanged,
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			return syncTime.isBefore(LocalDateTime.now().minusDays(days));
		}
		catch (DateTimeParseException e)
		{
			return true;
		}
	}
}
