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
import java.util.Locale;
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
	private static final String XP_LABEL = "XP: ";

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

	// One player's column of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String name;
		HiscoreResult result;
		int gotrRifts = -1;
		int gotrObtained = -1;
		int gotrTotal;
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	private BufferedImage chaliceSprite;
	private BufferedImage gotrIcon;
	private Skill hoveredSkill;
	private boolean gotrRowHovered;

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
		blue.name = blueName;
		blue.result = blueResult;
		red.name = redName;
		red.result = redResult;
		try
		{
			BufferedImage raw = KillClogIcons.pluginIcon();
			chaliceSprite = raw != null
				? ImageUtil.resizeImage(raw, CHALICE_SIZE, CHALICE_SIZE) : null;
		}
		catch (Exception ignored)
		{
		}
	}

	public void setGotr(ClogResult blueClog, ClogResult redClog, BufferedImage icon)
	{
		this.gotrIcon = icon;
		loadGotr(blue, blueClog);
		loadGotr(red, redClog);
	}

	private static void loadGotr(Side side, ClogResult clog)
	{
		side.gotrRifts = side.result != null
			? side.result.getActivityScore(PanelData.RIFTS_CLOSED_ACTIVITY) : -1;
		int[] counts = ClogHelper.clogCounts(PanelData.GOTR_CATEGORY, clog);
		if (counts != null)
		{
			side.gotrObtained = counts[0];
			side.gotrTotal = counts[1];
		}
	}

	// Winner logic.

	/**
	 * Determine overall winner by total level, then XP tiebreak.
	 * @return 1 = blue wins, -1 = red wins, 0 = tie
	 */
	private int overallWinner()
	{
		int blueLvl = blue.result != null ? blue.result.getTotalLevel() : 0;
		int redLvl = red.result != null ? red.result.getTotalLevel() : 0;
		if (blueLvl != redLvl)
		{
			return blueLvl > redLvl ? 1 : -1;
		}
		long blueXp = blue.result != null ? blue.result.getTotalXp() : 0;
		long redXp = red.result != null ? red.result.getTotalXp() : 0;
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
			statsRowsWidth(fm));
		int totalHeight = fm.getHeight() + XP_ROW_GAP + LINE_HEIGHT
			+ HEADER_GAP + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP + LINE_HEIGHT
			+ 2 * LINE_HEIGHT;
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
		return xpFieldWidth(fm, totalXpText(blue.result))
			+ XP_FIELD_GAP
			+ xpFieldWidth(fm, totalXpText(red.result));
	}

	private int measureGotrWidth(FontMetrics fm)
	{
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + SkillsTooltip.GOTR_ICON_GAP : 0;
		return iconW
			+ gotrValueWidth(fm, blue)
			+ fm.stringWidth(CHROME_SEPARATOR)
			+ gotrValueWidth(fm, red);
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
				int blueLevel = blue.result != null ? blue.result.getSkillLevel(skillName) : -1;
				int redLevel = red.result != null ? red.result.getSkillLevel(skillName) : -1;
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
		paintGotr(g2, fm, inset, w, footerY);
		paintStatsRows(g2, fm, inset, footerY + LINE_HEIGHT);
	}

	/**
	 * The reserved readout: XP and Rank labels always hold their rows, one
	 * blue and one red value while a skill is hovered. The title carries the
	 * skill name (see {@link #getTitleHoverText}), so the rows never move.
	 */
	private void paintStatsRows(Graphics2D g2, FontMetrics fm, int inset, int y)
	{
		String skillName = hoveredSkill != null ? hoveredSkill.getName().toLowerCase() : null;

		int xpY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(SkillsTooltip.XP_ROW_LABEL, inset, xpY);
		int x = inset + fm.stringWidth(SkillsTooltip.XP_ROW_LABEL);
		x = paintSideValue(g2, fm, x, xpY, xpValue(blue, skillName), COMPARE_BLUE);
		x = paintChromeSeparator(g2, fm, x, xpY);
		paintSideValue(g2, fm, x, xpY, xpValue(red, skillName), COMPARE_RED);

		int rankY = xpY + LINE_HEIGHT;
		g2.setColor(OSRS_ORANGE);
		g2.drawString(SkillsTooltip.RANK_ROW_LABEL, inset, rankY);
		x = inset + fm.stringWidth(SkillsTooltip.RANK_ROW_LABEL);
		x = paintSideValue(g2, fm, x, rankY, rankValue(blue, skillName), COMPARE_BLUE);
		x = paintChromeSeparator(g2, fm, x, rankY);
		paintSideValue(g2, fm, x, rankY, rankValue(red, skillName), COMPARE_RED);
	}

	private static String xpValue(Side side, String skillName)
	{
		long xp = skillName != null && side.result != null
			? side.result.getSkillXp(skillName) : -1;
		return xp > 0 ? String.format(Locale.US, "%,d", xp) : "--";
	}

	private static String rankValue(Side side, String skillName)
	{
		int rank = skillName != null && side.result != null
			? side.result.getSkillRank(skillName) : -1;
		return rank > 0 ? String.format(Locale.US, "%,d", rank) : "--";
	}

	private static int paintSideValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String value, Color playerColor)
	{
		g2.setColor("--".equals(value) ? UNRANKED_COLOR : playerColor);
		g2.drawString(value, x, y);
		return x + fm.stringWidth(value);
	}

	private static int statsRowsWidth(FontMetrics fm)
	{
		int xpRow = fm.stringWidth(SkillsTooltip.XP_ROW_LABEL)
			+ 2 * fm.stringWidth(SkillsTooltip.XP_ROW_SAMPLE)
			+ fm.stringWidth(CHROME_SEPARATOR);
		int rankRow = fm.stringWidth(SkillsTooltip.RANK_ROW_LABEL)
			+ 2 * fm.stringWidth(SkillsTooltip.RANK_ROW_SAMPLE)
			+ fm.stringWidth(CHROME_SEPARATOR);
		return Math.max(xpRow, rankRow);
	}

	@Override
	protected String getTitleHoverText()
	{
		if (hoveredSkill != null)
		{
			return hoveredSkill.getName();
		}
		return gotrRowHovered ? SkillsTooltip.RIFTS_HOVER_LABEL : null;
	}

	@Override
	protected Color getTitleHoverColor()
	{
		return Color.WHITE;
	}

	private void installSkillHoverHandlers()
	{
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHoveredSkill(skillAt(e.getX(), e.getY()));
				setGotrRowHovered(gotrRowContains(e.getX(), e.getY()));
			}
		});

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				setHoveredSkill(null);
				setGotrRowHovered(false);
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

	private void setGotrRowHovered(boolean hovered)
	{
		if (gotrRowHovered != hovered)
		{
			gotrRowHovered = hovered;
			repaint();
		}
	}

	/** The full-width band the rifts readout sits on, under the skill grid. */
	private boolean gotrRowContains(int mouseX, int mouseY)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int y = getInset() + getHeaderZoneHeight()
			+ fm.getHeight() + XP_ROW_GAP + LINE_HEIGHT + HEADER_GAP
			+ ROW_HEIGHT * ROWS + GOTR_SECTION_GAP;
		return new Rectangle(getInset(), y, getWidth() - 2 * getInset(), LINE_HEIGHT)
			.contains(mouseX, mouseY);
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
		g2.drawString(blue.name != null ? blue.name : "--", bx, y + fm.getAscent());

		String redText = red.name != null ? red.name : "--";
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
		String blueXp = totalXpText(blue.result);
		String redXp = totalXpText(red.result);
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
		int rowW = measureGotrWidth(fm);
		int x = inset + (w - 2 * inset - rowW) / 2;
		int textY = y + fm.getAscent();

		if (gotrIcon != null)
		{
			int iconY = y + (LINE_HEIGHT - gotrIcon.getHeight()) / 2;
			g2.drawImage(gotrIcon, x, iconY, null);
			x += gotrIcon.getWidth() + SkillsTooltip.GOTR_ICON_GAP;
		}

		x = paintGotrValue(g2, fm, x, textY, blue, COMPARE_BLUE);
		x = paintChromeSeparator(g2, fm, x, textY);
		paintGotrValue(g2, fm, x, textY, red, COMPARE_RED);
	}

	private static int gotrValueWidth(FontMetrics fm, Side side)
	{
		return fm.stringWidth(SkillsTooltip.riftsText(side.gotrRifts))
			+ wrappedProgressCountWidthOrDash(fm, side.gotrObtained, side.gotrTotal);
	}

	private static int paintGotrValue(Graphics2D g2, FontMetrics fm, int x, int y,
		Side side, Color playerColor)
	{
		String riftsValue = SkillsTooltip.riftsText(side.gotrRifts);
		g2.setColor(playerColor);
		g2.drawString(riftsValue, x, y);
		x += fm.stringWidth(riftsValue);
		return paintWrappedProgressCountOrDash(g2, fm, x, y,
			side.gotrObtained, side.gotrTotal, UNRANKED_COLOR);
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
