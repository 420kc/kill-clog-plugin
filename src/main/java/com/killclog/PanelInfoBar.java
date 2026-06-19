package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class PanelInfoBar
{
	private static final Color HAMBURGER_COLOR = new Color(70, 70, 70);
	private static final Color HAMBURGER_HOVER_COLOR = new Color(96, 96, 96);

	private PanelInfoBar()
	{
	}

	static JPanel build(JLabel playerName, JLabel clogInfoLabel,
		TooltipController tooltipController, Supplier<TooltipMode> tooltipMode,
		BooleanSupplier comparisonMode, Runnable toggleActivities)
	{
		JPanel infoRow = new JPanel(null)
		{
			@Override
			public void doLayout()
			{
				if (getComponentCount() < 3)
				{
					return;
				}

				int width = getWidth();
				int height = getHeight();
				int gap = 4;
				Component left = getComponent(0);
				Component center = getComponent(1);
				Component right = getComponent(2);
				Dimension centerSize = center.getPreferredSize();
				int centerW = centerSize.width;
				int centerH = Math.min(height, centerSize.height);
				int centerX = Math.max(0, (width - centerW) / 2);
				int centerY = Math.max(0, (height - centerH) / 2);
				int rightX = Math.min(width, centerX + centerW + gap);

				left.setBounds(0, 0, Math.max(0, centerX - gap), height);
				center.setBounds(centerX, centerY, centerW, centerH);
				right.setBounds(rightX, 0, Math.max(0, width - rightX), height);
			}
		};
		infoRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoRow.setPreferredSize(new Dimension(0, 18));

		configureBarLabel(playerName, JLabel.LEFT);
		playerName.setBorder(new EmptyBorder(0, 4, 0, 0));
		playerName.setMinimumSize(new Dimension(0, 0));

		configureBarLabel(clogInfoLabel, JLabel.RIGHT);
		clogInfoLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
		clogInfoLabel.setMinimumSize(new Dimension(0, 0));

		installInfoClicks(playerName, infoRow, tooltipController, tooltipMode);
		installInfoClicks(clogInfoLabel, infoRow, tooltipController, tooltipMode);
		for (JLabel barLabel : new JLabel[]{playerName, clogInfoLabel})
		{
			UnderlineLabel.installHoverUnderline(barLabel,
				() -> barLabel.getToolTipText() != null || comparisonMode.getAsBoolean());
		}

		infoRow.add(playerName);
		infoRow.add(buildTrayToggle(toggleActivities));
		infoRow.add(clogInfoLabel);
		return infoRow;
	}

	private static void configureBarLabel(JLabel label, int alignment)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(alignment);
		label.setVerticalAlignment(JLabel.CENTER);
		label.setVerticalTextPosition(JLabel.CENTER);
		label.setIconTextGap(3);
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private static void installInfoClicks(JLabel label, JPanel infoRow,
		TooltipController tooltipController, Supplier<TooltipMode> tooltipMode)
	{
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (tooltipMode.get() == TooltipMode.CLICK && label.getToolTipText() != null)
				{
					tooltipController.showClickTooltip(label, infoRow);
				}
			}
		});
	}

	private static JLabel buildTrayToggle(Runnable toggleActivities)
	{
		JLabel trayToggle = new JLabel();
		ImageIcon hamburgerIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_COLOR));
		ImageIcon hamburgerHoverIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_HOVER_COLOR));
		trayToggle.setIcon(hamburgerIcon);
		trayToggle.setHorizontalAlignment(JLabel.CENTER);
		trayToggle.setVerticalAlignment(JLabel.CENTER);
		trayToggle.setPreferredSize(new Dimension(18, 18));
		trayToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleActivities.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerHoverIcon);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerIcon);
			}
		});
		return trayToggle;
	}
}
