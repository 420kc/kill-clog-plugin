package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import javax.swing.SwingUtilities;
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
 * paint/format helpers shared by a pair live package-private on the solo class
 * (see SkillsTooltip's readout constants) - never duplicated across the pair.
 */
public abstract class TitleTooltip extends NativeTooltip
{
	private static final int NAME_LINE_HEIGHT = 20;
	private static final int SEPARATOR_GAP = 6;
	private static final int CORNER_GAP = 8;
	private static final int INFO_PAIR_GAP = 8;
	private static final int CORNER_BADGE_SIZE = 12;
	private static final float CORNER_BADGE_IDLE_ALPHA = 0.3f;
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
	protected static final String CHROME_SEPARATOR = " | ";
	private static final Color QTY_SHADOW = new Color(0, 0, 0);

	private String title;
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
	private boolean wikiLinksEnabled = true;
	private boolean titleHovered;
	private boolean titleCornerHovered;

	protected TitleTooltip()
	{
		installTitleLinkHandlers();
	}

	/** Set the bold orange title line (always required). */
	public void setTitle(String title)
	{
		this.title = title;
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

	/**
	 * Set the obtained subtitle line. Pass -1 for unknown ("?/Y").
	 * Color follows native OSRS stoplight progress: red, yellow, green.
	 */
	public void setObtained(int obtained, int total)
	{
		setSubtitle("Obtained: ", progressCountText(obtained, total), completionColor(obtained, total));
	}

	/**
	 * Empty-state obtained line: the shape of the data with no player in it.
	 * Muted gray so the preview never reads as a score.
	 */
	public void setObtainedPlaceholder(int total)
	{
		setSubtitle("Obtained: ", "--/" + total, MUTED_GRAY);
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

	protected static String progressCountTextOrDash(int obtained, int total)
	{
		return obtained >= 0 ? progressCountText(obtained, total) : "--";
	}

	protected static String wrappedProgressCountText(int obtained, int total)
	{
		return " (" + progressCountText(obtained, total) + ")";
	}

	protected static String wrappedProgressCountTextOrDash(int obtained, int total)
	{
		return " (" + progressCountTextOrDash(obtained, total) + ")";
	}

	protected static int wrappedProgressCountWidth(FontMetrics fm, int obtained, int total)
	{
		return fm.stringWidth(wrappedProgressCountText(obtained, total));
	}

	protected static int wrappedProgressCountWidthOrDash(FontMetrics fm, int obtained, int total)
	{
		return fm.stringWidth(wrappedProgressCountTextOrDash(obtained, total));
	}

	protected static int paintWrappedProgressCount(Graphics2D g2, FontMetrics fm, int x, int y,
		int obtained, int total)
	{
		String progress = wrappedProgressCountText(obtained, total);
		g2.setColor(completionColor(obtained, total));
		g2.drawString(progress, x, y);
		return x + fm.stringWidth(progress);
	}

	protected static int paintWrappedProgressCountOrDash(Graphics2D g2, FontMetrics fm, int x, int y,
		int obtained, int total, Color unknownColor)
	{
		String progress = wrappedProgressCountTextOrDash(obtained, total);
		g2.setColor(obtained >= 0 ? completionColor(obtained, total) : unknownColor);
		g2.drawString(progress, x, y);
		return x + fm.stringWidth(progress);
	}

	protected static int paintChromeSeparator(Graphics2D g2, FontMetrics fm, int x, int y)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(CHROME_SEPARATOR, x, y);
		return x + fm.stringWidth(CHROME_SEPARATOR);
	}

	/** Value-column text: a thousands-grouped count, or "--" when absent. */
	protected static String scoreText(int value)
	{
		return value > 0 ? String.format(Locale.US, "%,d", value) : "--";
	}

	/** Rank tail that flows after a score column, e.g. " #1,234,567". */
	protected static String rankTailText(int rank)
	{
		return " #" + String.format(Locale.US, "%,d", rank);
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

	protected static String capitalize(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}
		return text.substring(0, 1).toUpperCase() + text.substring(1);
	}

	protected static String tierDisplayName(CombatAchievementResult ca)
	{
		CombatAchievementTier tier = ca != null ? ca.getTier() : null;
		return tier != null ? capitalize(tier.name().toLowerCase()) : "None";
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

	protected void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value)
	{
		drawLabelValue(g2, fm, x, y, label, value, Color.WHITE);
	}

	protected void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value, Color valueColor)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, x, y);
		g2.setColor(valueColor);
		g2.drawString(value, x + fm.stringWidth(label), y);
	}

	protected void loadItemSprites(int[] itemIds, int size, BufferedImage[] sprites,
		ItemManager itemManager)
	{
		for (int i = 0; i < itemIds.length && i < sprites.length; i++)
		{
			loadItemSprite(itemIds[i], size, sprites, i, itemManager);
		}
	}

	protected void loadItemSprites(List<Integer> itemIds, int count, int size,
		BufferedImage[] sprites, ItemManager itemManager)
	{
		for (int i = 0; i < count && i < itemIds.size() && i < sprites.length; i++)
		{
			loadItemSprite(itemIds.get(i), size, sprites, i, itemManager);
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

	private void loadItemSprite(int itemId, int size, BufferedImage[] sprites, int index,
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

	protected void paintSpriteRow(Graphics2D g2, int x, int y, int colWidth,
		BufferedImage[] sprites, int count, int size, int pad)
	{
		if (sprites == null || count == 0)
		{
			return;
		}
		int spriteRowWidth = count * size + (count - 1) * pad;
		int startX = x + (colWidth - spriteRowWidth) / 2;
		for (int i = 0; i < count && i < sprites.length; i++)
		{
			int sx = startX + i * (size + pad);
			if (sprites[i] != null)
			{
				g2.drawImage(sprites[i], sx, y, null);
			}
		}
	}

	protected void paintQuantitySpriteRow(Graphics2D g2, FontMetrics fm, int x, int y,
		int colWidth, BufferedImage[] sprites, int[] counts, int size, int pad)
	{
		int count = Math.min(sprites.length, counts.length);
		int spriteRowWidth = count * size + (count - 1) * pad;
		int startX = x + (colWidth - spriteRowWidth) / 2;
		for (int i = 0; i < count; i++)
		{
			int sx = startX + i * (size + pad);
			BufferedImage sprite = sprites[i];
			if (sprite == null)
			{
				continue;
			}

			boolean obtained = counts[i] > 0;
			g2.setComposite(obtained
				? AlphaComposite.SrcOver
				: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
			g2.drawImage(sprite, sx, y, null);
			g2.setComposite(AlphaComposite.SrcOver);

			if (obtained && counts[i] > 1)
			{
				String qtyText = String.valueOf(counts[i]);
				g2.setColor(QTY_SHADOW);
				g2.drawString(qtyText, sx + 1, y + fm.getAscent() + 1);
				g2.setColor(CLOG_YELLOW);
				g2.drawString(qtyText, sx, y + fm.getAscent());
			}
		}
	}

	/** Faded version of a color for no-data cells, so an empty value reads quiet, not absent. */
	protected static Color dim(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), 110);
	}

	/**
	 * Color for a comparison value: a real value keeps the player's full color,
	 * a "--" no-data dash is dimmed. Missing data stays on the happy path instead
	 * of shouting a notice.
	 */
	protected static Color compareValueColor(String text, Color playerColor)
	{
		return "--".equals(text) ? dim(playerColor) : playerColor;
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
			this.rankText = String.format(Locale.US, "%,d", rank);
		}
		else
		{
			this.rankText = "Unranked";
		}
	}

	protected String getTitle()
	{
		return title;
	}

	/** Override in subclasses that need a smaller title font. */
	protected Font getTitleFont()
	{
		return TITLE_FONT;
	}

	/** Whether to show the optional top-right source badge. */
	protected boolean hasTitleCornerBadge()
	{
		return false;
	}

	/** Color for the source badge and hover text. */
	protected Color getTitleCornerColor()
	{
		return CLOG_GREEN;
	}

	/** Optional orange text painted on the right side of the last header row. */
	protected String getHeaderRightText()
	{
		return null;
	}

	/** Color for the optional right-side header text. */
	protected Color getHeaderRightColor()
	{
		return OSRS_ORANGE;
	}

	protected void paintHeaderRightText(Graphics2D g2, FontMetrics fm, int w, int baseline,
		int reservedLeftWidth)
	{
		String text = getHeaderRightText();
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

		g2.setColor(getHeaderRightColor());
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

	/**
	 * Return the content dimensions given the available width.
	 * The availableWidth accounts for the header-driven minimum width, so subclasses
	 * can wrap content to fill the space.
	 */
	protected abstract Dimension getContentSize(int availableWidth);

	/**
	 * Paint the body content starting at the given Y coordinate (below separator).
	 */
	protected abstract void paintBody(Graphics2D g2, int w, int h, int startY);

	@Override
	public Dimension getPreferredSize()
	{
		int inset = getInset();

		FontMetrics nfm = getFontMetrics(getTitleFont());
		FontMetrics sfm = getFontMetrics(FontManager.getRunescapeSmallFont());

		// Header text widths drive minimum tooltip width.
		// The full header width flows to getContentSize so grids can fill the space.
		int titleTextWidth = title != null ? nfm.stringWidth(title) : 0;
		int cornerWidth = titleCornerPreferredWidth();
		if (cornerWidth > 0)
		{
			titleTextWidth += CORNER_GAP + cornerWidth;
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
		// Title (bold orange). While the corner badge or a body item is
		// hovered the title yields its line to the reveal text, so nothing
		// ever clips regardless of tooltip width.
		String headerTitle = title;
		Color headerColor = titleColor();
		String cornerText = titleCornerHovered ? getTitleCornerHoverText() : null;
		String hoverText = getTitleHoverText();
		if (cornerText != null && !cornerText.isEmpty())
		{
			headerTitle = cornerText;
			headerColor = getTitleCornerColor();
		}
		else if (hoverText != null && !hoverText.isEmpty())
		{
			headerTitle = hoverText;
			headerColor = getTitleHoverColor();
		}
		g2.setFont(getTitleFont());
		FontMetrics nfm = g2.getFontMetrics();
		int lineY = inset + nfm.getAscent();
		int titleBaseline = lineY;
		g2.setColor(headerColor);
		// Long reveal texts (recent-item names) ellipsize instead of clipping
		// at the tooltip edge; the corner badge keeps its lane.
		int cornerW = titleCornerPreferredWidth();
		headerTitle = fitHeaderText(nfm, headerTitle,
			w - 2 * inset - (cornerW > 0 ? cornerW + 4 : 0));
		g2.drawString(headerTitle, inset, lineY);
		int activeLineWidth = nfm.stringWidth(headerTitle);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		paintTitleCorner(g2, w);

		// Header line order: stats read first (KC/PB info line, then rank),
		// clog progress reads last. The first line under the title keeps the
		// wider gap the larger title font needs.
		boolean firstHeaderLine = true;

		// Info line (KC/PB for boss cells, Kills for unsynced)
		if (infoLabel != null)
		{
			lineY += firstHeaderLine ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			firstHeaderLine = false;
			g2.setColor(OSRS_ORANGE);
			g2.drawString(infoLabel, inset, lineY);
			int infoLabelWidth = fm.stringWidth(infoLabel);
			g2.setColor(infoColor);
			g2.drawString(infoValue, inset + infoLabelWidth, lineY);
			activeLineWidth = infoLabelWidth + fm.stringWidth(infoValue);
			if (infoLabel2 != null)
			{
				int x2 = inset + activeLineWidth + INFO_PAIR_GAP;
				g2.setColor(OSRS_ORANGE);
				g2.drawString(infoLabel2, x2, lineY);
				int label2Width = fm.stringWidth(infoLabel2);
				g2.setColor(infoColor2);
				g2.drawString(infoValue2, x2 + label2Width, lineY);
				activeLineWidth += INFO_PAIR_GAP + label2Width + fm.stringWidth(infoValue2);
			}
		}

		// Rank line
		if (rankText != null)
		{
			lineY += firstHeaderLine ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			firstHeaderLine = false;
			String label = "Rank: ";
			g2.setColor(OSRS_ORANGE);
			g2.drawString(label, inset, lineY);
			if (!"Unranked".equals(rankText))
			{
				g2.setColor(Color.WHITE);
			}
			g2.drawString(rankText, inset + fm.stringWidth(label), lineY);
			activeLineWidth = fm.stringWidth(label) + fm.stringWidth(rankText);
		}

		// Subtitle (label in orange, value in subtitleColor)
		if (subtitleLabel != null)
		{
			lineY += firstHeaderLine ? NAME_LINE_HEIGHT : LINE_HEIGHT;
			firstHeaderLine = false;
			g2.setColor(OSRS_ORANGE);
			g2.drawString(subtitleLabel, inset, lineY);
			int labelWidth = fm.stringWidth(subtitleLabel);
			g2.setColor(subtitleColor);
			g2.drawString(subtitleValue, inset + labelWidth, lineY);
			activeLineWidth = labelWidth + fm.stringWidth(subtitleValue);
		}

		paintHeaderRightText(g2, fm, w, lineY, activeLineWidth);

		// Separator
		int sepY = lineY + SEPARATOR_GAP;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + SEPARATOR_GAP;
	}

	private int titleCornerPreferredWidth()
	{
		return hasTitleCornerBadge() ? CORNER_BADGE_SIZE : 0;
	}

	private void paintTitleCorner(Graphics2D g2, int w)
	{
		if (!hasTitleCornerBadge())
		{
			return;
		}

		int inset = getInset();
		int width = titleCornerPreferredWidth();
		int x = w - inset - width;
		Color color = getTitleCornerColor();

		Composite prior = g2.getComposite();
		float alpha = titleCornerHovered ? 1f : CORNER_BADGE_IDLE_ALPHA;
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		int badgeX = x + width - CORNER_BADGE_SIZE;
		int badgeY = getInset() + 2;
		g2.setColor(color);
		g2.fillOval(badgeX, badgeY, CORNER_BADGE_SIZE, CORNER_BADGE_SIZE);
		g2.setColor(new Color(0, 25, 0));
		int midX = badgeX + CORNER_BADGE_SIZE / 2;
		int arrowTop = badgeY + 3;
		int arrowBottom = badgeY + CORNER_BADGE_SIZE - 3;
		g2.drawLine(midX, arrowTop, midX, arrowBottom);
		g2.drawLine(midX, arrowTop, midX - 3, arrowTop + 3);
		g2.drawLine(midX, arrowTop, midX + 3, arrowTop + 3);
		g2.setComposite(prior);
	}

	/** Text that replaces the title while the corner badge is hovered. */
	protected String getTitleCornerHoverText()
	{
		return null;
	}

	/**
	 * Text that replaces the title while a body item is hovered. Narrow
	 * summary tooltips reveal hovered item names here; wide grid popups
	 * keep using the header-right zone instead.
	 */
	protected String getTitleHoverText()
	{
		return null;
	}

	protected Color getTitleHoverColor()
	{
		return CLOG_GREEN;
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
				if (e.getButton() == MouseEvent.BUTTON1 && titleLinkActive()
					&& titleBounds().contains(e.getX(), e.getY()))
				{
					TooltipItemLink.openWikiPage(titleWikiPage);
					titleHovered = false;
					setVisible(false);
					e.consume();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (titleHovered || titleCornerHovered)
				{
					titleHovered = false;
					titleCornerHovered = false;
					repaint();
				}
			}
		});
	}

	private void updateHeaderHover(int x, int y)
	{
		boolean nextTitle = titleLinkActive() && titleBounds().contains(x, y);
		boolean nextCorner = titleCornerActive() && titleCornerBounds().contains(x, y);
		if (nextTitle != titleHovered || nextCorner != titleCornerHovered)
		{
			boolean cornerChanged = nextCorner != titleCornerHovered;
			titleHovered = nextTitle;
			titleCornerHovered = nextCorner;
			if (cornerChanged)
			{
				onTitleCornerHoverChanged(nextCorner);
			}
			repaint();
		}
	}

	private boolean titleLinkActive()
	{
		return wikiLinksEnabled && titleWikiPage != null && !titleWikiPage.trim().isEmpty();
	}

	protected Color titleColor()
	{
		return titleHovered && titleLinkActive() ? Color.WHITE : OSRS_ORANGE;
	}

	boolean isTitleCornerHovered()
	{
		return titleCornerHovered;
	}

	protected void onTitleCornerHoverChanged(boolean hovered)
	{
	}

	private Rectangle titleBounds()
	{
		int inset = getInset();
		int width = title != null ? getFontMetrics(getTitleFont()).stringWidth(title) : 0;
		return new Rectangle(inset, inset, width, NAME_LINE_HEIGHT);
	}

	private boolean titleCornerActive()
	{
		return hasTitleCornerBadge();
	}

	private Rectangle titleCornerBounds()
	{
		int inset = getInset();
		int width = titleCornerPreferredWidth();
		return new Rectangle(getWidth() - inset - width, inset, width, NAME_LINE_HEIGHT);
	}
}
