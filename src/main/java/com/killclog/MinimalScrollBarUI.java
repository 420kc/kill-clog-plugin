package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.ui.ColorScheme;

/** Slim thumb, no arrow buttons, dark theme. */
final class MinimalScrollBarUI extends BasicScrollBarUI
{
	@Override
	protected void configureScrollBarColors()
	{
		thumbColor = new Color(70, 70, 70);
		trackColor = ColorScheme.DARKER_GRAY_COLOR;
	}

	@Override
	protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
	{
		if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
		g.setColor(isThumbRollover() ? new Color(110, 110, 110) : thumbColor);
		g.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
	}

	@Override
	protected JButton createDecreaseButton(int orientation)
	{
		return makeZeroButton();
	}

	@Override
	protected JButton createIncreaseButton(int orientation)
	{
		return makeZeroButton();
	}

	private static JButton makeZeroButton()
	{
		JButton btn = new JButton();
		Dimension d = new Dimension(0, 0);
		btn.setPreferredSize(d);
		btn.setMinimumSize(d);
		btn.setMaximumSize(d);
		return btn;
	}
}
