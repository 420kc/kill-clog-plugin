package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
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
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Clog summary tooltip on the summary-bar clog cell.
 * The log's total with its completion, the tier ladder, progress per
 * collection-log tab, then the trophy shelf, recent items and sources.
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
	// Hover sections: 0 highlights, 1 recent, then the tier ladder and the sources.
	// An unreached tier sits one past TIER_SECTION so its readout can turn red.
	private static final int TIER_SECTION = 2;
	private static final int SOURCE_SECTION = 4;
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
	private Shelf special;
	private Shelf recent;

	/** One row of item sprites under its own subheader. */
	static final class Shelf
	{
		@Nullable
		final TooltipItemSprites sprites;
		final int[] ids;
		final String[] names;
		final String[] dates;

		Shelf(@Nullable TooltipItemSprites sprites, int[] ids, String[] names, String[] dates)
		{
			this.sprites = sprites;
			this.ids = ids;
			this.names = names;
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
		setTitle("Clog Summary");
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
		setTitle("Clog Summary");
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

	public void setSpecialItems(List<ClogResult.ClogItem> specialItems, ClogResult clog,
		ItemManager itemManager)
	{
		special = shelf(specialItems, clog, itemManager, false);
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
		String[] dates = dated ? new String[count] : null;
		for (int i = 0; i < count; i++)
		{
			ClogResult.ClogItem item = items.get(i);
			ids[i] = item.getId();
			names[i] = clog != null ? clog.getItemName(item.getId()) : null;
			if (dated)
			{
				dates[i] = shortDate(item.getDate());
			}
		}
		return new Shelf(TooltipItemSprites.load(TooltipData.itemList(ids), null, itemManager, id -> 1, this),
			ids, names, dates);
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

		// Completion, its bar, the tier ladder and the ladder's readout row; the current tier sits large
		// in the header's corner.
		if (hasTotals())
		{
			body.add(CardBody.row(0, c -> 0, (c, y) ->
			{
				if (tierSprite != null)
				{
					c.g.drawImage(tierSprite, c.w - c.inset() - tierSprite.getWidth(),
						c.inset() + (getHeaderHeight() - tierSprite.getHeight()) / 2, null);
				}
			}))
				.add(CardBody.line("Completion: ", completionText()))
				.add(bar(obtained, totalSlots))
				.add(tierLadder());
		}

		if (!tabs.isEmpty())
		{
			body.add(CardBody.separator(SEPARATOR_PAD)).add(CardBody.subheader("Collection Log"));
			int row = 0;
			for (Map.Entry<String, int[]> tab : tabs.entrySet())
			{
				int[] count = tab.getValue();
				String label = tab.getKey() + ": ";
				String value = progressCountText(count[0], count[1]);
				Color color = completionColor(count[0], count[1]);
				// A tab's row opens its pages, answering hover in white like the Clue Summary's rares.
				body.add(onOpenTab == null ? CardBody.line(label, value, color)
					: CardBody.clickRow(tabRows, row, c -> c.fm.stringWidth(label + value), (c, y, hovered) ->
						drawLabelValue(c.g, c.fm, c.inset(), y + c.fm.getAscent(), label, value,
							hovered ? Color.WHITE : OSRS_ORANGE, color)))
					.add(bar(count[0], count[1]));
				row++;
			}
		}

		// The trophy shelf, present only when earned, then recent unlocks.
		shelf(body, 0, "Highlights", special);
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
		return body;
	}

	/** A thin rail under a count: the share at a glance, the number still primary. */
	private static CardBody.Part bar(int obtained, int total)
	{
		return CardBody.row(BAR_HEIGHT + BAR_GAP, c -> 0, (c, y) ->
		{
			int width = c.w - 2 * c.inset();
			c.g.setColor(BAR_TRACK);
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
		return CardBody.part(c ->
		{
			int width = legendWidth();
			for (String label : labels)
			{
				width = Math.max(width, c.fm.stringWidth(label));
			}
			return width;
		}, c -> ICON_SIZE + hoverRowHeight(c.fm), (c, y) ->
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
				c.hits.add(new TooltipItemHover.HitBox(reached ? TIER_SECTION : TIER_SECTION + 1, 0, labels[i],
					new Rectangle(x, y, ICON_SIZE, ICON_SIZE), false));
				x += ICON_SIZE + ICON_GAP;
			}
			c.g.setComposite(solid);
			int section = itemHover.hoveredSection();
			if (section >= TIER_SECTION && section < SOURCE_SECTION)
			{
				paintHeaderHoverLine(c.g, c.fm, c.w, y + ICON_SIZE + c.fm.getAscent());
			}
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
		for (int i = 0; i < count; i++)
		{
			named.put(shelf.ids[i], shelf.names[i]);
		}
		List<Integer> ids = TooltipData.itemList(shelf.ids);
		body.add(CardBody.separator(SEPARATOR_PAD)).add(CardBody.subheader(title)).add(CardBody.part(
			c -> count * shelf.cellWidth(c.fm) + (count - 1) * RECENT_PAD,
			c -> size + (shelf.dated() ? DATE_GAP + c.fm.getHeight() : 0) + hoverRowHeight(c.fm), (c, y) ->
			{
				// Every shelved item is held: the grids' painter draws them, each centered over its date.
				int cellWidth = shelf.cellWidth(c.fm);
				int startX = c.inset() + (c.w - 2 * c.inset() - (count * cellWidth + (count - 1) * RECENT_PAD)) / 2;
				c.hits.addAll(TooltipItemSprites.paintGrid(c.g, shelf.sprites, named, section, ids, new HashSet<>(ids),
					Collections.emptyMap(), startX + (cellWidth - size) / 2, y, count, size, cellWidth + RECENT_PAD));
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
				paintSectionHoverLine(c.g, c.fm, c.w, y + size + (shelf.dated() ? DATE_GAP + c.fm.getHeight() : 0),
					section);
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
		}, c -> c.fm.getHeight() + SOURCE_LABEL_GAP + SOURCE_ICON_SIZE + hoverRowHeight(c.fm), (c, y) ->
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
				c.hits.add(new TooltipItemHover.HitBox(SOURCE_SECTION + i, 0, source.name,
					new Rectangle(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2, SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2), false));
			}
			if (itemHover.hoveredSection() >= SOURCE_SECTION)
			{
				paintHeaderHoverLine(c.g, c.fm, c.w, y + SOURCE_ICON_SIZE + c.fm.getAscent());
			}
		});
	}

	@Override
	protected Color getHeaderHoverLineColor()
	{
		// Obtained items, verified providers and reached tiers share the success color.
		return itemHover.hoveredSection() == TIER_SECTION + 1 ? CLOG_RED : CLOG_GREEN;
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
