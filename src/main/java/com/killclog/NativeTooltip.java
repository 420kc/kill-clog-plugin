package com.killclog;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JToolTip;
import net.runelite.client.game.SpriteManager;

/**
 * Base tooltip styled to match native OSRS interface elements.
 * Provides parchment background fill, 9-slice stone border from game sprites,
 * and AA-hinted Graphics2D setup. Subclasses implement content via
 * {@link #paintContent(Graphics2D, int, int)}.
 */
public abstract class NativeTooltip extends JToolTip
{
    static final int MARGIN = 8;
    static final int LINE_HEIGHT = 14;
    static final Color OSRS_ORANGE = new Color(255, 152, 31);

    // Programmatic fallback border
    private static final int BORDER_THICKNESS = 3;
    private static final Color BORDER_OUTER = new Color(26, 26, 26);
    private static final Color BORDER_MID = new Color(61, 51, 34);
    private static final Color BORDER_INNER = new Color(84, 72, 53);
    private static final Color FALLBACK_BG = new Color(60, 50, 35);

    // 9-slice border sprites from game (dark stone side panel set)
    private static BufferedImage cornerTL, cornerTR, cornerBL, cornerBR;
    private static BufferedImage edgeTop, edgeBottom, edgeLeft, edgeRight;
    private static volatile boolean spritesLoaded;

    /**
     * Load border sprites from the game via SpriteManager.
     * Call once when the client is ready.
     */
    public static void loadSprites(SpriteManager spriteManager)
    {
        spriteManager.getSpriteAsync(824, 0, img -> { cornerTL = img; checkSprites(); });
        spriteManager.getSpriteAsync(825, 0, img -> { cornerTR = img; checkSprites(); });
        spriteManager.getSpriteAsync(826, 0, img -> { cornerBL = img; checkSprites(); });
        spriteManager.getSpriteAsync(827, 0, img -> { cornerBR = img; checkSprites(); });
        spriteManager.getSpriteAsync(820, 0, img -> { edgeTop = img; checkSprites(); });
        spriteManager.getSpriteAsync(821, 0, img -> { edgeLeft = img; checkSprites(); });
        spriteManager.getSpriteAsync(822, 0, img -> { edgeBottom = img; checkSprites(); });
        spriteManager.getSpriteAsync(823, 0, img -> { edgeRight = img; checkSprites(); });
    }

    private static void checkSprites()
    {
        spritesLoaded = cornerTL != null && cornerTR != null
            && cornerBL != null && cornerBR != null
            && edgeTop != null && edgeBottom != null
            && edgeLeft != null && edgeRight != null;
    }

    /** Content inset: border thickness + margin. */
    public static int getInset()
    {
        return BORDER_THICKNESS + MARGIN;
    }

    protected NativeTooltip()
    {
        setOpaque(false);
        setBorder(null);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();

        paintBackground(g2, w, h);
        paintBorder(g2, w, h);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        paintContent(g2, w, h);

        g2.dispose();
    }

    /**
     * Subclasses paint their content here.
     * Background, border, and AA hints are already applied.
     */
    protected abstract void paintContent(Graphics2D g2, int w, int h);

    static void paintBackground(Graphics2D g2, int w, int h)
    {
        g2.setColor(FALLBACK_BG);
        g2.fillRect(0, 0, w, h);
    }

    static void paintBorder(Graphics2D g2, int w, int h)
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

        // Top edge
        int tew = edgeTop.getWidth();
        int teh = edgeTop.getHeight();
        for (int x = cw; x < w - cw; x += tew)
        {
            int drawW = Math.min(tew, w - cw - x);
            g2.drawImage(edgeTop, x, 0, x + drawW, teh,
                0, 0, drawW, teh, null);
        }

        // Bottom edge
        int bew = edgeBottom.getWidth();
        int beh = edgeBottom.getHeight();
        for (int x = cw; x < w - cw; x += bew)
        {
            int drawW = Math.min(bew, w - cw - x);
            g2.drawImage(edgeBottom, x, h - beh, x + drawW, h,
                0, 0, drawW, beh, null);
        }

        // Left edge
        int lew = edgeLeft.getWidth();
        int leh = edgeLeft.getHeight();
        for (int y = ch; y < h - ch; y += leh)
        {
            int drawH = Math.min(leh, h - ch - y);
            g2.drawImage(edgeLeft, 0, y, lew, y + drawH,
                0, 0, lew, drawH, null);
        }

        // Right edge
        int rew = edgeRight.getWidth();
        int reh = edgeRight.getHeight();
        for (int y = ch; y < h - ch; y += reh)
        {
            int drawH = Math.min(reh, h - ch - y);
            g2.drawImage(edgeRight, w - rew, y, w, y + drawH,
                0, 0, rew, drawH, null);
        }
    }
}
