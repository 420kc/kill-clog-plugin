package com.killclog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Manages tooltip display mechanics: pinned popups, cell hover effects,
 * and component-scoped ToolTipManager registration.
 */
class TooltipController
{
	static final Border CELL_BORDER = new EmptyBorder(1, 1, 1, 1);
	private static final Color HOVER_OUTLINE_DIM = new Color(90, 90, 90);
	private static final Color HOVER_TINT_BG = new Color(41, 41, 41);
	private static final Color KC_COLOR = KillClogPanel.KC_COLOR;

	private final KillClogConfig config;

	// Stable popup state. Click mode opens this directly; hover mode promotes its
	// transient preview into the same popup when the source is pressed.
	private Popup activePinnedPopup;
	private JComponent activePinnedComponent;
	private JPanel activePinnedCell;
	private boolean pinnedFromHover;
	private JComponent suppressedTooltipComponent;
	private String suppressedTooltipText;
	private AWTEventListener pinDismissListener;
	private JComponent pinDismissedComponent;
	private Window focusWindow;
	private WindowAdapter windowFocusListener;

	// Cell hover state.
	private JPanel hoveredCell;
	private Timer hoverExitTimer;

	// ToolTipManager is shared by every RuneLite plugin. Track only Kill Clog's
	// components so click mode never changes another plugin's tooltip behavior.
	private final Set<JComponent> tooltipComponents =
		Collections.newSetFromMap(new WeakHashMap<>());

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
		addCellHoverEffect(cell, label, label);
	}

	/**
	 * Row-shaped variant: the hover outline follows {@code colorSource}'s
	 * foreground while the cell and every listed surface accept hover and
	 * click. The pinned tooltip comes from the pressed surface when it carries
	 * one, else from the first surface (which also keeps the popup anchor
	 * stable for presses on the bare cell).
	 */
	void addCellHoverEffect(JPanel cell, JLabel colorSource, JLabel... surfaces)
	{
		MouseAdapter hoverAdapter = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				JLabel source = e.getSource() instanceof JLabel
					&& ((JLabel) e.getSource()).getToolTipText() != null
					? (JLabel) e.getSource() : surfaces[0];
				if (source.getToolTipText() != null)
				{
					pinTooltipFromPress(source, cell, e);
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
						Color fg = colorSource.getForeground();
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
					if (hoveredCell == cell
						&& !(pinnedFromHover && activePinnedCell == cell))
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
		for (JLabel surface : surfaces)
		{
			surface.addMouseListener(hoverAdapter);
			trackTooltipComponent(surface);
		}
	}

	void trackTooltipComponent(JComponent component)
	{
		if (tooltipComponents.add(component))
		{
			component.addPropertyChangeListener("ToolTipText", event ->
				SwingUtilities.invokeLater(() -> applyTooltipMode(component)));
		}
		applyTooltipMode(component);
	}

	/**
	 * Update a tracked tooltip and immediately reapply this controller's
	 * registration rule. Swing otherwise re-registers null-to-value changes
	 * even while another tooltip is pinned.
	 */
	void setTooltipText(JComponent component, String tooltipText)
	{
		component.setToolTipText(tooltipText);
		applyTooltipMode(component);
	}

	void onTooltipModeChanged()
	{
		hideTransientTooltipState();
		refreshTooltipRegistrations();
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

	void pinTooltipFromPress(JComponent source, JPanel cell, MouseEvent event)
	{
		if (config.tooltipMode() == TooltipMode.HOVER)
		{
			dismissHoverPreview(event);
		}
		showPinnedTooltip(source, cell);
	}

	void dismissHoverPreview(MouseEvent event)
	{
		ToolTipManager.sharedInstance().mousePressed(event);
	}

	/** Pin the tooltip for any component with tooltip text (labels included). */
	void showPinnedTooltip(JComponent source, JPanel cell)
	{
		// The global listener dismissed this component on the same click; treat it as toggle-off.
		if (source == pinDismissedComponent)
		{
			pinDismissedComponent = null;
			return;
		}
		pinDismissedComponent = null;

		hidePinnedTooltip();

		String tooltipText = source.getToolTipText();
		JToolTip tip = source.createToolTip();
		tip.setTipText(tooltipText);
		guardPinnedTooltip(tip);

		Dimension tipSize = tip.getPreferredSize();

		Point sourceLoc = source.getLocationOnScreen();
		Point cellLoc = cell.getLocationOnScreen();
		Rectangle screen = cell.getGraphicsConfiguration().getBounds();

		int x = sourceLoc.x;
		int y = cellLoc.y + cell.getHeight();

		if (x + tipSize.width > screen.x + screen.width)
		{
			x = screen.x + screen.width - tipSize.width;
		}
		if (y + tipSize.height > screen.y + screen.height)
		{
			y = cellLoc.y - tipSize.height;
		}

		activePinnedPopup = PopupFactory.getSharedInstance().getPopup(cell, tip, x, y);
		activePinnedComponent = source;
		activePinnedCell = cell;
		pinnedFromHover = config.tooltipMode() == TooltipMode.HOVER;
		suppressPinnedSourceTooltip(source, tooltipText);
		activePinnedPopup.show();
		refreshTooltipRegistrations();

		pinDismissListener = event ->
		{
			if (event.getID() == MouseEvent.MOUSE_PRESSED)
			{
				JComponent dismissedComponent = activePinnedComponent;
				pinDismissedComponent = dismissedComponent;
				hidePinnedTooltip();
				SwingUtilities.invokeLater(() ->
				{
					if (pinDismissedComponent == dismissedComponent)
					{
						pinDismissedComponent = null;
					}
				});
			}
			else if (event.getID() == KeyEvent.KEY_PRESSED
				&& ((KeyEvent) event).getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				pinDismissedComponent = null;
				hidePinnedTooltip();
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(
			pinDismissListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
	}

	void hidePinnedTooltip()
	{
		JPanel pinnedCell = activePinnedCell;
		boolean clearPinnedHover = pinnedFromHover;
		boolean hadPopup = activePinnedPopup != null;

		if (pinDismissListener != null)
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener(pinDismissListener);
			pinDismissListener = null;
		}
		if (activePinnedPopup != null)
		{
			activePinnedPopup.hide();
			activePinnedPopup = null;
		}
		activePinnedComponent = null;
		activePinnedCell = null;
		pinnedFromHover = false;
		restorePinnedSourceTooltip();

		if (hadPopup)
		{
			refreshTooltipRegistrations();
		}
		if (clearPinnedHover && pinnedCell != null)
		{
			clearCellHover(pinnedCell);
		}
	}

	/** Dismiss only when a refresh is replacing the pinned tooltip's own source. */
	void hidePinnedTooltipIfOwnedBy(JComponent source)
	{
		if (activePinnedComponent == source)
		{
			hidePinnedTooltip();
		}
	}

	void guardPinnedTooltip(JToolTip tip)
	{
		tip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				dismissHoverPreview(event);
			}
		});
	}

	void suppressPinnedSourceTooltip(JComponent component, String tooltipText)
	{
		suppressedTooltipComponent = component;
		suppressedTooltipText = tooltipText;
		setTooltipText(component, null);
	}

	void restorePinnedSourceTooltip()
	{
		JComponent component = suppressedTooltipComponent;
		String tooltipText = suppressedTooltipText;
		suppressedTooltipComponent = null;
		suppressedTooltipText = null;
		if (component != null && component.getToolTipText() == null)
		{
			setTooltipText(component, tooltipText);
		}
	}

	void activate(Component owner)
	{
		onTooltipModeChanged();
		installWindowFocusListener(owner);
	}

	void deactivate()
	{
		hideTransientTooltipState();
		uninstallWindowFocusListener();
	}

	void clearHoveredCell()
	{
		if (hoverExitTimer != null)
		{
			hoverExitTimer.stop();
			hoverExitTimer = null;
		}
		resetCellHover();
		hoveredCell = null;
	}

	private void clearCellHover(JPanel cell)
	{
		if (hoveredCell == cell)
		{
			if (pinnedFromHover && activePinnedCell == cell)
			{
				return;
			}
			Point mouse = cell.getMousePosition();
			if (mouse != null)
			{
				return;
			}
			resetCellHover();
			hoveredCell = null;
		}
	}

	private void applyTooltipMode(JComponent component)
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		boolean registered = false;
		for (MouseListener listener : component.getMouseListeners())
		{
			if (listener == manager)
			{
				registered = true;
				break;
			}
		}

		boolean shouldRegister = config.tooltipMode() == TooltipMode.HOVER
			&& !hasPinnedTooltip()
			&& component.getToolTipText() != null;
		if (shouldRegister && !registered)
		{
			manager.registerComponent(component);
		}
		else if (!shouldRegister && registered)
		{
			manager.unregisterComponent(component);
		}
	}

	boolean hasPinnedTooltip()
	{
		return activePinnedPopup != null;
	}

	private void refreshTooltipRegistrations()
	{
		for (JComponent component : new ArrayList<>(tooltipComponents))
		{
			applyTooltipMode(component);
		}
	}

	private void installWindowFocusListener(Component owner)
	{
		if (owner == null)
		{
			return;
		}
		Window window = SwingUtilities.getWindowAncestor(owner);
		if (window == null)
		{
			return;
		}
		if (focusWindow == window && windowFocusListener != null)
		{
			return;
		}
		uninstallWindowFocusListener();
		focusWindow = window;
		windowFocusListener = new WindowAdapter()
		{
			@Override
			public void windowLostFocus(WindowEvent e)
			{
				hideTransientTooltipState();
			}
		};
		window.addWindowFocusListener(windowFocusListener);
	}

	private void uninstallWindowFocusListener()
	{
		if (focusWindow != null && windowFocusListener != null)
		{
			focusWindow.removeWindowFocusListener(windowFocusListener);
		}
		focusWindow = null;
		windowFocusListener = null;
	}

	private void hideTransientTooltipState()
	{
		hidePinnedTooltip();
		clearHoveredCell();
	}
}
