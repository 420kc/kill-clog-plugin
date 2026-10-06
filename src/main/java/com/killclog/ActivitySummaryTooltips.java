package com.killclog;

import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;

final class ActivitySummaryTooltips
{
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final Cells cells;
	private final TooltipController tooltipController;
	private final ItemManager itemManager;
	private final CaRewardSprites caRewardSprites;
	private final Supplier<Boolean> wikiLinks;

	ActivitySummaryTooltips(LookupSession lookupSession, ComparisonController comparison,
		Cells cells, TooltipController tooltipController, ItemManager itemManager,
		CaRewardSprites caRewardSprites,
		Supplier<Boolean> wikiLinks)
	{
		this.lookupSession = lookupSession;
		this.comparison = comparison;
		this.cells = cells;
		this.tooltipController = tooltipController;
		this.itemManager = itemManager;
		this.caRewardSprites = caRewardSprites;
		this.wikiLinks = wikiLinks;
	}

	JToolTip buildPvm(JLabel owner, JPanel parentCell)
	{
		JToolTip tip = soloOrPair(owner,
			pvmTooltip(owner, lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), lookupSession.getCaResult(),
				cells.getTooltipDataMap()),
			() -> pvmTooltip(owner, comparison.getCompareHiscoreResult(),
				comparison.getCompareClogResult(), comparison.getCompareCaResult(),
				comparison.getCompareTooltipDataMap()));
		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	/** One player's Combat Summary card: solo mode shows it alone, comparison pairs two. */
	private PvmSummaryTooltip pvmTooltip(JLabel owner, @Nullable HiscoreResult hiscore,
		@Nullable ClogResult clog, @Nullable CombatAchievementResult ca,
		Map<HiscoreSkill, TooltipData> tooltipData)
	{
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setComponent(owner);
		// The combat cell's own icon, so the card repeats exactly what the panel shows.
		if (owner.getIcon() instanceof ImageIcon)
		{
			tip.setCombatIcon(((ImageIcon) owner.getIcon()).getImage());
		}
		tip.setWikiLinksEnabled(wikiLinks.get());
		tip.setData(
			hiscore != null ? hiscore.getCombatLevelExact() : 0,
			LookupQueries.sumBossKills(hiscore),
			LookupQueries.countBossesWithKc(hiscore),
			PanelData.bossCount(),
			LookupQueries.getMostKilledBoss(hiscore),
			LookupQueries.getMostKilledKc(hiscore)
		);
		tip.setPvp(hiscore, clog, cells.getPvpActivityIcons());
		tip.setEhb(EhbRates.compute(hiscore, LookupQueries.accountType(hiscore, clog)));
		tip.setSlayer(hiscore, clog);
		if (clog != null)
		{
			tip.setCompletion(
				LookupQueries.countBossesCompleted(tooltipData, cells.getBossLabels().keySet()),
				LookupQueries.countBossesWithClog(tooltipData, cells.getBossLabels().keySet()));
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
		// A raid row opens that raid's popup where the summary was, like the Clue Summary's rares.
		tip.setOnOpenRaid((press, raid) -> tooltipController.pinTooltipFromPress(
			owner, (JPanel) owner.getParent(), press, cells.buildBossTooltipFor(owner, raid)));
		if (ca != null)
		{
			tip.setCombatAchievements(ca, caRewardSprites.sprite(ca.getReward(), PvmSummaryTooltip.ICON_SIZE));
		}
		return tip;
	}

	JToolTip buildSkills(JLabel owner)
	{
		JToolTip tip = soloOrPair(owner, skillsTooltip(owner, lookupSession.getHiscoreResult()),
			() -> skillsTooltip(owner, comparison.getCompareHiscoreResult()));
		if (owner.getParent() instanceof JPanel)
		{
			tooltipController.keepTooltipOnHover(tip, (JPanel) owner.getParent());
		}
		return tip;
	}

	/** The card alone, or beside the rival's while comparing. */
	private JToolTip soloOrPair(JLabel owner, JToolTip solo, Supplier<JToolTip> rival)
	{
		return comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null
			? comparison.wrapSideBySide(owner, solo, rival.get()) : solo;
	}

	/** One player's skills summary card: solo mode shows it alone, comparison pairs two. */
	private SkillsTooltip skillsTooltip(JLabel owner, @Nullable HiscoreResult result)
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setComponent(owner);
		tip.setData(result);
		return tip;
	}
}
