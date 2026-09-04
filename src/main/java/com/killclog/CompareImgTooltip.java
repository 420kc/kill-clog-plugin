package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * Comparison sprite tooltip: stacked blue/red grids for the same boss or category.
 * Header shows dual obtained/rank values, body shows both players' sprite grids.
 */
public class CompareImgTooltip extends TitleTooltip
{
	private static final int SPRITE_SIZE = 15;
	private static final int PADDING = 4;
	private static final int GRID_COLS = 5;
	private static final int SECTION_GAP = 6;
	private static final Font NAME_FONT = FontManager.getRunescapeSmallFont();

	// One player's header stats and grid data; the tooltip holds a blue and
	// a red side.
	private static final class Side
	{
		String playerName;
		int obtained;
		int total;
		int rank;
		boolean rankTracked = true;
		TooltipItemSprites itemSprites;
		Set<Integer> obtainedIds;
		Map<Integer, Integer> obtainedCounts;
		boolean hasData = true;
	}

	private final Side blue = new Side();
	private final Side red = new Side();

	private List<Integer> allItemIds;
	private final TooltipItemHover itemHover = new TooltipItemHover(this);
	private boolean showSpriteGrids = true;
	private String comparisonStatLabel;
	private int blueComparisonStat = -1;
	private int redComparisonStat = -1;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBluePlayer(String name, int obtained, int total, int rank)
	{
		setBluePlayer(name, obtained, total, rank, true);
	}

	public void setBluePlayer(String name, int obtained, int total, int rank,
		boolean rankTracked)
	{
		setPlayer(blue, name, obtained, total, rank, rankTracked);
	}

	public void setRedPlayer(String name, int obtained, int total, int rank)
	{
		setRedPlayer(name, obtained, total, rank, true);
	}

	public void setRedPlayer(String name, int obtained, int total, int rank,
		boolean rankTracked)
	{
		setPlayer(red, name, obtained, total, rank, rankTracked);
	}

	private static void setPlayer(Side side, String name, int obtained, int total,
		int rank, boolean rankTracked)
	{
		side.playerName = name;
		side.obtained = obtained;
		side.total = total;
		side.rank = rank;
		side.rankTracked = rankTracked;
	}

	public void setBlueHasData(boolean hasData)
	{
		blue.hasData = hasData;
	}

	public void setRedHasData(boolean hasData)
	{
		red.hasData = hasData;
	}

	public void setShowSpriteGrids(boolean showSpriteGrids)
	{
		this.showSpriteGrids = showSpriteGrids;
	}

	public void setComparisonStat(String label, int blueValue, int redValue)
	{
		comparisonStatLabel = label;
		blueComparisonStat = blueValue;
		redComparisonStat = redValue;
	}

