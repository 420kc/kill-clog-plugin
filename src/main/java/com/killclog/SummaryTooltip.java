package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Player summary tooltip on the info bar name label.
 * Centered "Player Summary" title, RSN + total kills with cape icon,
 * then obtained pet sprites below.
 */
public class SummaryTooltip extends TitleTooltip
{
    private static final int CAPE_RIGHT_PAD = 4;
    private static final int TEXT_CAPE_GAP = 8;
    private static final int PET_SIZE = 15;
    private static final int PET_PAD = 2;
    private static final int PET_COLS = 10;
    private static final int SECTION_GAP = 6;

    private static final int BADGE_SIZE = 13;
    private static final int BADGE_GAP = 3;

    private String rsn;
    private int overallRank;
    private BufferedImage capeIcon;
    private BufferedImage badgeIcon;
    private String accountLabel;
    private String prestige;

    // Pet data — only obtained pets
    private int totalPetCount;
    private List<Integer> obtainedPetList;
    private BufferedImage[] petSprites;

    public void setData(String rsn, int overallRank, BufferedImage capeIcon,
                        BufferedImage badgeIcon, String accountLabel, String prestige)
    {
        setTitle("Player Summary");
        this.rsn = rsn;
        this.overallRank = overallRank;
        this.capeIcon = capeIcon;
        this.badgeIcon = badgeIcon != null
            ? ImageUtil.resizeImage(badgeIcon, BADGE_SIZE, BADGE_SIZE) : null;
        this.accountLabel = accountLabel;
        this.prestige = prestige;
    }

    public void setPets(List<Integer> allPetIds, Set<Integer> obtainedPetIds, ItemManager itemManager)
    {
        this.totalPetCount = allPetIds != null ? allPetIds.size() : 0;

        // Build ordered list of obtained pet IDs (preserving category order)
        obtainedPetList = new ArrayList<>();
        if (allPetIds != null && obtainedPetIds != null)
        {
            for (int id : allPetIds)
            {
                if (obtainedPetIds.contains(id))
                {
                    obtainedPetList.add(id);
                }
            }
        }

        if (obtainedPetList.isEmpty()) return;

        // Pre-load and pre-scale sprites + register repaint on async load
        petSprites = new BufferedImage[obtainedPetList.size()];
        for (int i = 0; i < obtainedPetList.size(); i++)
        {
            BufferedImage img = itemManager.getImage(obtainedPetList.get(i), 1, false);
            petSprites[i] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
            if (img instanceof AsyncBufferedImage)
            {
                final int idx = i;
                ((AsyncBufferedImage) img).onLoaded(() ->
                {
                    petSprites[idx] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
                    SwingUtilities.invokeLater(this::repaint);
                });
            }
        }
    }

    private int getStatsHeight()
    {
        int lines = 3; // RSN + Overall Rank + Prestige
        if (accountLabel != null) lines++;
        return LINE_HEIGHT * lines;
    }

    private boolean hasPets()
    {
        return obtainedPetList != null && !obtainedPetList.isEmpty() && petSprites != null;
    }

