package com.killclog;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    // Close button sprites (535 normal, 536 hovered) — shared across all tooltips
    private static final int CLOSE_BTN_SIZE = 20;
    private static final int CLOSE_BTN_PAD = 4;
    private static volatile BufferedImage closeBtnNormal;
    private static volatile BufferedImage closeBtnHovered;

    // Tiled parchment background from game (sprite 297 = TRADEBACKING)
    private static volatile BufferedImage parchmentBg;

    // 9-slice border sprites from game (iron rivets — native collection log style)
    private static volatile BufferedImage cornerTL, cornerTR, cornerBL, cornerBR;
    private static volatile BufferedImage edgeTop, edgeBottom, edgeLeft, edgeRight;
    private static volatile boolean spritesLoaded;

    /**
     * Load border sprites from the game via SpriteManager.
     * Call once when the client is ready.
     */
    public static void loadSprites(SpriteManager spriteManager)
    {
        spriteManager.getSpriteAsync(297, 0, img -> parchmentBg = img);
        spriteManager.getSpriteAsync(831, 0, img -> closeBtnNormal = scaleSprite(img, CLOSE_BTN_SIZE));
        spriteManager.getSpriteAsync(832, 0, img -> closeBtnHovered = scaleSprite(img, CLOSE_BTN_SIZE));
        // Iron rivets: corners 310-313, edges 314 (horiz) + 315 (vert)
        spriteManager.getSpriteAsync(310, 0, img -> { cornerTL = img; checkSprites(); });
        spriteManager.getSpriteAsync(311, 0, img -> { cornerTR = img; checkSprites(); });
        spriteManager.getSpriteAsync(312, 0, img -> { cornerBL = img; checkSprites(); });
        spriteManager.getSpriteAsync(313, 0, img -> { cornerBR = img; checkSprites(); });
        spriteManager.getSpriteAsync(314, 0, img -> { edgeTop = img; edgeBottom = rotate180(img); checkSprites(); });
        spriteManager.getSpriteAsync(315, 0, img -> { edgeRight = img; edgeLeft = rotate180(img); checkSprites(); });
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

    /**
     * Right-side padding reserved for the close button in the header zone.
     * Subclasses use this to limit header text width.
     */
    public int getHeaderRightPad()
    {
        return closeAction != null ? CLOSE_BTN_SIZE + CLOSE_BTN_PAD : 0;
    }

    // Per-instance close button state
    private Runnable closeAction;
    private boolean closeBtnHover;

    /**
     * Enable the close button. When set, the tooltip paints an X in the
     * top-right corner (inside the stone border) and clicking it fires the action.
     */
    public void setCloseAction(Runnable action)
    {
        this.closeAction = action;
    }

    protected Runnable getCloseAction()
    {
        return closeAction;
    }

    protected NativeTooltip()
    {
        setOpaque(false);
        setBorder(null);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (closeAction != null && isInCloseButton(e.getX(), e.getY()))
                {
                    closeAction.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                updateCloseHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                if (closeBtnHover)
                {
                    closeBtnHover = false;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                updateCloseHover(e.getX(), e.getY());
            }
        });
    }

    protected boolean isInCloseButton(int mx, int my)
    {
        if (closeAction == null) return false;
        int bx = getWidth() - getInset() - CLOSE_BTN_SIZE;
        int by = getInset() - 1;
        return mx >= bx && mx <= bx + CLOSE_BTN_SIZE
            && my >= by && my <= by + CLOSE_BTN_SIZE;
    }

    private void updateCloseHover(int mx, int my)
    {
        if (closeAction == null) return;
        boolean inBtn = isInCloseButton(mx, my);
        if (inBtn != closeBtnHover)
        {
            closeBtnHover = inBtn;
            setCursor(inBtn
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
            repaint();
        }
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
        paintCloseButton(g2, w);

        g2.dispose();
    }

    /**
     * Subclasses paint their content here.
     * Background, border, and AA hints are already applied.
     */
    protected abstract void paintContent(Graphics2D g2, int w, int h);

    private static void paintBackground(Graphics2D g2, int w, int h)
    {
        if (parchmentBg != null)
        {
            int tw = parchmentBg.getWidth();
            int th = parchmentBg.getHeight();
            for (int y = 0; y < h; y += th)
            {
                for (int x = 0; x < w; x += tw)
                {
                    g2.drawImage(parchmentBg, x, y, null);
                }
            }
        }
        else
        {
            g2.setColor(FALLBACK_BG);
            g2.fillRect(0, 0, w, h);
        }
    }

    protected boolean isCloseHovered()
    {
        return closeBtnHover;
    }

    protected void paintCloseButton(Graphics2D g2, int w)
    {
        if (closeAction == null) return;
        BufferedImage sprite = closeBtnHover ? closeBtnHovered : closeBtnNormal;
        if (sprite == null) return;
        int x = w - getInset() - CLOSE_BTN_SIZE;
        int y = getInset() - 1;
        g2.drawImage(sprite, x, y, null);
    }

    private static BufferedImage scaleSprite(BufferedImage src, int size)
    {
        if (src == null) return null;
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage rotate180(BufferedImage src)
    {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotated.createGraphics();
        g.drawImage(src, w, h, 0, 0, 0, 0, w, h, null);
        g.dispose();
        return rotated;
    }

    private static void paintBorder(Graphics2D g2, int w, int h)
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
