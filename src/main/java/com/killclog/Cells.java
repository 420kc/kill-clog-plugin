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
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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

	// ── Deps ──────────────────────────────────────────────────────────────
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final TooltipController tooltipController;
	private final ComparisonController comparison;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final LookupSession lookupSession;
	private final ClogService clogService;
	@Nullable private SinglePlayerTooltipBuilder singlePlayerBuilder;

	// ── Tooltip data caches ───────────────────────────────────────────────
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// ── Icon caches ───────────────────────────────────────────────────────
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];

	// ── Cell labels ───────────────────────────────────────────────────────
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
	}

	public void setSinglePlayerTooltipBuilder(SinglePlayerTooltipBuilder builder)
	{
		this.singlePlayerBuilder = builder;
	}

	// ── Cell builders ─────────────────────────────────────────────────────

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

		spriteManager.getSpriteAsync(boss.getSpriteId(), 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null)
				{
					return;
				}
				BufferedImage scaled = ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20);
				ImageIcon icon = new ImageIcon(scaled);
				label.setIcon(icon);
				originalIcons.put(boss, icon);
				dimmedIcons.put(boss, new ImageIcon(ClogHelper.createDimmedImage(icon)));
			}));

		bossLabels.put(boss, label);
		return wrapInCell(label);
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
					ClueSummaryTooltip tip = new ClueSummaryTooltip();
					tip.setComponent(this);
					tip.setIcons(clueIcons);
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
				PvpSummaryTooltip tip = new PvpSummaryTooltip();
				tip.setComponent(this);
				tip.setIcons(pvpActivityIcons);
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

	// ── Primary-side rendering ────────────────────────────────────────────

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
			JLabel label = entry.getValue();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
			int kc = result.getBossKills().getOrDefault(hiscoreName, -1);
			boolean hasKc = kc > 0;

			label.setText(ClogHelper.pad(kc <= 0 ? "--" : ClogHelper.formatKc(kc)));
			label.setForeground(hasKc ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

			ImageIcon orig = originalIcons.get(skill);
			if (orig != null)
			{
				label.setIcon(hasKc ? orig : dimmedIcons.get(skill));
			}

			fourTwentyMode.applyOverrides(label, kc);
		}

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
					? activity.getName() + "\nRank: {w}" + String.format("%,d", rank)
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
				? shortName + "\nRank: {w}" + String.format("%,d", rank)
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

		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = entry.getValue();
			String bossName = skill.getName();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);

			int rank = lookupSession.getHiscoreResult() != null
				? lookupSession.getHiscoreResult().getRank(hiscoreName) : -1;

			String category = ClogService.bossToCategory(hiscoreName);

			if (lookupSession.getClogResult() == null)
			{
				boolean selfNoCache = localRsn != null
					&& localRsn.equalsIgnoreCase(lookupSession.getCurrentLookupRsn());
				if (!selfNoCache)
				{
					int total = clogService.getCategoryItemCount(category);
					tooltipDataMap.put(skill, new TooltipData(
						bossName, rank, -1, Math.max(total, 0),
						java.util.Collections.emptyList(),
						java.util.Collections.emptySet(),
						java.util.Collections.emptyMap()));
				}
				label.setToolTipText(" ");
				continue;
			}

			TooltipData data = tooltipDataBuilder.buildTooltipData(bossName, category, rank, lookupSession.getClogResult());
			if (data == null)
			{
				int total = clogService.getCategoryItemCount(category);
				tooltipDataMap.put(skill, new TooltipData(
					bossName, rank, -1, Math.max(total, 0),
					java.util.Collections.emptyList(),
					java.util.Collections.emptySet(),
					java.util.Collections.emptyMap()));
				label.setToolTipText(" ");
				continue;
			}

			tooltipDataMap.put(skill, data);
			tooltipDataBuilder.preloadItemImages(data);
			label.setToolTipText(" ");
		}

		// Clue tier cells
		if (lookupSession.getClogResult() == null)
		{
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

	private static Color rareColor(TooltipData data, KillClogConfig config)
	{
		if (data.obtainedCount <= 0)
		{
			return config.completionistHighlighter()
				? config.emptyClogColor() : ColorScheme.LIGHT_GRAY_COLOR;
		}
		return config.completionistHighlighter()
			? ClogHelper.clogColor(data.obtainedCount, data.totalItems, config) : KC_COLOR;
	}

	// ── Tooltip routing ───────────────────────────────────────────────────

	private JToolTip buildBossTooltip(JLabel owner, HiscoreSkill boss)
	{
		if (comparison.isComparisonMode())
		{
			return comparison.makeSpriteTooltip(owner,
				tooltipDataMap.get(boss),
				comparison.getCompareTooltipData(boss),
				boss.getName());
		}
		return buildSingleSpriteTooltip(owner, tooltipDataMap.get(boss), 5, boss.getName());
	}

	private JToolTip buildClueTierTooltip(JLabel owner, HiscoreSkill tier, String displayName, boolean compact)
	{
		if (comparison.isComparisonMode())
		{
			String category = PanelData.CLUE_CATEGORIES.get(tier);
			int redRank = comparison.getCompareHiscoreResult() != null
				? comparison.getCompareHiscoreResult().getActivityRank(tier.getName()) : -1;
			TooltipData redData = comparison.getCompareClogResult() != null
				? tooltipDataBuilder.buildTooltipData(displayName, category, redRank, comparison.getCompareClogResult())
				: null;
			return comparison.makeSpriteTooltip(owner, tooltipDataMap.get(tier), redData, displayName);
		}
		return buildSingleSpriteTooltip(owner, tooltipDataMap.get(tier), compact ? 10 : 5, displayName, compact);
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

	// ── Helpers ───────────────────────────────────────────────────────────

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

	/** "Clue Scrolls (hard)" -> "Hard". Public for the panel's tooltip-text builder. */
	public static String capitalizeTier(HiscoreSkill tier)
	{
		String name = tier.getName();
		int dash = name.indexOf('-');
		String tierName = dash < 0 ? name : name.substring(dash + 1).trim();
		if (tierName.isEmpty())
		{
			return name;
		}
		return Character.toUpperCase(tierName.charAt(0)) + tierName.substring(1);
	}

	// ── Read-only accessors ───────────────────────────────────────────────

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
