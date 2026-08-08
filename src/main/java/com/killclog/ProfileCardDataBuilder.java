package com.killclog;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Builds a card from the lookup caches already painted in the panel. */
final class ProfileCardDataBuilder
{
	private static final DateTimeFormatter CARD_DATE =
		DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US);

	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final AccountBadgeResolver accountBadges;
	private final PanelAccountTypes accountTypes;
	private final PanelIconCache iconCache;
	private final PersonalBests personalBests;
	private final KillClogConfig config;

	ProfileCardDataBuilder(ItemManager itemManager, ConfigManager configManager,
		AccountBadgeResolver accountBadges,
		PanelAccountTypes accountTypes, PanelIconCache iconCache,
		PersonalBests personalBests, KillClogConfig config)
	{
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.accountBadges = accountBadges;
		this.accountTypes = accountTypes;
		this.iconCache = iconCache;
		this.personalBests = personalBests;
		this.config = config;
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
		data.totalLevel = ClogHelper.displayTotalLevel(hiscore,
			ClogHelper.virtualTotalLevelEnabled(configManager));
		data.bossesWithKc = LookupQueries.countBossesWithKc(hiscore);
		data.totalBosses = PanelData.bossCount();
		data.profileUrl = syncConfirmed ? profileUrl(name) : null;
		data.createdDate = LocalDate.now().format(CARD_DATE);
		CombatAchievementResult ca = lookupSession.getCaResult();
		if (ca != null)
		{
			CombatAchievementTier tier = ca.getTier();
			data.caTier = tier != null ? tier.name() : "NO TIER";
			data.caPoints = ca.getTotalPoints();
		}

		if (clog != null)
		{
			populateClog(data, clog, lookupSession.getClogLastChanged());
		}
		data.personalBests = personalBests.countForPlayer(name, PanelData.BOSSES);
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
		data.completionColor = ClogHelper.clogColor(totals[0], totals[1], config);

		List<Integer> allPets = clog.getCategoryItems().get("all_pets");
		if (allPets != null)
		{
			data.pets = LookupQueries.getObtainedPetIds(clog).size();
			data.totalPets = allPets.size();
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
