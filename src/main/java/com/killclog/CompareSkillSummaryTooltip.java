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
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * Comparison skill summary tooltip. 3x8 grid matching the in-game
 * skills tab. Each cell: skill icon + blue level + red level with
 * green arrow next to the higher value. The overall winner uses total
 * level, then XP as the tiebreak.
 */
public class CompareSkillSummaryTooltip extends TitleTooltip
{
	private static final int COLS = 3;
	private static final int ROWS = 8;
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 2;
	private static final int ROW_HEIGHT = 18;
	private static final int COL_GAP = 6;
	private static final int LEVEL_GAP = 3;
	private static final int HEADER_GAP = 4;
	private static final int CHALICE_SIZE = 15;
	private static final int CHALICE_GAP = 3;
	private static final int ARROW_SIZE = 5;
	private static final int ARROW_PAD = 2;
	private static final int XP_ROW_GAP = 2;
	private static final int XP_FIELD_GAP = 8;
	private static final int GOTR_SECTION_GAP = 5;
	private static final int GOTR_ICON_GAP = 3;
	private static final String XP_LABEL = "XP: ";
	private static final String RIFTS_LABEL = "Rifts: ";

	private static final Color ARROW_GREEN = CLOG_GREEN;
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	// In-game skills tab layout.
	private static final Skill[][] GRID = {
		{Skill.ATTACK,       Skill.HITPOINTS,   Skill.MINING},
		{Skill.DEFENCE,      Skill.AGILITY,     Skill.SMITHING},
		{Skill.STRENGTH,     Skill.HERBLORE,    Skill.FISHING},
		{Skill.RANGED,       Skill.THIEVING,    Skill.COOKING},
		{Skill.PRAYER,       Skill.CRAFTING,     Skill.FIREMAKING},
		{Skill.MAGIC,        Skill.FLETCHING,    Skill.WOODCUTTING},
		{Skill.RUNECRAFT,    Skill.SLAYER,       Skill.FARMING},
		{Skill.CONSTRUCTION, Skill.HUNTER,       Skill.SAILING},
	};

	private String blueName;
	private String redName;
	private HiscoreResult blueResult;
	private HiscoreResult redResult;
	private BufferedImage chaliceSprite;
	private BufferedImage gotrIcon;
	private int blueGotrRifts = -1;
	private int blueGotrObtained = -1;
	private int blueGotrTotal;
	private int redGotrRifts = -1;
	private int redGotrObtained = -1;
	private int redGotrTotal;
	private Skill hoveredSkill;

	public CompareSkillSummaryTooltip()
	{
		installSkillHoverHandlers();
	}

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setData(String blueName, HiscoreResult blueResult,
		String redName, HiscoreResult redResult)
	{
		setTitle("Skill Summary");
		this.blueName = blueName;
		this.blueResult = blueResult;
		this.redName = redName;
		this.redResult = redResult;
		try
		{
			BufferedImage raw = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			chaliceSprite = ImageUtil.resizeImage(raw, CHALICE_SIZE, CHALICE_SIZE);
		}
		catch (Exception ignored)
		{
		}
	}

	public void setGotr(ClogResult blueClog, ClogResult redClog, BufferedImage icon)
	{
		this.gotrIcon = icon;
		this.blueGotrRifts = blueResult != null
			? blueResult.getActivityScore(PanelData.RIFTS_CLOSED_ACTIVITY) : -1;
		this.redGotrRifts = redResult != null
			? redResult.getActivityScore(PanelData.RIFTS_CLOSED_ACTIVITY) : -1;
		int[] blueCounts = ClogHelper.clogCounts(PanelData.GOTR_CATEGORY, blueClog);
		if (blueCounts != null)
		{
			this.blueGotrObtained = blueCounts[0];
			this.blueGotrTotal = blueCounts[1];
		}
		int[] redCounts = ClogHelper.clogCounts(PanelData.GOTR_CATEGORY, redClog);
		if (redCounts != null)
		{
			this.redGotrObtained = redCounts[0];
			this.redGotrTotal = redCounts[1];
		}
	}

	// Winner logic.

