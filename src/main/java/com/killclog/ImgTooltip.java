package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Sprite grid tooltip for collection log data.
 * Header with name, obtained count, rank, then item sprites in a configurable grid.
 *
 * <p>Standard 5-column layout: {@code new ImgTooltip()}
 * <p>Wide 10-column layout for large clue tiers: {@code new ImgTooltip(10)}
 */
public class ImgTooltip extends NativeTooltip
{
    private static final int DEFAULT_COLS = 5;

    private static final int SPRITE_SIZE = 32;
    private static final int PADDING = 4;
    private static final int NAME_LINE_HEIGHT = 18;
    private static final int SEPARATOR_GAP = 6;

    private static final Color SEPARATOR_COLOR = new Color(80, 70, 50);
    private static final Color CLOG_GREEN = new Color(0, 255, 0);
    private static final Color CLOG_YELLOW = new Color(255, 255, 0);
    private static final Color QTY_COLOR = new Color(255, 255, 0);
    private static final Color QTY_SHADOW = new Color(0, 0, 0);
    private static final Color ITEM_HOVER_BG = new Color(80, 70, 50);

    private final int gridCols;
    private int hoveredItemIndex = -1;

    private String name;
    private int rank;
    private int obtainedCount;
    private int totalItems;
    private List<Integer> allItemIds;
    private Set<Integer> obtainedIds;
    private Map<Integer, Integer> obtainedCounts;
    private BufferedImage[] sprites;

    /** Standard 5-column tooltip. */
    public ImgTooltip()
    {
        this(DEFAULT_COLS);
    }

    /** Configurable column count — use {@code 10} for wide clue-tier grids. */
    public ImgTooltip(int gridCols)
    {
        this.gridCols = gridCols;

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
     * Holds strong references to sprites so they survive ItemManager cache eviction.
     */
    public void setData(String name, int rank, int obtainedCount, int totalItems,
                        List<Integer> allItemIds, Set<Integer> obtainedIds,
                        Map<Integer, Integer> obtainedCounts, ItemManager itemManager)
    {
        this.name = name;
        this.rank = rank;
        this.obtainedCount = obtainedCount;
        this.totalItems = totalItems;
        this.allItemIds = allItemIds;
        this.obtainedIds = obtainedIds;
        this.obtainedCounts = obtainedCounts;

        if (allItemIds == null || itemManager == null)
        {
            sprites = null;
            return;
        }

        sprites = new BufferedImage[allItemIds.size()];
        for (int i = 0; i < allItemIds.size(); i++)
        {
            int itemId = allItemIds.get(i);
            int count = obtainedIds != null && obtainedIds.contains(itemId)
                ? obtainedCounts.getOrDefault(itemId, 1) : 1;
            BufferedImage img = itemManager.getImage(itemId, count, false);
            sprites[i] = img;
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

        int inset = getInset();

        FontMetrics nfm = getFontMetrics(FontManager.getRunescapeBoldFont());
        int titleWidth = name != null ? nfm.stringWidth(name) : 0;
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

        int inset = getInset();
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
    protected void paintContent(Graphics2D g2, int w, int h)
    {
        if (name == null)
        {
            return;
        }

        int inset = getInset();

        // Row 1: name (bold)
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics nfm = g2.getFontMetrics();
        int lineY = inset + nfm.getAscent();
        g2.setColor(OSRS_ORANGE);
        g2.drawString(name, inset, lineY);

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
        if (allItemIds != null && sprites != null)
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

                BufferedImage sprite = i < sprites.length ? sprites[i] : null;
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
    }
}
