package com.killclog;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;

class UnderlineLabel extends JLabel
{
	private final boolean leftAligned;

	UnderlineLabel(boolean leftAligned)
	{
		super(" ");
		this.leftAligned = leftAligned;
	}

	static void installHoverUnderline(JLabel label, BooleanSupplier shouldUnderline)
	{
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (shouldUnderline.getAsBoolean())
				{
					label.putClientProperty("underlined", true);
					label.repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.putClientProperty("underlined", null);
				label.repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (!Boolean.TRUE.equals(getClientProperty("underlined")))
		{
			return;
		}
		String text = getText();
		if (text == null || text.isBlank())
		{
			return;
		}
		FontMetrics fm = g.getFontMetrics();
		int textWidth = fm.stringWidth(text.trim());
		int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 + 1;
		g.setColor(getForeground());
		if (leftAligned)
		{
			int iconOffset = getIcon() != null ? getIcon().getIconWidth() + getIconTextGap() : 0;
			int textStart = getInsets().left + iconOffset;
			g.drawLine(textStart, y, textStart + textWidth, y);
		}
		else
		{
			int textEnd = getWidth() - getInsets().right;
			g.drawLine(textEnd - textWidth, y, textEnd, y);
		}
	}
}
