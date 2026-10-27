package com.killclog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ColorUtil;

/**
 * Manages tooltip display mechanics: pinned popups, cell hover effects,
 * and component-scoped ToolTipManager registration.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class TooltipController
{
	static final Border CELL_BORDER = new EmptyBorder(1, 1, 1, 1);
	// A grey-text cell's outline lifts the cell's own background toward white: RuneLite's grey 30 becomes 90.
	private static final double OUTLINE_TINT = 60 / 225.0;
	// Tint lifts a hovered cell's own background this far toward white: RuneLite's grey 30 becomes 41, a green 39 becomes 50.
	private static final double HOVER_TINT = 0.05;

	private final KillClogConfig config;

	// Stable popup state. Click mode opens this directly; hover mode promotes its
	// transient preview into the same popup when the source is pressed.
	private Popup activePinnedPopup;
	private JToolTip activePinnedTip;
	private JComponent activePinnedComponent;
	private JPanel activePinnedCell;
	private boolean pinnedFromHover;
	private JComponent suppressedTooltipComponent;
	private String suppressedTooltipText;
	private AWTEventListener pinDismissListener;
	private JComponent pinDismissedComponent;
	private Window focusWindow;
	private WindowAdapter windowFocusListener;

	// Cell hover state. Only the panel's own cells take the outline; a card pinned under another
	// surface, like the info row the Collection Log card opens from, leaves that surface as it is.
	private JPanel hoveredCell;
	// The hovered cell's own background, which it gets back when the pointer leaves.
	private Color hoveredRest;
	private final Set<JPanel> hoverCells = Collections.newSetFromMap(new WeakHashMap<>());
	private Timer hoverExitTimer;
	// While the panel autoscrolls, cells pass under a still pointer: no hover tint and no hover card until it ends.
	private boolean hoverSuspended;

	// ToolTipManager is shared by every RuneLite plugin. Track only Kill Clog's
	// components so click mode never changes another plugin's tooltip behavior.
	private final Set<JComponent> tooltipComponents =
		Collections.newSetFromMap(new WeakHashMap<>());

	// A shift-press opens a card and copies it as a picture, ready to paste (Dylan, 2026-10-20): where it goes, and
	// who says whether it went.
	@Setter(AccessLevel.PACKAGE)
	private Predicate<BufferedImage> clipboard = TooltipController::toClipboard;
	@Setter(AccessLevel.PACKAGE)
	private Consumer<Boolean> cardCopied = copied ->
	{
	};
	// A look every 200 ms; unchanged for two in a row (400 ms) means drawn, and 2.5 s copies whatever shows.
	private static final int COPY_LOOK_MS = 200;
	private static final int COPY_QUIET_LOOKS = 2;
	private static final int COPY_WAIT_MS = 2500;
	private Timer copyTimer;

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
				hoverCell(cell, colorSource);
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
		hoverCells.add(cell);
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
			hoveredCell.setBackground(hoveredRest);
		}
	}

	/**
	 * Wire mouse + hierarchy listeners on a tooltip to clear the parent cell's hover
	 * when the tooltip hides. Listeners attach to the tooltip and its children
	 * (a side-by-side comparison tooltip hosts two live child tooltips), and the
	 * exit handler checks position so crossing between the composite and a child
	 * never reads as leaving the tooltip.
	 */
	void keepTooltipOnHover(JToolTip tip, JPanel parentCell)
	{
		attachToTipTree(tip, new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				// Guard against the WHOLE tooltip: a child card's exit while
				// crossing into its sibling must not read as leaving.
				if (((JToolTip) NativeTooltip.tooltipRoot(tip)).getMousePosition(true) != null)
				{
					return;
				}
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

	private static void attachToTipTree(JToolTip tip, MouseListener listener)
	{
		tip.addMouseListener(listener);
		for (Component child : tip.getComponents())
		{
			child.addMouseListener(listener);
		}
	}

	/**
	 * A press on anything in the panel that opens a card: the left button opens it (with shift, copies it too), the
	 * right one offers both, and the middle one scrolls the panel ({@link PanelAutoscroll}).
	 */
	void pinTooltipFromPress(JComponent source, JPanel cell, MouseEvent event)
	{
		if (SwingUtilities.isMiddleMouseButton(event))
		{
			return;
		}
		if (SwingUtilities.isRightMouseButton(event))
		{
			cardMenu(source, cell, event).show(event.getComponent(), event.getX(), event.getY());
			return;
		}
		openFromPress(source, cell, event, event.isShiftDown());
	}

	/** The right button's menu: what a click and a shift-click do, named. */
	JPopupMenu cardMenu(JComponent source, JPanel cell, MouseEvent press)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem open = new JMenuItem("Open");
		open.addActionListener(a -> openFromPress(source, cell, press, false));
		JMenuItem copy = new JMenuItem("Copy (Shift+Click)");
		copy.addActionListener(a -> openFromPress(source, cell, press, true));
		menu.add(open);
		menu.add(copy);
		return menu;
	}

	private void openFromPress(JComponent source, JPanel cell, MouseEvent event, boolean copy)
	{
		if (config.tooltipMode() == TooltipMode.HOVER)
		{
			dismissHoverPreview(event);
		}
		showPinnedTooltip(source, cell);
		if (copy)
		{
			copyPinned(source);
		}
	}

	/** Opens a card where the owner's card was, with a back line that reopens the owner's own card. */
	void openInPlace(JLabel owner, MouseEvent press, JToolTip card, String backLabel)
	{
		pinTooltipFromPress(owner, (JPanel) owner.getParent(), press, TitleTooltip.withBack(card, backLabel, back ->
			pinTooltipFromPress(owner, (JPanel) owner.getParent(), back, owner.createToolTip())));
	}

	/**
	 * A press inside one modal that opens another in its place, the way a rare
	 * row opens its collection from the Clue Summary. The hover preview is a
	 * window of its own and has to be taken down like any other press does, and
	 * the cell keeps the outline that modal's closing just cleared.
	 */
	void pinTooltipFromPress(JComponent source, JPanel cell, MouseEvent event, JToolTip tip)
	{
		if (config.tooltipMode() == TooltipMode.HOVER)
		{
			dismissHoverPreview(event);
		}
		pinTooltip(source, cell, tip);
		if (hoverCells.contains(cell))
		{
			hoverCell(cell, source);
		}
		if (event.isShiftDown())
		{
			copyPinned(source);
		}
	}

	/**
	 * Copies the card the source just opened, once it has finished drawing: a card's item pictures load as it first
	 * draws, so the copy waits until none is still on its way and the card has looked the same for
	 * {@link #COPY_QUIET_LOOKS} looks in a row, at most {@link #COPY_WAIT_MS}. A card closed first is not copied.
	 */
	private void copyPinned(JComponent source)
	{
		JToolTip tip = activePinnedTip;
		if (activePinnedComponent != source || tip == null)
		{
			return;
		}
		stopCopy();
		long until = System.currentTimeMillis() + COPY_WAIT_MS;
		BufferedImage[] last = new BufferedImage[1];
		int[] same = new int[1];
		copyTimer = new Timer(COPY_LOOK_MS, e ->
		{
			if (activePinnedTip != tip)
			{
				stopCopy();
				return;
			}
			BufferedImage now = cardImage(tip);
			same[0] = !TooltipItemSprites.loading(tip) && last[0] != null && samePicture(last[0], now) ? same[0] + 1 : 0;
			if (same[0] >= COPY_QUIET_LOOKS || System.currentTimeMillis() >= until)
			{
				stopCopy();
				cardCopied.accept(clipboard.test(now));
			}
			last[0] = now;
		});
		copyTimer.setInitialDelay(0);
		copyTimer.start();
	}

	private void stopCopy()
	{
		if (copyTimer != null)
		{
			copyTimer.stop();
			copyTimer = null;
		}
	}

	private static boolean samePicture(BufferedImage a, BufferedImage b)
	{
		int w = a.getWidth();
		int h = a.getHeight();
		return w == b.getWidth() && h == b.getHeight()
			&& Arrays.equals(a.getRGB(0, 0, w, h, null, 0, w), b.getRGB(0, 0, w, h, null, 0, w));
	}

	/**
	 * The card exactly as it opened, the way the lab draws one: before the pointer is on it. A card that scrolls, or a
	 * comparison's cards that do, is drawn whole at its full height, then laid back down at its own size, so the one
	 * on screen keeps its rows where they show.
	 */
	static BufferedImage cardImage(JToolTip tip)
	{
		List<CardBody.Scroll> scrolls = new ArrayList<>();
		for (Component card : tip instanceof SideBySideTooltip ? tip.getComponents() : new Component[]{tip})
		{
			CardBody.Scroll scroll = card instanceof TitleTooltip ? ((TitleTooltip) card).scroll() : null;
			if (scroll != null)
			{
				scrolls.add(scroll);
			}
		}
		if (scrolls.isEmpty())
		{
			return drawn(tip);
		}
		Dimension shown = tip.getSize();
		try
		{
			return unrolled(scrolls, 0, () ->
			{
				tip.setSize(tip.getPreferredSize());
				tip.doLayout();
				return drawn(tip);
			});
		}
		finally
		{
			tip.setSize(shown);
			tip.doLayout();
			if (shown.width > 0 && shown.height > 0)
			{
				drawn(tip);
			}
		}
	}

	private static BufferedImage unrolled(List<CardBody.Scroll> scrolls, int from, Supplier<BufferedImage> draw)
	{
		return from == scrolls.size() ? draw.get() : scrolls.get(from).unrolled(() -> unrolled(scrolls, from + 1, draw));
	}

	private static BufferedImage drawn(JToolTip tip)
	{
		if (tip.getWidth() <= 0 || tip.getHeight() <= 0)
		{
			tip.setSize(tip.getPreferredSize());
			tip.doLayout();
		}
		BufferedImage image = new BufferedImage(Math.max(1, tip.getWidth()), Math.max(1, tip.getHeight()),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		tip.paint(g);
		g.dispose();
		return image;
	}

	private static boolean toClipboard(BufferedImage image)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable()
			{
				@Override
				public DataFlavor[] getTransferDataFlavors()
				{
					return new DataFlavor[]{DataFlavor.imageFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor)
				{
					return DataFlavor.imageFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
				{
					if (!isDataFlavorSupported(flavor))
					{
						throw new UnsupportedFlavorException(flavor);
					}
					return image;
				}
			}, null);
			return true;
		}
		catch (IllegalStateException | HeadlessException e)
		{
			// Another program holds the clipboard, or there is no desktop to hold one.
			return false;
		}
	}

	private void hoverCell(JPanel cell, JComponent colorSource)
	{
		if (hoveredCell == cell || hoverSuspended) return;

		if (hoveredCell != null) resetCellHover();

		hoveredCell = cell;
		hoveredRest = cell.getBackground();
		switch (config.hoverStyle())
		{
			case OUTLINE:
				Color fg = colorSource.getForeground();
				Color outline = (fg.equals(KillClogColors.KC_COLOR) || fg.equals(ColorScheme.LIGHT_GRAY_COLOR))
					? ColorUtil.colorLerp(hoveredRest, Color.WHITE, OUTLINE_TINT) : fg;
				cell.setBorder(new MatteBorder(1, 1, 1, 1, outline));
				break;
			case TINT:
				cell.setBackground(ColorUtil.colorLerp(hoveredRest, Color.WHITE, HOVER_TINT));
				break;
			case NONE:
				break;
		}
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
		pinTooltip(source, cell, source.createToolTip());
	}

	/**
	 * Pin a specific modal on a cell: how one modal opens another in its place.
	 * It skips the toggle-off check above, because the press that asked for this
	 * is the same press that just dismissed the modal it came from.
	 */
	void pinTooltip(JComponent source, JPanel cell, JToolTip tip)
	{
		// A row pressed in a preview that outlived its panel has nowhere to open.
		if (!source.isShowing())
		{
			return;
		}
		pinDismissedComponent = null;

		hidePinnedTooltip();

		String tooltipText = source.getToolTipText();
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
		// Wide side-by-side tooltips must stay reachable: clamp the top and
		// left edges too, like the hover path does.
		x = Math.max(x, screen.x);
		y = Math.max(y, screen.y);

		activePinnedPopup = PopupFactory.getSharedInstance().getPopup(cell, tip, x, y);
		activePinnedTip = tip;
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
				if (onCardRail((MouseEvent) event))
				{
					return;
				}
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

	/** A press on a card's own scroll rail scrolls it, and the card stays. */
	static boolean onCardRail(MouseEvent press)
	{
		Component source = press.getComponent();
		Component card = source instanceof TitleTooltip ? source
			: SwingUtilities.getAncestorOfClass(TitleTooltip.class, source);
		return card != null && CardBody.onRail(card, SwingUtilities.convertPoint(source, press.getPoint(), card));
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
		activePinnedTip = null;
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
		attachToTipTree(tip, new MouseAdapter()
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
			&& !hoverSuspended
			&& component.getToolTipText() != null;
		if (shouldRegister && !registered)
		{
			manager.registerComponent(component);
		}
		else if (!shouldRegister && registered)
		{
			manager.unregisterComponent(component);
		}
		if (!shouldRegister)
		{
			// Hovering adds the manager itself as a motion listener, and neither its unregister nor a press removes
			// it; that listener alone restarts the hover timer, so a cell clicked earlier opened a second card over
			// a pinned one whenever the mouse crossed it.
			component.removeMouseMotionListener(manager);
		}
	}

	/** Autoscroll began: the hover card the press found goes, and hovering waits until {@link #resumeHover}. */
	void suspendHover(MouseEvent press)
	{
		dismissHoverPreview(press);
		hoverSuspended = true;
		clearHoveredCell();
		refreshTooltipRegistrations();
	}

	void resumeHover()
	{
		hoverSuspended = false;
		refreshTooltipRegistrations();
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
