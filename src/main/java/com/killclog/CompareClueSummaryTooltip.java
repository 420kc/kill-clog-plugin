package com.killclog;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import lombok.Setter;
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

	// One player's column of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String name;
		final int[] scores = new int[8];
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	@Setter
	private BufferedImage[] icons;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBlueData(String name, HiscoreResult hs)
	{
		setTitle("Clue Summary");
		setData(blue, name, hs);
	}

	public void setRedData(String name, HiscoreResult hs)
	{
		setData(red, name, hs);
	}

	private static void setData(Side side, String name, HiscoreResult hs)
	{
		side.name = name;
		if (hs == null)
		{
			return;
		}
		for (int i = 0; i < HISCORE_NAMES.length; i++)
		{
			side.scores[i] = HISCORE_NAMES[i] != null
				? hs.getActivityScore(HISCORE_NAMES[i])
				: hs.getKc("Mimic");
		}
	}


	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelW = measureLabelWidth(fm);
		int valueW = Math.max(
			widestValue(fm, blue.scores, TitleTooltip::scoreText),
			widestValue(fm, red.scores, TitleTooltip::scoreText));
		if (blue.name != null) valueW = Math.max(valueW, fm.stringWidth(blue.name));
		if (red.name != null) valueW = Math.max(valueW, fm.stringWidth(red.name));
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
		g2.drawString(blue.name != null ? blue.name : "--", blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(red.name != null ? red.name : "--", redX, y + fm.getAscent());
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

			String blueText = scoreText(blue.scores[i]);
			g2.setColor(compareValueColor(blueText, COMPARE_BLUE));
			g2.drawString(blueText, blueX, y + fm.getAscent());

			String redText = scoreText(red.scores[i]);
			g2.setColor(compareValueColor(redText, COMPARE_RED));
			g2.drawString(redText, redX, y + fm.getAscent());

			y += LINE_HEIGHT;
		}
	}

	private BufferedImage icon(int index)
	{
		return icons != null && index >= 0 && index < icons.length ? icons[index] : null;
	}
}
