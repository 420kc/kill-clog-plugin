package com.killclog;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import lombok.*;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

/**
 * One skill's card: its level at the title's right after the skill's icon, the hiscores' Rank and XP and the XP to the
 * next level under the title with a bar of the way there, then the skill's log while Skill Clogs is on.
 */
public class SkillTooltip extends TitleTooltip
{
	static final String XP_LABEL = "XP: ";
	static final String RANK_LABEL = "Rank: ";
	static final String XP_TO_LEVEL_LABEL = "XP to Level: ";
	static final String RIFTS_CLOSED_LABEL = "Rifts Closed: ";

	private Stats stats = Stats.empty();
	private List<SkillClogSection> sections = Collections.emptyList();
	// One clog section's own count, as the game shows it for that activity: Runecraft's Rifts Closed in Guardians of
	// the Rift, Hunter's rumours. No category, no count.
	private String countCategory;
	private String countLabel;
	int count = -1;
	private final List<TooltipItemSprites> sprites = new ArrayList<>();
	// A skill's log scrolls at full size past the height of a long Collection Log tab, a sprite row a notch.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, CardBody.GRID_CELL);
	// The level at the header's right, as the Combat Summary shows its combat level: in the title's bold, after the
	// skill's icon from its cell, wearing the cell's color under the player's skill color setting.
	@Setter
	@Nullable
	private Image skillIcon;
	@Setter
	private Color levelColor = NativeTooltip.valueColor;

	public void setData(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels,
		List<SkillClogSection> sections, @Nullable ItemManager itemManager)
	{
		itemHover.clear();
		setTitle(skill.getName());
		clearSubtitle();
		stats = Stats.from(skill, result, virtualLevels);
		this.sections = sections != null ? sections : Collections.emptyList();
		countCategory = null;
		countLabel = null;
		count = -1;
		sprites.clear();
		for (SkillClogSection section : this.sections)
		{
			sprites.add(itemManager != null
				? TooltipItemSprites.load(section.itemIds(), section.itemNames(), itemManager, id -> 1, this) : null);
		}
		// The log's progress is its Obtained line under the title, as on every other log card.
		if (!this.sections.isEmpty())
		{
			SkillClogSection.Progress progress = SkillClogSection.combinedProgress(this.sections);
			setObtained(progress.obtained(), progress.total());
		}
	}

	/** The count the section for {@code category} shows under its Obtained line; -1 reads "--". */
	public void setSectionCount(String category, String label, int count)
	{
		countCategory = category;
		countLabel = label;
		this.count = count;
	}

	@Override
	protected CardBody body()
	{
		CardBody body = new CardBody().add(headerWord(stats.levelText(), skillIcon, getTitleFont(), levelColor))
			.add(CardBody.line(RANK_LABEL, stats.rankText(), stats.rankColor()))
			.add(CardBody.line(XP_LABEL, stats.xpText(), stats.xpColor()))
			.add(CardBody.line(XP_TO_LEVEL_LABEL, stats.xpToLevelText(), stats.xpToLevelColor()))
			// The way through the level, in the game's yellow, then its green once Maxed, as the line above turns.
			.add(CardBody.bar(stats.toNext(), stats.maxed ? CLOG_GREEN : CLOG_YELLOW));
		if (sections.isEmpty())
		{
			return body;
		}
		// The skill's clog sections fill the card's width under the band their hovered item is named in, each titled as a
		// card's section is.
		CardBody log = new CardBody();
		for (int i = 0; i < sections.size(); i++)
		{
			SkillClogSection section = sections.get(i);
			SkillClogSection.PlayerItems items = section.primary();
			int total = section.itemIds().size();
			log.add(CardBody.gap(i > 0 ? CardBody.SECTION_SPACE : 0));
			if (section.hasHeading())
			{
				log.add(CardBody.section(section.heading()));
			}
			log.add(CardBody.line("Obtained: ", progressCountText(items.obtainedCount(), total),
				progressColor(items.obtainedCount(), total)));
			if (countCategory != null && section.isCategory(countCategory))
			{
				log.add(CardBody.line(countLabel, count >= 0 ? grouped(count) : "--",
					count >= 0 ? NativeTooltip.valueColor : MUTED_GRAY));
			}
			log.add(grid(i, section, sprites.get(i)));
		}
		return body.add(CardBody.hoverBand()).add(CardBody.scroll(scroll, log.asPart()));
	}

