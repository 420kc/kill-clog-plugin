package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/** Paints adaptive item sections inside solo and comparison skill tooltips. */
final class SkillClogSectionRenderer
{
	private static final int REGULAR_SPRITE_SIZE = 32;
	private static final int COMPACT_SPRITE_SIZE = 15;
	private static final int REGULAR_MAX_ITEMS = 29;
	private static final int PADDING = 4;
	private static final int REGULAR_SOLO_MIN_COLS = 5;
	private static final int COMPACT_SOLO_MIN_COLS = 7;
	private static final int REGULAR_COMPARE_COLS = 3;
	private static final int COMPARE_MIN_COLS = 6;
	private static final int COMPARE_GAP = 10;
	private static final int HEADER_GAP = 2;
	private static final int SECTION_GAP = 6;
	private static final String OBTAINED_LABEL = "Obtained: ";
	private static final Color QTY_COLOR = new Color(255, 255, 0);
	private static final Color QTY_SHADOW = new Color(0, 0, 0);
	private static final Font SECTION_FONT = FontManager.getRunescapeBoldFont();
	private static final Font DETAIL_FONT = FontManager.getRunescapeSmallFont();

	private final JComponent repaintTarget;
	private List<Entry> entries = Collections.emptyList();
	private boolean compactSprites;
	private int spriteSize = REGULAR_SPRITE_SIZE;
	private boolean showRiftsClosed;
	private int primaryRiftsClosed = -1;
	private int comparedRiftsClosed = -1;

	SkillClogSectionRenderer(JComponent repaintTarget)
	{
		this.repaintTarget = repaintTarget;
	}

	void setSections(List<SkillClogSection> sections, @Nullable ItemManager itemManager)
	{
		showRiftsClosed = false;
		primaryRiftsClosed = -1;
		comparedRiftsClosed = -1;
		if (sections == null || sections.isEmpty())
		{
			entries = Collections.emptyList();
			compactSprites = false;
			spriteSize = REGULAR_SPRITE_SIZE;
			return;
		}

		compactSprites = distinctItemCount(sections) > REGULAR_MAX_ITEMS;
		spriteSize = compactSprites ? COMPACT_SPRITE_SIZE : REGULAR_SPRITE_SIZE;
		List<Entry> next = new ArrayList<>();
		for (SkillClogSection section : sections)
		{
			TooltipItemSprites sprites = itemManager != null
				? TooltipItemSprites.load(section.itemIds(), section.itemNames(), itemManager,
					spriteSize, itemId -> 1, repaintTarget)
				: null;
			next.add(new Entry(section, sprites));
		}
		entries = Collections.unmodifiableList(next);
	}

	void setRiftsClosed(int primaryRiftsClosed, int comparedRiftsClosed)
	{
		showRiftsClosed = true;
		this.primaryRiftsClosed = primaryRiftsClosed;
		this.comparedRiftsClosed = comparedRiftsClosed;
	}

