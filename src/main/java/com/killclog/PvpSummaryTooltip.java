package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import net.runelite.client.ui.FontManager;

/**
 * PvP summary tooltip on the skull cell.
 * Five label:value lines — LMS and Soul Wars first (have clog data),
 * then PvP Arena, BH Hunter, BH Rogue.
 */
public class PvpSummaryTooltip extends TitleTooltip
{
    private static final int ICON_SIZE = 13;
    private static final int ICON_GAP = 4;

    private int lmsScore;
    private int soulWarsScore;
    private int pvpArenaScore;
    private int bhHunterScore;
    private int bhRogueScore;

    private int lmsObtained = -1;
    private int lmsTotal;
    private int swObtained = -1;
    private int swTotal;

    private BufferedImage[] icons;
    private String notice;

    public void setData(HiscoreResult hiscoreResult, ClogResult clogResult)
    {
        setTitle("PvP Summary");

        lmsScore = hiscoreResult.getActivityScore("LMS - Rank");
        soulWarsScore = hiscoreResult.getActivityScore("Soul Wars Zeal");
        pvpArenaScore = hiscoreResult.getActivityScore("PvP Arena - Rank");
        bhHunterScore = hiscoreResult.getActivityScore("Bounty Hunter - Hunter");
        bhRogueScore = hiscoreResult.getActivityScore("Bounty Hunter - Rogue");

        if (clogResult != null)
        {
            int[] lms = clogCounts("last_man_standing", clogResult);
            if (lms != null) { lmsObtained = lms[0]; lmsTotal = lms[1]; }
            int[] sw = clogCounts("soul_wars", clogResult);
            if (sw != null) { swObtained = sw[0]; swTotal = sw[1]; }
        }
    }

    public void setIcons(BufferedImage[] icons)
    {
        this.icons = icons;
    }

    public void setNotice(String notice)
    {
        this.notice = notice;
        setTitle("PvP Summary");
    }

    private static int[] clogCounts(String category, ClogResult clogResult)
    {
        List<Integer> items = clogResult.getCategoryItems().get(category);
        if (items == null || items.isEmpty())
        {
            return null;
        }
        Set<Integer> obtained = ClogHelper.getObtainedIds(category, clogResult);
        return new int[]{ClogHelper.countObtained(items, obtained), items.size()};
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
        textWidth = Math.max(textWidth, fm.stringWidth("LMS: 99,999 (99/99)"));
        textWidth = Math.max(textWidth, fm.stringWidth("Soul Wars: 99,999 (99/99)"));
        textWidth = Math.max(textWidth, fm.stringWidth("PvP Arena: 99,999"));
        textWidth = Math.max(textWidth, fm.stringWidth("Bounty Hunter: 99,999"));
        textWidth = Math.max(textWidth, fm.stringWidth("BH Rogue: 99,999"));

        return new Dimension(iconWidth + textWidth, LINE_HEIGHT * 5);
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

        // Icons indexed: 0=LMS, 1=Soul Wars, 2=PvP Arena, 3=BH Hunter, 4=BH Rogue
        int y = startY;
        paintLine(g2, fm, inset, y, icon(0), "LMS: ", lmsScore, lmsObtained, lmsTotal);
        y += LINE_HEIGHT;
        paintLine(g2, fm, inset, y, icon(1), "Soul Wars: ", soulWarsScore, swObtained, swTotal);
        y += LINE_HEIGHT;
        paintLine(g2, fm, inset, y, icon(2), "PvP Arena: ", pvpArenaScore, -1, 0);
        y += LINE_HEIGHT;
        paintLine(g2, fm, inset, y, icon(3), "Bounty Hunter: ", bhHunterScore, -1, 0);
        y += LINE_HEIGHT;
        paintLine(g2, fm, inset, y, icon(4), "BH Rogue: ", bhRogueScore, -1, 0);
    }

    private BufferedImage icon(int index)
    {
        return icons != null && index < icons.length ? icons[index] : null;
    }

    private void paintLine(Graphics2D g2, FontMetrics fm, int inset, int y,
                            BufferedImage icon, String label, int score,
                            int obtained, int total)
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

        // Clog count (yellow when incomplete, green when complete)
        if (obtained >= 0)
        {
            String clogText = " (" + obtained + "/" + total + ")";
            g2.setColor(completionColor(obtained, total));
            g2.drawString(clogText, x, textY);
        }
    }
}
