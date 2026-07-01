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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

/**
 * 3x8 skill grid tooltip for the total level cell.
 * Header shows "Skill Summary" + optional Total Exp via TitleTooltip.
 * Matches the in-game skills tab layout with icon + level per cell.
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
	private static final int GOTR_ICON_GAP = 3;
	private static final String RIFTS_LABEL = "Rifts: ";

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
	private BufferedImage gotrIcon;
	private int gotrRifts = -1;
	private int gotrObtained = -1;
	private int gotrTotal;
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
		if (result != null && result.getTotalXp() > 0)
		{
			setSubtitle("Total Exp: ", String.format("%,d", result.getTotalXp()), Color.WHITE);
		}
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
			return String.format(java.util.Locale.US, "%.2fB", totalXp / 1_000_000_000.0);
		}
		return Math.round(totalXp / 1_000_000.0) + "M";
	}

	static String skillXpText(long xp)
	{
		if (xp <= 0)
		{
			return "--";
		}
		if (xp >= 1_000_000L)
		{
			return String.format(java.util.Locale.US, "%.1fM", xp / 1_000_000.0);
		}
		if (xp >= 1_000L)
		{
			return Math.round(xp / 1_000.0) + "K";
		}
		return String.valueOf(xp);
	}

	static String rankText(int rank)
	{
		return rank > 0 ? String.format("%,d", rank) : "--";
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int totalWidth = Math.max(gridWidth, measureGotrWidth(fm));
		int totalHeight = ROW_HEIGHT * ROWS + GOTR_SECTION_GAP + LINE_HEIGHT;
		return new Dimension(totalWidth, totalHeight);
	}

	private int measureGotrWidth(FontMetrics fm)
	{
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + GOTR_ICON_GAP : 0;
		int gotrWidth = iconW
			+ fm.stringWidth(RIFTS_LABEL)
			+ fm.stringWidth(riftsText(gotrRifts))
			+ gotrProgressWidth(fm, gotrObtained, gotrTotal);
		return Math.max(gotrWidth, measureSkillStatsWidth(fm));
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

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				Skill skill = GRID[row][col];
				int x = gridOffsetX + col * (cellWidth + COL_GAP);
				int y = startY + row * ROW_HEIGHT;

				// Icon
				BufferedImage icon = icons.get(skill);
				if (icon != null)
				{
					int iconY = y + (ROW_HEIGHT - icon.getHeight()) / 2;
					g2.drawImage(icon, x, iconY, null);
				}

				// Level text
				int level = result != null ? result.getSkillLevel(skill.getName().toLowerCase()) : -1;
				String text = level > 0 ? String.valueOf(level) : "--";
				g2.setColor(level > 0 ? Color.WHITE : UNRANKED_COLOR);
				int textX = x + ICON_SIZE + ICON_TEXT_GAP + (maxLevelWidth - fm.stringWidth(text)) / 2;
				int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
				g2.drawString(text, textX, textY);
			}
		}

		int footerY = startY + ROW_HEIGHT * ROWS + GOTR_SECTION_GAP;
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
		int maxLevelWidth = Math.max(fm.stringWidth("99"), fm.stringWidth("--"));
		int cellWidth = ICON_SIZE + ICON_TEXT_GAP + maxLevelWidth;
		int gridWidth = cellWidth * COLS + COL_GAP * (COLS - 1);
		int gridOffsetX = getInset() + (getWidth() - 2 * getInset() - gridWidth) / 2;
		int gridOffsetY = getInset() + getHeaderZoneHeight();

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
			+ fm.stringWidth("#")
			+ fm.stringWidth("9,999,999")
			+ fm.stringWidth(" XP: ")
			+ fm.stringWidth("200.0M");
	}

	private void paintSkillStats(Graphics2D g2, FontMetrics fm, int inset, int w, int y,
		Skill skill)
	{
		int rowW = measureSkillStatsWidth(fm);
		int x = inset + (w - 2 * inset - rowW) / 2;
		int textY = y + fm.getAscent();

		BufferedImage icon = icons.get(skill);
		if (icon != null)
		{
			int iconY = y + (LINE_HEIGHT - icon.getHeight()) / 2;
			g2.drawImage(icon, x, iconY, null);
		}
		x += ICON_SIZE + GOTR_ICON_GAP;

		String skillName = skill.getName().toLowerCase();
		String rank = rankText(result != null ? result.getSkillRank(skillName) : -1);
		String xp = skillXpText(result != null ? result.getSkillXp(skillName) : -1);

		g2.setColor(OSRS_ORANGE);
		g2.drawString("#", x, textY);
		x += fm.stringWidth("#");
		g2.setColor(Color.WHITE);
		g2.drawString(rank, x, textY);
		x += fm.stringWidth(rank);
		g2.setColor(OSRS_ORANGE);
		g2.drawString(" XP: ", x, textY);
		x += fm.stringWidth(" XP: ");
		g2.setColor(Color.WHITE);
		g2.drawString(xp, x, textY);
	}

	private void paintGotr(Graphics2D g2, FontMetrics fm, int inset, int w, int y)
	{
		String rifts = riftsText(gotrRifts);
		int iconW = gotrIcon != null ? gotrIcon.getWidth() + GOTR_ICON_GAP : 0;
		int rowW = iconW
			+ fm.stringWidth(RIFTS_LABEL)
			+ fm.stringWidth(rifts)
			+ gotrProgressWidth(fm, gotrObtained, gotrTotal);
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
		g2.setColor(Color.WHITE);
		g2.drawString(rifts, x, textY);
		x += fm.stringWidth(rifts);
		paintGotrProgress(g2, fm, x, textY, gotrObtained, gotrTotal);
	}

	private static String riftsText(int rifts)
	{
		return rifts >= 0 ? String.format("%,d", rifts) : "--";
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
