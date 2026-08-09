package com.killclog;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.FontManager;

/**
 * Shareable Kill Clog player profile rendered as one expanded native popup.
 * Parchment, iron frame, typography, separators, and data colors come from
 * the same primitives as the in-client Kill Clog tooltips.
 */
final class ProfileCard
{
	static final int WIDTH = 760;
	static final int HEIGHT = 456;

	private static final int PAD = 18;
	private static final int PORTRAIT_X = 18;
	private static final int PORTRAIT_Y = 62;
	private static final int PORTRAIT_WIDTH = 190;
	private static final int PORTRAIT_HEIGHT = 352;
	private static final int CONTENT_X = 226;
	private static final int SECOND_COLUMN_X = 500;
	private static final int RARE_SPRITE = 38;
	private static final int RARE_GAP = 22;
	private static final int PET_SPRITE = 16;
	private static final int PET_GAP = 3;

	private static final Color TEXT = new Color(255, 255, 255);
	private static final Color TEXT_SOFT = new Color(212, 212, 212);
	private static final Color TEXT_MUTED = TitleTooltip.MUTED_GRAY;
	private static final Color ORANGE = NativeTooltip.OSRS_ORANGE;
	private static final Color INSET = new Color(12, 10, 8, 118);

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
		int combatLevel = -1;
		int totalLevel = -1;
		long totalXp = -1;
		int questPoints = -1;
		String prestige;
		double ehb = -1;
		double templeEhp = -1;
		String caTier;
		int combatTasksCompleted = -1;
		int totalCombatTasks = -1;
		int bossesWithKc = -1;
		int totalBosses = -1;
		int totalClues = -1;
		int pets = -1;
		BufferedImage[] petSprites;
		BufferedImage[] rareSprites;
		String createdDate;
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
		paintRare(g, data, small, bold);
		paintFooter(g, data, small);

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
		NativeTooltip.paintInsetBorder(g, PORTRAIT_X, PORTRAIT_Y,
			PORTRAIT_WIDTH, PORTRAIT_HEIGHT);

