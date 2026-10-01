package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.ObjIntConsumer;
import javax.annotation.Nullable;
import lombok.Setter;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.FontManager;

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
	private static final int SECTION_PAD = 2;
	private static final int SUBHEADER_HEIGHT = 16;
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
	private int rareTop = -1;
	private int hoveredRare = -1;

	@Setter
	private BufferedImage[] icons;
	@Setter
	private BufferedImage[] rareIcons;
	@Nullable
	private ObjIntConsumer<MouseEvent> onOpenRare;

	public ClueSummaryTooltip()
	{
		Arrays.fill(scores, -1);
		Arrays.fill(ranks, -1);
		Arrays.fill(obtained, -1);
		Arrays.fill(rareObtained, -1);
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHoveredRare(rareAt(e.getY()));
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				int row = rareAt(e.getY());
				if (row >= 0 && onOpenRare != null && e.getButton() == MouseEvent.BUTTON1)
				{
					onOpenRare.accept(e, row);
					e.consume();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setHoveredRare(-1);
			}
		});
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
		return hoveredRare;
	}

	/** The rare row under a y coordinate, or -1. Rows are full width. */
	int rareAt(int y)
	{
		if (rareTop < 0 || y < rareTop)
		{
			return -1;
		}
		int row = (y - rareTop) / LINE_HEIGHT;
		return row < RARE_LABELS.length ? row : -1;
	}

	private void setHoveredRare(int row)
	{
		if (hoveredRare != row)
		{
			hoveredRare = row;
			repaint();
		}
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int lineWidth = scoreRight(fm, 0) + progressCol(fm) + rankCol(fm);
		int rareWidth = ICON_SIZE + ICON_GAP + widest(fm, RARE_LABELS)
			+ progressWidth(fm, rareObtained, rareTotal);
		int bold = getFontMetrics(FontManager.getRunescapeBoldFont()).stringWidth("Rare Collections");
		return new Dimension(Math.max(Math.max(lineWidth, rareWidth), bold),
			LINE_HEIGHT * LABELS.length + separatorHeight(SECTION_PAD)
				+ SUBHEADER_HEIGHT + LINE_HEIGHT * RARE_LABELS.length);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int scoreRight = scoreRight(fm, inset);
		int rankX = scoreRight + progressCol(fm);

		int y = startY;
		for (int i = 0; i < LABELS.length; i++)
		{
			paintLine(g2, fm, inset, y, i, scoreRight, rankX);
			y += LINE_HEIGHT;
		}

		y = paintSeparator(g2, w, y, SECTION_PAD);
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Rare Collections", inset, y + g2.getFontMetrics().getAscent());
		y += SUBHEADER_HEIGHT;

		g2.setFont(FontManager.getRunescapeSmallFont());
		rareTop = y;
		for (int i = 0; i < RARE_LABELS.length; i++)
		{
			paintRare(g2, fm, inset, y, w, i);
			y += LINE_HEIGHT;
		}
	}

	private void paintLine(Graphics2D g2, FontMetrics fm, int inset, int y, int line,
		int scoreRight, int rankX)
	{
		int textY = y + fm.getAscent();
		paintIcon(g2, icons, line, inset, y);

		g2.setColor(OSRS_ORANGE);
		g2.drawString(LABELS[line], inset + ICON_SIZE + ICON_GAP, textY);

		g2.setColor(Color.WHITE);
		drawRightAligned(g2, fm, scoreText(scores[line]), scoreRight, textY);

		// Progress rides in its own column, so ranks stay aligned down the card.
		if (obtained[line] >= 0)
		{
			paintWrappedProgressCount(g2, fm, scoreRight, textY, obtained[line], total[line]);
		}

		if (scores[line] > 0 && ranks[line] > 0)
		{
			String rankPrefix = " #";
			drawLabelValue(g2, fm, rankX + 1, textY, rankPrefix, String.format(Locale.US, "%,d", ranks[line]));
		}
	}

	private void paintRare(Graphics2D g2, FontMetrics fm, int inset, int y, int w, int row)
	{
		int textY = y + fm.getAscent();
		paintIcon(g2, rareIcons, row, inset, y);

		// No pointer cursor in this UI: the hovered row answers in white instead.
		g2.setColor(row == hoveredRare ? Color.WHITE : OSRS_ORANGE);
		g2.drawString(RARE_LABELS[row], inset + ICON_SIZE + ICON_GAP, textY);

		if (rareObtained[row] >= 0)
		{
			int width = wrappedProgressCountWidth(fm, rareObtained[row], rareTotal[row]);
			paintWrappedProgressCount(g2, fm, w - inset - width, textY, rareObtained[row], rareTotal[row]);
		}
	}

	private static void paintIcon(Graphics2D g2, @Nullable BufferedImage[] set, int index, int x, int y)
	{
		BufferedImage icon = set != null && index < set.length ? set[index] : null;
		if (icon != null)
		{
			g2.drawImage(icon, x, y + (LINE_HEIGHT - ICON_SIZE) / 2, null);
		}
	}

	private int scoreRight(FontMetrics fm, int inset)
	{
		return inset + ICON_SIZE + ICON_GAP + widest(fm, LABELS) + COL_GAP
			+ widestValue(fm, scores, TitleTooltip::scoreText);
	}

	private int progressCol(FontMetrics fm)
	{
		return progressWidth(fm, obtained, total);
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

	private int rankCol(FontMetrics fm)
	{
		int width = 0;
		for (int i = 0; i < ranks.length; i++)
		{
			if (scores[i] > 0 && ranks[i] > 0)
			{
				width = Math.max(width, 1 + fm.stringWidth(rankTailText(ranks[i])));
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
