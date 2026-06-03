package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Comparison sprite tooltip: stacked blue/red grids for the same boss or category.
 * Header shows dual obtained/rank values, body shows both players' sprite grids.
 */
public class CompareImgTooltip extends TitleTooltip
{
	private static final int SPRITE_SIZE = 15;
	private static final int DEFAULT_SPRITE_SIZE = 32;
	private static final int PADDING = 4;
	private static final int GRID_COLS = 5;
	private static final int SECTION_GAP = 6;
	private static final Font NAME_FONT = FontManager.getRunescapeSmallFont();

	// Header data.
	private String bluePlayerName;
	private int blueObtained;
	private int blueTotal;
	private int blueRank;
	private boolean blueRankTracked = true;

	private String redPlayerName;
	private int redObtained;
	private int redTotal;
	private int redRank;
	private boolean redRankTracked = true;

	// Grid data.
	private List<Integer> allItemIds;
	private BufferedImage[] sprites;

	private Set<Integer> blueObtainedIds;
	private Set<Integer> redObtainedIds;

	// No-data flags.
	private boolean blueHasData = true;
	private boolean redHasData = true;
	private boolean showSpriteGrids = true;

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
		this.bluePlayerName = name;
		this.blueObtained = obtained;
		this.blueTotal = total;
		this.blueRank = rank;
		this.blueRankTracked = rankTracked;
	}

	public void setRedPlayer(String name, int obtained, int total, int rank)
	{
		setRedPlayer(name, obtained, total, rank, true);
	}

	public void setRedPlayer(String name, int obtained, int total, int rank,
		boolean rankTracked)
	{
		this.redPlayerName = name;
		this.redObtained = obtained;
		this.redTotal = total;
		this.redRank = rank;
		this.redRankTracked = rankTracked;
	}

	public void setBlueHasData(boolean hasData)
	{
		this.blueHasData = hasData;
	}

	public void setRedHasData(boolean hasData)
	{
		this.redHasData = hasData;
	}

	public void setShowSpriteGrids(boolean showSpriteGrids)
	{
		this.showSpriteGrids = showSpriteGrids;
	}

	private Map<Integer, Integer> blueObtainedCounts;
	private Map<Integer, Integer> redObtainedCounts;

	/**
	 * Load sprites and store both players' obtained sets.
	 * The compared boss or category supplies one shared item list.
	 */
	public void setItems(List<Integer> allItemIds,
		Set<Integer> blueObtainedIds, Map<Integer, Integer> blueObtainedCounts,
		Set<Integer> redObtainedIds, Map<Integer, Integer> redObtainedCounts,
		ItemManager itemManager)
	{
		this.allItemIds = allItemIds;
		this.blueObtainedIds = blueObtainedIds;
		this.redObtainedIds = redObtainedIds;
		this.blueObtainedCounts = blueObtainedCounts;
		this.redObtainedCounts = redObtainedCounts;

		if (allItemIds == null || allItemIds.isEmpty() || itemManager == null)
		{
			sprites = null;
			return;
		}

		sprites = new BufferedImage[allItemIds.size()];
		final BufferedImage[] localSprites = sprites;
		for (int i = 0; i < allItemIds.size(); i++)
		{
			int itemId = allItemIds.get(i);
			// Use whichever player's quantity is available.
			int count = 1;
			if (blueObtainedIds != null && blueObtainedIds.contains(itemId))
			{
				count = blueObtainedCounts.getOrDefault(itemId, 1);
			}
			else if (redObtainedIds != null && redObtainedIds.contains(itemId))
			{
				count = redObtainedCounts.getOrDefault(itemId, 1);
			}
			BufferedImage img = itemManager.getImage(itemId, count, false);
			final int idx = i;
			if (img instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) img).onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						if (localSprites != sprites) return;
						localSprites[idx] = resizeSprite(img);
						repaint();
					}));
			}
			localSprites[i] = resizeSprite(img);
		}
	}

	private static BufferedImage resizeSprite(BufferedImage img)
	{
		if (img == null)
		{
			return null;
		}
		return ImageUtil.resizeImage(
			ImageUtil.resizeCanvas(img, DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE),
			SPRITE_SIZE, SPRITE_SIZE);
	}

	// Header.

	@Override
	int getHeaderHeight()
	{
		int h = 20; // title
		h += LINE_HEIGHT; // obtained
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
		g2.setColor(OSRS_ORANGE);
		g2.drawString(getTitle(), inset, lineY);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Obtained line.
		lineY += 20;
		g2.setColor(OSRS_ORANGE);
		String obtLabel = "Obtained: ";
		g2.drawString(obtLabel, inset, lineY);
		int x = inset + fm.stringWidth(obtLabel);

		String blueObt = formatObtained(blueObtained, blueTotal);
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueObt, x, lineY);
		x += fm.stringWidth(blueObt);

		x = paintChromeSeparator(g2, fm, x, lineY);

		String redObt = formatObtained(redObtained, redTotal);
		g2.setColor(COMPARE_RED);
		g2.drawString(redObt, x, lineY);

		if (showRankLine())
		{
			lineY += LINE_HEIGHT;
			g2.setColor(OSRS_ORANGE);
			String rnkLabel = "Rank: ";
			g2.drawString(rnkLabel, inset, lineY);
			x = inset + fm.stringWidth(rnkLabel);

			String blueRnk = formatRank(blueRank, blueRankTracked);
			g2.setColor(COMPARE_BLUE);
			g2.drawString(blueRnk, x, lineY);
			x += fm.stringWidth(blueRnk);

			x = paintChromeSeparator(g2, fm, x, lineY);

			String redRnk = formatRank(redRank, redRankTracked);
			g2.setColor(COMPARE_RED);
			g2.drawString(redRnk, x, lineY);
		}

		// Separator.
		int sepY = lineY + 6;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + 6;
	}

	private static String formatObtained(int obtained, int total)
	{
		return (obtained < 0 ? "?" : String.valueOf(obtained)) + "/" + total;
	}

	private boolean showRankLine()
	{
		return blueRankTracked || redRankTracked;
	}

	private static String formatRank(int rank, boolean rankTracked)
	{
		if (!rankTracked)
		{
			return "--";
		}
		return rank > 0 ? "#" + String.format("%,d", rank) : "Unranked";
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

		String obtLine = "Obtained: " + formatObtained(blueObtained, blueTotal)
			+ CHROME_SEPARATOR + formatObtained(redObtained, redTotal);
		int headerLineW = sfm.stringWidth(obtLine);
		if (showRankLine())
		{
			String rnkLine = "Rank: " + formatRank(blueRank, blueRankTracked)
				+ CHROME_SEPARATOR + formatRank(redRank, redRankTracked);
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

		int h = 0;

		// Blue section.
		h += sfm.getHeight() + 2;
		if (blueHasData && itemCount > 0)
		{
			int rows = (itemCount + cols - 1) / cols;
			h += rows * cellSize - PADDING;
		}
		else
		{
			h += sfm.getHeight();
		}

		h += SECTION_GAP;

		// Red section.
		h += sfm.getHeight() + 2;
		if (redHasData && itemCount > 0)
		{
			int rows = (itemCount + cols - 1) / cols;
			h += rows * cellSize - PADDING;
		}
		else
		{
			h += sfm.getHeight();
		}

		int gridWidth = cols * cellSize - PADDING;
		return new Dimension(gridWidth, h);
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		if (getTitle() == null || !showSpriteGrids)
		{
			return;
		}

		int inset = getInset();
		int cellSize = SPRITE_SIZE + PADDING;
		int cols = Math.max(GRID_COLS, (w - 2 * inset + PADDING) / cellSize);
		int gridWidth = cols * cellSize - PADDING;
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		FontMetrics sfm = g2.getFontMetrics(NAME_FONT);
		int y = startY;

		// Blue section.
		g2.setFont(NAME_FONT);
		g2.setColor(COMPARE_BLUE);
		g2.drawString(bluePlayerName != null ? bluePlayerName : "Blue", inset, y + sfm.getAscent());
		y += sfm.getHeight() + 2;

		if (blueHasData && sprites != null && allItemIds != null && !allItemIds.isEmpty())
		{
			y = paintGrid(g2, gridOffsetX, y, cols, cellSize, blueObtainedIds, blueObtainedCounts);
		}
		else
		{
			g2.setFont(NAME_FONT);
			g2.setColor(NOTICE_COLOR);
			g2.drawString("No Collection Log Data", inset, y + sfm.getAscent());
			y += sfm.getHeight();
		}

		y += SECTION_GAP;

		// Red section.
		g2.setFont(NAME_FONT);
		g2.setColor(COMPARE_RED);
		g2.drawString(redPlayerName != null ? redPlayerName : "Red", inset, y + sfm.getAscent());
		y += sfm.getHeight() + 2;

		if (redHasData && sprites != null && allItemIds != null && !allItemIds.isEmpty())
		{
			y = paintGrid(g2, gridOffsetX, y, cols, cellSize, redObtainedIds, redObtainedCounts);
		}
		else
		{
			g2.setFont(NAME_FONT);
			g2.setColor(NOTICE_COLOR);
			g2.drawString("No Collection Log Data", inset, y + sfm.getAscent());
			y += sfm.getHeight();
		}

	}

	/**
	 * Paint a sprite grid for one player. Returns the Y after the last row.
	 */
	private int paintGrid(Graphics2D g2, int gridOffsetX, int y, int cols,
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

			BufferedImage sprite = i < sprites.length ? sprites[i] : null;
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
}
