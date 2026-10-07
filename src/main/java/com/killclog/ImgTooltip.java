package com.killclog;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * Sprite grid tooltip for collection log data.
 * Header (title, obtained, rank) via TitleTooltip, then auto-wrapping item grid.
 *
 * <p>Compact 15px sprites for dense grids: {@code new ImgTooltip(5, 15)}
 */
public class ImgTooltip extends TitleTooltip
{
	private static final int DEFAULT_SPRITE_SIZE = 32;
	private static final int PADDING = 4;


	private final int gridCols;
	private final int spriteSize;
	private int effectiveCols;
	@Setter
	private String notice = "No collection log synced";

	private int totalItems;
	private List<Integer> allItemIds;
	private Set<Integer> obtainedIds;
	private Map<Integer, Integer> obtainedCounts;
	private TooltipItemSprites itemSprites;

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
		itemNameInHeader = true;
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

	/** Without items or a notice the card is its header alone, as the dense grids are in a comparison. */
	@Override
	protected boolean hasBody()
	{
		return allItemIds != null && !allItemIds.isEmpty() || notice != null;
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		if (!hasBody())
		{
			return new Dimension(0, 0);
		}
		boolean hasItems = allItemIds != null && !allItemIds.isEmpty();
		int itemCount = hasItems ? allItemIds.size() : Math.max(totalItems, 1);
		int cellSize = spriteSize + PADDING;

		effectiveCols = gridColumnsForItemCount(gridCols, itemCount, spriteSize);

		// A long title already pays for tooltip width; fill it with extra
		// columns so the grid never floats between wide dead margins.
		if (hasItems && availableWidth > 0)
		{
			int fitCols = (availableWidth + PADDING) / cellSize;
			if (fitCols > effectiveCols)
			{
				effectiveCols = Math.min(fitCols, itemCount);
			}
		}

		int rows = (itemCount + effectiveCols - 1) / effectiveCols;
		int gridWidth = effectiveCols * cellSize - PADDING;
		int gridHeight = rows * cellSize - PADDING;

		if (!hasItems)
		{
			FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());
			gridWidth = Math.max(gridWidth, sfm.stringWidth(notice));
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
		if (getTitle() == null || !hasBody())
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

			int nx = inset + (w - inset * 2 - nfm.stringWidth(notice)) / 2;
			int ny = startY + (gridHeight - nfm.getHeight()) / 2 + nfm.getAscent();
			g2.drawString(notice, nx, ny);
			return;
		}

		// Item grid with auto-wrapped columns
		if (itemSprites != null)
		{
			g2.setFont(FontManager.getRunescapeSmallFont());
			int cellSize = spriteSize + PADDING;
			int gridWidth = effectiveCols * cellSize - PADDING;
			// Quantities skip compact sprites, where the text is unreadable.
			itemHover.setHitBoxes(TooltipItemSprites.paintGrid(g2, itemSprites, null, 0, allItemIds,
				obtainedIds, obtainedCounts, inset + (w - 2 * inset - gridWidth) / 2, startY, effectiveCols,
				spriteSize, cellSize, spriteSize >= DEFAULT_SPRITE_SIZE));
		}
	}

}
