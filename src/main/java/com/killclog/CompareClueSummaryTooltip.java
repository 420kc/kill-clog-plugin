package com.killclog;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

/**
 * 3-column clue scroll comparison tooltip. Left column: tier labels (orange).
 * Middle column: blue player scores. Right column: red player scores.
 * 8 rows: All, Beginner through Master, and Mimic.
 */
public class CompareClueSummaryTooltip extends TitleTooltip
{
	private static final int COL_GAP = 10;
	private static final int HEADER_GAP = 4;
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;


	private static final String[] LABELS = {
		"All", "Beginner", "Easy", "Medium", "Hard", "Elite", "Master", "Mimic"
	};

	private static final String[] HISCORE_NAMES = {
		"Clue Scrolls (all)", "Clue Scrolls (beginner)", "Clue Scrolls (easy)",
		"Clue Scrolls (medium)", "Clue Scrolls (hard)", "Clue Scrolls (elite)",
		"Clue Scrolls (master)", null
	};

	private String blueName;
	private String redName;
	private final int[] blueScores = new int[8];
	private final int[] redScores = new int[8];
	private BufferedImage[] icons;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBlueData(String name, HiscoreResult hs)
	{
		setTitle("Clue Summary");
		this.blueName = name;
		if (hs != null)
		{
			for (int i = 0; i < HISCORE_NAMES.length; i++)
			{
				blueScores[i] = HISCORE_NAMES[i] != null
					? hs.getActivityScore(HISCORE_NAMES[i])
					: hs.getKc("Mimic");
			}
		}
	}

	public void setRedData(String name, HiscoreResult hs)
	{
		this.redName = name;
		if (hs != null)
		{
			for (int i = 0; i < HISCORE_NAMES.length; i++)
			{
				redScores[i] = HISCORE_NAMES[i] != null
					? hs.getActivityScore(HISCORE_NAMES[i])
					: hs.getKc("Mimic");
			}
		}
	}

	public void setIcons(BufferedImage[] icons)
	{
		this.icons = icons;
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelW = measureLabelWidth(fm);
		int valueW = fm.stringWidth("999,999");
		if (blueName != null) valueW = Math.max(valueW, fm.stringWidth(blueName));
		if (redName != null) valueW = Math.max(valueW, fm.stringWidth(redName));
		int totalWidth = ICON_SIZE + ICON_GAP + labelW + COL_GAP + valueW + COL_GAP + valueW;
		int totalHeight = fm.getHeight() + HEADER_GAP + LINE_HEIGHT * LABELS.length;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureLabelWidth(FontMetrics fm)
	{
		int w = 0;
		for (String label : LABELS)
		{
			w = Math.max(w, fm.stringWidth(label));
		}
		return w;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		int contentWidth = w - 2 * inset;
		FontMetrics fm = g2.getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelW = measureLabelWidth(fm);
		int iconColW = ICON_SIZE + ICON_GAP;
		int valueW = (contentWidth - iconColW - labelW - COL_GAP * 2) / 2;
		int iconX = inset;
		int labelX = inset + iconColW;
		int blueX = labelX + labelW + COL_GAP;
		int redX = blueX + valueW + COL_GAP;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		int y = startY;

		// Header.
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueName != null ? blueName : "--", blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(redName != null ? redName : "--", redX, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		// Tier rows.
		for (int i = 0; i < LABELS.length; i++)
		{
			BufferedImage icon = icon(i);
			if (icon != null)
			{
				int ix = iconX + (ICON_SIZE - icon.getWidth()) / 2;
				int iy = y + (LINE_HEIGHT - icon.getHeight()) / 2;
				g2.drawImage(icon, ix, iy, null);
			}

			g2.setColor(OSRS_ORANGE);
			g2.drawString(LABELS[i], labelX, y + fm.getAscent());

			g2.setColor(COMPARE_BLUE);
			g2.drawString(blueScores[i] > 0 ? String.format("%,d", blueScores[i]) : "--",
				blueX, y + fm.getAscent());

			g2.setColor(COMPARE_RED);
			g2.drawString(redScores[i] > 0 ? String.format("%,d", redScores[i]) : "--",
				redX, y + fm.getAscent());

			y += LINE_HEIGHT;
		}
	}

	private BufferedImage icon(int index)
	{
		return icons != null && index >= 0 && index < icons.length ? icons[index] : null;
	}
}
