package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

/**
 * 3x8 skill grid tooltip for the total level cell.
 * The hover readouts (XP/Rank) sit directly under the title, so the values
 * that change with the hovered skill live where the changing title is;
 * Total Exp anchors the bottom and never moves. Matches the in-game skills
 * tab layout with icon + level per cell.
 */
@Slf4j
public class SkillsTooltip extends TitleTooltip
{
	private static final int COLS = 3;
	private static final int ROWS = 8;
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 3;
	private static final int ROW_HEIGHT = 18;
	private static final int COL_GAP = 8;
	private static final int GOTR_SECTION_GAP = 5;
	static final int GOTR_ICON_GAP = 3;
	// The rifts readout carries no inline label; hovering the row names it
	// in the title, the same reveal the skill cells use.
	static final String RIFTS_HOVER_LABEL = "Rifts Closed";

	// The reserved readout rows directly under the title: labels always
	// present, exact values (1..200,000,000 xp) while a skill is hovered.
	// Shared with CompareSkillSummaryTooltip, which paints one value per side.
	static final String XP_ROW_LABEL = "XP: ";
	static final String RANK_ROW_LABEL = "Rank: ";
	static final String XP_ROW_SAMPLE = "200,000,000";
	static final String RANK_ROW_SAMPLE = "9,999,999";
	static final String TOTAL_EXP_LABEL = "Total Exp: ";

	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	// In-game skills tab layout: 3 columns x 8 rows
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

	private static final Map<Skill, BufferedImage> icons = new LinkedHashMap<>();

	/** Package-private accessor so CompareSkillSummaryTooltip can reuse loaded icons. */
	static Map<Skill, BufferedImage> getIcons()
	{
		return icons;
	}

	private HiscoreResult result;
	private boolean virtualLevels;

	// The body start the painter was actually handed. Hit-tests reuse it so
	// hover regions and painted rows cannot disagree; header-height
	// arithmetic approximates the title's font ascent and drifts by a few
	// pixels. -1 until the first paint.
	private int paintedBodyStartY = -1;

	private BufferedImage gotrIcon;
	private int gotrRifts = -1;
	private int gotrObtained = -1;
	private int gotrTotal;
	private Skill hoveredSkill;
	private boolean gotrRowHovered;

	public SkillsTooltip()
	{
		installSkillHoverHandlers();
	}

	/**
	 * Load skill icons from RuneLite's bundled resources.
	 * Call once at panel construction time.
	 */
	public static void loadIcons(SkillIconManager skillIconManager)
	{
		for (Skill[] row : GRID)
		{
			for (Skill skill : row)
			{
				try
				{
					BufferedImage img = skillIconManager.getSkillImage(skill, true);
					if (img != null)
					{
						icons.put(skill, img);
					}
				}
				catch (Exception e)
				{
					log.debug("Failed to load icon for {}", skill.getName(), e);
				}
			}
		}
	}

	public void setData(HiscoreResult result)
	{
		this.result = result;
		setTitle("Skill Summary");
	}

	/** Follows RuneLite's core Virtual Levels plugin toggle, read at build time. */
	public void setVirtualLevels(boolean virtualLevels)
	{
		this.virtualLevels = virtualLevels;
	}

	public void setGotr(ClogResult clogResult, BufferedImage icon, int riftsClosed)
	{
		this.gotrIcon = icon;
		this.gotrRifts = riftsClosed;
		int[] counts = ClogHelper.clogCounts(PanelData.GOTR_CATEGORY, clogResult);
		if (counts != null)
		{
			this.gotrObtained = counts[0];
			this.gotrTotal = counts[1];
		}
	}

	static String formatCompactXp(long totalXp)
	{
		if (totalXp <= 0)
		{
			return "--";
		}
		if (totalXp >= 1_000_000_000L)
		{
			return String.format(Locale.US, "%.2fB", totalXp / 1_000_000_000.0);
		}
		return Math.round(totalXp / 1_000_000.0) + "M";
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int totalWidth = Math.max(gridWidth, Math.max(measureGotrWidth(fm),
			Math.max(statsRowsWidth(fm), fm.stringWidth(TOTAL_EXP_LABEL + totalExpValue()))));
		int totalHeight = 2 * LINE_HEIGHT + GOTR_SECTION_GAP + ROW_HEIGHT * ROWS
			+ GOTR_SECTION_GAP + LINE_HEIGHT + LINE_HEIGHT;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureGotrWidth(FontMetrics fm)
	{
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + GOTR_ICON_GAP : 0;
		return iconW
			+ fm.stringWidth(riftsText(gotrRifts))
			+ gotrProgressWidth(fm, gotrObtained, gotrTotal);
	}

	private static int statsRowsWidth(FontMetrics fm)
	{
		return Math.max(
			fm.stringWidth(XP_ROW_LABEL) + fm.stringWidth(XP_ROW_SAMPLE),
			fm.stringWidth(RANK_ROW_LABEL) + fm.stringWidth(RANK_ROW_SAMPLE));
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int inset = getInset();

		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		paintedBodyStartY = startY;

		// Hover readouts first: the values that change with the hovered skill
		// sit under the changing title, so hovering moves only the top of the
		// tooltip; the grid, rifts, and Total Exp below never shift.
		paintStatsRows(g2, fm, inset, startY);
		int gridTop = startY + 2 * LINE_HEIGHT + GOTR_SECTION_GAP;

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				Skill skill = GRID[row][col];
				int x = gridOffsetX + col * (cellWidth + COL_GAP);
				int y = gridTop + row * ROW_HEIGHT;

				// Icon
				BufferedImage icon = icons.get(skill);
				if (icon != null)
				{
					int iconY = y + (ROW_HEIGHT - icon.getHeight()) / 2;
					g2.drawImage(icon, x, iconY, null);
				}

				// Level text
				String skillKey = skill.getName().toLowerCase();
				int level = result != null
					? ClogHelper.displayLevel(result.getSkillLevel(skillKey),
						result.getSkillXp(skillKey), virtualLevels)
					: -1;
				String text = level > 0 ? String.valueOf(level) : "--";
				g2.setColor(level > 0 ? Color.WHITE : UNRANKED_COLOR);
				int textX = x + ICON_SIZE + ICON_TEXT_GAP + (maxLevelWidth - fm.stringWidth(text)) / 2;
				int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
				g2.drawString(text, textX, textY);
			}
		}

