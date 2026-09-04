package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

/** Compact Kill Clog-native stat tooltip for one skill cell. */
public class SkillTooltip extends TitleTooltip
{
	static final String LEVEL_LABEL = "Level: ";
	static final String XP_LABEL = "XP: ";
	static final String RANK_LABEL = "Rank: ";
	static final String XP_TO_LEVEL_LABEL = "XP to Level: ";
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	private Stats stats = Stats.empty();

	public void setData(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels)
	{
		setTitle(skill.getName());
		stats = Stats.from(skill, result, virtualLevels);
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
		return new Dimension(width, LINE_HEIGHT * 4);
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
	}

	Stats stats()
	{
		return stats;
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
