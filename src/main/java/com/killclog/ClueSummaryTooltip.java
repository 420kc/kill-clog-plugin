package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Clue Summary on the Clues cell, as the game's tiles: the six tiers, each its scroll and its log, then the rare
 * collections and Mimic, each its item and its log. Every tile opens its own card, as from its own cell, so every
 * clue is reached from the summary. The clues done are on the panel's cells and on each tier's card.
 */
public class ClueSummaryTooltip extends TitleTooltip
{
	static final int MIMIC = 7;
	// The Mimic's tile shows its casket.
	private static final int MIMIC_ITEM_ID = 23184;

	private static final String[] LABELS = {
		"All", "Beginner", "Easy", "Medium",
		"Hard", "Elite", "Master", "Mimic",
	};

	static final String[] RARE_LABELS = PanelData.RARE_ROWS.stream().map(RareRow::getLabel).toArray(String[]::new);

	// Collection progress per line, 1 to 6 the tiers and 7 Mimic; -1 obtained is unknown.
	private final int[] obtained = new int[LABELS.length];
	private final int[] total = new int[LABELS.length];
	// The hiscores' completions per tier and Mimic's kills, which a tile shows while its log progress is unknown.
	private final int[] scores = new int[LABELS.length];
	private final int[] rareObtained = new int[RARE_LABELS.length];
	private final int[] rareTotal = new int[RARE_LABELS.length];
	private final Image[] lineSprites = new Image[LABELS.length];
	private final Image[] rareSprites = new Image[RARE_LABELS.length];

	@Nullable
	private BiConsumer<MouseEvent, Integer> onOpenRare;
	@Nullable
	private BiConsumer<MouseEvent, Integer> onOpenLine;

	public ClueSummaryTooltip()
	{
		Arrays.fill(obtained, -1);
		Arrays.fill(scores, -1);
		Arrays.fill(rareObtained, -1);
	}

	/** The header's rank, the all-clues one, and each tier's completions; a null result is the empty state. */
	public void setData(HiscoreResult hiscoreResult, boolean showRank)
	{
		setTitle("Clue Summary");
		if (hiscoreResult != null)
		{
			for (int line = 1; line < MIMIC; line++)
			{
				scores[line] = hiscoreResult.getActivityScore(PanelData.CLUE_TIERS[line - 1].getName());
			}
			scores[MIMIC] = hiscoreResult.getKc("Mimic");
		}
		if (hiscoreResult != null && showRank && hiscoreResult.isRankDataAvailable())
		{
			setRank(hiscoreResult.getActivityRank(HiscoreSkill.CLUE_SCROLL_ALL.getName()));
		}
	}

	/** Collection progress for a line (1 to 6 the tiers, 7 Mimic); null counts leave it bare. */
	void setProgress(int line, @Nullable int[] counts)
	{
		obtained[line] = counts != null ? counts[0] : -1;
		total[line] = counts != null ? counts[1] : 0;
	}

	/** Each tile's item: a tier's scroll, the Mimic's casket, a rare collection's own. */
	void setSprites(@Nullable ItemManager itemManager)
	{
		for (int line = 1; line < LABELS.length; line++)
		{
			lineSprites[line] = itemSprite(line == MIMIC ? MIMIC_ITEM_ID : PanelData.CLUE_TIER_ITEM_IDS[line - 1], itemManager);
		}
		for (int i = 0; i < rareSprites.length; i++)
		{
			rareSprites[i] = itemSprite(PanelData.RARE_ROWS.get(i).getIconItemId(), itemManager);
		}
	}

	/** One rare collection; null counts show its tile without progress. */
	void setRare(int row, @Nullable int[] counts)
	{
		rareObtained[row] = counts != null ? counts[0] : -1;
		rareTotal[row] = counts != null ? counts[1] : 0;
	}

	/** Called with the line (1 to 6 the tiers, 7 Mimic) when the player presses one. */
	void setOnOpenLine(@Nullable BiConsumer<MouseEvent, Integer> onOpenLine)
	{
		this.onOpenLine = onOpenLine;
	}

	/** Called with the rare row index when the player presses one. */
	void setOnOpenRare(@Nullable BiConsumer<MouseEvent, Integer> onOpenRare)
	{
		this.onOpenRare = onOpenRare;
	}

	@Override
	protected CardBody body()
	{
		List<GameTile.Tile> lines = new ArrayList<>();
		for (int line = 1; line < MIMIC; line++)
		{
			lines.add(new GameTile.Tile(LABELS[line], lineSprites[line], obtained[line], total[line])
				.opens(onOpenLine, line).score(scores[line]));
		}
		List<GameTile.Tile> rares = new ArrayList<>();
		for (int i = 0; i < RARE_LABELS.length; i++)
		{
			rares.add(new GameTile.Tile(RARE_LABELS[i], rareSprites[i], rareObtained[i], rareTotal[i]).opens(onOpenRare, i));
		}
		// The Mimic's tile sits last among the rares: its log is a rare, its card a clue line's.
		rares.add(new GameTile.Tile(LABELS[MIMIC], lineSprites[MIMIC], obtained[MIMIC], total[MIMIC])
			.opens(onOpenLine, MIMIC).score(scores[MIMIC]));
		return new CardBody().add(GameTile.grid(lines)).titled("Rare Collections")
			.add(GameTile.grid(rares));
	}
}
