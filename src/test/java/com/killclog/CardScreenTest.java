package com.killclog;

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
	public void noMonitorHoldingTheAnchorLeavesItToTheWindowsOwn()
	{
		// Below the primary's 1080 but beside the ultrawide's taller bottom: in no monitor.
		assertNull(TooltipController.screenAt(new Point(100, 1200), DESK));
	}
}
