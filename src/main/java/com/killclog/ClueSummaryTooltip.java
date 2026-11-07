package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Clue Summary on the Clues cell. Eight lines, All through Mimic, each with its
 * score and rank and, for the six tiers and Mimic, collection progress. Below
 * them the rare collections as the game's tiles, each opening its own modal. The six tiers
 * and Mimic open theirs the same way (Dylan, 2026-10-17: every clue reached from
 * the summary as from its own cell, and nothing passed over without answering).
 */
public class ClueSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;
	static final int MIMIC = 7;

	private static final HiscoreSkill[] CLUE_TIERS = {
		HiscoreSkill.CLUE_SCROLL_ALL,
		HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
		HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
		HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
	};

	private static final String[] LABELS = {
		"All", "Beginner", "Easy", "Medium",
		"Hard", "Elite", "Master", "Mimic",
	};

	static final String[] RARE_LABELS = PanelData.RARE_ROWS.stream().map(RareRow::getLabel).toArray(String[]::new);

	private final int[] scores = new int[8];
	private final int[] ranks = new int[8];
	// Collection progress per line; -1 obtained means none is shown.
	private final int[] obtained = new int[8];
	private final int[] total = new int[8];
	private final int[] rareObtained = new int[RARE_LABELS.length];
	private final int[] rareTotal = new int[RARE_LABELS.length];

	@Setter
	private BufferedImage[] icons;
	private final Image[] rareSprites = new Image[RARE_LABELS.length];
	@Nullable
	private ObjIntConsumer<MouseEvent> onOpenRare;
	private final CardBody.ClickRows rareRows = new CardBody.ClickRows(this, (e, row) ->
	{
		if (onOpenRare != null)
		{
			onOpenRare.accept(e, row);
			e.consume();
		}
	});
	@Nullable
	private ObjIntConsumer<MouseEvent> onOpenLine;
	private final CardBody.ClickRows lineRows = new CardBody.ClickRows(this, (e, line) ->
	{
		if (onOpenLine != null)
		{
			onOpenLine.accept(e, line);
			e.consume();
		}
	});

	public ClueSummaryTooltip()
	{
		Arrays.fill(scores, -1);
		Arrays.fill(ranks, -1);
		Arrays.fill(obtained, -1);
		Arrays.fill(rareObtained, -1);
	}

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
		scores[MIMIC] = hiscoreResult.getKc("Mimic");
		ranks[MIMIC] = hiscoreResult.getRank("Mimic");

		if (showRank && hiscoreResult.isRankDataAvailable())
		{
			setRank(ranks[0]);
		}
	}

	/** Collection progress for a line (1 to 6 the tiers, 7 Mimic); null counts leave it bare. */
	void setProgress(int line, @Nullable int[] counts)
	{
		obtained[line] = counts != null ? counts[0] : -1;
		total[line] = counts != null ? counts[1] : 0;
	}

	/** Each rare collection's item, for its tile. */
	void setRareSprites(@Nullable ItemManager itemManager)
	{
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
	void setOnOpenLine(@Nullable ObjIntConsumer<MouseEvent> onOpenLine)
	{
		this.onOpenLine = onOpenLine;
	}

	/** Called with the rare row index when the player presses one. */
	void setOnOpenRare(@Nullable ObjIntConsumer<MouseEvent> onOpenRare)
	{
		this.onOpenRare = onOpenRare;
	}

	@Override
	protected CardBody body()
	{
		// Ranks ride in their own column, so they stay aligned down the card.
		int[] shownRanks = new int[ranks.length];
		for (int i = 0; i < ranks.length; i++)
		{
			shownRanks[i] = scores[i] > 0 && ranks[i] > 0 ? ranks[i] : 0;
		}
		CardBody body = new CardBody()
			.add(CardBody.table(icons, LABELS, scores, obtained, total, shownRanks, ICON_SIZE, ICON_GAP, COL_GAP,
				lineRows, line -> line > 0))
			.titled("Rare Collections");
		List<GameTile.Tile> rares = new ArrayList<>();
		for (int i = 0; i < RARE_LABELS.length; i++)
		{
			rares.add(new GameTile.Tile(RARE_LABELS[i], rareSprites[i], rareObtained[i], rareTotal[i],
				onOpenRare != null ? i : -1));
		}
		return body.add(GameTile.grid(rareRows, rares));
	}
}
