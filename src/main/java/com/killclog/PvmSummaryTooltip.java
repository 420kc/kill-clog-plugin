package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * PVM summary tooltip on the combat level cell.
 * Megarare weapon sprites centered at top, stats below, most-killed at bottom.
 */
public class PvmSummaryTooltip extends TitleTooltip
{
    private static final int WEAPON_SIZE = 28;
    private static final int WEAPON_PAD = 6;
    private static final int SEPARATOR_PAD = 2;
    private static final int MOST_KILLED_GAP = 4;

    private static final Color QTY_COLOR = new Color(255, 255, 0);
    private static final Color QTY_SHADOW = new Color(0, 0, 0);

    private static final int TBOW_ID = 20997;
    private static final int SCYTHE_ID = 22486;
    private static final int SHADOW_ID = 27277;

    private int combatLevel;
    private int totalKills;
    private int bossesWithKc;
    private int totalBosses;
    private String mostKilled;
    private int mostKilledKc;

    private int bossesCompleted = -1;
    private int bossesWithClog;

    private final BufferedImage[] weaponSprites = new BufferedImage[3];
    private final int[] weaponCounts = new int[3];

    public void setData(int combatLevel, int totalKills, int bossesWithKc, int totalBosses,
                        String mostKilled, int mostKilledKc)
    {
        setTitle("PVM Summary");
        this.combatLevel = combatLevel;
        this.totalKills = totalKills;
        this.bossesWithKc = bossesWithKc;
        this.totalBosses = totalBosses;
        this.mostKilled = mostKilled;
        this.mostKilledKc = mostKilledKc;
    }

    public void setCompletion(int completed, int total)
    {
        this.bossesCompleted = completed;
        this.bossesWithClog = total;
    }

    public void setMegarares(int tbowCount, int scytheCount,
                             int shadowCount, ItemManager itemManager)
    {
        weaponCounts[0] = tbowCount;
        weaponCounts[1] = scytheCount;
        weaponCounts[2] = shadowCount;

        int[] ids = {TBOW_ID, SCYTHE_ID, SHADOW_ID};
        for (int i = 0; i < 3; i++)
        {
            BufferedImage img = itemManager.getImage(ids[i], 1, false);
            weaponSprites[i] = ImageUtil.resizeImage(img, WEAPON_SIZE, WEAPON_SIZE);
            if (img instanceof AsyncBufferedImage)
            {
                final int idx = i;
                ((AsyncBufferedImage) img).onLoaded(() ->
                {
                    weaponSprites[idx] = ImageUtil.resizeImage(img, WEAPON_SIZE, WEAPON_SIZE);
                    SwingUtilities.invokeLater(this::repaint);
                });
            }
        }
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

        // Megarares section: subheader + sprite row
        int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
        int megarareHeight = LINE_HEIGHT + WEAPON_SIZE;

        // Stats section
        int statsLines = 3; // Combat, Total Kills, Bosses
        if (bossesCompleted >= 0) statsLines++;
        int statsHeight = LINE_HEIGHT * statsLines;

        // Most killed section
        int mostKilledHeight = 0;
        if (mostKilled != null)
        {
            mostKilledHeight = MOST_KILLED_GAP + LINE_HEIGHT * 2;
        }

        // Width — widest of stats text, sprite row, subheader
        int textWidth = 0;
        textWidth = Math.max(textWidth, fm.stringWidth("Combat: 126"));
        textWidth = Math.max(textWidth, fm.stringWidth("Total Kills: 999,999"));
        textWidth = Math.max(textWidth, fm.stringWidth("Bosses killed: 99 / 99"));
        if (bossesCompleted >= 0)
        {
            textWidth = Math.max(textWidth, fm.stringWidth("Logs completed: 99 / 99"));
        }
        if (mostKilled != null)
        {
            textWidth = Math.max(textWidth,
                fm.stringWidth(mostKilled + " (" + String.format("%,d", mostKilledKc) + ")"));
        }
        textWidth = Math.max(textWidth, fm.stringWidth("Megarares:"));

        int contentWidth = Math.max(textWidth, spriteRowWidth);
        int separatorHeight = SEPARATOR_PAD + 1 + SEPARATOR_PAD;
        int contentHeight = megarareHeight + separatorHeight + statsHeight + mostKilledHeight;

        return new Dimension(contentWidth, contentHeight);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        int inset = getInset();
        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();
        int y = startY;

        // "Megarares" subheader — left-aligned
        g2.setColor(OSRS_ORANGE);
        g2.drawString("Megarares:", inset, y + fm.getAscent());
        y += LINE_HEIGHT;

        // 3 weapon sprites — horizontally centered
        int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
        int spriteStartX = inset + (w - 2 * inset - spriteRowWidth) / 2;
        for (int i = 0; i < 3; i++)
        {
            int sx = spriteStartX + i * (WEAPON_SIZE + WEAPON_PAD);
            BufferedImage sprite = weaponSprites[i];
            if (sprite != null)
            {
                boolean obtained = weaponCounts[i] > 0;
                g2.setComposite(obtained
                    ? AlphaComposite.SrcOver
                    : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2.drawImage(sprite, sx, y, null);
                g2.setComposite(AlphaComposite.SrcOver);

                // Quantity overlay — top-left, matching clog tooltips
                if (obtained && weaponCounts[i] > 1)
                {
                    String qtyText = String.valueOf(weaponCounts[i]);
                    g2.setColor(QTY_SHADOW);
                    g2.drawString(qtyText, sx + 1, y + fm.getAscent() + 1);
                    g2.setColor(QTY_COLOR);
                    g2.drawString(qtyText, sx, y + fm.getAscent());
                }
            }
        }
        y += WEAPON_SIZE + SEPARATOR_PAD;
        g2.setColor(SEPARATOR_COLOR);
        g2.drawLine(inset, y, w - inset - 1, y);
        y += 1 + SEPARATOR_PAD;

        // Combat
        drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Combat: ",
            combatLevel > 0 ? String.valueOf(combatLevel) : "--");
        y += LINE_HEIGHT;

        // Total Kills
        drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Total Kills: ",
            totalKills > 0 ? String.format("%,d", totalKills) : "--");
        y += LINE_HEIGHT;

        // Bosses killed
        drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Bosses killed: ",
            bossesWithKc + "/" + totalBosses,
            completionColor(bossesWithKc, totalBosses));
        y += LINE_HEIGHT;

        // Logs completed (clog only)
        if (bossesCompleted >= 0)
        {
            drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Logs completed: ",
                bossesCompleted + "/" + bossesWithClog,
                completionColor(bossesCompleted, bossesWithClog));
            y += LINE_HEIGHT;
        }

        // Most Killed — label on its own line, value on next line
        if (mostKilled != null)
        {
            y += MOST_KILLED_GAP;
            g2.setColor(OSRS_ORANGE);
            g2.drawString("Most Killed:", inset, y + fm.getAscent());
            y += LINE_HEIGHT;
            g2.setColor(Color.WHITE);
            g2.drawString(mostKilled + " (" + String.format("%,d", mostKilledKc) + ")",
                inset, y + fm.getAscent());
        }
    }

    private void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
                                String label, String value)
    {
        drawLabelValue(g2, fm, x, y, label, value, Color.WHITE);
    }

    private void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
                                String label, String value, Color valueColor)
    {
        g2.setColor(OSRS_ORANGE);
        g2.drawString(label, x, y);
        g2.setColor(valueColor);
        g2.drawString(value, x + fm.stringWidth(label), y);
    }
}
