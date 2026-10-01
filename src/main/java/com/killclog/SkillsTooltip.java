package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import net.runelite.client.ui.FontManager;

/**
 * Skill Summary on the total level cell: total XP and overall rank.
 * Each skill's own numbers live in its cell, in the grid or the activity tray.
 */
public class SkillsTooltip extends TitleTooltip
{
	private static final String XP_ROW_LABEL = "XP: ";
	private static final String RANK_ROW_LABEL = "Rank: ";
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	private HiscoreResult result;

	public void setData(HiscoreResult result)
	{
		this.result = result;
		setTitle("Skill Summary");
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		return new Dimension(Math.max(
			fm.stringWidth(XP_ROW_LABEL + displayedXpText()),
			fm.stringWidth(RANK_ROW_LABEL + rankText())), 2 * LINE_HEIGHT);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int y = startY + fm.getAscent();
		drawLabelValue(g2, fm, getInset(), y, XP_ROW_LABEL, displayedXpText(),
			result != null && result.getTotalXp() >= 0 ? Color.WHITE : UNRANKED_COLOR);
		drawLabelValue(g2, fm, getInset(), y + LINE_HEIGHT, RANK_ROW_LABEL, rankText(),
			displayedRank() > 0 ? Color.WHITE : UNRANKED_COLOR);
	}

	String displayedXpText()
	{
		long xp = result != null ? result.getTotalXp() : -1;
		return xp >= 0 ? grouped(xp) : "--";
	}

	int displayedRank()
	{
		return result != null ? result.getOverallRank() : -1;
	}

	private String rankText()
	{
		int rank = displayedRank();
		return scoreText(rank);
	}
}
