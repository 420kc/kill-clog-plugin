package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * A movable 3x8 grid of real skill cells with compact native stat tooltips.
 * Skill-specific collection-log sections can grow below those stats later.
 */
final class SkillCellGrid
{
	private static final String COMPARE_BLUE_HEX = colorHex(TitleTooltip.COMPARE_BLUE);
	private static final String COMPARE_RED_HEX = colorHex(TitleTooltip.COMPARE_RED);
	private static final String COMPARE_SEPARATOR_HEX = "#949494";

	private final JPanel component = new JPanel();
	private final JPanel grid = new JPanel(
		new GridLayout(SkillGridOrder.ROWS, SkillGridOrder.COLUMNS));
	private final Map<Skill, JLabel> labels = new LinkedHashMap<>();
	private final TooltipController tooltipController;
	private final KillClogConfig config;
	@Nullable
	private HiscoreResult primary;
	@Nullable
	private HiscoreResult compared;
	private boolean virtualLevels;

	SkillCellGrid(SkillIconManager skillIconManager,
		TooltipController tooltipController, KillClogConfig config)
	{
		this.tooltipController = tooltipController;
		this.config = config;

		component.setLayout(new javax.swing.BoxLayout(
			component, javax.swing.BoxLayout.Y_AXIS));
		component.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		component.setAlignmentX(0f);

		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setAlignmentX(0f);
		for (Skill skill : SkillGridOrder.skills())
		{
			grid.add(buildCell(skill, skillIconManager));
		}
		component.add(grid);
		component.add(buildDivider());
	}

	private JPanel buildCell(Skill skill, SkillIconManager skillIconManager)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildTooltip(this, skill);
			}
		};
		Cells.styleLabel(label, " ");
		try
		{
			BufferedImage image = skillIconManager.getSkillImage(skill, true);
			if (image != null)
			{
				label.setIcon(new ImageIcon(ImageUtil.resizeCanvas(image, 25, 25)));
			}
		}
		catch (Exception ignored)
		{
			// A missing icon must not suppress the skill's level.
		}

		JPanel cell = new JPanel();
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(TooltipController.CELL_BORDER);
		cell.add(label);
		tooltipController.addCellHoverEffect(cell, label);
		labels.put(skill, label);
		return cell;
	}

	private static JPanel buildDivider()
	{
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(0, 7));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		divider.setAlignmentX(0f);
		return divider;
	}

	void render(HiscoreResult primary, @Nullable HiscoreResult compared,
		boolean virtualLevels)
	{
		this.primary = primary;
		this.compared = compared;
		this.virtualLevels = virtualLevels;
		for (Map.Entry<Skill, JLabel> entry : labels.entrySet())
		{
			Skill skill = entry.getKey();
			JLabel label = entry.getValue();
			int primaryLevel = level(primary, skill, virtualLevels);
			if (compared != null)
			{
				int comparedLevel = level(compared, skill, virtualLevels);
				renderComparison(label, primaryLevel, comparedLevel);
			}
			else
			{
				renderSolo(label, primaryLevel);
			}
		}
	}

	void clear()
	{
		primary = null;
		compared = null;
		for (JLabel label : labels.values())
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setHorizontalAlignment(JLabel.LEADING);
		}
	}

	private JToolTip buildTooltip(JLabel owner, Skill skill)
	{
		JToolTip tooltip;
		if (compared != null)
		{
			CompareSkillTooltip comparison = new CompareSkillTooltip();
			comparison.setData(skill, primary, compared, virtualLevels);
			tooltip = comparison;
		}
		else
		{
			SkillTooltip solo = new SkillTooltip();
			solo.setData(skill, primary, virtualLevels);
			tooltip = solo;
		}
		tooltip.setComponent(owner);
		tooltipController.keepTooltipOnHover(tooltip, (JPanel) owner.getParent());
		return tooltip;
	}

	private void renderSolo(JLabel label, int level)
	{
		label.setText(ClogHelper.pad(levelText(level)));
		label.setForeground(level > 0
			? SkillLevelColor.forLevel(level, config)
			: ColorScheme.LIGHT_GRAY_COLOR);
		label.setHorizontalAlignment(JLabel.LEADING);
	}

	private static void renderComparison(JLabel label, int primaryLevel, int comparedLevel)
	{
		label.setText("<html><span style='color:" + COMPARE_BLUE_HEX + ";'>"
			+ levelText(primaryLevel) + "</span>"
			+ "<span style='color:" + COMPARE_SEPARATOR_HEX + ";'>/</span>"
			+ "<span style='color:" + COMPARE_RED_HEX + ";'>"
			+ levelText(comparedLevel) + "</span></html>");
		label.setForeground(TitleTooltip.COMPARE_BLUE);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	private static int level(HiscoreResult result, Skill skill, boolean virtualLevels)
	{
		String key = skill.getName().toLowerCase(Locale.ROOT);
		return ClogHelper.displayLevel(result.getSkillLevel(key),
			result.getSkillXp(key), virtualLevels);
	}

	private static String levelText(int level)
	{
		return level > 0 ? String.valueOf(level) : "--";
	}

	private static String colorHex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}

	JPanel component()
	{
		return component;
	}

	JPanel grid()
	{
		return grid;
	}

	Map<Skill, JLabel> labels()
	{
		return Collections.unmodifiableMap(labels);
	}
}
