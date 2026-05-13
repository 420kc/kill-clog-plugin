/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the per-cell tooltip data caches + the icon caches used by the
 * KillClogPanel cell-factory tooltip overrides. Pure storage + getter API in
 * this skeleton; the cell-builder method bodies migrate from the panel in
 * subsequent refactor-cut-3 commits.
 *
 * Extracted from KillClogPanel as refactor cut 3.
 */
package com.killclog;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
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
	// ── Tooltip data caches ───────────────────────────────────────────────
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// ── Icon caches ───────────────────────────────────────────────────────
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];

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
