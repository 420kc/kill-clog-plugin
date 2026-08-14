/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Full-width alphabetical boss list: the hiscores-style alternative to the
 * boss grid. One row per boss - [icon] name, colored kc - reusing the grid's
 * tooltip routing so every row opens the same clog popup as its grid cell.
 */
package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * List-style rendering of the boss cells. Rows MIRROR the grid labels rather
 * than re-deriving values: the grid is always rendered (even while hidden),
 * and each row listens to its grid label's text / foreground / icon property
 * changes. Every writer - the search-start reset, results landing, the
 * completionist highlighter, 420 mode, comparison exit, whatever comes next -
 * propagates into the list with no per-call-site wiring, which is what keeps
 * the two views incapable of disagreeing.
 */
public class BossListView
{
	private static final int ROW_HEIGHT = 22;
	private static final int KC_WIDTH = 46;

	private final JPanel root;
	private final Map<HiscoreSkill, Row> rows = new LinkedHashMap<>();

	private static final class Row
	{
		JLabel icon;
		JLabel name;
		JLabel kc;
		ImageIcon original;
		ImageIcon dimmed;
		// Grid-truth dim state, remembered so a sprite that finishes loading
		// after a lookup still lands in the right state.
		boolean dimmedInGrid;
	}

	public BossListView(SpriteManager spriteManager, TooltipController tooltipController, Cells cells)
	{
		root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// PanelData.BOSSES is the curated alphabetical order; the list keeps it.
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			root.add(buildRow(boss, spriteManager, tooltipController, cells));
			installMirror(boss, cells);
		}
	}

	/** The stacked-rows component the panel places where the grid goes. */
	public JPanel component()
	{
		return root;
	}

	/** The row's name label, for callers wiring extra listeners (420 mode easter egg). */
	@Nullable
	public JLabel nameLabel(HiscoreSkill boss)
	{
		Row row = rows.get(boss);
		return row != null ? row.name : null;
	}

	/**
	 * Subscribe the row to its grid label. Named registrations keep the
	 * listeners off the label's unrelated property traffic (tooltips flip on
	 * every lookup).
	 */
	private void installMirror(HiscoreSkill boss, Cells cells)
	{
		JLabel gridLabel = cells.getBossLabel(boss);
		Row row = rows.get(boss);
		if (gridLabel == null || row == null)
		{
			return;
		}
		gridLabel.addPropertyChangeListener("text",
			e -> row.kc.setText(gridLabel.getText().trim()));
		// The name wears the same color as the kc: completion gradient when
		// the highlighter is on, the soft kc/grey tones when it is off.
		gridLabel.addPropertyChangeListener("foreground",
			e -> applyForeground(row, gridLabel.getForeground()));
		gridLabel.addPropertyChangeListener("icon",
			e -> mirrorIconState(row, gridLabel, cells, boss));

		// Initial sync: the labels are freshly built with cold defaults, but
		// copying once here removes any ordering assumption.
		row.kc.setText(gridLabel.getText().trim());
		applyForeground(row, gridLabel.getForeground());
		mirrorIconState(row, gridLabel, cells, boss);
	}

	private static void applyForeground(Row row, Color color)
	{
		row.kc.setForeground(color);
		row.name.setForeground(color);
	}

	private void mirrorIconState(Row row, JLabel gridLabel, Cells cells, HiscoreSkill boss)
	{
		row.dimmedInGrid = gridLabel.getIcon() != null
			&& gridLabel.getIcon() == cells.getDimmedIcons().get(boss);
		applyIcon(row);
	}

	private static void applyIcon(Row row)
	{
		if (row.original == null)
		{
			return;
		}
		row.icon.setIcon(row.dimmedInGrid ? row.dimmed : row.original);
	}

	private JPanel buildRow(HiscoreSkill boss, SpriteManager spriteManager,
		TooltipController tooltipController, Cells cells)
	{
		Row row = new Row();

		row.icon = new JLabel();
		row.icon.setPreferredSize(new Dimension(24, ROW_HEIGHT));
		row.icon.setHorizontalAlignment(JLabel.CENTER);
		loadIcon(row, boss.getSpriteId(), spriteManager);

		// The name label is the tooltip owner, same routing as the grid cell:
		// hover mode pops it over the name, click mode pops it from anywhere
		// in the row via the cell hover effect below.
		row.name = new JLabel(boss.getName())
		{
			@Override
			public JToolTip createToolTip()
			{
				return cells.buildBossTooltipFor(this, boss);
			}
		};
		row.name.setFont(FontManager.getRunescapeSmallFont());
		row.name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.name.setToolTipText(" ");
		antialias(row.name);

		row.kc = new JLabel("--");
		row.kc.setFont(FontManager.getRunescapeSmallFont());
		row.kc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.kc.setHorizontalAlignment(JLabel.RIGHT);
		row.kc.setPreferredSize(new Dimension(KC_WIDTH, ROW_HEIGHT));
		row.kc.setBorder(new EmptyBorder(0, 0, 0, 4));
		antialias(row.kc);

		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(TooltipController.CELL_BORDER);
		panel.setPreferredSize(new Dimension(0, ROW_HEIGHT));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		panel.add(row.icon, BorderLayout.WEST);
		panel.add(row.name, BorderLayout.CENTER);
		panel.add(row.kc, BorderLayout.EAST);
		tooltipController.addCellHoverEffect(panel, row.name);

		rows.put(boss, row);
		return panel;
	}

	/** The panel-wide text treatment every other label already wears. */
	private static void antialias(JLabel label)
	{
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private void loadIcon(Row row, int spriteId, SpriteManager spriteManager)
	{
		spriteManager.getSpriteAsync(spriteId, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null)
				{
					return;
				}
				row.original = new ImageIcon(ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20));
				row.dimmed = new ImageIcon(ClogHelper.createDimmedImage(row.original));
				// Late sprite arrivals land in whatever state the grid last
				// mirrored, not blindly undimmed.
				applyIcon(row);
			}));
	}
}
