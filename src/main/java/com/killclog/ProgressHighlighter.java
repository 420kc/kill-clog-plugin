package com.killclog;

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

	private final Map<HiscoreSkill, JLabel> activityLabels;
	private final Map<HiscoreSkill, JLabel> clueTierLabels;
	private final KillClogConfig config;

	ProgressHighlighter(
		Map<HiscoreSkill, JLabel> bossLabels,
		Map<HiscoreSkill, JLabel> activityLabels,
		Map<HiscoreSkill, JLabel> clueTierLabels,
		KillClogConfig config)
	{
		this.bossLabels = bossLabels;
		this.activityLabels = activityLabels;
		this.clueTierLabels = clueTierLabels;
		this.config = config;
	}

	/** Color boss, activity and clue tier cells by clog completion progress. */
	void colorCellsByCompletion(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		if (clogResult == null)
		{
			return;
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
			colorBossCell(entry.getValue(), hiscoreName, hiscoreResult, clogResult);
		}
		colorClueTiers(hiscoreResult, clogResult);

		// Clue All aggregates across all six tier categories.
		JLabel clueAllLabel = activityLabels.get(HiscoreSkill.CLUE_SCROLL_ALL);
		if (clueAllLabel != null)
		{
			int totalItems = 0;
			int totalObtained = 0;
			for (String cat : PanelData.CLUE_CATEGORIES.values())
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

	private void colorBossCell(JLabel label, String hiscoreName,
		HiscoreResult hiscoreResult, ClogResult clogResult)
	{

		int kc = hiscoreResult.getKc(hiscoreName);
		if (kc <= 0) return;

		colorByCompletion(label, ClogService.bossToCategory(hiscoreName), clogResult);
	}

	private void colorClueTiers(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		for (Map.Entry<HiscoreSkill, String> entry : PanelData.CLUE_CATEGORIES.entrySet())
		{
			JLabel label = clueTierLabels.get(entry.getKey());
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
