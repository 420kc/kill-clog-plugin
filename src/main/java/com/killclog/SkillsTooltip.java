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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;

/**
 * 3x8 skill grid tooltip for the total level cell.
 * The XP/Rank readouts sit directly under the title. XP defaults to total XP,
 * then both rows switch to the hovered skill. Matches the in-game skills tab
 * layout with icon + level per cell.
 */
@Slf4j
public class SkillsTooltip extends TitleTooltip
{
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 3;
	private static final int ROW_HEIGHT = 18;
	private static final int COL_GAP = 8;
	private static final int GRID_GAP = 5;

	// The reserved readout rows directly under the title: labels always
	// present, exact values (1..200,000,000 xp) while a skill is hovered.
	// Shared with CompareSkillSummaryTooltip, which paints one value per side.
	static final String XP_ROW_LABEL = "XP: ";
	static final String RANK_ROW_LABEL = "Rank: ";
	static final String XP_ROW_SAMPLE = "4,800,000,000";
	static final String RANK_ROW_SAMPLE = "9,999,999";

	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	private static final Map<Skill, BufferedImage> icons = new LinkedHashMap<>();

	/** Package-private accessor so CompareSkillSummaryTooltip can reuse loaded icons. */
	static Map<Skill, BufferedImage> getIcons()
	{
		return icons;
	}

	private HiscoreResult result;
	@Setter
	private boolean virtualLevels; // Kill Clog's Display Virtual Levels setting, read at build time

	// The body start the painter was actually handed. Hit-tests reuse it so
	// hover regions and painted rows cannot disagree; header-height
	// arithmetic approximates the title's font ascent and drifts by a few
	// pixels. -1 until the first paint.
	private int paintedBodyStartY = -1;

	private Skill hoveredSkill;

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
		for (Skill skill : SkillGridOrder.skills())
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

	public void setData(HiscoreResult result)
	{
		this.result = result;
		setTitle("Skill Summary");
	}


	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int totalWidth = Math.max(gridWidth, statsRowsWidth(fm));
		int totalHeight = 2 * LINE_HEIGHT + GRID_GAP
			+ ROW_HEIGHT * SkillGridOrder.ROWS;
		return new Dimension(totalWidth, totalHeight);
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
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int gridOffsetX = inset + (w - 2 * inset - gridWidth) / 2;

		paintedBodyStartY = startY;

		// The readouts stay above the grid. XP defaults to total XP, then both
		// values switch to the hovered skill without moving the grid.
		paintStatsRows(g2, fm, inset, startY);
		int gridTop = startY + 2 * LINE_HEIGHT + GRID_GAP;

		for (int row = 0; row < SkillGridOrder.ROWS; row++)
		{
			for (int col = 0; col < SkillGridOrder.COLUMNS; col++)
			{
				Skill skill = SkillGridOrder.at(row, col);
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

	}

	/**
	 * The reserved readout: XP and Rank labels always hold their rows; the
	 * hovered skill fills the exact values. The title carries the skill name
	 * (see {@link #getTitleHoverText}), and the rows sit directly under it.
	 */
	private void paintStatsRows(Graphics2D g2, FontMetrics fm, int inset, int y)
	{
		String skillName = hoveredSkill != null ? hoveredSkill.getName().toLowerCase() : null;
		long xp = displayedXp(skillName);
		int rank = skillName != null && result != null ? result.getSkillRank(skillName) : -1;

		int xpY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(XP_ROW_LABEL, inset, xpY);
		String xpText = xp >= 0 ? String.format(Locale.US, "%,d", xp) : "--";
		g2.setColor(xp >= 0 ? Color.WHITE : UNRANKED_COLOR);
		g2.drawString(xpText, inset + fm.stringWidth(XP_ROW_LABEL), xpY);

		int rankY = xpY + LINE_HEIGHT;
		g2.setColor(OSRS_ORANGE);
		g2.drawString(RANK_ROW_LABEL, inset, rankY);
		String rankValue = rank > 0 ? String.format(Locale.US, "%,d", rank) : "--";
		g2.setColor(rank > 0 ? Color.WHITE : UNRANKED_COLOR);
		g2.drawString(rankValue, inset + fm.stringWidth(RANK_ROW_LABEL), rankY);
	}

	String displayedXpText()
	{
		long xp = displayedXp(hoveredSkill != null
			? hoveredSkill.getName().toLowerCase() : null);
		return xp >= 0 ? String.format(Locale.US, "%,d", xp) : "--";
	}

	private long displayedXp(String skillName)
	{
		return result == null ? -1 : skillName != null
			? result.getSkillXp(skillName) : result.getTotalXp();
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

	/** Top of the skill grid: painted body start, then the two hover-readout rows. */
	private int gridTopOffset()
	{
		int bodyStart = paintedBodyStartY >= 0
			? paintedBodyStartY
			: getInset() + getHeaderZoneHeight();
		return bodyStart + 2 * LINE_HEIGHT + GRID_GAP;
	}

	private Skill skillAt(int mouseX, int mouseY)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * SkillGridOrder.COLUMNS
			+ COL_GAP * (SkillGridOrder.COLUMNS - 1);
		int gridOffsetX = getInset() + (getWidth() - 2 * getInset() - gridWidth) / 2;
		int gridOffsetY = gridTopOffset();

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

}
