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
 * Header (title, obtained, rank) via TitleTooltip, then auto-wrapping item grid.
 *
 * <p>Standard 5-column max: {@code new ImgTooltip()}
 * <p>Wide 10-column max for large clue tiers: {@code new ImgTooltip(10)}
 */
public class ImgTooltip extends TitleTooltip
{
    private static final int DEFAULT_COLS = 5;

    private static final int SPRITE_SIZE = 32;
    private static final int PADDING = 4;

    private static final Color QTY_COLOR = new Color(255, 255, 0);
    private static final Color QTY_SHADOW = new Color(0, 0, 0);
    private static final Color ITEM_HOVER_BG = new Color(80, 70, 50);
    private static final Color NOTICE_COLOR = new Color(160, 160, 160);

    private final int gridCols;
    private int effectiveCols;
    private int hoveredItemIndex = -1;

    private int totalItems;
    private List<Integer> allItemIds;
    private Set<Integer> obtainedIds;
    private Map<Integer, Integer> obtainedCounts;
    private BufferedImage[] sprites;

    /** Standard 5-column max tooltip. */
    public ImgTooltip()
    {
        this(DEFAULT_COLS);
    }

    /** Configurable max column count — use {@code 10} for wide clue-tier grids. */
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
     * Set item grid data. Call after setTitle/setObtained/setRank.
     * Holds strong references to sprites so they survive ItemManager cache eviction.
     */
    public void setItems(int totalItems, List<Integer> allItemIds, Set<Integer> obtainedIds,
                         Map<Integer, Integer> obtainedCounts, ItemManager itemManager)
    {
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
    protected Dimension getContentSize(int availableWidth)
    {
        boolean hasItems = allItemIds != null && !allItemIds.isEmpty();
        int itemCount = hasItems ? allItemIds.size() : Math.max(totalItems, 1);
        int cellSize = SPRITE_SIZE + PADDING;

        // 5 cols minimum for bosses, 10 for wide clue grids, expand for wider headers
        int baseMin = gridCols > DEFAULT_COLS ? gridCols : DEFAULT_COLS;
        int minCols = Math.min(baseMin, Math.max(itemCount, 1));
        int fitCols = Math.max(minCols, (availableWidth + PADDING) / cellSize);
        effectiveCols = Math.min(gridCols, fitCols);
        effectiveCols = Math.min(effectiveCols, Math.max(itemCount, 1));

        int rows = (itemCount + effectiveCols - 1) / effectiveCols;
        int gridWidth = effectiveCols * cellSize - PADDING;
        int gridHeight = rows * cellSize - PADDING;

        if (!hasItems)
        {
            FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());
            int noticeWidth = sfm.stringWidth("No TempleOSRS Data");
            gridWidth = Math.max(gridWidth, noticeWidth);
        }

        return new Dimension(gridWidth, gridHeight);
    }

    @Override
    protected void paintBody(Graphics2D g2, int w, int h, int startY)
    {
        if (getTitle() == null)
        {
            return;
        }

        int inset = getInset();
        boolean hasItems = allItemIds != null && !allItemIds.isEmpty();

        // No clog data — center notice in the grid area
        if (!hasItems)
        {
            g2.setFont(FontManager.getRunescapeSmallFont());
            g2.setColor(NOTICE_COLOR);
            String notice = "No TempleOSRS Data";
            FontMetrics nfm = g2.getFontMetrics();

            int itemCount = Math.max(totalItems, 1);
            int cols = Math.min(effectiveCols, Math.max(itemCount, 1));
            int rows = (itemCount + cols - 1) / cols;
            int cellSize = SPRITE_SIZE + PADDING;
            int gridHeight = rows * cellSize - PADDING;

            int nx = inset + (w - inset * 2 - nfm.stringWidth(notice)) / 2;
            int ny = startY + (gridHeight - nfm.getHeight()) / 2 + nfm.getAscent();
            g2.drawString(notice, nx, ny);
            return;
        }

        // Item grid with auto-wrapped columns
        if (sprites != null)
        {
            g2.setFont(FontManager.getRunescapeSmallFont());

            for (int i = 0; i < allItemIds.size(); i++)
            {
                int col = i % effectiveCols;
                int row = i / effectiveCols;
                int x = inset + col * (SPRITE_SIZE + PADDING);
                int y = startY + row * (SPRITE_SIZE + PADDING);

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

    private int getItemIndexAt(int mx, int my)
    {
        if (allItemIds == null || allItemIds.isEmpty())
        {
            return -1;
        }

        int inset = getInset();
        int gridStartY = inset + getHeaderZoneHeight();

        int relX = mx - inset;
        int relY = my - gridStartY;
        if (relX < 0 || relY < 0)
        {
            return -1;
        }

        int cellSize = SPRITE_SIZE + PADDING;
        int col = relX / cellSize;
        int row = relY / cellSize;
        if (col >= effectiveCols)
        {
            return -1;
        }

        int idx = row * effectiveCols + col;
        return idx < allItemIds.size() ? idx : -1;
    }
}
