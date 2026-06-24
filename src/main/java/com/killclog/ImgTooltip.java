package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

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
	private TooltipItemSprites itemSprites;
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

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
	}

	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
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
			itemSprites = null;
			itemHover.setHitBoxes(Collections.emptyList());
			itemHover.clear();
			return;
		}

		itemSprites = TooltipItemSprites.load(allItemIds, itemNames, itemManager, spriteSize,
			itemId -> obtainedIds != null && obtainedIds.contains(itemId) && obtainedCounts != null
				? obtainedCounts.getOrDefault(itemId, 1) : 1,
			this);
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
		itemHover.setHitBoxes(Collections.emptyList());

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
		if (itemSprites != null)
		{
			List<TooltipItemHover.HitBox> nextHitBoxes = new ArrayList<>(allItemIds.size());
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
				nextHitBoxes.add(new TooltipItemHover.HitBox(itemId, itemNameAt(i),
					new Rectangle(x, y, spriteSize, spriteSize), obtained));

				BufferedImage sprite = itemSprites.spriteAt(i);
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
			itemHover.setHitBoxes(nextHitBoxes);
		}
	}

	@Override
	protected String getHeaderRightText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getHeaderRightColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	private String itemNameAt(int index)
	{
		if (itemSprites == null)
		{
			return null;
		}
		return itemSprites.nameAt(index);
	}

}
