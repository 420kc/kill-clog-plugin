package com.killclog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Manages tooltip display mechanics: click-to-reveal popups, cell hover effects,
 * and ToolTipManager delay capture/restore.
 */
class TooltipController
{
	static final Border CELL_BORDER = new EmptyBorder(1, 1, 1, 1);
	private static final Color HOVER_OUTLINE_DIM = new Color(90, 90, 90);
	private static final Color HOVER_TINT_BG = new Color(41, 41, 41);
	private static final Color KC_COLOR = KillClogPanel.KC_COLOR;

	private final KillClogConfig config;

	// Click-to-reveal state
	private Popup activeClickPopup;
	private JLabel activeClickLabel;
	private AWTEventListener clickDismissListener;
	private JLabel clickDismissedLabel;

	// Hover state
	private JPanel hoveredCell;
	private Timer hoverExitTimer;

	// ToolTipManager defaults — captured in onActivate, restored in onDeactivate
	private int defaultDismissDelay;
	private int defaultInitialDelay = -1;

	TooltipController(KillClogConfig config)
	{
		this.config = config;
	}

	/**
	 * Wire hover effect onto a cell panel.
	 * Shows outline or tint on hover, handles click-to-reveal.
	 * 150ms debounced exit keeps the effect while tooltip is open.
	 */
	void addCellHoverEffect(JPanel cell, JLabel label)
	{
		MouseAdapter hoverAdapter = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (config.tooltipMode() == TooltipMode.CLICK && label.getToolTipText() != null)
				{
					showClickTooltip(label, cell);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();

				if (hoveredCell == cell) return;

				if (hoveredCell != null) resetCellHover();

				hoveredCell = cell;
				switch (config.hoverStyle())
				{
					case OUTLINE:
						Color fg = label.getForeground();
						Color outline = (fg.equals(KC_COLOR) || fg.equals(ColorScheme.LIGHT_GRAY_COLOR))
							? HOVER_OUTLINE_DIM : fg;
						cell.setBorder(new MatteBorder(1, 1, 1, 1, outline));
						break;
					case TINT:
						cell.setBackground(HOVER_TINT_BG);
						break;
					case NONE:
						break;
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();
				hoverExitTimer = new Timer(150, evt ->
				{
					if (hoveredCell == cell)
					{
						resetCellHover();
						hoveredCell = null;
					}
				});
				hoverExitTimer.setRepeats(false);
				hoverExitTimer.start();
			}
		};
		cell.addMouseListener(hoverAdapter);
		label.addMouseListener(hoverAdapter);
	}

	void resetCellHover()
	{
		if (hoveredCell != null)
		{
			hoveredCell.setBorder(CELL_BORDER);
			hoveredCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
	}

	/**
	 * Wire mouse + hierarchy listeners on a tooltip to clear the parent cell's hover
	 * when the tooltip hides.
	 */
	void keepTooltipOnHover(JToolTip tip, JPanel parentCell)
	{
		tip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clearCellHover(parentCell);
			}
		});
		tip.addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !tip.isShowing())
			{
				clearCellHover(parentCell);
			}
		});
	}

	void showClickTooltip(JLabel label, JPanel cell)
	{
		// AWTEventListener dismissed this label on the same click — treat as toggle-off
		if (label == clickDismissedLabel)
		{
			clickDismissedLabel = null;
			return;
		}
		clickDismissedLabel = null;

		hideClickTooltip();

		JToolTip tip = label.createToolTip();
		tip.setTipText(label.getToolTipText());

		if (tip instanceof NativeTooltip)
		{
			((NativeTooltip) tip).setCloseAction(this::hideClickTooltip);
		}

		Dimension tipSize = tip.getPreferredSize();

		// Position below cell, aligned to label's x, screen-bounds aware
		Point labelLoc = label.getLocationOnScreen();
		Point cellLoc = cell.getLocationOnScreen();
		Rectangle screen = cell.getGraphicsConfiguration().getBounds();

		int x = labelLoc.x;
		int y = cellLoc.y + cell.getHeight();

		if (x + tipSize.width > screen.x + screen.width)
		{
			x = screen.x + screen.width - tipSize.width;
		}
		if (y + tipSize.height > screen.y + screen.height)
		{
			y = cellLoc.y - tipSize.height;
		}

		activeClickPopup = PopupFactory.getSharedInstance().getPopup(cell, tip, x, y);
		activeClickLabel = label;
		activeClickPopup.show();

		// Dismiss on next click anywhere
		clickDismissListener = event ->
		{
			if (event.getID() == MouseEvent.MOUSE_PRESSED)
			{
				clickDismissedLabel = activeClickLabel;
				hideClickTooltip();
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(
			clickDismissListener, AWTEvent.MOUSE_EVENT_MASK);
	}

	void hideClickTooltip()
	{
		if (clickDismissListener != null)
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener(clickDismissListener);
			clickDismissListener = null;
		}
		if (activeClickPopup != null)
		{
			activeClickPopup.hide();
			activeClickPopup = null;
			activeClickLabel = null;
		}
	}

	/** Capture ToolTipManager defaults — call from onActivate(). */
	void captureDefaults()
	{
		defaultDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
		ToolTipManager.sharedInstance().setDismissDelay(15000);

		if (config.tooltipMode() == TooltipMode.CLICK)
		{
			defaultInitialDelay = ToolTipManager.sharedInstance().getInitialDelay();
			ToolTipManager.sharedInstance().setInitialDelay(Integer.MAX_VALUE);
		}
	}

	/** Restore ToolTipManager defaults — call from onDeactivate()/shutdown(). */
	void restoreDefaults()
	{
		ToolTipManager.sharedInstance().setDismissDelay(defaultDismissDelay);
		if (defaultInitialDelay >= 0)
		{
			ToolTipManager.sharedInstance().setInitialDelay(defaultInitialDelay);
			defaultInitialDelay = -1;
		}
		hideClickTooltip();
	}

	void clearHoveredCell()
	{
		resetCellHover();
		hoveredCell = null;
	}

	private void clearCellHover(JPanel cell)
	{
		if (hoveredCell == cell)
		{
			Point mouse = cell.getMousePosition();
			if (mouse != null)
			{
				return;
			}
			resetCellHover();
			hoveredCell = null;
		}
	}
}
