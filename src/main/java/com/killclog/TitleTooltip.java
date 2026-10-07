package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Intermediate tooltip with a titled header zone: bold title line,
 * optional subtitle (label: value), optional rank line, then separator.
 * Subclasses provide content below the separator via
 * {@link #getContentSize(int)} and {@link #paintBody(Graphics2D, int, int, int)}.
 *
 * <p>Family convention: every Compare* tooltip pairs with a solo sibling, and
 * paint/format helpers shared by a pair live package-private on the solo class,
 * never duplicated across the pair.
 */
public abstract class TitleTooltip extends NativeTooltip
{
	private static final int NAME_LINE_HEIGHT = 20;
	// The hiscore badges are 18 px art, the title line's own height.
	private static final int TITLE_ICON_MAX = 18;
	private static final int TITLE_ICON_GAP = 4;
	private static final int SEPARATOR_GAP = 6;
	private static final int INFO_PAIR_GAP = 8;
	private static final String ELLIPSIS = "...";
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	static final Font TITLE_FONT_SMALL = FontManager.getRunescapeBoldFont().deriveFont(16f);
	static final Color SEPARATOR_COLOR = new Color(80, 70, 50);

	protected static final Color CLOG_RED = new Color(255, 0, 0);
	protected static final Color CLOG_GREEN = new Color(0, 255, 0);
	protected static final Color STALE_RED = new Color(255, 60, 60);
	protected static final Color CLOG_YELLOW = new Color(255, 255, 0);
	protected static final Color COMPARE_BLUE = new Color(91, 164, 207);
	protected static final Color COMPARE_RED = new Color(224, 86, 86);
	protected static final Color MUTED_GRAY = new Color(148, 148, 148);

	@Getter(AccessLevel.PROTECTED)
	@Setter
	private String title; // the bold orange title line (always required)
	@Getter(AccessLevel.PROTECTED)
	private String titleSuffix;
	@Getter(AccessLevel.PROTECTED)
	private Color titleSuffixColor;
	private String subtitleLabel;
	private String subtitleValue;
	private Color subtitleColor;
	private String infoLabel;
	private String infoValue;
	private String infoLabel2;
	private String infoValue2;
	private Color infoColor2;
	private Color infoColor;
	private String rankText;
	private String titleWikiPage;
	@Nullable
	private BufferedImage titleIcon;
	private boolean wikiLinksEnabled = true;
	private boolean titleHovered;
	// The way back to the card this one opened from: the header's last line, white while hovered.
	private String backLabel;
	@Nullable
	private Consumer<MouseEvent> onBack;
	private boolean backHovered;
	private int backTop = -1;
	// Item sprites on the card: hover names and wiki links. Installed after the
	// title's own listeners, as the cards' own copies were.
	final TooltipItemHover itemHover;
	// Image grids name the hovered item at the header's right instead of a hover line.
	boolean itemNameInHeader;

	protected TitleTooltip()
	{
		installTitleLinkHandlers();
		itemHover = new TooltipItemHover(this);
	}

	/** Optional colored text painted immediately after the main title. */
	protected void setTitleSuffix(String titleSuffix, Color color)
	{
		this.titleSuffix = titleSuffix;
		this.titleSuffixColor = color;
	}

	protected void clearTitleSuffix()
	{
		titleSuffix = null;
		titleSuffixColor = null;
	}

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

	private int titleIconWidth()
	{
		return titleIcon != null ? titleIcon.getWidth() + TITLE_ICON_GAP : 0;
	}

	/** The header's last line leads back to the card this one opened from. */
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
		titleHovered = false;
	}

	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		this.wikiLinksEnabled = wikiLinksEnabled;
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
		if (!wikiLinksEnabled && titleHovered)
		{
			titleHovered = false;
			repaint();
		}
	}

	/**
	 * Set a subtitle line: label in orange, value in the given color.
	 */
	public void setSubtitle(String label, String value, Color valueColor)
	{
		this.subtitleLabel = label;
		this.subtitleValue = value;
		this.subtitleColor = valueColor;
	}

	protected void clearSubtitle()
	{
		subtitleLabel = null;
		subtitleValue = null;
		subtitleColor = null;
	}

	/**
	 * Set the obtained subtitle line. Pass -1 for unknown ("?/Y").
	 * Color follows native OSRS stoplight progress: red, yellow, green.
	 */
	public void setObtained(int obtained, int total)
	{
		setSubtitle(obtainedLabel(), progressCountText(obtained, total), completionColor(obtained, total));
	}

	/** The count's label; the Clog Summary counts the whole log, not one page. */
	protected String obtainedLabel()
	{
		return "Obtained: ";
	}

	/**
	 * Empty-state obtained line: the shape of the data with no player in it.
	 * Muted gray so the preview never reads as a score.
	 */
	public void setObtainedPlaceholder(int total)
	{
		setSubtitle(obtainedLabel(), progressPlaceholderText(total), MUTED_GRAY);
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

	protected static String wrappedProgressCountText(int obtained, int total)
	{
		return " (" + progressCountText(obtained, total) + ")";
	}

	protected static int wrappedProgressCountWidth(FontMetrics fm, int obtained, int total)
	{
		return fm.stringWidth(wrappedProgressCountText(obtained, total));
	}

	protected static int paintWrappedProgressCount(Graphics2D g2, FontMetrics fm, int x, int y,
		int obtained, int total)
	{
		String progress = wrappedProgressCountText(obtained, total);
		g2.setColor(completionColor(obtained, total));
		g2.drawString(progress, x, y);
		return x + fm.stringWidth(progress);
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

	/** Rank tail that flows after a score column, e.g. " #1,234,567". */
	protected static String rankTailText(int rank)
	{
		return " #" + grouped(rank);
	}

	/** Efficient-hours text: one decimal, thousands-grouped, "--" when absent. */
	protected static String ehbText(double hours)
	{
		return hours >= 0 ? String.format(Locale.US, "%,.1f", hours) : "--";
	}

	/**
	 * Widest rendered width across actual values under the given formatter.
	 * Sizing measures the strings it will paint, never a placeholder.
	 */
	protected static int widestValue(FontMetrics fm, int[] values, IntFunction<String> fmt)
	{
		int width = 0;
		for (int value : values)
		{
			width = Math.max(width, fm.stringWidth(fmt.apply(value)));
		}
		return width;
	}

	/** Draw text so its right edge lands at rightX (right-aligned value column). */
	protected static void drawRightAligned(Graphics2D g2, FontMetrics fm, String text, int rightX, int y)
	{
		g2.drawString(text, rightX - fm.stringWidth(text), y);
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
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, y, w - inset - 1, y);
		return y + 1 + pad;
	}

	static final int SUBHEADER_HEIGHT = 16;

	/** A bold orange subheader, the small font handed back after; returns the Y under it. */
	int paintSubheader(Graphics2D g2, int y, String text)
	{
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString(text, getInset(), y + g2.getFontMetrics().getAscent());
		g2.setFont(FontManager.getRunescapeSmallFont());
		return y + SUBHEADER_HEIGHT;
	}

	static int drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value)
	{
		return drawLabelValue(g2, fm, x, y, label, value, Color.WHITE);
	}

	/** An orange label with its value right after it; returns the pair's width. */
	static int drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value, Color valueColor)
	{
		return drawLabelValue(g2, fm, x, y, label, value, OSRS_ORANGE, valueColor);
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

	protected void loadItemSprites(int[] itemIds, int size, BufferedImage[] sprites,
		ItemManager itemManager)
	{
		for (int i = 0; i < itemIds.length && i < sprites.length; i++)
		{
			loadItemSprite(itemIds[i], size, sprites, i, itemManager);
		}
	}

	protected void loadClogItemSprites(List<ClogResult.ClogItem> items, int count, int size,
		BufferedImage[] sprites, ItemManager itemManager)
	{
		for (int i = 0; i < count && i < items.size() && i < sprites.length; i++)
		{
			loadItemSprite(items.get(i).getId(), size, sprites, i, itemManager);
		}
	}

	void loadItemSprite(int itemId, int size, BufferedImage[] sprites, int index,
		ItemManager itemManager)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		sprites[index] = ImageUtil.resizeImage(img, size, size);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() -> SwingUtilities.invokeLater(() ->
			{
				BufferedImage loaded = itemManager.getImage(itemId, 1, false);
				sprites[index] = ImageUtil.resizeImage(loaded, size, size);
				repaint();
			}));
		}
	}

	/** Set an extra info line below the subtitle. Label in orange, value in given color. */
	public void setInfoLine(String label, String value, Color valueColor)
	{
		this.infoLabel = label;
		this.infoValue = value;
		this.infoColor = valueColor;
	}

	/** Optional second label+value pair painted after the first on the same info line. */
	public void setInfoLinePair(String label, String value, Color valueColor)
	{
		this.infoLabel2 = label;
		this.infoValue2 = value;
		this.infoColor2 = valueColor;
	}

	/** Set the rank line. 0 = "Unranked". */
	public void setRank(int rank)
	{
		if (rank > 0)
		{
			this.rankText = grouped(rank);
		}
		else
		{
			this.rankText = "Unranked";
		}
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

	/** The hover line under a sprite row, while one of that section's items is hovered. */
	void paintSectionHoverLine(Graphics2D g2, FontMetrics fm, int width, int y, int section)
	{
		if (itemHover.isSectionHovered(section))
		{
			paintHeaderHoverLine(g2, fm, width, y + fm.getAscent());
		}
	}

	protected String getHeaderHoverLineRightText()
	{
		return null;
	}

	protected Color getHeaderHoverLineRightColor()
	{
		return OSRS_ORANGE;
	}

	private void paintHeaderRightText(Graphics2D g2, FontMetrics fm, int w, int baseline,
		int reservedLeftWidth, String text, Color color)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}

		int inset = getInset();
		int maxWidth = w - inset * 2 - reservedLeftWidth - 8;
		if (maxWidth <= 0)
		{
			return;
		}

		String label = fitHeaderText(fm, text, maxWidth);
		if (label.isEmpty())
		{
			return;
		}

		g2.setColor(color);
		g2.drawString(label, w - inset - fm.stringWidth(label), baseline);
	}

	private static String fitHeaderText(FontMetrics fm, String text, int maxWidth)
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
		int h = NAME_LINE_HEIGHT;
		if (subtitleLabel != null)
		{
			h += LINE_HEIGHT;
		}
		if (infoLabel != null)
		{
			h += LINE_HEIGHT;
		}
		if (rankText != null)
		{
			h += LINE_HEIGHT;
		}
		if (onBack != null)
		{
			h += LINE_HEIGHT;
		}
		return h;
	}

	/**
	 * Total header zone height including separator line and gaps.
	 * Content starts at inset + this value.
	 */
	protected int getHeaderZoneHeight()
	{
		return getHeaderHeight() + SEPARATOR_GAP + 1 + SEPARATOR_GAP;
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

	/**
	 * Return the content dimensions given the available width.
	 * The availableWidth accounts for the header-driven minimum width, so subclasses
	 * can wrap content to fill the space.
	 */
	protected Dimension getContentSize(int availableWidth)
	{
		return body().size(this, availableWidth);
	}

	/**
	 * Paint the body content starting at the given Y coordinate (below separator).
	 */
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(body().paint(this, g2, w, startY));
	}

	@Override
	public Dimension getPreferredSize()
	{
		int inset = getInset();

		FontMetrics nfm = getFontMetrics(getTitleFont());
		FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());

		// Header text widths drive minimum tooltip width.
		// The full header width flows to getContentSize so grids can fill the space.
		int titleTextWidth = title != null ? titleIconWidth() + nfm.stringWidth(title) : 0;
		if (titleSuffix != null)
		{
			titleTextWidth += nfm.stringWidth(titleSuffix);
		}
		int subTextWidth = subtitleLabel != null
			? sfm.stringWidth(subtitleLabel + subtitleValue) : 0;
		int infoTextWidth = infoLabel != null
			? sfm.stringWidth(infoLabel + infoValue) : 0;
		if (infoLabel != null && infoLabel2 != null)
		{
			infoTextWidth += INFO_PAIR_GAP + sfm.stringWidth(infoLabel2 + infoValue2);
		}
		int rnkTextWidth = rankText != null ? sfm.stringWidth("Rank: " + rankText) : 0;
		int maxTextWidth = Math.max(titleTextWidth,
			Math.max(subTextWidth, Math.max(infoTextWidth, rnkTextWidth)));
		if (onBack != null)
		{
			maxTextWidth = Math.max(maxTextWidth, sfm.stringWidth(backLabel));
		}
		int headerMinWidth = maxTextWidth;

		Dimension contentSize = getContentSize(Math.max(headerMinWidth, 1));

		int contentWidth = Math.max(headerMinWidth, contentSize.width);
		int totalHeight = inset + getHeaderZoneHeight() + contentSize.height + inset;
		int totalWidth = contentWidth + inset * 2;

		return new Dimension(totalWidth, totalHeight);
	}

	@Override
	protected void paintContent(Graphics2D g2, int w, int h)
	{
		int startY = paintHeader(g2, w);
		paintBody(g2, w, h, startY);
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
		// Modal titles remain stable while body labels change.
		String headerTitle = title;
		Color headerColor = titleColor();
		boolean showTitleSuffix = titleSuffix != null;
		g2.setFont(getTitleFont());
		FontMetrics nfm = g2.getFontMetrics();
		int lineY = inset + nfm.getAscent();
		int titleBaseline = lineY;
		g2.setColor(headerColor);
		// Long reveal texts (recent-item names) ellipsize instead of clipping.
		int suffixWidth = showTitleSuffix ? nfm.stringWidth(titleSuffix) : 0;
		int titleX = inset + titleIconWidth();
		if (titleIcon != null)
		{
			// Centered on the title's line, lifted a pixel to sit level with the letters.
			g2.drawImage(titleIcon, inset, inset + (NAME_LINE_HEIGHT - titleIcon.getHeight()) / 2 - 1, null);
		}
		headerTitle = fitHeaderText(nfm, headerTitle,
			w - inset - titleX - suffixWidth);
		g2.drawString(headerTitle, titleX, lineY);
		int activeLineWidth = titleX - inset + nfm.stringWidth(headerTitle);
		if (showTitleSuffix)
		{
			g2.setColor(titleSuffixColor != null ? titleSuffixColor : OSRS_ORANGE);
			g2.drawString(titleSuffix, inset + activeLineWidth, lineY);
			activeLineWidth += suffixWidth;
		}

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Header line order: stats read first (KC/PB info line, then rank),
		// clog progress reads last. The first line under the title keeps the
		// wider gap the larger title font needs.

		// Info line (KC/PB for boss cells, Kills for unsynced)
		if (infoLabel != null)
		{
			lineY += lineY == titleBaseline ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			activeLineWidth = drawLabelValue(g2, fm, inset, lineY, infoLabel, infoValue, infoColor);
			if (infoLabel2 != null)
			{
				activeLineWidth += INFO_PAIR_GAP + drawLabelValue(g2, fm,
					inset + activeLineWidth + INFO_PAIR_GAP, lineY, infoLabel2, infoValue2, infoColor2);
			}
		}

		// Rank line
		if (rankText != null)
		{
			lineY += lineY == titleBaseline ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			activeLineWidth = drawLabelValue(g2, fm, inset, lineY, "Rank: ", rankText,
				"Unranked".equals(rankText) ? OSRS_ORANGE : Color.WHITE);
		}

		// Subtitle (label in orange, value in subtitleColor)
		if (subtitleLabel != null)
		{
			lineY += lineY == titleBaseline ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			activeLineWidth = drawLabelValue(g2, fm, inset, lineY, subtitleLabel, subtitleValue, subtitleColor);
		}

		if (onBack != null)
		{
			lineY += lineY == titleBaseline ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			g2.setColor(backHovered ? Color.WHITE : OSRS_ORANGE);
			g2.drawString(backLabel, inset, lineY);
			activeLineWidth = fm.stringWidth(backLabel);
			backTop = lineY - fm.getAscent();
		}

		if (itemNameInHeader)
		{
			// The hovered item's name on the last header row; full-size sprites carry their own counts.
			paintHeaderRightText(g2, fm, w, lineY, activeLineWidth,
				itemHover.hoveredItemName(), getHeaderHoverLineColor());
		}

		// Separator
		int sepY = lineY + SEPARATOR_GAP;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + SEPARATOR_GAP;
	}

	/** Room above and below the label, shared by sprite-section readouts. */
	static int hoverRowHeight(FontMetrics fm)
	{
		return fm.getHeight() + 4;
	}

	protected final void paintHeaderHoverLine(Graphics2D g2, FontMetrics fm, int w, int baseline)
	{
		baseline += 2;
		String itemName = getHeaderHoverLineText();
		String duplicateCount = getHeaderHoverLineRightText();
		int inset = getInset();
		int duplicateWidth = duplicateCount != null ? fm.stringWidth(duplicateCount) : 0;
		if (itemName != null && !itemName.isEmpty())
		{
			int maxNameWidth = w - inset * 2 - (duplicateWidth > 0 ? duplicateWidth + 8 : 0);
			String fittedName = fitHeaderText(fm, itemName, maxNameWidth);
			g2.setColor(getHeaderHoverLineColor());
			g2.drawString(fittedName, inset, baseline);
		}
		if (duplicateCount != null && !duplicateCount.isEmpty())
		{
			g2.setColor(getHeaderHoverLineRightColor());
			g2.drawString(duplicateCount, w - inset - duplicateWidth, baseline);
		}
	}

	private void installTitleLinkHandlers()
	{
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				updateHeaderHover(e.getX(), e.getY());
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1 && onBack != null && onBackRow(e.getY()))
				{
					onBack.accept(e);
					e.consume();
					return;
				}
				if (e.getButton() == MouseEvent.BUTTON1 && titleLinkActive()
					&& titleBounds().contains(e.getX(), e.getY()))
				{
					TooltipItemLink.openWikiPage(titleWikiPage);
					titleHovered = false;
					NativeTooltip.hideTooltipTree(TitleTooltip.this);
					e.consume();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (titleHovered || backHovered)
				{
					titleHovered = false;
					backHovered = false;
					repaint();
				}
			}
		});
	}

	private void updateHeaderHover(int x, int y)
	{
		boolean nextTitle = titleLinkActive() && titleBounds().contains(x, y);
		boolean nextBack = onBack != null && onBackRow(y);
		if (nextTitle != titleHovered || nextBack != backHovered)
		{
			titleHovered = nextTitle;
			backHovered = nextBack;
			repaint();
		}
	}

	/** The back line answers across the card's whole width, a row like the others. */
	boolean onBackRow(int y)
	{
		return backTop >= 0 && y >= backTop && y < backTop + LINE_HEIGHT;
	}

	private boolean titleLinkActive()
	{
		return wikiLinksEnabled && titleWikiPage != null && !titleWikiPage.trim().isEmpty();
	}

	protected Color titleColor()
	{
		return titleHovered && titleLinkActive() ? Color.WHITE : OSRS_ORANGE;
	}

	private Rectangle titleBounds()
	{
		int inset = getInset();
		int width = title != null ? getFontMetrics(getTitleFont()).stringWidth(title) : 0;
		return new Rectangle(inset + titleIconWidth(), inset, width, NAME_LINE_HEIGHT);
	}
}
