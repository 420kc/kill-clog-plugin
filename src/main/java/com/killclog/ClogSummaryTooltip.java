package com.killclog;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.runelite.client.ui.FontManager;

/**
 * Clog summary tooltip on the info bar clog cell.
 * Title "Clog Summary", obtained subtitle, tier progress lines with icons.
 */
public class ClogSummaryTooltip extends TitleTooltip
{
    private static final int ICON_SIZE = 13;
    private static final int ICON_GAP = 3;
    private String tierRange;
    private String tierName;
    private String progressText;
    private String nextTierName;
    private String syncText;

    private Map<String, BufferedImage> tierIcons;
    private String notice;

    public void setTierData(int obtained, int totalSlots, Map<String, BufferedImage> tierIcons)
    {
        setTitle("Clog Summary");
        setObtained(obtained, totalSlots);
        this.tierIcons = tierIcons;

        int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
        String currentTier = ClogHelper.getClogTierName(obtained, totalSlots);

        if (currentTier == null)
        {
            tierRange = null;
            tierName = null;
            progressText = (ClogHelper.CLOG_TIER_THRESHOLDS[0] - obtained) + " more until";
            nextTierName = "bronze";
            return;
        }

        if ("gilded".equals(currentTier))
        {
            tierRange = gildedThreshold + "+:";
            tierName = "gilded";
            progressText = null;
            nextTierName = null;
            return;
        }

        int tierIndex = -1;
        for (int i = 0; i < ClogHelper.CLOG_TIERS.length; i++)
        {
            if (ClogHelper.CLOG_TIERS[i].equals(currentTier)) { tierIndex = i; break; }
        }

        int currentThreshold = ClogHelper.CLOG_TIER_THRESHOLDS[tierIndex];
        int nextThreshold;
        String nextTier;
        if (tierIndex + 1 < ClogHelper.CLOG_TIER_THRESHOLDS.length)
        {
            nextThreshold = ClogHelper.CLOG_TIER_THRESHOLDS[tierIndex + 1];
            nextTier = ClogHelper.CLOG_TIERS[tierIndex + 1];
        }
        else
        {
            nextThreshold = gildedThreshold;
            nextTier = "gilded";
        }

        tierRange = currentThreshold + "-" + (nextThreshold - 1) + ":";
        tierName = currentTier;
        progressText = (nextThreshold - obtained) + " more until";
        nextTierName = nextTier;
    }

    public void setSyncText(String syncText)
    {
        this.syncText = syncText;
    }

    public void setNotice(String notice)
    {
        this.notice = notice;
        setTitle("Clog Summary");
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

        if (tierRange != null)
        {
            textWidth = Math.max(textWidth, fm.stringWidth(tierRange) + iconWidth);
        }
        if (progressText != null)
        {
            textWidth = Math.max(textWidth, fm.stringWidth(progressText) + iconWidth);
        }
        if (syncText != null)
        {
            textWidth = Math.max(textWidth, fm.stringWidth(syncText));
        }

        int lines = 0;
        if (tierRange != null) lines++;
        if (progressText != null) lines++;
        if (syncText != null) lines++;

        return new Dimension(textWidth, LINE_HEIGHT * lines);
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

        // Current tier range with icon
        if (tierRange != null)
        {
            paintTierLine(g2, fm, inset, y, tierRange, tierName);
            y += LINE_HEIGHT;
        }

        // Progress to next tier with icon
        if (progressText != null)
        {
            paintTierLine(g2, fm, inset, y, progressText, nextTierName);
            y += LINE_HEIGHT;
        }

        // Sync line
        if (syncText != null)
        {
            g2.setColor(OSRS_ORANGE);
            g2.drawString(syncText, inset, y + fm.getAscent());
        }
    }

    private void paintTierLine(Graphics2D g2, FontMetrics fm, int x, int y,
                                String text, String tier)
    {
        int textY = y + fm.getAscent();
        g2.setColor(OSRS_ORANGE);
        g2.drawString(text, x, textY);
        x += fm.stringWidth(text) + ICON_GAP;

        BufferedImage icon = tierIcons != null ? tierIcons.get(tier) : null;
        if (icon != null)
        {
            int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
            g2.drawImage(icon, x, iconY, null);
        }
    }
}
