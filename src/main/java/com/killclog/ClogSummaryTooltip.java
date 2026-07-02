package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
	private static final String TEMPLE_SOURCE = "TempleOSRS";
	private static final String RUNEPROFILE_SOURCE = "RuneProfile";
	private static final Color STALE_RED = new Color(255, 60, 60);

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
	private boolean clogTemple;
	private boolean clogRuneProfile;

	public void setTierData(int obtained, int totalSlots, Map<String, BufferedImage> tierIcons)
	{
		setTitle("Clog Summary");
		setObtained(obtained, totalSlots);
		this.tierIcons = tierIcons;

		int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
		String currentTier = ClogHelper.getClogTierName(obtained, totalSlots);

		if (currentTier == null)
		{
			tierRange = null;
			tierName = null;
			progressCount = String.valueOf(ClogHelper.CLOG_TIER_THRESHOLDS[0] - obtained);
			nextTierName = "bronze";
			return;
		}

		if ("gilded".equals(currentTier))
		{
			tierRange = gildedThreshold + "+";
			tierName = "gilded";
			progressCount = null;
			nextTierName = null;
			return;
		}

		int tierIndex = -1;
		for (int i = 0; i < ClogHelper.CLOG_TIERS.length; i++)
		{
			if (ClogHelper.CLOG_TIERS[i].equals(currentTier))
			{
				tierIndex = i;
				break;
			}
		}

		int currentThreshold = ClogHelper.CLOG_TIER_THRESHOLDS[tierIndex];
		int nextThreshold;
		String nextTier;
		if (tierIndex + 1 < ClogHelper.CLOG_TIER_THRESHOLDS.length)
		{
			nextThreshold = ClogHelper.CLOG_TIER_THRESHOLDS[tierIndex + 1];
			nextTier = ClogHelper.CLOG_TIERS[tierIndex + 1];
		}
		else
		{
			nextThreshold = gildedThreshold;
			nextTier = "gilded";
		}

		tierRange = currentThreshold + "-" + (nextThreshold - 1);
		tierName = currentTier;
		progressCount = String.valueOf(nextThreshold - obtained);
		nextTierName = nextTier;
	}

	public void setSyncData(String dateText, boolean stale)
	{
		this.syncDate = dateText;
		this.syncStale = stale;
	}

	/** Record which providers supplied this player's synced clog data for the summary badge. */
	public void setClogSources(boolean temple, boolean runeProfile)
	{
		this.clogTemple = temple;
		this.clogRuneProfile = runeProfile;
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

	public void setRecentItems(List<ClogResult.ClogItem> recentItems, ItemManager itemManager)
	{
		recentCount = recentItems.size();
		if (recentCount == 0)
		{
			return;
		}

		recentSprites = new BufferedImage[recentCount];
		loadClogItemSprites(recentItems, recentCount, RECENT_SIZE, recentSprites, itemManager);
	}

	@Override
	protected boolean hasTitleCornerBadge()
	{
		return clogTemple || clogRuneProfile;
	}

	@Override
	protected void onTitleCornerHoverChanged(boolean hovered)
	{
		setSize(getPreferredSize());
		revalidate();
	}

	String[] sourceRows()
	{
		if (clogTemple && clogRuneProfile)
		{
			return new String[]{TEMPLE_SOURCE, RUNEPROFILE_SOURCE};
		}
		if (clogTemple)
		{
			return new String[]{TEMPLE_SOURCE};
		}
		if (clogRuneProfile)
		{
			return new String[]{RUNEPROFILE_SOURCE};
		}
		return new String[0];
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

		// Recent section.
		if (recentCount > 0)
		{
			FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());
			int separatorHeight = separatorHeight(SEPARATOR_PAD);
			contentHeight += separatorHeight + SUBHEADER_HEIGHT + RECENT_SIZE;

			int spriteRowWidth = recentCount * RECENT_SIZE
				+ (recentCount - 1) * RECENT_PAD;
			textWidth = Math.max(textWidth, spriteRowWidth);
			textWidth = Math.max(textWidth, bfm.stringWidth("Recent"));
		}

		if (isTitleCornerHovered())
		{
			String[] sourceRows = sourceRows();
			if (sourceRows.length > 0)
			{
				contentHeight += separatorHeight(SEPARATOR_PAD) + LINE_HEIGHT * sourceRows.length;
				for (String row : sourceRows)
				{
					textWidth = Math.max(textWidth, fm.stringWidth(row));
				}
			}
		}

		return new Dimension(textWidth, contentHeight);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
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

		// Recent items section
		if (recentCount > 0 && recentSprites != null)
		{
			// Separator.
			y = paintSeparator(g2, w, y, SEPARATOR_PAD);

			// "Recent" subheader.
			g2.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics bfm = g2.getFontMetrics();
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Recent", inset, y + bfm.getAscent());
			y += SUBHEADER_HEIGHT;

			// Center the recent item sprites.
			paintSpriteRow(g2, inset, y, w - 2 * inset,
				recentSprites, recentCount, RECENT_SIZE, RECENT_PAD);
			y += RECENT_SIZE;
		}

		paintSourceRows(g2, fm, w, y);
	}

	private void paintSourceRows(Graphics2D g2, FontMetrics fm, int w, int y)
	{
		if (!isTitleCornerHovered())
		{
			return;
		}
		String[] sourceRows = sourceRows();
		if (sourceRows.length == 0)
		{
			return;
		}

		y = paintSeparator(g2, w, y, SEPARATOR_PAD);
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		g2.setColor(CLOG_GREEN);
		int inset = getInset();
		for (String row : sourceRows)
		{
			int textX = inset + (w - 2 * inset - fm.stringWidth(row)) / 2;
			g2.drawString(row, textX, y + fm.getAscent());
			y += LINE_HEIGHT;
		}
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
