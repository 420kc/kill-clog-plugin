package com.killclog;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.ImageIcon;
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
    private final Map<String, String> nameOverrides;
    private final Map<HiscoreSkill, String> activityCategories;
    private final Map<HiscoreSkill, String> clueCategories;
    private final Set<HiscoreSkill> noClogActivities;
    private final Map<HiscoreSkill, ImageIcon> dimmedIcons;
    private final KillClogConfig config;

    ProgressHighlighter(
        Map<HiscoreSkill, JLabel> bossLabels,
        Map<HiscoreSkill, JLabel> activityLabels,
        Map<HiscoreSkill, JLabel> clueTierLabels,
        Map<String, String> nameOverrides,
        Map<HiscoreSkill, String> activityCategories,
        Map<HiscoreSkill, String> clueCategories,
        Set<HiscoreSkill> noClogActivities,
        Map<HiscoreSkill, ImageIcon> dimmedIcons,
        KillClogConfig config)
    {
        this.bossLabels = bossLabels;
        this.activityLabels = activityLabels;
        this.clueTierLabels = clueTierLabels;
        this.nameOverrides = nameOverrides;
        this.activityCategories = activityCategories;
        this.clueCategories = clueCategories;
        this.noClogActivities = noClogActivities;
        this.dimmedIcons = dimmedIcons;
        this.config = config;
    }

    /**
     * Color boss, activity, clue tier, and rare cells by clog completion progress.
     * Rare cell labels are passed per-call because they're assigned after construction.
     */
    void colorCellsByCompletion(HiscoreResult hiscoreResult, ClogResult clogResult,
                                Map<String, TooltipData> rareTooltips,
                                FourTwentyMode fourTwentyMode, Color fourTwentyGreen,
                                JLabel thirdAgeCell, String clogThirdAge,
                                JLabel gildedCell, String clogGilded,
                                JLabel hardRare, String rareHard,
                                JLabel eliteRare, String rareElite,
                                JLabel masterRare, String rareMaster)
    {
        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            String hiscoreName = nameOverrides.getOrDefault(skill.getName(), skill.getName());
            colorBossCell(entry.getValue(), hiscoreName, hiscoreResult, clogResult,
                fourTwentyMode, fourTwentyGreen);
        }

        colorActivityCategories(activityCategories, activityLabels, hiscoreResult, clogResult);
        colorActivityCategories(clueCategories, clueTierLabels, hiscoreResult, clogResult);

        // Clue All — aggregate across all 6 tier categories
        JLabel clueAllLabel = activityLabels.get(HiscoreSkill.CLUE_SCROLL_ALL);
        if (clueAllLabel != null
            && hiscoreResult.getActivityScore(HiscoreSkill.CLUE_SCROLL_ALL.getName()) > 0)
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

        if (thirdAgeCell != null) colorByCompletion(thirdAgeCell, clogThirdAge, clogResult);
        if (gildedCell != null) colorByCompletion(gildedCell, clogGilded, clogResult);
        colorCustomRare(hardRare, rareHard, rareTooltips);
        colorCustomRare(eliteRare, rareElite, rareTooltips);
        colorCustomRare(masterRare, rareMaster, rareTooltips);
    }

    /** Dim activities without clog categories when highlighter is active. */
    void dimNoClogActivities(HiscoreResult hiscoreResult)
    {
        for (HiscoreSkill noClog : noClogActivities)
        {
            JLabel label = activityLabels.get(noClog);
            if (label == null) continue;

            int score = hiscoreResult.getActivityScore(noClog.getName());
            label.setForeground(score > 0 ? config.inProgressClogColor() : config.emptyClogColor());

            ImageIcon dimmed = dimmedIcons.get(noClog);
            if (dimmed != null)
            {
                label.setIcon(dimmed);
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
        for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
        {
            if (!noClogActivities.contains(entry.getKey())
                && ColorScheme.LIGHT_GRAY_COLOR.equals(entry.getValue().getForeground()))
            {
                entry.getValue().setForeground(config.emptyClogColor());
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
            // KC > 0 but no clog data for this category — treat as empty
            label.setForeground(config.emptyClogColor());
            return;
        }

        label.setForeground(ClogHelper.clogColor(
            ClogHelper.countObtained(allItems, ClogHelper.getObtainedIds(category, clogResult)),
            allItems.size(), config));
    }
}
