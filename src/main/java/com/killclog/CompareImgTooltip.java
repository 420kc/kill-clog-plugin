package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import javax.swing.SwingUtilities;

/**
 * Comparison mode sprite tooltip — stacked blue/red grids for the same boss or category.
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

	private static final Color COMPARE_BLUE = ComparisonController.COMPARE_BLUE;
	private static final Color COMPARE_RED = ComparisonController.COMPARE_RED;

	// Header data
	private String bluePlayerName;
	private int blueObtained;
	private int blueTotal;
	private int blueRank;

	private String redPlayerName;
	private int redObtained;
	private int redTotal;
	private int redRank;

	// Grid data
	private List<Integer> allItemIds;
	private BufferedImage[] sprites;

	private Set<Integer> blueObtainedIds;
	private Set<Integer> redObtainedIds;

	// Notice when a player has no clog data
	private boolean blueHasData = true;
	private boolean redHasData = true;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBluePlayer(String name, int obtained, int total, int rank)
	{
		this.bluePlayerName = name;
		this.blueObtained = obtained;
		this.blueTotal = total;
		this.blueRank = rank;
	}

	public void setRedPlayer(String name, int obtained, int total, int rank)
	{
		this.redPlayerName = name;
		this.redObtained = obtained;
		this.redTotal = total;
		this.redRank = rank;
	}

	public void setBlueHasData(boolean hasData)
	{
		this.blueHasData = hasData;
	}

	public void setRedHasData(boolean hasData)
	{
		this.redHasData = hasData;
	}

	/**
	 * Load sprites and store both players' obtained sets.
	 * Items are the same for both players (same boss = same items).
	 */
	public void setItems(List<Integer> allItemIds,
		Set<Integer> blueObtainedIds, Map<Integer, Integer> blueObtainedCounts,
		Set<Integer> redObtainedIds, Map<Integer, Integer> redObtainedCounts,
		ItemManager itemManager)
	{
		this.allItemIds = allItemIds;
		this.blueObtainedIds = blueObtainedIds;
		this.redObtainedIds = redObtainedIds;

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
			// Use count from whichever player has it, default 1
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

	// ------------------------------------------------------------------
	// Header: dual obtained/rank with blue | red coloring
	// ------------------------------------------------------------------

	@Override
	int getHeaderHeight()
	{
		// Title + obtained line + rank line
		int h = 20; // title
		h += LINE_HEIGHT; // obtained
		h += LINE_HEIGHT; // rank
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

		// Title (bold orange)
		g2.setColor(OSRS_ORANGE);
		g2.drawString(getTitle(), inset, lineY);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Obtained: 4/8 | 6/8
		lineY += 20;
		g2.setColor(OSRS_ORANGE);
		String obtLabel = "Obtained: ";
		g2.drawString(obtLabel, inset, lineY);
		int x = inset + fm.stringWidth(obtLabel);

		String blueObt = formatObtained(blueObtained, blueTotal);
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueObt, x, lineY);
		x += fm.stringWidth(blueObt);

		g2.setColor(Color.WHITE);
		g2.drawString(" | ", x, lineY);
		x += fm.stringWidth(" | ");

		String redObt = formatObtained(redObtained, redTotal);
		g2.setColor(COMPARE_RED);
		g2.drawString(redObt, x, lineY);

		// Rank: #400 | #529
		lineY += LINE_HEIGHT;
		g2.setColor(OSRS_ORANGE);
		String rnkLabel = "Rank: ";
		g2.drawString(rnkLabel, inset, lineY);
		x = inset + fm.stringWidth(rnkLabel);

		String blueRnk = formatRank(blueRank);
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueRnk, x, lineY);
		x += fm.stringWidth(blueRnk);

		g2.setColor(Color.WHITE);
		g2.drawString(" | ", x, lineY);
		x += fm.stringWidth(" | ");

		String redRnk = formatRank(redRank);
		g2.setColor(COMPARE_RED);
		g2.drawString(redRnk, x, lineY);

		// Separator
		int sepY = lineY + 6;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + 6;
	}

	private static String formatObtained(int obtained, int total)
	{
		return (obtained < 0 ? "?" : String.valueOf(obtained)) + "/" + total;
	}

	private static String formatRank(int rank)
	{
		return rank > 0 ? "#" + String.format("%,d", rank) : "Unranked";
	}

	// ------------------------------------------------------------------
	// Sizing
	// ------------------------------------------------------------------

	@Override
	public Dimension getPreferredSize()
	{
		int inset = getInset();
		FontMetrics nfm = getFontMetrics(getTitleFont());
		FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());

		// Header width from title and dual-value lines
		int titleW = getTitle() != null ? nfm.stringWidth(getTitle()) : 0;

		String obtLine = "Obtained: " + formatObtained(blueObtained, blueTotal)
			+ " | " + formatObtained(redObtained, redTotal);
		String rnkLine = "Rank: " + formatRank(blueRank) + " | " + formatRank(redRank);
		int headerMinWidth = Math.max(titleW,
			Math.max(sfm.stringWidth(obtLine), sfm.stringWidth(rnkLine)))
			+ getHeaderRightPad();

		Dimension contentSize = getContentSize(Math.max(headerMinWidth, 1));
		int contentWidth = Math.max(headerMinWidth, contentSize.width);
		int totalHeight = inset + getHeaderZoneHeight() + contentSize.height + inset;
		int totalWidth = contentWidth + inset * 2;

		return new Dimension(totalWidth, totalHeight);
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		int cellSize = SPRITE_SIZE + PADDING;
		int cols = Math.max(GRID_COLS, (availableWidth + PADDING) / cellSize);
		int itemCount = allItemIds != null ? allItemIds.size() : 0;

		FontMetrics sfm = getFontMetrics(NAME_FONT);

		int h = 0;

		// Blue section
		h += sfm.getHeight() + 2; // player name
		if (blueHasData && itemCount > 0)
		{
			int rows = (itemCount + cols - 1) / cols;
			h += rows * cellSize - PADDING;
		}
		else
		{
			h += sfm.getHeight(); // notice text
		}

		h += SECTION_GAP;

		// Red section
		h += sfm.getHeight() + 2; // player name
		if (redHasData && itemCount > 0)
		{
			int rows = (itemCount + cols - 1) / cols;
			h += rows * cellSize - PADDING;
		}
		else
		{
			h += sfm.getHeight(); // notice text
		}

		int gridWidth = cols * cellSize - PADDING;
		return new Dimension(gridWidth, h);
	}

	// ------------------------------------------------------------------
	// Body: blue grid on top, red grid on bottom
	// ------------------------------------------------------------------

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		if (getTitle() == null)
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

		// Blue player section
		g2.setFont(NAME_FONT);
		g2.setColor(COMPARE_BLUE);
		g2.drawString(bluePlayerName != null ? bluePlayerName : "Blue", inset, y + sfm.getAscent());
		y += sfm.getHeight() + 2;

		if (blueHasData && sprites != null && allItemIds != null && !allItemIds.isEmpty())
		{
			y = paintGrid(g2, gridOffsetX, y, cols, cellSize, blueObtainedIds);
		}
		else
		{
			g2.setFont(NAME_FONT);
			g2.setColor(NOTICE_COLOR);
			g2.drawString("No TempleOSRS Data", inset, y + sfm.getAscent());
			y += sfm.getHeight();
		}

		y += SECTION_GAP;

		// Red player section
		g2.setFont(NAME_FONT);
		g2.setColor(COMPARE_RED);
		g2.drawString(redPlayerName != null ? redPlayerName : "Red", inset, y + sfm.getAscent());
		y += sfm.getHeight() + 2;

		if (redHasData && sprites != null && allItemIds != null && !allItemIds.isEmpty())
		{
			paintGrid(g2, gridOffsetX, y, cols, cellSize, redObtainedIds);
		}
		else
		{
			g2.setFont(NAME_FONT);
			g2.setColor(NOTICE_COLOR);
			g2.drawString("No TempleOSRS Data", inset, y + sfm.getAscent());
		}
	}

	/**
	 * Paint a sprite grid for one player. Returns the Y after the last row.
	 */
	private int paintGrid(Graphics2D g2, int gridOffsetX, int y, int cols,
		int cellSize, Set<Integer> obtainedIds)
	{
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
