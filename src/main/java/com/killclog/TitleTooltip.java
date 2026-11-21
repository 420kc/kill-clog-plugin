package com.killclog;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.annotation.Nullable;
import javax.swing.JToolTip;
import lombok.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.*;

/**
 * Intermediate tooltip with a titled header zone: bold title line,
 * optional subtitle (label: value), optional rank line, then separator.
 * Subclasses list their content below the separator as parts ({@link #body()}).
 */
public abstract class TitleTooltip extends NativeTooltip
{
	private static final int NAME_LINE_HEIGHT = 20;
	// The hiscore badges are 18 px art, the title line's own height.
	private static final int TITLE_ICON_MAX = 18;
	private static final int TITLE_ICON_GAP = 4;
	private static final int BACK = 0;
	private static final int TITLE = 1;
	static final int SEPARATOR_GAP = 6;
	private static final int INFO_PAIR_GAP = 8;
	private static final String ELLIPSIS = "...";
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	static final Font TITLE_FONT_SMALL = FontManager.getRunescapeBoldFont().deriveFont(16f);

	protected static final Color CLOG_RED = new Color(255, 0, 0);
	protected static final Color CLOG_GREEN = new Color(0, 255, 0);
	protected static final Color STALE_RED = new Color(255, 60, 60);
	protected static final Color CLOG_YELLOW = new Color(255, 255, 0);
	protected static final Color MUTED_GRAY = new Color(148, 148, 148);
	private static final int HEADER_WORD_GAP = 8;
	private static final int HEADER_ICON_GAP = 3;

	@Getter(AccessLevel.PROTECTED)
	@Setter
	private String title; // the bold orange title line (always required)
	// The line under the title: the Obtained count, or the Player Summary's own line.
	@Nullable
	private InfoLine subtitle;
	// An icon after the line under the title (the Player Summary's clan), and where it was last drawn.
	@Nullable
	private BufferedImage subtitleIcon;
	@Nullable
	@Getter(AccessLevel.PACKAGE)
	private Rectangle subtitleIconBounds;
	// The header's stat lines (KC and PB; a raid's hard mode on a second), each a label and value and maybe a pair.
	private final List<InfoLine> infoLines = new ArrayList<>();
	// The rank line; a raid's hard mode ranks beside the raid on the same line.
	@Nullable
	private InfoLine rank;
	private String titleWikiPage;
	@Nullable
	private BufferedImage titleIcon;
	private CardLinks links = CardLinks.ON;
	// The way back to the card this one opened from: the card's first line, above its title, white while hovered.
	private String backLabel;
	@Nullable
	private Consumer<MouseEvent> onBack;
	// The way back and the title's wiki link answer the pointer as a body's rows do: lit while hovered, a press opens.
	private final CardBody.ClickRows headerRows = new CardBody.ClickRows(this, (e, key) ->
	{
		if (key == BACK)
		{
			onBack.accept(e);
			e.consume();
		}
		else if (linkOpens(e))
		{
			TooltipItemLink.openWikiPage(titleWikiPage);
			NativeTooltip.hideTooltipTree(this);
			e.consume();
		}
	});
	private boolean titleLit;
	// Item sprites on the card: hover names and wiki links. Installed after the
	// title's own listeners, as the cards' own copies were.
	final TooltipItemHover itemHover = new TooltipItemHover(this);

	/**
	 * Optional badge before the title, drawn at its own size: art no taller than the title's line
	 * stays crisp, and anything taller is scaled down to fit it.
	 */
	protected void setTitleIcon(@Nullable BufferedImage icon)
	{
		if (icon != null && icon.getHeight() > TITLE_ICON_MAX)
		{
			int width = Math.max(1, (int) Math.round((double) icon.getWidth() / icon.getHeight() * TITLE_ICON_MAX));
			icon = ImageUtil.resizeImage(icon, width, TITLE_ICON_MAX);
		}
		titleIcon = icon;
	}

