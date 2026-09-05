package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import lombok.Setter;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.FontManager;

/**
 * Clue summary tooltip on the Clue All cell.
 * Eight label:value lines: All, Beginner through Master, and Mimic kills.
 * Each line has a clue scroll icon on the left.
 */
public class ClueSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;

	private static final HiscoreSkill[] CLUE_TIERS = {
		HiscoreSkill.CLUE_SCROLL_ALL,
		HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
		HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
		HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
	};

	private static final String[] LABELS = {
		"All", "Beginner", "Easy", "Medium",
		"Hard", "Elite", "Master",
	};

	private final int[] scores = new int[7];
	private final int[] ranks = new int[7];
	private int mimicKc = -1;
	private int mimicRank = -1;

	@Setter
	private BufferedImage[] icons;

	/** A null result renders the full tier ladder with "--" scores: the empty state. */
	public void setData(HiscoreResult hiscoreResult, boolean showRank)
	{
		setTitle("Clue Summary");
		if (hiscoreResult == null)
		{
			return;
		}

		for (int i = 0; i < CLUE_TIERS.length; i++)
		{
			String name = CLUE_TIERS[i].getName();
			scores[i] = hiscoreResult.getActivityScore(name);
			ranks[i] = hiscoreResult.getActivityRank(name);
		}

		mimicKc = hiscoreResult.getKc("Mimic");
		mimicRank = hiscoreResult.getRank("Mimic");

		if (showRank)
		{
			setRank(ranks[0]);
		}
	}


	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		int iconCol = ICON_SIZE + ICON_GAP;
		int labelCol = 0;
		for (String label : LABELS)
		{
			labelCol = Math.max(labelCol, fm.stringWidth(label));
		}
		labelCol = Math.max(labelCol, fm.stringWidth("Mimic"));
		int scoreCol = measureScoreWidth(fm);
		int rankTail = measureRankTailWidth(fm);

		int totalWidth = iconCol + labelCol + COL_GAP + scoreCol + rankTail;

		return new Dimension(totalWidth, LINE_HEIGHT * 8);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Compute column positions.
		int iconCol = ICON_SIZE + ICON_GAP;
		int labelColW = 0;
		for (String label : LABELS)
		{
			labelColW = Math.max(labelColW, fm.stringWidth(label));
		}
		labelColW = Math.max(labelColW, fm.stringWidth("Mimic"));
		int scoreColW = measureScoreWidth(fm);

		int scoreRight = inset + iconCol + labelColW + COL_GAP + scoreColW;

		int y = startY;
		for (int i = 0; i < LABELS.length; i++)
		{
			paintLine(g2, fm, inset, y, icon(i), LABELS[i], scores[i], ranks[i],
				scoreRight);
			y += LINE_HEIGHT;
		}

		paintLine(g2, fm, inset, y, icon(7), "Mimic", mimicKc, mimicRank,
			scoreRight);
	}

	private BufferedImage icon(int index)
	{
		return icons != null && index < icons.length ? icons[index] : null;
	}

	private int measureScoreWidth(FontMetrics fm)
	{
		int width = fm.stringWidth("--");
		for (int score : scores)
		{
			width = Math.max(width, fm.stringWidth(scoreText(score)));
		}
		width = Math.max(width, fm.stringWidth(scoreText(mimicKc)));
		return width;
	}

	private int measureRankTailWidth(FontMetrics fm)
	{
		int width = 0;
		for (int rank : ranks)
		{
			width = Math.max(width, rankTailWidth(fm, rank));
		}
		width = Math.max(width, rankTailWidth(fm, mimicRank));
		return width;
	}

	private static int rankTailWidth(FontMetrics fm, int rank)
	{
		return rank > 0 ? 1 + fm.stringWidth(rankTailText(rank)) : 0;
	}

	private void paintLine(Graphics2D g2, FontMetrics fm, int inset, int y,
		BufferedImage icon, String label, int score, int rank,
		int scoreRight)
	{
		int x = inset;
		int textY = y + fm.getAscent();

		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - ICON_SIZE) / 2;
			g2.drawImage(icon, x, iconY, null);
		}
		x += ICON_SIZE + ICON_GAP;

		// Column 1: tier label.
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, x, textY);

		if (score <= 0)
		{
			// Column 2: no score.
			String scoreText = scoreText(score);
			g2.setColor(Color.WHITE);
			g2.drawString(scoreText, scoreRight - fm.stringWidth(scoreText), textY);
			return;
		}

		// Column 2: score.
		String scoreText = scoreText(score);
		g2.setColor(Color.WHITE);
		g2.drawString(scoreText, scoreRight - fm.stringWidth(scoreText), textY);

		if (rank > 0)
		{
			// Rank flows after the score column as " #1,234": the # stays orange,
			// the rank value is white. Widths match rankTailText so layout is unchanged.
			String rankPrefix = " #";
			String rankValue = String.format(Locale.US, "%,d", rank);
			int rankX = scoreRight + 1;
			g2.setColor(OSRS_ORANGE);
			g2.drawString(rankPrefix, rankX, textY);
			g2.setColor(Color.WHITE);
			g2.drawString(rankValue, rankX + fm.stringWidth(rankPrefix), textY);
		}
	}
}
