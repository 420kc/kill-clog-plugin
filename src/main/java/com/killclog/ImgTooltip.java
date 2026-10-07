package com.killclog;

import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import net.runelite.client.game.ItemManager;

/**
 * Sprite grid tooltip for collection log data: the header (title, counts, rank) via TitleTooltip, the band the
 * hovered item is named in, then the item grid at full size. A grid taller than the rows {@link CardBody#WINDOW}
 * holds scrolls, a row a notch.
 */
public class ImgTooltip extends TitleTooltip
{
	private final int gridCols;
	@Setter
	private String notice = "No collection log synced";

	private int totalItems;
	private List<Integer> allItemIds;
	private Set<Integer> obtainedIds;
	private Map<Integer, Integer> obtainedCounts;
	private TooltipItemSprites itemSprites;
	// The window holds whole sprite rows and moves a row a notch, so a scrolled grid never shows half a sprite.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.GRID_WINDOW, CardBody.GRID_CELL);

	/** Configurable min column count. */
	public ImgTooltip(int gridCols)
	{
		this.gridCols = gridCols;
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
		return body.add(CardBody.headerHoverBand()).add(CardBody.scroll(scroll,
			CardBody.grid(gridCols, itemSprites, null, allItemIds, obtainedIds, obtainedCounts)));
	}

	/** No clog data: the notice centered where the grid would be. */
	private CardBody.Part notice()
	{
		int count = Math.max(totalItems, 1);
		int cols = CardBody.gridColumns(gridCols, count);
		int height = (count + cols - 1) / cols * CardBody.GRID_CELL - CardBody.GRID_GAP;
		return CardBody.part(c -> Math.max(cols * CardBody.GRID_CELL - CardBody.GRID_GAP, c.fm.stringWidth(notice)),
			c -> height, (c, y) ->
		{
			c.g.setColor(NOTICE_COLOR);
			c.g.drawString(notice, c.inset() + (c.w - 2 * c.inset() - c.fm.stringWidth(notice)) / 2,
				y + (height - c.fm.getHeight()) / 2 + c.fm.getAscent());
		});
	}
}