	public CompareImgTooltip()
	{
	}

	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
	}

	/**
	 * Load sprites and store both players' obtained sets.
	 * The compared boss or category supplies one shared item list.
	 */
	public void setItems(List<Integer> allItemIds,
		Set<Integer> blueObtainedIds, Map<Integer, Integer> blueObtainedCounts,
		Set<Integer> redObtainedIds, Map<Integer, Integer> redObtainedCounts,
		Map<Integer, String> itemNames,
		ItemManager itemManager)
	{
		this.allItemIds = allItemIds;
		blue.obtainedIds = blueObtainedIds;
		red.obtainedIds = redObtainedIds;
		blue.obtainedCounts = blueObtainedCounts;
		red.obtainedCounts = redObtainedCounts;

		if (allItemIds == null || allItemIds.isEmpty() || itemManager == null)
		{
			blue.itemSprites = null;
			red.itemSprites = null;
			itemHover.setHitBoxes(Collections.emptyList());
			itemHover.clear();
			return;
		}

		blue.itemSprites = loadPlayerSprites(allItemIds, itemNames,
			blue.obtainedIds, blue.obtainedCounts, itemManager);
		red.itemSprites = loadPlayerSprites(allItemIds, itemNames,
			red.obtainedIds, red.obtainedCounts, itemManager);
	}

	private TooltipItemSprites loadPlayerSprites(List<Integer> itemIds, Map<Integer, String> itemNames,
		Set<Integer> obtainedIds, Map<Integer, Integer> obtainedCounts,
		ItemManager itemManager)
	{
		return TooltipItemSprites.load(itemIds, itemNames, itemManager, SPRITE_SIZE,
			itemId -> spriteCountForItem(itemId, obtainedIds, obtainedCounts), this);
	}

	static int spriteCountForItem(int itemId, Set<Integer> obtainedIds,
		Map<Integer, Integer> obtainedCounts)
	{
		return obtainedIds != null && obtainedIds.contains(itemId) && obtainedCounts != null
			? obtainedCounts.getOrDefault(itemId, 1) : 1;
	}

	// Header.

	@Override
	int getHeaderHeight()
	{
		int h = 20; // title
		h += LINE_HEIGHT; // obtained
		if (showComparisonStat())
		{
			h += LINE_HEIGHT;
		}
		if (showRankLine())
		{
			h += LINE_HEIGHT;
		}
		return h;
	}

	@Override
	int paintHeader(Graphics2D g2, int w)
	{
		if (getTitle() == null)
		{
			return getInset();
		}

		int inset = getInset();
		Font titleFont = getTitleFont();
		g2.setFont(titleFont);
		FontMetrics nfm = g2.getFontMetrics();
		int lineY = inset + nfm.getAscent();

		// Title.
		g2.setColor(titleColor());
		g2.drawString(getTitle(), inset, lineY);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Obtained line.
		lineY += 20;
		int activeLineWidth = paintDualLine(g2, fm, inset, lineY, "Obtained: ",
			blueObtainedText(), redObtainedText());

		if (showComparisonStat())
		{
			lineY += LINE_HEIGHT;
			activeLineWidth = paintDualLine(g2, fm, inset, lineY, comparisonStatLabel,
				blueComparisonStatText(), redComparisonStatText(), true);
		}

		if (showRankLine())
		{
			lineY += LINE_HEIGHT;
			activeLineWidth = paintDualLine(g2, fm, inset, lineY, "Rank: ",
				formatRank(blue.rank, blue.rankTracked),
				formatRank(red.rank, red.rankTracked));
		}

		paintHeaderRightText(g2, fm, w, lineY, activeLineWidth);

		// Separator.
		int sepY = lineY + 6;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + 6;
	}

	// Orange label, blue value, chrome separator, red value. Returns the
	// painted line width from the inset.
	private int paintDualLine(Graphics2D g2, FontMetrics fm, int inset, int lineY,
		String label, String blueText, String redText)
	{
		return paintDualLine(g2, fm, inset, lineY, label, blueText, redText, false);
	}

	private int paintDualLine(Graphics2D g2, FontMetrics fm, int inset, int lineY,
		String label, String blueText, String redText, boolean dimDashes)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, inset, lineY);
		int x = inset + fm.stringWidth(label);

		g2.setColor(dimDashes ? compareValueColor(blueText, COMPARE_BLUE) : COMPARE_BLUE);
		g2.drawString(blueText, x, lineY);
		x += fm.stringWidth(blueText);

		x = paintChromeSeparator(g2, fm, x, lineY);

		g2.setColor(dimDashes ? compareValueColor(redText, COMPARE_RED) : COMPARE_RED);
		g2.drawString(redText, x, lineY);
		return x + fm.stringWidth(redText) - inset;
	}

	/**
	 * The compare header's per-player obtained text, shared by painting and
	 * sizing so both surfaces ride the one inherited progress formatter
	 * (unknown totals render "?", unknown obtained renders "--").
	 */
	/* package */ String blueObtainedText()
	{
		return progressCountTextOrDash(blue.obtained, blue.total);
	}

	/* package */ String redObtainedText()
	{
		return progressCountTextOrDash(red.obtained, red.total);
	}

	/* package */ boolean showComparisonStat()
	{
		return comparisonStatLabel != null
			&& (blueComparisonStat > 0 || redComparisonStat > 0);
	}

	/* package */ String blueComparisonStatText()
	{
		return scoreText(blueComparisonStat);
	}

	/* package */ String redComparisonStatText()
	{
		return scoreText(redComparisonStat);
	}

	private boolean showRankLine()
	{
		return blue.rankTracked || red.rankTracked;
	}

	private static String formatRank(int rank, boolean rankTracked)
	{
		if (!rankTracked)
		{
			return "--";
		}
		return rank > 0 ? "#" + String.format(Locale.US, "%,d", rank) : "Unranked";
	}

	// Sizing.

	@Override
	public Dimension getPreferredSize()
	{
		int inset = getInset();
		FontMetrics nfm = getFontMetrics(getTitleFont());
		FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());

		// Header width from title and dual-value lines.
		int titleW = getTitle() != null ? nfm.stringWidth(getTitle()) : 0;

		String obtLine = "Obtained: " + blueObtainedText()
			+ CHROME_SEPARATOR + redObtainedText();
		int headerLineW = sfm.stringWidth(obtLine);
		if (showComparisonStat())
		{
			String statLine = comparisonStatLabel + blueComparisonStatText()
				+ CHROME_SEPARATOR + redComparisonStatText();
			headerLineW = Math.max(headerLineW, sfm.stringWidth(statLine));
		}
		if (showRankLine())
		{
			String rnkLine = "Rank: " + formatRank(blue.rank, blue.rankTracked)
				+ CHROME_SEPARATOR + formatRank(red.rank, red.rankTracked);
			headerLineW = Math.max(headerLineW, sfm.stringWidth(rnkLine));
		}
		int headerMinWidth = Math.max(titleW, headerLineW);

		Dimension contentSize = getContentSize(Math.max(headerMinWidth, 1));
		int contentWidth = Math.max(headerMinWidth, contentSize.width);
		int totalHeight = inset + getHeaderZoneHeight() + contentSize.height + inset;
		int totalWidth = contentWidth + inset * 2;

		return new Dimension(totalWidth, totalHeight);
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		if (!showSpriteGrids)
		{
			return new Dimension(0, 0);
		}

		int cellSize = SPRITE_SIZE + PADDING;
		int cols = Math.max(GRID_COLS, (availableWidth + PADDING) / cellSize);
		int itemCount = allItemIds != null ? allItemIds.size() : 0;

		FontMetrics sfm = getFontMetrics(NAME_FONT);

		int h = sectionHeight(sfm, blue, itemCount, cols, cellSize)
			+ SECTION_GAP
			+ sectionHeight(sfm, red, itemCount, cols, cellSize);

		int gridWidth = cols * cellSize - PADDING;
		return new Dimension(gridWidth, h);
	}

	private static int sectionHeight(FontMetrics sfm, Side side, int itemCount,
		int cols, int cellSize)
	{
		int h = sfm.getHeight() + 2;
		if (side.hasData && itemCount > 0)
		{
			int rows = (itemCount + cols - 1) / cols;
			h += rows * cellSize - PADDING;
		}
		else
		{
			h += sfm.getHeight();
		}
		return h;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(Collections.emptyList());
		if (getTitle() == null || !showSpriteGrids)
		{
			return;
		}

		List<TooltipItemHover.HitBox> nextHitBoxes = new ArrayList<>();
		int inset = getInset();
		int cellSize = SPRITE_SIZE + PADDING;
		int cols = Math.max(GRID_COLS, (w - 2 * inset + PADDING) / cellSize);
		int gridWidth = cols * cellSize - PADDING;
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		FontMetrics sfm = g2.getFontMetrics(NAME_FONT);
		int y = startY;

		y = paintSection(g2, sfm, nextHitBoxes, blue, 0, "Blue", COMPARE_BLUE,
			inset, gridOffsetX, y, cols, cellSize);
		y += SECTION_GAP;
		paintSection(g2, sfm, nextHitBoxes, red, 1, "Red", COMPARE_RED,
			inset, gridOffsetX, y, cols, cellSize);

		itemHover.setHitBoxes(nextHitBoxes);
	}

	/**
	 * Paint one player's name and sprite grid. Returns the Y after the section.
	 */
	private int paintSection(Graphics2D g2, FontMetrics sfm,
		List<TooltipItemHover.HitBox> nextHitBoxes, Side side, int section,
		String fallbackName, Color playerColor,
		int inset, int gridOffsetX, int y, int cols, int cellSize)
	{
		g2.setFont(NAME_FONT);
		g2.setColor(playerColor);
		g2.drawString(side.playerName != null ? side.playerName : fallbackName,
			inset, y + sfm.getAscent());
		y += sfm.getHeight() + 2;

		if (side.hasData && side.itemSprites != null && allItemIds != null && !allItemIds.isEmpty())
		{
			return paintGrid(g2, nextHitBoxes, side.itemSprites, section, gridOffsetX, y,
				cols, cellSize, side.obtainedIds, side.obtainedCounts);
		}

		g2.setFont(NAME_FONT);
		g2.setColor(dim(playerColor));
		g2.drawString("--", inset, y + sfm.getAscent());
		return y + sfm.getHeight();
	}

	/**
	 * Paint a sprite grid for one player. Returns the Y after the last row.
	 */
	private int paintGrid(Graphics2D g2, List<TooltipItemHover.HitBox> nextHitBoxes,
		TooltipItemSprites sprites, int section, int gridOffsetX, int y, int cols,
		int cellSize, Set<Integer> obtainedIds, Map<Integer, Integer> obtainedCounts)
	{
		g2.setFont(FontManager.getRunescapeSmallFont());

		for (int i = 0; i < allItemIds.size(); i++)
		{
			int col = i % cols;
			int row = i / cols;
			int x = gridOffsetX + col * cellSize;
			int sy = y + row * cellSize;

			int itemId = allItemIds.get(i);
			boolean obtained = obtainedIds != null && obtainedIds.contains(itemId);
			nextHitBoxes.add(new TooltipItemHover.HitBox(section, itemId, itemNameAt(i),
				new Rectangle(x, sy, SPRITE_SIZE, SPRITE_SIZE), obtained));

			BufferedImage sprite = sprites.spriteAt(i);
			if (sprite != null)
			{
				g2.setComposite(obtained
					? AlphaComposite.SrcOver
					: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

				int sx = x + (SPRITE_SIZE - sprite.getWidth()) / 2;
				int ssy = sy + (SPRITE_SIZE - sprite.getHeight()) / 2;
				g2.drawImage(sprite, sx, ssy, null);
				g2.setComposite(AlphaComposite.SrcOver);
			}
		}

		int rows = (allItemIds.size() + cols - 1) / cols;
		return y + rows * cellSize - PADDING;
	}

	@Override
	protected String getHeaderRightText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getHeaderRightColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	private String itemNameAt(int index)
	{
		TooltipItemSprites sprites = blue.itemSprites != null ? blue.itemSprites : red.itemSprites;
		if (sprites == null)
		{
			return null;
		}
		return sprites.nameAt(index);
	}

}
