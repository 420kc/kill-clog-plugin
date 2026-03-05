package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Custom tooltip that paints item sprites in a grid for boss/activity collection log data.
 * Uses Graphics2D painting instead of HTML to reliably render item images.
 *
 * <p>Standard 5-column layout: {@code new BossTooltip()}
 * <p>Wide 10-column layout for large clue tiers: {@code new BossTooltip(10)}
 */
public class BossTooltip extends JToolTip
{
    private static final int DEFAULT_COLS = 5;

    private static final int SPRITE_SIZE = 32;
    private static final int PADDING = 4;
    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 14;
    private static final int NAME_LINE_HEIGHT = 18;
    private static final int SEPARATOR_GAP = 6;

    private static final Color SEPARATOR_COLOR = new Color(80, 70, 50);
    private static final Color OSRS_ORANGE = new Color(255, 152, 31);
    private static final Color CLOG_GREEN = new Color(0, 255, 0);
    private static final Color CLOG_YELLOW = new Color(255, 255, 0);
    private static final Color QTY_COLOR = new Color(255, 255, 0);
    private static final Color QTY_SHADOW = new Color(0, 0, 0);
    private static final Color ITEM_HOVER_BG = new Color(80, 70, 50);

    private final int gridCols;
    private int hoveredItemIndex = -1;

    private String bossName;
    private int rank;
    private int obtainedCount;
    private int totalItems;
    private List<Integer> allItemIds;
    private Set<Integer> obtainedIds;
    private Map<Integer, Integer> obtainedCounts;
    private ItemManager itemManager;

    /** Standard 5-column tooltip. */
    public BossTooltip()
    {
        this(DEFAULT_COLS);
    }

