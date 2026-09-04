package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.annotation.Nullable;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

/** Compact blue/red Kill Clog-native stat tooltip for one skill cell. */
public class CompareSkillTooltip extends TitleTooltip
{
	private static final int COLUMN_GAP = 10;
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	private SkillTooltip.Stats blue = SkillTooltip.Stats.empty();
	private SkillTooltip.Stats red = SkillTooltip.Stats.empty();

	public void setData(Skill skill, @Nullable HiscoreResult blueResult,
		@Nullable HiscoreResult redResult, boolean virtualLevels)
	{
		setTitle(skill.getName());
		blue = SkillTooltip.Stats.from(skill, blueResult, virtualLevels);
		red = SkillTooltip.Stats.from(skill, redResult, virtualLevels);
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelWidth = labelWidth(fm);
		int blueWidth = valueWidth(fm, blue, "Blue");
		int redWidth = valueWidth(fm, red, "Red");
		return new Dimension(labelWidth + blueWidth + COLUMN_GAP + redWidth,
			LINE_HEIGHT * 5);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int labelWidth = labelWidth(fm);
		int blueWidth = valueWidth(fm, blue, "Blue");
		int redWidth = valueWidth(fm, red, "Red");
		int blueX = getInset() + labelWidth;
		int redX = blueX + blueWidth + COLUMN_GAP;
		int y = startY + fm.getAscent();

		drawCentered(g2, fm, "Blue", blueX, blueWidth, y, COMPARE_BLUE);
		drawCentered(g2, fm, "Red", redX, redWidth, y, COMPARE_RED);
		y += LINE_HEIGHT;
		drawRow(g2, fm, y, SkillTooltip.LEVEL_LABEL,
			blue.levelText(), red.levelText(), blueX, redX);
		y += LINE_HEIGHT;
		drawRow(g2, fm, y, SkillTooltip.XP_LABEL,
			blue.xpText(), red.xpText(), blueX, redX);
		y += LINE_HEIGHT;
		drawRow(g2, fm, y, SkillTooltip.RANK_LABEL,
			blue.rankText(), red.rankText(), blueX, redX);
		y += LINE_HEIGHT;
		drawRow(g2, fm, y, SkillTooltip.XP_TO_LEVEL_LABEL,
			blue.xpToLevelText(), red.xpToLevelText(), blueX, redX);
	}

	SkillTooltip.Stats blueStats()
	{
		return blue;
	}

	SkillTooltip.Stats redStats()
	{
		return red;
	}

	private static void drawCentered(Graphics2D g2, FontMetrics fm, String text,
		int x, int width, int y, Color color)
	{
		g2.setColor(color);
		g2.drawString(text, x + (width - fm.stringWidth(text)) / 2, y);
	}

	private static void drawRow(Graphics2D g2, FontMetrics fm, int y, String label,
		String blueValue, String redValue, int blueX, int redX)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, getInset(), y);
		g2.setColor("--".equals(blueValue) ? UNRANKED_COLOR : COMPARE_BLUE);
		g2.drawString(blueValue, blueX, y);
		g2.setColor("--".equals(redValue) ? UNRANKED_COLOR : COMPARE_RED);
		g2.drawString(redValue, redX, y);
	}

	private static int labelWidth(FontMetrics fm)
	{
		int width = fm.stringWidth(SkillTooltip.LEVEL_LABEL);
		width = Math.max(width, fm.stringWidth(SkillTooltip.XP_LABEL));
		width = Math.max(width, fm.stringWidth(SkillTooltip.RANK_LABEL));
		return Math.max(width, fm.stringWidth(SkillTooltip.XP_TO_LEVEL_LABEL));
	}

	private static int valueWidth(FontMetrics fm, SkillTooltip.Stats stats, String heading)
	{
		int width = fm.stringWidth(heading);
		width = Math.max(width, fm.stringWidth(stats.levelText()));
		width = Math.max(width, fm.stringWidth(stats.xpText()));
		width = Math.max(width, fm.stringWidth(stats.rankText()));
		return Math.max(width, fm.stringWidth(stats.xpToLevelText()));
	}
}