		int footerY = gridTop + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP;
		paintGotr(g2, fm, inset, w, footerY);
		paintTotalExp(g2, fm, inset, footerY + LINE_HEIGHT);
	}

	/** Total Exp anchors the bottom; it never changes while hovering. */
	private void paintTotalExp(Graphics2D g2, FontMetrics fm, int inset, int y)
	{
		int textY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(TOTAL_EXP_LABEL, inset, textY);
		long totalXp = result != null ? result.getTotalXp() : -1;
		g2.setColor(totalXp > 0 ? Color.WHITE : UNRANKED_COLOR);
		g2.drawString(totalExpValue(), inset + fm.stringWidth(TOTAL_EXP_LABEL), textY);
	}

	private String totalExpValue()
	{
		return result != null && result.getTotalXp() > 0
			? String.format(Locale.US, "%,d", result.getTotalXp()) : "--";
	}

	/**
	 * The reserved readout: XP and Rank labels always hold their rows; the
	 * hovered skill fills the exact values. The title carries the skill name
	 * (see {@link #getTitleHoverText}), and the rows sit directly under it.
	 */
	private void paintStatsRows(Graphics2D g2, FontMetrics fm, int inset, int y)
	{
		String skillName = hoveredSkill != null ? hoveredSkill.getName().toLowerCase() : null;
		long xp = skillName != null && result != null ? result.getSkillXp(skillName) : -1;
		int rank = skillName != null && result != null ? result.getSkillRank(skillName) : -1;

		int xpY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(XP_ROW_LABEL, inset, xpY);
		String xpText = xp > 0 ? String.format(Locale.US, "%,d", xp) : "--";
		g2.setColor(xp > 0 ? Color.WHITE : UNRANKED_COLOR);
		g2.drawString(xpText, inset + fm.stringWidth(XP_ROW_LABEL), xpY);

		int rankY = xpY + LINE_HEIGHT;
		g2.setColor(OSRS_ORANGE);
		g2.drawString(RANK_ROW_LABEL, inset, rankY);
		String rankValue = rank > 0 ? String.format(Locale.US, "%,d", rank) : "--";
		g2.setColor(rank > 0 ? Color.WHITE : UNRANKED_COLOR);
		g2.drawString(rankValue, inset + fm.stringWidth(RANK_ROW_LABEL), rankY);
	}

	@Override
	protected String getTitleHoverText()
	{
		if (hoveredSkill != null)
		{
			return hoveredSkill.getName();
		}
		return gotrRowHovered ? RIFTS_HOVER_LABEL : null;
	}

	@Override
	protected Color getTitleHoverColor()
	{
		// The hovered skill name stands in for the title, so it wears the
		// title's own orange rather than a value color.
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

	/** Top of the skill grid: painted body start, then the two hover-readout rows. */
	private int gridTopOffset()
	{
		int bodyStart = paintedBodyStartY >= 0
			? paintedBodyStartY
			: getInset() + getHeaderZoneHeight();
		return bodyStart + 2 * LINE_HEIGHT + GOTR_SECTION_GAP;
	}

	/** The full-width band the rifts readout sits on, under the skill grid. */
	private boolean gotrRowContains(int mouseX, int mouseY)
	{
		int y = gridTopOffset() + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP;
		return new Rectangle(getInset(), y, getWidth() - 2 * getInset(), LINE_HEIGHT)
			.contains(mouseX, mouseY);
	}

	private Skill skillAt(int mouseX, int mouseY)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int gridOffsetX = getInset() + (getWidth() - 2 * getInset() - gridWidth) / 2;
		int gridOffsetY = gridTopOffset();

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

	private void paintGotr(Graphics2D g2, FontMetrics fm, int inset, int w, int y)
	{
		String rifts = riftsText(gotrRifts);
		int rowW = measureGotrWidth(fm);
		int x = inset + (w - 2 * inset - rowW) / 2;
		int textY = y + fm.getAscent();

		if (gotrIcon != null)
		{
			int iconY = y + (LINE_HEIGHT - gotrIcon.getHeight()) / 2;
			g2.drawImage(gotrIcon, x, iconY, null);
			x += gotrIcon.getWidth() + GOTR_ICON_GAP;
		}

		g2.setColor(Color.WHITE);
		g2.drawString(rifts, x, textY);
		x += fm.stringWidth(rifts);
		paintGotrProgress(g2, fm, x, textY, gotrObtained, gotrTotal);
	}

	static String riftsText(int rifts)
	{
		return rifts >= 0 ? String.format(Locale.US, "%,d", rifts) : "--";
	}

	private static int gotrProgressWidth(FontMetrics fm, int obtained, int total)
	{
		return wrappedProgressCountWidthOrDash(fm, obtained, total);
	}

	private static int paintGotrProgress(Graphics2D g2, FontMetrics fm, int x, int y,
		int obtained, int total)
	{
		return paintWrappedProgressCountOrDash(g2, fm, x, y, obtained, total, UNRANKED_COLOR);
	}
}
