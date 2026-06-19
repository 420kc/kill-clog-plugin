package com.killclog;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

final class CompareClogTotalsBar
{
	private final BooleanSupplier comparisonMode;
	private final Supplier<TooltipMode> tooltipMode;
	private final TooltipController tooltipController;
	private final Function<JComponent, JToolTip> tooltipFactory;
	private final JPanel panel;
	private final JLabel blueTotal;
	private final JLabel redTotal;

	CompareClogTotalsBar(BooleanSupplier comparisonMode, Supplier<TooltipMode> tooltipMode,
		TooltipController tooltipController, Function<JComponent, JToolTip> tooltipFactory)
	{
		this.comparisonMode = comparisonMode;
		this.tooltipMode = tooltipMode;
		this.tooltipController = tooltipController;
		this.tooltipFactory = tooltipFactory;
		this.panel = buildPanel();
		this.blueTotal = buildTotalLabel(true, JLabel.LEFT);
		this.redTotal = buildTotalLabel(false, JLabel.RIGHT);
		wireClickAndHover();
		layoutLabels();
	}

	JPanel component()
	{
		return panel;
	}

	void setVisible(boolean visible)
	{
		panel.setVisible(visible);
	}

	void update(ClogResult blueClog, ClogResult redClog, PanelIconCache iconCache)
	{
		setTotal(blueTotal, blueClog, iconCache);
		setTotal(redTotal, redClog, iconCache);
		panel.setVisible(true);
		panel.revalidate();
	}

	private JPanel buildPanel()
	{
		JPanel totalsPanel = new JPanel(new GridBagLayout())
		{
			@Override
			public JToolTip createToolTip()
			{
				if (!comparisonMode.getAsBoolean())
				{
					return super.createToolTip();
				}
				return tooltipFactory.apply(this);
			}
		};
		totalsPanel.setOpaque(false);
		totalsPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		totalsPanel.setVisible(false);
		totalsPanel.setToolTipText(" ");
		return totalsPanel;
	}

	private JLabel buildTotalLabel(boolean leftAligned, int alignment)
	{
		JLabel label = new UnderlineLabel(leftAligned)
		{
			@Override
			public JToolTip createToolTip()
			{
				if (!comparisonMode.getAsBoolean())
				{
					return super.createToolTip();
				}
				return tooltipFactory.apply(panel);
			}
		};
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(alignment);
		label.setIconTextGap(3);
		label.setBorder(new EmptyBorder(0, 0, 2, 0));
		label.setToolTipText(" ");
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return label;
	}

	private void wireClickAndHover()
	{
		blueTotal.setForeground(ComparisonController.COMPARE_BLUE);
		redTotal.setForeground(ComparisonController.COMPARE_RED);

		MouseAdapter clickHandler = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (tooltipMode.get() == TooltipMode.CLICK && panel.getToolTipText() != null)
				{
					tooltipController.showClickTooltip(panel, panel);
				}
			}
		};
		panel.addMouseListener(clickHandler);
		blueTotal.addMouseListener(clickHandler);
		redTotal.addMouseListener(clickHandler);
		UnderlineLabel.installHoverUnderline(blueTotal,
			() -> blueTotal.getToolTipText() != null || comparisonMode.getAsBoolean());
		UnderlineLabel.installHoverUnderline(redTotal,
			() -> redTotal.getToolTipText() != null || comparisonMode.getAsBoolean());
	}

	private void layoutLabels()
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.weightx = 1.0;
		c.anchor = GridBagConstraints.WEST;
		panel.add(blueTotal, c);
		c.gridx = 1;
		c.anchor = GridBagConstraints.EAST;
		panel.add(redTotal, c);
	}

	private static void setTotal(JLabel label, ClogResult clog, PanelIconCache iconCache)
	{
		if (clog == null)
		{
			label.setIcon(null);
			label.setText("--");
			return;
		}
		int[] totals = ClogHelper.sumClogTotals(clog);
		String tierName = ClogHelper.getClogTierName(totals[0], totals[1]);
		ImageIcon icon = iconCache.clogTierIcon(tierName);
		label.setIcon(icon);
		label.setText(String.valueOf(totals[0]));
	}
}
