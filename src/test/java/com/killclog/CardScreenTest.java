package com.killclog;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JPanel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import org.junit.Test;

public class CardScreenTest
{
	@Test
	public void aCardOpensOnTheMonitorUnderItsAnchorWhateverTheWindowRecorded()
	{
		assumeFalse(GraphicsEnvironment.isHeadless());
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			Rectangle bounds = device.getDefaultConfiguration().getBounds();
			Point middle = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
			// The fallback panel never shows, so its own record can't be what picks the monitor.
			assertEquals(bounds, TooltipController.screenAt(middle, new JPanel()));
		}
	}
}
