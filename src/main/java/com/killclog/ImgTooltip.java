package com.killclog;

import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import net.runelite.client.game.ItemManager;

/**
 * Sprite grid tooltip for collection log data: the header (title, counts, rank) via TitleTooltip, then the
 * item grid at full size. A grid taller than the rows {@link CardBody#WINDOW} holds scrolls, a row a notch.
 */
public class ImgTooltip extends TitleTooltip
{
	private static final int SPRITE_SIZE = 32;
	private static final int PADDING = 4;
	private static final int CELL = SPRITE_SIZE + PADDING;

	private final int gridCols;
	@Setter
	private String notice = "No collection log synced";

	private int totalItems;
	private List<Integer> allItemIds;
	private Set<Integer> obtainedIds;
	private Map<Integer, Integer> obtainedCounts;
	private TooltipItemSprites itemSprites;
	// The window holds whole sprite rows and moves a row a notch, so a scrolled grid never shows half a sprite.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW / CELL * CELL - PADDING, CELL);

	/** Configurable min column count. */
	public ImgTooltip(int gridCols)
	{
		this.gridCols = gridCols;
		itemNameInHeader = true;
	}

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
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

		itemSprites = TooltipItemSprites.load(allItemIds, itemNames, itemManager,
			itemId -> obtainedIds != null && obtainedIds.contains(itemId) && obtainedCounts != null
				? obtainedCounts.getOrDefault(itemId, 1) : 1,
			this);
	}

	static int gridColumnsForItemCount(int requestedCols, int itemCount)
	{
		return Math.min(Math.max(requestedCols, 1), Math.max(4, Math.max(itemCount, 1)));
	}

	/** The grid's own columns, or more when a long title already pays for the width. */
	private int columns(int available, int count)
	{
		int cols = gridColumnsForItemCount(gridCols, count);
		int fit = (available + PADDING) / CELL;
		return available > 0 && fit > cols ? Math.min(fit, count) : cols;
	}

	@Override
	protected CardBody body()
	{
		CardBody body = new CardBody();
		if (getTitle() == null)
		{
			return body;
		}
		if (allItemIds == null || allItemIds.isEmpty())
		{
			return body.add(notice());
		}
		int count = allItemIds.size();
		return body.add(CardBody.scroll(scroll, CardBody.part(c -> columns(c.available, count) * CELL - PADDING,
			c -> (count + columns(c.available, count) - 1) / columns(c.available, count) * CELL - PADDING, (c, y) ->
			{
				int cols = columns(c.available, count);
				int width = cols * CELL - PADDING;
				if (itemSprites != null)
				{
					c.hits.addAll(TooltipItemSprites.paintGrid(c.g, itemSprites, null, 0, allItemIds, obtainedIds,
						obtainedCounts, c.inset() + (c.w - 2 * c.inset() - width) / 2, y, cols, SPRITE_SIZE, CELL));
				}
			})));
	}

	/** No clog data: the notice centered where the grid would be. */
	private CardBody.Part notice()
	{
		int count = Math.max(totalItems, 1);
		int cols = gridColumnsForItemCount(gridCols, count);
		int height = (count + cols - 1) / cols * CELL - PADDING;
		return CardBody.part(c -> Math.max(cols * CELL - PADDING, c.fm.stringWidth(notice)), c -> height, (c, y) ->
		{
			c.g.setColor(NOTICE_COLOR);
			c.g.drawString(notice, c.inset() + (c.w - 2 * c.inset() - c.fm.stringWidth(notice)) / 2,
				y + (height - c.fm.getHeight()) / 2 + c.fm.getAscent());
		});
	}
}