	/**
	 * A word at the header's right on the title's line, with its icon before it: the Collection Log names its tier beside
	 * the tier's icon, the Combat Summary its combat level and a skill's card its level.
	 */
	CardBody.Part headerWord(@Nullable String word, @Nullable Image icon, Font wordFont, Color color)
	{
		FontMetrics fm = getFontMetrics(wordFont);
		int iconWidth = icon != null ? icon.getWidth(null) + HEADER_ICON_GAP : 0;
		return CardBody.row(0, c -> word != null ? headerTextWidth() + HEADER_WORD_GAP + iconWidth + fm.stringWidth(word) : 0, (c, y) ->
		{
			if (word != null)
			{
				int ascent = getFontMetrics(getTitleFont()).getAscent();
				int x = c.w - c.inset() - fm.stringWidth(word);
				if (icon != null)
				{
					c.g.drawImage(icon, x - iconWidth, c.inset() + (ascent - icon.getHeight(null)) / 2, null);
				}
				Font font = c.g.getFont();
				c.g.setFont(fm.getFont());
				c.g.setColor(color);
				c.g.drawString(word, x, c.inset() + ascent);
				c.g.setFont(font);
			}
		});
	}

	/** An item's image at its own size, which stays crisp; the card repaints once it loads. */
	@Nullable
	AsyncBufferedImage itemSprite(int itemId, @Nullable ItemManager itemManager)
	{
		if (itemId < 0 || itemManager == null)
		{
			return null;
		}
		AsyncBufferedImage sprite = itemManager.getImage(itemId);
		TooltipItemSprites.whenLoaded(sprite, this, this::repaint);
		return sprite;
	}

	private int titleIconWidth()
	{
		return titleIcon != null ? titleIcon.getWidth() + TITLE_ICON_GAP : 0;
	}

	/** The card's first line, above its title, leads back to the card this one opened from. */
	void setBack(String label, Consumer<MouseEvent> onBack)
	{
		backLabel = label;
		this.onBack = onBack;
	}

	/** The way back on a card, or on both cards of a comparison. */
	static JToolTip withBack(JToolTip tip, String label, Consumer<MouseEvent> onBack)
	{
		for (JToolTip card : tip instanceof SideBySideTooltip ? ((SideBySideTooltip) tip).sides() : new JToolTip[]{tip})
		{
			if (card instanceof TitleTooltip)
			{
				((TitleTooltip) card).setBack(label, onBack);
			}
		}
		return tip;
	}

	/** Optional OSRS Wiki page opened when the title is clicked. */
	public void setTitleWikiPage(String titleWikiPage)
	{
		this.titleWikiPage = titleWikiPage;
	}

	public void setLinks(CardLinks links)
	{
		this.links = links;
		itemHover.setLinks(links);
	}

	/**
	 * Set a subtitle line: label in orange, value in the given color.
	 */
	public void setSubtitle(String label, String value, Color valueColor)
	{
		subtitle = new InfoLine(label, value, valueColor);
	}

	protected void clearSubtitle()
	{
		subtitle = null;
	}

	/**
	 * The Obtained line in native OSRS stoplight progress, red, yellow and green; -1 while the count is unknown, the
	 * data's shape with no player in it ("--/Y"), muted so it never reads as a score.
	 */
	public void setObtained(int obtained, int total)
	{
		setSubtitle("Obtained: ", obtained >= 0 ? progressCountText(obtained, total) : progressPlaceholderText(total),
			obtained >= 0 ? completionColor(obtained, total) : MUTED_GRAY);
	}

	protected static String progressPlaceholderText(int total)
	{
		return "--/" + (total >= 0 ? String.valueOf(total) : "?");
	}

	/** Native OSRS stoplight progress color: red for none, yellow for some, green for complete. */
	protected static Color completionColor(int obtained, int total)
	{
		if (obtained >= total && total > 0)
		{
			return CLOG_GREEN;
		}
		if (obtained == 0 && total > 0)
		{
			return CLOG_RED;
		}
		return CLOG_YELLOW;
	}

	protected static String progressCountText(int obtained, int total)
	{
		return (obtained < 0 ? "?" : String.valueOf(obtained))
			+ "/" + (total < 0 ? "?" : String.valueOf(total));
	}

