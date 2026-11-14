package com.killclog;

import java.util.*;
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
			String hiscoreName = PanelData.hiscoreName(skill.getName());
			colorBossCell(entry.getValue(), hiscoreName, hiscoreResult, clogResult);
		}
		colorClueTiers(hiscoreResult, clogResult);

		// Clue All aggregates across all six tier categories.
		JLabel clueAllLabel = activityLabels.get(HiscoreSkill.CLUE_SCROLL_ALL);
		int[] all = new int[2];
		for (String cat : PanelData.CLUE_CATEGORIES.values())
		{
			int[] counts = ClogHelper.clogCounts(cat, clogResult);
			if (counts != null)
			{
				all[0] += counts[0];
				all[1] += counts[1];
			}
		}
		if (clueAllLabel != null && all[1] > 0)
		{
			clueAllLabel.setForeground(ClogHelper.clogColor(all[0], all[1], config));
		}
	}

	/** Recolor "--" cells to emptyClogColor when highlighter is active. */
	void colorEmptyCells()
	{
		for (Map<HiscoreSkill, JLabel> labels : Arrays.asList(bossLabels, activityLabels, clueTierLabels))
		{
			for (JLabel label : labels.values())
			{
				if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
				{
					label.setForeground(config.emptyClogColor());
				}
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
		// KC exists but no clog data was found for this category: the empty colour.
		int[] counts = ClogHelper.clogCounts(category, clogResult);
		label.setForeground(counts == null ? config.emptyClogColor() : ClogHelper.clogColor(counts[0], counts[1], config));
	}
}
