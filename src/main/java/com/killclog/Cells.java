/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the cells of the kill clog panel: builds them, holds the labels,
 * caches tooltip + icon data, renders both primary and comparison values,
 * and routes between single-player and dual-player tooltips. KillClogPanel
 * (layout) and ComparisonController (dual-player rendering) talk to Cells
 * directly.
 */
package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Per-skill {@link JLabel} cells, their tooltip data caches, the helpers that
 * construct them, and the renderers that fill them with primary or comparison
 * values. Single source of truth for "what cells does the panel show, and how
 * do they hover."
 *
 * <p>The single-player tooltip body is built by the panel ({@code
 * SinglePlayerTooltipBuilder}) because it reaches the panel's sync-notice
 * text; everything else lives here.
 */
public class Cells
{
	/** Builds the single-player sprite tooltip body. Implemented by the panel (it owns the sync-notice text). */
	public interface SinglePlayerTooltipBuilder
	{
		JToolTip build(JLabel owner, @Nullable TooltipData data, int gridCols, String name);

		JToolTip build(JLabel owner, @Nullable TooltipData data, int gridCols, String name, boolean compact);
	}

	// Deps
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final TooltipController tooltipController;
	private final ComparisonController comparison;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final LookupSession lookupSession;
	private final ClogService clogService;
	private final UnsyncedClogCatalog unsyncedCatalog;
	@Nullable private SinglePlayerTooltipBuilder singlePlayerBuilder;

