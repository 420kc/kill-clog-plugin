package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
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

	private final int gridCols;
	private final int spriteSize;
	private int effectiveCols;
	private String notice = "No collection log synced";
	private BufferedImage noticeIcon;

	private int totalItems;
	private List<Integer> allItemIds;
	private Set<Integer> obtainedIds;
	private Map<Integer, Integer> obtainedCounts;
	private BufferedImage[] sprites;
	private String[] itemNames;
	private List<ItemHitBox> hitBoxes = Collections.emptyList();
	private int hoveredItemId = -1;
	private String hoveredItemName;

	/** Configurable min column count. */
	public ImgTooltip(int gridCols)
	{
		this(gridCols, DEFAULT_SPRITE_SIZE);
	}

	/** Compact mode - smaller sprites for dense grids like clue tiers. */
	public ImgTooltip(int gridCols, int spriteSize)
	{
		this.gridCols = gridCols;
		this.spriteSize = spriteSize;
		installItemMouseListeners();
	}

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	/**
	 * Set item grid data. Call after setTitle/setObtained/setRank.
	 * Holds strong references to sprites so they survive ItemManager cache eviction.
	 */
	public void setItems(int totalItems, List<Integer> allItemIds, Set<Integer> obtainedIds,
		Map<Integer, Integer> obtainedCounts, ItemManager itemManager)
	{
		setItems(totalItems, allItemIds, obtainedIds, obtainedCounts, Collections.emptyMap(), itemManager);
	}

	/**
	 * Set item grid data. Call after setTitle/setObtained/setRank.
	 * Holds strong references to sprites so they survive ItemManager cache eviction.
	 */
	public void setItems(int totalItems, List<Integer> allItemIds, Set<Integer> obtainedIds,
		Map<Integer, Integer> obtainedCounts, Map<Integer, String> itemNames,
		ItemManager itemManager)
	{
		this.totalItems = totalItems;
		this.allItemIds = allItemIds;
		this.obtainedIds = obtainedIds;
		this.obtainedCounts = obtainedCounts;

		if (allItemIds == null || itemManager == null)
		{
			sprites = null;
			this.itemNames = null;
			clearHover();
			return;
		}

		sprites = new BufferedImage[allItemIds.size()];
		this.itemNames = new String[allItemIds.size()];
		for (int i = 0; i < allItemIds.size(); i++)
		{
			int itemId = allItemIds.get(i);
			this.itemNames[i] = TooltipItemLink.itemName(itemNames, itemId);
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

	public void setNotice(String msg, BufferedImage icon)
	{
		this.notice = msg;
		this.noticeIcon = icon;
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

		effectiveCols = gridColumnsForItemCount(gridCols, itemCount, spriteSize);

		int rows = (itemCount + effectiveCols - 1) / effectiveCols;
		int gridWidth = effectiveCols * cellSize - PADDING;
		int gridHeight = rows * cellSize - PADDING;

		if (!hasItems)
		{
			FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());
			int noticeWidth = sfm.stringWidth(notice);
			if (noticeIcon != null)
			{
				noticeWidth += noticeIcon.getWidth() + 3;
			}
			gridWidth = Math.max(gridWidth, noticeWidth);
		}

		return new Dimension(gridWidth, gridHeight);
	}

	static int gridColumnsForItemCount(int requestedCols, int itemCount, int spriteSize)
	{
		int normalizedItemCount = Math.max(itemCount, 1);
		if (spriteSize < DEFAULT_SPRITE_SIZE)
		{
			return Math.max(requestedCols, 1);
		}
		return Math.min(Math.max(requestedCols, 1), Math.max(4, normalizedItemCount));
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
		hitBoxes = Collections.emptyList();

		// No clog data - center notice in the grid area
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

			int totalWidth = nfm.stringWidth(notice);
			int iconW = 0;
			if (noticeIcon != null)
			{
				iconW = noticeIcon.getWidth() + 3;
				totalWidth += iconW;
			}

			int nx = inset + (w - inset * 2 - totalWidth) / 2;
			int ny = startY + (gridHeight - nfm.getHeight()) / 2 + nfm.getAscent();

			if (noticeIcon != null)
			{
				int iconY = ny - noticeIcon.getHeight() + nfm.getDescent();
				g2.drawImage(noticeIcon, nx, iconY, null);
				nx += iconW;
			}
			g2.drawString(notice, nx, ny);
			return;
		}

		// Item grid with auto-wrapped columns
		if (sprites != null)
		{
			List<ItemHitBox> nextHitBoxes = new ArrayList<>(allItemIds.size());
			g2.setFont(FontManager.getRunescapeSmallFont());

			int cellSize = spriteSize + PADDING;
			int gridWidth = effectiveCols * cellSize - PADDING;
			int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;
			int gridStartY = startY;

			for (int i = 0; i < allItemIds.size(); i++)
			{
				int col = i % effectiveCols;
				int row = i / effectiveCols;
				int x = gridOffsetX + col * cellSize;
				int y = gridStartY + row * cellSize;

				int itemId = allItemIds.get(i);
				boolean obtained = obtainedIds.contains(itemId);
				int count = obtained ? obtainedCounts.getOrDefault(itemId, 1) : 1;
				nextHitBoxes.add(new ItemHitBox(itemId, itemNameAt(i),
					new Rectangle(x, y, spriteSize, spriteSize)));

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

				// Quantity overlay - skip on compact sprites where text is unreadable
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
			hitBoxes = nextHitBoxes;
		}
	}

	@Override
	protected String getHeaderRightText()
	{
		return hoveredItemName;
	}

	private String itemNameAt(int index)
	{
		if (itemNames == null || index < 0 || index >= itemNames.length)
		{
			return null;
		}
		return itemNames[index];
	}

	private void installItemMouseListeners()
	{
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				updateHoveredItem(e.getX(), e.getY());
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				updateHoveredItem(e.getX(), e.getY());
				if (e.getButton() == MouseEvent.BUTTON1 && hoveredItemId > 0)
				{
					TooltipItemLink.openWiki(hoveredItemId);
					e.consume();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clearHover();
			}
		});
	}

	private void updateHoveredItem(int mx, int my)
	{
		ItemHitBox hitBox = findHitBox(mx, my);
		int nextId = hitBox != null ? hitBox.itemId : -1;
		if (nextId == hoveredItemId)
		{
			return;
		}
		hoveredItemId = nextId;
		hoveredItemName = hitBox != null ? hitBox.itemName : null;
		setCursor(Cursor.getPredefinedCursor(hitBox != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
		repaint();
	}

	private ItemHitBox findHitBox(int mx, int my)
	{
		for (ItemHitBox hitBox : hitBoxes)
		{
			if (hitBox.bounds.contains(mx, my))
			{
				return hitBox;
			}
		}
		return null;
	}

	private void clearHover()
	{
		if (hoveredItemId != -1 || hoveredItemName != null)
		{
			hoveredItemId = -1;
			hoveredItemName = null;
			setCursor(Cursor.getDefaultCursor());
			repaint();
		}
	}

	private static final class ItemHitBox
	{
		private final int itemId;
		private final String itemName;
		private final Rectangle bounds;

		private ItemHitBox(int itemId, String itemName, Rectangle bounds)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.bounds = bounds;
		}
	}
}
