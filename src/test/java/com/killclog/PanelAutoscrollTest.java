package com.killclog;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Hold the middle button and move: the panel scrolls, faster the farther; let go and it stops. No real mouse. */
public class PanelAutoscrollTest
{
	private final AtomicReference<Point> pointer = new AtomicReference<>(new Point(100, 100));
	private final List<String> calls = new ArrayList<>();

	@Test
	public void holdingTheMiddleButtonScrollsFasterTheFartherAndLettingGoStops() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JLabel cell = new JLabel("cell");
			JScrollPane pane = pane(3000, cell);
			PanelAutoscroll scroll = autoscroll(pane);
			int middle = pane.getVerticalScrollBar().getValue();

			scroll.eventDispatched(middle(cell, MouseEvent.MOUSE_PRESSED));
			assertTrue(scroll.active());
			assertEquals(List.of("start"), calls);

			// Resting near the anchor moves nothing.
			pointer.set(new Point(100, 106));
			scroll.tick();
			assertEquals(middle, pane.getVerticalScrollBar().getValue());

			// Below it scrolls down, and farther away scrolls more each step.
			pointer.set(new Point(100, 130));
			scroll.tick();
			int near = pane.getVerticalScrollBar().getValue() - middle;
			pointer.set(new Point(100, 200));
			int before = pane.getVerticalScrollBar().getValue();
			scroll.tick();
			int far = pane.getVerticalScrollBar().getValue() - before;
			assertTrue(near > 0);
			assertTrue(far > near * 5);

			// Above it scrolls up, and a long flick reaches the top.
			pointer.set(new Point(100, -300));
			for (int i = 0; i < 20; i++)
			{
				scroll.tick();
			}
			assertEquals(0, pane.getVerticalScrollBar().getValue());

			scroll.eventDispatched(middle(cell, MouseEvent.MOUSE_RELEASED));
			assertFalse(scroll.active());
			assertEquals(List.of("start", "stop"), calls);
		});
	}

	@Test
	public void itStartsOnlyWhereThePanelCanScrollAndNeverInATextField() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JTextField search = new JTextField();
			PanelAutoscroll inText = autoscroll(pane(3000, search));
			inText.eventDispatched(middle(search, MouseEvent.MOUSE_PRESSED));
			assertFalse("a text field keeps its middle button", inText.active());

			JLabel cell = new JLabel("cell");
			PanelAutoscroll tooShort = autoscroll(pane(100, cell));
			tooShort.eventDispatched(middle(cell, MouseEvent.MOUSE_PRESSED));
			assertFalse("nothing to scroll", tooShort.active());

			JLabel elsewhere = new JLabel("not in the panel");
			PanelAutoscroll outside = autoscroll(pane(3000, new JLabel("cell")));
			outside.eventDispatched(middle(elsewhere, MouseEvent.MOUSE_PRESSED));
			assertFalse(outside.active());

			JLabel left = new JLabel("cell");
			PanelAutoscroll leftButton = autoscroll(pane(3000, left));
			leftButton.eventDispatched(new MouseEvent(left, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 100, 100, 1, false,
				MouseEvent.BUTTON1));
			assertFalse(leftButton.active());
			assertTrue(calls.isEmpty());
		});
	}

	private PanelAutoscroll autoscroll(JScrollPane pane)
	{
		return new PanelAutoscroll(pane, press -> calls.add("start"), () -> calls.add("stop"), pointer::get);
	}

	/** A 200-pixel window on content of the given height, scrolled to its middle, with the component inside. */
	private static JScrollPane pane(int contentHeight, JComponent inside)
	{
		JPanel content = new JPanel(null);
		content.setPreferredSize(new Dimension(200, contentHeight));
		inside.setBounds(0, 0, 100, 20);
		content.add(inside);
		JScrollPane pane = new JScrollPane(content)
		{
			@Override
			public boolean isShowing()
			{
				return true;
			}
		};
		pane.setSize(220, 200);
		pane.doLayout();
		pane.getViewport().doLayout();
		pane.getVerticalScrollBar().setValue(Math.max(0, contentHeight / 2 - 100));
		return pane;
	}

	private static MouseEvent middle(JComponent on, int id)
	{
		return new MouseEvent(on, id, 0L, 0, 5, 5, 100, 100, 1, false, MouseEvent.BUTTON2);
	}
}