	/** A thousands-grouped count, the game's way: 1,234,567. */
	static String grouped(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	/** Value-column text: a thousands-grouped count, or "--" when absent. */
	protected static String scoreText(long value)
	{
		return value > 0 ? grouped(value) : "--";
	}

	/** Efficient-hours text: one decimal, thousands-grouped, "--" when absent. */
	protected static String ehbText(double hours)
	{
		return hours >= 0 ? String.format(Locale.US, "%,.1f", hours) : "--";
	}

	protected static String tierDisplayName(CombatAchievementResult ca)
	{
		CombatAchievementTier tier = ca != null ? ca.getTier() : null;
		return tier != null ? tier.name().charAt(0) + tier.name().substring(1).toLowerCase() : "None";
	}

	protected static int separatorHeight(int pad)
	{
		return pad + 1 + pad;
	}

	protected int paintSeparator(Graphics2D g2, int w, int y, int pad)
	{
		int inset = getInset();
		y += pad;
		CardBody.divider(g2, y, inset, w);
		return y + 1 + pad;
	}

	static final int SUBHEADER_HEIGHT = 16;

	/** A bold orange subheader, the small font handed back after; returns the Y under it. */
	int paintSubheader(Graphics2D g2, int y, String text)
	{
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(labelColor);
		g2.drawString(text, getInset(), y + g2.getFontMetrics().getAscent());
		g2.setFont(FontManager.getRunescapeSmallFont());
		return y + SUBHEADER_HEIGHT;
	}

	static int drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value)
	{
		return drawLabelValue(g2, fm, x, y, label, value, NativeTooltip.valueColor);
	}

