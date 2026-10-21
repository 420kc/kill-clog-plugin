/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Full-width boss list in the hiscores' own order: the list-style alternative
 * to the boss grid. One row per boss - [icon] name, colored kc - reusing the
 * grid's tooltip routing so every row opens the same clog popup as its cell.
 */
package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * List-style rendering of the boss cells. Rows MIRROR the grid labels rather
 * than re-deriving values: the grid is always rendered (even while hidden),
 * and each row listens to its grid label's text / foreground / icon property
 * changes. Every writer - the search-start reset, results landing, the
 * completionist highlighter, whatever comes next - propagates into
 * the list with no per-call-site wiring, which is what keeps the two views
 * incapable of disagreeing.
 *
 * <p>The one deliberate pause: while comparison holds the grid on screen the
 * mirror skips text and foreground, so compare's per-hover HTML rewrites
 * never reach the hidden rows; the exit path rewrites both, which resyncs
 * them. Icons keep mirroring straight through - comparison never writes
 * icons, and an async sprite landing mid-compare would otherwise be lost
 * for good (reapplying the same ImageIcon later fires no change event).
 * {@link #resyncAll()} backstops the whole contract whenever the list is
 * about to show.
 */
public class BossListView
{
	private static final int ROW_HEIGHT = 22;
	private static final int KC_WIDTH = 46;

	private final JPanel root;
	private final Map<HiscoreSkill, Row> rows = new LinkedHashMap<>();
	private final BooleanSupplier mirrorActive;
	private final JTextField search = new JTextField();
	@Getter(AccessLevel.PACKAGE)
	private final JPanel searchStrip = new JPanel();

	private static final class Row
	{
		JLabel icon;
		JLabel name;
		JLabel kc;
		JLabel gridLabel;
		JPanel panel;
	}

	/**
	 * @param mirrorActive false while the grid is the forced view (compare);
	 * the mirror skips those writes and the exit rewrite resyncs the rows
	 */
	public BossListView(TooltipController tooltipController, Cells cells, BooleanSupplier mirrorActive)
	{
		this.mirrorActive = mirrorActive;
		root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// PanelData.BOSSES is the Jagex hiscores order: near-alphabetical with
		// vanilla's deliberate pairings kept (Nightmare then Phosani's,
		// Gauntlet then Corrupted). The list keeps vanilla's order rather
		// than imposing a strict sort - players have already scanned this
		// exact order in the game's own hiscores.
		// The search and the strip under it are one piece, which pins under the panel's top once it scrolls past.
		searchStrip.setLayout(new BoxLayout(searchStrip, BoxLayout.Y_AXIS));
		searchStrip.setOpaque(false);
		searchStrip.add(searchRow());
		// A strip like the activities separator, in its gray and height, sets the search apart from the first boss.
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(0, ActivitiesTray.SEPARATOR_HEIGHT));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, ActivitiesTray.SEPARATOR_HEIGHT));
		searchStrip.add(divider);
		root.add(searchStrip);
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			root.add(buildRow(boss, tooltipController, cells));
			installMirror(boss, cells);
		}
	}

	/** The stacked-rows component the panel places where the grid goes. */
	public JPanel component()
	{
		return root;
	}

	/**
	 * Re-copy every row from its grid label. Called before the list shows,
	 * so it always opens on grid truth regardless of what the paused mirror
	 * skipped - the structural backstop for the pause contract above.
	 */
	public void resyncAll()
	{
		for (Row row : rows.values())
		{
			if (row.gridLabel == null)
			{
				continue;
			}
			sync(row, row.gridLabel, "text");
			sync(row, row.gridLabel, "foreground");
			sync(row, row.gridLabel, "icon");
		}
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
		row.gridLabel = gridLabel;
		PropertyChangeListener mirror = e ->
		{
			// Icons pass the pause: comparison never writes them, and an
			// async sprite load landing mid-compare is the only icon event
			// there is - skip it and the post-exit render reapplies the same
			// ImageIcon object, which fires nothing to catch the row up.
			if ("icon".equals(e.getPropertyName()) || mirrorActive.getAsBoolean())
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

	private void sync(Row row, JLabel gridLabel, String property)
	{
		switch (property)
		{
			case "text":
				String text = gridLabel.getText();
				row.kc.setText(text != null ? text.trim() : "");
				// Counts landing under a typed search can change which rows it finds.
				if (!search.getText().isEmpty())
				{
					filter(search.getText());
				}
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
		Cells cells)
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
		ClogHelper.antialias(row.name);

		row.kc = bossTooltipLabel(boss, cells);
		row.kc.setText("--");
		row.kc.setFont(FontManager.getRunescapeSmallFont());
		row.kc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.kc.setHorizontalAlignment(JLabel.RIGHT);
		row.kc.setPreferredSize(new Dimension(KC_WIDTH, ROW_HEIGHT));
		row.kc.setBorder(new EmptyBorder(0, 0, 0, 4));
		ClogHelper.antialias(row.kc);

		JPanel panel = rowPanel(row.icon, row.name, ROW_HEIGHT);
		panel.add(row.kc, BorderLayout.EAST);
		// Outline color follows the kc label (mirrored from the grid, so the
		// completion highlighter shows through, same as the grid cell); the
		// name stays the popup anchor for bare-cell presses.
		tooltipController.addCellHoverEffect(panel, row.kc, row.name, row.icon, row.kc);

		row.panel = panel;
		rows.put(boss, row);
		return panel;
	}

	/** The list's own search: a row above the first boss, typed into like the panel's search bar. */
	private JPanel searchRow()
	{
		JLabel icon = new JLabel(new ImageIcon(ClogHelper.makeSearchIcon(KillClogPanel.TEXT_DIM, 0.70f)));
		icon.setPreferredSize(new Dimension(24, PanelSearchBox.HEIGHT));
		JTextField field = search;
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		field.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
		// Selected text as the panel's search bar (RuneLite's FlatTextField) paints it: white on the brand orange.
		field.setSelectedTextColor(Color.WHITE);
		field.setSelectionColor(ColorScheme.BRAND_ORANGE_TRANSPARENT);
		field.setOpaque(false);
		field.setBorder(null);
		// Greyscale text like every panel label, not the desktop's ClearType colour fringes.
		ClogHelper.antialias(field);
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filter(field.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filter(field.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
			}
		});
		field.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					field.setText("");
				}
			}
		});
		// Like the panel's search bar: the text cursor over the field, and the row lightens while the mouse is on it.
		JPanel row = rowPanel(icon, field, PanelSearchBox.HEIGHT);
		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		field.addMouseListener(hover);
		icon.addMouseListener(hover);
		return row;
	}

	/** One list row: a fixed-height strip, its icon on the left and its content filling the rest. */
	private static JPanel rowPanel(JComponent icon, JComponent content, int height)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(TooltipController.CELL_BORDER);
		panel.setPreferredSize(new Dimension(0, height));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		panel.add(icon, BorderLayout.WEST);
		panel.add(content, BorderLayout.CENTER);
		return panel;
	}

	/** Empties the search, so each new lookup opens on every boss. */
	void clearSearch()
	{
		search.setText("");
	}

	/**
	 * Shows the bosses whose name holds the text, or whose chat shorthand starts with it (cox, tob, cg). A raid's
	 * hard mode shows with its raid: cox brings Challenge Mode too, while cm is Challenge Mode alone.
	 */
	void filter(String text)
	{
		String query = KillClogChatCommand.normalize(text);
		// A search with a digit in it also finds a boss by its count, as the row shows it ("147k", "420").
		String typed = text.trim().toLowerCase(Locale.ROOT);
		boolean byCount = typed.chars().anyMatch(Character::isDigit);
		Set<String> named = new HashSet<>();
		KillClogChatCommand.aliases().forEach((alias, boss) ->
		{
			if (!query.isEmpty() && alias.startsWith(query))
			{
				named.add(boss);
			}
		});
		rows.forEach((boss, row) -> row.panel.setVisible(query.isEmpty() || matches(boss, query, named)
			|| matches(PanelData.card(boss), query, named)
			|| byCount && row.kc.getText().toLowerCase(Locale.ROOT).contains(typed)));
		root.revalidate();
		root.repaint();
	}

	private static boolean matches(HiscoreSkill boss, String query, Set<String> named)
	{
		return boss != null && (named.contains(boss.getName())
			|| KillClogChatCommand.normalize(boss.getName()).contains(query));
	}

	/** A row label that routes its tooltip through the grid's boss popup. */
	private static JLabel bossTooltipLabel(HiscoreSkill boss, Cells cells)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return cells.buildCellTooltipFor(this, boss);
			}
		};
		label.setToolTipText(" ");
		return label;
	}

}
