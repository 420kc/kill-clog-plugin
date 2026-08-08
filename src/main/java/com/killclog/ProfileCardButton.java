package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/** Bare camera glyph for the panel chrome. */
final class ProfileCardButton extends JComponent
{
	private final Runnable onClick;
	private final Runnable onEnter;
	private final Runnable onExit;
	private final Supplier<Color> hoverColor;
	private boolean hovered;

	ProfileCardButton(Runnable onClick, Runnable onEnter, Runnable onExit,
		Supplier<Color> hoverColor)
	{
		this.onClick = onClick;
		this.onEnter = onEnter;
		this.onExit = onExit;
		this.hoverColor = hoverColor;
		setOpaque(false);
		setPreferredSize(new Dimension(21, 16));
		setMinimumSize(getPreferredSize());
		setMaximumSize(getPreferredSize());
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				hovered = true;
				repaint();
				onEnter.run();
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hovered = false;
				repaint();
				onExit.run();
			}

			@Override
			public void mousePressed(MouseEvent event)
			{
				if (event.getButton() == MouseEvent.BUTTON1 && isVisible())
				{
					onClick.run();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		Color color = hovered ? hoverColor.get() : ColorScheme.LIGHT_GRAY_COLOR;
		g.setColor(color);

		int x = (getWidth() - 13) / 2;
		int y = (getHeight() - 9) / 2;
		g.drawRoundRect(x, y, 12, 8, 2, 2);
		g.drawLine(x + 3, y - 2, x + 6, y - 2);
		g.drawLine(x + 2, y - 1, x + 7, y - 1);
		g.drawOval(x + 4, y + 2, 4, 4);
		g.dispose();
	}
}