    private int getPetGridHeight()
    {
        if (!hasPets()) return 0;
        int rows = (obtainedPetList.size() + PET_COLS - 1) / PET_COLS;
        return rows * (PET_SIZE + PET_PAD) - PET_PAD;
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

        // Stats section width
        int textWidth = 0;
        if (rsn != null)
        {
            int rsnWidth = fm.stringWidth(rsn);
            if (badgeIcon != null) rsnWidth += BADGE_SIZE + BADGE_GAP;
            textWidth = Math.max(textWidth, rsnWidth);
        }
        if (accountLabel != null)
        {
            textWidth = Math.max(textWidth, fm.stringWidth(accountLabel));
        }
        textWidth = Math.max(textWidth, fm.stringWidth("Overall Rank: 999,999"));
        textWidth = Math.max(textWidth,
            fm.stringWidth("Prestige: " + (prestige != null ? prestige : "None")));

        int statsWidth = textWidth;
        int statsHeight = getStatsHeight();

        if (capeIcon != null)
        {
            statsWidth += TEXT_CAPE_GAP + capeIcon.getWidth() + CAPE_RIGHT_PAD;
            statsHeight = Math.max(statsHeight, capeIcon.getHeight());
        }

        // Pet grid width
        int petCount = hasPets() ? obtainedPetList.size() : 0;
        int petGridWidth = petCount > 0
            ? Math.min(petCount, PET_COLS) * (PET_SIZE + PET_PAD) - PET_PAD
            : 0;

        int contentWidth = Math.max(statsWidth, petGridWidth);
        int contentHeight = statsHeight;

        if (hasPets())
        {
            FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
            contentHeight += SECTION_GAP + 1 + SECTION_GAP
                + bfm.getHeight() + PET_PAD
                + getPetGridHeight();
        }

        return new Dimension(contentWidth, contentHeight);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        int inset = getInset();
        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();

        int statsHeight = getStatsHeight();

        // Cape icon — right-aligned, vertically centered in stats section
        if (capeIcon != null)
        {
            int capeW = capeIcon.getWidth();
            int capeH = capeIcon.getHeight();
            int capeSectionH = Math.max(statsHeight, capeH);
            int capeX = w - inset - CAPE_RIGHT_PAD - capeW;
            int capeY = startY + (capeSectionH - capeH) / 2;
            g2.drawImage(capeIcon, capeX, capeY, null);
        }

        int lineY = startY + fm.getAscent();

        // RSN with optional badge
        if (rsn != null)
        {
            int rsnX = inset;
            if (badgeIcon != null)
            {
                int iconY = lineY - fm.getAscent() + (LINE_HEIGHT - BADGE_SIZE) / 2;
                g2.drawImage(badgeIcon, rsnX, iconY, null);
                rsnX += BADGE_SIZE + BADGE_GAP;
            }
            g2.setColor(Color.WHITE);
            g2.drawString(rsn, rsnX, lineY);
        }
        lineY += LINE_HEIGHT;

        // Account type label (iron variants only)
        if (accountLabel != null)
        {
            g2.setColor(OSRS_ORANGE);
            g2.drawString(accountLabel, inset, lineY);
            lineY += LINE_HEIGHT;
        }

        // Overall Rank
        {
            String label = "Overall Rank: ";
            g2.setColor(OSRS_ORANGE);
            g2.drawString(label, inset, lineY);
            if (overallRank > 0)
            {
                g2.setColor(Color.WHITE);
                g2.drawString(String.format("%,d", overallRank), inset + fm.stringWidth(label), lineY);
            }
            else
            {
                g2.drawString("Unranked", inset + fm.stringWidth(label), lineY);
            }
            lineY += LINE_HEIGHT;
        }

        // Prestige
        {
            String label = "Prestige: ";
            String value = prestige != null ? prestige : "None";
            g2.setColor(OSRS_ORANGE);
            g2.drawString(label, inset, lineY);
            g2.setColor(prestige != null ? Color.WHITE : OSRS_ORANGE);
            g2.drawString(value, inset + fm.stringWidth(label), lineY);
        }

        if (!hasPets()) return;

        // Separator between stats and pets
        int capeSectionH = capeIcon != null
            ? Math.max(statsHeight, capeIcon.getHeight())
            : statsHeight;
        int sepY = startY + capeSectionH + SECTION_GAP;
        g2.setColor(SEPARATOR_COLOR);
        g2.drawLine(inset, sepY, w - inset - 1, sepY);

        // "Pets (X/Y)" header
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics bfm = g2.getFontMetrics();
        int petsHeaderY = sepY + 1 + SECTION_GAP + bfm.getAscent();
        g2.setColor(OSRS_ORANGE);
        g2.drawString("Pets", inset, petsHeaderY);

        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics sfm = g2.getFontMetrics();
        String countStr = " (" + obtainedPetList.size() + "/" + totalPetCount + ")";
        boolean complete = obtainedPetList.size() >= totalPetCount;
        g2.setColor(complete ? CLOG_GREEN : OSRS_ORANGE);
        g2.drawString(countStr, inset + bfm.stringWidth("Pets"), petsHeaderY);

        // Obtained pet sprite grid
        int gridY = petsHeaderY + PET_PAD + bfm.getDescent();
        int cellSize = PET_SIZE + PET_PAD;

        for (int i = 0; i < obtainedPetList.size(); i++)
        {
            int col = i % PET_COLS;
            int row = i / PET_COLS;
            int px = inset + col * cellSize;
            int py = gridY + row * cellSize;

            BufferedImage sprite = petSprites[i];
            if (sprite != null)
            {
                g2.drawImage(sprite, px, py, null);
            }
        }
    }
}
