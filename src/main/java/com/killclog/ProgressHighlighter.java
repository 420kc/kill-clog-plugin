package com.killclog;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;

/**
 * Color-setting logic for the completionist highlighter.
 * Constructor takes stable references (maps populated before construction);
 * methods take per-lookup state and late-assigned labels.
 */
final class ProgressHighlighter
{
	private final Map<HiscoreSkill, JLabel> bossLabels;

	// Pending Mad Angel cell (pre-enum only; see PanelData). Late-assigned.
	@javax.annotation.Nullable
	private final Map<HiscoreSkill, JLabel> activityLabels;
	private final Map<HiscoreSkill, JLabel> clueTierLabels;
	private final Map<String, String> nameOverrides;
	private final Map<HiscoreSkill, String> clueCategories;
	private final KillClogConfig config;

	ProgressHighlighter(
		Map<HiscoreSkill, JLabel> bossLabels,
		Map<HiscoreSkill, JLabel> activityLabels,
		Map<HiscoreSkill, JLabel> clueTierLabels,
		Map<String, String> nameOverrides,
		Map<HiscoreSkill, String> clueCategories,
		KillClogConfig config)
	{
		this.bossLabels = bossLabels;
		this.activityLabels = activityLabels;
		this.clueTierLabels = clueTierLabels;
		this.nameOverrides = nameOverrides;
		this.clueCategories = clueCategories;
		this.config = config;
	}

	/**
	 * Color boss, activity, clue tier, and rare cells by clog completion progress.
	 * Rare cells are passed as a category-key to label map.
	 */
	void colorCellsByCompletion(HiscoreResult hiscoreResult, ClogResult clogResult,
								Map<String, TooltipData> rareTooltips,
								Map<String, JLabel> rareCells,
								FourTwentyMode fourTwentyMode, Color fourTwentyGreen)
	{
		if (clogResult == null)
		{
			return;
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			String hiscoreName = nameOverrides.getOrDefault(skill.getName(), skill.getName());
			colorBossCell(entry.getValue(), hiscoreName, hiscoreResult, clogResult,
				fourTwentyMode, fourTwentyGreen);
		}
		colorActivityCategories(clueCategories, clueTierLabels, hiscoreResult, clogResult);

		// Clue All aggregates across all six tier categories.
		JLabel clueAllLabel = activityLabels.get(HiscoreSkill.CLUE_SCROLL_ALL);
		if (clueAllLabel != null)
		{
			int totalItems = 0;
			int totalObtained = 0;
			for (String cat : clueCategories.values())
			{
				List<Integer> items = clogResult.getCategoryItems().get(cat);
				if (items != null)
				{
					totalItems += items.size();
					totalObtained += ClogHelper.countObtained(items, ClogHelper.getObtainedIds(cat, clogResult));
				}
			}
			if (totalItems > 0)
			{
				clueAllLabel.setForeground(ClogHelper.clogColor(totalObtained, totalItems, config));
			}
		}

		for (Map.Entry<String, JLabel> entry : rareCells.entrySet())
		{
			String key = entry.getKey();
			JLabel label = entry.getValue();
			if (label == null) continue;
			TooltipData data = rareTooltips.get(key);
			if (data != null)
			{
				colorCustomRare(label, key, rareTooltips);
			}
			else
			{
				colorByCompletion(label, key, clogResult);
			}
		}
	}

	/** Recolor "--" cells to emptyClogColor when highlighter is active. */
	void colorEmptyCells()
	{
		for (JLabel label : bossLabels.values())
		{
			if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
			{
				label.setForeground(config.emptyClogColor());
			}
		}
		for (JLabel label : activityLabels.values())
		{
			if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
			{
				label.setForeground(config.emptyClogColor());
			}
		}
		for (JLabel label : clueTierLabels.values())
		{
			if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
			{
				label.setForeground(config.emptyClogColor());
			}
		}
	}

	// Private helpers.

	private void colorCustomRare(JLabel label, String rareKey,
		Map<String, TooltipData> rareTooltips)
	{
		if (label == null) return;
		TooltipData data = rareTooltips.get(rareKey);
		if (data == null) return;

		label.setForeground(ClogHelper.clogColor(data.obtainedCount, data.totalItems, config));
	}

	private void colorBossCell(JLabel label, String hiscoreName,
		HiscoreResult hiscoreResult, ClogResult clogResult,
		FourTwentyMode fourTwentyMode, Color fourTwentyGreen)
	{
		if (fourTwentyMode != FourTwentyMode.OFF && fourTwentyGreen.equals(label.getForeground()))
		{
			return;
		}

		int kc = hiscoreResult.getKc(hiscoreName);
		if (kc <= 0) return;

		colorByCompletion(label, ClogService.bossToCategory(hiscoreName), clogResult);
	}

	private void colorActivityCategories(Map<HiscoreSkill, String> categories,
		Map<HiscoreSkill, JLabel> labels,
		HiscoreResult hiscoreResult,
		ClogResult clogResult)
	{
		for (Map.Entry<HiscoreSkill, String> entry : categories.entrySet())
		{
			JLabel label = labels.get(entry.getKey());
			if (label != null)
			{
				int score = hiscoreResult.getActivityScore(entry.getKey().getName());
				if (score > 0)
				{
					colorByCompletion(label, entry.getValue(), clogResult);
				}
			}
		}
	}

	private void colorByCompletion(JLabel label, String category, ClogResult clogResult)
	{
		List<Integer> allItems = clogResult.getCategoryItems().get(category);
		if (allItems == null || allItems.isEmpty())
		{
			// KC exists but no clog data was found for this category.
			label.setForeground(config.emptyClogColor());
			return;
		}

		label.setForeground(ClogHelper.clogColor(
			ClogHelper.countObtained(allItems, ClogHelper.getObtainedIds(category, clogResult)),
			allItems.size(), config));
	}
}