	/** An orange label with its value right after it; returns the pair's width. */
	static int drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value, Color valueColor)
	{
		return drawLabelValue(g2, fm, x, y, label, value, labelColor, valueColor);
	}

	/** A label in its own color (white on a hovered row) with its value right after it. */
	static int drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value, Color labelColor, Color valueColor)
	{
		g2.setColor(labelColor);
		g2.drawString(label, x, y);
		int labelWidth = fm.stringWidth(label);
		g2.setColor(valueColor);
		g2.drawString(value, x + labelWidth, y);
		return labelWidth + fm.stringWidth(value);
	}

	/** One more info line under the last. */
	void addInfoLine(String label, String value, Color valueColor)
	{
		infoLines.add(new InfoLine(label, value, valueColor));
	}

	/** Optional second label+value pair painted after the first on the last info line. */
	public void setInfoLinePair(String label, String value, Color valueColor)
	{
		if (!infoLines.isEmpty())
		{
			infoLines.get(infoLines.size() - 1).pair = new InfoLine(label, value, valueColor);
		}
	}

	/** A label and value in the header's info lines, and the pair painted after it. */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class InfoLine
	{
		private final String label;
		private final String value;
		private final Color color;
		@Nullable
		private InfoLine pair;

		private int width(FontMetrics fm)
		{
			return fm.stringWidth(label + value) + (pair != null ? INFO_PAIR_GAP + pair.width(fm) : 0);
		}
	}

	/** A second rank after the first on the rank line, as a raid's hard mode ranks beside the raid. */
	void setRankPair(String label, int rank)
	{
		if (this.rank != null)
		{
			this.rank.pair = rankLine(label, rank);
		}
	}

	/** Set the rank line. 0 = "Unranked". */
	public void setRank(int rank)
	{
		this.rank = rankLine("Rank: ", rank);
	}

	/** A rank as the header shows it: grouped in the value's colour, or "Unranked" in the label's. */
	private static InfoLine rankLine(String label, int rank)
	{
		return rank > 0 ? new InfoLine(label, grouped(rank), NativeTooltip.valueColor)
			: new InfoLine(label, "Unranked", labelColor);
	}

	/** The header's lines under the title, in the order they paint: one list sizes, counts and paints them. */
	private List<InfoLine> headerLines()
	{
		List<InfoLine> lines = new ArrayList<>();
		// Obtained leads, as the game's Collection Log pages do, then the stats: KC and PB, then rank.
		if (subtitle != null)
		{
			lines.add(subtitle);
		}
		lines.addAll(infoLines);
		if (rank != null)
		{
			lines.add(rank);
		}
		return lines;
	}

	/** A header line's text width, with the subtitle's icon after it. */
	private int lineWidth(FontMetrics fm, InfoLine line)
	{
		int width = line.width(fm);
		return line == subtitle && subtitleIcon != null ? width + (width > 0 ? TITLE_ICON_GAP : 0) + subtitleIcon.getWidth()
			: width;
	}

	/** Override in subclasses that need a smaller title font. */
	protected Font getTitleFont()
	{
		return TITLE_FONT;
	}

	protected String getHeaderHoverLineText()
	{
		return itemHover.hoveredItemName();
	}

	protected Color getHeaderHoverLineColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	static String fitHeaderText(FontMetrics fm, String text, int maxWidth)
	{
		if (fm.stringWidth(text) <= maxWidth)
		{
			return text;
		}

		int ellipsisWidth = fm.stringWidth(ELLIPSIS);
		if (ellipsisWidth >= maxWidth)
		{
			return "";
		}

		StringBuilder out = new StringBuilder(text);
		while (out.length() > 0 && fm.stringWidth(out.toString()) + ellipsisWidth > maxWidth)
		{
			out.deleteCharAt(out.length() - 1);
		}
		return out + ELLIPSIS;
	}

	/**
	 * Number of pixel rows the header occupies (title + optional lines).
	 * Does not include the separator gap below.
	 */
	int getHeaderHeight()
	{
		return NAME_LINE_HEIGHT + (headerLines().size() + (onBack != null ? 1 : 0)) * LINE_HEIGHT;
	}

	/** The scroll window of a card that has one. */
	@Nullable
	CardBody.Scroll scroll()
	{
		return null;
	}

	/** A comparison's two cards scroll as one wherever both can. */
	static void scrollTogether(JToolTip blue, JToolTip red)
	{
		CardBody.Scroll left = blue instanceof TitleTooltip ? ((TitleTooltip) blue).scroll() : null;
		CardBody.Scroll right = red instanceof TitleTooltip ? ((TitleTooltip) red).scroll() : null;
		if (left != null && right != null)
		{
			left.scrollWith(right);
		}
	}

	/** The card's body as parts; a card that lists its body needs no sizing or painting of its own. */
	protected CardBody body()
	{
		return new CardBody();
	}

	@Override
	public Dimension getPreferredSize()
	{
		int inset = getInset();
		// The header's widest line is the least width and the body may ask for more; the body is measured again at the
		// width it paints at, where a grid may take another column.
		int header = headerTextWidth();
		int width = Math.max(header, body().size(this, Math.max(header, 1)).width);
		return new Dimension(width + inset * 2,
			inset + getHeaderHeight() + separatorHeight(SEPARATOR_GAP) + body().size(this, width).height + inset);
	}

	/** The widest header line: the card is never narrower, and a corner image keeps clear of every line. */
	int headerTextWidth()
	{
		FontMetrics nfm = getFontMetrics(getTitleFont());
		FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxTextWidth = title != null ? titleIconWidth() + nfm.stringWidth(title) : 0;
		for (InfoLine line : headerLines())
		{
			maxTextWidth = Math.max(maxTextWidth, lineWidth(sfm, line));
		}
		if (onBack != null)
		{
			maxTextWidth = Math.max(maxTextWidth, sfm.stringWidth(backLabel));
		}
		return maxTextWidth;
	}

	@Override
	protected void paintContent(Graphics2D g2, int w, int h)
	{
		itemHover.setHitBoxes(body().paint(this, g2, w, paintHeader(g2, w)));
	}

	/**
	 * Paint the header (title, optional subtitle, optional rank, separator).
	 * Returns the Y coordinate where body content should start.
	 */
	int paintHeader(Graphics2D g2, int w)
	{
		if (title == null)
		{
			return getInset();
		}

		int inset = getInset();
		// The way back leads the card, top left above the title.
		int top = inset;
		if (onBack != null)
		{
			g2.setFont(FontManager.getRunescapeSmallFont());
			// The back line answers across the card's whole width, a row like the others.
			g2.setColor(headerRows.lit(new Rectangle(0, inset, Integer.MAX_VALUE, LINE_HEIGHT), BACK)
				? NativeTooltip.valueColor : labelColor);
			g2.drawString(backLabel, inset, inset + g2.getFontMetrics().getAscent());
			top += LINE_HEIGHT;
		}
		titleLit = titleWikiPage != null && !titleWikiPage.trim().isEmpty() && headerRows.lit(titleBounds(), TITLE)
			&& links.opens(headerRows.shift);
		// Modal titles remain stable while body labels change.
		g2.setFont(getTitleFont());
		FontMetrics nfm = g2.getFontMetrics();
		int lineY = top + nfm.getAscent();
		int titleBaseline = lineY;
		g2.setColor(titleColor());
		int titleX = inset + titleIconWidth();
		if (titleIcon != null)
		{
			// Centered on the title's line, lifted a pixel to sit level with the letters.
			g2.drawImage(titleIcon, inset, top + (NAME_LINE_HEIGHT - titleIcon.getHeight()) / 2 - 1, null);
		}
		// A title wider than its card ends in an ellipsis instead of clipping, measured as the card was sized: painting's
		// antialiased bold runs up to 3 px wider, which the margin holds.
		g2.drawString(fitHeaderText(getFontMetrics(nfm.getFont()), title, w - inset - titleX), titleX, lineY);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Each line a label in orange and its value in its own colour, then any pair after it.
		subtitleIconBounds = null;
		for (InfoLine line : headerLines())
		{
			lineY = nextLine(lineY, titleBaseline);
			int width = drawLabelValue(g2, fm, inset, lineY, line.label, line.value, line.color);
			if (line.pair != null)
			{
				drawLabelValue(g2, fm, inset + width + INFO_PAIR_GAP, lineY, line.pair.label, line.pair.value,
					line.pair.color);
			}
			if (line == subtitle && subtitleIcon != null)
			{
				// After the text, level with its figures, never lower than the digits.
				subtitleIconBounds = new Rectangle(inset + width + (width > 0 ? TITLE_ICON_GAP : 0),
					lineY - 7 - subtitleIcon.getHeight() / 2, subtitleIcon.getWidth(), subtitleIcon.getHeight());
				g2.drawImage(subtitleIcon, subtitleIconBounds.x, subtitleIconBounds.y, null);
			}
		}

		return paintSeparator(g2, w, lineY, SEPARATOR_GAP);
	}

	/** The baseline of the header's next line: the first under the title keeps the wider gap its larger font needs. */
	private static int nextLine(int lineY, int titleBaseline)
	{
		return lineY + (lineY == titleBaseline ? NAME_LINE_HEIGHT : LINE_HEIGHT);
	}

	/** Room above and below the label, shared by sprite-section readouts. */
	static int hoverRowHeight(FontMetrics fm)
	{
		return fm.getHeight() + 4;
	}

	protected final void paintHeaderHoverLine(Graphics2D g2, FontMetrics fm, int w, int baseline)
	{
		String itemName = getHeaderHoverLineText();
		if (itemName != null && !itemName.isEmpty())
		{
			g2.setColor(getHeaderHoverLineColor());
			g2.drawString(fitHeaderText(fm, itemName, w - getInset() * 2), getInset(), baseline + 2);
		}
	}

	/** Whether this press opens a link on the card ({@link CardLinks}). */
	protected boolean linkOpens(MouseEvent press)
	{
		return links.opens(press);
	}

	protected Color titleColor()
	{
		return titleLit ? NativeTooltip.valueColor : labelColor;
	}

	void setSubtitleIcon(@Nullable BufferedImage icon)
	{
		subtitleIcon = icon;
	}

	/** The title with its icon: the name a card can tell more about in its band. */
	Rectangle titleArea()
	{
		Rectangle title = titleBounds();
		return new Rectangle(getInset(), title.y, title.x + title.width - getInset(), title.height);
	}

	private Rectangle titleBounds()
	{
		int inset = getInset();
		int width = title != null ? getFontMetrics(getTitleFont()).stringWidth(title) : 0;
		return new Rectangle(inset + titleIconWidth(), inset + (onBack != null ? LINE_HEIGHT : 0), width,
			NAME_LINE_HEIGHT);
	}
}
