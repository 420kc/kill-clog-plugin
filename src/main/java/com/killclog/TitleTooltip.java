package com.killclog;

import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.function.IntFunction;
import net.runelite.client.ui.FontManager;

/**
 * Intermediate tooltip with a titled header zone: bold title line,
 * optional subtitle (label: value), optional rank line, then separator.
 * Subclasses provide content below the separator via
 * {@link #getContentSize(int)} and {@link #paintBody(Graphics2D, int, int, int)}.
 */
public abstract class TitleTooltip extends NativeTooltip
{
	private static final int NAME_LINE_HEIGHT = 20;
	private static final int SEPARATOR_GAP = 6;
	private static final String ELLIPSIS = "...";
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	static final Font TITLE_FONT_SMALL = FontManager.getRunescapeBoldFont().deriveFont(16f);
	static final Color SEPARATOR_COLOR = new Color(80, 70, 50);

	protected static final Color CLOG_RED = new Color(255, 0, 0);
	protected static final Color CLOG_GREEN = new Color(0, 255, 0);
	protected static final Color CLOG_YELLOW = new Color(255, 255, 0);
	protected static final Color COMPARE_BLUE = new Color(91, 164, 207);
	protected static final Color COMPARE_RED = new Color(224, 86, 86);
	protected static final String CHROME_SEPARATOR = " | ";

	private String title;
	private String subtitleLabel;
	private String subtitleValue;
	private Color subtitleColor;
	private String infoLabel;
	private String infoValue;
	private Color infoColor;
	private String rankText;

	/** Set the bold orange title line (always required). */
	public void setTitle(String title)
	{
		this.title = title;
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
		return (obtained < 0 ? "?" : String.valueOf(obtained)) + "/" + total;
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
		return value > 0 ? String.format("%,d", value) : "--";
	}

	/** Rank tail that flows after a score column, e.g. " #1,234,567". */
	protected static String rankTailText(int rank)
	{
		return " #" + String.format("%,d", rank);
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

	/** Set the rank line. 0 = "Unranked". */
	public void setRank(int rank)
	{
		if (rank > 0)
		{
			this.rankText = String.format("%,d", rank);
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

		String label = fitHeaderRightText(fm, text, maxWidth);
		if (label.isEmpty())
		{
			return;
		}

		g2.setColor(getHeaderRightColor());
		g2.drawString(label, w - inset - fm.stringWidth(label), baseline);
	}

	private static String fitHeaderRightText(FontMetrics fm, String text, int maxWidth)
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
		int subTextWidth = subtitleLabel != null
			? sfm.stringWidth(subtitleLabel + subtitleValue) : 0;
		int infoTextWidth = infoLabel != null
			? sfm.stringWidth(infoLabel + infoValue) : 0;
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
		// Title (bold orange)
		g2.setFont(getTitleFont());
		FontMetrics nfm = g2.getFontMetrics();
		int lineY = inset + nfm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(title, inset, lineY);
		int activeLineWidth = nfm.stringWidth(title);

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// Subtitle (label in orange, value in subtitleColor)
		if (subtitleLabel != null)
		{
			lineY += NAME_LINE_HEIGHT;
			g2.setColor(OSRS_ORANGE);
			g2.drawString(subtitleLabel, inset, lineY);
			int labelWidth = fm.stringWidth(subtitleLabel);
			g2.setColor(subtitleColor);
			g2.drawString(subtitleValue, inset + labelWidth, lineY);
			activeLineWidth = labelWidth + fm.stringWidth(subtitleValue);
		}

		// Info line (e.g. Glory for Sol Heredit)
		if (infoLabel != null)
		{
			lineY += LINE_HEIGHT;
			g2.setColor(OSRS_ORANGE);
			g2.drawString(infoLabel, inset, lineY);
			int infoLabelWidth = fm.stringWidth(infoLabel);
			g2.setColor(infoColor);
			g2.drawString(infoValue, inset + infoLabelWidth, lineY);
			activeLineWidth = infoLabelWidth + fm.stringWidth(infoValue);
		}

		// Rank line
		if (rankText != null)
		{
			lineY += LINE_HEIGHT;
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

		paintHeaderRightText(g2, fm, w, lineY, activeLineWidth);

		// Separator
		int sepY = lineY + SEPARATOR_GAP;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		return sepY + 1 + SEPARATOR_GAP;
	}
}
