package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

final class SearchRowController
{
	private static final int SEARCH_ICON_HIT_X = 32;
	private static final int COMPARE_ICON_WIDTH = 22;

	private final JPanel searchRow;
	private final IconTextField searchBar;
	private final JLabel compareLabel;
	private final JTextField searchTextField;
	private final BooleanSupplier comparisonMode;
	private final BooleanSupplier compareLookupInFlight;
	private final Runnable normalLookup;
	private final Runnable compareLookup;
	private final Runnable comparisonExit;
	private final Runnable clearSearchStatus;
	private final Runnable lookupSelf;
	private final Runnable panelRevalidate;
	private final ImageIcon compareIconDim;
	private final ImageIcon compareIconBright;
	private final ImageIcon searchIconDim;
	private final ImageIcon searchIconBright;
	private final KeyListener compareEntryKeyListener;

	@Getter(AccessLevel.PACKAGE)
	private boolean compareEntryMode;
	private boolean compareIconHover;
	private boolean searchRowHover;
	private boolean compareAvailable;
	private boolean comparisonEnabled = true;

	SearchRowController(
		JPanel searchRow,
		IconTextField searchBar,
		JLabel compareLabel,
		JTextField searchTextField,
		Color textDim,
		BooleanSupplier comparisonMode,
		BooleanSupplier compareLookupInFlight,
		Runnable normalLookup,
		Runnable compareLookup,
		Runnable comparisonExit,
		Runnable clearSearchStatus,
		Runnable lookupSelf,
		Runnable panelRevalidate)
	{
		this.searchRow = searchRow;
		this.searchBar = searchBar;
		this.compareLabel = compareLabel;
		this.searchTextField = searchTextField;
		this.comparisonMode = comparisonMode;
		this.compareLookupInFlight = compareLookupInFlight;
		this.normalLookup = normalLookup;
		this.compareLookup = compareLookup;
		this.comparisonExit = comparisonExit;
		this.clearSearchStatus = clearSearchStatus;
		this.lookupSelf = lookupSelf;
		this.panelRevalidate = panelRevalidate;
		compareIconDim = new ImageIcon(ClogHelper.makeCompareIcon(
			ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 0.55f));
		compareIconBright = new ImageIcon(ClogHelper.makeCompareIcon(
			ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 1.0f));
		searchIconDim = new ImageIcon(ClogHelper.makeSearchIcon(textDim, 0.70f));
		searchIconBright = new ImageIcon(ClogHelper.makeSearchIcon(textDim, 1.0f));
		compareEntryKeyListener = new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					exitEntry();
				}
			}
		};
	}

	void install()
	{
		searchBar.addActionListener(e ->
		{
			if (compareEntryMode)
			{
				compareLookup.run();
			}
			else
			{
				normalLookup.run();
			}
		});

		compareLabel.setIcon(compareIconDim);
		compareLabel.setHorizontalAlignment(JLabel.CENTER);
		compareLabel.setVerticalAlignment(JLabel.CENTER);
		compareLabel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		compareLabel.setOpaque(true);
		// Mirror the search glass's left inset: glyph sits 8 px from the right edge.
		compareLabel.setBorder(new EmptyBorder(0, 0, 0, 7));
		compareLabel.setVisible(false);
		compareLabel.setPreferredSize(new Dimension(COMPARE_ICON_WIDTH, 30));

		MouseAdapter searchRowHoverSync = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setSearchRowHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (!isMouseInsideSearchRow())
					{
						setSearchRowHover(false);
					}
				});
			}
		};
		installMouseListenerDeep(searchRow, searchRowHoverSync);

		installMouseListenerDeep(searchBar, new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2)
				{
					return;
				}
				Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), searchBar);
				if (p.x <= SEARCH_ICON_HIT_X)
				{
					lookupSelf.run();
				}
			}
		});

		compareLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleEntry();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				compareIconHover = true;
				setSearchRowHover(true);
				refreshIcon();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				compareIconHover = false;
				refreshIcon();
				SwingUtilities.invokeLater(() ->
				{
					if (!isMouseInsideSearchRow())
					{
						setSearchRowHover(false);
					}
				});
			}
		});
	}

	int compareIconWidth()
	{
		return compareLabel.isVisible() ? COMPARE_ICON_WIDTH : 0;
	}

	void setCompareVisible(boolean visible)
	{
		compareAvailable = visible;
		applyCompareVisibility();
	}

	void setComparisonEnabled(boolean enabled)
	{
		comparisonEnabled = enabled;
		if (!enabled)
		{
			compareIconHover = false;
			exitIfActive();
		}
		applyCompareVisibility();
	}

	private void applyCompareVisibility()
	{
		compareLabel.setVisible(comparisonEnabled && compareAvailable);
		refreshIcon();
		searchRow.revalidate();
	}

	void toggleEntry()
	{
		if (!comparisonEnabled)
		{
			return;
		}
		if (compareEntryMode || comparisonMode.getAsBoolean())
		{
			exitEntry();
		}
		else
		{
			enterEntry();
		}
	}

	void exitIfActive()
	{
		if (compareEntryMode || comparisonMode.getAsBoolean())
		{
			exitEntry();
		}
	}

	void onComparisonExit()
	{
		searchBar.setText("");
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		resetTextField();
		compareEntryMode = false;
		refreshIcon();
	}

	void onComparisonEnter()
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		refreshIcon();
	}

	void refreshIcon()
	{
		boolean active = compareIconHover || compareEntryMode || comparisonMode.getAsBoolean();
		boolean comparing = compareEntryMode || comparisonMode.getAsBoolean();
		compareLabel.setIcon(comparing
			? (compareIconHover ? searchIconBright : searchIconDim)
			: (active ? compareIconBright : compareIconDim));
		compareLabel.setBackground(searchRowHover
			? ColorScheme.DARK_GRAY_HOVER_COLOR
			: ColorScheme.DARKER_GRAY_COLOR);
		if (!compareLookupInFlight.getAsBoolean())
		{
			if (comparing)
			{
				searchBar.setIcon(compareIconBright);
			}
			else
			{
				searchBar.setIcon(IconTextField.Icon.SEARCH);
			}
		}
	}

	private void enterEntry()
	{
		if (!comparisonEnabled)
		{
			return;
		}
		compareEntryMode = true;
		refreshIcon();

		if (searchTextField != null)
		{
			searchTextField.setText("");
			searchTextField.setForeground(ComparisonController.COMPARE_RED);
			searchTextField.setCaretColor(ComparisonController.COMPARE_RED);
			searchTextField.addKeyListener(compareEntryKeyListener);
			searchTextField.requestFocusInWindow();
		}

		clearSearchStatus.run();
		panelRevalidate.run();
	}

	private void exitEntry()
	{
		compareEntryMode = false;
		if (comparisonMode.getAsBoolean())
		{
			comparisonExit.run();
			return;
		}
		resetTextField();
		searchBar.setText("");
		refreshIcon();
		clearSearchStatus.run();
		panelRevalidate.run();
	}

	private void resetTextField()
	{
		if (searchTextField != null)
		{
			searchTextField.removeKeyListener(compareEntryKeyListener);
			searchTextField.setForeground(Color.WHITE);
			searchTextField.setCaretColor(Color.WHITE);
		}
	}

	private void installMouseListenerDeep(Component component, MouseAdapter adapter)
	{
		component.addMouseListener(adapter);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				installMouseListenerDeep(child, adapter);
			}
		}
	}

	private boolean isMouseInsideSearchRow()
	{
		return searchRow.getMousePosition(true) != null;
	}

	private void setSearchRowHover(boolean hover)
	{
		searchRowHover = hover;
		Color background = hover ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		searchBar.setBackground(background);
		compareLabel.setBackground(background);
	}
}
