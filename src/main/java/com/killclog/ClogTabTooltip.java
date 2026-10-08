package com.killclog;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;

/**
 * One Collection Log tab, opened from the Collection Log card where it was: the tab's progress and the way back,
 * then every page in the game's order with the player's count. A page opens its own popup. A tab
 * more than a notch past {@link #VISIBLE_ROWS} pages scrolls with the mouse wheel, a thin rail showing where.
 */
public class ClogTabTooltip extends TitleTooltip
{
	static final int VISIBLE_ROWS = CardBody.WINDOW / LINE_HEIGHT;
	private static final int COUNT_GAP = 12;

	private String[] names = new String[0];
	private int[] obtained = new int[0];
	private int[] total = new int[0];
	@Nullable
	private KillClogConfig progress;
	// Three pages a notch.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, 3 * LINE_HEIGHT);
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
	 * {@code progress}, a known page's name and count wear its progression color from those settings.
	 */
	void setPages(String[] names, int[] obtained, int[] total, @Nullable KillClogConfig progress)
	{
		this.names = names;
		this.obtained = obtained;
		this.total = total;
		this.progress = progress;
	}

	/** Called with the page's index when the player presses its row. */
	void setOnOpenPage(@Nullable ObjIntConsumer<MouseEvent> onOpenPage)
	{
		this.onOpenPage = onOpenPage;
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	/** The first page in view. */
	int offset()
	{
		return scroll.offset() / LINE_HEIGHT;
	}

	/** The page row under a y coordinate, or -1. */
	int pageAt(int y)
	{
		return pageRows.at(y);
	}

	private boolean scrolls()
	{
		return scroll.scrolls(names.length * LINE_HEIGHT);
	}

	private int shown()
	{
		return scrolls() ? VISIBLE_ROWS : names.length;
	}

	private String countText(int page)
	{
		return obtained[page] >= 0 ? progressCountText(obtained[page], total[page])
			: progressPlaceholderText(total[page]);
	}

	@Override
	protected CardBody body()
	{
		return new CardBody().add(pageList());
	}

	/** The pages in view: names on the left, counts right-aligned, the rail beside them when the tab scrolls. */
	private CardBody.Part pageList()
	{
		return CardBody.part(c ->
		{
			int nameWidth = 0;
			int countWidth = 0;
			for (int i = 0; i < names.length; i++)
			{
				nameWidth = Math.max(nameWidth, c.fm.stringWidth(names[i]));
				countWidth = Math.max(countWidth, c.fm.stringWidth(countText(i)));
			}
			return nameWidth + COUNT_GAP + countWidth + (scrolls() ? CardBody.RAIL_GAP + CardBody.RAIL_WIDTH : 0);
		}, c -> shown() * LINE_HEIGHT, (c, y) ->
		{
			// Only the pages in view answer; the page under a still mouse is hovered as the list moves.
			int offset = scroll.follow((names.length - shown()) * LINE_HEIGHT) / LINE_HEIGHT;
			pageRows.clear();
			for (int i = 0; i < shown(); i++)
			{
				pageRows.span(y + i * LINE_HEIGHT, offset + i);
			}
			pageRows.rehover();
			int right = c.w - c.inset() - (scrolls() ? CardBody.RAIL_GAP + CardBody.RAIL_WIDTH : 0);
			for (int i = 0; i < shown(); i++)
			{
				int page = offset + i;
				int textY = y + i * LINE_HEIGHT + c.fm.getAscent();
				// With the highlighter on, a page's row wears its progression color whole, as the boss list's rows do.
				Color count = obtained[page] < 0 ? MUTED_GRAY : progress != null
					? ClogHelper.clogColor(obtained[page], total[page], progress) : completionColor(obtained[page], total[page]);
				c.g.setColor(pageRows.hovered() == page ? Color.WHITE
					: progress != null && obtained[page] >= 0 ? count : OSRS_ORANGE);
				c.g.drawString(names[page], c.inset(), textY);
				c.g.setColor(count);
				drawRightAligned(c.g, c.fm, countText(page), right, textY);
			}
			if (scrolls())
			{
				CardBody.rail(c, y, shown() * LINE_HEIGHT, VISIBLE_ROWS, names.length, offset, names.length - VISIBLE_ROWS);
			}
		});
	}
}
