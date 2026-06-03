package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Stacked clog comparison tooltip. Blue block on top, red block below,
 * separated by an inlay line. Each block renders obtained count,
 * tier progress, sync age, and recent items.
 */
public class CompareClogSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 3;
	private static final int SEPARATOR_PAD = 3;
	private static final int RECENT_SIZE = 20;
	private static final int RECENT_PAD = 4;
	private static final int COL_GAP = 10;
	private static final int HEADER_GAP = 4;
	private static final int BLOCK_RECENT_COUNT = 4;
	private static final int MIN_BLOCK_WIDTH = 230;
	private static final Color STALE_RED = new Color(255, 60, 60);


	// Blue player values.
	private String blueName;
	private int blueObtained;
	private int blueTotal;
	private String blueTierRange;
	private String blueTierName;
	private String blueProgress;
	private String blueNextTier;
	private String blueSync;
	private boolean blueSyncStale;
	private Map<String, BufferedImage> blueTierIcons;
	private BufferedImage[] blueRecentSprites;
	private int blueRecentCount;

	// Red player values.
	private String redName;
	private int redObtained;
	private int redTotal;
	private String redTierRange;
	private String redTierName;
	private String redProgress;
	private String redNextTier;
	private String redSync;
	private boolean redSyncStale;
	private Map<String, BufferedImage> redTierIcons;
	private BufferedImage[] redRecentSprites;
	private int redRecentCount;

	// Optional no-data notices.
	private String blueNotice;
	private String redNotice;

	public void setBlueData(String name, int obtained, int total,
		Map<String, BufferedImage> tierIcons)
	{
		setTitle("Clog Summary");
		this.blueName = name;
		this.blueObtained = obtained;
		this.blueTotal = total;
		this.blueTierIcons = tierIcons;
		computeTier(obtained, total, true);
	}

	public void setRedData(String name, int obtained, int total,
		Map<String, BufferedImage> tierIcons)
	{
		this.redName = name;
		this.redObtained = obtained;
		this.redTotal = total;
		this.redTierIcons = tierIcons;
		computeTier(obtained, total, false);
	}

	public void setBlueSync(String dateText, boolean stale)
	{
		this.blueSync = dateText;
		this.blueSyncStale = stale;
	}

	public void setRedSync(String dateText, boolean stale)
	{
		this.redSync = dateText;
		this.redSyncStale = stale;
	}

	public void setBlueNotice(String notice)
	{
		this.blueNotice = notice;
	}

	public void setRedNotice(String notice)
	{
		this.redNotice = notice;
	}

	public void setBlueRecent(List<ClogResult.ClogItem> items, ItemManager itemManager)
	{
		blueRecentCount = Math.min(items.size(), BLOCK_RECENT_COUNT);
		blueRecentSprites = loadRecentSprites(items, blueRecentCount, itemManager);
	}

	public void setRedRecent(List<ClogResult.ClogItem> items, ItemManager itemManager)
	{
		redRecentCount = Math.min(items.size(), BLOCK_RECENT_COUNT);
		redRecentSprites = loadRecentSprites(items, redRecentCount, itemManager);
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());

		int labelW = measureLabelWidth(fm, bfm);
		int valueW = measureValueWidth(fm);
		int blockWidth = labelW + COL_GAP + valueW;

		int blueH = measureBlockHeight(fm, blueTierRange, blueProgress,
			blueSync, blueRecentCount);
		int redH = measureBlockHeight(fm, redTierRange, redProgress,
			redSync, redRecentCount);
		int totalHeight = blueH + SEPARATOR_PAD + 1 + SEPARATOR_PAD + redH;

		return new Dimension(Math.max(blockWidth, MIN_BLOCK_WIDTH), totalHeight);
	}

	private int measureLabelWidth(FontMetrics fm, FontMetrics bfm)
	{
		int w = 0;
		w = Math.max(w, fm.stringWidth("Obtained"));
		w = Math.max(w, fm.stringWidth("Tier"));
		w = Math.max(w, fm.stringWidth("Progress"));
		w = Math.max(w, fm.stringWidth("Last update"));
		w = Math.max(w, bfm.stringWidth("Recent"));
		return w;
	}

	private int measureValueWidth(FontMetrics fm)
	{
		int w = 0;
		// Player names.
		if (blueName != null) w = Math.max(w, fm.stringWidth(blueName));
		if (redName != null) w = Math.max(w, fm.stringWidth(redName));
		w = Math.max(w, fm.stringWidth("9999/9999"));
		int iconW = ICON_SIZE + ICON_GAP;
		w = Math.max(w, iconW + fm.stringWidth("Adamant: 1000-1249"));
		w = Math.max(w, iconW + fm.stringWidth("Gilded: 1525+"));
		w = Math.max(w, iconW + fm.stringWidth("999 more"));
		w = Math.max(w, fm.stringWidth("3 months ago"));
		// Recent sprites.
		int recentW = BLOCK_RECENT_COUNT * RECENT_SIZE + (BLOCK_RECENT_COUNT - 1) * RECENT_PAD;
		w = Math.max(w, recentW);
		return w;
	}

	private int measureBlockHeight(FontMetrics fm, String tierRange,
		String progress, String sync, int recentCount)
	{
		int h = 0;
		// Name.
		h += fm.getHeight() + HEADER_GAP;
		// Obtained.
		h += LINE_HEIGHT;
		// Tier.
		if (tierRange != null) h += LINE_HEIGHT;
		// Progress.
		if (progress != null) h += LINE_HEIGHT;
		// Last update.
		if (sync != null) h += LINE_HEIGHT;
		// Recent.
		if (recentCount > 0)
		{
			h += LINE_HEIGHT + RECENT_SIZE;
		}
		return h;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		int contentWidth = w - 2 * inset;

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		int labelW = measureLabelWidth(fm, g2.getFontMetrics(FontManager.getRunescapeBoldFont()));
		int valueX = inset + labelW + COL_GAP;
		int labelX = inset;
		int y = startY;

		// Blue block.
		y = paintBlock(g2, fm, labelX, valueX, y, w, contentWidth,
			COMPARE_BLUE, blueName, blueNotice, blueObtained, blueTotal,
			blueTierName, blueTierRange, blueTierIcons,
			blueNextTier, blueProgress,
			blueSync, blueSyncStale,
			blueRecentSprites, blueRecentCount);

		// Separator.
		y += SEPARATOR_PAD;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, y, w - inset - 1, y);
		y += 1 + SEPARATOR_PAD;

		// Red block.
		g2.setFont(FontManager.getRunescapeSmallFont());
		paintBlock(g2, fm, labelX, valueX, y, w, contentWidth,
			COMPARE_RED, redName, redNotice, redObtained, redTotal,
			redTierName, redTierRange, redTierIcons,
			redNextTier, redProgress,
			redSync, redSyncStale,
			redRecentSprites, redRecentCount);
	}

	private int paintBlock(Graphics2D g2, FontMetrics fm,
		int labelX, int valueX, int y, int w, int contentWidth,
		Color playerColor, String name, String notice,
		int obtained, int total,
		String tierName, String tierRange, Map<String, BufferedImage> tierIcons,
		String nextTier, String progress,
		String sync, boolean syncStale,
		BufferedImage[] recentSprites, int recentCount)
	{
		int inset = getInset();

		// Name.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		g2.setColor(playerColor);
		g2.drawString(name != null ? name : "--", labelX, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		// Obtained.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Obtained", labelX, y + fm.getAscent());
		if (notice != null)
		{
			g2.setColor(NOTICE_COLOR);
			g2.drawString(notice, valueX, y + fm.getAscent());
		}
		else
		{
			Color progressColor = completionColor(obtained, total);
			g2.setColor(progressColor);
			g2.drawString(obtained + "/" + total, valueX, y + fm.getAscent());
		}
		y += LINE_HEIGHT;

		// Tier.
		if (tierRange != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Tier", labelX, y + fm.getAscent());
			paintTierValue(g2, fm, valueX, y, tierName, tierRange, tierIcons, playerColor);
			y += LINE_HEIGHT;
		}

		// Progress.
		if (progress != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Progress", labelX, y + fm.getAscent());
			paintProgressValue(g2, fm, valueX, y, nextTier, progress, tierIcons,
				completionColor(obtained, total));
			y += LINE_HEIGHT;
		}

		// Last update.
		if (sync != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Last update", labelX, y + fm.getAscent());
			g2.setColor(syncStale ? STALE_RED : CLOG_GREEN);
			g2.drawString(sync, valueX, y + fm.getAscent());
			y += LINE_HEIGHT;
		}

		// Recent.
		if (recentCount > 0)
		{
			g2.setFont(FontManager.getRunescapeBoldFont());
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Recent", labelX, y + g2.getFontMetrics().getAscent());
			y += LINE_HEIGHT;

			int recentW = contentWidth - (valueX - inset);
			paintRecentSprites(g2, valueX, y, recentW, recentSprites, recentCount);
			y += RECENT_SIZE;
		}

		return y;
	}

	// Value painters.

	private void paintTierValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String tierName, String tierRange, Map<String, BufferedImage> tierIcons, Color playerColor)
	{
		if (tierRange == null)
		{
			g2.setColor(playerColor);
			g2.drawString("--", x, y + fm.getAscent());
			return;
		}
		int cx = x;
		BufferedImage icon = tierIcons != null ? tierIcons.get(tierName) : null;
		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
			g2.drawImage(icon, cx, iconY, null);
			cx += icon.getWidth() + ICON_GAP;
		}
		g2.setColor(playerColor);
		g2.drawString(capitalize(tierName) + ": " + tierRange, cx, y + fm.getAscent());
	}

	private void paintProgressValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String nextTier, String progress, Map<String, BufferedImage> tierIcons, Color progressColor)
	{
		if (progress == null)
		{
			g2.setColor(progressColor);
			g2.drawString("--", x, y + fm.getAscent());
			return;
		}
		int cx = x;
		BufferedImage icon = tierIcons != null ? tierIcons.get(nextTier) : null;
		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
			g2.drawImage(icon, cx, iconY, null);
			cx += icon.getWidth() + ICON_GAP;
		}
		g2.setColor(OSRS_ORANGE);
		g2.drawString(progress, cx, y + fm.getAscent());
		cx += fm.stringWidth(progress);
		g2.drawString(" more", cx, y + fm.getAscent());
	}

	private void paintRecentSprites(Graphics2D g2, int x, int y, int colWidth,
		BufferedImage[] sprites, int count)
	{
		if (sprites == null || count == 0) return;
		int spriteRowW = count * RECENT_SIZE + (count - 1) * RECENT_PAD;
		int startX = x + (colWidth - spriteRowW) / 2;
		for (int i = 0; i < count; i++)
		{
			int sx = startX + i * (RECENT_SIZE + RECENT_PAD);
			if (sprites[i] != null)
			{
				g2.drawImage(sprites[i], sx, y, null);
			}
		}
	}

	// Data helpers.

	private void computeTier(int obtained, int total, boolean blue)
	{
		if (total <= 0)
		{
			return;
		}
		int gildedThreshold = (int) (total * 0.9) / 25 * 25;
		String currentTier = ClogHelper.getClogTierName(obtained, total);

		if (currentTier == null)
		{
			if (blue)
			{
				blueProgress = String.valueOf(ClogHelper.CLOG_TIER_THRESHOLDS[0] - obtained);
				blueNextTier = "bronze";
			}
			else
			{
				redProgress = String.valueOf(ClogHelper.CLOG_TIER_THRESHOLDS[0] - obtained);
				redNextTier = "bronze";
			}
			return;
		}

		if ("gilded".equals(currentTier))
		{
			if (blue)
			{
				blueTierRange = gildedThreshold + "+";
				blueTierName = "gilded";
			}
			else
			{
				redTierRange = gildedThreshold + "+";
				redTierName = "gilded";
			}
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

		String range = currentThreshold + "-" + (nextThreshold - 1);
		String prog = String.valueOf(nextThreshold - obtained);
		if (blue)
		{
			blueTierRange = range; blueTierName = currentTier;
			blueProgress = prog; blueNextTier = nextTier;
		}
		else
		{
			redTierRange = range; redTierName = currentTier;
			redProgress = prog; redNextTier = nextTier;
		}
	}

	private BufferedImage[] loadRecentSprites(List<ClogResult.ClogItem> items,
		int count, ItemManager itemManager)
	{
		if (count == 0) return null;
		BufferedImage[] sprites = new BufferedImage[count];
		for (int i = 0; i < count; i++)
		{
			BufferedImage img = itemManager.getImage(items.get(i).getId(), 1, false);
			sprites[i] = ImageUtil.resizeImage(img, RECENT_SIZE, RECENT_SIZE);
			if (img instanceof AsyncBufferedImage)
			{
				final int idx = i;
				((AsyncBufferedImage) img).onLoaded(() ->
				{
					sprites[idx] = ImageUtil.resizeImage(
						itemManager.getImage(items.get(idx).getId(), 1, false),
						RECENT_SIZE, RECENT_SIZE);
					SwingUtilities.invokeLater(this::repaint);
				});
			}
		}
		return sprites;
	}

	private static String capitalize(String s)
	{
		if (s == null || s.isEmpty()) return s;
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}
}
