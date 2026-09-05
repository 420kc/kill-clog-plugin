package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/** Compact Kill Clog-native stat tooltip for one skill cell. */
public class SkillTooltip extends TitleTooltip
{
	static final String LEVEL_LABEL = "Level: ";
	static final String XP_LABEL = "XP: ";
	static final String RANK_LABEL = "Rank: ";
	static final String XP_TO_LEVEL_LABEL = "XP to Level: ";
	static final String RIFTS_CLOSED_LABEL = "Rifts Closed: ";
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);
	private static final int SECTION_SEPARATOR_PAD = 4;

	private Stats stats = Stats.empty();
	private List<SkillClogSection> sections = Collections.emptyList();
	private boolean showRiftsClosed;
	private int riftsClosed = -1;
	private final SkillClogSectionRenderer sectionRenderer = new SkillClogSectionRenderer(this);
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	public void setData(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels)
	{
		setData(skill, result, virtualLevels, Collections.emptyList(), null);
	}

	public void setData(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels,
		List<SkillClogSection> sections, @Nullable ItemManager itemManager)
	{
		setTitle(skill.getName());
		clearTitleSuffix();
		clearSubtitle();
		stats = Stats.from(skill, result, virtualLevels);
		this.sections = sections != null ? sections : Collections.emptyList();
		showRiftsClosed = false;
		riftsClosed = -1;
		sectionRenderer.setSections(this.sections, itemManager);
		if (!this.sections.isEmpty())
		{
			SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
				this.sections, false);
			String progressText = progress.obtained() >= 0
				? progressCountText(progress.obtained(), progress.total())
				: progressPlaceholderText(progress.total());
			setTitleSuffix(" (" + progressText + ")",
				progress.obtained() >= 0
					? completionColor(progress.obtained(), progress.total()) : MUTED_GRAY);
		}
	}

	public void setRiftsClosed(int riftsClosed)
	{
		showRiftsClosed = true;
		this.riftsClosed = riftsClosed;
		sectionRenderer.setRiftsClosed(riftsClosed, -1);
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
		int width = 0;
		width = Math.max(width, rowWidth(fm, LEVEL_LABEL, stats.levelText()));
		width = Math.max(width, rowWidth(fm, XP_LABEL, stats.xpText()));
		width = Math.max(width, rowWidth(fm, RANK_LABEL, stats.rankText()));
		width = Math.max(width, rowWidth(fm, XP_TO_LEVEL_LABEL, stats.xpToLevelText()));
		int height = LINE_HEIGHT * 4;
		if (!sections.isEmpty())
		{
			height += separatorHeight(SECTION_SEPARATOR_PAD);
			Dimension sectionSize = sectionRenderer.soloSize(Math.max(width, availableWidth));
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
		int x = getInset();
		int y = startY + fm.getAscent();

		drawLabelValue(g2, fm, x, y, LEVEL_LABEL, stats.levelText(), stats.levelColor());
		y += LINE_HEIGHT;
		drawLabelValue(g2, fm, x, y, XP_LABEL, stats.xpText(), stats.xpColor());
		y += LINE_HEIGHT;
		drawLabelValue(g2, fm, x, y, RANK_LABEL, stats.rankText(), stats.rankColor());
		y += LINE_HEIGHT;
		drawLabelValue(g2, fm, x, y, XP_TO_LEVEL_LABEL,
			stats.xpToLevelText(), stats.xpToLevelColor());

		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
		if (!sections.isEmpty())
		{
			int sectionY = paintSeparator(g2, w,
				startY + LINE_HEIGHT * 4, SECTION_SEPARATOR_PAD);
			sectionRenderer.paintSolo(g2, w, sectionY, hitBoxes);
		}
		itemHover.setHitBoxes(hitBoxes);
	}

	Stats stats()
	{
		return stats;
	}

	List<SkillClogSection> sections()
	{
		return sections;
	}

	boolean showsRiftsClosed()
	{
		return showRiftsClosed;
	}

	String riftsClosedText()
	{
		return riftsClosed >= 0 ? String.format(Locale.US, "%,d", riftsClosed) : "--";
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

	private static int rowWidth(FontMetrics fm, String label, String value)
	{
		return fm.stringWidth(label) + fm.stringWidth(value);
	}

	static final class Stats
	{
		private final int level;
		private final long xp;
		private final int rank;
		private final long xpToLevel;
		private final boolean maxed;

		private Stats(int level, long xp, int rank, long xpToLevel, boolean maxed)
		{
			this.level = level;
			this.xp = xp;
			this.rank = rank;
			this.xpToLevel = xpToLevel;
			this.maxed = maxed;
		}

		static Stats empty()
		{
			return new Stats(-1, -1, -1, -1, false);
		}

		static Stats from(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels)
		{
			if (result == null)
			{
				return empty();
			}

			String key = skill.getName().toLowerCase(Locale.ROOT);
			long xp = result.getSkillXp(key);
			int level = ClogHelper.displayLevel(result.getSkillLevel(key), xp, virtualLevels);
			int rank = result.getSkillRank(key);
			int maxLevel = virtualLevels ? Experience.MAX_VIRT_LEVEL : Experience.MAX_REAL_LEVEL;
			boolean maxed = level >= maxLevel && xp >= 0;
			long xpToLevel = level > 0 && xp >= 0 && !maxed
				? Math.max(0L, (long) Experience.getXpForLevel(level + 1) - xp)
				: -1;
			return new Stats(level, xp, rank, xpToLevel, maxed);
		}

		String levelText()
		{
			return level > 0 ? String.valueOf(level) : "--";
		}

		String xpText()
		{
			return xp >= 0 ? format(xp) : "--";
		}

		String rankText()
		{
			return rank > 0 ? format(rank) : "--";
		}

		String xpToLevelText()
		{
			if (maxed)
			{
				return "Maxed";
			}
			return xpToLevel >= 0 ? format(xpToLevel) : "--";
		}

		private Color levelColor()
		{
			return level > 0 ? Color.WHITE : UNRANKED_COLOR;
		}

		private Color xpColor()
		{
			return xp >= 0 ? Color.WHITE : UNRANKED_COLOR;
		}

		private Color rankColor()
		{
			return rank > 0 ? Color.WHITE : UNRANKED_COLOR;
		}

		private Color xpToLevelColor()
		{
			if (maxed)
			{
				return CLOG_GREEN;
			}
			return xpToLevel >= 0 ? Color.WHITE : UNRANKED_COLOR;
		}

		private static String format(long value)
		{
			return String.format(Locale.US, "%,d", value);
		}
	}
}