		g.setColor(new Color(0, 0, 0, 72));
		g.fillOval(PORTRAIT_X + 28, PORTRAIT_Y + PORTRAIT_HEIGHT - 25,
			PORTRAIT_WIDTH - 56, 13);
		if (data.playerModel != null)
		{
			int modelY = PORTRAIT_Y + PORTRAIT_HEIGHT - data.playerModel.getHeight() - 14;
			g.drawImage(data.playerModel, PORTRAIT_X + 7, modelY, null);
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
		g.drawString("Account", CONTENT_X, titleY);
		g.drawString("Collection Log", SECOND_COLUMN_X, titleY);

		NativeTooltip.paintHorizontalDivider(g, CONTENT_X, 88,
			SECOND_COLUMN_X - CONTENT_X - 20);
		NativeTooltip.paintHorizontalDivider(g, SECOND_COLUMN_X, 88,
			WIDTH - PAD - SECOND_COLUMN_X);
		NativeTooltip.paintVerticalDivider(g, SECOND_COLUMN_X - 14, 62, 228);

		Font labelFont = bold.deriveFont(15f);
		Font valueFont = base.deriveFont(15f);
		int y = 109;
		y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"Combat: ", optionalNumber(data.combatLevel), TEXT);
		y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"Total Level: ", optionalNumber(data.totalLevel), TEXT);
		y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"Total XP: ", optionalNumber(data.totalXp), TEXT);
		y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"Quest Points: ", optionalNumber(data.questPoints), TEXT);
		if (data.caTier != null)
		{
			y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
				"CA Tier: ", capitalize(data.caTier.replace('_', ' ')), TEXT);
			y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
				"Combat Tasks Completed: ",
				fraction(data.combatTasksCompleted, data.totalCombatTasks), TEXT);
		}
		if (data.prestige != null && !data.prestige.isBlank())
		{
			y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
				"Prestige: ", data.prestige, TEXT);
		}
		y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"EHB: ", optionalDecimal(data.ehb), TEXT);
		if (data.templeEhp >= 0)
		{
			y = paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
				"Temple EHP: ", optionalDecimal(data.templeEhp), TEXT);
		}
		paintValueLine(g, labelFont, valueFont, CONTENT_X, y,
			"Bosses Killed: ", fraction(data.bossesWithKc, data.totalBosses), TEXT);

		y = 109;
		y = paintValueLine(g, labelFont, valueFont, SECOND_COLUMN_X, y,
			"Obtained: ", fraction(data.obtained, data.total), TEXT);
		y = paintTierLine(g, data, labelFont, valueFont, SECOND_COLUMN_X, y);
		String percent = data.total > 0
			? Math.round(100d * data.obtained / data.total) + "%" : "--";
		y = paintValueLine(g, labelFont, valueFont, SECOND_COLUMN_X, y,
			"Completion: ", percent, TEXT);
		y = paintValueLine(g, labelFont, valueFont, SECOND_COLUMN_X, y,
			"Total Clues: ", optionalNumber(data.totalClues), TEXT);
		y = paintValueLine(g, labelFont, valueFont, SECOND_COLUMN_X, y,
			"Updated: ", text(data.updated, "--"), TEXT);
		paintPets(g, data, labelFont, valueFont, SECOND_COLUMN_X, y + 2);

		NativeTooltip.paintHorizontalDivider(g, CONTENT_X, 300, WIDTH - PAD - CONTENT_X);
	}

	private static void paintPets(Graphics2D g, Data data, Font labelFont,
		Font valueFont, int x, int y)
	{
		paintValueLine(g, labelFont, valueFont, x, y, "Pets: ",
			optionalNumber(data.pets), TEXT);
		if (!hasSprites(data.petSprites))
		{
			return;
		}
		int maxWidth = WIDTH - PAD - x;
		int columns = Math.max(1, maxWidth / (PET_SPRITE + PET_GAP));
		int spriteY = y + 5;
		for (int i = 0; i < data.petSprites.length; i++)
		{
			BufferedImage sprite = data.petSprites[i];
			if (sprite == null)
			{
				continue;
			}
			int column = i % columns;
			int row = i / columns;
			g.drawImage(sprite, x + column * (PET_SPRITE + PET_GAP),
				spriteY + row * (PET_SPRITE + PET_GAP), PET_SPRITE, PET_SPRITE, null);
		}
	}

	private static int paintTierLine(Graphics2D g, Data data, Font labelFont,
		Font valueFont, int x, int y)
	{
		String tier = data.tierName != null ? capitalize(data.tierName) : "--";
		g.setFont(labelFont);
		g.setColor(ORANGE);
		String label = "Clog Tier: ";
		g.drawString(label, x, y);
		int valueX = x + g.getFontMetrics().stringWidth(label);
		if (data.tierIcon != null)
		{
			g.drawImage(data.tierIcon, valueX, y - 13, 17, 17, null);
			valueX += 20;
		}
		g.setFont(valueFont);
		g.setColor(TEXT);
		g.drawString(tier, valueX, y);
		return y + 18;
	}

	private static int paintValueLine(Graphics2D g, Font labelFont, Font valueFont,
		int x, int y, String label, String value, Color valueColor)
	{
		g.setFont(labelFont);
		g.setColor(ORANGE);
		g.drawString(label, x, y);
		int valueX = x + g.getFontMetrics().stringWidth(label);
		g.setFont(valueFont);
		g.setColor(valueColor);
		g.drawString(value, valueX, y);
		return y + 18;
	}

	private static void paintRare(Graphics2D g, Data data, Font small, Font bold)
	{
		g.setFont(bold.deriveFont(16f));
		g.setColor(ORANGE);
		g.drawString("Rare", CONTENT_X, 324);

		if (!hasSprites(data.rareSprites))
		{
			g.setFont(small);
			g.setColor(TEXT_MUTED);
			g.drawString("No rare trophies yet", CONTENT_X, 353);
			paintHorizontalSeparator(g, 414);
			return;
		}

		int count = Math.min(data.rareSprites.length, 6);
		int rowWidth = count * RARE_SPRITE + Math.max(0, count - 1) * RARE_GAP;
		int contentWidth = WIDTH - CONTENT_X - PAD;
		int spriteX = CONTENT_X + (contentWidth - rowWidth) / 2;
		int spriteY = 345;
		for (int i = 0; i < count; i++)
		{
			BufferedImage sprite = data.rareSprites[i];
			if (sprite != null)
			{
				g.drawImage(sprite, spriteX, spriteY, RARE_SPRITE, RARE_SPRITE, null);
			}
			spriteX += RARE_SPRITE + RARE_GAP;
		}
		paintHorizontalSeparator(g, 414);
	}

	private static void paintFooter(Graphics2D g, Data data, Font small)
	{
		g.setFont(small);
		int footerY = HEIGHT - 14;
		if (data.createdDate != null)
		{
			g.setColor(ORANGE);
			g.drawString(data.createdDate, PAD, footerY);
		}
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

	private static void paintHorizontalSeparator(Graphics2D g, int y)
	{
		NativeTooltip.paintHorizontalDivider(g, PAD, y, WIDTH - PAD * 2);
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

	private static String optionalNumber(int value)
	{
		return value >= 0 ? number(value) : "--";
	}

	private static String optionalNumber(long value)
	{
		return value >= 0 ? number(value) : "--";
	}

	private static String optionalDecimal(double value)
	{
		return value >= 0 ? String.format(Locale.US, "%,.1f", value) : "--";
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
		return number((long) value);
	}

	private static String number(long value)
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
