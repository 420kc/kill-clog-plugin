/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 */
package com.killclog;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.*;

/**
 * The boss cells as a list, one full-width row per boss in the hiscores' own order: its icon, its name and its colored
 * kill count, opening the same card as its cell. Rows mirror the grid's labels instead of working the values out again:
 * the grid is always built, even while hidden, and each row listens to its grid label's text, color and icon. So
 * whatever writes a label (a search starting, results landing, the completionist highlighter) reaches the list with no
 * wiring of its own, and the two views can't disagree.
 *
 * One pause, on purpose: while comparison holds the grid on screen the mirror skips text and color, so comparison's
 * rewrites on each hover never reach the hidden rows; leaving comparison rewrites both, which brings them back in step.
 * Icons keep mirroring throughout: comparison never writes icons, and a sprite that loads mid-comparison would
 * otherwise be lost for good (setting the same ImageIcon later fires no change). {@link #resyncAll()} backs all of it
 * up whenever the list is about to show.
 */
public class BossListView
{
	private static final int ROW_HEIGHT = 22;
	private static final int KC_WIDTH = 46;

	private final JPanel root;
	// While a search filters the list, it keeps the height it had before: the rows it hides leave empty space at the
	// bottom, so a shorter list never pulls the panel's scroll position.
	private int filterHeight;
	private final Map<HiscoreSkill, Row> rows = new LinkedHashMap<>();
	// The Collection Log pages no boss row stands for: rows of their own, shown only while a search finds them.
	private final Map<String, Row> pageRows = new LinkedHashMap<>();
	private final BooleanSupplier mirrorActive;
	private final JTextField search = new JTextField();
	private final TooltipController tooltipController;
	private final Cells cells;
	private final ClogPages pages;
	private final KillClogConfig config;

	private static final class Row
	{
		JLabel icon;
		JLabel name;
		JLabel kc;
		JLabel gridLabel;
		JPanel panel;
	}