    /** Configurable column count — use {@code 10} for wide clue-tier grids. */
    public BossTooltip(int gridCols)
    {
        this.gridCols = gridCols;
        setOpaque(false);
        setBorder(null);

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                int idx = getItemIndexAt(e.getX(), e.getY());
                if (idx != hoveredItemIndex)
                {
                    hoveredItemIndex = idx;
                    repaint();
                }
            }
        });

        addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e)
            {
                if (hoveredItemIndex != -1)
                {
                    hoveredItemIndex = -1;
                    repaint();
                }
            }
        });
    }

    /**
     * Populate tooltip data before display.
     * Registers async repaint callbacks so sprites appear as they load.
     */
    public void setData(String bossName, int rank, int obtainedCount, int totalItems,
                        List<Integer> allItemIds, Set<Integer> obtainedIds,
                        Map<Integer, Integer> obtainedCounts, ItemManager itemManager)
    {
        this.bossName = bossName;
        this.rank = rank;
        this.obtainedCount = obtainedCount;
        this.totalItems = totalItems;
        this.allItemIds = allItemIds;
        this.obtainedIds = obtainedIds;
        this.obtainedCounts = obtainedCounts;
        this.itemManager = itemManager;

        if (allItemIds == null || itemManager == null)
        {
            return;
        }

        for (int itemId : allItemIds)
        {
            int count = obtainedIds != null && obtainedIds.contains(itemId)
                ? obtainedCounts.getOrDefault(itemId, 1) : 1;
            BufferedImage img = itemManager.getImage(itemId, count, false);
            if (img instanceof AsyncBufferedImage)
            {
                ((AsyncBufferedImage) img).onLoaded(() ->
                    SwingUtilities.invokeLater(this::repaint));
            }
        }
    }

    @Override
    public Dimension getPreferredSize()
    {
        if (allItemIds == null || allItemIds.isEmpty())
        {
            return new Dimension(100, 30);
        }

        int detailLines = rank > 0 ? 2 : 1;
        int headerHeight = NAME_LINE_HEIGHT + detailLines * LINE_HEIGHT;

        int spriteRows = (allItemIds.size() + gridCols - 1) / gridCols;
        int gridWidth = gridCols * (SPRITE_SIZE + PADDING) - PADDING;
        int gridHeight = spriteRows * (SPRITE_SIZE + PADDING) - PADDING;

        int inset = ParchmentTooltip.getBorderThickness() + MARGIN;

        // Flex width to accommodate long boss names (e.g. "Chambers of Xeric: Challenge Mode")
        FontMetrics nfm = getFontMetrics(FontManager.getRunescapeBoldFont());
        int titleWidth = bossName != null ? nfm.stringWidth(bossName) : 0;
        int contentWidth = Math.max(gridWidth, titleWidth);

        int height = inset + headerHeight + SEPARATOR_GAP + 1 + SEPARATOR_GAP + gridHeight + inset;
        int width = contentWidth + inset * 2;

        return new Dimension(width, height);
    }

    private int getItemIndexAt(int mx, int my)
    {
        if (allItemIds == null || allItemIds.isEmpty())
        {
            return -1;
        }

        int inset = ParchmentTooltip.getBorderThickness() + MARGIN;
        int detailLines = rank > 0 ? 2 : 1;
        int headerHeight = NAME_LINE_HEIGHT + detailLines * LINE_HEIGHT;
        int sepY = inset + headerHeight + SEPARATOR_GAP;
        int gridStartY = sepY + 1 + SEPARATOR_GAP;

        int relX = mx - inset;
        int relY = my - gridStartY;
        if (relX < 0 || relY < 0)
        {
            return -1;
        }

        int cellSize = SPRITE_SIZE + PADDING;
        int col = relX / cellSize;
        int row = relY / cellSize;
        if (col >= gridCols)
        {
            return -1;
        }

        int idx = row * gridCols + col;
        return idx < allItemIds.size() ? idx : -1;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        int inset = ParchmentTooltip.getBorderThickness() + MARGIN;

        ParchmentTooltip.paintParchmentFill(g2, w, h);
        ParchmentTooltip.paintStoneBorder(g2, w, h);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (bossName == null)
        {
            g2.dispose();
            return;
        }

        // Row 1: boss name (bold)
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics nfm = g2.getFontMetrics();
        int lineY = inset + nfm.getAscent();
        g2.setColor(OSRS_ORANGE);
        g2.drawString(bossName, inset, lineY);

        // Row 2: "Obtained: X/Y"
        g2.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g2.getFontMetrics();
        lineY += NAME_LINE_HEIGHT;
        String obtainedLabel = "Obtained: ";
        g2.setColor(OSRS_ORANGE);
        g2.drawString(obtainedLabel, inset, lineY);
        int labelWidth = fm.stringWidth(obtainedLabel);
        String countText = obtainedCount + "/" + totalItems;
        g2.setColor(obtainedCount >= totalItems && totalItems > 0 ? CLOG_GREEN : CLOG_YELLOW);
        g2.drawString(countText, inset + labelWidth, lineY);

        // Row 3 (optional): "Rank: N"
        if (rank > 0)
        {
            lineY += LINE_HEIGHT;
            String rankLabel = "Rank: ";
            g2.setColor(OSRS_ORANGE);
            g2.drawString(rankLabel, inset, lineY);
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("%,d", rank), inset + fm.stringWidth(rankLabel), lineY);
        }

        // Separator
        int headerLines = rank > 0 ? 3 : 2;
        int sepY = inset + NAME_LINE_HEIGHT + (headerLines - 1) * LINE_HEIGHT + SEPARATOR_GAP;
        g2.setColor(SEPARATOR_COLOR);
        g2.drawLine(inset, sepY, w - inset - 1, sepY);

        // Item grid
        if (allItemIds != null && itemManager != null)
        {
            int gridStartY = sepY + 1 + SEPARATOR_GAP;

            for (int i = 0; i < allItemIds.size(); i++)
            {
                int col = i % gridCols;
                int row = i / gridCols;
                int x = inset + col * (SPRITE_SIZE + PADDING);
                int y = gridStartY + row * (SPRITE_SIZE + PADDING);

                if (i == hoveredItemIndex)
                {
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(ITEM_HOVER_BG);
                    g2.fillRect(x - 1, y - 1, SPRITE_SIZE + 2, SPRITE_SIZE + 2);
                }

                int itemId = allItemIds.get(i);
                boolean obtained = obtainedIds.contains(itemId);
                int count = obtained ? obtainedCounts.getOrDefault(itemId, 1) : 1;

                BufferedImage sprite = itemManager.getImage(itemId, count, false);
                if (sprite != null)
                {
                    g2.setComposite(obtained
                        ? AlphaComposite.SrcOver
                        : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

                    int sx = x + (SPRITE_SIZE - sprite.getWidth()) / 2;
                    int sy = y + (SPRITE_SIZE - sprite.getHeight()) / 2;
                    g2.drawImage(sprite, sx, sy, null);
                    g2.setComposite(AlphaComposite.SrcOver);
                }

                // Quantity overlay — top-left, matching native OSRS clog
                if (obtained && count > 1)
                {
                    FontMetrics qfm = g2.getFontMetrics();
                    String qtyText = String.valueOf(count);
                    g2.setColor(QTY_SHADOW);
                    g2.drawString(qtyText, x + 1, y + qfm.getAscent() + 1);
                    g2.setColor(QTY_COLOR);
                    g2.drawString(qtyText, x, y + qfm.getAscent());
                }
            }
        }

        g2.dispose();
    }
}