	boolean usesCompactSprites()
	{
		return compactSprites;
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
					SkillTooltip.RIFTS_CLOSED_LABEL + riftsClosedText(primaryRiftsClosed)));
			}
		}
		for (Entry entry : entries)
		{
			int cols = soloColumns(width, entry.section.itemIds().size());
			width = Math.max(width, gridWidth(cols));
		}

		int height = 0;
		for (Entry entry : entries)
		{
			int cols = soloColumns(width, entry.section.itemIds().size());
			if (entry.section.hasHeading())
			{
				height += headingMetrics.getHeight() + HEADER_GAP;
			}
			height += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(entry.section))
			{
				height += detailMetrics.getHeight() + HEADER_GAP;
			}
			height += gridHeight(entry.section.itemIds().size(), cols);
		}
		height += SECTION_GAP * (entries.size() - 1);
		return new Dimension(width, height);
	}

	Dimension compareSize(int availableWidth)
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
				width = Math.max(width, headingMetrics.stringWidth(entry.section.heading()));
			}
			width = Math.max(width, compareDetailWidth(detailMetrics,
				OBTAINED_LABEL,
				progressText(entry.section.primary(), entry.section.itemIds().size()),
				progressText(entry.section.compared(), entry.section.itemIds().size())));
			if (showsRiftsClosed(entry.section))
			{
				width = Math.max(width, compareDetailWidth(detailMetrics,
					SkillTooltip.RIFTS_CLOSED_LABEL,
					riftsClosedText(primaryRiftsClosed),
					riftsClosedText(comparedRiftsClosed)));
			}
		}
		for (Entry entry : entries)
		{
			int cols = compareColumns(width, entry.section.itemIds().size());
			width = Math.max(width, gridWidth(cols) * 2 + COMPARE_GAP);
		}

		int height = 0;
		for (Entry entry : entries)
		{
			int cols = compareColumns(width, entry.section.itemIds().size());
			if (entry.section.hasHeading())
			{
				height += headingMetrics.getHeight() + HEADER_GAP;
			}
			height += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(entry.section))
			{
				height += detailMetrics.getHeight() + HEADER_GAP;
			}
			height += gridHeight(entry.section.itemIds().size(), cols);
		}
		height += SECTION_GAP * (entries.size() - 1);
		return new Dimension(width, height);
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
				y += headingMetrics.getHeight() + HEADER_GAP;
			}
			g2.setFont(DETAIL_FONT);
			paintSoloProgress(g2, detailMetrics, section.primary(),
				section.itemIds().size(), inset, y + detailMetrics.getAscent());
			y += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(section))
			{
				paintSoloDetail(g2, detailMetrics, SkillTooltip.RIFTS_CLOSED_LABEL,
					riftsClosedText(primaryRiftsClosed), inset,
					y + detailMetrics.getAscent(), primaryRiftsClosed >= 0
						? Color.WHITE : TitleTooltip.MUTED_GRAY);
				y += detailMetrics.getHeight() + HEADER_GAP;
			}
			y = paintGrid(g2, entry, section.primary(), i, inset, availableWidth,
				y, cols, hitBoxes);
			if (i + 1 < entries.size())
			{
				y += SECTION_GAP;
			}
		}
		return y;
	}

	int paintCompare(Graphics2D g2, int width, int startY,
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
			int cols = compareColumns(availableWidth, section.itemIds().size());
			int alignedGridWidth = (availableWidth - COMPARE_GAP) / 2;
			int pairWidth = alignedGridWidth * 2 + COMPARE_GAP;
			int pairX = inset + (availableWidth - pairWidth) / 2;
			int redX = pairX + alignedGridWidth + COMPARE_GAP;

			if (section.hasHeading())
			{
				g2.setFont(SECTION_FONT);
				g2.setColor(TitleTooltip.OSRS_ORANGE);
				g2.drawString(section.heading(), inset, y + headingMetrics.getAscent());
				y += headingMetrics.getHeight() + HEADER_GAP;
			}

			g2.setFont(DETAIL_FONT);
			paintCompareDetail(g2, detailMetrics, OBTAINED_LABEL,
				progressText(section.primary(), section.itemIds().size()),
				progressText(section.compared(), section.itemIds().size()), inset,
				y + detailMetrics.getAscent(),
				section.primary().synced() ? TitleTooltip.COMPARE_BLUE : TitleTooltip.MUTED_GRAY,
				section.compared().synced() ? TitleTooltip.COMPARE_RED : TitleTooltip.MUTED_GRAY);
			y += detailMetrics.getHeight() + HEADER_GAP;
			if (showsRiftsClosed(section))
			{
				paintCompareDetail(g2, detailMetrics, SkillTooltip.RIFTS_CLOSED_LABEL,
					riftsClosedText(primaryRiftsClosed), riftsClosedText(comparedRiftsClosed),
					inset, y + detailMetrics.getAscent(),
					primaryRiftsClosed >= 0 ? TitleTooltip.COMPARE_BLUE : TitleTooltip.MUTED_GRAY,
					comparedRiftsClosed >= 0 ? TitleTooltip.COMPARE_RED : TitleTooltip.MUTED_GRAY);
				y += detailMetrics.getHeight() + HEADER_GAP;
			}

			int blueBottom = paintGridAt(g2, entry, section.primary(), i * 2,
				pairX, y, cols, hitBoxes);
			int redBottom = paintGridAt(g2, entry, section.compared(), i * 2 + 1,
				redX, y, cols, hitBoxes);
			y = Math.max(blueBottom, redBottom);
			if (i + 1 < entries.size())
			{
				y += SECTION_GAP;
			}
		}
		return y;
	}

	private int paintGrid(Graphics2D g2, Entry entry,
		SkillClogSection.PlayerItems playerItems, int sectionIndex,
		int inset, int availableWidth, int y, int cols,
		List<TooltipItemHover.HitBox> hitBoxes)
	{
		return paintGridAt(g2, entry, playerItems, sectionIndex, inset, y, cols, hitBoxes);
	}

	private int paintGridAt(Graphics2D g2, Entry entry,
		SkillClogSection.PlayerItems playerItems, int sectionIndex,
		int startX, int y, int cols, List<TooltipItemHover.HitBox> hitBoxes)
	{
		List<Integer> itemIds = entry.section.itemIds();
		g2.setFont(DETAIL_FONT);
		FontMetrics quantityMetrics = g2.getFontMetrics();
		int cellSize = cellSize();
		for (int i = 0; i < itemIds.size(); i++)
		{
			int x = startX + (i % cols) * cellSize;
			int spriteY = y + (i / cols) * cellSize;
			int itemId = itemIds.get(i);
			boolean obtained = playerItems.obtainedIds().contains(itemId);
			int count = obtained
				? playerItems.obtainedCounts().getOrDefault(itemId, 1) : 1;
			String itemName = TooltipItemLink.itemName(entry.section.itemNames(), itemId);
			hitBoxes.add(new TooltipItemHover.HitBox(sectionIndex, itemId, itemName,
				new Rectangle(x, spriteY, spriteSize, spriteSize), obtained, count));

			BufferedImage sprite = entry.sprites != null ? entry.sprites.spriteAt(i) : null;
			if (sprite != null)
			{
				g2.setComposite(obtained
					? AlphaComposite.SrcOver
					: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				int spriteX = x + (spriteSize - sprite.getWidth()) / 2;
				int centeredY = spriteY + (spriteSize - sprite.getHeight()) / 2;
				g2.drawImage(sprite, spriteX, centeredY, null);
				g2.setComposite(AlphaComposite.SrcOver);
			}

			if (obtained && count > 1 && !compactSprites)
			{
				String quantity = String.valueOf(count);
				g2.setColor(QTY_SHADOW);
				g2.drawString(quantity, x + 1,
					spriteY + quantityMetrics.getAscent() + 1);
				g2.setColor(QTY_COLOR);
				g2.drawString(quantity, x, spriteY + quantityMetrics.getAscent());
			}
		}
		return y + gridHeight(itemIds.size(), cols);
	}

	private static void paintSoloProgress(Graphics2D g2, FontMetrics fm,
		SkillClogSection.PlayerItems items, int total, int x, int y)
	{
		g2.setColor(TitleTooltip.OSRS_ORANGE);
		g2.drawString(OBTAINED_LABEL, x, y);
		String text = progressText(items, total);
		g2.setColor(progressColor(items, total));
		g2.drawString(text, x + fm.stringWidth(OBTAINED_LABEL), y);
	}

	private static void paintSoloDetail(Graphics2D g2, FontMetrics fm,
		String label, String value, int x, int y, Color valueColor)
	{
		g2.setColor(TitleTooltip.OSRS_ORANGE);
		g2.drawString(label, x, y);
		g2.setColor(valueColor);
		g2.drawString(value, x + fm.stringWidth(label), y);
	}

	private static void paintCompareDetail(Graphics2D g2, FontMetrics fm,
		String label, String primaryValue, String comparedValue, int x, int y,
		Color primaryColor, Color comparedColor)
	{
		g2.setColor(TitleTooltip.OSRS_ORANGE);
		g2.drawString(label, x, y);
		int valueX = x + fm.stringWidth(label);
		g2.setColor(primaryColor);
		g2.drawString(primaryValue, valueX, y);
		valueX += fm.stringWidth(primaryValue);
		g2.setColor(TitleTooltip.MUTED_GRAY);
		g2.drawString(TitleTooltip.CHROME_SEPARATOR, valueX, y);
		valueX += fm.stringWidth(TitleTooltip.CHROME_SEPARATOR);
		g2.setColor(comparedColor);
		g2.drawString(comparedValue, valueX, y);
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

	private static int compareDetailWidth(FontMetrics fm, String label,
		String primaryValue, String comparedValue)
	{
		return fm.stringWidth(label + primaryValue
			+ TitleTooltip.CHROME_SEPARATOR + comparedValue);
	}

	private boolean showsRiftsClosed(SkillClogSection section)
	{
		return showRiftsClosed && section.isCategory(PanelData.GOTR_CATEGORY);
	}

	private static String riftsClosedText(int riftsClosed)
	{
		return riftsClosed >= 0
			? String.format(java.util.Locale.US, "%,d", riftsClosed) : "--";
	}

	private int soloColumns(int availableWidth, int itemCount)
	{
		int minimum = compactSprites ? COMPACT_SOLO_MIN_COLS : REGULAR_SOLO_MIN_COLS;
		int fit = Math.max(minimum, (availableWidth + PADDING) / cellSize());
		return Math.min(fit, Math.max(itemCount, 1));
	}

	private int compareColumns(int availableWidth, int itemCount)
	{
		if (!compactSprites)
		{
			return Math.min(REGULAR_COMPARE_COLS, Math.max(itemCount, 1));
		}
		int halfWidth = Math.max(0, (availableWidth - COMPARE_GAP) / 2);
		int fit = Math.max(COMPARE_MIN_COLS, (halfWidth + PADDING) / cellSize());
		return Math.min(fit, Math.max(itemCount, 1));
	}

	private int gridWidth(int cols)
	{
		return cols * cellSize() - PADDING;
	}

	private int gridHeight(int itemCount, int cols)
	{
		int rows = (Math.max(itemCount, 1) + cols - 1) / cols;
		return rows * cellSize() - PADDING;
	}

	private int cellSize()
	{
		return spriteSize + PADDING;
	}

	private static int distinctItemCount(List<SkillClogSection> sections)
	{
		Set<Integer> itemIds = new HashSet<>();
		for (SkillClogSection section : sections)
		{
			itemIds.addAll(section.itemIds());
		}
		return itemIds.size();
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
