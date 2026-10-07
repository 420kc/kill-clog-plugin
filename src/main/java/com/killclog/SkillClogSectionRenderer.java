package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/** Paints adaptive item sections inside solo and comparison skill tooltips. */
final class SkillClogSectionRenderer
{
	private static final int SPRITE_SIZE = 32;
	private static final int PADDING = 4;
	static final int CELL_SIZE = SPRITE_SIZE + PADDING;
	private static final int SOLO_MIN_COLS = 5;
	private static final int HEADER_GAP = 2;
	private static final String OBTAINED_LABEL = "Obtained: ";
	private static final Font SECTION_FONT = FontManager.getRunescapeBoldFont();
	private static final Font DETAIL_FONT = FontManager.getRunescapeSmallFont();

	private final SkillTooltip repaintTarget;
	private List<Entry> entries = Collections.emptyList();

	SkillClogSectionRenderer(SkillTooltip repaintTarget)
	{
		this.repaintTarget = repaintTarget;
	}

	void setSections(List<SkillClogSection> sections, @Nullable ItemManager itemManager)
	{
		if (sections == null || sections.isEmpty())
		{
			entries = Collections.emptyList();
			return;
		}

		List<Entry> next = new ArrayList<>();
		for (SkillClogSection section : sections)
		{
			TooltipItemSprites sprites = itemManager != null
				? TooltipItemSprites.load(section.itemIds(), section.itemNames(), itemManager,
					itemId -> 1, repaintTarget)
				: null;
			next.add(new Entry(section, sprites));
		}
		entries = Collections.unmodifiableList(next);
	}

	Dimension soloSize(int availableWidth)
	{
		if (entries.isEmpty())
		{
			return new Dimension(0, 0);
		}

		FontMetrics headingMetrics = repaintTarget.getFontMetrics(SECTION_FONT);
		FontMetrics detailMetrics = repaintTarget.getFontMetrics(DETAIL_FONT);
		int width = availableWidth;
		for (Entry entry : entries)
		{
			if (entry.section.hasHeading())
			{
				width = Math.max(width,
					headingMetrics.stringWidth(entry.section.heading()));
			}
			width = Math.max(width, soloProgressWidth(detailMetrics, entry.section));
			if (showsRiftsClosed(entry.section))
			{
				width = Math.max(width, detailMetrics.stringWidth(
					SkillTooltip.RIFTS_CLOSED_LABEL + repaintTarget.riftsClosedText()));
			}
		}
		for (Entry entry : entries)
		{
			int cols = soloColumns(width, entry.section.itemIds().size());
			width = Math.max(width, gridWidth(cols));
		}

		return new Dimension(width, sectionHeight(width, headingMetrics, detailMetrics));
	}

