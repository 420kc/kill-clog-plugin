package com.killclog;

import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import net.runelite.client.game.ItemManager;

final class ActivitySummaryTooltips
{
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final Cells cells;
	private final TooltipController tooltipController;
	private final ItemManager itemManager;
	private final CaRewardSprites caRewardSprites;
	private final Supplier<String> primaryName;
	private final Supplier<Boolean> wikiLinks;
	private final Supplier<Boolean> virtualLevels;

	ActivitySummaryTooltips(LookupSession lookupSession, ComparisonController comparison,
		Cells cells, TooltipController tooltipController, ItemManager itemManager,
		CaRewardSprites caRewardSprites, Supplier<String> primaryName,
		Supplier<Boolean> wikiLinks, Supplier<Boolean> virtualLevels)
	{
		this.lookupSession = lookupSession;
		this.comparison = comparison;
		this.cells = cells;
		this.tooltipController = tooltipController;
		this.itemManager = itemManager;
		this.caRewardSprites = caRewardSprites;
		this.primaryName = primaryName;
		this.wikiLinks = wikiLinks;
		this.virtualLevels = virtualLevels;
	}

	JToolTip buildPvm(JLabel owner, JPanel parentCell)
	{
		if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
		{
			ComparePvmSummaryTooltip cmp = new ComparePvmSummaryTooltip();
			cmp.setComponent(owner);
			cmp.setWikiLinksEnabled(wikiLinks.get());
			HiscoreResult blueHs = lookupSession.getHiscoreResult();
			HiscoreResult redHs = comparison.getCompareHiscoreResult();
			ClogResult blueClog = lookupSession.getClogResult();
			ClogResult redClog = comparison.getCompareClogResult();
			String blueName = primaryName.get() != null ? primaryName.get() : "--";
			String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";

			cmp.setBlueData(blueName,
				blueHs != null ? blueHs.getCombatLevel() : 0,
				LookupQueries.sumBossKills(blueHs),
				LookupQueries.countBossesWithKc(blueHs),
				PanelData.bossCount(),
				LookupQueries.getMostKilledBoss(blueHs),
				LookupQueries.getMostKilledKc(blueHs));
			cmp.setRedData(redName,
				redHs.getCombatLevel(),
				LookupQueries.sumBossKills(redHs),
				LookupQueries.countBossesWithKc(redHs),
				PanelData.bossCount(),
				LookupQueries.getMostKilledBoss(redHs),
				LookupQueries.getMostKilledKc(redHs));
			cmp.setBlueEhb(EhbRates.compute(blueHs,
				LookupQueries.accountType(blueHs, blueClog)));
			cmp.setRedEhb(EhbRates.compute(redHs,
				LookupQueries.accountType(redHs, redClog)));
			cmp.setBlueSlayer(blueClog);
			cmp.setRedSlayer(redClog);

			if (blueClog != null)
			{
				cmp.setBlueCompletion(
					LookupQueries.countBossesCompleted(cells.getTooltipDataMap(), cells.getBossLabels().keySet()),
					LookupQueries.countBossesWithClog(cells.getTooltipDataMap(), cells.getBossLabels().keySet()));
			}
			if (redClog != null)
			{
				cmp.setRedCompletion(
					LookupQueries.countBossesCompleted(comparison.getCompareTooltipDataMap(), cells.getBossLabels().keySet()),
					LookupQueries.countBossesWithClog(comparison.getCompareTooltipDataMap(), cells.getBossLabels().keySet()));
			}

			CombatAchievementResult blueCa = lookupSession.getCaResult();
			if (blueCa != null)
			{
				cmp.setBlueCa(blueCa, caRewardSprites.sprite(blueCa.getReward(), 14));
			}
			CombatAchievementResult redCa = comparison.getCompareCaResult();
			if (redCa != null)
			{
				cmp.setRedCa(redCa, caRewardSprites.sprite(redCa.getReward(), 14));
			}

			cmp.setBlueMegarares(
				LookupQueries.getClogItemCount(blueClog, PanelData.COX_CATEGORY, PanelData.TWISTED_BOW_ITEM_ID),
				LookupQueries.getClogItemCount(blueClog, PanelData.TOB_CATEGORY, PanelData.SCYTHE_ITEM_ID),
				LookupQueries.getClogItemCount(blueClog, PanelData.TOA_CATEGORY, PanelData.SHADOW_ITEM_ID),
				itemManager);
			cmp.setRedMegarares(
				LookupQueries.getClogItemCount(redClog, PanelData.COX_CATEGORY, PanelData.TWISTED_BOW_ITEM_ID),
				LookupQueries.getClogItemCount(redClog, PanelData.TOB_CATEGORY, PanelData.SCYTHE_ITEM_ID),
				LookupQueries.getClogItemCount(redClog, PanelData.TOA_CATEGORY, PanelData.SHADOW_ITEM_ID),
				itemManager);
			cmp.setBlueSuperiors(
				LookupQueries.getClogItemCount(blueClog,
					PanelData.SLAYER_CATEGORY, PanelData.IMBUED_HEART_ITEM_ID),
				LookupQueries.getClogItemCount(blueClog,
					PanelData.SLAYER_CATEGORY, PanelData.ETERNAL_GEM_ITEM_ID),
				itemManager);
			cmp.setRedSuperiors(
				LookupQueries.getClogItemCount(redClog,
					PanelData.SLAYER_CATEGORY, PanelData.IMBUED_HEART_ITEM_ID),
				LookupQueries.getClogItemCount(redClog,
					PanelData.SLAYER_CATEGORY, PanelData.ETERNAL_GEM_ITEM_ID),
				itemManager);

			if (blueHs != null)
			{
				cmp.setBlueRaids(blueHs, blueClog);
			}
			cmp.setRedRaids(redHs, redClog);

			tooltipController.keepTooltipOnHover(cmp, parentCell);
			return cmp;
		}

		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setComponent(owner);
		tip.setWikiLinksEnabled(wikiLinks.get());
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		ClogResult clog = lookupSession.getClogResult();
		tip.setData(
			hiscore != null ? hiscore.getCombatLevel() : 0,
			LookupQueries.sumBossKills(hiscore),
			LookupQueries.countBossesWithKc(hiscore),
			PanelData.bossCount(),
			LookupQueries.getMostKilledBoss(hiscore),
			LookupQueries.getMostKilledKc(hiscore)
		);
		tip.setEhb(EhbRates.compute(hiscore, LookupQueries.accountType(hiscore, clog)));
		tip.setSlayer(hiscore, clog);
		if (clog != null)
		{
			tip.setCompletion(
				LookupQueries.countBossesCompleted(cells.getTooltipDataMap(), cells.getBossLabels().keySet()),
				LookupQueries.countBossesWithClog(cells.getTooltipDataMap(), cells.getBossLabels().keySet()));
		}
		tip.setMegarares(
			LookupQueries.getClogItemCount(clog, PanelData.COX_CATEGORY, PanelData.TWISTED_BOW_ITEM_ID),
			LookupQueries.getClogItemCount(clog, PanelData.TOB_CATEGORY, PanelData.SCYTHE_ITEM_ID),
			LookupQueries.getClogItemCount(clog, PanelData.TOA_CATEGORY, PanelData.SHADOW_ITEM_ID),
			itemManager
		);
		tip.setSuperiors(
			LookupQueries.getClogItemCount(clog,
				PanelData.SLAYER_CATEGORY, PanelData.IMBUED_HEART_ITEM_ID),
			LookupQueries.getClogItemCount(clog,
				PanelData.SLAYER_CATEGORY, PanelData.ETERNAL_GEM_ITEM_ID),
			itemManager
		);
		if (hiscore != null)
		{
			tip.setRaids(hiscore, clog);
		}
		CombatAchievementResult ca = lookupSession.getCaResult();
		if (ca != null)
		{
			tip.setCombatAchievements(ca, caRewardSprites.sprite(ca.getReward(), 16));
		}
		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	JToolTip buildSkills(JLabel owner)
	{
		if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
		{
			CompareSkillSummaryTooltip cmp = new CompareSkillSummaryTooltip();
			cmp.setComponent(owner);
			cmp.setVirtualLevels(virtualLevels.get());
			String blueName = primaryName.get() != null ? primaryName.get() : "--";
			String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
			cmp.setData(blueName, lookupSession.getHiscoreResult(),
				redName, comparison.getCompareHiscoreResult());
			return cmp;
		}
		SkillsTooltip tip = new SkillsTooltip();
		tip.setComponent(owner);
		tip.setVirtualLevels(virtualLevels.get());
		HiscoreResult result = lookupSession.getHiscoreResult();
		tip.setData(result);
		return tip;
	}
}
