package com.killclog;

import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;

/**
 * One Collection Log tab, opened from the Collection Log card where it was: the tab's progress and the way back,
 * then every page in the game's order as the game's tiles, two across, each its count and bar (Dylan, 2026-11-07). A
 * page opens its own popup. A tab past the card's window scrolls with the mouse wheel, a row of tiles a notch, a thin
 * rail showing where.
 */
public class ClogTabTooltip extends TitleTooltip
{
	private String[] names = new String[0];
	private int[] obtained = new int[0];
	private int[] total = new int[0];
	@Nullable
	private KillClogConfig progress;
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, GameTile.PAGE_HEIGHT + GameTile.GAP);
	@Nullable
	private ObjIntConsumer<MouseEvent> onOpenPage;
	private final CardBody.ClickRows pageRows = new CardBody.ClickRows(this, (e, page) ->
	{
		if (onOpenPage != null)
		{
			onOpenPage.accept(e, page);
			e.consume();
		}
	});

	/** The tab and the player's progress through it; null progress shows the tab's size alone. */
	void setTab(String tab, @Nullable int[] progress, int tabTotal)
	{
		setTitle(tab);
		if (progress != null && progress[0] >= 0)
		{
			setObtained(progress[0], progress[1]);
		}
		else
		{
			setObtainedPlaceholder(tabTotal);
		}
	}

	/**
	 * Each page's name and count in the game's order; a negative count is unknown and shows its size. With
	 * {@code progress}, a known page wears its progression color from those settings, name and count.
	 */
	void setPages(String[] names, int[] obtained, int[] total, @Nullable KillClogConfig progress)
	{
		this.names = names;
		this.obtained = obtained;
		this.total = total;
		this.progress = progress;
	}

	/** Called with the page's index when the player presses its tile. */
	void setOnOpenPage(@Nullable ObjIntConsumer<MouseEvent> onOpenPage)
	{
		this.onOpenPage = onOpenPage;
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	@Override
	protected CardBody body()
	{
		List<GameTile.Tile> pages = new ArrayList<>();
		for (int i = 0; i < names.length; i++)
		{
			pages.add(new GameTile.Tile(names[i], null, obtained[i], total[i], onOpenPage != null ? i : -1)
				.wear(progress != null && obtained[i] >= 0 ? ClogHelper.clogColor(obtained[i], total[i], progress) : null));
		}
		return new CardBody().add(CardBody.scroll(scroll, GameTile.pages(pageRows, pages)));
	}
}
