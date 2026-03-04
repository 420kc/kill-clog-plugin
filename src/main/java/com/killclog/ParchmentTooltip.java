package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JToolTip;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

/**
 * Parchment-styled text tooltip with game-extracted stone border.
 * Also provides shared painting methods for BossTooltip.
 */
public class ParchmentTooltip extends JToolTip
{
    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 14;
    private static final Color OSRS_ORANGE = new Color(255, 152, 31);

    // Fallback programmatic border
    static final int FALLBACK_BORDER = 3;
    private static final Color BORDER_OUTER = new Color(26, 26, 26);
    private static final Color BORDER_MID = new Color(61, 51, 34);
    private static final Color BORDER_INNER = new Color(84, 72, 53);
    private static final Color FALLBACK_BG = new Color(60, 50, 35);

    // Border sprites — 9-slice from game (UNKNOWN_BORDER set)
    private static BufferedImage cornerTL, cornerTR, cornerBL, cornerBR;
    private static BufferedImage edgeH, edgeV;
    private static volatile boolean spritesLoaded;

    /**
     * Load border sprites from the game via SpriteManager.
     * Call once when the client is ready.
     */
    public static void loadBorderSprites(SpriteManager spriteManager)
    {
        // UNKNOWN_BORDER sprites — classic OSRS stone window frame
        spriteManager.getSpriteAsync(991, 0, img -> { cornerTL = img; checkSprites(); });
        spriteManager.getSpriteAsync(992, 0, img -> { cornerTR = img; checkSprites(); });
        spriteManager.getSpriteAsync(993, 0, img -> { cornerBL = img; checkSprites(); });
        spriteManager.getSpriteAsync(994, 0, img -> { cornerBR = img; checkSprites(); });
        spriteManager.getSpriteAsync(987, 0, img -> { edgeH = img; checkSprites(); });
        spriteManager.getSpriteAsync(988, 0, img -> { edgeV = img; checkSprites(); });
    }

    private static void checkSprites()
    {
        spritesLoaded = cornerTL != null && cornerTR != null
            && cornerBL != null && cornerBR != null
            && edgeH != null && edgeV != null;
    }

    /**
     * Border inset in pixels for content positioning.
     * The sprites paint at full size along edges; this controls how far
     * content sits from the edge (not the sprite dimension).
     */
    public static int getBorderThickness()
    {
        return FALLBACK_BORDER;
    }

    public ParchmentTooltip()
    {
        setOpaque(false);
        setBorder(null);
    }

    @Override
    public Dimension getPreferredSize()
    {
        String text = getTipText();
        if (text == null || text.isEmpty())
        {
            return new Dimension(100, 30);
        }

        Font font = FontManager.getRunescapeSmallFont();
        FontMetrics fm = getFontMetrics(font);

        String[] lines = text.split("\n");
        int maxWidth = 0;
        for (String line : lines)
        {
            maxWidth = Math.max(maxWidth, fm.stringWidth(line));
        }

        int inset = getBorderThickness() + MARGIN;
        int width = maxWidth + inset * 2;
        int height = lines.length * LINE_HEIGHT + inset * 2;

        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();

        paintParchmentFill(g2, w, h);
        paintStoneBorder(g2, w, h);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(FontManager.getRunescapeSmallFont());
        g2.setColor(OSRS_ORANGE);

        String text = getTipText();
        if (text != null)
        {
            FontMetrics fm = g2.getFontMetrics();
            int inset = getBorderThickness() + MARGIN;
            String[] lines = text.split("\n");
            int y = inset + fm.getAscent();
            for (String line : lines)
            {
                g2.drawString(line, inset, y);
                y += LINE_HEIGHT;
            }
        }

        g2.dispose();
    }

    /**
     * Paint tiled parchment background. Shared by ParchmentTooltip and BossTooltip.
     */
    static void paintParchmentFill(Graphics2D g2, int w, int h)
    {
        g2.setColor(FALLBACK_BG);
        g2.fillRect(0, 0, w, h);
    }

    /**
     * Paint 9-slice stone border from game sprites.
     * Falls back to programmatic 3-tone border when sprites haven't loaded yet.
     */
    static void paintStoneBorder(Graphics2D g2, int w, int h)
    {
        if (!spritesLoaded)
        {
            g2.setColor(BORDER_OUTER);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.setColor(BORDER_MID);
            g2.drawRect(1, 1, w - 3, h - 3);
            g2.setColor(BORDER_INNER);
            g2.drawRect(2, 2, w - 5, h - 5);
            return;
        }

        int cw = cornerTL.getWidth();
        int ch = cornerTL.getHeight();

        // Corners
        g2.drawImage(cornerTL, 0, 0, null);
        g2.drawImage(cornerTR, w - cornerTR.getWidth(), 0, null);
        g2.drawImage(cornerBL, 0, h - cornerBL.getHeight(), null);
        g2.drawImage(cornerBR, w - cornerBR.getWidth(), h - cornerBR.getHeight(), null);

        // Top edge — tile between corners
        int ew = edgeH.getWidth();
        int eh = edgeH.getHeight();
        for (int x = cw; x < w - cw; x += ew)
        {
            int drawW = Math.min(ew, w - cw - x);
            g2.drawImage(edgeH, x, 0, x + drawW, eh,
                0, 0, drawW, eh, null);
        }

        // Bottom edge
        for (int x = cw; x < w - cw; x += ew)
        {
            int drawW = Math.min(ew, w - cw - x);
            g2.drawImage(edgeH, x, h - eh, x + drawW, h,
                0, 0, drawW, eh, null);
        }

        // Left edge — tile between corners
        int vw = edgeV.getWidth();
        int vh = edgeV.getHeight();
        for (int y = ch; y < h - ch; y += vh)
        {
            int drawH = Math.min(vh, h - ch - y);
            g2.drawImage(edgeV, 0, y, vw, y + drawH,
                0, 0, vw, drawH, null);
        }

        // Right edge
        for (int y = ch; y < h - ch; y += vh)
        {
            int drawH = Math.min(vh, h - ch - y);
            g2.drawImage(edgeV, w - vw, y, w, y + drawH,
                0, 0, vw, drawH, null);
        }
    }
}
