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
	private static final int SUBHEADER_HEIGHT = 16;
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
	private BufferedImage noticeIcon;
	private boolean firstTimeSetup;

	private BufferedImage[] recentSprites;
	private int recentCount;
	private int[] recentIds;
	private String[] recentNames;
	private String[] recentDates;

	// Trophy shelf: only obtained specials are ever set here; an empty shelf
	// paints nothing.
	private BufferedImage[] specialSprites;
	private int specialCount;
	private int[] specialIds;
	private String[] specialNames;

	private final TooltipItemHover itemHover = new TooltipItemHover(this);
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
	public void setClogSources(boolean temple, boolean runeProfile, boolean killclog)
	{
		setClogSources(temple, runeProfile, killclog, false);
	}

	private void setClogSources(boolean temple, boolean runeProfile, boolean killclog, boolean local)
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

	public void setNotice(String notice, BufferedImage icon)
	{
		firstTimeSetup = false;
		this.notice = notice;
		this.noticeIcon = icon;
		setTitle("Clog Summary");
	}

	public void setFirstTimeSetup()
	{
		firstTimeSetup = true;
		notice = null;
		noticeIcon = null;
		setTitle("First Time Setup");
	}

	public void setRecentItems(List<ClogResult.ClogItem> recentItems, ClogResult clog,
		ItemManager itemManager)
	{
		recentCount = recentItems.size();
		if (recentCount == 0)
		{
			return;
		}

		recentSprites = new BufferedImage[recentCount];
		recentIds = new int[recentCount];
		recentNames = new String[recentCount];
		recentDates = new String[recentCount];
		for (int i = 0; i < recentCount; i++)
		{
			ClogResult.ClogItem item = recentItems.get(i);
			recentIds[i] = item.getId();
			recentNames[i] = clog != null ? clog.getItemName(item.getId()) : null;
			recentDates[i] = shortDate(item.getDate());
		}
		loadClogItemSprites(recentItems, recentCount, RECENT_SIZE, recentSprites, itemManager);
	}

	public void setSpecialItems(List<ClogResult.ClogItem> specialItems, ClogResult clog,
		ItemManager itemManager)
	{
		specialCount = specialItems.size();
		if (specialCount == 0)
		{
			return;
		}

		specialSprites = new BufferedImage[specialCount];
		specialIds = new int[specialCount];
		specialNames = new String[specialCount];
		for (int i = 0; i < specialCount; i++)
		{
			ClogResult.ClogItem item = specialItems.get(i);
			specialIds[i] = item.getId();
			specialNames[i] = clog != null ? clog.getItemName(item.getId()) : null;
		}
		loadClogItemSprites(specialItems, specialCount, RECENT_SIZE, specialSprites, itemManager);
	}

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
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
			int nw = fm.stringWidth(notice);
			if (noticeIcon != null)
			{
				nw += noticeIcon.getWidth() + 3;
			}
			return new Dimension(nw, LINE_HEIGHT);
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

		// Highlights: obtained trophies only.
		if (specialCount > 0)
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			contentHeight += separatorHeight(SEPARATOR_PAD) + SUBHEADER_HEIGHT + RECENT_SIZE + hoverRowHeight(fm);

			int rowWidth = specialCount * RECENT_SIZE + (specialCount - 1) * RECENT_PAD;
			textWidth = Math.max(textWidth, rowWidth);
			textWidth = Math.max(textWidth, bfm.stringWidth("Highlights"));
		}

		// Recent section.
		if (recentCount > 0)
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			int separatorHeight = separatorHeight(SEPARATOR_PAD);
			contentHeight += separatorHeight + SUBHEADER_HEIGHT + RECENT_SIZE + hoverRowHeight(fm);
			if (hasRecentDates())
			{
				contentHeight += DATE_GAP + fm.getHeight();
			}

			int cellWidth = recentCellWidth(fm);
			int spriteRowWidth = recentCount * cellWidth
				+ (recentCount - 1) * RECENT_PAD;
			textWidth = Math.max(textWidth, spriteRowWidth);
			textWidth = Math.max(textWidth, bfm.stringWidth("Recent"));
		}

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
			int nx = inset;
			g2.drawString(notice, nx, startY + fm.getAscent());
			if (noticeIcon != null)
			{
				nx += fm.stringWidth(notice) + 3;
				int iconY = startY + (LINE_HEIGHT - noticeIcon.getHeight()) / 2;
				g2.drawImage(noticeIcon, nx, iconY, null);
			}
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
			y = paintSubheader(g2, w, y, "Collection Log");
			g2.setFont(FontManager.getRunescapeSmallFont());
			for (Map.Entry<String, int[]> tab : tabs.entrySet())
			{
				int[] count = tab.getValue();
				drawLabelValue(g2, fm, inset, y + fm.getAscent(), tab.getKey() + ": ",
					progressCountText(count[0], count[1]), completionColor(count[0], count[1]));
				y = paintBar(g2, w, y + LINE_HEIGHT, count[0], count[1]);
			}
		}

		// Highlights: the trophy shelf, present only when earned.
		if (specialCount > 0 && specialSprites != null)
		{
			y = paintSubheader(g2, w, y, "Highlights");
			paintItemRow(g2, hitBoxes, 0, inset, y, w - 2 * inset,
				specialSprites, specialIds, specialNames, null, RECENT_SIZE, fm);
			y += RECENT_SIZE;
			paintSectionLabel(g2, fm, w, y, 0);
			y += hoverRowHeight(fm);
		}

		// Recent items section
		if (recentCount > 0 && recentSprites != null)
		{
			y = paintSubheader(g2, w, y, "Recent");
			paintItemRow(g2, hitBoxes, 1, inset, y, w - 2 * inset,
				recentSprites, recentIds, recentNames, recentDates, recentCellWidth(fm), fm);
			y += RECENT_SIZE;
			if (hasRecentDates())
			{
				y += DATE_GAP + fm.getHeight();
			}
			paintSectionLabel(g2, fm, w, y, 1);
			y += hoverRowHeight(fm);
		}

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
						SOURCE_ICON_SIZE + SOURCE_HIT_PAD * 2)));
			}
			if (itemHover.hoveredSection() >= SOURCE_SECTION)
			{
				paintHeaderHoverLine(g2, fm, w, y + SOURCE_ICON_SIZE + fm.getAscent());
			}
		}

		itemHover.setHitBoxes(hitBoxes);
	}

	private void paintSectionLabel(Graphics2D g2, FontMetrics fm, int width, int y, int section)
	{
		if (itemHover.isSectionHovered(section))
		{
			paintHeaderHoverLine(g2, fm, width, y + fm.getAscent());
		}
	}

	/** Separator plus a bold orange subheader; returns the Y under the header. */
	private int paintSubheader(Graphics2D g2, int w, int y, String label)
	{
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, getInset(), y + g2.getFontMetrics().getAscent());
		return y + SUBHEADER_HEIGHT;
	}

	/**
	 * Centered sprite row with hover hit boxes and optional date captions
	 * under each cell.
	 */
	private void paintItemRow(Graphics2D g2, List<TooltipItemHover.HitBox> hitBoxes, int section,
		int x, int y, int colWidth, BufferedImage[] sprites, int[] ids, String[] names,
		String[] dates, int cellWidth, FontMetrics fm)
	{
		int count = sprites.length;
		int rowWidth = count * cellWidth + (count - 1) * RECENT_PAD;
		int startX = x + (colWidth - rowWidth) / 2;
		g2.setFont(FontManager.getRunescapeSmallFont());
		for (int i = 0; i < count; i++)
		{
			int cellX = startX + i * (cellWidth + RECENT_PAD);
			int sx = cellX + (cellWidth - RECENT_SIZE) / 2;
			if (sprites[i] != null)
			{
				g2.drawImage(sprites[i], sx, y, null);
			}
			hitBoxes.add(new TooltipItemHover.HitBox(section, ids[i], names[i],
				new Rectangle(sx, y, RECENT_SIZE, RECENT_SIZE), true));

			String date = dates != null ? dates[i] : null;
			if (date != null)
			{
				g2.setColor(MUTED_GRAY);
				int dx = cellX + (cellWidth - fm.stringWidth(date)) / 2;
				g2.drawString(date, dx, y + RECENT_SIZE + DATE_GAP + fm.getAscent());
			}
		}
	}

	private boolean hasRecentDates()
	{
		if (recentDates == null)
		{
			return false;
		}
		for (String date : recentDates)
		{
			if (date != null)
			{
				return true;
			}
		}
		return false;
	}

	/** Recent cells widen past the sprite when a date caption needs the room. */
	private int recentCellWidth(FontMetrics fm)
	{
		int w = RECENT_SIZE;
		if (recentDates != null)
		{
			for (String date : recentDates)
			{
				if (date != null)
				{
					w = Math.max(w, fm.stringWidth(date));
				}
			}
		}
		return w;
	}

	@Override
	protected String getHeaderHoverLineText()
	{
		return itemHover.hoveredItemName();
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
				new Rectangle(x, y, ICON_SIZE, ICON_SIZE)));
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
