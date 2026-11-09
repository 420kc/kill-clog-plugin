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
	private static final int DATE_GAP = 1;
	private static final int SOURCE_ICON_SIZE = 13;
	private static final int SOURCE_ICON_GAP = 5;
	private static final int SOURCE_HIT_PAD = 2;
	private static final int SOURCE_LABEL_GAP = 3;
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
	// Whose log the sources hold, for their pages.
	@Nullable
	private String sourcesPlayer;
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

	/**
	 * Every site that holds this player's log, whichever one's log the card shows (Dylan, 2026-10-12): one this lookup
	 * heard from, or one known to hold them by RuneProfile's profile, Temple's last answer or Kill Clog's sync list, since
	 * a request can be skipped (your own log) or time out. Your own log read from the game is Kill Clog's when you sync.
	 */
	void setClogSources(ClogResult result, boolean onTemple, boolean onRuneProfile, boolean onKillclog)
	{
		boolean killclog = result.isFromKillclog() || onKillclog;
		sourcesPlayer = result.getPlayerName();
		setClogSources(result.isFromTemple() || onTemple, result.isFromRuneProfile() || onRuneProfile,
			killclog, result.isFromLocal() && !killclog);
	}

	/** Record contributing sources, including Kill Clog's local game capture. */
	void setClogSources(boolean temple, boolean runeProfile, boolean killclog, boolean local)
	{
		clogSources.clear();
		if (killclog || local)
		{
			clogSources.add(new ClogSource(
				local ? LOCAL_SOURCE : KILLCLOG_SOURCE,
				KillClogIcons.killClogSourceIcon(SOURCE_ICON_SIZE), local ? null : link(TooltipItemLink::killclogUrl)));
		}
		if (temple)
		{
			clogSources.add(new ClogSource(
				TEMPLE_SOURCE, KillClogIcons.templeSourceIcon(SOURCE_ICON_SIZE), link(TooltipItemLink::templeUrl)));
		}
		if (runeProfile)
		{
			clogSources.add(new ClogSource(
				RUNEPROFILE_SOURCE, KillClogIcons.runeProfileSourceIcon(SOURCE_ICON_SIZE), link(TooltipItemLink::runeProfileUrl)));
		}
		itemHover.clear();
		itemHover.setHitBoxes(Collections.emptyList());
		revalidate();
		repaint();
	}

	@Nullable
	private String link(java.util.function.UnaryOperator<String> page)
	{
		return sourcesPlayer != null && !sourcesPlayer.isEmpty() ? page.apply(sourcesPlayer) : null;
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

	/** A stored UTC date becomes the player's own day, "Jul 4"; anything else is dropped. */
	static String shortDate(String date)
	{
		java.time.LocalDateTime day = ClogDates.here(date);
		return day == null ? null : MONTHS[day.getMonthValue() - 1] + " " + day.getDayOfMonth();
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

		// The tier's name at the header's right. The Total and each tab stand under the header as the game's tiles,
		// three across, the Total first; a tab's tile opens its pages. The tier ladder follows; any tier's, item's or
		// source's name shows in the band under it, and the shelves and footer scroll below.
		CardBody card = new CardBody().add(headerWord(tierName()));
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

	/**
	 * A shelf under its subheader: sprites from the left in rows of at most this many, dates below, then its hover
	 * line. Recent is one row; the Rare shelf wraps.
	 */
	private void shelf(CardBody body, int section, String title, Shelf shelf, int perRow)
	{
		if (shelf == null)
		{
			return;
		}
		int count = shelf.ids.length;
		int cols = Math.min(count, perRow);
		int rows = (count + cols - 1) / cols;
		int size = CardBody.GRID_SPRITE;
		Map<Integer, String> named = CardBody.byId(shelf.ids, i -> shelf.names[i]);
		Map<Integer, Integer> held = CardBody.byId(shelf.ids, i -> shelf.counts[i]);
		List<Integer> ids = TooltipData.itemList(shelf.ids);
		body.titled(title).add(CardBody.part(
			c -> cols * shelf.cellWidth(c.fm) + (cols - 1) * RECENT_PAD,
			c -> rows * size + (rows - 1) * RECENT_PAD + (shelf.dated() ? DATE_GAP + c.fm.getHeight() : 0), (c, y) ->
			{
				// Every shelved item is held: the grids' painter draws them, each centered over its date.
				int cellWidth = shelf.cellWidth(c.fm);
				// Shelves start at the left, as killclog.com's do.
				int startX = c.inset();
				c.hits.addAll(TooltipItemSprites.paintGrid(c.g, shelf.sprites, named, section, ids, new HashSet<>(ids),
					held, startX + (cellWidth - size) / 2, y, cols, size, cellWidth + RECENT_PAD, null));
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
			for (int i = 0; i < clogSources.size(); i++)
			{
				ClogSource source = clogSources.get(i);
				int iconX = sourceX + i * (SOURCE_ICON_SIZE + SOURCE_ICON_GAP);
				if (source.icon != null)
				{
					c.g.drawImage(source.icon, iconX, y, null);
				}
				c.hits.add(new TooltipItemHover.HitBox(SOURCE_SECTION, 0, source.name,
					new Rectangle(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2, SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2), true, source.url));
			}
		});
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class ClogSource
	{
		private final String name;
		private final BufferedImage icon;
		@Nullable
		private final String url;
	}
}
