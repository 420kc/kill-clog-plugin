package com.killclog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * Middle-button autoscroll over the panel, as browsers do (Dylan, 2026-10-20): hold it down and move away from where
 * it went down to scroll, faster the farther, so a flick reaches the top or the bottom; let go to stop. A marker
 * shows the anchor; cards and the hover tint wait until it ends.
 */
final class PanelAutoscroll implements AWTEventListener
{
	// Inside this many pixels of the anchor nothing moves, so a held button can rest.
	private static final int DEAD_ZONE = 8;
	private static final int TICK_MS = 16;
	private static final int MARKER_SIZE = 22;

	private final JScrollPane pane;
	private final Consumer<MouseEvent> onStart;
	private final Runnable onStop;
	private final Supplier<Point> pointer;
	private final Timer timer = new Timer(TICK_MS, e -> tick());
	private final Marker marker = new Marker();
	private Point anchor;
	// The scroll's fraction of a pixel, carried to the next tick so slow speeds still move.
	private double carry;

	PanelAutoscroll(JScrollPane pane, Consumer<MouseEvent> onStart, Runnable onStop)
	{
		this(pane, onStart, onStop, PanelAutoscroll::pointerOnScreen);
	}

	PanelAutoscroll(JScrollPane pane, Consumer<MouseEvent> onStart, Runnable onStop, Supplier<Point> pointer)
	{
		this.pane = pane;
		this.onStart = onStart;
		this.onStop = onStop;
		this.pointer = pointer;
	}

	/** Listens while the panel is showing; the middle button reaches the panel's cells, never the scroll pane. */
	void install()
	{
		Toolkit.getDefaultToolkit().removeAWTEventListener(this);
		Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
	}

	void uninstall()
	{
		Toolkit.getDefaultToolkit().removeAWTEventListener(this);
		stop();
	}

	@Override
	public void eventDispatched(AWTEvent event)
	{
		MouseEvent e = (MouseEvent) event;
		if (e.getButton() != MouseEvent.BUTTON2)
		{
			return;
		}
		if (e.getID() == MouseEvent.MOUSE_PRESSED && anchor == null && startsHere(e))
		{
			start(e);
		}
		else if (e.getID() == MouseEvent.MOUSE_RELEASED && anchor != null)
		{
			stop();
		}
	}

	boolean active()
	{
		return anchor != null;
	}

	/** Over the panel's own content, where it can scroll; a text field keeps its middle button (Linux pastes there). */
	private boolean startsHere(MouseEvent e)
	{
		return e.getComponent() != null && !(e.getComponent() instanceof JTextComponent)
			&& SwingUtilities.isDescendingFrom(e.getComponent(), pane.getViewport())
			&& pane.getVerticalScrollBar().getMaximum() - pane.getVerticalScrollBar().getVisibleAmount() > 0;
	}

	private void start(MouseEvent press)
	{
		anchor = press.getLocationOnScreen();
		carry = 0;
		onStart.accept(press);
		showMarker();
		timer.start();
	}

	private void stop()
	{
		if (anchor == null)
		{
			return;
		}
		anchor = null;
		timer.stop();
		hideMarker();
		onStop.run();
	}

	/** One step: the farther the pointer from the anchor, the faster, growing quicker than the distance does. */
	void tick()
	{
		Point at = pointer.get();
		if (anchor == null || at == null || !pane.isShowing())
		{
			stop();
			return;
		}
		int dy = at.y - anchor.y;
		int beyond = Math.abs(dy) - DEAD_ZONE;
		if (beyond <= 0)
		{
			return;
		}
		carry += Math.signum(dy) * Math.pow(beyond, 1.5) / 12;
		int step = (int) carry;
		carry -= step;
		JScrollBar bar = pane.getVerticalScrollBar();
		bar.setValue(bar.getValue() + step);
	}

	private void showMarker()
	{
		JRootPane root = SwingUtilities.getRootPane(pane);
		if (root == null)
		{
			return;
		}
		JLayeredPane layers = root.getLayeredPane();
		Point at = new Point(anchor);
		SwingUtilities.convertPointFromScreen(at, layers);
		marker.setBounds(at.x - MARKER_SIZE / 2, at.y - MARKER_SIZE / 2, MARKER_SIZE, MARKER_SIZE);
		layers.add(marker, JLayeredPane.DRAG_LAYER);
		marker.repaint();
	}

	private void hideMarker()
	{
		java.awt.Container layers = marker.getParent();
		if (layers != null)
		{
			layers.remove(marker);
			layers.repaint(marker.getX(), marker.getY(), marker.getWidth(), marker.getHeight());
		}
	}

	private static Point pointerOnScreen()
	{
		PointerInfo info = MouseInfo.getPointerInfo();
		return info != null ? info.getLocation() : null;
	}

	/**
	 * The anchor: a ring, and the up and down arrow itself in it, both drawn about the ring's exact centre (Dylan,
	 * 10/23; the two triangles and dot before sat half a pixel left of the ring's).
	 */
	private static final class Marker extends JComponent
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			double s = MARKER_SIZE;
			double c = s / 2;
			Ellipse2D ring = new Ellipse2D.Double(0.5, 0.5, s - 1, s - 1);
			g.setColor(new Color(20, 20, 20, 210));
			g.fill(ring);
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.draw(ring);
			// A head each way, 8 wide and 4.5 deep, on a shaft 2 wide: tip to tip 14 of the ring's 22.
			double tip = 4;
			double base = tip + 4.5;
			Path2D arrow = new Path2D.Double();
			arrow.moveTo(c, tip);
			arrow.lineTo(c + 4, base);
			arrow.lineTo(c + 1, base);
			arrow.lineTo(c + 1, s - base);
			arrow.lineTo(c + 4, s - base);
			arrow.lineTo(c, s - tip);
			arrow.lineTo(c - 4, s - base);
			arrow.lineTo(c - 1, s - base);
			arrow.lineTo(c - 1, base);
			arrow.lineTo(c - 4, base);
			arrow.closePath();
			g.fill(arrow);
			g.dispose();
		}
	}
}
