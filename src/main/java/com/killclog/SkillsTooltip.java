package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

/**
 * 3x8 skill grid tooltip for the total level cell.
 * Header shows "Skill Summary" + optional Total EXP via TitleTooltip.
 * Matches the in-game skills tab layout with icon + level per cell.
 */
@Slf4j
public class SkillsTooltip extends TitleTooltip
{
    private static final int COLS = 3;
    private static final int ROWS = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 3;
    private static final int ROW_HEIGHT = 18;
    private static final int COL_GAP = 8;

    private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

    // In-game skills tab layout: 3 columns x 8 rows
    private static final Skill[][] GRID = {
        {Skill.ATTACK,       Skill.HITPOINTS,   Skill.MINING},
        {Skill.DEFENCE,      Skill.AGILITY,     Skill.SMITHING},
        {Skill.STRENGTH,     Skill.HERBLORE,    Skill.FISHING},
        {Skill.RANGED,       Skill.THIEVING,    Skill.COOKING},
        {Skill.PRAYER,       Skill.CRAFTING,     Skill.FIREMAKING},
        {Skill.MAGIC,        Skill.FLETCHING,    Skill.WOODCUTTING},
        {Skill.RUNECRAFT,    Skill.SLAYER,       Skill.FARMING},
        {Skill.CONSTRUCTION, Skill.HUNTER,       Skill.SAILING},
    };

    private static final Map<Skill, BufferedImage> icons = new LinkedHashMap<>();

    private HiscoreResult result;

    /**
     * Load skill icons from RuneLite's bundled resources.
     * Call once at panel construction time.
     */
    public static void loadIcons(SkillIconManager skillIconManager)
    {
        for (Skill[] row : GRID)
        {
            for (Skill skill : row)
            {
                try
                {
                    BufferedImage img = skillIconManager.getSkillImage(skill, true);
                    if (img != null)
                    {
                        icons.put(skill, img);
                    }
                }
                catch (Exception e)
                {
                    log.debug("Failed to load icon for {}", skill.getName(), e);
                }
            }
        }
    }

    public void setData(HiscoreResult result)
    {
        this.result = result;
        setTitle("Skill Summary");
        if (result != null && result.getTotalXp() > 0)
        {
            setSubtitle("Total EXP: ", String.format("%,d", result.getTotalXp()), Color.WHITE);
        }
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
        int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
        int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
        int totalWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
        int totalHeight = ROW_HEIGHT * ROWS;
        return new Dimension(totalWidth, totalHeight);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();
        int inset = getInset();

        int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
        int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
        int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
        int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                Skill skill = GRID[row][col];
                int x = gridOffsetX + col * (cellWidth + COL_GAP);
                int y = startY + row * ROW_HEIGHT;

                // Icon
                BufferedImage icon = icons.get(skill);
                if (icon != null)
                {
                    int iconY = y + (ROW_HEIGHT - icon.getHeight()) / 2;
                    g2.drawImage(icon, x, iconY, null);
                }

                // Level text
                int level = result != null ? result.getSkillLevel(skill.getName().toLowerCase()) : -1;
                String text = level > 0 ? String.valueOf(level) : "--";
                g2.setColor(level > 0 ? OSRS_ORANGE : UNRANKED_COLOR);
                int textX = x + ICON_SIZE + ICON_TEXT_GAP;
                int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, textX, textY);
            }
        }
    }
}
