package com.killclog;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.FontManager;

/**
 * Shareable Kill Clog player profile rendered as one expanded native popup.
 * Parchment, iron frame, typography, separators, and source colors come from
 * the same primitives as the in-client Kill Clog tooltips.
 */
final class ProfileCard
{
	static final int WIDTH = 720;
	static final int HEIGHT = 420;

	private static final int PAD = 18;
	private static final int PORTRAIT_X = 18;
	private static final int PORTRAIT_Y = 62;
	private static final int PORTRAIT_WIDTH = 190;
	private static final int PORTRAIT_HEIGHT = 270;
	private static final int CONTENT_X = 226;
	private static final int SECOND_COLUMN_X = 474;
	private static final int RECENT_SPRITE = 40;
	private static final int RECENT_GAP = 24;

	private static final Color TEXT = new Color(255, 255, 255);
	private static final Color TEXT_SOFT = new Color(212, 212, 212);
	private static final Color TEXT_MUTED = TitleTooltip.MUTED_GRAY;
	private static final Color SOURCE = TitleTooltip.CLOG_GREEN;
	private static final Color SEPARATOR = TitleTooltip.SEPARATOR_COLOR;
	private static final Color ORANGE = NativeTooltip.OSRS_ORANGE;
	private static final Color INSET = new Color(12, 10, 8, 118);
	private static final Color INSET_HEADER = new Color(29, 23, 17, 185);

	private ProfileCard()
	{
	}

	/** Snapshot of the already-loaded lookup state. No network work belongs here. */
	static final class Data
	{
		String rsn;
		String accountLabel;
		BufferedImage accountIcon;
		BufferedImage pluginIcon;
		BufferedImage playerModel;
		int overallRank = -1;
		int obtained = -1;
		int total = -1;
		String tierName;
		BufferedImage tierIcon;
		Color completionColor;
		int combatLevel = -1;
		int totalLevel = -1;
		int questPoints = -1;
		String caTier;
		int caPoints = -1;
		int bossesWithKc = -1;
		int totalBosses = -1;
		int pets = -1;
		int totalPets = -1;
		int personalBests = -1;
		BufferedImage[] recentSprites;
		String[] recentDates;
		String clogSource;
		String personalBestSource;
		String updated;
		String profileUrl;
	}

	static BufferedImage render(Data data)
	{
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		configure(g);
		NativeTooltip.paintFrame(g, WIDTH, HEIGHT);

		Font small = FontManager.getRunescapeSmallFont();
		Font base = FontManager.getRunescapeFont();
		Font bold = FontManager.getRunescapeBoldFont();
		paintHeader(g, data, base, bold);
		paintPortrait(g, data, small);
		paintProfileColumns(g, data, base, bold);
		paintRecent(g, data, small, bold);
		paintSources(g, data, small);

		g.dispose();
		return image;
	}

	private static void configure(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	}

	private static void paintHeader(Graphics2D g, Data data, Font base, Font bold)
	{
		int x = PAD;
		if (data.accountIcon != null)
		{
			g.drawImage(data.accountIcon, x, 14, 18, 18, null);
			x += 24;
		}

		g.setFont(bold.deriveFont(20f));
		g.setColor(ORANGE);
		String name = text(data.rsn, "Unknown player");
		g.drawString(name, x, 29);
		x += g.getFontMetrics().stringWidth(name) + 10;
		if (data.accountLabel != null && !data.accountLabel.isBlank())
		{
			g.setFont(base.deriveFont(15f));
			g.setColor(TEXT_SOFT);
			String identity = data.accountLabel + (data.overallRank > 0
				? " #" + number(data.overallRank) : "");
			g.drawString(identity, x, 29);
		}

		paintHorizontalSeparator(g, 52);
	}

