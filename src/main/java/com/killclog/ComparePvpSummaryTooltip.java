package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

/**
 * 3-column PvP comparison tooltip. Left column: labels (orange).
 * Middle column: blue player values. Right column: red player values.
 * LMS and Soul Wars rows include clog counts when available.
 */
public class ComparePvpSummaryTooltip extends TitleTooltip
{
	private static final int COL_GAP = 10;
	private static final int HEADER_GAP = 4;
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;


	// One player's column of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String name;
		int lms;
		int soulWars;
		int pvpArena;
		int bhHunter;
		int bhRogue;
		int lmsObt = -1;
		int lmsTotal;
		int swObt = -1;
		int swTotal;
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	private BufferedImage[] icons;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBlueData(String name, HiscoreResult hs, ClogResult clog)
	{
		setTitle("PvP Summary");
		setData(blue, name, hs, clog);
	}

	public void setRedData(String name, HiscoreResult hs, ClogResult clog)
	{
		setData(red, name, hs, clog);
	}

	public void setIcons(BufferedImage[] icons)
	{
		this.icons = icons;
	}

	private static void setData(Side side, String name, HiscoreResult hs, ClogResult clog)
	{
		side.name = name;
		if (hs != null)
		{
			side.lms = hs.getActivityScore("LMS - Rank");
			side.soulWars = hs.getActivityScore("Soul Wars Zeal");
			side.pvpArena = hs.getActivityScore("PvP Arena - Rank");
			side.bhHunter = hs.getActivityScore("Bounty Hunter - Hunter");
			side.bhRogue = hs.getActivityScore("Bounty Hunter - Rogue");
		}
		if (clog == null)
		{
			return;
		}
		int[] lms = ClogHelper.clogCounts("last_man_standing", clog);
		int[] sw = ClogHelper.clogCounts("soul_wars", clog);
		if (lms != null)
		{
			side.lmsObt = lms[0];
			side.lmsTotal = lms[1];
		}
		if (sw != null)
		{
			side.swObt = sw[0];
			side.swTotal = sw[1];
		}
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelW = measureLabelWidth(fm);
		int valueW = measureValueWidth(fm);
		int totalWidth = ICON_SIZE + ICON_GAP + labelW + COL_GAP + valueW + COL_GAP + valueW;
		int totalHeight = fm.getHeight() + HEADER_GAP + LINE_HEIGHT * 5;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureLabelWidth(FontMetrics fm)
	{
		int w = 0;
		w = Math.max(w, fm.stringWidth("LMS"));
		w = Math.max(w, fm.stringWidth("Soul Wars"));
		w = Math.max(w, fm.stringWidth("PvP Arena"));
		w = Math.max(w, fm.stringWidth("BH Hunter"));
		w = Math.max(w, fm.stringWidth("BH Rogue"));
		return w;
	}

	private int measureValueWidth(FontMetrics fm)
	{
		int w = 0;
		if (blue.name != null) w = Math.max(w, fm.stringWidth(blue.name));
		if (red.name != null) w = Math.max(w, fm.stringWidth(red.name));

		// Clog rows: score plus optional progress, measured from real values.
		w = Math.max(w, clogValueWidth(fm, blue.lms, blue.lmsObt, blue.lmsTotal));
		w = Math.max(w, clogValueWidth(fm, red.lms, red.lmsObt, red.lmsTotal));
		w = Math.max(w, clogValueWidth(fm, blue.soulWars, blue.swObt, blue.swTotal));
		w = Math.max(w, clogValueWidth(fm, red.soulWars, red.swObt, red.swTotal));

		// Simple rows: score only.
		int[] simple = {
			blue.pvpArena, red.pvpArena, blue.bhHunter, red.bhHunter, blue.bhRogue, red.bhRogue,
		};
		w = Math.max(w, widestValue(fm, simple, TitleTooltip::scoreText));
		return w;
	}

	private static int clogValueWidth(FontMetrics fm, int score, int obtained, int total)
	{
		int w = fm.stringWidth(scoreText(score));
		if (score > 0 && obtained >= 0)
		{
			w += wrappedProgressCountWidth(fm, obtained, total);
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

		// Rows.
		paintClogRow(g2, fm, iconX, labelX, blueX, redX, y, 0, "LMS",
			blue.lms, blue.lmsObt, blue.lmsTotal,
			red.lms, red.lmsObt, red.lmsTotal);
		y += LINE_HEIGHT;

		paintClogRow(g2, fm, iconX, labelX, blueX, redX, y, 1, "Soul Wars",
			blue.soulWars, blue.swObt, blue.swTotal,
			red.soulWars, red.swObt, red.swTotal);
		y += LINE_HEIGHT;

		paintSimpleRow(g2, fm, iconX, labelX, blueX, redX, y, 2,
			"PvP Arena", blue.pvpArena, red.pvpArena);
		y += LINE_HEIGHT;

		paintSimpleRow(g2, fm, iconX, labelX, blueX, redX, y, 3,
			"BH Hunter", blue.bhHunter, red.bhHunter);
		y += LINE_HEIGHT;

		paintSimpleRow(g2, fm, iconX, labelX, blueX, redX, y, 4,
			"BH Rogue", blue.bhRogue, red.bhRogue);
	}

	private void paintSimpleRow(Graphics2D g2, FontMetrics fm,
		int iconX, int labelX, int blueX, int redX, int y, int iconIndex,
		String label, int blueVal, int redVal)
	{
		paintIcon(g2, iconIndex, iconX, y);
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, labelX, y + fm.getAscent());
		String blueText = scoreText(blueVal);
		g2.setColor(compareValueColor(blueText, COMPARE_BLUE));
		g2.drawString(blueText, blueX, y + fm.getAscent());
		String redText = scoreText(redVal);
		g2.setColor(compareValueColor(redText, COMPARE_RED));
		g2.drawString(redText, redX, y + fm.getAscent());
	}

	private void paintClogRow(Graphics2D g2, FontMetrics fm,
		int iconX, int labelX, int blueX, int redX, int y, int iconIndex, String label,
		int blueScore, int blueObt, int blueTotal,
		int redScore, int redObt, int redTotal)
	{
		paintIcon(g2, iconIndex, iconX, y);
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, labelX, y + fm.getAscent());
		paintClogValue(g2, fm, blueX, y, blueScore, blueObt, blueTotal, COMPARE_BLUE);
		paintClogValue(g2, fm, redX, y, redScore, redObt, redTotal, COMPARE_RED);
	}

	private void paintClogValue(Graphics2D g2, FontMetrics fm, int x, int y,
		int score, int obtained, int total, Color playerColor)
	{
		int textY = y + fm.getAscent();
		String text = scoreText(score);
		g2.setColor(compareValueColor(text, playerColor));
		g2.drawString(text, x, textY);
		// Progress rides alongside a real score; a "--" value stays dash-only.
		if (score > 0 && obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, x + fm.stringWidth(text), textY, obtained, total);
		}
	}

	private void paintIcon(Graphics2D g2, int index, int iconX, int y)
	{
		BufferedImage icon = icon(index);
		if (icon == null)
		{
			return;
		}

		int ix = iconX + (ICON_SIZE - icon.getWidth()) / 2;
		int iy = y + (LINE_HEIGHT - icon.getHeight()) / 2;
		g2.drawImage(icon, ix, iy, null);
	}

	private BufferedImage icon(int index)
	{
		return icons != null && index >= 0 && index < icons.length ? icons[index] : null;
	}
}
