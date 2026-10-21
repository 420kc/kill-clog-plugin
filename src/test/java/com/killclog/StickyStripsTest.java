package com.killclog;

import java.awt.Dimension;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** A strip pins under the top once its spot scrolls past, the rows under it holding still, and drops back on the way up. */
public class StickyStripsTest
{
	@Test
	public void aStripPinsAsItsSpotScrollsPastAndDropsBackWithTheRowsHoldingStill() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel content = column();
			for (int i = 0; i < 5; i++)
			{
				content.add(row(40));
			}
			JPanel strip = column();
			JTextField field = new JTextField();
			strip.add(field);
			strip.add(row(20));
			content.add(strip);
			JLabel below = row(40);
			content.add(below);
			for (int i = 0; i < 40; i++)
			{
				content.add(row(40));
			}
			JPanel top = column();
			top.add(row(30));
			JScrollPane pane = new JScrollPane(content);
			pane.setColumnHeaderView(top);
			pane.setSize(200, 300);
			AtomicBoolean allowed = new AtomicBoolean(true);
			StickyStrips sticky = new StickyStrips(pane, top);
			sticky.add(strip, allowed::get);
			layout(pane);

			// At the top nothing is pinned; the strip's spot is 200 px down the content.
			assertFalse(sticky.pinned(strip));
			scroll(pane, 150);
			assertFalse("its spot still shows", sticky.pinned(strip));
			int belowOnScreen = onScreen(pane, below);

			scroll(pane, 201);
			assertTrue(sticky.pinned(strip));
			assertSame(top, strip.getParent());
			layout(pane);
			assertEquals("the row under it did not move", belowOnScreen - 51, onScreen(pane, below));

			// Back up past its spot it drops back into its place, just above that row.
			scroll(pane, 200);
			assertFalse(sticky.pinned(strip));
			assertSame(content, strip.getParent());
			assertEquals(content.getComponentZOrder(below) - 1, content.getComponentZOrder(strip));

			// A strip that may not pin stays where it is however far the panel scrolls.
			allowed.set(false);
			scroll(pane, 900);
			assertFalse(sticky.pinned(strip));
		});
	}

	private static void scroll(JScrollPane pane, int y)
	{
		pane.getViewport().setViewPosition(new Point(0, y));
		layout(pane);
	}

	/** Lays the whole tree out now: with no window there is no validate pass to do it. */
	private static void layout(java.awt.Container container)
	{
		container.doLayout();
		for (java.awt.Component child : container.getComponents())
		{
			if (child instanceof java.awt.Container)
			{
				layout((java.awt.Container) child);
			}
		}
	}

	/** A component's top on the screen, as the pane's own coordinates count it. */
	private static int onScreen(JScrollPane pane, JLabel label)
	{
		return SwingUtilities.convertPoint(label.getParent(), label.getLocation(), pane).y;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	private static JLabel row(int height)
	{
		JLabel label = new JLabel("row");
		label.setPreferredSize(new Dimension(180, height));
		label.setMinimumSize(new Dimension(0, height));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return label;
	}
}