	private static void paintPortrait(Graphics2D g, Data data, Font small)
	{
		g.setColor(INSET);
		g.fillRect(PORTRAIT_X, PORTRAIT_Y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT);
		g.setColor(SEPARATOR);
		g.drawRect(PORTRAIT_X, PORTRAIT_Y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT);
		g.setColor(INSET_HEADER);
		g.fillRect(PORTRAIT_X + 1, PORTRAIT_Y + 1, PORTRAIT_WIDTH - 1, 20);
		g.setColor(SEPARATOR);
		g.drawLine(PORTRAIT_X + 1, PORTRAIT_Y + 21,
			PORTRAIT_X + PORTRAIT_WIDTH - 1, PORTRAIT_Y + 21);
		g.setFont(small);
		g.setColor(ORANGE);
		String title = "CURRENT EQUIPMENT";
		g.drawString(title,
			PORTRAIT_X + (PORTRAIT_WIDTH - g.getFontMetrics().stringWidth(title)) / 2,
			PORTRAIT_Y + 15);

		g.setColor(new Color(0, 0, 0, 72));
		g.fillOval(PORTRAIT_X + 28, PORTRAIT_Y + PORTRAIT_HEIGHT - 25,
			PORTRAIT_WIDTH - 56, 13);
		if (data.playerModel != null)
		{
			g.drawImage(data.playerModel, PORTRAIT_X + 7, PORTRAIT_Y + 16, null);
		}
		else
		{
			g.setColor(TEXT_MUTED);
			String unavailable = "Live model unavailable";
			g.drawString(unavailable,
				PORTRAIT_X + (PORTRAIT_WIDTH - g.getFontMetrics().stringWidth(unavailable)) / 2,
				PORTRAIT_Y + PORTRAIT_HEIGHT / 2);
		}
	}

	private static void paintProfileColumns(Graphics2D g, Data data, Font base, Font bold)
	{
		int titleY = 78;

		g.setFont(bold.deriveFont(16f));
		g.setColor(ORANGE);
		g.drawString("ACCOUNT", CONTENT_X, titleY);
		g.drawString("COLLECTION LOG", SECOND_COLUMN_X, titleY);

		g.setColor(SEPARATOR);
		g.drawLine(CONTENT_X, 86, SECOND_COLUMN_X - 20, 86);
		g.drawLine(SECOND_COLUMN_X, 86, WIDTH - PAD, 86);
		g.drawLine(SECOND_COLUMN_X - 14, 62, SECOND_COLUMN_X - 14, 215);

		g.setFont(base.deriveFont(15f));
		int y = 107;
		y = paintValueLine(g, CONTENT_X, y, "Combat: ", optionalNumber(data.combatLevel), TEXT);
		y = paintValueLine(g, CONTENT_X, y, "Total Level: ", optionalNumber(data.totalLevel), TEXT);
		y = paintValueLine(g, CONTENT_X, y, "Quest Points: ", optionalNumber(data.questPoints), TEXT);
		y = paintValueLine(g, CONTENT_X, y, "CA Tier: ", caValue(data), TEXT);
		y = paintValueLine(g, CONTENT_X, y, "Boss KCs: ",
			fraction(data.bossesWithKc, data.totalBosses), TEXT);
		paintValueLine(g, CONTENT_X, y, "Personal Bests: ",
			optionalNumber(data.personalBests), TEXT);

		y = 107;
		Color completion = data.completionColor != null ? data.completionColor : TEXT;
		y = paintValueLine(g, SECOND_COLUMN_X, y, "Obtained: ",
			fraction(data.obtained, data.total), completion);
		y = paintTierLine(g, data, SECOND_COLUMN_X, y);
		String percent = data.total > 0
			? Math.round(100d * data.obtained / data.total) + "%" : "--";
		y = paintValueLine(g, SECOND_COLUMN_X, y, "Completion: ", percent, completion);
		y = paintValueLine(g, SECOND_COLUMN_X, y, "Pets: ",
			fraction(data.pets, data.totalPets), TEXT);
		paintValueLine(g, SECOND_COLUMN_X, y, "Updated: ", text(data.updated, "--"),
			data.updated != null ? SOURCE : TEXT_MUTED);

		g.setColor(SEPARATOR);
		g.drawLine(CONTENT_X, 226, WIDTH - PAD, 226);
	}

	private static int paintTierLine(Graphics2D g, Data data, int x, int y)
	{
		String tier = data.tierName != null ? capitalize(data.tierName) : "--";
		g.setColor(ORANGE);
		String label = "Clog Tier: ";
		g.drawString(label, x, y);
		int valueX = x + g.getFontMetrics().stringWidth(label);
		if (data.tierIcon != null)
		{
			g.drawImage(data.tierIcon, valueX, y - 13, 17, 17, null);
			valueX += 20;
		}
		g.setColor(TEXT);
		g.drawString(tier, valueX, y);
		return y + 18;
	}

