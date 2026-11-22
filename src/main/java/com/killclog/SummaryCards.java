package com.killclog;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.*;
import javax.annotation.Nullable;
import javax.swing.*;
import lombok.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * The summary cards the panel's name, Collection Log, combat and total level cells open: the Player Summary, the
 * Collection Log, the Combat Summary and Experience &amp; Efficiency, alone or beside the rival's while comparing, built
 * from the lookup's results each time one opens. The panel only wires them; the Clue Summary is its cell's (Cells).
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SummaryCards
{
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final Cells cells;
	private final TooltipController tooltipController;
	private final ItemManager itemManager;
	private final Supplier<CardLinks> links;
	private final ClogPages clogPages;
	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final PlayerPortraits portraits;
	private final PanelIconCache iconCache;
	private final UnsyncedClogCatalog unsyncedCatalog;
	// Who is logged in now (a lookup of them is your own), and the comparison's blue side's name: the panel's to keep.
	private final Supplier<String> localRsn;
	private final Supplier<String> blueName;
	// The game's own log once read, which counts a log on its own items, and the clans once the client has them.
	@Setter
	@Nullable
	private ClogIndex clogIndex;
	@Setter
	@Nullable
	private PlayerClans clans;

	/**
	 * The Player Summary, or both players' side by side while comparing, the blue side under its comparison name; held
	 * open over the name it opened from, so its pets can be hovered. The account rows' identities are the panel's.
	 */
	JToolTip buildPlayer(JLabel owner, String soloName, @Nullable AccountDisplay blue, @Nullable AccountDisplay red)
	{
		return comparison.held(owner, playerCard(owner, lookupSession.getHiscoreResult(), lookupSession.getClogResult(),
			comparison.showsPair() ? blueName.get() : soloName, blue),
			() -> playerCard(owner, comparison.getCompareHiscoreResult(), comparison.getCompareClogResult(),
				comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--", red));
	}

	/** One player's Player Summary card. */
	SummaryTooltip playerCard(JComponent owner, @Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		String shownName, @Nullable AccountDisplay display)
	{
		SummaryTooltip tip = new SummaryTooltip();
		tip.setComponent(owner);
		// A Kill Clog syncer who published their character stands where the prestige cape would; the cape and its
		// prestige then show under the Combat Summary's title instead.
		BufferedImage character = portraits.forSummary(clog);
		tip.setData(
			shownName.isEmpty() ? "Player" : shownName,
			hiscore != null ? hiscore.getOverallRank() : -1,
			character != null ? character : iconCache.capeFor(hiscore),
			AccountBadgeResolver.badge(display),
			AccountBadgeResolver.label(display),
			character != null ? null : LookupQueries.getPrestige(hiscore)
		);
		if (clog != null)
		{
			tip.setSources(CardSources.of(clog, clogService.onTemple(shownName), runeProfileService.hasProfile(shownName),
				lookupSession.onKillclog(shownName), SummaryTooltip.SOURCE_PREFIX));
		}
		if (clans != null)
		{
			tip.setClan(clans.name(shownName, clog), clans.icon(shownName, clog));
		}
		tip.setLinks(links.get());
		if (clog != null)
		{
			List<Integer> allPets = clog.getCategoryItems().get("all_pets");
			tip.setPets(allPets, LookupQueries.getObtainedPetCounts(clog), itemManager, clog::getItemName);
		}
		return tip;
	}

	/** The looked-up player's Collection Log card. */
	JToolTip buildClog(JComponent owner, @Nullable String rsn)
	{
		return clogCard(owner, lookupSession.getHiscoreResult(), lookupSession.getClogResult(), rsn,
			lookupSession.getClogLastChanged());
	}

	/** Both players' Collection Log cards side by side, for the comparison's totals bar. */
	JToolTip buildClogPair(JComponent owner)
	{
		ClogResult redClog = comparison.getCompareClogResult();
		return comparison.wrapSideBySide(owner,
			clogCard(owner, lookupSession.getHiscoreResult(), lookupSession.getClogResult(), blueName.get(),
				lookupSession.getClogLastChanged()),
			clogCard(owner, comparison.getCompareHiscoreResult(), redClog, comparison.getCompareRsn(),
				redClog != null ? redClog.getLastChanged() : null));
	}

	/** One player's Collection Log card. */
	ClogSummaryTooltip clogCard(JComponent owner, @Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable String playerRsn, @Nullable String lastChanged)
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setComponent(owner);
		tip.setLinks(links.get());
		if (clog != null)
		{
			int[] totals = totals(clog);
			tip.setTierData(totals[0], totals[1], iconCache.clogTierImages(), itemManager);
			if (clogIndex != null)
			{
				tip.setTabs(clogIndex.tabProgress(clog, totals), itemManager);
				tip.setOnOpenTab((press, tab) -> clogPages.openTab(owner, press, tab, "< Collection Log"));
			}
			tip.setClogSources(clog, clogService.onTemple(playerRsn), runeProfileService.hasProfile(playerRsn),
				lookupSession.onKillclog(playerRsn));
			boolean stale = LookupQueries.isSyncStale(lastChanged, 90);
			String sync = LookupQueries.syncLine(lastChanged, stale);
			if (sync != null) tip.setSyncData(sync, stale);
			tip.setRareItems(ClogHelper.rareItems(clog), clog, itemManager);
			tip.setRecentItems(LookupQueries.getRecentItems(clog, ClogSummaryTooltip.RECENT_SHOWN,
				clogIndex != null ? clogIndex::canonicalItemId : IntUnaryOperator.identity()), clog, itemManager);
		}
		else
		{
			if (lookupSession.offersSetup(localRsn.get(), playerRsn))
			{
				tip.setFirstTimeSetup();
			}
			else if (hiscore != null)
			{
				tip.setNotice(cells.noClogNotice(playerRsn, hiscore));
			}
			else
			{
				// No player yet: preview the log's shape from the catalog.
				ClogResult catalog = unsyncedCatalog.result();
				if (catalog != null)
				{
					tip.setTitle("Collection Log");
					tip.setObtained(-1, totals(catalog)[1]);
				}
				else
				{
					tip.setNotice(Cells.CATALOG_NOTICE);
				}
			}
			if (hiscore != null)
			{
				int[] totals = ClogHelper.summaryTotals(null, hiscore, unsyncedCatalog.result());
				if (totals[0] >= 0) tip.setObtained(totals[0], totals[1]);
			}
		}
		if (hiscore != null && hiscore.isRankDataAvailable())
		{
			tip.setRank(hiscore.getActivityRank(HiscoreSkill.COLLECTIONS_LOGGED.getName()));
		}
		return tip;
	}

	/** A log's obtained and total counts, counted on the game's own items once the game's log has been read. */
	private int[] totals(ClogResult log)
	{
		return clogIndex != null ? ClogHelper.sumClogTotals(log, clogIndex::canonicalItemId) : ClogHelper.sumClogTotals(log);
	}

	JToolTip buildPvm(JLabel owner)
	{
		PvmSummaryTooltip blue = pvmTooltip(owner, lookupSession.getHiscoreResult(), lookupSession.getClogResult(),
			lookupSession.getCaResult(), cells.getTooltipDataMap());
		return comparison.held(owner, blue, () ->
		{
			PvmSummaryTooltip red = pvmTooltip(owner, comparison.getCompareHiscoreResult(),
				comparison.getCompareClogResult(), comparison.getCompareCaResult(), comparison.getCompareTooltipDataMap());
			PvmSummaryTooltip.lineUp(blue, red);
			return red;
		});
	}

	/** One player's Combat Summary card: solo mode shows it alone, comparison pairs two. */
	private PvmSummaryTooltip pvmTooltip(JLabel owner, @Nullable HiscoreResult hiscore,
		@Nullable ClogResult clog, @Nullable CombatAchievementResult ca,
		Map<HiscoreSkill, TooltipData> tooltipData)
	{
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setComponent(owner);
		tip.setCombatIcon(Cells.icon(owner));
		tip.setLinks(links.get());
		// Dizana's max cape takes the plain max cape's place, as the infernal max cape does; beside an infernal max
		// cape, both show.
		int quiver = LookupQueries.quiverItemId(hiscore, clog);
		boolean maxCape = quiver == PanelData.DIZANAS_MAX_CAPE_ITEM_ID;
		int cape = LookupQueries.capeItemId(hiscore);
		if (cape >= 0 && (!maxCape || LookupQueries.infernal(hiscore)))
		{
			tip.addGear(cape, LookupQueries.getPrestige(hiscore), itemManager);
		}
		if (quiver >= 0)
		{
			tip.addGear(quiver, maxCape ? "Dizana's max cape" : "Dizana's quiver", itemManager);
		}
		String mostKilled = LookupQueries.getMostKilledBoss(hiscore);
		tip.setData(
			hiscore != null ? hiscore.getCombatLevelExact() : 0,
			LookupQueries.sumBossKills(hiscore),
			// No hiscores (the dormant panel): no count to show, not a count of none.
			hiscore != null ? LookupQueries.countBossesWithKc(hiscore) : -1,
			LookupQueries.bossTotal(hiscore),
			mostKilled,
			mostKilled != null ? hiscore.getKc(mostKilled) : 0
		);
		// The most killed boss's tile: its cell's icon, and its log as its card counts it.
		for (HiscoreSkill boss : cells.getBossLabels().keySet())
		{
			if (PanelData.hiscoreName(boss.getName()).equals(mostKilled))
			{
				TooltipData data = clog != null ? tooltipData.get(boss) : null;
				tip.setMostKilled(boss, cells.bossIcon(boss), data != null ? data.obtainedCount : -1,
					data != null ? data.totalItems : 0);
			}
		}
		tip.setPvp(hiscore, clog, cells.getPvpActivityIcons());
		tip.setEhb(LookupQueries.ehb(hiscore, clog));
		tip.setSlayer(hiscore, clog);
		if (clog != null)
		{
			int[] logs = LookupQueries.bossLogs(tooltipData, cells.getBossLabels().keySet());
			tip.setCompletion(logs[0], logs[1]);
		}
		tip.setMegarares(PanelData.RAIDS.stream()
			.mapToInt(raid -> LookupQueries.getClogItemCount(clog, raid.getPage(), raid.getMegarare())).toArray(), itemManager);
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
		// The Bosses tile opens the Collection Log's Bosses tab where the summary was.
		tip.setOnOpenBosses(press -> clogPages.openTab(owner, press, "Bosses", "< Combat Summary"));
		// A raid row opens that raid's popup where the summary was, like the Clue Summary's rares.
		tip.setOnOpenRaid((press, raid) ->
			tooltipController.openInPlace(owner, press, cells.buildCellTooltipFor(owner, raid), "< Combat Summary"));
		// LMS's and Soul Wars' tiles open their log pages there too.
		tip.setOnOpenPage((press, page) ->
			tooltipController.openInPlace(owner, press, clogPages.page(owner, page), "< Combat Summary"));
		tip.setCombatAchievements(CombatAchievementResult.shown(ca, clog != null ? clog.getSyncedCa() : null), itemManager);
		return tip;
	}

	JToolTip buildSkills(JLabel owner)
	{
		ClogResult blue = lookupSession.getClogResult();
		ClogResult red = comparison.isComparisonMode() ? comparison.getCompareClogResult() : null;
		// Both sides show TempleOSRS's rows when either player has them, so a pair's cards line up.
		boolean temple = SkillsTooltip.onTemple(blue) || SkillsTooltip.onTemple(red);
		return comparison.held(owner, skillsTooltip(owner, lookupSession.getHiscoreResult(), blue, temple),
			() -> skillsTooltip(owner, comparison.getCompareHiscoreResult(), red, temple));
	}

	/** One player's skills summary card: solo mode shows it alone, comparison pairs two. */
	private SkillsTooltip skillsTooltip(JLabel owner, @Nullable HiscoreResult result, @Nullable ClogResult clog,
		boolean temple)
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setComponent(owner);
		tip.setData(result, clog, temple);
		return tip;
	}
}