	private int sectionHeight(int width, FontMetrics headingMetrics, FontMetrics detailMetrics)
	{
		int height = 0;
		for (Entry entry : entries)
		{
			int cols = soloColumns(width, entry.section.itemIds().size());
			if (entry.section.hasHeading())
			{
				height += headingMetrics.getHeight() + HEADER_GAP + CardBody.SECTION_RULE + CardBody.SECTION_AFTER;
			}
			height += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(entry.section))
			{
				height += detailMetrics.getHeight() + HEADER_GAP;
			}
			height += gridHeight(entry.section.itemIds().size(), cols);
		}
		height += CardBody.SECTION_SPACE * Math.max(0, entries.size() - 1);
		return height;
	}

	int paintSolo(Graphics2D g2, int width, int startY,
		List<TooltipItemHover.HitBox> hitBoxes)
	{
		int y = startY;
		int inset = TitleTooltip.getInset();
		int availableWidth = width - inset * 2;
		FontMetrics headingMetrics = g2.getFontMetrics(SECTION_FONT);
		FontMetrics detailMetrics = g2.getFontMetrics(DETAIL_FONT);
		for (int i = 0; i < entries.size(); i++)
		{
			Entry entry = entries.get(i);
			SkillClogSection section = entry.section;
			int cols = soloColumns(availableWidth, section.itemIds().size());
			if (section.hasHeading())
			{
				g2.setFont(SECTION_FONT);
				g2.setColor(TitleTooltip.OSRS_ORANGE);
				g2.drawString(section.heading(), inset, y + headingMetrics.getAscent());
				// A heading carries its rule like a card's section title.
				CardBody.sectionRule(g2, y + headingMetrics.getHeight() + HEADER_GAP, inset, width);
				y += headingMetrics.getHeight() + HEADER_GAP + CardBody.SECTION_RULE + CardBody.SECTION_AFTER;
			}
			g2.setFont(DETAIL_FONT);
			TitleTooltip.drawLabelValue(g2, detailMetrics, inset, y + detailMetrics.getAscent(), OBTAINED_LABEL,
				progressText(section.primary(), section.itemIds().size()),
				progressColor(section.primary(), section.itemIds().size()));
			y += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(section))
			{
				TitleTooltip.drawLabelValue(g2, detailMetrics, inset, y + detailMetrics.getAscent(),
					SkillTooltip.RIFTS_CLOSED_LABEL, repaintTarget.riftsClosedText(),
					repaintTarget.riftsClosed >= 0 ? Color.WHITE : TitleTooltip.MUTED_GRAY);
				y += detailMetrics.getHeight() + HEADER_GAP;
			}
			y = paintGrid(g2, entry, section.primary(), i, inset, y, cols, hitBoxes);
			if (i + 1 < entries.size()) y += CardBody.SECTION_SPACE;
		}
		return y;
	}

	private int paintGrid(Graphics2D g2, Entry entry,
		SkillClogSection.PlayerItems playerItems, int sectionIndex,
		int startX, int y, int cols, List<TooltipItemHover.HitBox> hitBoxes)
	{
		List<Integer> itemIds = entry.section.itemIds();
		g2.setFont(DETAIL_FONT);
		hitBoxes.addAll(TooltipItemSprites.paintGrid(g2, entry.sprites, entry.section.itemNames(), sectionIndex,
			itemIds, playerItems.obtainedIds(), playerItems.obtainedCounts(), startX, y, cols, SPRITE_SIZE,
			CELL_SIZE));
		return y + gridHeight(itemIds.size(), cols);
	}

	static String progressText(SkillClogSection.PlayerItems items, int total)
	{
		return items.synced()
			? TitleTooltip.progressCountText(items.obtainedCount(), total)
			: TitleTooltip.progressPlaceholderText(total);
	}

	private static Color progressColor(SkillClogSection.PlayerItems items, int total)
	{
		return items.synced()
			? TitleTooltip.completionColor(items.obtainedCount(), total)
			: TitleTooltip.MUTED_GRAY;
	}

	private static int soloProgressWidth(FontMetrics fm, SkillClogSection section)
	{
		return fm.stringWidth(OBTAINED_LABEL
			+ progressText(section.primary(), section.itemIds().size()));
	}

	private boolean showsRiftsClosed(SkillClogSection section)
	{
		return repaintTarget.showsRiftsClosed() && section.isCategory(PanelData.GOTR_CATEGORY);
	}

	private int soloColumns(int availableWidth, int itemCount)
	{
		int fit = Math.max(SOLO_MIN_COLS, (availableWidth + PADDING) / CELL_SIZE);
		return Math.min(fit, Math.max(itemCount, 1));
	}

	private static int gridWidth(int cols)
	{
		return cols * CELL_SIZE - PADDING;
	}

	private static int gridHeight(int itemCount, int cols)
	{
		int rows = (Math.max(itemCount, 1) + cols - 1) / cols;
		return rows * CELL_SIZE - PADDING;
	}

	private static final class Entry
	{
		private final SkillClogSection section;
		@Nullable
		private final TooltipItemSprites sprites;

		private Entry(SkillClogSection section, @Nullable TooltipItemSprites sprites)
		{
			this.section = section;
			this.sprites = sprites;
		}
	}
}
