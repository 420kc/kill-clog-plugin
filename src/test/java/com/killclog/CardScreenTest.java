package com.killclog;

import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class CardScreenTest
{
	// A desk like Dylan's, plus a monitor left of the primary: Windows puts it at negative x.
	private static final Rectangle LEFT = new Rectangle(-1280, 0, 1280, 1024);
	private static final Rectangle PRIMARY = new Rectangle(0, 0, 1920, 1080);
	private static final Rectangle ULTRAWIDE = new Rectangle(1920, 0, 3440, 1440);
	private static final List<Rectangle> DESK = Arrays.asList(LEFT, PRIMARY, ULTRAWIDE);

	@Test
	public void aCardOpensOnTheMonitorUnderItsAnchor()
	{
		assertEquals(ULTRAWIDE, TooltipController.screenAt(new Point(2394, 400), DESK));
		assertEquals(PRIMARY, TooltipController.screenAt(new Point(100, 100), DESK));
		assertEquals(LEFT, TooltipController.screenAt(new Point(-5, 1000), DESK));
	}

	@Test
	public void aSharedEdgeBelongsToTheMonitorThatStartsThere()
	{
		assertEquals(ULTRAWIDE, TooltipController.screenAt(new Point(1920, 10), DESK));
		assertEquals(PRIMARY, TooltipController.screenAt(new Point(1919, 10), DESK));
	}

	@Test
	public void aCardKeepsClearOfTheTaskbarMenuBarAndDock()
	{
		// Windows: a 40 px taskbar along the bottom.
		assertEquals(new Rectangle(0, 0, 1920, 1040), TooltipController.usable(PRIMARY, new Insets(0, 0, 40, 0)));
		// A Mac: the menu bar on top, the Dock on the left, on a monitor right of the main one.
		assertEquals(new Rectangle(1990, 25, 3370, 1415),
			TooltipController.usable(ULTRAWIDE, new Insets(25, 70, 0, 0)));
		// Nothing along the edges: the whole monitor.
		assertEquals(LEFT, TooltipController.usable(LEFT, new Insets(0, 0, 0, 0)));
	}

	@Test
	public void noMonitorHoldingTheAnchorLeavesItToTheWindowsOwn()
	{
		// Below the primary's 1080 but beside the ultrawide's taller bottom: in no monitor.
		assertNull(TooltipController.screenAt(new Point(100, 1200), DESK));
	}
}
