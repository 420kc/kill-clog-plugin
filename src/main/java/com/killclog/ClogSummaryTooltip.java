package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Clog summary tooltip on the summary-bar clog cell.
 * The log's total with its completion, the tier ladder, progress per
 * collection-log tab, then the Rare shelf, recent items and sources.
 */
public class ClogSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 3;
	private static final int SEPARATOR_PAD = 2;
	private static final int RECENT_PAD = 6;
	private static final int DATE_GAP = 1;
	private static final int SOURCE_ICON_SIZE = 13;
	private static final int SOURCE_ICON_GAP = 5;
	private static final int SOURCE_HIT_PAD = 2;
	private static final int SOURCE_LABEL_GAP = 3;
	// Hover sections: 0 rare, 1 recent, then the tier ladder and the sources. A reached tier reads green and
	// an unreached one red, as held and missing items do.
	private static final int TIER_SECTION = 2;
	private static final String TITLE = "Collection Log";
	private static final int SOURCE_SECTION = 3;
	// The tab rows read a size up from the card's text, their counts at the right, each tab apart from the next.
	private static final Font TAB_FONT = FontManager.getRunescapeFont();
	private static final int TAB_GAP = 4;
	private static final int TAB_VALUE_GAP = 8;
	private static final int BAR_HEIGHT = 3;
	private static final int BAR_GAP = 3;
	static final Color BAR_TRACK = new Color(40, 35, 28);
	private static final String SOURCE_LABEL = "Sources";
	private static final String[] MONTHS = {
		"Jan", "Feb", "Mar", "Apr", "May", "Jun",
		"Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
	};
	private static final String TEMPLE_SOURCE = "TempleOSRS";
	private static final String RUNEPROFILE_SOURCE = "RuneProfile";
	private static final String KILLCLOG_SOURCE = "Kill Clog";
	private static final String LOCAL_SOURCE = "Local Collection Log";
	private static final String SETUP_OPEN_LINE = "1. Open your Collection Log.";
	private static final String SETUP_SEARCH_LINE = "2. Wait for setup to finish.";
	private static final String SETUP_CHAT_LINE = "Chat will confirm when setup is complete.";

	private int obtained = -1;
	private int totalSlots;
	private int tier = -1;
	// Collection-log tabs in game order: name, then {obtained, total}.
	private Map<String, int[]> tabs = Collections.emptyMap();
	private String syncDate;
	private boolean syncStale;

	private Map<String, BufferedImage> tierIcons;
	// The current tier, large, in the header's corner. Empty below bronze.
	private BufferedImage tierSprite;
	private String notice;
	private boolean firstTimeSetup;

	// The trophy shelf holds only obtained specials; recent unlocks carry their dates.
	// An empty shelf is null and paints nothing.
	private Shelf rare;
	private Shelf recent;

	/** One row of item sprites under its own subheader. */
	static final class Shelf
	{
		@Nullable
		final TooltipItemSprites sprites;
		final int[] ids;
		final String[] names;
		final int[] counts;
		final String[] dates;

		Shelf(@Nullable TooltipItemSprites sprites, int[] ids, String[] names, int[] counts, String[] dates)
		{
			this.sprites = sprites;
			this.ids = ids;
			this.names = names;
			this.counts = counts;
			this.dates = dates;
		}

		boolean dated()
		{
			return dates != null && java.util.Arrays.stream(dates).anyMatch(java.util.Objects::nonNull);
		}

		/** Cells widen past the sprite when a date caption needs the room. */
		int cellWidth(FontMetrics fm)
		{
			int w = CardBody.GRID_SPRITE;
			for (String date : dates != null ? dates : new String[0])
			{
				if (date != null)
				{
					w = Math.max(w, fm.stringWidth(date));
				}
			}
			return w;
		}
	}

	private final List<ClogSource> clogSources = new ArrayList<>(3);
	@Nullable
	private BiConsumer<MouseEvent, String> onOpenTab;
	// Everything under the anchored band scrolls once the card grows past a long tab's height, three lines a notch.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, 3 * LINE_HEIGHT);
	private final CardBody.ClickRows tabRows = new CardBody.ClickRows(this, (e, row) ->
	{
		if (onOpenTab != null)
		{
			onOpenTab.accept(e, new ArrayList<>(tabs.keySet()).get(row));
			e.consume();
		}
	});

	public void setTierData(int obtained, int totalSlots, Map<String, BufferedImage> tierIcons,
		ItemManager itemManager)
	{
		setTitle(TITLE);
		setObtained(obtained, totalSlots);
		this.tierIcons = tierIcons;
		this.obtained = obtained;
		this.totalSlots = totalSlots;

		tier = ClogHelper.tierIndex(obtained, totalSlots);
		if (tier >= 0 && itemManager != null)
		{
			// The item image at its own size, which stays crisp; it fills in once loaded.
			AsyncBufferedImage sprite = itemManager.getImage(PanelData.CLOG_TIER_ITEM_IDS[tier]);
			if (sprite != null)
			{
				sprite.onLoaded(this::repaint);
			}
			tierSprite = sprite;
		}
	}

	/** Progress per collection-log tab, in the game's tab order. */
	void setTabs(Map<String, int[]> tabs)
	{
		this.tabs = tabs;
	}

	/** Called with the tab's name when the player presses its row; without it the rows only read. */
	void setOnOpenTab(@Nullable BiConsumer<MouseEvent, String> onOpenTab)
	{
		this.onOpenTab = onOpenTab;
	}

	/** The tab row under a y coordinate, or -1. */
	int tabAt(int y)
	{
		return tabRows.at(y);
	}

	@Override
	protected String obtainedLabel()
	{
		return "Total: ";
	}

	private boolean hasTotals()
	{
		return obtained >= 0 && totalSlots > 0;
	}

	String completionText()
	{
		return String.format(Locale.US, "%.1f%%", Math.min(obtained, totalSlots) * 100.0 / totalSlots);
	}

	private static int legendWidth()
	{
		int tiers = ClogHelper.CLOG_TIERS.length;
		return tiers * ICON_SIZE + (tiers - 1) * ICON_GAP;
	}

	public void setSyncData(String dateText, boolean stale)
	{
		this.syncDate = dateText;
		this.syncStale = stale;
	}

	/**
	 * Every provider this player is synced to. RuneProfile counts whenever it
	 * holds a profile for them: its collection-log request is skipped for the
	 * local player and can miss the lookup window for anyone else.
	 */
	void setClogSources(ClogResult result, boolean hasRuneProfile)
	{
		setClogSources(result.isFromTemple(), result.isFromRuneProfile() || hasRuneProfile,
			result.isFromKillclog(), result.isFromLocal());
	}

	/** Record contributing sources, including Kill Clog's local game capture. */
	void setClogSources(boolean temple, boolean runeProfile, boolean killclog, boolean local)
	{
		clogSources.clear();
		if (killclog || local)
		{
			clogSources.add(new ClogSource(
				local ? LOCAL_SOURCE : KILLCLOG_SOURCE,
				KillClogIcons.killClogSourceIcon(SOURCE_ICON_SIZE)));
		}
		if (temple)
		{
			clogSources.add(new ClogSource(
				TEMPLE_SOURCE, KillClogIcons.templeSourceIcon(SOURCE_ICON_SIZE)));
		}
		if (runeProfile)
		{
			clogSources.add(new ClogSource(
				RUNEPROFILE_SOURCE, KillClogIcons.runeProfileSourceIcon(SOURCE_ICON_SIZE)));
		}
		itemHover.clear();
		itemHover.setHitBoxes(Collections.emptyList());
		revalidate();
		repaint();
	}

	public void setNotice(String notice)
	{
		firstTimeSetup = false;
		this.notice = notice;
		setTitle(TITLE);
	}

	public void setFirstTimeSetup()
	{
		firstTimeSetup = true;
		notice = null;
		setTitle("First Time Setup");
	}

	public void setRecentItems(List<ClogResult.ClogItem> recentItems, ClogResult clog,
		ItemManager itemManager)
	{
		recent = shelf(recentItems, clog, itemManager, true);
	}

	public void setRareItems(List<ClogResult.ClogItem> rareItems, ClogResult clog,
		ItemManager itemManager)
	{
		rare = shelf(rareItems, clog, itemManager, false);
	}

	private Shelf shelf(List<ClogResult.ClogItem> items, ClogResult clog, ItemManager itemManager, boolean dated)
	{
		int count = items.size();
		if (count == 0)
		{
			return null;
		}
		int[] ids = new int[count];
		String[] names = new String[count];
		int[] counts = new int[count];
		String[] dates = dated ? new String[count] : null;
		for (int i = 0; i < count; i++)
		{
			ClogResult.ClogItem item = items.get(i);
			ids[i] = item.getId();
			names[i] = clog != null ? clog.getItemName(item.getId()) : null;
			counts[i] = item.getCount();
			if (dated)
			{
				dates[i] = shortDate(item.getDate());
			}
		}
		return new Shelf(TooltipItemSprites.load(TooltipData.itemList(ids), null, itemManager, id -> 1, this),
			ids, names, counts, dates);
	}

	/** "2026-07-04 ..." from the provider becomes "Jul 4"; anything else is dropped. */
	static String shortDate(String date)
	{
		if (date == null || date.length() < 10)
		{
			return null;
		}
		try
		{
			int month = Integer.parseInt(date.substring(5, 7));
			int day = Integer.parseInt(date.substring(8, 10));
			if (month < 1 || month > 12 || day < 1 || day > 31)
			{
				return null;
			}
			return MONTHS[month - 1] + " " + day;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	List<String> sourceNames()
	{
		List<String> names = new ArrayList<>(clogSources.size());
		for (ClogSource source : clogSources)
		{
			names.add(source.name);
		}
		return names;
	}

	static int sourceRowWidth(int count)
	{
		return count > 0 ? count * SOURCE_ICON_SIZE + (count - 1) * SOURCE_ICON_GAP : 0;
	}

	static int sourceRowStartX(int tooltipWidth, int count)
	{
		return (tooltipWidth - sourceRowWidth(count)) / 2;
	}

	@Override
	protected CardBody body()
	{
		CardBody body = new CardBody();
		if (firstTimeSetup)
		{
			return body.add(CardBody.text(SETUP_OPEN_LINE, NOTICE_COLOR, true))
				.add(CardBody.text(SETUP_SEARCH_LINE, NOTICE_COLOR, true))
				.add(CardBody.text(SETUP_CHAT_LINE, NOTICE_COLOR, true));
		}
		if (notice != null)
		{
			return body.add(CardBody.text(notice, NOTICE_COLOR, true));
		}

		// The current tier sits large in the header's corner. Completion, the tier ladder and the tabs stand under the
		// header; any tier's, item's or source's name shows in the band under the progress bars, and the shelves and
		// footer scroll below it.
		CardBody card = new CardBody().add(headerCorner(hasTotals(), hasTotals() ? tierSprite : null, null));
		if (hasTotals())
		{
			// The completion heads the tab rows, read the same way: a row that opens nothing.
			card.add(tabRow(-1, "Completion", completionText(), Color.WHITE))
				.add(bar(obtained, totalSlots))
				.add(tierLadder());
		}

		if (!tabs.isEmpty())
		{
			// The tabs need no heading of their own: the card is the Collection Log. A divider parts them from
			// the completion above it.
			if (hasTotals())
			{
				card.add(CardBody.separator(SEPARATOR_PAD));
			}
			int row = 0;
			for (Map.Entry<String, int[]> tab : tabs.entrySet())
			{
				int[] count = tab.getValue();
				if (row > 0)
				{
					card.add(CardBody.gap(TAB_GAP));
				}
				card.add(tabRow(row, tab.getKey(), count[0] < 0 ? progressPlaceholderText(count[1])
					: progressCountText(count[0], count[1]), count[0] < 0 ? MUTED_GRAY : completionColor(count[0], count[1])))
					.add(bar(count[0], count[1]));
				row++;
			}
		}
		card.add(hasTotals() || !tabs.isEmpty() ? CardBody.hoverBand() : CardBody.headerHoverBand());

		// The Rare shelf, present only when earned, then recent unlocks.
		shelf(body, 0, "Rare", rare);
		shelf(body, 1, "Recent", recent);

		// The footer: when it last changed and who supplied it, under one rule.
		if (syncDate != null || !clogSources.isEmpty())
		{
			body.add(CardBody.separator(SEPARATOR_PAD));
		}
		if (syncDate != null)
		{
			body.add(CardBody.line("Last update: ", syncDate, syncStale ? STALE_RED : CLOG_GREEN));
		}
		if (!clogSources.isEmpty())
		{
			body.add(sources());
		}
		return card.add(CardBody.scroll(scroll, body.asPart()));
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	/**
	 * A tab's row, a size up from the card's text, its count at the right like the PvP rows': it opens the tab's
	 * pages, answering hover in white like the Clue Summary's rares.
	 */
	private CardBody.Part tabRow(int row, String label, String value, Color color)
	{
		FontMetrics fm = getFontMetrics(TAB_FONT);
		ToIntFunction<CardBody.Ctx> width = c -> fm.stringWidth(label) + TAB_VALUE_GAP + fm.stringWidth(value);
		CardBody.RowPainter painter = (c, y, hovered) ->
		{
			Font font = c.g.getFont();
			c.g.setFont(TAB_FONT);
			int baseline = y + fm.getAscent();
			c.g.setColor(hovered ? Color.WHITE : OSRS_ORANGE);
			c.g.drawString(label, c.inset(), baseline);
			c.g.setColor(color);
			c.g.drawString(value, c.w - c.inset() - fm.stringWidth(value), baseline);
			c.g.setFont(font);
		};
		return row < 0 || onOpenTab == null ? CardBody.row(fm.getHeight(), width, (c, y) -> painter.paint(c, y, false))
			: CardBody.clickRow(tabRows, row, fm.getHeight(), width, painter);
	}

	/** A thin rail under a count: the share at a glance, the number still primary. */
	private static CardBody.Part bar(int obtained, int total)
	{
		return CardBody.row(BAR_HEIGHT + BAR_GAP, c -> 0, (c, y) ->
		{
			int width = c.w - 2 * c.inset();
			c.g.setColor(NativeTooltip.railTrack(BAR_TRACK));
			c.g.fillRect(c.inset(), y, width, BAR_HEIGHT);
			if (obtained > 0 && total > 0)
			{
				c.g.setColor(completionColor(obtained, total));
				c.g.fillRect(c.inset(), y, Math.max(1, width * Math.min(obtained, total) / total), BAR_HEIGHT);
			}
		});
	}

	/** Every tier's icon, dimmed until reached; hovering one reads its range below. */
	private CardBody.Part tierLadder()
	{
		String[] labels = ClogHelper.tierLabels(obtained, totalSlots);
		return CardBody.row(ICON_SIZE, c -> legendWidth(), (c, y) ->
		{
			int x = (c.w - legendWidth()) / 2;
			Composite solid = c.g.getComposite();
			for (int i = 0; i < labels.length; i++)
			{
				boolean reached = i <= tier;
				BufferedImage icon = tierIcons != null ? tierIcons.get(ClogHelper.CLOG_TIERS[i]) : null;
				if (icon != null)
				{
					c.g.setComposite(reached ? solid : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					c.g.drawImage(icon, x, y, null);
				}
				c.hits.add(new TooltipItemHover.HitBox(TIER_SECTION, 0, labels[i],
					new Rectangle(x, y, ICON_SIZE, ICON_SIZE), reached));
				x += ICON_SIZE + ICON_GAP;
			}
			c.g.setComposite(solid);
		});
	}

	/** A shelf under its subheader: sprites centered in a row, dates below, then its hover line. */
	private void shelf(CardBody body, int section, String title, Shelf shelf)
	{
		if (shelf == null)
		{
			return;
		}
		int count = shelf.ids.length;
		int size = CardBody.GRID_SPRITE;
		Map<Integer, String> named = new HashMap<>();
		Map<Integer, Integer> held = new HashMap<>();
		for (int i = 0; i < count; i++)
		{
			named.put(shelf.ids[i], shelf.names[i]);
			held.put(shelf.ids[i], shelf.counts[i]);
		}
		List<Integer> ids = TooltipData.itemList(shelf.ids);
		body.titled(title).add(CardBody.part(
			c -> count * shelf.cellWidth(c.fm) + (count - 1) * RECENT_PAD,
			c -> size + (shelf.dated() ? DATE_GAP + c.fm.getHeight() : 0), (c, y) ->
			{
				// Every shelved item is held: the grids' painter draws them, each centered over its date.
				int cellWidth = shelf.cellWidth(c.fm);
				// Shelves start at the left, as killclog.com's do.
				int startX = c.inset();
				c.hits.addAll(TooltipItemSprites.paintGrid(c.g, shelf.sprites, named, section, ids, new HashSet<>(ids),
					held, startX + (cellWidth - size) / 2, y, count, size, cellWidth + RECENT_PAD));
				for (int i = 0; i < count; i++)
				{
					String date = shelf.dates != null ? shelf.dates[i] : null;
					if (date != null)
					{
						int cellX = startX + i * (cellWidth + RECENT_PAD);
						c.g.setColor(MUTED_GRAY);
						c.g.drawString(date, cellX + (cellWidth - c.fm.stringWidth(date)) / 2,
							y + size + DATE_GAP + c.fm.getAscent());
					}
				}
			}));
	}

	/** Who supplied the log: each source's icon, its name on hover. */
	private CardBody.Part sources()
	{
		return CardBody.part(c ->
		{
			int width = Math.max(c.fm.stringWidth(SOURCE_LABEL), sourceRowWidth(clogSources.size()));
			for (ClogSource source : clogSources)
			{
				width = Math.max(width, c.fm.stringWidth(source.name));
			}
			return width;
		}, c -> c.fm.getHeight() + SOURCE_LABEL_GAP + SOURCE_ICON_SIZE, (c, y) ->
		{
			c.g.setColor(MUTED_GRAY);
			c.g.drawString(SOURCE_LABEL, (c.w - c.fm.stringWidth(SOURCE_LABEL)) / 2, y + c.fm.getAscent());
			y += c.fm.getHeight() + SOURCE_LABEL_GAP;
			int sourceX = sourceRowStartX(c.w, clogSources.size());
			String hoveredName = itemHover.hoveredItemName();
			for (int i = 0; i < clogSources.size(); i++)
			{
				ClogSource source = clogSources.get(i);
				int iconX = sourceX + i * (SOURCE_ICON_SIZE + SOURCE_ICON_GAP);
				if (source.icon != null)
				{
					c.g.drawImage(source.icon, iconX, y, null);
				}
				if (source.name.equals(hoveredName))
				{
					c.g.setColor(CLOG_GREEN);
					c.g.drawRect(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2 - 1, SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2 - 1);
				}
				c.hits.add(new TooltipItemHover.HitBox(SOURCE_SECTION, 0, source.name,
					new Rectangle(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2, SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2), true));
			}
		});
	}

	private static final class ClogSource
	{
		private final String name;
		private final BufferedImage icon;

		private ClogSource(String name, BufferedImage icon)
		{
			this.name = name;
			this.icon = icon;
		}
	}
}