	/**
	 * Determine overall winner by total level, then XP tiebreak.
	 * @return 1 = blue wins, -1 = red wins, 0 = tie
	 */
	private int overallWinner()
	{
		int blueLvl = blueResult != null ? blueResult.getTotalLevel() : 0;
		int redLvl = redResult != null ? redResult.getTotalLevel() : 0;
		if (blueLvl != redLvl)
		{
			return blueLvl > redLvl ? 1 : -1;
		}
		long blueXp = blueResult != null ? blueResult.getTotalXp() : 0;
		long redXp = redResult != null ? redResult.getTotalXp() : 0;
		if (blueXp != redXp)
		{
			return blueXp > redXp ? 1 : -1;
		}
		return 0;
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int cellWidth = measureCellWidth(fm);
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int xpWidth = measureXpRowWidth(fm);
		int gotrWidth = measureGotrWidth(fm);
		int totalWidth = Math.max(Math.max(Math.max(gridWidth, xpWidth), gotrWidth),
			measureSkillStatsWidth(fm));
		int totalHeight = fm.getHeight() + XP_ROW_GAP + LINE_HEIGHT
			+ HEADER_GAP + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP + LINE_HEIGHT;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureCellWidth(FontMetrics fm)
	{
		int levelW = fm.stringWidth("99");
		int arrowSlot = ARROW_PAD + ARROW_SIZE;
		return ICON_SIZE + ICON_TEXT_GAP + levelW + arrowSlot + LEVEL_GAP + levelW + arrowSlot;
	}

	private int measureXpRowWidth(FontMetrics fm)
	{
		return xpFieldWidth(fm, totalXpText(blueResult))
			+ XP_FIELD_GAP
			+ xpFieldWidth(fm, totalXpText(redResult));
	}

	private int measureGotrWidth(FontMetrics fm)
	{
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + GOTR_ICON_GAP : 0;
		return iconW
			+ fm.stringWidth(RIFTS_LABEL)
			+ gotrValueWidth(fm, blueGotrRifts, blueGotrObtained, blueGotrTotal)
			+ fm.stringWidth(CHROME_SEPARATOR)
			+ gotrValueWidth(fm, redGotrRifts, redGotrObtained, redGotrTotal);
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int y = startY;

		// Header.
		int winner = overallWinner();
		paintHeader(g2, fm, inset, w, y, winner);
		y += fm.getHeight() + XP_ROW_GAP;
		paintXpRow(g2, fm, inset, w, y);
		y += LINE_HEIGHT + HEADER_GAP;

		// Skill grid.
		int levelW = fm.stringWidth("99");
		int arrowSlot = ARROW_PAD + ARROW_SIZE;
		int cellWidth = measureCellWidth(fm);
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		Map<Skill, BufferedImage> icons = SkillsTooltip.getIcons();

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				Skill skill = GRID[row][col];
				int cellX = gridOffsetX + col * (cellWidth + COL_GAP);
				int cellY = y + row * ROW_HEIGHT;

				// Icon.
				BufferedImage icon = icons.get(skill);
				if (icon != null)
				{
					int iconY = cellY + (ROW_HEIGHT - icon.getHeight()) / 2;
					g2.drawImage(icon, cellX, iconY, null);
				}

				// Skill levels.
				String skillName = skill.getName().toLowerCase();
				int blueLevel = blueResult != null ? blueResult.getSkillLevel(skillName) : -1;
				int redLevel = redResult != null ? redResult.getSkillLevel(skillName) : -1;
				String blueText = blueLevel > 0 ? String.valueOf(blueLevel) : "--";
				String redText = redLevel > 0 ? String.valueOf(redLevel) : "--";

				int textY = cellY + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
				int levelX = cellX + ICON_SIZE + ICON_TEXT_GAP;

				// Blue level.
				int blueW = fm.stringWidth(blueText);
				g2.setColor(COMPARE_BLUE);
				g2.drawString(blueText, levelX + levelW - blueW, textY);

				// Green arrow if blue is higher.
				if (blueLevel > 0 && redLevel > 0 && blueLevel > redLevel)
				{
					paintArrow(g2, levelX + levelW + ARROW_PAD,
						cellY + (ROW_HEIGHT - ARROW_SIZE) / 2);
				}

				// Red level.
				int redLevelX = levelX + levelW + arrowSlot + LEVEL_GAP;
				int redW = fm.stringWidth(redText);
				g2.setColor(COMPARE_RED);
				g2.drawString(redText, redLevelX + levelW - redW, textY);

				// Green arrow if red is higher.
				if (blueLevel > 0 && redLevel > 0 && redLevel > blueLevel)
				{
					paintArrow(g2, redLevelX + levelW + ARROW_PAD,
						cellY + (ROW_HEIGHT - ARROW_SIZE) / 2);
				}
			}
		}

