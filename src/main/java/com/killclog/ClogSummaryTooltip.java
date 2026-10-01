package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
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
	private static final int RECENT_SIZE = 24;
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
	private static final Color BAR_TRACK = new Color(40, 35, 28);
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
		final BufferedImage[] sprites;
		final int[] ids;
		final String[] names;
		final String[] dates;

		Shelf(BufferedImage[] sprites, int[] ids, String[] names, String[] dates)
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
			int w = RECENT_SIZE;
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
		Shelf shelf = new Shelf(new BufferedImage[count], new int[count], new String[count],
			dated ? new String[count] : null);
		for (int i = 0; i < count; i++)
		{
			ClogResult.ClogItem item = items.get(i);
			shelf.ids[i] = item.getId();
			shelf.names[i] = clog != null ? clog.getItemName(item.getId()) : null;
			if (dated)
			{
				shelf.dates[i] = shortDate(item.getDate());
			}
		}
		loadClogItemSprites(items, count, RECENT_SIZE, shelf.sprites, itemManager);
		return shelf;
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
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		if (firstTimeSetup)
		{
			int width = Math.max(fm.stringWidth(SETUP_OPEN_LINE), fm.stringWidth(SETUP_SEARCH_LINE));
			width = Math.max(width, fm.stringWidth(SETUP_CHAT_LINE));
			return new Dimension(width, LINE_HEIGHT * 3);
		}

		if (notice != null)
		{
			return new Dimension(fm.stringWidth(notice), LINE_HEIGHT);
		}

		int textWidth = 0;
		int contentHeight = 0;

		// Completion, its bar, the tier ladder and the ladder's readout row.
		if (hasTotals())
		{
			textWidth = Math.max(fm.stringWidth("Completion: " + completionText()), legendWidth());
			for (String label : ClogHelper.tierLabels(obtained, totalSlots))
			{
				textWidth = Math.max(textWidth, fm.stringWidth(label));
			}
			contentHeight += LINE_HEIGHT + BAR_HEIGHT + BAR_GAP + ICON_SIZE + hoverRowHeight(fm);
		}

		if (!tabs.isEmpty())
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			textWidth = Math.max(textWidth, bfm.stringWidth("Collection Log"));
			for (Map.Entry<String, int[]> tab : tabs.entrySet())
			{
				textWidth = Math.max(textWidth, fm.stringWidth(tab.getKey() + ": "
					+ progressCountText(tab.getValue()[0], tab.getValue()[1])));
			}
			contentHeight += separatorHeight(SEPARATOR_PAD) + SUBHEADER_HEIGHT
				+ tabs.size() * (LINE_HEIGHT + BAR_HEIGHT + BAR_GAP);
		}

		// Highlights (obtained trophies only), then recent unlocks.
		for (Shelf shelf : new Shelf[]{special, recent})
		{
			if (shelf != null)
			{
				contentHeight += separatorHeight(SEPARATOR_PAD) + SUBHEADER_HEIGHT + shelfRowHeight(shelf, fm);
				int count = shelf.ids.length;
				textWidth = Math.max(textWidth, count * shelf.cellWidth(fm) + (count - 1) * RECENT_PAD);
			}
		}
		FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
		textWidth = Math.max(textWidth, Math.max(special != null ? bfm.stringWidth("Highlights") : 0,
			recent != null ? bfm.stringWidth("Recent") : 0));

		// The footer: when it last changed and who supplied it, under one rule.
		if (syncDate != null || !clogSources.isEmpty())
		{
			contentHeight += separatorHeight(SEPARATOR_PAD);
		}
		if (syncDate != null)
		{
			textWidth = Math.max(textWidth, fm.stringWidth("Last update: " + syncDate));
			contentHeight += LINE_HEIGHT;
		}
		if (!clogSources.isEmpty())
		{
			contentHeight += fm.getHeight()
				+ SOURCE_LABEL_GAP + SOURCE_ICON_SIZE + hoverRowHeight(fm);
			textWidth = Math.max(textWidth, fm.stringWidth(SOURCE_LABEL));
			textWidth = Math.max(textWidth, sourceRowWidth(clogSources.size()));
			for (ClogSource source : clogSources)
			{
				textWidth = Math.max(textWidth, fm.stringWidth(source.name));
			}
		}

		return new Dimension(textWidth, contentHeight);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(Collections.emptyList());
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		if (firstTimeSetup)
		{
			g2.setColor(NOTICE_COLOR);
			int y = startY + fm.getAscent();
			g2.drawString(SETUP_OPEN_LINE, inset, y);
			y += LINE_HEIGHT;
			g2.drawString(SETUP_SEARCH_LINE, inset, y);
			y += LINE_HEIGHT;
			g2.drawString(SETUP_CHAT_LINE, inset, y);
			return;
		}

		if (notice != null)
		{
			g2.setColor(NOTICE_COLOR);
			g2.drawString(notice, inset, startY + fm.getAscent());
			return;
		}

		int y = startY;

		if (hasTotals())
		{
			if (tierSprite != null)
			{
				g2.drawImage(tierSprite, w - inset - tierSprite.getWidth(),
					inset + (getHeaderHeight() - tierSprite.getHeight()) / 2, null);
			}
			drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Completion: ", completionText());
			y = paintBar(g2, w, y + LINE_HEIGHT, obtained, totalSlots);
			y = paintTierLadder(g2, fm, hitBoxes, w, y);
		}

		if (!tabs.isEmpty())
		{
			y = paintSubheader(g2, paintSeparator(g2, w, y, SEPARATOR_PAD), "Collection Log");
			for (Map.Entry<String, int[]> tab : tabs.entrySet())
			{
				int[] count = tab.getValue();
				drawLabelValue(g2, fm, inset, y + fm.getAscent(), tab.getKey() + ": ",
					progressCountText(count[0], count[1]), completionColor(count[0], count[1]));
				y = paintBar(g2, w, y + LINE_HEIGHT, count[0], count[1]);
			}
		}

		// The trophy shelf, present only when earned, then recent unlocks.
		y = paintShelf(g2, hitBoxes, 0, "Highlights", special, w, y, fm);
		y = paintShelf(g2, hitBoxes, 1, "Recent", recent, w, y, fm);

		if (syncDate != null || !clogSources.isEmpty())
		{
			y = paintSeparator(g2, w, y, SEPARATOR_PAD);
			g2.setFont(FontManager.getRunescapeSmallFont());
		}

		// Sync line: label orange, date green or red.
		if (syncDate != null)
		{
			drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Last update: ", syncDate,
				syncStale ? STALE_RED : CLOG_GREEN);
			y += LINE_HEIGHT;
		}

		if (!clogSources.isEmpty())
		{
			g2.setColor(MUTED_GRAY);
			int labelX = (w - fm.stringWidth(SOURCE_LABEL)) / 2;
			g2.drawString(SOURCE_LABEL, labelX, y + fm.getAscent());
			y += fm.getHeight() + SOURCE_LABEL_GAP;

			int sourceX = sourceRowStartX(w, clogSources.size());
			String hoveredName = itemHover.hoveredItemName();
			for (int i = 0; i < clogSources.size(); i++)
			{
				ClogSource source = clogSources.get(i);
				int iconX = sourceX + i * (SOURCE_ICON_SIZE + SOURCE_ICON_GAP);
				if (source.icon != null)
				{
					g2.drawImage(source.icon, iconX, y, null);
				}
				if (source.name.equals(hoveredName))
				{
					g2.setColor(CLOG_GREEN);
					g2.drawRect(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2 - 1,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2 - 1);
				}
				hitBoxes.add(new TooltipItemHover.HitBox(
					SOURCE_SECTION + i, 0, source.name,
					new Rectangle(iconX - SOURCE_HIT_PAD, y - SOURCE_HIT_PAD,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2,
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2), false, 1));
			}
			if (itemHover.hoveredSection() >= SOURCE_SECTION)
			{
				paintHeaderHoverLine(g2, fm, w, y + SOURCE_ICON_SIZE + fm.getAscent());
			}
		}

		itemHover.setHitBoxes(hitBoxes);
	}

	/**
	 * Centered sprite row with hover hit boxes and optional date captions
	 * under each cell.
	 */
	private int shelfRowHeight(Shelf shelf, FontMetrics fm)
	{
		return RECENT_SIZE + (shelf.dated() ? DATE_GAP + fm.getHeight() : 0) + hoverRowHeight(fm);
	}

	/** A shelf under its subheader: sprites centered in a row, dates below, then its hover line. */
	private int paintShelf(Graphics2D g2, List<TooltipItemHover.HitBox> hitBoxes, int section, String title,
		Shelf shelf, int w, int y, FontMetrics fm)
	{
		if (shelf == null)
		{
			return y;
		}
		y = paintSubheader(g2, paintSeparator(g2, w, y, SEPARATOR_PAD), title);
		int inset = getInset();
		int count = shelf.sprites.length;
		int cellWidth = shelf.cellWidth(fm);
		int startX = inset + (w - 2 * inset - (count * cellWidth + (count - 1) * RECENT_PAD)) / 2;
		for (int i = 0; i < count; i++)
		{
			int cellX = startX + i * (cellWidth + RECENT_PAD);
			int sx = cellX + (cellWidth - RECENT_SIZE) / 2;
			if (shelf.sprites[i] != null)
			{
				g2.drawImage(shelf.sprites[i], sx, y, null);
			}
			hitBoxes.add(new TooltipItemHover.HitBox(section, shelf.ids[i], shelf.names[i],
				new Rectangle(sx, y, RECENT_SIZE, RECENT_SIZE), true, 1));
			String date = shelf.dates != null ? shelf.dates[i] : null;
			if (date != null)
			{
				g2.setColor(MUTED_GRAY);
				g2.drawString(date, cellX + (cellWidth - fm.stringWidth(date)) / 2,
					y + RECENT_SIZE + DATE_GAP + fm.getAscent());
			}
		}
		y += shelfRowHeight(shelf, fm) - hoverRowHeight(fm);
		paintSectionHoverLine(g2, fm, w, y, section);
		return y + hoverRowHeight(fm);
	}

	@Override
	protected Color getHeaderHoverLineColor()
	{
		// Obtained items, verified providers and reached tiers share the success color.
		return itemHover.hoveredSection() == TIER_SECTION + 1 ? CLOG_RED : CLOG_GREEN;
	}

	/** A thin rail under a count: the share at a glance, the number still primary. */
	private int paintBar(Graphics2D g2, int w, int y, int obtained, int total)
	{
		int inset = getInset();
		int width = w - 2 * inset;
		g2.setColor(BAR_TRACK);
		g2.fillRect(inset, y, width, BAR_HEIGHT);
		if (obtained > 0 && total > 0)
		{
			g2.setColor(completionColor(obtained, total));
			g2.fillRect(inset, y, Math.max(1, width * Math.min(obtained, total) / total), BAR_HEIGHT);
		}
		return y + BAR_HEIGHT + BAR_GAP;
	}

	/** Every tier's icon, dimmed until reached; hovering one reads its range below. */
	private int paintTierLadder(Graphics2D g2, FontMetrics fm, List<TooltipItemHover.HitBox> hitBoxes,
		int w, int y)
	{
		String[] labels = ClogHelper.tierLabels(obtained, totalSlots);
		int x = (w - legendWidth()) / 2;
		Composite solid = g2.getComposite();
		for (int i = 0; i < labels.length; i++)
		{
			boolean reached = i <= tier;
			BufferedImage icon = tierIcons != null ? tierIcons.get(ClogHelper.CLOG_TIERS[i]) : null;
			if (icon != null)
			{
				g2.setComposite(reached ? solid : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2.drawImage(icon, x, y, null);
			}
			hitBoxes.add(new TooltipItemHover.HitBox(reached ? TIER_SECTION : TIER_SECTION + 1, 0, labels[i],
				new Rectangle(x, y, ICON_SIZE, ICON_SIZE), false, 1));
			x += ICON_SIZE + ICON_GAP;
		}
		g2.setComposite(solid);
		int section = itemHover.hoveredSection();
		if (section >= TIER_SECTION && section < SOURCE_SECTION)
		{
			paintHeaderHoverLine(g2, fm, w, y + ICON_SIZE + fm.getAscent());
		}
		return y + ICON_SIZE + hoverRowHeight(fm);
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
