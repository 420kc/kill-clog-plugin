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
 * Stacked clog comparison tooltip. Blue block on top, red block below,
 * separated by an inlay line. Each block renders obtained count,
 * tier progress, sync age, trophies, and recent items.
 */
public class CompareClogSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 3;
	private static final int SEPARATOR_PAD = 3;
	private static final int RECENT_SIZE = 20;
	private static final int RECENT_PAD = 4;
	private static final int DATE_GAP = 1;
	private static final int COL_GAP = 10;
	private static final int HEADER_GAP = 4;
	private static final int BLOCK_RECENT_COUNT = 4;
	private static final int MIN_BLOCK_WIDTH = 230;


	// One player's block of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String name;
		int obtained;
		int total;
		String tierRange;
		String tierName;
		String progress;
		String nextTier;
		String sync;
		boolean syncStale;
		Map<String, BufferedImage> tierIcons;
		BufferedImage[] recentSprites;
		int recentCount;
		int[] recentIds;
		String[] recentNames;
		String[] recentDates;
		BufferedImage[] specialSprites;
		int specialCount;
		int[] specialIds;
		String[] specialNames;
		String notice;
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
	}

	@Override
	protected String getHeaderRightText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getHeaderRightColor()
	{
		// Everything on the shelves and in recents is obtained.
		return CLOG_GREEN;
	}

	public void setBlueData(String name, int obtained, int total,
		Map<String, BufferedImage> tierIcons)
	{
		setTitle("Clog Summary");
		setData(blue, name, obtained, total, tierIcons);
	}

	public void setRedData(String name, int obtained, int total,
		Map<String, BufferedImage> tierIcons)
	{
		setData(red, name, obtained, total, tierIcons);
	}

	private static void setData(Side side, String name, int obtained, int total,
		Map<String, BufferedImage> tierIcons)
	{
		side.name = name;
		side.obtained = obtained;
		side.total = total;
		side.tierIcons = tierIcons;
		if (total <= 0)
		{
			return;
		}
		ClogHelper.TierProgress tier = ClogHelper.tierProgress(obtained, total);
		side.tierRange = tier.tierRange;
		side.tierName = tier.tierName;
		side.progress = tier.progressCount;
		side.nextTier = tier.nextTierName;
	}

	public void setBlueSync(String dateText, boolean stale)
	{
		blue.sync = dateText;
		blue.syncStale = stale;
	}

	public void setRedSync(String dateText, boolean stale)
	{
		red.sync = dateText;
		red.syncStale = stale;
	}

	public void setBlueNotice(String notice)
	{
		blue.notice = notice;
	}

	public void setRedNotice(String notice)
	{
		red.notice = notice;
	}

	public void setBlueRecent(List<ClogResult.ClogItem> items, ClogResult clog, ItemManager itemManager)
	{
		setRecent(blue, items, clog, itemManager);
	}

	public void setRedRecent(List<ClogResult.ClogItem> items, ClogResult clog, ItemManager itemManager)
	{
		setRecent(red, items, clog, itemManager);
	}

	private void setRecent(Side side, List<ClogResult.ClogItem> items, ClogResult clog,
		ItemManager itemManager)
	{
		side.recentCount = Math.min(items.size(), BLOCK_RECENT_COUNT);
		side.recentSprites = loadRecentSprites(items, side.recentCount, itemManager);
		side.recentIds = new int[side.recentCount];
		side.recentNames = new String[side.recentCount];
		side.recentDates = new String[side.recentCount];
		for (int i = 0; i < side.recentCount; i++)
		{
			ClogResult.ClogItem item = items.get(i);
			side.recentIds[i] = item.getId();
			side.recentNames[i] = clog != null ? clog.getItemName(item.getId()) : null;
			side.recentDates[i] = ClogSummaryTooltip.shortDate(item.getDate());
		}
	}

	public void setBlueSpecial(List<ClogResult.ClogItem> items, ClogResult clog, ItemManager itemManager)
	{
		setSpecial(blue, items, clog, itemManager);
	}

	public void setRedSpecial(List<ClogResult.ClogItem> items, ClogResult clog, ItemManager itemManager)
	{
		setSpecial(red, items, clog, itemManager);
	}

	private void setSpecial(Side side, List<ClogResult.ClogItem> items, ClogResult clog,
		ItemManager itemManager)
	{
		side.specialCount = items.size();
		if (side.specialCount == 0)
		{
			return;
		}
		side.specialSprites = loadRecentSprites(items, side.specialCount, itemManager);
		side.specialIds = new int[side.specialCount];
		side.specialNames = new String[side.specialCount];
		for (int i = 0; i < side.specialCount; i++)
		{
			ClogResult.ClogItem item = items.get(i);
			side.specialIds[i] = item.getId();
			side.specialNames[i] = clog != null ? clog.getItemName(item.getId()) : null;
		}
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

		int blueH = measureBlockHeight(fm, blue);
		int redH = measureBlockHeight(fm, red);
		int totalHeight = blueH + separatorHeight(SEPARATOR_PAD) + redH;

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
		int w = Math.max(sideValueWidth(fm, blue), sideValueWidth(fm, red));
		// Recent sprites.
		int recentW = BLOCK_RECENT_COUNT * RECENT_SIZE + (BLOCK_RECENT_COUNT - 1) * RECENT_PAD;
		return Math.max(w, recentW);
	}

	private int sideValueWidth(FontMetrics fm, Side side)
	{
		int w = 0;
		// Player name.
		if (side.name != null) w = Math.max(w, fm.stringWidth(side.name));
		// Obtained count or no-data notice.
		w = Math.max(w, fm.stringWidth(obtainedValueText(side.notice, side.obtained, side.total)));
		// Tier range and progress (icon + text), measured from real values.
		w = Math.max(w, tierValueWidth(fm, side.tierName, side.tierRange, side.tierIcons));
		w = Math.max(w, progressValueWidth(fm, side.nextTier, side.progress, side.tierIcons));
		// Last update.
		if (side.sync != null) w = Math.max(w, fm.stringWidth(side.sync));
		// Trophy and recent rows (recent cells widen for date captions).
		w = Math.max(w, itemRowWidth(side.specialCount, RECENT_SIZE));
		w = Math.max(w, itemRowWidth(side.recentCount, recentCellWidth(fm, side)));
		return w;
	}

	private static int itemRowWidth(int count, int cellWidth)
	{
		return count > 0 ? count * cellWidth + (count - 1) * RECENT_PAD : 0;
	}

	/** Recent cells widen past the sprite when a date caption needs the room. */
	private static int recentCellWidth(FontMetrics fm, Side side)
	{
		int w = RECENT_SIZE;
		if (side.recentDates != null)
		{
			for (String date : side.recentDates)
			{
				if (date != null)
				{
					w = Math.max(w, fm.stringWidth(date));
				}
			}
		}
		return w;
	}

	private static boolean hasRecentDates(Side side)
	{
		if (side.recentDates == null)
		{
			return false;
		}
		for (String date : side.recentDates)
		{
			if (date != null)
			{
				return true;
			}
		}
		return false;
	}

	private static String obtainedValueText(String notice, int obtained, int total)
	{
		return notice != null ? notice : obtained + "/" + total;
	}

	private int tierValueWidth(FontMetrics fm, String tierName, String tierRange,
		Map<String, BufferedImage> tierIcons)
	{
		if (tierRange == null)
		{
			return fm.stringWidth("--");
		}
		int w = fm.stringWidth(capitalize(tierName) + ": " + tierRange);
		BufferedImage icon = tierIcons != null ? tierIcons.get(tierName) : null;
		if (icon != null)
		{
			w += icon.getWidth() + ICON_GAP;
		}
		return w;
	}

	private int progressValueWidth(FontMetrics fm, String nextTier, String progress,
		Map<String, BufferedImage> tierIcons)
	{
		if (progress == null)
		{
			return fm.stringWidth("--");
		}
		int w = fm.stringWidth(progress) + fm.stringWidth(" more");
		BufferedImage icon = tierIcons != null ? tierIcons.get(nextTier) : null;
		if (icon != null)
		{
			w += icon.getWidth() + ICON_GAP;
		}
		return w;
	}

	private int measureBlockHeight(FontMetrics fm, Side side)
	{
		int h = 0;
		// Name.
		h += fm.getHeight() + HEADER_GAP;
		// Obtained.
		h += LINE_HEIGHT;
		// Tier.
		if (side.tierRange != null) h += LINE_HEIGHT;
		// Progress.
		if (side.progress != null) h += LINE_HEIGHT;
		// Last update.
		if (side.sync != null) h += LINE_HEIGHT;
		// Special.
		if (side.specialCount > 0)
		{
			h += LINE_HEIGHT + RECENT_SIZE;
		}
		// Recent.
		if (side.recentCount > 0)
		{
			h += LINE_HEIGHT + RECENT_SIZE;
			if (hasRecentDates(side))
			{
				h += DATE_GAP + fm.getHeight();
			}
		}
		return h;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(Collections.emptyList());
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
		int inset = getInset();
		int contentWidth = w - 2 * inset;

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		int labelW = measureLabelWidth(fm, g2.getFontMetrics(FontManager.getRunescapeBoldFont()));
		int valueX = inset + labelW + COL_GAP;
		int labelX = inset;
		int y = startY;

		// Blue block.
		y = paintBlock(g2, fm, labelX, valueX, y, w, contentWidth, COMPARE_BLUE, blue, 0, hitBoxes);

		// Separator.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// Red block.
		g2.setFont(FontManager.getRunescapeSmallFont());
		paintBlock(g2, fm, labelX, valueX, y, w, contentWidth, COMPARE_RED, red, 2, hitBoxes);

		itemHover.setHitBoxes(hitBoxes);
	}

	private int paintBlock(Graphics2D g2, FontMetrics fm,
		int labelX, int valueX, int y, int w, int contentWidth,
		Color playerColor, Side side, int sectionBase,
		List<TooltipItemHover.HitBox> hitBoxes)
	{
		int inset = getInset();

		// Name.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		g2.setColor(playerColor);
		g2.drawString(side.name != null ? side.name : "--", labelX, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		// Obtained.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Obtained", labelX, y + fm.getAscent());
		if (side.notice != null)
		{
			g2.setColor(compareValueColor(side.notice, playerColor));
			g2.drawString(side.notice, valueX, y + fm.getAscent());
		}
		else
		{
			Color progressColor = completionColor(side.obtained, side.total);
			g2.setColor(progressColor);
			g2.drawString(side.obtained + "/" + side.total, valueX, y + fm.getAscent());
		}
		y += LINE_HEIGHT;

		// Tier.
		if (side.tierRange != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Tier", labelX, y + fm.getAscent());
			paintTierValue(g2, fm, valueX, y, side.tierName, side.tierRange,
				side.tierIcons, playerColor);
			y += LINE_HEIGHT;
		}

		// Progress.
		if (side.progress != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Progress", labelX, y + fm.getAscent());
			paintProgressValue(g2, fm, valueX, y, side.nextTier, side.progress,
				side.tierIcons, completionColor(side.obtained, side.total));
			y += LINE_HEIGHT;
		}

		// Last update.
		if (side.sync != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Last update", labelX, y + fm.getAscent());
			g2.setColor(side.syncStale ? STALE_RED : CLOG_GREEN);
			g2.drawString(side.sync, valueX, y + fm.getAscent());
			y += LINE_HEIGHT;
		}

		// Special: the trophy shelf, present only when earned.
		int rowW = contentWidth - (valueX - inset);
		if (side.specialCount > 0)
		{
			g2.setFont(FontManager.getRunescapeBoldFont());
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Special", labelX, y + g2.getFontMetrics().getAscent());
			y += LINE_HEIGHT;

			paintItemRow(g2, hitBoxes, sectionBase, valueX, y, rowW,
				side.specialSprites, side.specialIds, side.specialNames, null, RECENT_SIZE, fm);
			y += RECENT_SIZE;
		}

		// Recent.
		if (side.recentCount > 0)
		{
			g2.setFont(FontManager.getRunescapeBoldFont());
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Recent", labelX, y + g2.getFontMetrics().getAscent());
			y += LINE_HEIGHT;

			paintItemRow(g2, hitBoxes, sectionBase + 1, valueX, y, rowW,
				side.recentSprites, side.recentIds, side.recentNames, side.recentDates,
				recentCellWidth(fm, side), fm);
			y += RECENT_SIZE;
			if (hasRecentDates(side))
			{
				y += DATE_GAP + fm.getHeight();
			}
		}

		return y;
	}

	/**
	 * Centered sprite row with hover hit boxes and optional date captions
	 * under each cell.
	 */
	private void paintItemRow(Graphics2D g2, List<TooltipItemHover.HitBox> hitBoxes, int section,
		int x, int y, int colWidth, BufferedImage[] sprites, int[] ids, String[] names,
		String[] dates, int cellWidth, FontMetrics fm)
	{
		int count = sprites != null ? sprites.length : 0;
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

	// Data helpers.

	private BufferedImage[] loadRecentSprites(List<ClogResult.ClogItem> items,
		int count, ItemManager itemManager)
	{
		if (count == 0) return null;
		BufferedImage[] sprites = new BufferedImage[count];
		loadClogItemSprites(items, count, RECENT_SIZE, sprites, itemManager);
		return sprites;
	}
}
