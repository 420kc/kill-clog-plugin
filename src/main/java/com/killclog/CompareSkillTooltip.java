package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/** Compact blue/red Kill Clog-native stat tooltip for one skill cell. */
public class CompareSkillTooltip extends TitleTooltip
{
	private static final int COLUMN_GAP = 10;
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);
	private static final int SECTION_SEPARATOR_PAD = 4;

	private SkillTooltip.Stats blue = SkillTooltip.Stats.empty();
	private SkillTooltip.Stats red = SkillTooltip.Stats.empty();
	private List<SkillClogSection> sections = Collections.emptyList();
	private boolean showRiftsClosed;
	private int blueRiftsClosed = -1;
	private int redRiftsClosed = -1;
	private final SkillClogSectionRenderer sectionRenderer = new SkillClogSectionRenderer(this);
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	public void setData(Skill skill, @Nullable HiscoreResult blueResult,
		@Nullable HiscoreResult redResult, boolean virtualLevels)
	{
		setData(skill, blueResult, redResult, virtualLevels, Collections.emptyList(), null);
	}

	public void setData(Skill skill, @Nullable HiscoreResult blueResult,
		@Nullable HiscoreResult redResult, boolean virtualLevels,
		List<SkillClogSection> sections, @Nullable ItemManager itemManager)
	{
		setTitle(skill.getName());
		clearTitleSuffix();
		clearSubtitle();
		clearInfoLine();
		blue = SkillTooltip.Stats.from(skill, blueResult, virtualLevels);
		red = SkillTooltip.Stats.from(skill, redResult, virtualLevels);
		this.sections = sections != null ? sections : Collections.emptyList();
		showRiftsClosed = false;
		blueRiftsClosed = -1;
		redRiftsClosed = -1;
		sectionRenderer.setSections(this.sections, itemManager);
		if (!this.sections.isEmpty())
		{
			SkillClogSection.Progress blueProgress = SkillClogSection.combinedProgress(
				this.sections, false);
			SkillClogSection.Progress redProgress = SkillClogSection.combinedProgress(
				this.sections, true);
			setInfoLine("Blue", wrappedProgressCountTextOrDash(
				blueProgress.obtained(), blueProgress.total()),
				progressColor(blueProgress));
			setInfoLinePair("Red", wrappedProgressCountTextOrDash(
				redProgress.obtained(), redProgress.total()),
				progressColor(redProgress));
		}
	}

	public void setRiftsClosed(int blueRiftsClosed, int redRiftsClosed)
	{
		showRiftsClosed = true;
		this.blueRiftsClosed = blueRiftsClosed;
		this.redRiftsClosed = redRiftsClosed;
		sectionRenderer.setRiftsClosed(blueRiftsClosed, redRiftsClosed);
	}

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int labelWidth = labelWidth(fm);
		int blueWidth = valueWidth(fm, blue, "Blue");
		int redWidth = valueWidth(fm, red, "Red");
		int width = labelWidth + blueWidth + COLUMN_GAP + redWidth;
		int height = LINE_HEIGHT * 5;
		if (!sections.isEmpty())
		{
			height += separatorHeight(SECTION_SEPARATOR_PAD);
			Dimension sectionSize = sectionRenderer.compareSize(Math.max(width, availableWidth));
			width = Math.max(width, sectionSize.width);
			height += sectionSize.height;
		}
		return new Dimension(width, height);
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

		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
		if (!sections.isEmpty())
		{
			int sectionY = paintSeparator(g2, w,
				startY + LINE_HEIGHT * 5, SECTION_SEPARATOR_PAD);
			sectionRenderer.paintCompare(g2, w, sectionY, hitBoxes);
		}
		itemHover.setHitBoxes(hitBoxes);
	}

	SkillTooltip.Stats blueStats()
	{
		return blue;
	}

	SkillTooltip.Stats redStats()
	{
		return red;
	}

	List<SkillClogSection> sections()
	{
		return sections;
	}

	boolean showsRiftsClosed()
	{
		return showRiftsClosed;
	}

	String blueRiftsClosedText()
	{
		return riftsClosedText(blueRiftsClosed);
	}

	String redRiftsClosedText()
	{
		return riftsClosedText(redRiftsClosed);
	}

	@Override
	protected boolean hasHeaderHoverLine()
	{
		return !sections.isEmpty();
	}

	@Override
	protected String getHeaderHoverLineText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getHeaderHoverLineColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	@Override
	protected String getHeaderHoverLineRightText()
	{
		return sectionRenderer.usesCompactSprites()
			? itemHover.hoveredDuplicateCountText() : null;
	}

	@Override
	protected Color getHeaderHoverLineRightColor()
	{
		return CLOG_YELLOW;
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

	private static Color progressColor(SkillClogSection.Progress progress)
	{
		return progress.obtained() >= 0
			? completionColor(progress.obtained(), progress.total()) : MUTED_GRAY;
	}

	private static String riftsClosedText(int riftsClosed)
	{
		return riftsClosed >= 0 ? String.format(java.util.Locale.US, "%,d", riftsClosed) : "--";
	}
}