	/**
	 * {@code mirrorActive} is false while a comparison holds the grid on screen: the mirror skips text and color then,
	 * and leaving the comparison brings the rows back in step.
	 */
	public BossListView(TooltipController tooltipController, Cells cells, ClogPages pages, KillClogConfig config,
		BooleanSupplier mirrorActive)
	{
		this.tooltipController = tooltipController;
		this.cells = cells;
		this.pages = pages;
		this.config = config;
		this.mirrorActive = mirrorActive;
		root = new JPanel()
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension size = super.getPreferredSize();
				return new Dimension(size.width, Math.max(size.height, filterHeight));
			}
		};
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// PanelData.BOSSES is the Jagex hiscores' order: nearly alphabetical, with its pairs kept together (the
		// Nightmare then Phosani's, the Gauntlet then the Corrupted). The list keeps it, since players already know
		// this order from the game's own hiscores.
		root.add(searchRow());
		// A strip like the activities separator, in its gray and height, sets the search apart from the first boss.
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(0, ActivitiesTray.SEPARATOR_HEIGHT));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, ActivitiesTray.SEPARATOR_HEIGHT));
		root.add(divider);
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
	 * Copy every row from its grid label again. Called before the list shows, so it always opens matching the grid,
	 * whatever the paused mirror skipped.
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
	 * The row listens to its grid label for the properties it mirrors, each by name, so the label's other changes (its
	 * tooltip flips on every lookup) never reach it.
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
			// Icons pass the pause: a comparison never writes them, and a sprite that finishes loading during one is
			// the only icon change there is. Skipped, it would be lost: leaving the comparison sets the same ImageIcon
			// again, which fires nothing.
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
				// The name wears the kill count's color: the completion colors when the highlighter is on, the resting
				// grey when off.
				row.kc.setForeground(gridLabel.getForeground());
				row.name.setForeground(gridLabel.getForeground());
				break;
			case "icon":
				// The grid's label already holds the icon the row wants (the same 20x20, dimmed or not by
				// renderBossValue), so the row takes the object itself, whoever wrote it.
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

		// All three labels carry the boss's card, so in hover mode the whole row opens it, the icon and the kill count
		// included. The icon itself arrives through the mirror when the grid's loads.
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
		// The outline takes the kill count's color (mirrored from the grid, so the completion highlighter shows as it
		// does on the cell); a press on the bare row opens the card where the name is.
		tooltipController.addCellHoverEffect(panel, row.kc, row.name, row.icon, row.kc);

		row.panel = panel;
		rows.put(boss, row);
		return panel;
	}

	/** The list's own search: a row above the first boss, typed into like the panel's search bar. */
	private JPanel searchRow()
	{
		JLabel icon = new JLabel(new ImageIcon(KillClogIcons.makeSearchIcon(KillClogColors.TEXT_DIM, 0.70f)));
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
		// Greyscale text like every panel label, not the desktop's ClearType color fringes.
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

	/** A typed search reads the results again: a log landing changes the counts its page rows show. */
	void refresh()
	{
		if (!search.getText().isEmpty())
		{
			filter(search.getText());
		}
	}

	/** Empties the search, so each new lookup opens on every boss. */
	void clearSearch()
	{
		search.setText("");
	}

	/**
	 * Shows the bosses whose name holds the text, or whose chat shorthand starts with it (cox, tob, cg), and the
	 * bosses and pages whose log holds an item by that name (twisted bow: Chambers of Xeric). A raid's hard mode shows
	 * with its raid: cox brings Challenge Mode too, while cm is Challenge Mode alone.
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
		filterHeight = query.isEmpty() ? 0 : filterHeight > 0 ? filterHeight : root.getHeight();
		// A boss or page also shows when its log page holds an item by that name. A number is a count, never an item,
		// so a typed number never searches item names.
		Set<String> itemPages = typed.chars().anyMatch(Character::isLetter) ? pages.pagesWithItem(query)
			: Collections.emptySet();
		rows.forEach((boss, row) -> row.panel.setVisible(query.isEmpty() || matches(boss, query, named)
			|| matches(PanelData.card(boss), query, named) || ClogPages.bossOn(boss, itemPages)
			|| byCount && row.kc.getText().toLowerCase(Locale.ROOT).contains(typed)));
		if (!query.isEmpty() && pageRows.isEmpty())
		{
			for (String key : pages.searchPages())
			{
				pageRows.put(key, buildPageRow(key));
			}
		}
		// A page row is found by its name, or by an item it holds.
		pageRows.forEach((key, row) ->
		{
			boolean found = !query.isEmpty() && (KillClogChatCommand.normalize(row.name.getText()).contains(query)
				|| itemPages.contains(key));
			if (found)
			{
				showCount(row, pages.count(key));
			}
			row.panel.setVisible(found);
		});
		root.revalidate();
		root.repaint();
	}

	/** A page's row, after the bosses: its first item, its name, and its count, opening the page's own popup. */
	private Row buildPageRow(String key)
	{
		Row row = new Row();
		row.icon = pageTooltipLabel(key);
		row.icon.setPreferredSize(new Dimension(24, ROW_HEIGHT));
		row.icon.setHorizontalAlignment(JLabel.CENTER);
		int item = pages.firstItem(key);
		if (item > 0)
		{
			cells.setItemIcon(row.icon, item);
		}
		row.name = pageTooltipLabel(key);
		row.name.setText(pages.name(key));
		row.name.setFont(FontManager.getRunescapeSmallFont());
		ClogHelper.antialias(row.name);
		row.kc = pageTooltipLabel(key);
		row.kc.setFont(FontManager.getRunescapeSmallFont());
		row.kc.setHorizontalAlignment(JLabel.RIGHT);
		row.kc.setPreferredSize(new Dimension(KC_WIDTH, ROW_HEIGHT));
		row.kc.setBorder(new EmptyBorder(0, 0, 0, 4));
		ClogHelper.antialias(row.kc);
		row.panel = rowPanel(row.icon, row.name, ROW_HEIGHT);
		row.panel.add(row.kc, BorderLayout.EAST);
		row.panel.setVisible(false);
		tooltipController.addCellHoverEffect(row.panel, row.kc, row.name, row.icon, row.kc);
		root.add(row.panel);
		return row;
	}

	/**
	 * A page's obtained/total in the boss rows' colors: the player's progression colors under the highlighter,
	 * else the count color, and grey without a log.
	 */
	private void showCount(Row row, int[] count)
	{
		row.kc.setText(count[0] >= 0 ? count[0] + "/" + count[1] : "--/" + count[1]);
		Color color = count[0] < 0 ? ColorScheme.LIGHT_GRAY_COLOR
			: config.completionistHighlighter() ? ClogHelper.clogColor(count[0], count[1], config)
			: count[0] > 0 ? KillClogColors.KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR;
		row.kc.setForeground(color);
		row.name.setForeground(color);
	}

	/** A page row's label, opening the page's popup. */
	private JLabel pageTooltipLabel(String key)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return pages.page(this, key);
			}
		};
		label.setToolTipText(" ");
		return label;
	}

	private static boolean matches(HiscoreSkill boss, String query, Set<String> named)
	{
		return boss != null && (named.contains(boss.getName())
			|| KillClogChatCommand.normalize(boss.getName()).contains(query));
	}

	/** A row label whose card is its boss's, as the grid's cell opens it. */
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
