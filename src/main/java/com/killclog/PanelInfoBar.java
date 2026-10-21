package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ColorUtil;

final class PanelInfoBar
{
	// The menu button lifts the bar it sits on toward white, at rest and hovered, so it reads on any background: on
	// RuneLite's grey 40 these are the 70 and 96 it always drew (30 and 56 of the 215 steps to white).
	private static final double HAMBURGER_TINT = 30 / 215.0;
	private static final double HAMBURGER_HOVER_TINT = 56 / 215.0;

	private PanelInfoBar()
	{
	}

	static JPanel build(JLabel playerName, JLabel clogInfoLabel,
		TooltipController tooltipController,
		BooleanSupplier comparisonMode, Runnable toggleBossView)
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

		installInfoClicks(playerName, infoRow, tooltipController);
		installInfoClicks(clogInfoLabel, infoRow, tooltipController);
		for (JLabel barLabel : new JLabel[]{playerName, clogInfoLabel})
		{
			HoverLabel.installHover(barLabel,
				() -> barLabel.getToolTipText() != null || comparisonMode.getAsBoolean());
		}

		infoRow.add(playerName);
		infoRow.add(buildViewToggle(toggleBossView, infoRow.getBackground()));
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
		ClogHelper.antialias(label);
	}

	private static void installInfoClicks(JLabel label, JPanel infoRow,
		TooltipController tooltipController)
	{
		tooltipController.trackTooltipComponent(label);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (label.getToolTipText() != null)
				{
					tooltipController.pinTooltipFromPress(label, infoRow, e);
				}
			}
		});
	}

	/** The hamburger: switches the boss area between grid and list views. */
	private static JLabel buildViewToggle(Runnable toggleBossView, Color bar)
	{
		JLabel viewToggle = new JLabel();
		ImageIcon hamburgerIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(
			ColorUtil.colorLerp(bar, Color.WHITE, HAMBURGER_TINT)));
		ImageIcon hamburgerHoverIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(
			ColorUtil.colorLerp(bar, Color.WHITE, HAMBURGER_HOVER_TINT)));
		viewToggle.setIcon(hamburgerIcon);
		viewToggle.setHorizontalAlignment(JLabel.CENTER);
		viewToggle.setVerticalAlignment(JLabel.CENTER);
		viewToggle.setPreferredSize(new Dimension(18, 18));
		viewToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleBossView.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				viewToggle.setIcon(hamburgerHoverIcon);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				viewToggle.setIcon(hamburgerIcon);
			}
		});
		return viewToggle;
	}
}
