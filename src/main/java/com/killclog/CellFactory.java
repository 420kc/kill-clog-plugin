/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the per-cell tooltip data caches + the icon caches used by the
 * KillClogPanel cell-factory tooltip overrides, plus the routing helpers that
 * decide between single-player and dual-player tooltip rendering.
 *
 * Extracted from KillClogPanel as refactor cut 3.
 */
package com.killclog;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Holds the tooltip + icon caches that the panel's cell-factory overrides
 * read on every hover. Per-skill {@code TooltipData} entries are populated by
 * the lookup pipeline (panel-side, populated post-lookup-result); the per-rare
 * entries are populated alongside. Sprite-driven icon caches are populated
 * once during cell construction by the panel's cell-factory methods.
 *
 * <p>This class intentionally has no Swing imports yet — it's the skeleton for
 * cut 3. The cell-builder methods (makeBossCell etc.) move into this class in
 * the next commit.
 */
public class CellFactory
{
	/** Builds the single-player sprite tooltip for non-comparison mode. The panel keeps the implementation; we delegate via this hook. */
	public interface SinglePlayerTooltipBuilder
	{
		JToolTip build(JLabel owner, @Nullable TooltipData data, int gridCols, String name);

		JToolTip build(JLabel owner, @Nullable TooltipData data, int gridCols, String name, boolean compact);
	}

	// ── Deps ──────────────────────────────────────────────────────────────
	@Nullable private final LookupSession lookupSession;
	@Nullable private final ComparisonController comparison;
	@Nullable private final TooltipDataBuilder tooltipDataBuilder;
	@Nullable private SinglePlayerTooltipBuilder singlePlayerBuilder;

	// ── Tooltip data caches ───────────────────────────────────────────────
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// ── Icon caches ───────────────────────────────────────────────────────
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];

	public CellFactory(@Nullable LookupSession lookupSession, @Nullable ComparisonController comparison,
		@Nullable TooltipDataBuilder tooltipDataBuilder)
	{
		this.lookupSession = lookupSession;
		this.comparison = comparison;
		this.tooltipDataBuilder = tooltipDataBuilder;
	}

	/** No-arg constructor retained for the field-only skeleton callsite; deps are wired via the full constructor in production. */
	public CellFactory()
	{
		this(null, null, null);
	}

	public void setSinglePlayerTooltipBuilder(SinglePlayerTooltipBuilder builder)
	{
		this.singlePlayerBuilder = builder;
	}

	// ── Tooltip routing ───────────────────────────────────────────────────

	/** Build the boss-cell tooltip, routing between single-player and comparison-mode renderers. */
	public JToolTip buildBossTooltip(JLabel owner, HiscoreSkill boss)
	{
		if (comparison != null && comparison.isComparisonMode())
		{
			return comparison.makeSpriteTooltip(owner,
				tooltipDataMap.get(boss),
				comparison.getCompareTooltipData(boss),
				boss.getName());
		}
		return singlePlayerBuilder.build(owner, tooltipDataMap.get(boss), 5, boss.getName());
	}

	/** Build the clue-tier tooltip, routing between single-player and comparison-mode renderers. */
	public JToolTip buildClueTierTooltip(JLabel owner, HiscoreSkill tier, String displayName, boolean compact)
	{
		if (comparison != null && comparison.isComparisonMode())
		{
			String category = PanelData.CLUE_CATEGORIES.get(tier);
			int redRank = comparison.getCompareHiscoreResult() != null
				? comparison.getCompareHiscoreResult().getActivityRank(tier.getName()) : -1;
			TooltipData redData = comparison.getCompareClogResult() != null && tooltipDataBuilder != null
				? tooltipDataBuilder.buildTooltipData(displayName, category, redRank, comparison.getCompareClogResult())
				: null;
			return comparison.makeSpriteTooltip(owner, tooltipDataMap.get(tier), redData, displayName);
		}
		return singlePlayerBuilder.build(owner, tooltipDataMap.get(tier), compact ? 10 : 5, displayName, compact);
	}

	/** Build a clue-rare-cell tooltip (3rd Age / Gilded), routing between single-player and comparison-mode. */
	public JToolTip buildClueRareTooltip(JLabel owner, String name, String clogCategory, boolean isThirdAge)
	{
		TooltipData data = rareTooltips.get(isThirdAge ? PanelData.CLOG_THIRD_AGE : PanelData.CLOG_GILDED);
		if (comparison != null && comparison.isComparisonMode())
		{
			TooltipData redData = comparison.buildClueRare(name, clogCategory);
			return comparison.makeSpriteTooltip(owner, data, redData, name);
		}
		return singlePlayerBuilder.build(owner, data, 5, name);
	}

	/** Build a custom-rare-cell tooltip (Hard / Elite / Master), routing between single-player and comparison-mode. */
	public JToolTip buildCustomRareTooltip(JLabel owner, String name, String rareKey, int[] itemIds)
	{
		if (comparison != null && comparison.isComparisonMode())
		{
			TooltipData redData = comparison.buildCustomRare(name, itemIds);
			return comparison.makeSpriteTooltip(owner, rareTooltips.get(rareKey), redData, name);
		}
		return singlePlayerBuilder.build(owner, rareTooltips.get(rareKey), 5, name);
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
