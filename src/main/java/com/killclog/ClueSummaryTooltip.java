package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.FontManager;

/**
 * Clue summary tooltip on the Clue All cell.
 * Eight label:value lines — All, Beginner through Master, and Mimic kills.
 * Each line has a clue scroll icon on the left.
 */
public class ClueSummaryTooltip extends TitleTooltip
{
    private static final int ICON_SIZE = 13;
    private static final int ICON_GAP = 4;
    private static final Color NOTICE_COLOR = new Color(160, 160, 160);

    private static final HiscoreSkill[] CLUE_TIERS = {
        HiscoreSkill.CLUE_SCROLL_ALL,
        HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
        HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
        HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
    };

    private static final String[] LABELS = {
        "All: ", "Beginner: ", "Easy: ", "Medium: ",
        "Hard: ", "Elite: ", "Master: ",
    };

    private final int[] scores = new int[7];
    private final int[] ranks = new int[7];
    private int mimicKc = -1;

    private BufferedImage[] icons;
    private String notice;

    public void setData(HiscoreResult hiscoreResult)
    {
        setTitle("Clue Summary");

        for (int i = 0; i < CLUE_TIERS.length; i++)
        {
            String name = CLUE_TIERS[i].getName();
            scores[i] = hiscoreResult.getActivityScore(name);
            ranks[i] = hiscoreResult.getActivityRank(name);
        }

        mimicKc = hiscoreResult.getKc("Mimic");

        int allRank = ranks[0];
        setRank(allRank);
    }

    public void setIcons(BufferedImage[] icons)
    {
        this.icons = icons;
    }

    public void setNotice(String notice)
    {
        this.notice = notice;
        setTitle("Clue Summary");
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

        if (notice != null)
        {
            return new Dimension(fm.stringWidth(notice), LINE_HEIGHT);
        }

        int iconWidth = ICON_SIZE + ICON_GAP;
        int textWidth = 0;
        for (String label : LABELS)
        {
            textWidth = Math.max(textWidth, fm.stringWidth(label + "99,999  Rank: 99,999"));
        }
        textWidth = Math.max(textWidth, fm.stringWidth("Mimic: 99,999"));

        return new Dimension(iconWidth + textWidth, LINE_HEIGHT * 8);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        int inset = getInset();
        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();

        if (notice != null)
        {
            g2.setColor(NOTICE_COLOR);
            g2.drawString(notice, inset, startY + fm.getAscent());
            return;
        }

        int y = startY;
        for (int i = 0; i < LABELS.length; i++)
        {
            paintLine(g2, fm, inset, y, icon(i), LABELS[i], scores[i], ranks[i]);
            y += LINE_HEIGHT;
        }

        // Mimic kills
        paintLine(g2, fm, inset, y, icon(7), "Mimic: ", mimicKc, -1);
    }

    private BufferedImage icon(int index)
    {
        return icons != null && index < icons.length ? icons[index] : null;
    }

    private void paintLine(Graphics2D g2, FontMetrics fm, int inset, int y,
                            BufferedImage icon, String label, int score, int rank)
    {
        int x = inset;
        int textY = y + fm.getAscent();

        if (icon != null)
        {
            int iconY = y + (LINE_HEIGHT - ICON_SIZE) / 2;
            g2.drawImage(icon, x, iconY, null);
            x += ICON_SIZE + ICON_GAP;
        }

        // Label (orange)
        g2.setColor(OSRS_ORANGE);
        g2.drawString(label, x, textY);
        x += fm.stringWidth(label);

        if (score <= 0)
        {
            g2.setColor(Color.WHITE);
            g2.drawString("--", x, textY);
            return;
        }

        // Score (white)
        String scoreText = String.format("%,d", score);
        g2.setColor(Color.WHITE);
        g2.drawString(scoreText, x, textY);
        x += fm.stringWidth(scoreText);

        // Rank (orange label, white value)
        if (rank > 0)
        {
            String rankLabel = "  Rank: ";
            g2.setColor(OSRS_ORANGE);
            g2.drawString(rankLabel, x, textY);
            x += fm.stringWidth(rankLabel);

            g2.setColor(Color.WHITE);
            g2.drawString(String.format("%,d", rank), x, textY);
        }
    }
}
