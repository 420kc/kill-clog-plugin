/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the cells of the kill clog panel: builds them, holds the labels,
 * caches tooltip + icon data, renders the looked-up player's values, and
 * routes between single-player and dual-player tooltips. KillClogPanel
 * (layout) and ComparisonController (the comparison's values) talk to Cells
 * directly.
 */
package com.killclog;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Per-skill {@link JLabel} cells, their tooltip data caches, the helpers that
 * construct them, and the renderers that fill them with the looked-up player's
 * values (ComparisonController fills in a comparison's). Single source of truth
 * for "what cells does the panel show, and how do they hover."
 */
public class Cells
{
	private static final String SETUP_NOTICE = "Open your Collection Log to start setup automatically";

	// Deps
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final TooltipController tooltipController;
	private final ComparisonController comparison;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final LookupSession lookupSession;
	private final KillclogService killclogService;
	// Your own PB reader for the viewed game (null: the main game).
	private final Function<String, UnaryOperator<String>> selfPbs;
	private final KillClogConfig config;
	private final UnsyncedClogCatalog unsyncedCatalog;
	private final RankSelector ranks;
	private final Supplier<String> localRsn;

	// Tooltip data caches
	@Getter
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	@Getter
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// Icon caches
	@Getter
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	@Getter
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];
	@Getter
	private final BufferedImage[] rareIcons = new BufferedImage[PanelData.RARE_KEYS.length];

	// Cell labels
	private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> clueTierLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();

	/**
	 * Boss PBs in the viewed game: yours from RuneLite's profiles, read once for the whole grid, anyone else's from
	 * their Kill Clog sync.
	 */
	UnaryOperator<String> pbs(boolean self)
	{
		ClogResult lookupClog = lookupSession.getClogResult();
		String league = lookupSession.league();
		return self ? selfPbs.apply(league)
			: lookupClog == null ? boss -> null
			: boss -> killclogService.pbText(league == null ? lookupClog.getPlayerName()
				: KillclogService.modeKey(league, lookupClog.getPlayerName()), boss);
	}

	public Cells(SpriteManager spriteManager, ItemManager itemManager,
		TooltipController tooltipController, ComparisonController comparison,
		TooltipDataBuilder tooltipDataBuilder, LookupSession lookupSession,
		UnsyncedClogCatalog unsyncedCatalog, KillclogService killclogService,
		Function<String, UnaryOperator<String>> selfPbs, KillClogConfig config, RankSelector ranks,
		Supplier<String> localRsn)
	{
		this.config = config;
		this.ranks = ranks;
		this.localRsn = localRsn;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.tooltipController = tooltipController;
		this.comparison = comparison;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.lookupSession = lookupSession;
		this.killclogService = killclogService;
		this.selfPbs = selfPbs;
		loadPvpActivityIcons();
		this.unsyncedCatalog = unsyncedCatalog;
	}

	// Cell builders

	/** Build the boss grid (3-wide GridLayout) populated with one cell per boss. */
	public JPanel buildBossGrid()
	{
		JPanel grid = new JPanel(new GridLayout(0, 3));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			grid.add(buildBossCell(boss));
		}
		return grid;
	}

	/** Build a single boss cell. Caller hooks listeners onto {@link #getBossLabel} after construction if needed. */
	public JPanel buildBossCell(HiscoreSkill boss)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildBossTooltip(this, boss);
			}
		};
		styleLabel(label, boss.getName());

		loadBossIcon(label, boss.getSpriteId(), icon ->
		{
			originalIcons.put(boss, icon);
			dimmedIcons.put(boss, new ImageIcon(ClogHelper.createDimmedImage(icon)));
		});

		bossLabels.put(boss, label);
		return wrapInCell(label);
	}

	/** Load a 25x25 game sprite into a boss label at cell size, then hand it to the icon sink. */
	private void loadBossIcon(JLabel label, int spriteId, Consumer<ImageIcon> sink)
	{
		PanelIconCache.cellSprite(spriteManager, spriteId, 20, image ->
		{
			ImageIcon icon = new ImageIcon(image);
			label.setIcon(icon);
			sink.accept(icon);
		});
	}

	/** Build the All Clues cell, which opens the Clue Summary. */
	public JPanel buildClueSummaryCell()
	{
		HiscoreSkill activity = HiscoreSkill.CLUE_SCROLL_ALL;
		JLabel label = new JLabel(activity.getName())
		{
			@Override
			public JToolTip createToolTip()
			{
				if (comparison.showsPair())
				{
					return wrapSideBySide(this,
						clueSummaryTooltip(this, lookupSession.getHiscoreResult(),
							lookupSession.getClogResult(), false),
						clueSummaryTooltip(this, comparison.getCompareHiscoreResult(),
							comparison.getCompareClogResult(), true));
				}
				ClueSummaryTooltip tip = clueSummaryTooltip(this, lookupSession.getHiscoreResult(),
					lookupSession.getClogResult(), false);
				tooltipController.keepTooltipOnHover(tip, (JPanel) this.getParent());
				return tip;
			}
		};
		styleLabel(label, activity.getName());

		PanelIconCache.cellSprite(spriteManager, activity.getSpriteId(), 16,
			image -> label.setIcon(new ImageIcon(ImageUtil.resizeCanvas(image, 20, 20))));

		activityLabels.put(activity, label);
		return wrapInCell(label);
	}

	/** Icons for the PvP rows inside the Combat Summary, loaded once. */
	private void loadPvpActivityIcons()
	{
		for (int i = 0; i < PanelData.PVP_ACTIVITIES.length; i++)
		{
			int spriteId = PanelData.PVP_ACTIVITIES[i].getSpriteId();
			if (spriteId == -1)
			{
				continue;
			}
			final int idx = i;
			PanelIconCache.cellSprite(spriteManager, spriteId, 13, icon -> pvpActivityIcons[idx] = icon);
		}
	}

	public JPanel buildClueTierCell(HiscoreSkill tier, int itemId)
	{
		String displayName = capitalizeTier(tier);
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildClueTierTooltip(this, tier, displayName);
			}
		};
		styleLabel(label, tier.getName());
		setItemIcon(label, itemId);
		clueTierLabels.put(tier, label);
		return wrapInCell(label);
	}

	// Primary-side rendering

	/** Standard kc text color (light gray-white) used for any cell that has a non-zero kc / score. */
	static final Color KC_COLOR = new Color(215, 215, 215);

	/**
	 * Write hiscore-driven values into every primary-side cell: bosses
	 * (text + color + dimmed-icon swap), activities
	 * and clue tiers. Mirrors
	 * {@link ComparisonController#updateAllCells()} for the comparison side.
	 */
	public void renderHiscore(HiscoreResult result)
	{
		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			renderBossValue(entry.getKey(), entry.getValue(), result);
		}
		// Activity cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
		{
			HiscoreSkill activity = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(activity.getName());
			showCount(label, score, KC_COLOR);
			tooltipController.setTooltipText(label, " ");
		}

		// Clue tier cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
		{
			HiscoreSkill tier = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(tier.getName());
			showCount(label, score, KC_COLOR);

			String shortName = capitalizeTier(tier);
			int rank = result.getActivityRank(tier.getName());
			tooltipController.setTooltipText(label, rank > 0
				? shortName + "\nRank: {w}" + TitleTooltip.grouped(rank)
				: shortName);
		}
	}

	/** Build the rare clue collections from the clog; the Clue Summary lists and opens them. */
	public void renderClog(ClogResult result)
	{
		putRareRows(result, null);
	}

	/**
	 * Return every cell to its pre-lookup state: dashes in the resting colour,
	 * the resting tooltip, boss cells back on their original icons, and both
	 * tooltip caches emptied. The panel resets its own header alongside.
	 */
	public void reset()
	{
		tooltipDataMap.clear();
		rareTooltips.clear();
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			JLabel label = entry.getValue();
			showCount(label, 0, null);
			tooltipController.setTooltipText(label, " ");
			ImageIcon orig = originalIcons.get(entry.getKey());
			if (orig != null) label.setIcon(orig);
		}
		resetLabels(activityLabels);
		resetLabels(clueTierLabels);
	}


	/** A count cell's number in its colour, or a grey "--" without one: every count cell on the panel. */
	static void showCount(JLabel label, int count, @Nullable Color color)
	{
		label.setText(ClogHelper.pad(ClogHelper.countText(count)));
		label.setForeground(count > 0 ? color : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private void resetLabels(Map<HiscoreSkill, JLabel> labels)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : labels.entrySet())
		{
			JLabel label = entry.getValue();
			showCount(label, 0, null);
			tooltipController.setTooltipText(label, entry.getKey().getName());
		}
	}

	/**
	 * Rebuild the per-cell {@code TooltipData} cache for the primary side from
	 * the current lookup session results. Mirrors what
	 * {@link ComparisonController#enter()} does for the comparison side.
	 */
	public void rebuildPrimaryTooltips()
	{
		tooltipDataMap.clear();
		if (lookupSession.getClogResult() == null)
		{
			rareTooltips.clear();
		}
		String you = localRsn.get();
		boolean self = RsnInputPolicy.sameName(you, lookupSession.getCurrentLookupRsn());
		boolean selfNoCache = lookupSession.getClogResult() == null
			&& lookupSession.offersSetup(you, lookupSession.getCurrentLookupRsn());
		ClogResult catalog = lookupSession.getClogResult() == null && !selfNoCache
			? unsyncedCatalog.result() : null;

		// Boss cells
		UnaryOperator<String> pbs = pbs(self);
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			// A raid's hard mode holds its raid's card, so its cell opens, colors and counts as the raid.
			HiscoreSkill card = PanelData.card(skill);
			String bossName = card.getName();
			String hiscoreName = PanelData.hiscoreName(bossName);
			TooltipData data = TooltipDataBuilder.withHardMode(buildPrimaryBossData(bossName, hiscoreName, selfNoCache, pbs),
				card, lookupSession.getHiscoreResult(), pbs);
			if (data != null)
			{
				tooltipDataMap.put(skill, data);
			}
			tooltipController.setTooltipText(entry.getValue(), " ");
		}
		// Clue tier cells
		if (lookupSession.getClogResult() == null)
		{
			if (!selfNoCache)
			{
				rebuildClueTooltips(null, catalog);
				putRareRows(null, catalog);
			}
			return;
		}
		rebuildClueTooltips(lookupSession.getClogResult(), null);
	}

	/** Each clue tier cell's card, from the synced log or, without one, the catalog's preview. */
	private void rebuildClueTooltips(@Nullable ClogResult clog, @Nullable ClogResult catalog)
	{
		for (HiscoreSkill skill : PanelData.CLUE_CATEGORIES.keySet())
		{
			JLabel label = clueTierLabels.get(skill);
			// A synced log without the tier shows no card, never the preview.
			TooltipData data = label == null ? null
				: tooltipDataBuilder.buildClueTierCard(skill, lookupSession.getHiscoreResult(), clog, catalog);
			if (data != null)
			{
				tooltipDataMap.put(skill, data);
				if (clog != null)
				{
					tooltipDataBuilder.preloadItemImages(data);
				}
			}
			if (label != null && (data != null || clog == null))
			{
				tooltipController.setTooltipText(label, " ");
			}
		}
	}

	private void putRareRows(@Nullable ClogResult clog, @Nullable ClogResult catalog)
	{
		for (int i = 0; i < PanelData.RARE_KEYS.length; i++)
		{
			TooltipData data = tooltipDataBuilder.rareRow(i, clog, catalog);
			if (data != null)
			{
				rareTooltips.put(PanelData.RARE_KEYS[i], data);
			}
		}
	}

	// Tooltip routing

	/**
	 * A panel cell's own card, for whatever else shows it (a list row, a summary, a Collection Log page): a
	 * boss's or a clue tier's. Owner is the anchor label.
	 */
	public JToolTip buildCellTooltipFor(JLabel owner, HiscoreSkill cell)
	{
		return PanelData.CLUE_CATEGORIES.containsKey(cell) ? buildClueTierTooltip(owner, cell, capitalizeTier(cell))
			: buildBossTooltip(owner, cell);
	}

	/**
	 * A Collection Log page's popup: its grid for the player, or both players' side by side. Its title opens the
	 * page's section of the wiki's Collection log, where a boss's title opens the boss.
	 */
	public JToolTip buildPageTooltip(JLabel owner, String name, @Nullable TooltipData blue, @Nullable TooltipData red)
	{
		return gridTooltip(owner, blue, () -> red, name, PanelData.CLOG_WIKI_PAGE + "#" + name);
	}

	private JToolTip buildBossTooltip(JLabel owner, HiscoreSkill boss)
	{
		HiscoreSkill card = PanelData.card(boss);
		TooltipData red = comparison.getCompareTooltipData(boss);
		return gridTooltip(owner, tooltipDataMap.get(boss), () -> red, card.getName(), PanelData.bossWikiPage(card));
	}

	/**
	 * Build a sprite tooltip for a cell - always ImgTooltip, with contextual notice when no data.
	 *
	 * @param owner     the label whose parent cell drives hover tint
	 * @param data      tooltip data, or null for notice-only display
	 * @param gridCols  min columns for the sprite grid
	 * @param name      display name shown as title when data is null
	 * @param result    with {@code rsn}, picks whose card this is
	 */
	JToolTip cellCard(JLabel owner, TooltipData data, int gridCols,
		String name, @Nullable HiscoreResult result, @Nullable String rsn)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		ImgTooltip tip = new ImgTooltip(gridCols);
		tip.setComponent(owner);
		tip.setLinks(config.cardLinks());
		boolean isSolHeredit = ColosseumGlory.replacesKc(name);
		int glory = ColosseumGlory.score(result);

		// Synced clog data with real item counts.
		if (data != null && data.obtainedCount >= 0)
		{
			tip.setTitle(data.name);
			tip.setObtained(data.obtainedCount, data.totalItems);
			boolean hasHeaderScore = isSolHeredit
				? ColosseumGlory.hasHeaderScore(glory, data.kc) : data.kc >= 0;
			if (hasHeaderScore && config.showTooltipKc())
			{
				tip.setInfoLine(isSolHeredit ? ColosseumGlory.headerLabel(glory) : data.kcLabel != null ? data.kcLabel : "KC: ",
					isSolHeredit ? ColosseumGlory.headerValue(glory, data.kc) : ClogHelper.formatKc(data.kc),
					NativeTooltip.valueColor);
				if (data.pb != null && config.showTooltipPb())
				{
					tip.setInfoLinePair(ClogHelper.pbLabel(data.pb), ClogHelper.pbTime(data.pb), NativeTooltip.valueColor);
				}
			}
			else if (data.pb != null && config.showTooltipPb())
			{
				tip.setInfoLine(ClogHelper.pbLabel(data.pb), ClogHelper.pbTime(data.pb), NativeTooltip.valueColor);
			}
			ClogHelper.addHardModeLine(tip, data, config.showTooltipKc(), config.showTooltipPb());
			if (data.allItemIds != null)
			{
				tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
					data.obtainedCounts, data.itemNames, itemManager);
			}
		}
		else if (!ClogHelper.configureNotSynced(tip, data, itemManager, config.showTooltipKc()))
		{
			tip.setTitle(data != null ? data.name : name);
			boolean isSelfNoCache = result != null && lookupSession.offersSetup(localRsn.get(), rsn);
			if (isSelfNoCache)
			{
				tip.setNotice(SETUP_NOTICE);
			}
			else if (result != null)
			{
				tip.setNotice(noClogNotice(rsn, result));
			}
			else
			{
				// Cold and the catalog has not arrived yet; the warm-up
				// rebuild replaces this with the dimmed preview.
				tip.setNotice("Loading catalog...");
			}
		}
		ClogHelper.configureRank(tip, data, result, config.showTooltipRank());
		if (isSolHeredit && ColosseumGlory.isVisible(glory)
			&& config.showTooltipKc() && (data == null || data.obtainedCount < 0))
		{
			tip.setInfoLine(ColosseumGlory.LABEL, ColosseumGlory.format(glory), NativeTooltip.valueColor);
		}

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	// No-data copy stays quiet: another player simply has no synced collection log, or theirs hasn't loaded.
	// No provider names, no blame - just a calm statement.
	String noClogNotice(String rsn, @Nullable HiscoreResult hiscore)
	{
		return LookupSession.frozen(hiscore) ? ranks.logNotice(hiscore)
			: rsn == null || rsn.isEmpty() ? "No collection log synced"
			: lookupSession.logUnanswered(rsn) ? rsn + "'s collection log hasn't loaded"
			: rsn + " hasn't synced a collection log";
	}

	/** One log's card: the player's grid, or both players' side by side, the title opening the wiki. */
	private JToolTip gridTooltip(JLabel owner, @Nullable TooltipData blue, Supplier<TooltipData> red, String name,
		@Nullable String wikiPage)
	{
		JToolTip tip = cellCard(owner, blue, 5, name, lookupSession.getHiscoreResult(), lookupSession.getCurrentLookupRsn());
		linkTitle(tip, wikiPage);
		if (!comparison.isComparisonMode())
		{
			return tip;
		}
		JToolTip rival = cellCard(owner, red.get(), 5, name, comparison.getCompareHiscoreResult(), comparison.getCompareRsn());
		linkTitle(rival, wikiPage);
		return wrapSideBySide(owner, tip, rival);
	}

	private static void linkTitle(JToolTip tip, @Nullable String wikiPage)
	{
		if (tip instanceof TitleTooltip)
		{
			((TitleTooltip) tip).setTitleWikiPage(wikiPage);
		}
	}

	private JToolTip wrapSideBySide(JLabel owner, JToolTip blue, JToolTip red)
	{
		JToolTip tip = comparison.wrapSideBySide(owner, blue, red);
		tooltipController.keepTooltipOnHover(tip, (JPanel) owner.getParent());
		return tip;
	}

	/** Write one boss cell's KC text, color, and icon dim state from the name-keyed hiscore map. */
	private void renderBossValue(HiscoreSkill boss, JLabel label, HiscoreResult result)
	{
		int kc = result.getBossKills().getOrDefault(PanelData.hiscoreName(boss.getName()), -1);
		showCount(label, kc, KC_COLOR);
		ImageIcon orig = originalIcons.get(boss);
		if (orig != null)
		{
			label.setIcon(kc > 0 ? orig : dimmedIcons.get(boss));
		}
	}

	/**
	 * Build one boss cell's primary-side TooltipData: synced clog data when
	 * available, otherwise the unsynced catalog preview (skipped for the
	 * sync-prompt self-lookup).
	 */
	@Nullable
	private TooltipData buildPrimaryBossData(String displayName, String hiscoreName, boolean selfNoCache,
		UnaryOperator<String> pbs)
	{
		if (selfNoCache)
		{
			return null;
		}
		String category = ClogService.bossToCategory(hiscoreName);
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		int rank = hiscore != null ? hiscore.getRank(hiscoreName) : -1;
		int kc = hiscore != null ? hiscore.getKc(hiscoreName) : -1;
		boolean showGlory = ColosseumGlory.leadsCard(displayName, hiscore);
		// Vanilla records pbs on the local profile only; for looked-up players
		// the killclog.com sync is the one source that serves them, cached by
		// the same fetch that raced the clog providers.
		return tooltipDataBuilder.buildBossCard(displayName, category, rank, kc, pbs.apply(displayName),
			lookupSession.getClogResult(), showGlory ? null : "Kills: ", showGlory ? -1 : kc, unsyncedCatalog.result());
	}

	private ClueSummaryTooltip clueSummaryTooltip(JLabel owner, @Nullable HiscoreResult result,
		@Nullable ClogResult clog, boolean rival)
	{
		ClueSummaryTooltip tip = new ClueSummaryTooltip();
		tip.setRareIcons(rareIcons);
		for (int i = 0; i < PanelData.CLUE_TIERS.length; i++)
		{
			tip.setProgress(i + 1, ClogHelper.clogCounts(
				PanelData.CLUE_CATEGORIES.get(PanelData.CLUE_TIERS[i]), clog));
		}
		// The Mimic's one log slot is the 3rd age ring.
		tip.setProgress(7, clog == null ? null : counts(tooltipDataBuilder.buildCustomRareData(
			"Mimic", new int[]{PanelData.THIRD_AGE_RING_ITEM_ID}, clog)));
		for (int i = 0; i < PanelData.RARE_KEYS.length; i++)
		{
			tip.setRare(i, counts(rareData(i, rival)));
		}
		// A rare row, a tier's line or Mimic's opens that card where the summary was, its back the summary.
		tip.setOnOpenRare((press, row) -> openFromClueSummary(owner, press, buildRareTooltipFor(owner, row)));
		tip.setOnOpenLine((press, line) -> openFromClueSummary(owner, press, buildCellTooltipFor(owner,
			line == ClueSummaryTooltip.MIMIC ? HiscoreSkill.MIMIC : PanelData.CLUE_TIERS[line - 1])));
		tip.setComponent(owner);
		tip.setIcons(clueIcons);
		tip.setData(result, config.showTooltipRank());
		return tip;
	}

	private void openFromClueSummary(JLabel owner, MouseEvent press, JToolTip card)
	{
		tooltipController.openInPlace(owner, press, card, "< Clue Summary");
	}

	private JToolTip buildClueTierTooltip(JLabel owner, HiscoreSkill tier, String displayName)
	{
		return gridTooltip(owner, cluesCompleted(tooltipDataMap.get(tier), tier, lookupSession.getHiscoreResult()),
			() -> cluesCompleted(comparison.buildCompareClueTierData(tier), tier, comparison.getCompareHiscoreResult()),
			displayName, PanelData.clueWikiPage(tier));
	}

	/** A clue tier's completed count, labeled as the game's own Collection Log page labels it. */
	static String cluesCompletedLabel(HiscoreSkill tier)
	{
		return capitalizeTier(tier) + " clues completed: ";
	}

	/** A clue card's data reading its tier's completed clues where a boss card reads its KC. */
	@Nullable
	private static TooltipData cluesCompleted(@Nullable TooltipData data, @Nullable HiscoreSkill tier,
		@Nullable HiscoreResult result)
	{
		return data == null || tier == null || result == null ? data
			: data.toBuilder().kcLabel(cluesCompletedLabel(tier)).kc(result.getActivityScore(tier.getName())).build();
	}

	/** Progress for a Clue Summary row; unsynced or unknown data shows none. */
	@Nullable
	private static int[] counts(@Nullable TooltipData data)
	{
		return data == null || data.obtainedCount < 0 ? null
			: new int[]{data.obtainedCount, data.totalItems};
	}

	private TooltipData rivalRare(int row)
	{
		return tooltipDataBuilder.rareRow(row, comparison.getCompareClogResult(), unsyncedCatalog.result());
	}

	/** A rare row's data for either player, as its card shows it. */
	@Nullable
	TooltipData rareData(int row, boolean rival)
	{
		return rival ? rivalRare(row) : rareTooltips.get(PanelData.RARE_KEYS[row]);
	}

	/** A Clue Summary rare row's card, for the Collection Log page it stands for. */
	JToolTip buildRareTooltipFor(JLabel owner, int row)
	{
		HiscoreSkill tier = PanelData.RARE_TIERS[row];
		return gridTooltip(owner, cluesCompleted(rareTooltips.get(PanelData.RARE_KEYS[row]), tier,
			lookupSession.getHiscoreResult()), () -> cluesCompleted(rivalRare(row), tier, comparison.getCompareHiscoreResult()),
			PanelData.RARE_NAMES[row], PanelData.RARE_WIKI_PAGES[row]);
	}

	// Helpers

	/** Standard grid-cell label styling (used by every cell on the panel). */
	public static void styleLabel(JLabel label, String tooltipText)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		showCount(label, 0, null);
		label.setIconTextGap(4);
		label.setToolTipText(tooltipText);
		ClogHelper.antialias(label);
	}

	/** Wrap a label in a standard grid cell panel with hover effect wired. */
	public JPanel wrapInCell(JLabel label)
	{
		return cell(label, tooltipController, comparison);
	}

	/** One panel cell around its label, with the grid's hover and the comparison's: every grid builds them here. */
	static JPanel cell(JLabel label, TooltipController tooltipController, ComparisonController comparison)
	{
		JPanel cell = new JPanel();
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(TooltipController.CELL_BORDER);
		cell.add(label);
		tooltipController.addCellHoverEffect(cell, label);
		comparison.installCompareCellHover(cell, label);
		return cell;
	}

	private void setItemIcon(JLabel label, int itemId)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img == null)
		{
			return;
		}
		label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 20, 20)));
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
					label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 20, 20)))));
		}
	}

	/** "Clue Scrolls (hard)" -> "Hard". */
	public static String capitalizeTier(HiscoreSkill tier)
	{
		String name = tier.getName().replace("Clue Scrolls (", "").replace(")", "");
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	// Read-only accessors

	public Map<HiscoreSkill, JLabel> getBossLabels()
	{
		return Collections.unmodifiableMap(bossLabels);
	}

	public Map<HiscoreSkill, JLabel> getActivityLabels()
	{
		return Collections.unmodifiableMap(activityLabels);
	}

	public Map<HiscoreSkill, JLabel> getClueTierLabels()
	{
		return Collections.unmodifiableMap(clueTierLabels);
	}

	@Nullable
	public JLabel getBossLabel(HiscoreSkill boss)
	{
		return bossLabels.get(boss);
	}

	@Nullable
	public TooltipData getTooltipData(HiscoreSkill skill)
	{
		return tooltipDataMap.get(skill);
	}
}
