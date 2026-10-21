package com.killclog;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Strips in the scrolled content that catch under the panel's anchored top once their spot scrolls past it, and drop
 * back into it as it comes back into view (Dylan, 2026-10-20): the boss list's search, and the activities tray's toggle
 * while the tray is closed. A strip leaves the content as it joins the top, so the rows under it never move; it pins
 * and drops at the same scroll position, so it can't flicker.
 */
final class StickyStrips
{
	private final JScrollPane pane;
	private final JPanel top;
	private final List<Strip> strips = new ArrayList<>();
	private boolean updating;

	private static final class Strip
	{
		private final JComponent strip;
		// Where the strip goes back to: a line of no height just above it in its own column.
		private final JComponent spot = new JPanel();
		private final BooleanSupplier allowed;
		private final float alignment;
		private boolean pinned;

		private Strip(JComponent strip, BooleanSupplier allowed)
		{
			this.strip = strip;
			this.allowed = allowed;
			alignment = strip.getAlignmentX();
			spot.setOpaque(false);
			spot.setPreferredSize(new Dimension(0, 0));
			spot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
			spot.setAlignmentX(alignment);
		}
	}

	StickyStrips(JScrollPane pane, JPanel top)
	{
		this.pane = pane;
		this.top = top;
		pane.getViewport().addChangeListener(e -> update());
	}

	/** A strip already in its column (a vertical box); it may pin only while allowed. Pinned strips stack in this order. */
	void add(JComponent strip, BooleanSupplier allowed)
	{
		Strip s = new Strip(strip, allowed);
		Container column = strip.getParent();
		column.add(s.spot, indexIn(column, strip));
		strips.add(s);
	}

	/** Pins each strip whose spot has scrolled above the view, and drops back each whose spot shows again. */
	void update()
	{
		if (updating)
		{
			return;
		}
		updating = true;
		try
		{
			int view = pane.getViewport().getViewPosition().y;
			boolean moved = false;
			for (Strip s : strips)
			{
				boolean pin = s.allowed.getAsBoolean() && inContent(s) && spotY(s) < view;
				if (pin != s.pinned)
				{
					move(s, pin);
					moved = true;
				}
			}
			if (moved)
			{
				top.revalidate();
				top.repaint();
			}
		}
		finally
		{
			updating = false;
		}
	}

	boolean pinned(JComponent strip)
	{
		return strips.stream().anyMatch(s -> s.strip == strip && s.pinned);
	}

	private void move(Strip s, boolean pin)
	{
		Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		boolean focused = focus != null && SwingUtilities.isDescendingFrom(focus, s.strip);
		Container from = s.strip.getParent();
		if (from != null)
		{
			from.remove(s.strip);
			from.revalidate();
			from.repaint();
		}
		s.pinned = pin;
		if (pin)
		{
			// The top's own lines sit left-aligned; the strip takes that while it is there.
			s.strip.setAlignmentX(Component.LEFT_ALIGNMENT);
			top.add(s.strip, pinnedIndex(s));
		}
		else
		{
			s.strip.setAlignmentX(s.alignment);
			Container column = s.spot.getParent();
			column.add(s.strip, indexIn(column, s.spot) + 1);
			column.revalidate();
		}
		if (focused)
		{
			focus.requestFocusInWindow();
		}
	}

	/** After the top's own lines and the strips pinned before this one. */
	private int pinnedIndex(Strip s)
	{
		int index = top.getComponentCount();
		for (int i = strips.indexOf(s) + 1; i < strips.size(); i++)
		{
			if (strips.get(i).pinned && strips.get(i).strip.getParent() == top)
			{
				index = Math.min(index, indexIn(top, strips.get(i).strip));
			}
		}
		return index;
	}

	/** The spot is in what scrolls now: the boss list's isn't while the grid shows. */
	private boolean inContent(Strip s)
	{
		Component content = pane.getViewport().getView();
		return content != null && SwingUtilities.isDescendingFrom(s.spot, content);
	}

	private int spotY(Strip s)
	{
		return SwingUtilities.convertPoint(s.spot.getParent(), s.spot.getLocation(), pane.getViewport().getView()).y;
	}

	private static int indexIn(Container column, Component child)
	{
		for (int i = 0; i < column.getComponentCount(); i++)
		{
			if (column.getComponent(i) == child)
			{
				return i;
			}
		}
		return column.getComponentCount();
	}
}
