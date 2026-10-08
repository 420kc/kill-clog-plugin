package com.killclog;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;

/**
 * A summary-bar label that answers hover in k2, the Kill Clog palette's step up from the bar's k4, as every other
 * hover in the plugin answers in color. Its own color is never touched, so nothing can leave it lit.
 */
class HoverLabel extends JLabel
{
	private static final Color HOVER = new Color(0xCAFF00);

	HoverLabel()
	{
		super(" ");
	}

	static void installHover(JLabel label, BooleanSupplier answers)
	{
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.putClientProperty("hovered", answers.getAsBoolean() ? true : null);
				label.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.putClientProperty("hovered", null);
				label.repaint();
			}
		});
	}

	@Override
	public Color getForeground()
	{
		return getClientProperty("hovered") != null ? HOVER : super.getForeground();
	}
}
