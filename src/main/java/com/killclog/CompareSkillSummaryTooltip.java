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
import lombok.Setter;
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
	private static final int STATS_GAP = 2;

	private static final Color ARROW_GREEN = CLOG_GREEN;
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	// One player's column of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String name;
		HiscoreResult result;
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	private BufferedImage chaliceSprite;
	private Skill hoveredSkill;

	public CompareSkillSummaryTooltip()
	{
		installSkillHoverHandlers();
	}

	@Setter
	private boolean virtualLevels; // Kill Clog's Display Virtual Levels setting, read at build time

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
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int totalWidth = Math.max(gridWidth, statsRowsWidth(fm));
		int totalHeight = fm.getHeight() + STATS_GAP + 2 * LINE_HEIGHT
			+ HEADER_GAP + ROW_HEIGHT * SkillGridOrder.ROWS;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureCellWidth(FontMetrics fm)
	{
		int levelW = fm.stringWidth("99");
		int arrowSlot = ARROW_PAD + ARROW_SIZE;
		return ICON_SIZE + ICON_TEXT_GAP + levelW + arrowSlot + LEVEL_GAP + levelW + arrowSlot;
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
		y += fm.getHeight() + STATS_GAP;
		paintStatsRows(g2, fm, inset, y);
		y += 2 * LINE_HEIGHT + HEADER_GAP;

		// Skill grid.
		int levelW = fm.stringWidth("99");
		int arrowSlot = ARROW_PAD + ARROW_SIZE;
		int cellWidth = measureCellWidth(fm);
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		Map<Skill, BufferedImage> icons = SkillsTooltip.getIcons();

		for (int row = 0; row < SkillGridOrder.ROWS; row++)
		{
			for (int col = 0; col < SkillGridOrder.COLUMNS; col++)
			{
				Skill skill = SkillGridOrder.at(row, col);
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
				int blueLevel = blue.result != null
					? ClogHelper.displayLevel(blue.result.getSkillLevel(skillName),
						blue.result.getSkillXp(skillName), virtualLevels)
					: -1;
				int redLevel = red.result != null
					? ClogHelper.displayLevel(red.result.getSkillLevel(skillName),
						red.result.getSkillXp(skillName), virtualLevels)
					: -1;
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

	}

	/**
	 * XP defaults to each player's total XP. Hovering a skill switches XP and
	 * Rank to that skill while the rows stay fixed above the grid.
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
		long xp = side.result == null ? -1 : skillName != null
			? side.result.getSkillXp(skillName) : side.result.getTotalXp();
		return xp >= 0 ? String.format(Locale.US, "%,d", xp) : "--";
	}

	String blueDisplayedXpText()
	{
		return xpValue(blue, hoveredSkill != null
			? hoveredSkill.getName().toLowerCase() : null);
	}

	String redDisplayedXpText()
	{
		return xpValue(red, hoveredSkill != null
			? hoveredSkill.getName().toLowerCase() : null);
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
		return null;
	}

	@Override
	protected Color getTitleHoverColor()
	{
		// Matches the solo skill tooltip: the hovered skill name takes the
		// title slot, so it takes the title's color too.
		return OSRS_ORANGE;
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
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int gridOffsetX = getInset() + (getWidth() - 2 * getInset() - gridWidth) / 2;
		int gridOffsetY = getInset() + getHeaderZoneHeight()
			+ fm.getHeight() + STATS_GAP + 2 * LINE_HEIGHT + HEADER_GAP;

		for (int row = 0; row < SkillGridOrder.ROWS; row++)
		{
			for (int col = 0; col < SkillGridOrder.COLUMNS; col++)
			{
				int x = gridOffsetX + col * (cellWidth + COL_GAP);
				int y = gridOffsetY + row * ROW_HEIGHT;
				if (new Rectangle(x, y, cellWidth, ROW_HEIGHT).contains(mouseX, mouseY))
				{
					return SkillGridOrder.at(row, col);
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
