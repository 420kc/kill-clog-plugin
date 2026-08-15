package com.killclog;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;

/** Builds a card from the lookup caches already painted in the panel. */
final class ProfileCardDataBuilder
{
	private static final int MAX_PET_SPRITES = 24;
	private static final DateTimeFormatter CARD_DATE =
		DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US);

	private final ItemManager itemManager;
	private final AccountBadgeResolver accountBadges;
	private final PanelAccountTypes accountTypes;
	private final PanelIconCache iconCache;

	ProfileCardDataBuilder(ItemManager itemManager, AccountBadgeResolver accountBadges,
		PanelAccountTypes accountTypes, PanelIconCache iconCache)
	{
		this.itemManager = itemManager;
		this.accountBadges = accountBadges;
		this.accountTypes = accountTypes;
		this.iconCache = iconCache;
	}

	@Nullable
	ProfileCard.Data build(LookupSession lookupSession, @Nullable String displayedRsn,
		@Nullable String localRsn, boolean syncConfirmed)
	{
		ClogResult clog = lookupSession.getClogResult();
		String name = cardName(displayedRsn, lookupSession.getCurrentLookupRsn(), clog);
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		if (!canBuild(name, localRsn, clog != null, hiscore != null))
		{
			return null;
		}

		ProfileCard.Data data = new ProfileCard.Data();
		data.rsn = name;
		AccountDisplay display = accountTypes.displayIdentity(hiscore, clog, name);
		data.accountLabel = AccountBadgeResolver.label(display);
		data.accountIcon = accountBadges.badge(display);
		data.pluginIcon = KillClogIcons.resizedPluginIcon(18, 18, itemManager);
		data.overallRank = hiscore.getOverallRank();
		data.combatLevel = hiscore.getCombatLevel();
		data.totalLevel = hiscore.getTotalLevel();
		data.totalXp = hiscore.getTotalXp();
		data.prestige = LookupQueries.getPrestige(hiscore);
		data.ehb = EhbRates.compute(hiscore, LookupQueries.accountType(hiscore, clog));
		data.totalClues = hiscore.getActivityScore("Clue Scrolls (all)");
		data.bossesWithKc = LookupQueries.countBossesWithKc(hiscore);
		data.totalBosses = PanelData.bossCount();
		data.profileUrl = syncConfirmed ? profileUrl(name) : null;
		data.createdDate = LocalDate.now().format(CARD_DATE);
		CombatAchievementResult ca = lookupSession.getCaResult();
		if (ca != null)
		{
			CombatAchievementTier tier = ca.getTier();
			data.caTier = tier != null ? tier.name() : "NO TIER";
			data.combatTasksCompleted = 0;
			data.totalCombatTasks = 0;
			for (CombatAchievementTier loopTier : CombatAchievementTier.values())
			{
				data.combatTasksCompleted += ca.getCompleted(loopTier);
				data.totalCombatTasks += ca.getTotal(loopTier);
			}
		}

		if (clog != null)
		{
			populateClog(data, clog, lookupSession.getClogLastChanged());
		}
		return data;
	}

	private void populateClog(ProfileCard.Data data, ClogResult clog,
		@Nullable String lastChanged)
	{
		int[] totals = ClogHelper.sumClogTotals(clog);
		data.obtained = totals[0];
		data.total = totals[1];
		data.tierName = ClogHelper.getClogTierName(totals[0], totals[1]);
		data.tierIcon = data.tierName != null
			? iconCache.clogTierImages().get(data.tierName) : null;

		List<ClogResult.ClogItem> obtainedPets = clog.getObtainedItems().get("all_pets");
		if (obtainedPets != null)
		{
			Set<Integer> seen = new HashSet<>();
			data.pets = 0;
			data.petSprites = new BufferedImage[Math.min(obtainedPets.size(), MAX_PET_SPRITES)];
			int sprite = 0;
			for (ClogResult.ClogItem pet : obtainedPets)
			{
				if (!seen.add(pet.getId()))
				{
					continue;
				}
				data.pets++;
				if (sprite < data.petSprites.length)
				{
					data.petSprites[sprite++] = itemManager.getImage(pet.getId(), 1, false);
				}
			}
			if (sprite < data.petSprites.length)
			{
				data.petSprites = java.util.Arrays.copyOf(data.petSprites, sprite);
			}
		}

		List<ClogResult.ClogItem> recent = LookupQueries.getRecentItems(clog, 6);
		data.recentSprites = new BufferedImage[recent.size()];
		data.recentDates = new String[recent.size()];
		for (int i = 0; i < recent.size(); i++)
		{
			ClogResult.ClogItem item = recent.get(i);
			data.recentSprites[i] = itemManager.getImage(item.getId(), 1, false);
			data.recentDates[i] = ClogSummaryTooltip.shortDate(item.getDate());
		}

		boolean stale = LookupQueries.isSyncStale(lastChanged, 90);
		data.updated = LookupQueries.syncLine(lastChanged, stale);
	}

	static boolean isSelfPlayer(@Nullable String displayedRsn, @Nullable String localRsn)
	{
		return displayedRsn != null && localRsn != null
			&& displayedRsn.trim().equalsIgnoreCase(localRsn.trim());
	}

	static boolean canBuild(@Nullable String displayedRsn, @Nullable String localRsn,
		boolean hasClog, boolean hasHiscore)
	{
		return isSelfPlayer(displayedRsn, localRsn) && hasClog && hasHiscore;
	}

	static String profileUrl(String name)
	{
		return "killclog.com/p/" + name.trim().replace(" ", "-");
	}

	private static String cardName(@Nullable String displayedRsn,
		@Nullable String lookupRsn, @Nullable ClogResult clog)
	{
		if (displayedRsn != null && !displayedRsn.isBlank())
		{
			return displayedRsn.trim();
		}
		if (clog != null && clog.getPlayerName() != null && !clog.getPlayerName().isBlank())
		{
			return clog.getPlayerName();
		}
		return lookupRsn != null && !lookupRsn.isBlank() ? lookupRsn.trim() : "Unknown player";
	}
}
