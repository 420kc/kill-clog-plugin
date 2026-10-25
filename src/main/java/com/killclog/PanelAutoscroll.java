package com.killclog;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.BasicStroke;
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

	/**
	 * Over the panel's own content, where it can scroll; a text field keeps its middle button (Linux pastes there).
	 * The press reaches the nearest component listening for the mouse, the client's window itself over a plain label
	 * or a gap (10/25: Hive Ops never scrolled), so what counts is what's drawn under the pointer.
	 */
	private boolean startsHere(MouseEvent e)
	{
		Component under = e.getComponent() == null ? null
			: SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
		return under != null && !(under instanceof JTextComponent)
			&& SwingUtilities.isDescendingFrom(under, pane.getViewport())
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
		marker.head(beyond <= 0 ? 0 : (int) Math.signum(dy));
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
		marker.head(0);
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
	 * The anchor: a small dot, and a caret the way it scrolls, black in a white outline so it reads on any card
	 * (Dylan, 10/24: no ring). Held still, the dot alone.
	 */
	private static final class Marker extends JComponent
	{
		// -1 up, 1 down, 0 held still.
		private int heading;

		void head(int next)
		{
			if (next != heading)
			{
				heading = next;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			double c = MARKER_SIZE / 2.0;
			Path2D caret = new Path2D.Double();
			caret.moveTo(c - 3.5, c + heading * 5.5);
			caret.lineTo(c, c + heading * 8.5);
			caret.lineTo(c + 3.5, c + heading * 5.5);
			// White under, black over: the outline is the white the black leaves showing.
			for (int pass = 0; pass < 2; pass++)
			{
				g.setColor(pass == 0 ? Color.WHITE : Color.BLACK);
				double r = pass == 0 ? 3.5 : 2.5;
				g.fill(new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));
				if (heading != 0)
				{
					g.setStroke(new BasicStroke(pass == 0 ? 4f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(caret);
				}
			}
			g.dispose();
		}
	}
}
