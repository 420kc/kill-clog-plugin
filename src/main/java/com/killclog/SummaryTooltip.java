package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Player summary tooltip on the info bar name label.
 * Shows RSN as title via TitleTooltip. Body content TBD.
 */
public class SummaryTooltip extends TitleTooltip
{
    private static final int SHELL_WIDTH = 120;
    private static final int SHELL_HEIGHT = 40;

    public void setData(String rsn)
    {
        setTitle(rsn);
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        return new Dimension(SHELL_WIDTH, SHELL_HEIGHT);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        // Content TBD — empty shell for now
    }
}
