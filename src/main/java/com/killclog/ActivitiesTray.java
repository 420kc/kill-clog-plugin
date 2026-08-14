/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Slide-down activities tray: expandable content panel with eased animation.
 * Self-contained widget; the panel hands it the content + an onToggle hook,
 * the tray owns expanded state, the clip's preferred-size override, the
 * separator click target, and the Timer-driven slide.
 */
package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;

/**
 * Expandable tray with an eased height animation. Construction wires a content
 * panel into a clip whose preferred-size depends on the tray's expanded state;
 * {@link #getSeparator()} is the click target between this tray and whatever
 * lives below it. Clip + separator are exposed as Components so the panel can
 * place them inside its existing GridBag layout.
 */
public class ActivitiesTray
{
	private static final long SLIDE_MS = 150;
	private static final int SLIDE_TICK_MS = 12;

	private final JPanel content;
	private final JPanel clip;
	private final JPanel separator;
	private final Runnable onToggle;
	private boolean expanded;
	private Timer slideTimer;

	/**
	 * @param content the JPanel to show / hide inside the tray
	 * @param initiallyExpanded whether the tray starts expanded
	 * @param onToggle invoked after each toggle with the new expanded state (panel persists to config)
	 */
	public ActivitiesTray(JPanel content, boolean initiallyExpanded, OnToggle onToggle)
	{
		this.content = content;
		this.expanded = initiallyExpanded;
		this.onToggle = () -> onToggle.toggled(expanded);

		this.clip = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				int h;
				if (slideTimer != null && slideTimer.isRunning())
				{
					h = super.getPreferredSize().height;
				}
				else if (expanded)
				{
					h = content.getPreferredSize().height;
				}
				else
				{
					h = 0;
				}
				return new Dimension(content.getPreferredSize().width, h);
			}
		};
		clip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clip.add(content, BorderLayout.NORTH);
		clip.setPreferredSize(new Dimension(0, expanded ? content.getPreferredSize().height : 0));
		clip.setVisible(expanded);

		// The separator is the tray's only toggle (the hamburger switches boss
		// views now), so it stays visible in BOTH states: hidden-when-collapsed
		// would make one collapse unrecoverable for the whole session.
		this.separator = new JPanel();
		separator.setBackground(ColorScheme.DARK_GRAY_COLOR);
		separator.setPreferredSize(new Dimension(0, 7));
		Color sepNormal = ColorScheme.DARK_GRAY_COLOR;
		Color sepHover = new Color(46, 46, 46);
		separator.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggle();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				separator.setBackground(sepHover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				separator.setBackground(sepNormal);
			}
		});
	}

	public JPanel getClip()
	{
		return clip;
	}

	public JPanel getSeparator()
	{
		return separator;
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	/** Run the slide animation, flip {@link #expanded}, and notify the onToggle hook. */
	public void toggle()
	{
		if (slideTimer != null && slideTimer.isRunning())
		{
			slideTimer.stop();
		}

		int startHeight = clip.getPreferredSize().height;
		expanded = !expanded;
		onToggle.run();

		int targetHeight = expanded ? content.getPreferredSize().height : 0;
		if (expanded)
		{
			clip.setVisible(true);
		}

		long startTime = System.currentTimeMillis();
		slideTimer = new Timer(SLIDE_TICK_MS, null);
		slideTimer.addActionListener(e ->
		{
			long elapsed = System.currentTimeMillis() - startTime;
			float progress = Math.min(1f, (float) elapsed / SLIDE_MS);
			// Ease-out: 1 - (1-t)^2
			float eased = 1f - (1f - progress) * (1f - progress);
			int height = startHeight + Math.round((targetHeight - startHeight) * eased);
			clip.setPreferredSize(new Dimension(0, height));
			clip.getParent().revalidate();

			if (progress >= 1f)
			{
				slideTimer.stop();
				if (!expanded)
				{
					clip.setVisible(false);
				}
			}
		});
		slideTimer.start();
	}

	/** Toggle callback the panel implements (typically: persist the new state to config). */
	public interface OnToggle
	{
		void toggled(boolean expanded);
	}
}
