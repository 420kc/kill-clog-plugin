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
 * List-style rendering of the boss cells. Values, colors, and icon dim state
 * MIRROR the grid labels rather than re-deriving them: the grid is always
 * rendered (even while hidden), so copying its state keeps the list in exact
 * parity with every coloring rule the grid learns - completionist
 * highlighter, 420 mode, future ones - without a second render path.
 */
public class BossListView
{
	private static final int ROW_HEIGHT = 22;
	private static final int KC_WIDTH = 46;
	private static final Color NAME_COLOR = Color.WHITE;

	private final JPanel root;
	private final Map<HiscoreSkill, Row> rows = new LinkedHashMap<>();

	private static final class Row
	{
		JLabel icon;
		JLabel name;
		JLabel kc;
		ImageIcon original;
		ImageIcon dimmed;
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
	 * Mirror the grid labels into the rows: kc text and color, plus icon dim
	 * state. Before the first lookup the grid holds its cold defaults and the
	 * rows copy those.
	 */
	public void renderFrom(Cells cells)
	{
		for (Map.Entry<HiscoreSkill, Row> entry : rows.entrySet())
		{
			HiscoreSkill boss = entry.getKey();
			Row row = entry.getValue();

			JLabel gridLabel = cells.getBossLabel(boss);
			if (gridLabel == null)
			{
				continue;
			}
			row.kc.setText(gridLabel.getText().trim());
			row.kc.setForeground(gridLabel.getForeground());
			boolean dimmedInGrid = gridLabel.getIcon() != null
				&& gridLabel.getIcon() == cells.getDimmedIcons().get(boss);
			if (row.original != null)
			{
				row.icon.setIcon(dimmedInGrid ? row.dimmed : row.original);
			}
		}
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
		row.name.setForeground(NAME_COLOR);
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
				// Cold state matches the grid: full icon until a lookup dims it.
				if (row.icon.getIcon() == null)
				{
					row.icon.setIcon(row.original);
				}
			}));
	}
}