	/** A section's items from the left in as many columns as the card holds, five at least, a set the catalog rows alone. */
	private static CardBody.Part grid(int index, SkillClogSection section, @Nullable TooltipItemSprites sprites)
	{
		List<Integer> ids = section.itemIds();
		ToIntFunction<CardBody.Ctx> cols = c -> Math.min(Math.max(CardBody.MIN_COLS, (c.available + CardBody.GRID_GAP) / CardBody.GRID_CELL),
			ids.size());
		return CardBody.part(c -> cols.applyAsInt(c) * CardBody.GRID_CELL - CardBody.GRID_GAP, c ->
		{
			int[] slots = section.slots(cols.applyAsInt(c));
			return (slots[slots.length - 1] / cols.applyAsInt(c) + 1) * CardBody.GRID_CELL - CardBody.GRID_GAP;
		}, (c, y) -> c.hits.addAll(TooltipItemSprites.paintGrid(c.g, sprites, section.itemNames(), index, ids,
			section.primary().obtainedIds(), section.primary().obtainedCounts(), c.inset(), y, cols.applyAsInt(c),
			CardBody.GRID_SPRITE, CardBody.GRID_CELL, section.slots(cols.applyAsInt(c)))));
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Stats
	{
		private final int level;
		private final long xp;
		private final int rank;
		private final long xpToLevel;
		private final boolean maxed;
		private final boolean rankAvailable;

		static Stats empty()
		{
			return new Stats(-1, -1, -1, -1, false, false);
		}

		static Stats from(Skill skill, @Nullable HiscoreResult result, boolean virtualLevels)
		{
			if (result == null)
			{
				return empty();
			}

			String key = skill.getName().toLowerCase(Locale.ROOT);
			long xp = result.getSkillXp(key);
			int level = SkillCellGrid.displayLevel(result.getSkillLevel(key), xp, virtualLevels);
			int rank = result.getSkillRank(key);
			int maxLevel = virtualLevels ? Experience.MAX_VIRT_LEVEL : Experience.MAX_REAL_LEVEL;
			boolean maxed = level >= maxLevel && xp >= 0;
			long xpToLevel = level > 0 && xp >= 0 && !maxed
				? Math.max(0L, (long) Experience.getXpForLevel(level + 1) - xp)
				: -1;
			return new Stats(level, xp, rank, xpToLevel, maxed, result.isRankDataAvailable());
		}

		String levelText()
		{
			return level > 0 ? String.valueOf(level) : "--";
		}

		String xpText()
		{
			return xp >= 0 ? grouped(xp) : "--";
		}

		String rankText()
		{
			return rank > 0 ? grouped(rank) : rankAvailable ? "Unranked" : "--";
		}

		/** The way from this level's XP to the next level's, 0 to 1: all of it once maxed, none while unknown. */
		double toNext()
		{
			if (maxed || level <= 0 || xp < 0)
			{
				return maxed ? 1 : 0;
			}
			long from = Experience.getXpForLevel(level);
			return (double) (xp - from) / (Experience.getXpForLevel(level + 1) - from);
		}

		String xpToLevelText()
		{
			if (maxed)
			{
				return "Maxed";
			}
			return xpToLevel >= 0 ? grouped(xpToLevel) : "--";
		}

		private Color xpColor()
		{
			return xp >= 0 ? NativeTooltip.valueColor : KillClogColors.UNRANKED_COLOR;
		}

		private Color rankColor()
		{
			return rank > 0 ? NativeTooltip.valueColor : KillClogColors.UNRANKED_COLOR;
		}

		private Color xpToLevelColor()
		{
			if (maxed)
			{
				return CLOG_GREEN;
			}
			return xpToLevel >= 0 ? NativeTooltip.valueColor : KillClogColors.UNRANKED_COLOR;
		}
	}
}
