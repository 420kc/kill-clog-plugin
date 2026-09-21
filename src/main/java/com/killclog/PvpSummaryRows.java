package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import lombok.Setter;

/**
 * The five PvP activities as rows inside the Combat Summary: icon, label,
 * score, and collection progress where the activity has a log.
 */
final class PvpSummaryRows
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;

	private static final String[] LABELS = {
		"LMS", "Soul Wars", "PvP Arena", "BH Hunter", "BH Rogue",
	};

	private final int[] scores = new int[5];
	private final int[] obtained = new int[5];
	private final int[] total = new int[5];

	@Setter
	private BufferedImage[] icons;

	/** A null result renders all five rows with "--" scores: the empty state. */
	void setData(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		Arrays.fill(scores, 0);
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
			setProgress(0, ClogHelper.clogCounts("last_man_standing", clogResult));
			setProgress(1, ClogHelper.clogCounts("soul_wars", clogResult));
		}
	}

	private void setProgress(int row, int[] counts)
	{
		if (counts != null)
		{
			obtained[row] = counts[0];
			total[row] = counts[1];
		}
	}

	Dimension size(FontMetrics fm)
	{
		int progressTail = 0;
		for (int i = 0; i < LABELS.length; i++)
		{
			// Progress only rides alongside a real score; a "--" row stays dash-only.
			if (scores[i] > 0 && obtained[i] >= 0)
			{
				progressTail = Math.max(progressTail,
					TitleTooltip.wrappedProgressCountWidth(fm, obtained[i], total[i]));
			}
		}
		return new Dimension(scoreRight(fm, 0) + progressTail,
			TitleTooltip.LINE_HEIGHT * LABELS.length);
	}

	void paint(Graphics2D g2, FontMetrics fm, int inset, int y)
	{
		int scoreRight = scoreRight(fm, inset);
		for (int i = 0; i < LABELS.length; i++)
		{
			int textY = y + fm.getAscent();
			BufferedImage icon = icons != null && i < icons.length ? icons[i] : null;
			if (icon != null)
			{
				g2.drawImage(icon, inset, y + (TitleTooltip.LINE_HEIGHT - ICON_SIZE) / 2, null);
			}

			g2.setColor(TitleTooltip.OSRS_ORANGE);
			g2.drawString(LABELS[i], inset + ICON_SIZE + ICON_GAP, textY);

			g2.setColor(Color.WHITE);
			TitleTooltip.drawRightAligned(g2, fm, TitleTooltip.scoreText(scores[i]), scoreRight, textY);

			if (scores[i] > 0 && obtained[i] >= 0)
			{
				TitleTooltip.paintWrappedProgressCount(g2, fm, scoreRight, textY, obtained[i], total[i]);
			}
			y += TitleTooltip.LINE_HEIGHT;
		}
	}

	private int scoreRight(FontMetrics fm, int inset)
	{
		int labelCol = 0;
		for (String label : LABELS)
		{
			labelCol = Math.max(labelCol, fm.stringWidth(label));
		}
		return inset + ICON_SIZE + ICON_GAP + labelCol + COL_GAP
			+ TitleTooltip.widestValue(fm, scores, TitleTooltip::scoreText);
	}
}
