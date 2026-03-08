package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Sprite grid tooltip for collection log data.
 * Header (title, obtained, rank) via TitleTooltip, then auto-wrapping item grid.
 *
 * <p>Standard 32px sprites: {@code new ImgTooltip()}
 * <p>Compact 15px sprites for dense grids: {@code new ImgTooltip(5, 15)}
 */
public class ImgTooltip extends TitleTooltip
{
    private static final int DEFAULT_COLS = 5;

    private static final int DEFAULT_SPRITE_SIZE = 32;
    private static final int PADDING = 4;

    private static final Color QTY_COLOR = new Color(255, 255, 0);
    private static final Color QTY_SHADOW = new Color(0, 0, 0);
    private static final Color ITEM_HOVER_BG = new Color(80, 70, 50);
    private static final Color NOTICE_COLOR = new Color(160, 160, 160);

    private final int gridCols;
    private final int spriteSize;
    private int effectiveCols;
    private int hoveredItemIndex = -1;
    private String notice = "No TempleOSRS Data";

    private int totalItems;
    private List<Integer> allItemIds;
    private Set<Integer> obtainedIds;
    private Map<Integer, Integer> obtainedCounts;
    private BufferedImage[] sprites;

    /** Standard 5-column, 32px sprite tooltip. */
    public ImgTooltip()
    {
        this(DEFAULT_COLS, DEFAULT_SPRITE_SIZE);
    }

    /** Configurable min column count. */
    public ImgTooltip(int gridCols)
    {
        this(gridCols, DEFAULT_SPRITE_SIZE);
    }

    /** Compact mode — smaller sprites for dense grids like clue tiers. */
    public ImgTooltip(int gridCols, int spriteSize)
    {
        this.gridCols = gridCols;
        this.spriteSize = spriteSize;

        addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                int idx = getItemIndexAt(e.getX(), e.getY());
                if (idx != hoveredItemIndex)
                {
                    hoveredItemIndex = idx;
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseExited(MouseEvent e)
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
            final int idx = i;
            if (img instanceof AsyncBufferedImage)
            {
                ((AsyncBufferedImage) img).onLoaded(() ->
                    SwingUtilities.invokeLater(() ->
                    {
                        sprites[idx] = resizeSprite(img);
                        repaint();
                    }));
            }
            sprites[i] = resizeSprite(img);
        }
    }

    public void setNotice(String msg)
    {
        this.notice = msg;
    }

    private BufferedImage resizeSprite(BufferedImage img)
    {
        if (img == null || spriteSize >= DEFAULT_SPRITE_SIZE)
        {
            return img;
        }
        return ImageUtil.resizeImage(
            ImageUtil.resizeCanvas(img, DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE),
            spriteSize, spriteSize);
    }

    @Override
    protected Dimension getContentSize(int availableWidth)
    {
        boolean hasItems = allItemIds != null && !allItemIds.isEmpty();
        int itemCount = hasItems ? allItemIds.size() : Math.max(totalItems, 1);
        int cellSize = spriteSize + PADDING;

        // Native parity: at least gridCols wide, expand to fill header-driven width
        effectiveCols = Math.max(gridCols, (availableWidth + PADDING) / cellSize);

        int rows = (itemCount + effectiveCols - 1) / effectiveCols;
        int gridWidth = effectiveCols * cellSize - PADDING;
        int gridHeight = rows * cellSize - PADDING;

        if (!hasItems)
        {
            FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());
            int noticeWidth = sfm.stringWidth(notice);
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
            String notice = this.notice;
            FontMetrics nfm = g2.getFontMetrics();

            int itemCount = Math.max(totalItems, 1);
            int cols = Math.min(effectiveCols, Math.max(itemCount, 1));
            int rows = (itemCount + cols - 1) / cols;
            int cellSize = spriteSize + PADDING;
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
            int cellSize = spriteSize + PADDING;
            int gridWidth = effectiveCols * cellSize - PADDING;
            int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

            for (int i = 0; i < allItemIds.size(); i++)
            {
                int col = i % effectiveCols;
                int row = i / effectiveCols;
                int x = gridOffsetX + col * cellSize;
                int y = startY + row * cellSize;

                if (i == hoveredItemIndex)
                {
                    g2.setColor(ITEM_HOVER_BG);
                    g2.fillRect(x - 1, y - 1, spriteSize + 2, spriteSize + 2);
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

                    int sx = x + (spriteSize - sprite.getWidth()) / 2;
                    int sy = y + (spriteSize - sprite.getHeight()) / 2;
                    g2.drawImage(sprite, sx, sy, null);
                    g2.setComposite(AlphaComposite.SrcOver);
                }

                // Quantity overlay — skip on compact sprites where text is unreadable
                if (obtained && count > 1 && spriteSize >= DEFAULT_SPRITE_SIZE)
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
        int w = getWidth();
        int gridStartY = inset + getHeaderZoneHeight();
        int cellSize = spriteSize + PADDING;
        int gridWidth = effectiveCols * cellSize - PADDING;
        int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

        int relX = mx - gridOffsetX;
        int relY = my - gridStartY;
        if (relX < 0 || relY < 0)
        {
            return -1;
        }

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
