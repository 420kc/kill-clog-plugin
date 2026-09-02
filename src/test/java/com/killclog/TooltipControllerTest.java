package com.killclog;

import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class TooltipControllerTest
{
	private TooltipMode mode = TooltipMode.CLICK;
	private final KillClogConfig config = new KillClogConfig()
	{
		@Override
		public TooltipMode tooltipMode()
		{
			return mode;
		}
	};

	@Test
	public void modeLifecycleNeverChangesGlobalTooltipDelays()
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		int originalInitial = manager.getInitialDelay();
		int originalDismiss = manager.getDismissDelay();
		try
		{
			manager.setInitialDelay(321);
			manager.setDismissDelay(4321);
			TooltipController controller = new TooltipController(config);

			controller.activate(new JPanel());
			assertEquals(321, manager.getInitialDelay());
			assertEquals(4321, manager.getDismissDelay());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			mode = TooltipMode.CLICK;
			controller.onTooltipModeChanged();
			controller.deactivate();

			assertEquals(321, manager.getInitialDelay());
			assertEquals(4321, manager.getDismissDelay());
		}
		finally
		{
			manager.setInitialDelay(originalInitial);
			manager.setDismissDelay(originalDismiss);
		}
	}

	@Test
	public void clickModeUnregistersOnlyTrackedComponents() throws Exception
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config);
		JLabel tracked = new JLabel();
		JLabel otherPlugin = new JLabel();
		tracked.setToolTipText("Kill Clog");
		otherPlugin.setToolTipText("Other plugin");

		try
		{
			controller.trackTooltipComponent(tracked);
			flushEdt();
			assertFalse(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));
			assertEquals("Kill Clog", tracked.getToolTipText());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			assertTrue(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));

			mode = TooltipMode.CLICK;
			controller.onTooltipModeChanged();
			tracked.setToolTipText("Updated Kill Clog");
			flushEdt();
			assertFalse(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));
		}
		finally
		{
			tracked.setToolTipText(null);
			otherPlugin.setToolTipText(null);
		}
	}

	@Test
	public void cellPressPinsTooltipInBothModes()
	{
		AtomicInteger pins = new AtomicInteger();
		AtomicInteger dismissedHoverPreviews = new AtomicInteger();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			void dismissHoverPreview(MouseEvent event)
			{
				dismissedHoverPreviews.incrementAndGet();
			}

			@Override
			void showPinnedTooltip(JComponent source, JPanel cell)
			{
				pins.incrementAndGet();
			}
		};
		JPanel cell = new JPanel();
		JLabel label = new JLabel();
		label.setToolTipText("Kill Clog");

		try
		{
			controller.addCellHoverEffect(cell, label);
			pressWithoutToolTipManager(label);
			assertEquals(1, pins.get());
			assertEquals(0, dismissedHoverPreviews.get());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			pressWithoutToolTipManager(label);
			assertEquals(2, pins.get());
			assertEquals(1, dismissedHoverPreviews.get());
		}
		finally
		{
			label.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void pinnedSourceTooltipIsSuppressedAndRestored() throws Exception
	{
		mode = TooltipMode.HOVER;
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config);
		JLabel source = new JLabel();
		source.setToolTipText("Kill Clog");
		controller.trackTooltipComponent(source);

		try
		{
			controller.suppressPinnedSourceTooltip(source, source.getToolTipText());
			flushEdt();
			assertNull(source.getToolTipText());
			assertFalse(isRegistered(source, manager));

			controller.restorePinnedSourceTooltip();
			flushEdt();
			assertEquals("Kill Clog", source.getToolTipText());
			assertTrue(isRegistered(source, manager));
		}
		finally
		{
			source.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void tooltipTextChangesAreFencedSynchronouslyWhilePinned() throws Exception
	{
		mode = TooltipMode.HOVER;
		AtomicBoolean pinned = new AtomicBoolean();
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			boolean hasPinnedTooltip()
			{
				return pinned.get();
			}
		};
		JLabel summary = new JLabel();
		summary.setToolTipText("Summary");

		try
		{
			controller.trackTooltipComponent(summary);
			assertTrue(isRegistered(summary, manager));
			controller.setTooltipText(summary, null);
			assertFalse(isRegistered(summary, manager));

			pinned.set(true);
			SwingUtilities.invokeAndWait(() ->
			{
				controller.setTooltipText(summary, "Updated summary");
				assertFalse(isRegistered(summary, manager));
			});

			pinned.set(false);
			SwingUtilities.invokeAndWait(() ->
			{
				controller.setTooltipText(summary, "Restored summary");
				assertTrue(isRegistered(summary, manager));
			});
		}
		finally
		{
			summary.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void enteringPinnedTooltipClearsResidualHoverPreview()
	{
		AtomicInteger dismissedHoverPreviews = new AtomicInteger();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			void dismissHoverPreview(MouseEvent event)
			{
				dismissedHoverPreviews.incrementAndGet();
			}
		};
		JToolTip tip = new JToolTip();
		controller.guardPinnedTooltip(tip);
		MouseEvent event = new MouseEvent(tip, MouseEvent.MOUSE_ENTERED,
			System.currentTimeMillis(), 0, 1, 1, 0, false);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseEntered(event);
		}

		assertEquals(1, dismissedHoverPreviews.get());
	}

	private static boolean isRegistered(JLabel label, ToolTipManager manager)
	{
		for (MouseListener listener : label.getMouseListeners())
		{
			if (listener == manager)
			{
				return true;
			}
		}
		return false;
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static void pressWithoutToolTipManager(JLabel label)
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		MouseEvent event = new MouseEvent(label, MouseEvent.MOUSE_PRESSED,
			System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1);
		for (MouseListener listener : label.getMouseListeners())
		{
			if (listener != manager)
			{
				listener.mousePressed(event);
			}
		}
	}
}
