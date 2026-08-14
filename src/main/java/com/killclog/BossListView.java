/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Full-width boss list in the hiscores' own order: the list-style alternative
 * to the boss grid. One row per boss - [icon] name, colored kc - reusing the
 * grid's tooltip routing so every row opens the same clog popup as its cell.
 */
package com.killclog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.border.EmptyBorder;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * List-style rendering of the boss cells. Rows MIRROR the grid labels rather
 * than re-deriving values: the grid is always rendered (even while hidden),
 * and each row listens to its grid label's text / foreground / icon property
 * changes. Every writer - the search-start reset, results landing, the
 * completionist highlighter, 420 mode, whatever comes next - propagates into
 * the list with no per-call-site wiring, which is what keeps the two views
 * incapable of disagreeing.
 *
 * <p>The one deliberate pause: while comparison holds the grid on screen the
 * mirror sits out, so compare's per-hover HTML rewrites never reach the
 * hidden rows. The exit path rewrites every boss label (restore + rerender),
 * which resyncs the rows the moment the list can show again.
 */
public class BossListView
{
	private static final int ROW_HEIGHT = 22;
	private static final int KC_WIDTH = 46;

	private final JPanel root;
	private final Map<HiscoreSkill, Row> rows = new LinkedHashMap<>();
	private final BooleanSupplier mirrorActive;

	private static final class Row
	{
		JLabel icon;
		JLabel name;
		JLabel kc;
	}

	/**
	 * @param thermoEasterEgg the guarded 420-mode cycle, wired to the Thermo
	 * row's kc label - the same hit area the grid gives it
	 * @param mirrorActive false while the grid is the forced view (compare);
	 * the mirror skips those writes and the exit rewrite resyncs the rows
	 */
	public BossListView(TooltipController tooltipController, Cells cells,
		Runnable thermoEasterEgg, BooleanSupplier mirrorActive)
	{
		this.mirrorActive = mirrorActive;
		root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// PanelData.BOSSES is the Jagex hiscores order: near-alphabetical with
		// vanilla's deliberate pairings kept (Nightmare then Phosani's,
		// Gauntlet then Corrupted). The list keeps vanilla's order rather
		// than imposing a strict sort - "the other vanilla format" is the
		// product promise, and every player has already scanned this order.
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			root.add(buildRow(boss, tooltipController, cells, thermoEasterEgg));
			installMirror(boss, cells);
		}
	}

	/** The stacked-rows component the panel places where the grid goes. */
	public JPanel component()
	{
		return root;
	}

	/**
	 * Subscribe the row to its grid label: one listener, registered under
	 * each mirrored property name. Named registration keeps it off the
	 * label's unrelated property traffic (tooltips flip on every lookup).
	 */
	private void installMirror(HiscoreSkill boss, Cells cells)
	{
		JLabel gridLabel = cells.getBossLabel(boss);
		Row row = rows.get(boss);
		if (gridLabel == null || row == null)
		{
			return;
		}
		PropertyChangeListener mirror = e ->
		{
			if (mirrorActive.getAsBoolean())
			{
				sync(row, gridLabel, e.getPropertyName());
			}
		};
		gridLabel.addPropertyChangeListener("text", mirror);
		gridLabel.addPropertyChangeListener("foreground", mirror);
		gridLabel.addPropertyChangeListener("icon", mirror);

		// Initial sync: the labels are freshly built with cold defaults, but
		// copying once here removes any ordering assumption.
		sync(row, gridLabel, "text");
		sync(row, gridLabel, "foreground");
		sync(row, gridLabel, "icon");
	}

	private static void sync(Row row, JLabel gridLabel, String property)
	{
		switch (property)
		{
			case "text":
				String text = gridLabel.getText();
				row.kc.setText(text != null ? text.trim() : "");
				break;
			case "foreground":
				// The name wears the same color as the kc: completion
				// gradient when the highlighter is on, soft kc/grey when off.
				row.kc.setForeground(gridLabel.getForeground());
				row.name.setForeground(gridLabel.getForeground());
				break;
			case "icon":
				// The grid renders the exact icon the row wants (same 20x20
				// pipeline, dim state decided by renderBossValue), so mirror
				// the object itself; any future icon writer is covered too.
				row.icon.setIcon(gridLabel.getIcon());
				break;
			default:
				break;
		}
	}

	private JPanel buildRow(HiscoreSkill boss, TooltipController tooltipController,
		Cells cells, Runnable thermoEasterEgg)
	{
		Row row = new Row();

		// All three labels carry the boss tooltip so the WHOLE row pops the
		// clog popup in hover mode - icon and kc included, no dead zones.
		// The icon itself arrives via the mirror when the grid's loads.
		row.icon = bossTooltipLabel(boss, cells);
		row.icon.setPreferredSize(new Dimension(24, ROW_HEIGHT));
		row.icon.setHorizontalAlignment(JLabel.CENTER);

		row.name = bossTooltipLabel(boss, cells);
		row.name.setText(boss.getName());
		row.name.setFont(FontManager.getRunescapeSmallFont());
		row.name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		antialias(row.name);

		row.kc = bossTooltipLabel(boss, cells);
		row.kc.setText("--");
		row.kc.setFont(FontManager.getRunescapeSmallFont());
		row.kc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.kc.setHorizontalAlignment(JLabel.RIGHT);
		row.kc.setPreferredSize(new Dimension(KC_WIDTH, ROW_HEIGHT));
		row.kc.setBorder(new EmptyBorder(0, 0, 0, 4));
		antialias(row.kc);

		if (boss == HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL)
		{
			// Same hit area as the grid's easter egg: the kc label, not the
			// whole row - a wide trigger would cycle 420 mode by accident.
			row.kc.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					thermoEasterEgg.run();
				}
			});
		}

		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(TooltipController.CELL_BORDER);
		panel.setPreferredSize(new Dimension(0, ROW_HEIGHT));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		panel.add(row.icon, BorderLayout.WEST);
		panel.add(row.name, BorderLayout.CENTER);
		panel.add(row.kc, BorderLayout.EAST);
		// Outline color follows the kc label (mirrored from the grid, so the
		// completion highlighter shows through, same as the grid cell); the
		// name stays the popup anchor for bare-cell presses.
		tooltipController.addCellHoverEffect(panel, row.kc, row.name, row.icon, row.kc);

		rows.put(boss, row);
		return panel;
	}

	/** A row label that routes its tooltip through the grid's boss popup. */
	private static JLabel bossTooltipLabel(HiscoreSkill boss, Cells cells)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return cells.buildBossTooltipFor(this, boss);
			}
		};
		label.setToolTipText(" ");
		return label;
	}

	/** The panel-wide text treatment every other label already wears. */
	private static void antialias(JLabel label)
	{
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}
}
