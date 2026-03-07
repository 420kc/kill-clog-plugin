package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import net.runelite.client.ui.FontManager;

/**
 * Intermediate tooltip with a titled header zone: bold title line,
 * optional subtitle (label: value), optional rank line, then separator.
 * Subclasses provide content below the separator via
 * {@link #getContentSize(int)} and {@link #paintBody(Graphics2D, int, int, int)}.
 */
public abstract class TitleTooltip extends NativeTooltip
{
    private static final int NAME_LINE_HEIGHT = 18;
    private static final int SEPARATOR_GAP = 6;
    static final Color SEPARATOR_COLOR = new Color(80, 70, 50);

    protected static final Color CLOG_GREEN = new Color(0, 255, 0);
    private static final Color CLOG_YELLOW = new Color(255, 255, 0);

    private String title;
    private String subtitleLabel;
    private String subtitleValue;
    private Color subtitleColor;
    private String rankText;

    /** Set the bold orange title line (always required). */
    public void setTitle(String title)
    {
        this.title = title;
    }

    /**
     * Set a subtitle line: label in orange, value in the given color.
     */
    public void setSubtitle(String label, String value, Color valueColor)
    {
        this.subtitleLabel = label;
        this.subtitleValue = value;
        this.subtitleColor = valueColor;
    }

    /**
     * Set the obtained subtitle line. Pass -1 for unknown ("?/Y").
     * Color: green if complete, yellow otherwise.
     */
    public void setObtained(int obtained, int total)
    {
        String countPart = (obtained < 0 ? "?" : String.valueOf(obtained)) + "/" + total;
        setSubtitle("Obtained: ", countPart,
            (obtained >= total && total > 0) ? CLOG_GREEN : CLOG_YELLOW);
    }

    /** Set the rank line. 0 = "Unranked". */
    public void setRank(int rank)
    {
        if (rank > 0)
        {
            this.rankText = String.format("%,d", rank);
        }
        else
        {
            this.rankText = "Unranked";
        }
    }

    protected String getTitle()
    {
        return title;
    }

    /**
     * Number of pixel rows the header occupies (title + optional lines).
     * Does NOT include the separator gap below.
     */
    int getHeaderHeight()
    {
        int h = NAME_LINE_HEIGHT;
        if (subtitleLabel != null)
        {
            h += LINE_HEIGHT;
        }
        if (rankText != null)
        {
            h += LINE_HEIGHT;
        }
        return h;
    }

    /**
     * Total header zone height including separator line and gaps.
     * Content starts at inset + this value.
     */
    protected int getHeaderZoneHeight()
    {
        return getHeaderHeight() + SEPARATOR_GAP + 1 + SEPARATOR_GAP;
    }

    /**
     * Return the content dimensions given the available width (tooltip width - 2*inset).
     * The availableWidth accounts for the header-driven minimum width, so subclasses
     * can wrap content to fill the space.
     */
    protected abstract Dimension getContentSize(int availableWidth);

    /**
     * Paint the body content starting at the given Y coordinate (below separator).
     */
    protected abstract void paintBody(Graphics2D g2, int w, int h, int startY);

    @Override
    public Dimension getPreferredSize()
    {
        int inset = getInset();

        FontMetrics nfm = getFontMetrics(FontManager.getRunescapeBoldFont());
        FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());

        // Header text widths + close button padding drive minimum tooltip width.
        // The full header width flows to getContentSize so grids can fill the space.
        int titleTextWidth = title != null ? nfm.stringWidth(title) : 0;
        int subTextWidth = subtitleLabel != null
            ? sfm.stringWidth(subtitleLabel + subtitleValue) : 0;
        int rnkTextWidth = rankText != null ? sfm.stringWidth("Rank: " + rankText) : 0;
        int maxTextWidth = Math.max(titleTextWidth, Math.max(subTextWidth, rnkTextWidth));
        int headerMinWidth = maxTextWidth + getHeaderRightPad();

        Dimension contentSize = getContentSize(Math.max(headerMinWidth, 1));

        int contentWidth = Math.max(headerMinWidth, contentSize.width);
        int totalHeight = inset + getHeaderZoneHeight() + contentSize.height + inset;
        int totalWidth = contentWidth + inset * 2;

        return new Dimension(totalWidth, totalHeight);
    }

    @Override
    protected void paintContent(Graphics2D g2, int w, int h)
    {
        int startY = paintHeader(g2, w);
        paintBody(g2, w, h, startY);
    }

    /**
     * Paint the header (title, optional subtitle, optional rank, separator).
     * Returns the Y coordinate where body content should start.
     */
    int paintHeader(Graphics2D g2, int w)
    {
        if (title == null)
        {
            return getInset();
        }

        int inset = getInset();
        // Title (bold orange)
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics nfm = g2.getFontMetrics();
        int lineY = inset + nfm.getAscent();
        g2.setColor(OSRS_ORANGE);
        g2.drawString(title, inset, lineY);

        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();

        // Subtitle (label in orange, value in subtitleColor)
        if (subtitleLabel != null)
        {
            lineY += NAME_LINE_HEIGHT;
            g2.setColor(OSRS_ORANGE);
            g2.drawString(subtitleLabel, inset, lineY);
            int labelWidth = fm.stringWidth(subtitleLabel);
            g2.setColor(subtitleColor);
            g2.drawString(subtitleValue, inset + labelWidth, lineY);
        }

        // Rank line
        if (rankText != null)
        {
            lineY += LINE_HEIGHT;
            String label = "Rank: ";
            g2.setColor(OSRS_ORANGE);
            g2.drawString(label, inset, lineY);
            if (!"Unranked".equals(rankText))
            {
                g2.setColor(Color.WHITE);
            }
            g2.drawString(rankText, inset + fm.stringWidth(label), lineY);
        }

        // Separator
        int sepY = lineY + SEPARATOR_GAP;
        g2.setColor(SEPARATOR_COLOR);
        g2.drawLine(inset, sepY, w - inset - 1, sepY);

        return sepY + 1 + SEPARATOR_GAP;
    }
}
