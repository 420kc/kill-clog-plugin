package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * A movable 3x8 grid of real skill cells with compact native stat tooltips.
 * Reusable skill collection-log sections grow below those stats when mapped.
 */
final class SkillCellGrid
{
	static final int SKILL_ICON_SIZE = 22;
	private static final int SKILL_ICON_CANVAS_SIZE = 25;

	private final JPanel component = new JPanel();
	private final JPanel grid = new JPanel(
		new GridLayout(SkillGridOrder.ROWS, SkillGridOrder.COLUMNS));
	private final Map<Skill, JLabel> labels = new LinkedHashMap<>();
	private final ComparisonController comparison;
	private final Supplier<String> blueName;
	private final Supplier<String> redName;
	private final TooltipController tooltipController;
	private final KillClogConfig config;
	@Nullable
	private final ItemManager itemManager;
	@Nullable
	private HiscoreResult primary;
	@Nullable
	private HiscoreResult compared;
	@Nullable
	private ClogResult primaryClog;
	@Nullable
	private ClogResult comparedClog;
	@Nullable
	private ClogResult catalog;
	private boolean virtualLevels;

	SkillCellGrid(SkillIconManager skillIconManager,
		TooltipController tooltipController, ComparisonController comparison,
		KillClogConfig config, @Nullable ItemManager itemManager,
		Supplier<String> blueName, Supplier<String> redName)
	{
		this.comparison = comparison;
		this.blueName = blueName;
		this.redName = redName;
		this.tooltipController = tooltipController;
		this.config = config;
		this.itemManager = itemManager;

		component.setLayout(new BoxLayout(component, BoxLayout.Y_AXIS));
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
				label.setIcon(new ImageIcon(ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(image, SKILL_ICON_CANVAS_SIZE,
						SKILL_ICON_CANVAS_SIZE), SKILL_ICON_SIZE, SKILL_ICON_SIZE)));
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
		comparison.installCompareCellHover(cell, label);
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
		render(primary, compared, virtualLevels, null, null, null);
	}

	void render(HiscoreResult primary, @Nullable HiscoreResult compared,
		boolean virtualLevels, @Nullable ClogResult primaryClog,
		@Nullable ClogResult comparedClog, @Nullable ClogResult catalog)
	{
		this.primary = primary;
		this.compared = compared;
		this.virtualLevels = virtualLevels;
		this.primaryClog = primaryClog;
		this.comparedClog = comparedClog;
		this.catalog = catalog;
		for (Map.Entry<Skill, JLabel> entry : labels.entrySet())
		{
			Skill skill = entry.getKey();
			JLabel label = entry.getValue();
			int primaryLevel = level(primary, skill, virtualLevels);
			if (compared != null)
			{
				int comparedLevel = level(compared, skill, virtualLevels);
				renderComparison(label, skill, primaryLevel, comparedLevel);
			}
			else
			{
				renderSolo(label, skill, primaryLevel);
			}
		}
	}

	void clear()
	{
		clear(null);
	}

	void clear(@Nullable ClogResult catalog)
	{
		primary = null;
		compared = null;
		primaryClog = null;
		comparedClog = null;
		this.catalog = catalog;
		for (JLabel label : labels.values())
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setHorizontalAlignment(JLabel.LEADING);
		}
	}

	private JToolTip buildTooltip(JLabel owner, Skill skill)
	{
		// In comparison mode each side is the ordinary solo tooltip, with the
		// other player's clog unioned into its sections so both cards show the
		// same slot census.
		JToolTip tooltip = compared != null
			? new SideBySideTooltip(
				blueName.get(), soloTooltip(skill, primary, primaryClog, comparedClog),
				redName.get(), soloTooltip(skill, compared, comparedClog, primaryClog))
			: soloTooltip(skill, primary, primaryClog, comparedClog);
		tooltip.setComponent(owner);
		tooltipController.keepTooltipOnHover(tooltip, (JPanel) owner.getParent());
		return tooltip;
	}

	private SkillTooltip soloTooltip(Skill skill, @Nullable HiscoreResult result,
		@Nullable ClogResult clog, @Nullable ClogResult otherClog)
	{
		SkillTooltip solo = new SkillTooltip();
		solo.setData(skill, result, virtualLevels,
			SkillClogSection.forSkill(skill, clog, otherClog, catalog), itemManager);
		if (skill == Skill.RUNECRAFT)
		{
			solo.setRiftsClosed(riftsClosed(result));
		}
		solo.setWikiLinksEnabled(config.wikiItemLinks());
		return solo;
	}

	private static int riftsClosed(@Nullable HiscoreResult result)
	{
		return result != null
			? result.getActivityScore(PanelData.RIFTS_CLOSED_ACTIVITY) : -1;
	}

	private void renderSolo(JLabel label, Skill skill, int level)
	{
		comparison.clearCompareCell(label);
		int obtained = -1;
		int total = -1;
		if (config.skillColorMode() == SkillColorMode.CLOG_PROGRESSION)
		{
			SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
				SkillClogSection.forSkill(skill, primaryClog, null, catalog), false);
			obtained = progress.obtained();
			total = progress.total();
		}
		label.setText(ClogHelper.pad(levelText(level)));
		label.setForeground(level > 0
			? SkillLevelColor.forCell(level, primaryClog != null, obtained, total, config)
			: ColorScheme.LIGHT_GRAY_COLOR);
		label.setHorizontalAlignment(JLabel.LEADING);
	}

	private void renderComparison(JLabel label, Skill skill, int primaryLevel, int comparedLevel)
	{
		// Same machinery as the boss cells: shared blue-over-red state plus
		// hover swapping to each player's configured skill colors.
		comparison.setCompareCell(label, primaryLevel, comparedLevel,
			hoverColor(skill, primaryLevel, primaryClog),
			hoverColor(skill, comparedLevel, comparedClog));
	}

	/**
	 * The hovered value color for one player's side, from the same skill color
	 * configs solo cells use. Null (no swap) when that player has no clog,
	 * matching the boss cells' synced-only hover rule.
	 */
	@Nullable
	private Color hoverColor(Skill skill, int level, @Nullable ClogResult clog)
	{
		if (level <= 0 || clog == null)
		{
			return null;
		}
		int obtained = -1;
		int total = -1;
		if (config.skillColorMode() == SkillColorMode.CLOG_PROGRESSION)
		{
			SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
				SkillClogSection.forSkill(skill, clog, null, catalog), false);
			obtained = progress.obtained();
			total = progress.total();
		}
		return SkillLevelColor.forCell(level, true, obtained, total, config);
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
