package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Player summary tooltip on the info bar name label.
 * Empty shell — content and layout designed in a follow-up session.
 */
public class SummaryTooltip extends NativeTooltip
{
    private static final int SHELL_WIDTH = 120;
    private static final int SHELL_HEIGHT = 40;

    @Override
    public Dimension getPreferredSize()
    {
        int inset = getInset();
        return new Dimension(SHELL_WIDTH + inset * 2, SHELL_HEIGHT + inset * 2);
    }

    @Override
    protected void paintContent(Graphics2D g2, int w, int h)
    {
        // Content TBD — empty shell for now
    }
}
