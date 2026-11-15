package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.annotation.Nullable;
import lombok.*;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.StringUtils;

/**
 * Clog summary tooltip on the summary-bar clog cell.
 * The tier in the header; the log's total and each collection-log tab as the game's tiles, then the tier ladder, the
 * Rare shelf, recent items and sources.
 */
public class ClogSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 3;
	private static final int SEPARATOR_PAD = 2;
	private static final int RECENT_PAD = 6;
	// Recent fills the tiles' width: six items, their dates under them.
	static final int RECENT_SHOWN = 6;
	private static final int SOURCES_GAP = 3;
	// Hover sections: the shelves, the tier ladder and the sources. A reached tier reads green and an unreached one
	// red, as held and missing items do.
	private static final int RARE_SECTION = 0;
	private static final int RECENT_SECTION = 1;
	private static final int TIER_SECTION = 2;
	private static final int SOURCE_SECTION = 3;
	private static final String TITLE = "Collection Log";
	// The bars' empty track, as the game's Combat Achievements draw it.
	static final Color BAR_TRACK = new Color(40, 35, 28);
	// Each tab's tile shows an item of its own (clog-tab-sprites.tsv); the Total's is the tier's trophy.
	static final Map<String, Integer> TAB_SPRITES = new HashMap<>();
	static
	{
		CatalogTsv.rows(ClogSummaryTooltip.class, "clog-tab-sprites.tsv", 2)
			.forEach(row -> TAB_SPRITES.put(row[0], Integer.parseInt(row[1])));
	}
	private final Map<String, Image> tabSprites = new HashMap<>();
	private static final java.time.format.DateTimeFormatter SHORT_DATE =
		java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
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
	// The current tier's trophy on the Total's tile; none below bronze.
	private BufferedImage tierSprite;
	private String notice;
	private boolean firstTimeSetup;

	// The trophy shelf holds only obtained specials; recent unlocks carry their dates.
	// An empty shelf is null and paints nothing.
	private Shelf rare;
	private Shelf recent;

	/** One row of item sprites under its own subheader. */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Shelf
	{
		@Nullable
		final TooltipItemSprites sprites;
		final int[] ids;
		final String[] names;
		final int[] counts;
		final String[] dates;
	}

	private List<CardSources.Source> clogSources = Collections.emptyList();
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
		this.tierIcons = tierIcons;
		this.obtained = obtained;
		this.totalSlots = totalSlots;

		tier = ClogHelper.tierIndex(obtained, totalSlots);
		tierSprite = tier >= 0 ? itemSprite(ClogHelper.CLOG_TIER_ITEM_IDS[tier], itemManager) : null;
	}

	/** Progress per collection-log tab, in the game's tab order, each with its tile's item. */
	void setTabs(Map<String, int[]> tabs, @Nullable ItemManager itemManager)
	{
		this.tabs = tabs;
		tabSprites.clear();
		tabs.keySet().forEach(tab -> tabSprites.put(tab, itemSprite(TAB_SPRITES.getOrDefault(tab, -1), itemManager)));
	}

	/** Called with the tab's name when the player presses its row; without it the rows only read. */
	void setOnOpenTab(@Nullable BiConsumer<MouseEvent, String> onOpenTab)
	{
		this.onOpenTab = onOpenTab;
	}

	private boolean hasTotals()
	{
		return obtained >= 0 && totalSlots > 0;
	}

	/** The tier the log has reached, as the header names it; null below bronze. */
	@Nullable
	String tierName()
	{
		return hasTotals() && tier >= 0 ? StringUtils.capitalize(ClogHelper.CLOG_TIERS[tier]) : null;
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

	/** Every site that holds this player's log ({@link CardSources#of}), in the footer. */
	void setClogSources(ClogResult result, boolean onTemple, boolean onRuneProfile, boolean onKillclog)
	{
		showSources(CardSources.of(result, onTemple, onRuneProfile, onKillclog, ""));
	}

	void showSources(List<CardSources.Source> sources)
	{
		clogSources = sources;
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
			// Every shelved item is held, a log that sent no count included.
			counts[i] = Math.max(1, item.getCount());
			if (dated)
			{
				dates[i] = shortDate(item.getDate());
			}
		}
		return new Shelf(TooltipItemSprites.load(TooltipData.itemList(ids), null, itemManager, id -> 1, this),
			ids, names, counts, dates);
	}

	/** A stored UTC date becomes the player's own day, "Jul 4"; anything else is dropped. */
	static String shortDate(String date)
	{
		java.time.LocalDateTime day = ClogDates.here(date);
		return day == null ? null : SHORT_DATE.format(day);
	}

	@Override
	protected CardBody body()
	{
		CardBody body = new CardBody();
		if (firstTimeSetup)
		{
			return body.add(CardBody.text(SETUP_OPEN_LINE, NOTICE_COLOR))
				.add(CardBody.text(SETUP_SEARCH_LINE, NOTICE_COLOR))
				.add(CardBody.text(SETUP_CHAT_LINE, NOTICE_COLOR));
		}
		if (notice != null)
		{
			return body.add(CardBody.text(notice, NOTICE_COLOR));
		}

		// The tier's name at the header's right. The Total and each tab stand under the header as the game's tiles,
		// three across, the Total first; a tab's tile opens its pages. The tier ladder follows; any tier's, item's or
		// source's name shows in the band under it, and the shelves and footer scroll below.
		CardBody card = new CardBody().add(headerWord(tierName(),
			tierName() != null && tierIcons != null ? tierIcons.get(ClogHelper.CLOG_TIERS[tier]) : null,
			net.runelite.client.ui.FontManager.getRunescapeFont()));
		List<GameTile.Tile> tiles = new ArrayList<>();
		if (hasTotals())
		{
			tiles.add(new GameTile.Tile("Total", tierSprite, obtained, totalSlots, -1));
		}
		int key = 0;
		for (Map.Entry<String, int[]> tab : tabs.entrySet())
		{
			int[] count = tab.getValue();
			tiles.add(new GameTile.Tile(tab.getKey(), tabSprites.get(tab.getKey()), count[0], count[1],
				onOpenTab != null ? key : -1));
			key++;
		}
		if (!tiles.isEmpty())
		{
			card.add(GameTile.grid(tabRows, tiles)).add(CardBody.gap(GameTile.GAP));
		}
		if (hasTotals())
		{
			card.add(tierLadder());
		}
		card.add(hasTotals() || !tabs.isEmpty() ? CardBody.hoverBand() : CardBody.headerHoverBand());

		// The Rare shelf, present only when earned, then recent unlocks.
		shelf(body, RARE_SECTION, "Rare", rare, ClogHelper.RARES_PER_ROW);
		shelf(body, RECENT_SECTION, "Recent", recent, Integer.MAX_VALUE);

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
					c.g.setComposite(reached ? solid : KillClogColors.DIM);
					c.g.drawImage(icon, x, y, null);
				}
				c.hits.add(new TooltipItemHover.HitBox(TIER_SECTION, 0, labels[i],
					new Rectangle(x, y, ICON_SIZE, ICON_SIZE), reached));
				x += ICON_SIZE + ICON_GAP;
			}
			c.g.setComposite(solid);
		});
	}

	/**
	 * A shelf under its subheader, from the left as killclog.com's: sprites in rows of at most this many, each recent
	 * one over its date. Recent is one row; the Rare shelf wraps.
	 */
	private static void shelf(CardBody body, int section, String title, @Nullable Shelf shelf, int perRow)
	{
		if (shelf != null)
		{
			body.titled(title).add(CardBody.sprites(section, shelf.sprites, shelf.ids, shelf.names, shelf.counts,
				RECENT_PAD, perRow, shelf.dates));
		}
	}

	/** Who supplied the log: each source's icon under the last update, from the left, its name on hover (Dylan 11/14). */
	private CardBody.Part sources()
	{
		return CardBody.row(SOURCES_GAP + CardSources.ICON, c -> CardSources.width(clogSources.size()),
			(c, y) -> CardSources.draw(c, clogSources, c.inset(), y + SOURCES_GAP, SOURCE_SECTION));
	}
}
