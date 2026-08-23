package com.killclog;

import java.awt.event.MouseListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
}
