package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * Clog summary tooltip on the summary-bar clog cell.
 * Title "Clog Summary", obtained subtitle, tier progress lines with icons,
 * and recent obtained items at the bottom.
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
	private static final String[] MONTHS = {
		"Jan", "Feb", "Mar", "Apr", "May", "Jun",
		"Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
	};
	private static final String TEMPLE_SOURCE = "TempleOSRS";
	private static final String RUNEPROFILE_SOURCE = "RuneProfile";
	private static final String KILLCLOG_SOURCE = "Kill Clog Sync";

	private String tierRange;
	private String tierName;
	private String progressCount;
	private String nextTierName;
	private String syncDate;
	private boolean syncStale;

	private Map<String, BufferedImage> tierIcons;
	private String notice;
	private BufferedImage noticeIcon;

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
	private boolean clogTemple;
	private boolean clogRuneProfile;
	private boolean clogKillclog;

	public void setTierData(int obtained, int totalSlots, Map<String, BufferedImage> tierIcons)
	{
		setTitle("Clog Summary");
		setObtained(obtained, totalSlots);
		this.tierIcons = tierIcons;

		ClogHelper.TierProgress tier = ClogHelper.tierProgress(obtained, totalSlots);
		tierRange = tier.tierRange;
		tierName = tier.tierName;
		progressCount = tier.progressCount;
		nextTierName = tier.nextTierName;
	}

	public void setSyncData(String dateText, boolean stale)
	{
		this.syncDate = dateText;
		this.syncStale = stale;
	}

	/** Record which providers supplied this player's synced clog data for the summary badge. */
	public void setClogSources(boolean temple, boolean runeProfile, boolean killclog)
	{
		this.clogTemple = temple;
		this.clogRuneProfile = runeProfile;
		this.clogKillclog = killclog;
	}

	public void setNotice(String notice)
	{
		this.notice = notice;
		setTitle("Clog Summary");
	}

	public void setNotice(String notice, BufferedImage icon)
	{
		this.notice = notice;
		this.noticeIcon = icon;
		setTitle("Clog Summary");
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

	@Override
	protected boolean hasTitleCornerBadge()
	{
		return clogTemple || clogRuneProfile || clogKillclog;
	}

	// Provenance takes over the title line while the badge is hovered, so it
	// can never crowd or clip the title. The player's own sync leads when it
	// fed the result, matching the website's source labeling.
	String sourceLine()
	{
		if (clogKillclog)
		{
			if (clogTemple && clogRuneProfile)
			{
				return "Kill Clog + Temple + RP";
			}
			if (clogTemple)
			{
				return "Kill Clog + Temple";
			}
			if (clogRuneProfile)
			{
				return "Kill Clog + RP";
			}
			return KILLCLOG_SOURCE;
		}
		if (clogTemple && clogRuneProfile)
		{
			return "Temple + RP";
		}
		if (clogTemple)
		{
			return TEMPLE_SOURCE;
		}
		if (clogRuneProfile)
		{
			return RUNEPROFILE_SOURCE;
		}
		return null;
	}

	@Override
	protected String getTitleCornerHoverText()
	{
		return sourceLine();
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		if (notice != null)
		{
			int nw = fm.stringWidth(notice);
			if (noticeIcon != null)
			{
				nw += noticeIcon.getWidth() + 3;
			}
			return new Dimension(nw, LINE_HEIGHT);
		}

		int iconWidth = ICON_SIZE + ICON_GAP;
		int textWidth = 0;

		if (tierRange != null)
		{
			String label = capitalize(tierName) + ": ";
			textWidth = Math.max(textWidth, iconWidth + fm.stringWidth(label) + fm.stringWidth(tierRange));
		}
		if (progressCount != null)
		{
			String label = capitalize(nextTierName) + ": ";
			textWidth = Math.max(textWidth, iconWidth + fm.stringWidth(label) + fm.stringWidth(progressCount + " more"));
		}
		String syncLabel = "Last update: ";
		if (syncDate != null)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(syncLabel + syncDate));
		}

		int lines = 0;
		if (tierRange != null) lines++;
		if (progressCount != null) lines++;
		if (syncDate != null) lines++;

		int contentHeight = LINE_HEIGHT * lines;

		// Special section: obtained trophies only.
		if (specialCount > 0)
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			contentHeight += separatorHeight(SEPARATOR_PAD) + SUBHEADER_HEIGHT + RECENT_SIZE;

			int rowWidth = specialCount * RECENT_SIZE + (specialCount - 1) * RECENT_PAD;
			textWidth = Math.max(textWidth, rowWidth);
			textWidth = Math.max(textWidth, bfm.stringWidth("Special"));
		}

		// Recent section.
		if (recentCount > 0)
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			int separatorHeight = separatorHeight(SEPARATOR_PAD);
			contentHeight += separatorHeight + SUBHEADER_HEIGHT + RECENT_SIZE;
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

		// Current tier: [icon] Rune: 1100-1199
		if (tierRange != null)
		{
			paintTierLine(g2, fm, inset, y, tierName, tierRange, Color.WHITE, null);
			y += LINE_HEIGHT;
		}

		// Progress: [icon] Dragon: 18 more
		if (progressCount != null)
		{
			paintTierLine(g2, fm, inset, y, nextTierName, progressCount, Color.WHITE, " more");
			y += LINE_HEIGHT;
		}

		// Sync line: label orange, date green or red.
		if (syncDate != null)
		{
			String label = "Last update: ";
			g2.setColor(OSRS_ORANGE);
			g2.drawString(label, inset, y + fm.getAscent());
			g2.setColor(syncStale ? STALE_RED : CLOG_GREEN);
			g2.drawString(syncDate, inset + fm.stringWidth(label), y + fm.getAscent());
			y += LINE_HEIGHT;
		}

		// Special items section: the trophy shelf, present only when earned.
		if (specialCount > 0 && specialSprites != null)
		{
			y = paintSubheader(g2, w, y, "Special");
			paintItemRow(g2, hitBoxes, 0, inset, y, w - 2 * inset,
				specialSprites, specialIds, specialNames, null, RECENT_SIZE, fm);
			y += RECENT_SIZE;
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
		}

		itemHover.setHitBoxes(hitBoxes);
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
	protected String getTitleHoverText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getTitleHoverColor()
	{
		// Everything on the shelf and in recents is obtained.
		return CLOG_GREEN;
	}

	/** Draws: [icon] Tier: value [suffix]. */
	private void paintTierLine(Graphics2D g2, FontMetrics fm, int x, int y,
		String tier, String value, Color valueColor, String suffix)
	{
		int textY = y + fm.getAscent();

		BufferedImage icon = tierIcons != null ? tierIcons.get(tier) : null;
		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
			g2.drawImage(icon, x, iconY, null);
			x += icon.getWidth() + ICON_GAP;
		}

		String label = capitalize(tier) + ": ";
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, x, textY);
		x += fm.stringWidth(label);

		g2.setColor(valueColor);
		g2.drawString(value, x, textY);
		x += fm.stringWidth(value);

		if (suffix != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString(suffix, x, textY);
		}
	}
}
