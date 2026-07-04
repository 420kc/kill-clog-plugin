package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.client.ui.FontManager;

/**
 * PvP summary tooltip on the skull cell.
 * Five rows: LMS and Soul Wars (with clog progress), then PvP Arena,
 * BH Hunter, BH Rogue. Scores right-align into a measured column so the
 * right edge stays clean at any value magnitude; clog progress flows after.
 */
public class PvpSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;

	private static final String[] LABELS = {
		"LMS", "Soul Wars", "PvP Arena", "Bounty Hunter", "BH Rogue",
	};

	private final int[] scores = new int[5];
	private final int[] obtained = new int[5];
	private final int[] total = new int[5];

	private BufferedImage[] icons;

	/** A null result renders all five rows with "--" scores: the empty state. */
	public void setData(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		setTitle("PvP Summary");
		Arrays.fill(obtained, -1);
		if (hiscoreResult == null)
		{
			return;
		}

		scores[0] = hiscoreResult.getActivityScore("LMS - Rank");
		scores[1] = hiscoreResult.getActivityScore("Soul Wars Zeal");
		scores[2] = hiscoreResult.getActivityScore("PvP Arena - Rank");
		scores[3] = hiscoreResult.getActivityScore("Bounty Hunter - Hunter");
		scores[4] = hiscoreResult.getActivityScore("Bounty Hunter - Rogue");

		if (clogResult != null)
		{
			int[] lms = ClogHelper.clogCounts("last_man_standing", clogResult);
			if (lms != null)
			{
				obtained[0] = lms[0];
				total[0] = lms[1];
			}
			int[] sw = ClogHelper.clogCounts("soul_wars", clogResult);
			if (sw != null)
			{
				obtained[1] = sw[0];
				total[1] = sw[1];
			}
		}
	}

	public void setIcons(BufferedImage[] icons)
	{
		this.icons = icons;
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		int scoreCol = widestValue(fm, scores, TitleTooltip::scoreText);
		int progressTail = 0;
		for (int i = 0; i < LABELS.length; i++)
		{
			// Progress only rides alongside a real score; a "--" row stays dash-only.
			if (scores[i] > 0 && obtained[i] >= 0)
			{
				progressTail = Math.max(progressTail, wrappedProgressCountWidth(fm, obtained[i], total[i]));
			}
		}

		int totalWidth = ICON_SIZE + ICON_GAP + labelCol(fm) + COL_GAP + scoreCol + progressTail;
		return new Dimension(totalWidth, LINE_HEIGHT * LABELS.length);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		int scoreCol = widestValue(fm, scores, TitleTooltip::scoreText);
		int scoreRight = inset + ICON_SIZE + ICON_GAP + labelCol(fm) + COL_GAP + scoreCol;

		int y = startY;
		for (int i = 0; i < LABELS.length; i++)
		{
			paintLine(g2, fm, inset, y, icon(i), LABELS[i], scores[i], obtained[i], total[i], scoreRight);
			y += LINE_HEIGHT;
		}
	}

	private static int labelCol(FontMetrics fm)
	{
		int width = 0;
		for (String label : LABELS)
		{
			width = Math.max(width, fm.stringWidth(label));
		}
		return width;
	}

	private BufferedImage icon(int index)
	{
		return icons != null && index < icons.length ? icons[index] : null;
	}

	private void paintLine(Graphics2D g2, FontMetrics fm, int inset, int y,
		BufferedImage icon, String label, int score, int obtained, int total, int scoreRight)
	{
		int textY = y + fm.getAscent();

		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - ICON_SIZE) / 2;
			g2.drawImage(icon, inset, iconY, null);
		}

		// Column 1: activity label.
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, inset + ICON_SIZE + ICON_GAP, textY);

		// Column 2: score, right-aligned.
		String scoreText = scoreText(score);
		g2.setColor(Color.WHITE);
		drawRightAligned(g2, fm, scoreText, scoreRight, textY);

		// Clog progress flows after a real score; a "--" row stays dash-only.
		if (score > 0 && obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, scoreRight, textY, obtained, total);
		}
	}
}