		int footerY = y + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP;
		if (hoveredSkill != null)
		{
			paintSkillStats(g2, fm, inset, w, footerY, hoveredSkill);
		}
		else
		{
			paintGotr(g2, fm, inset, w, footerY);
		}
	}

	private void installSkillHoverHandlers()
	{
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHoveredSkill(skillAt(e.getX(), e.getY()));
			}
		});

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				setHoveredSkill(null);
			}
		});
	}

	private void setHoveredSkill(Skill skill)
	{
		if (hoveredSkill != skill)
		{
			hoveredSkill = skill;
			repaint();
		}
	}

	private Skill skillAt(int mouseX, int mouseY)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int cellWidth = measureCellWidth(fm);
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int gridOffsetX = getInset() + (getWidth() - 2 * getInset() - gridWidth) / 2;
		int gridOffsetY = getInset() + getHeaderZoneHeight()
			+ fm.getHeight() + XP_ROW_GAP + LINE_HEIGHT + HEADER_GAP;

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				int x = gridOffsetX + col * (cellWidth + COL_GAP);
				int y = gridOffsetY + row * ROW_HEIGHT;
				if (new Rectangle(x, y, cellWidth, ROW_HEIGHT).contains(mouseX, mouseY))
				{
					return GRID[row][col];
				}
			}
		}
		return null;
	}

	private int measureSkillStatsWidth(FontMetrics fm)
	{
		int iconW = ICON_SIZE + GOTR_ICON_GAP;
		return iconW
			+ skillStatsValueWidth(fm)
			+ fm.stringWidth(CHROME_SEPARATOR)
			+ skillStatsValueWidth(fm);
	}

	private int skillStatsValueWidth(FontMetrics fm)
	{
		return SkillsTooltip.skillStatsValueWidth(fm);
	}

	private void paintSkillStats(Graphics2D g2, FontMetrics fm, int inset, int w, int y,
		Skill skill)
	{
		int rowW = measureSkillStatsWidth(fm);
		int x = inset + (w - 2 * inset - rowW) / 2;
		int textY = y + fm.getAscent();

		BufferedImage icon = SkillsTooltip.getIcons().get(skill);
		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
			g2.drawImage(icon, x, iconY, null);
		}
		x += ICON_SIZE + GOTR_ICON_GAP;

		x = paintSkillStatsValue(g2, fm, x, textY, blueResult, skill);
		x = paintChromeSeparator(g2, fm, x, textY);
		paintSkillStatsValue(g2, fm, x, textY, redResult, skill);
	}

	private int paintSkillStatsValue(Graphics2D g2, FontMetrics fm, int x, int y,
		HiscoreResult result, Skill skill)
	{
		return SkillsTooltip.paintSkillStatsValue(g2, fm, x, y, result, skill);
	}

	private void paintHeader(Graphics2D g2, FontMetrics fm, int inset, int w, int y, int winner)
	{
		int bx = inset;
		if (winner == 1 && chaliceSprite != null)
		{
			int iconY = y + (fm.getHeight() - CHALICE_SIZE) / 2;
			g2.drawImage(chaliceSprite, bx, iconY, null);
			bx += CHALICE_SIZE + CHALICE_GAP;
		}
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueName != null ? blueName : "--", bx, y + fm.getAscent());

		String redText = redName != null ? redName : "--";
		int redNameW = fm.stringWidth(redText);
		int chaliceW = winner == -1 && chaliceSprite != null ? CHALICE_SIZE + CHALICE_GAP : 0;
		int rx = w - inset - redNameW - chaliceW;
		g2.setColor(COMPARE_RED);
		g2.drawString(redText, rx, y + fm.getAscent());

		if (winner == -1 && chaliceSprite != null)
		{
			int iconY = y + (fm.getHeight() - CHALICE_SIZE) / 2;
			g2.drawImage(chaliceSprite, rx + redNameW + CHALICE_GAP, iconY, null);
		}
	}

	private void paintXpRow(Graphics2D g2, FontMetrics fm, int inset, int w, int y)
	{
		int textY = y + fm.getAscent();
		String blueXp = totalXpText(blueResult);
		String redXp = totalXpText(redResult);
		int redX = w - inset - xpFieldWidth(fm, redXp);

		paintXpField(g2, fm, inset, textY, blueXp, COMPARE_BLUE);
		paintXpField(g2, fm, redX, textY, redXp, COMPARE_RED);
	}

	private static int paintXpField(Graphics2D g2, FontMetrics fm, int x, int y,
		String value, Color valueColor)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(XP_LABEL, x, y);
		x += fm.stringWidth(XP_LABEL);

		g2.setColor(valueColor);
		g2.drawString(value, x, y);
		return x + fm.stringWidth(value);
	}

	private static int xpFieldWidth(FontMetrics fm, String value)
	{
		return fm.stringWidth(XP_LABEL) + fm.stringWidth(value);
	}

	private static String totalXpText(HiscoreResult result)
	{
		return result != null ? SkillsTooltip.formatCompactXp(result.getTotalXp()) : "--";
	}

	private void paintGotr(Graphics2D g2, FontMetrics fm, int inset, int w, int y)
	{
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + GOTR_ICON_GAP : 0;
		int rowW = iconW + fm.stringWidth(RIFTS_LABEL)
			+ gotrValueWidth(fm, blueGotrRifts, blueGotrObtained, blueGotrTotal)
			+ fm.stringWidth(CHROME_SEPARATOR)
			+ gotrValueWidth(fm, redGotrRifts, redGotrObtained, redGotrTotal);
		int x = inset + (w - 2 * inset - rowW) / 2;
		int textY = y + fm.getAscent();

		if (gotrIcon != null)
		{
			int iconY = y + (LINE_HEIGHT - gotrIcon.getHeight()) / 2;
			g2.drawImage(gotrIcon, x, iconY, null);
			x += gotrIcon.getWidth() + GOTR_ICON_GAP;
		}

		g2.setColor(OSRS_ORANGE);
		g2.drawString(RIFTS_LABEL, x, textY);
		x += fm.stringWidth(RIFTS_LABEL);
		x = paintGotrValue(g2, fm, x, textY,
			blueGotrRifts, blueGotrObtained, blueGotrTotal, COMPARE_BLUE);
		x = paintChromeSeparator(g2, fm, x, textY);
		paintGotrValue(g2, fm, x, textY,
			redGotrRifts, redGotrObtained, redGotrTotal, COMPARE_RED);
	}

	private static int gotrValueWidth(FontMetrics fm, int rifts, int obtained, int total)
	{
		return fm.stringWidth(riftsText(rifts))
			+ wrappedProgressCountWidthOrDash(fm, obtained, total);
	}

	private static int paintGotrValue(Graphics2D g2, FontMetrics fm, int x, int y,
		int rifts, int obtained, int total, Color playerColor)
	{
		String riftsValue = riftsText(rifts);
		g2.setColor(playerColor);
		g2.drawString(riftsValue, x, y);
		x += fm.stringWidth(riftsValue);
		return paintWrappedProgressCountOrDash(g2, fm, x, y, obtained, total, UNRANKED_COLOR);
	}

	private static String riftsText(int rifts)
	{
		return rifts >= 0 ? String.format("%,d", rifts) : "--";
	}

	/**
	 * Paint a small filled green up-pointing triangle.
	 */
	private void paintArrow(Graphics2D g2, int x, int y)
	{
		int[] xPoints = {x, x + ARROW_SIZE / 2, x + ARROW_SIZE};
		int[] yPoints = {y + ARROW_SIZE, y, y + ARROW_SIZE};
		g2.setColor(ARROW_GREEN);
		g2.fillPolygon(xPoints, yPoints, 3);
	}
}