	// Tooltip data caches
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// Icon caches
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];

	// Cell labels
	private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> clueTierLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();
	@Nullable private JLabel pvpSummaryCell;
	@Nullable private JLabel thirdAgeCell;
	@Nullable private JLabel gildedCell;
	@Nullable private JLabel hardRare;
	@Nullable private JLabel eliteRare;
	@Nullable private JLabel masterRare;

	// Pending Maggot King cell (pre-enum only; see PanelData). None of these
	// are populated once RuneLite ships HiscoreSkill.MAGGOT_KING.
	@Nullable private JLabel pendingMaggotLabel;
	@Nullable private ImageIcon pendingMaggotOriginalIcon;
	@Nullable private ImageIcon pendingMaggotDimmedIcon;
	@Nullable private TooltipData pendingMaggotData;

	public Cells(SpriteManager spriteManager, ItemManager itemManager,
		TooltipController tooltipController, ComparisonController comparison,
		TooltipDataBuilder tooltipDataBuilder, LookupSession lookupSession,
		ClogService clogService)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.tooltipController = tooltipController;
		this.comparison = comparison;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.lookupSession = lookupSession;
		this.clogService = clogService;
		this.unsyncedCatalog = new UnsyncedClogCatalog(clogService);
	}

	public void setSinglePlayerTooltipBuilder(SinglePlayerTooltipBuilder builder)
	{
		this.singlePlayerBuilder = builder;
	}

	public void setClogIndex(ClogIndex clogIndex)
	{
		this.unsyncedCatalog.setClogIndex(clogIndex);
	}

	public void setUnsyncedCatalogResolver(Consumer<ClogResult> resolver)
	{
		this.unsyncedCatalog.setResolver(resolver);
	}

	/** The empty catalog backing cold and unsynced previews, if available yet. */
	@Nullable
	public ClogResult unsyncedCatalogResult()
	{
		return unsyncedCatalog.result();
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
			if (boss == HiscoreSkill.MIMIC && !PanelData.hasOfficialMaggotKing())
			{
				grid.add(buildPendingMaggotCell());
			}
		}
		return grid;
	}

	/** Build the pending Maggot King cell shown until RuneLite ships the HiscoreSkill enum. */
	private JPanel buildPendingMaggotCell()
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildPendingMaggotTooltip(this);
			}
		};
		styleLabel(label, PanelData.PENDING_MAGGOT_KING_NAME);

		loadBossIcon(label, PanelData.PENDING_MAGGOT_KING_SPRITE_ID, icon ->
		{
			pendingMaggotOriginalIcon = icon;
			pendingMaggotDimmedIcon = new ImageIcon(ClogHelper.createDimmedImage(icon));
		});

		pendingMaggotLabel = label;
		return wrapInCell(label);
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
		spriteManager.getSpriteAsync(spriteId, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null)
				{
					return;
				}
				ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20));
				label.setIcon(icon);
				sink.accept(icon);
			}));
	}

	/** Build an activity cell (skill icon + tooltip, with a special-case for the All-Clues activity). */
	public JPanel buildActivityCell(HiscoreSkill activity)
	{
		JLabel label = new JLabel(activity.getName())
		{
			@Override
			public JToolTip createToolTip()
			{
				if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
				{
					if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
					{
						CompareClueSummaryTooltip cmp = new CompareClueSummaryTooltip();
						cmp.setComponent(this);
						String blueName = lookupSession.getCurrentLookupRsn() != null
							? lookupSession.getCurrentLookupRsn() : "--";
						String redName = comparison.getCompareRsn() != null
							? comparison.getCompareRsn() : "--";
						cmp.setBlueData(blueName, lookupSession.getHiscoreResult());
						cmp.setRedData(redName, comparison.getCompareHiscoreResult());
						cmp.setIcons(clueIcons);
						JPanel parentCell = (JPanel) this.getParent();
						tooltipController.keepTooltipOnHover(cmp, parentCell);
						return cmp;
					}
					ClueSummaryTooltip tip = new ClueSummaryTooltip();
					tip.setComponent(this);
					tip.setIcons(clueIcons);
					tip.setData(lookupSession.getHiscoreResult());
					JPanel parentCell = (JPanel) this.getParent();
					tooltipController.keepTooltipOnHover(tip, parentCell);
					return tip;
				}
				return buildSingleSpriteTooltip(this, tooltipDataMap.get(activity), 5, activity.getName());
			}
		};
		styleLabel(label, activity.getName());

		if (activity.getSpriteId() != -1)
		{
			spriteManager.getSpriteAsync(activity.getSpriteId(), 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						label.setIcon(new ImageIcon(ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20)));
					}
				}));
		}

		activityLabels.put(activity, label);
		return wrapInCell(label);
	}

	/** Build the PvP summary cell with its custom tooltip + 5-icon row. */
	public JPanel buildPvpSummaryCell()
	{
		JLabel cell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
				{
					ComparePvpSummaryTooltip cmp = new ComparePvpSummaryTooltip();
					cmp.setComponent(this);
					cmp.setIcons(pvpActivityIcons);
					String blueName = lookupSession.getCurrentLookupRsn() != null
						? lookupSession.getCurrentLookupRsn() : "--";
					String redName = comparison.getCompareRsn() != null
						? comparison.getCompareRsn() : "--";
					cmp.setBlueData(blueName, lookupSession.getHiscoreResult(), lookupSession.getClogResult());
					cmp.setRedData(redName, comparison.getCompareHiscoreResult(), comparison.getCompareClogResult());
					JPanel parentCell = (JPanel) this.getParent();
					tooltipController.keepTooltipOnHover(cmp, parentCell);
					return cmp;
				}
				PvpSummaryTooltip tip = new PvpSummaryTooltip();
				tip.setComponent(this);
				tip.setIcons(pvpActivityIcons);
				tip.setData(lookupSession.getHiscoreResult(), lookupSession.getClogResult());
				JPanel parentCell = (JPanel) this.getParent();
				tooltipController.keepTooltipOnHover(tip, parentCell);
				return tip;
			}
		};
		styleLabel(cell, "PvP Summary");
		pvpSummaryCell = cell;

		spriteManager.getSpriteAsync(439, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					cell.setIcon(new ImageIcon(ImageUtil.resizeCanvas(
						ImageUtil.resizeImage(sprite, 16, 16), 20, 20)));
				}
			}));

		for (int i = 0; i < PanelData.PVP_ACTIVITIES.length; i++)
		{
			int spriteId = PanelData.PVP_ACTIVITIES[i].getSpriteId();
			if (spriteId == -1)
			{
				continue;
			}
			final int idx = i;
			spriteManager.getSpriteAsync(spriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						pvpActivityIcons[idx] = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}

		return wrapInCell(cell);
	}

	public JPanel buildClueTierCell(HiscoreSkill tier, int itemId, boolean compact)
	{
		String displayName = capitalizeTier(tier);
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildClueTierTooltip(this, tier, displayName, compact);
			}
		};
		styleLabel(label, tier.getName());
		setItemIcon(label, itemId);
		clueTierLabels.put(tier, label);
		return wrapInCell(label);
	}

	public JPanel buildClueRareCell(String name, int itemId, String clogCategory, boolean isThirdAge)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildClueRareTooltip(this, name, clogCategory);
			}
		};
		styleLabel(label, name);
		setItemIcon(label, itemId);

		if (isThirdAge)
		{
			thirdAgeCell = label;
		}
		else
		{
			gildedCell = label;
		}

		return wrapInCell(label);
	}

	public JPanel buildCustomRareCell(String name, int iconItemId, String rareKey, int[] itemIds)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildCustomRareTooltip(this, name, rareKey, itemIds);
			}
		};
		styleLabel(label, name);
		setItemIcon(label, iconItemId);

		if (PanelData.RARE_HARD.equals(rareKey))
		{
			hardRare = label;
		}
		else if (PanelData.RARE_ELITE.equals(rareKey))
		{
			eliteRare = label;
		}
		else if (PanelData.RARE_MASTER.equals(rareKey))
		{
			masterRare = label;
		}

		return wrapInCell(label);
	}

	// Primary-side rendering

	/** Standard kc text color (light gray-white) used for any cell that has a non-zero kc / score. */
	static final Color KC_COLOR = new Color(215, 215, 215);

	/**
	 * Write hiscore-driven values into every primary-side cell: bosses
	 * (text + color + dimmed-icon swap + 420 mode overrides), activities,
	 * clue tiers, and the PvP summary cell. Mirrors
	 * {@link ComparisonController#updateAllCells()} for the comparison side.
	 */
	public void renderHiscore(HiscoreResult result, FourTwentyMode fourTwentyMode)
	{
		// Boss cells (with 420 mode overrides applied last)
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
			renderBossValue(entry.getValue(), hiscoreName,
				originalIcons.get(skill), dimmedIcons.get(skill), result, fourTwentyMode);
		}
		renderBossValue(pendingMaggotLabel, PanelData.PENDING_MAGGOT_KING_NAME,
			pendingMaggotOriginalIcon, pendingMaggotDimmedIcon, result, fourTwentyMode);

		// Activity cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
		{
			HiscoreSkill activity = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(activity.getName());
			label.setText(ClogHelper.pad(score <= 0 ? "--" : ClogHelper.formatKc(score)));
			label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
			{
				label.setToolTipText(" ");
			}
			else
			{
				int rank = result.getActivityRank(activity.getName());
				label.setToolTipText(rank > 0
					? activity.getName() + "\nRank: {w}" + String.format(Locale.US, "%,d", rank)
					: activity.getName());
			}
		}

		// PvP summary cell: BH Hunter + BH Rogue total
		if (pvpSummaryCell != null)
		{
			int bhTotal = Math.max(0, result.getActivityScore("Bounty Hunter - Hunter"))
				+ Math.max(0, result.getActivityScore("Bounty Hunter - Rogue"));
			pvpSummaryCell.setText(ClogHelper.pad(bhTotal > 0 ? ClogHelper.formatKc(bhTotal) : "--"));
		}

		// Clue tier cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
		{
			HiscoreSkill tier = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(tier.getName());
			label.setText(ClogHelper.pad(score <= 0 ? "--" : ClogHelper.formatKc(score)));
			label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

			String shortName = capitalizeTier(tier);
			int rank = result.getActivityRank(tier.getName());
			label.setToolTipText(rank > 0
				? shortName + "\nRank: {w}" + String.format(Locale.US, "%,d", rank)
				: shortName);
		}
	}

	/** Write clog-driven values into the rare cells (3rd Age, Gilded, Hard/Elite/Master Treasure). */
	public void renderClog(ClogResult result, KillClogConfig config)
	{
		writeClueRare(thirdAgeCell, "3rd Age", PanelData.CLOG_THIRD_AGE, result, config);
		writeClueRare(gildedCell, "Gilded", PanelData.CLOG_GILDED, result, config);
		writeCustomRare(hardRare, "Hard Treasure (Rare)", PanelData.RARE_HARD, PanelData.HARD_RARE_ITEMS, result, config);
		writeCustomRare(eliteRare, "Elite Treasure (Rare)", PanelData.RARE_ELITE, PanelData.ELITE_RARE_ITEMS, result, config);
		writeCustomRare(masterRare, "Master Treasure (Rare)", PanelData.RARE_MASTER, PanelData.MASTER_RARE_ITEMS, result, config);
	}

	private void writeClueRare(@Nullable JLabel label, String name, String clogCategory,
		ClogResult result, KillClogConfig config)
	{
		if (label == null)
		{
			return;
		}
		TooltipData data = tooltipDataBuilder.buildClueRareData(name, clogCategory, result);
		if (data == null)
		{
			label.setToolTipText(name);
			return;
		}
		label.setText(ClogHelper.pad(data.obtainedCount > 0 ? ClogHelper.formatKc(data.obtainedCount) : "--"));
		rareTooltips.put(clogCategory, data);
		label.setForeground(rareColor(data, config));
		label.setToolTipText(" ");
	}

	private void writeCustomRare(@Nullable JLabel label, String name, String rareKey,
		int[] itemIds, ClogResult result, KillClogConfig config)
	{
		if (label == null)
		{
			return;
		}
		TooltipData data = tooltipDataBuilder.buildCustomRareData(name, itemIds, result);
		label.setText(ClogHelper.pad(data.obtainedCount > 0 ? ClogHelper.formatKc(data.obtainedCount) : "--"));
		rareTooltips.put(rareKey, data);
		label.setForeground(rareColor(data, config));
		label.setToolTipText(" ");
	}

	/**
	 * Rebuild the per-cell {@code TooltipData} cache for the primary side from
	 * the current lookup session results. Mirrors what
	 * {@link ComparisonController#enter()} does for the comparison side.
	 */
	public void rebuildPrimaryTooltips(@Nullable String localRsn)
	{
		tooltipDataMap.clear();
		if (lookupSession.getClogResult() == null)
		{
			rareTooltips.clear();
		}
		boolean selfNoCache = lookupSession.getClogResult() == null && localRsn != null
			&& localRsn.equalsIgnoreCase(lookupSession.getCurrentLookupRsn());
		ClogResult catalog = lookupSession.getClogResult() == null && !selfNoCache
			? unsyncedCatalog.result() : null;

		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			String bossName = skill.getName();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);
			TooltipData data = buildPrimaryBossData(bossName, hiscoreName, selfNoCache, catalog);
			if (data != null)
			{
				tooltipDataMap.put(skill, data);
			}
			entry.getValue().setToolTipText(" ");
		}
		if (pendingMaggotLabel != null)
		{
			String name = PanelData.PENDING_MAGGOT_KING_NAME;
			pendingMaggotData = buildPrimaryBossData(name, name, selfNoCache, catalog);
			pendingMaggotLabel.setToolTipText(" ");
		}

		// Clue tier cells
		if (lookupSession.getClogResult() == null)
		{
			if (!selfNoCache)
			{
				rebuildUnsyncedClueTooltips(catalog);
			}
			return;
		}
		for (Map.Entry<HiscoreSkill, String> entry : PanelData.CLUE_CATEGORIES.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = clueTierLabels.get(skill);
			if (label == null)
			{
				continue;
			}
			int rank = lookupSession.getHiscoreResult() != null
				? lookupSession.getHiscoreResult().getActivityRank(skill.getName()) : -1;
			TooltipData data = tooltipDataBuilder.buildTooltipData(capitalizeTier(skill), entry.getValue(),
				rank, lookupSession.getClogResult());
			if (data == null)
			{
				continue;
			}
			tooltipDataMap.put(skill, data);
			tooltipDataBuilder.preloadItemImages(data);
			label.setToolTipText(" ");
		}
	}

	private void rebuildUnsyncedClueTooltips(@Nullable ClogResult catalog)
	{
		for (Map.Entry<HiscoreSkill, String> entry : PanelData.CLUE_CATEGORIES.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = clueTierLabels.get(skill);
			if (label == null)
			{
				continue;
			}
			int rank = lookupSession.getHiscoreResult() != null
				? lookupSession.getHiscoreResult().getActivityRank(skill.getName()) : -1;
			int score = lookupSession.getHiscoreResult() != null
				? lookupSession.getHiscoreResult().getActivityScore(skill.getName()) : -1;
			TooltipData data = tooltipDataBuilder.buildUnsyncedTooltipData(
				capitalizeTier(skill), entry.getValue(), rank, "Score: ", score, catalog);
			if (data != null)
			{
				tooltipDataMap.put(skill, data);
			}
			label.setToolTipText(" ");
		}

		putUnsyncedRare(PanelData.CLOG_THIRD_AGE, thirdAgeCell,
			"3rd Age", PanelData.THIRD_AGE_ITEMS, catalog);
		putUnsyncedRare(PanelData.CLOG_GILDED, gildedCell,
			"Gilded", PanelData.GILDED_ITEMS, catalog);
		putUnsyncedRare(PanelData.RARE_HARD, hardRare,
			"Hard Treasure (Rare)", PanelData.HARD_RARE_ITEMS, catalog);
		putUnsyncedRare(PanelData.RARE_ELITE, eliteRare,
			"Elite Treasure (Rare)", PanelData.ELITE_RARE_ITEMS, catalog);
		putUnsyncedRare(PanelData.RARE_MASTER, masterRare,
			"Master Treasure (Rare)", PanelData.MASTER_RARE_ITEMS, catalog);
	}

	private void putUnsyncedRare(String key, @Nullable JLabel label, String name,
		int[] itemIds, @Nullable ClogResult catalog)
	{
		TooltipData data = tooltipDataBuilder.buildUnsyncedTooltipData(name, key, -1, null, -1, catalog);
		if (data == null)
		{
			data = tooltipDataBuilder.buildUnsyncedItemData(name, itemIds, catalog);
		}
		if (data != null)
		{
			rareTooltips.put(key, data);
		}
		if (label != null)
		{
			label.setToolTipText(" ");
		}
	}

	private static Color rareColor(TooltipData data, KillClogConfig config)
	{
		if (data.obtainedCount < 0)
		{
			// No collection-log data for this player (e.g. tracked but never
			// synced to a provider). Show the normal KC color instead of
			// emptyClogColor, which reads as a real 0% / not-started profile.
			return KC_COLOR;
		}
		if (data.obtainedCount == 0)
		{
			return config.completionistHighlighter()
				? config.emptyClogColor() : ColorScheme.LIGHT_GRAY_COLOR;
		}
		return config.completionistHighlighter()
			? ClogHelper.clogColor(data.obtainedCount, data.totalItems, config) : KC_COLOR;
	}

	// Tooltip routing

	private JToolTip buildBossTooltip(JLabel owner, HiscoreSkill boss)
	{
		return buildBossTooltip(owner, tooltipDataMap.get(boss),
			comparison.getCompareTooltipData(boss), boss.getName(), PanelData.bossWikiPage(boss));
	}

	private JToolTip buildPendingMaggotTooltip(JLabel owner)
	{
		// Wiki page name matches the boss name exactly (no "The" prefix).
		String name = PanelData.PENDING_MAGGOT_KING_NAME;
		return buildBossTooltip(owner, pendingMaggotData,
			comparison.getPendingMaggotTooltipData(), name, name);
	}

	private JToolTip buildBossTooltip(JLabel owner, @Nullable TooltipData blueData,
		@Nullable TooltipData redData, String name, String wikiPage)
	{
		JToolTip tip = comparison.isComparisonMode()
			? comparison.makeSpriteTooltip(owner, blueData, redData, name)
			: buildSingleSpriteTooltip(owner, blueData, 5, name);
		if (tip instanceof TitleTooltip)
		{
			((TitleTooltip) tip).setTitleWikiPage(wikiPage);
		}
		return tip;
	}

	/** Write one boss cell's KC text, color, and icon dim state from the name-keyed hiscore map. */
	private void renderBossValue(@Nullable JLabel label, String hiscoreName,
		@Nullable ImageIcon orig, @Nullable ImageIcon dimmed,
		HiscoreResult result, FourTwentyMode fourTwentyMode)
	{
		if (label == null)
		{
			return;
		}
		int kc = result.getBossKills().getOrDefault(hiscoreName, -1);
		boolean hasKc = kc > 0;
		label.setText(ClogHelper.pad(kc <= 0 ? "--" : ClogHelper.formatKc(kc)));
		label.setForeground(hasKc ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		if (orig != null)
		{
			label.setIcon(hasKc ? orig : dimmed);
		}
		fourTwentyMode.applyOverrides(label, kc);
	}

	/**
	 * Build one boss cell's primary-side TooltipData: synced clog data when
	 * available, otherwise the unsynced catalog preview (skipped for the
	 * sync-prompt self-lookup). Shared by the enum bosses and the pending
	 * Maggot King cell.
	 */
	@Nullable
	private TooltipData buildPrimaryBossData(String displayName, String hiscoreName,
		boolean selfNoCache, @Nullable ClogResult catalog)
	{
		String category = ClogService.bossToCategory(hiscoreName);
		int rank = lookupSession.getHiscoreResult() != null
			? lookupSession.getHiscoreResult().getRank(hiscoreName) : -1;
		int kc = lookupSession.getHiscoreResult() != null
			? lookupSession.getHiscoreResult().getKc(hiscoreName) : -1;

		if (lookupSession.getClogResult() == null)
		{
			return selfNoCache ? null : tooltipDataBuilder.buildUnsyncedTooltipData(
				displayName, category, rank, "Kills: ", kc, catalog);
		}

		TooltipData data = tooltipDataBuilder.buildTooltipData(
			displayName, category, rank, lookupSession.getClogResult());
		if (data == null)
		{
			return tooltipDataBuilder.buildUnsyncedTooltipData(
				displayName, category, rank, "Kills: ", kc, unsyncedCatalog.result());
		}
		tooltipDataBuilder.preloadItemImages(data);
		return data;
	}

	private JToolTip buildClueTierTooltip(JLabel owner, HiscoreSkill tier, String displayName, boolean compact)
	{
		if (comparison.isComparisonMode())
		{
			TooltipData redData = comparison.buildCompareClueTierData(tier);
			return comparison.makeSpriteTooltip(owner, tooltipDataMap.get(tier), redData, displayName,
				!suppressComparisonClueGrid(tier));
		}
		return buildSingleSpriteTooltip(owner, tooltipDataMap.get(tier), compact ? 10 : 5, displayName, compact);
	}

	private static boolean suppressComparisonClueGrid(HiscoreSkill tier)
	{
		return tier == HiscoreSkill.CLUE_SCROLL_EASY
			|| tier == HiscoreSkill.CLUE_SCROLL_MEDIUM
			|| tier == HiscoreSkill.CLUE_SCROLL_HARD;
	}

	private JToolTip buildClueRareTooltip(JLabel owner, String name, String clogCategory)
	{
		TooltipData data = rareTooltips.get(clogCategory);
		if (comparison.isComparisonMode())
		{
			TooltipData redData = comparison.buildClueRare(name, clogCategory);
			return comparison.makeSpriteTooltip(owner, data, redData, name);
		}
		return buildSingleSpriteTooltip(owner, data, 5, name);
	}

	private JToolTip buildCustomRareTooltip(JLabel owner, String name, String rareKey, int[] itemIds)
	{
		if (comparison.isComparisonMode())
		{
			TooltipData redData = comparison.buildCustomRare(name, itemIds);
			return comparison.makeSpriteTooltip(owner, rareTooltips.get(rareKey), redData, name);
		}
		return buildSingleSpriteTooltip(owner, rareTooltips.get(rareKey), 5, name);
	}

	private JToolTip buildSingleSpriteTooltip(JLabel owner, @Nullable TooltipData data, int gridCols, String name)
	{
		return singlePlayerBuilder.build(owner, data, gridCols, name);
	}

	private JToolTip buildSingleSpriteTooltip(JLabel owner, @Nullable TooltipData data, int gridCols, String name, boolean compact)
	{
		return singlePlayerBuilder.build(owner, data, gridCols, name, compact);
	}

	// Helpers

	/** Standard grid-cell label styling (used by every cell on the panel). */
	public static void styleLabel(JLabel label, String tooltipText)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setText(ClogHelper.pad("--"));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setIconTextGap(4);
		label.setToolTipText(tooltipText);
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	/** Wrap a label in a standard grid cell panel with hover effect wired. */
	public JPanel wrapInCell(JLabel label)
	{
		JPanel cell = new JPanel();
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(TooltipController.CELL_BORDER);
		cell.add(label);
		tooltipController.addCellHoverEffect(cell, label);
		installCompareCellHover(cell, label);
		return cell;
	}

	private void installCompareCellHover(JPanel cell, JLabel label)
	{
		MouseAdapter compareHover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				comparison.setCompareCellHover(label, true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (!isInsideCell(cell, e))
				{
					comparison.setCompareCellHover(label, false);
				}
			}
		};

		cell.addMouseListener(compareHover);
		label.addMouseListener(compareHover);
	}

	private static boolean isInsideCell(JPanel cell, MouseEvent e)
	{
		Object source = e.getSource();
		if (!(source instanceof Component))
		{
			return false;
		}

		Point point = SwingUtilities.convertPoint((Component) source, e.getPoint(), cell);
		return point.x >= 0 && point.y >= 0 && point.x < cell.getWidth() && point.y < cell.getHeight();
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

	public Map<HiscoreSkill, ImageIcon> getOriginalIcons()
	{
		return Collections.unmodifiableMap(originalIcons);
	}

	public Map<HiscoreSkill, ImageIcon> getDimmedIcons()
	{
		return Collections.unmodifiableMap(dimmedIcons);
	}

	@Nullable
	public JLabel getBossLabel(HiscoreSkill boss)
	{
		return bossLabels.get(boss);
	}

	@Nullable
	public JLabel getPendingMaggotLabel()
	{
		return pendingMaggotLabel;
	}

	@Nullable
	public TooltipData getPendingMaggotData()
	{
		return pendingMaggotData;
	}

	@Nullable
	public JLabel getPvpSummaryCell()
	{
		return pvpSummaryCell;
	}

	@Nullable
	public JLabel getThirdAgeCell()
	{
		return thirdAgeCell;
	}

	@Nullable
	public JLabel getGildedCell()
	{
		return gildedCell;
	}

	@Nullable
	public JLabel getHardRare()
	{
		return hardRare;
	}

	@Nullable
	public JLabel getEliteRare()
	{
		return eliteRare;
	}

	@Nullable
	public JLabel getMasterRare()
	{
		return masterRare;
	}

	public Map<HiscoreSkill, TooltipData> getTooltipDataMap()
	{
		return tooltipDataMap;
	}

	public Map<String, TooltipData> getRareTooltips()
	{
		return rareTooltips;
	}

	public BufferedImage[] getClueIcons()
	{
		return clueIcons;
	}

	public BufferedImage[] getPvpActivityIcons()
	{
		return pvpActivityIcons;
	}

	@Nullable
	public TooltipData getTooltipData(HiscoreSkill skill)
	{
		return tooltipDataMap.get(skill);
	}

	@Nullable
	public TooltipData getRareTooltip(String key)
	{
		return rareTooltips.get(key);
	}
}
