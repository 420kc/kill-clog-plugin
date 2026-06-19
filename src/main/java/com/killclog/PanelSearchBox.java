package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.util.function.IntSupplier;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;

final class PanelSearchBox
{
	private PanelSearchBox()
	{
	}

	static void configureStatus(JLabel searchStatus, Color textColor)
	{
		searchStatus.setFont(FontManager.getRunescapeSmallFont());
		searchStatus.setForeground(textColor);
		searchStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchStatus.setBorder(new EmptyBorder(0, 4, 2, 0));
		searchStatus.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	static JTextField configureSearchBar(IconTextField searchBar)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);

		ClogHelper.styleSearchBar(searchBar);
		JTextField searchTextField = null;
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField tf =
					((FlatTextField) c).getTextField();
				searchTextField = tf;
				tf.setFont(FontManager.getRunescapeFont());
				tf.setForeground(Color.WHITE);
				tf.setCaretColor(Color.WHITE);
				tf.putClientProperty(
					RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			}
			else if (c instanceof Container)
			{
				ClogHelper.styleSearchBar((Container) c);
			}
		}
		return searchTextField;
	}

	static JPanel buildSearchRow(IconTextField searchBar, JLabel compareLabel, IntSupplier compareWidth)
	{
		JPanel searchRow = new JPanel(null)
		{
			@Override
			public void doLayout()
			{
				int w = getWidth(), h = getHeight();
				int compareW = compareWidth.getAsInt();
				searchBar.setBounds(0, 0, w - compareW, h);
				compareLabel.setBounds(w - compareW, 0, compareW, h);
			}
		};
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchRow.setPreferredSize(new Dimension(0, 30));
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchRow.setOpaque(false);
		searchRow.add(compareLabel);
		searchRow.add(searchBar);
		return searchRow;
	}
}
