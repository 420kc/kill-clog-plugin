package com.killclog;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;
import lombok.Setter;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Clue Summary on the Clues cell. Eight lines, All through Mimic, each with its
 * score and rank and, for the six tiers and Mimic, collection progress. Below
 * them the rare collections, each a row that opens its own modal.
 */
public class ClueSummaryTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;
	private static final int MIMIC = 7;

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

	static final String[] RARE_LABELS = {
		"3rd Age", "Gilded", "Hard Rare", "Elite Rare", "Master Rare",
	};

	private final int[] scores = new int[8];
	private final int[] ranks = new int[8];
	// Collection progress per line; -1 obtained means none is shown.
	private final int[] obtained = new int[8];
	private final int[] total = new int[8];
	private final int[] rareObtained = new int[RARE_LABELS.length];
	private final int[] rareTotal = new int[RARE_LABELS.length];

	@Setter
	private BufferedImage[] icons;
	@Setter
	private BufferedImage[] rareIcons;
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

	/** One rare collection row; null counts show the row without progress. */
	void setRare(int row, @Nullable int[] counts)
	{
		rareObtained[row] = counts != null ? counts[0] : -1;
		rareTotal[row] = counts != null ? counts[1] : 0;
	}

	/** Called with the rare row index when the player presses one. */
	void setOnOpenRare(@Nullable ObjIntConsumer<MouseEvent> onOpenRare)
	{
		this.onOpenRare = onOpenRare;
	}

	int hoveredRare()
	{
		return rareRows.hovered();
	}

	/** The rare row under a y coordinate, or -1. Rows are full width. */
	int rareAt(int y)
	{
		return rareRows.at(y);
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
			.add(CardBody.table(icons, LABELS, scores, obtained, total, shownRanks, ICON_SIZE, ICON_GAP, COL_GAP))
			.titled("Rare Collections");
		for (int i = 0; i < RARE_LABELS.length; i++)
		{
			body.add(rareRow(i));
		}
		return body;
	}

	/** One rare collection; the row opens its own modal, its progress right-aligned. */
	private CardBody.Part rareRow(int row)
	{
		return CardBody.clickRow(rareRows, row, c -> ICON_SIZE + ICON_GAP + widest(c.fm, RARE_LABELS)
			+ progressWidth(c.fm, rareObtained, rareTotal), (c, y, hovered) ->
		{
			int textY = y + c.fm.getAscent();
			paintIcon(c.g, rareIcons, row, c.inset(), y);
			// No pointer cursor in this UI: the hovered row answers in white instead.
			c.g.setColor(hovered ? Color.WHITE : OSRS_ORANGE);
			c.g.drawString(RARE_LABELS[row], c.inset() + ICON_SIZE + ICON_GAP, textY);
			if (rareObtained[row] >= 0)
			{
				int width = wrappedProgressCountWidth(c.fm, rareObtained[row], rareTotal[row]);
				paintWrappedProgressCount(c.g, c.fm, c.w - c.inset() - width, textY, rareObtained[row], rareTotal[row]);
			}
		});
	}

	private static void paintIcon(Graphics2D g2, @Nullable BufferedImage[] set, int index, int x, int y)
	{
		BufferedImage icon = set != null && index < set.length ? set[index] : null;
		if (icon != null)
		{
			g2.drawImage(icon, x, y + (LINE_HEIGHT - ICON_SIZE) / 2, null);
		}
	}

	private static int progressWidth(FontMetrics fm, int[] obtained, int[] total)
	{
		int width = 0;
		for (int i = 0; i < obtained.length; i++)
		{
			if (obtained[i] >= 0)
			{
				width = Math.max(width, wrappedProgressCountWidth(fm, obtained[i], total[i]));
			}
		}
		return width;
	}

	private static int widest(FontMetrics fm, String[] labels)
	{
		int width = 0;
		for (String label : labels)
		{
			width = Math.max(width, fm.stringWidth(label));
		}
		return width;
	}
}
