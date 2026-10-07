package com.killclog;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;

/**
 * One Collection Log tab, opened from the Clog Summary where it was: the tab's progress, the way back,
 * then every page in the game's order with the player's count. A page opens its own popup. A tab
 * longer than {@link #VISIBLE_ROWS} pages scrolls with the mouse wheel, a thin rail showing where.
 */
public class ClogTabTooltip extends TitleTooltip
{
	static final int VISIBLE_ROWS = 24;
	private static final int WHEEL_ROWS = 3;
	private static final int COUNT_GAP = 12;
	private static final int RAIL_WIDTH = 3;
	private static final int RAIL_GAP = 4;
	private static final int SEPARATOR_PAD = 2;
	private static final String BACK_LABEL = "< Clog Summary";
	private static final Color RAIL_TRACK = SEPARATOR_COLOR;
	private static final Color RAIL_THUMB = OSRS_ORANGE;

	private String[] names = new String[0];
	private int[] obtained = new int[0];
	private int[] total = new int[0];
	private int offset;
	private double wheel;
	private int listTop;
	// The other player's side of a comparison, kept on the same pages.
	@Nullable
	private ClogTabTooltip partner;
	@Nullable
	private Consumer<MouseEvent> onBack;
	@Nullable
	private ObjIntConsumer<MouseEvent> onOpenPage;
	private final CardBody.ClickRows backRow = new CardBody.ClickRows(this, (e, row) ->
	{
		if (onBack != null)
		{
			onBack.accept(e);
			e.consume();
		}
	});
	private final CardBody.ClickRows pageRows = new CardBody.ClickRows(this, (e, page) ->
	{
		if (onOpenPage != null)
		{
			onOpenPage.accept(e, page);
			e.consume();
		}
	});

	public ClogTabTooltip()
	{
		addMouseWheelListener(this::scroll);
	}

	/** The tab and the player's progress through it; null progress shows the tab's size alone. */
	void setTab(String tab, @Nullable int[] progress, int tabTotal)
	{
		setTitle(tab);
		if (progress != null)
		{
			setObtained(progress[0], progress[1]);
		}
		else
		{
			setObtainedPlaceholder(tabTotal);
		}
	}

	/** Each page's name and count in the game's order; a negative count is unknown and shows its size. */
	void setPages(String[] names, int[] obtained, int[] total)
	{
		this.names = names;
		this.obtained = obtained;
		this.total = total;
		offset = 0;
	}

	void setOnBack(@Nullable Consumer<MouseEvent> onBack)
	{
		this.onBack = onBack;
	}

	/** Called with the page's index when the player presses its row. */
	void setOnOpenPage(@Nullable ObjIntConsumer<MouseEvent> onOpenPage)
	{
		this.onOpenPage = onOpenPage;
	}

	/** Keeps two players' sides on the same pages as either one scrolls. */
	void scrollWith(ClogTabTooltip other)
	{
		partner = other;
		other.partner = this;
	}

	/** The first page in view. */
	int offset()
	{
		return offset;
	}

	/** The page row under a y coordinate, or -1. */
	int pageAt(int y)
	{
		return pageRows.at(y);
	}

	private boolean scrolls()
	{
		return names.length > VISIBLE_ROWS;
	}

	private int shown()
	{
		return Math.min(names.length, VISIBLE_ROWS);
	}

	/** Moves the list by the wheel's rows, keeping the hover on whatever page now sits under the mouse. */
	void scroll(MouseWheelEvent e)
	{
		if (!scrolls())
		{
			return;
		}
		wheel += e.getPreciseWheelRotation() * WHEEL_ROWS;
		int step = (int) wheel;
		wheel -= step;
		int next = Math.max(0, Math.min(names.length - VISIBLE_ROWS, offset + step));
		if (next != offset)
		{
			offset = next;
			layRows();
			pageRows.moved(e.getY());
			repaint();
			if (partner != null)
			{
				partner.offset = next;
				partner.layRows();
				partner.repaint();
			}
		}
		e.consume();
	}

	private void layRows()
	{
		pageRows.clear();
		for (int i = 0; i < shown(); i++)
		{
			pageRows.span(listTop + i * LINE_HEIGHT, offset + i);
		}
	}

	private String countText(int page)
	{
		return obtained[page] >= 0 ? progressCountText(obtained[page], total[page])
			: progressPlaceholderText(total[page]);
	}

	@Override
	protected CardBody body()
	{
		CardBody body = new CardBody();
		if (onBack != null)
		{
			body.add(CardBody.clickRow(backRow, 0, c -> c.fm.stringWidth(BACK_LABEL), (c, y, hovered) ->
			{
				c.g.setColor(hovered ? Color.WHITE : OSRS_ORANGE);
				c.g.drawString(BACK_LABEL, c.inset(), y + c.fm.getAscent());
			})).add(CardBody.separator(SEPARATOR_PAD));
		}
		return body.add(pageList());
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
			return nameWidth + COUNT_GAP + countWidth + (scrolls() ? RAIL_GAP + RAIL_WIDTH : 0);
		}, c -> shown() * LINE_HEIGHT, (c, y) ->
		{
			listTop = y;
			layRows();
			int right = c.w - c.inset() - (scrolls() ? RAIL_GAP + RAIL_WIDTH : 0);
			for (int i = 0; i < shown(); i++)
			{
				int page = offset + i;
				int textY = y + i * LINE_HEIGHT + c.fm.getAscent();
				c.g.setColor(pageRows.hovered() == page ? Color.WHITE : OSRS_ORANGE);
				c.g.drawString(names[page], c.inset(), textY);
				c.g.setColor(obtained[page] >= 0 ? completionColor(obtained[page], total[page]) : MUTED_GRAY);
				drawRightAligned(c.g, c.fm, countText(page), right, textY);
			}
			if (scrolls())
			{
				int x = c.w - c.inset() - RAIL_WIDTH;
				int track = shown() * LINE_HEIGHT;
				int thumb = Math.max(LINE_HEIGHT, track * VISIBLE_ROWS / names.length);
				c.g.setColor(RAIL_TRACK);
				c.g.fillRect(x, y, RAIL_WIDTH, track);
				c.g.setColor(RAIL_THUMB);
				c.g.fillRect(x, y + (track - thumb) * offset / (names.length - VISIBLE_ROWS), RAIL_WIDTH, thumb);
			}
		});
	}
}