	private static int paintValueLine(Graphics2D g, int x, int y,
		String label, String value, Color valueColor)
	{
		g.setColor(ORANGE);
		g.drawString(label, x, y);
		g.setColor(valueColor);
		g.drawString(value, x + g.getFontMetrics().stringWidth(label), y);
		return y + 18;
	}

	private static void paintRecent(Graphics2D g, Data data, Font small, Font bold)
	{
		g.setFont(bold.deriveFont(16f));
		g.setColor(ORANGE);
		g.drawString("RECENT UNLOCKS", CONTENT_X, 250);

		if (!hasSprites(data.recentSprites))
		{
			g.setFont(small);
			g.setColor(TEXT_MUTED);
			g.drawString("No recent collection log items", CONTENT_X, 279);
			paintHorizontalSeparator(g, 341);
			return;
		}

		int count = Math.min(data.recentSprites.length, 6);
		int rowWidth = count * RECENT_SPRITE + Math.max(0, count - 1) * RECENT_GAP;
		int contentWidth = WIDTH - CONTENT_X - PAD;
		int spriteX = CONTENT_X + (contentWidth - rowWidth) / 2;
		int spriteY = 263;
		for (int i = 0; i < count; i++)
		{
			BufferedImage sprite = data.recentSprites[i];
			if (sprite != null)
			{
				g.drawImage(sprite, spriteX, spriteY, RECENT_SPRITE, RECENT_SPRITE, null);
			}
			String date = data.recentDates != null && i < data.recentDates.length
				? data.recentDates[i] : null;
			if (date != null)
			{
				g.setFont(small);
				g.setColor(TEXT_MUTED);
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(date, spriteX + (RECENT_SPRITE - metrics.stringWidth(date)) / 2,
					spriteY + RECENT_SPRITE + 15);
			}
			spriteX += RECENT_SPRITE + RECENT_GAP;
		}
		paintHorizontalSeparator(g, 341);
	}

	private static void paintSources(Graphics2D g, Data data, Font small)
	{
		g.setFont(small);
		int y = 363;
		if (data.clogSource != null && !data.clogSource.isBlank())
		{
			paintSourceLine(g, PAD, "Collection Log: ", data.clogSource, y);
		}
		if (data.personalBestSource != null && !data.personalBestSource.isBlank())
		{
			paintSourceLine(g, WIDTH / 2, "Personal Bests: ", data.personalBestSource, y);
		}

		g.setFont(small);
		int footerY = HEIGHT - 14;
		if (data.profileUrl != null)
		{
			g.setColor(ORANGE);
			int urlX = WIDTH - PAD - g.getFontMetrics().stringWidth(data.profileUrl);
			g.drawString(data.profileUrl, urlX, footerY);
			if (data.pluginIcon != null)
			{
				g.drawImage(data.pluginIcon, urlX - 20, footerY - 12, 14, 14, null);
			}
		}
	}

	private static void paintSourceLine(Graphics2D g, int x,
		String label, String source, int y)
	{
		g.setColor(SOURCE);
		g.drawString("\u2191", x, y);
		g.setColor(ORANGE);
		g.drawString(label, x + 13, y);
		int sourceX = x + 13 + g.getFontMetrics().stringWidth(label);
		g.setColor(SOURCE);
		g.drawString(source, sourceX, y);
	}

	private static void paintHorizontalSeparator(Graphics2D g, int y)
	{
		g.setColor(SEPARATOR);
		g.drawLine(PAD, y, WIDTH - PAD, y);
	}

	private static boolean hasSprites(BufferedImage[] sprites)
	{
		if (sprites == null)
		{
			return false;
		}
		for (BufferedImage sprite : sprites)
		{
			if (sprite != null)
			{
				return true;
			}
		}
		return false;
	}

	private static String caValue(Data data)
	{
		if (data.caTier == null)
		{
			return "--";
		}
		String tier = capitalize(data.caTier.replace('_', ' '));
		return data.caPoints >= 0
			? tier + "  " + number(data.caPoints) + " pts"
			: tier;
	}

	private static String optionalNumber(int value)
	{
		return value >= 0 ? number(value) : "--";
	}

	private static String fraction(int value, int total)
	{
		if (value < 0)
		{
			return "--";
		}
		return total >= 0 ? number(value) + "/" + number(total) : number(value);
	}

	private static String number(int value)
	{
		return String.format(Locale.US, "%,d", Math.max(0, value));
	}

	private static String text(String value, String fallback)
	{
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String capitalize(String value)
	{
		if (value.isBlank())
		{
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
	}
}
