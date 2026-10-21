package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ColorUtil;

/** Slim thumb, no arrow buttons, dark theme. */
final class MinimalScrollBarUI extends BasicScrollBarUI
{
	static final int WIDTH = 7;
	// The thumb lifts its track toward white, more while hovered, so it reads on any theme: on RuneLite's grey 30
	// it is 70, hovered 110.
	private static final double THUMB_TINT = 40 / 225.0;
	private static final double ROLLOVER_TINT = 80 / 225.0;

	// The track and thumb are read from the panel's background as they paint, so they follow it whatever it is now.
	@Override
	protected void configureScrollBarColors()
	{
		trackColor = ColorScheme.DARKER_GRAY_COLOR;
	}

	@Override
	public void paint(Graphics g, JComponent c)
	{
		trackColor = ColorScheme.DARKER_GRAY_COLOR;
		super.paint(g, c);
	}

	@Override
	protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
	{
		if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
		g.setColor(ColorUtil.colorLerp(trackColor, Color.WHITE, isThumbRollover() ? ROLLOVER_TINT : THUMB_TINT));
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
