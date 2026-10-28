package com.killclog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MenuLabelTest
{
	@Test
	public void theRightClickMenuReadsKillClogAndLookup()
	{
		assertEquals("Kill Clog", MenuLabel.KILL_CLOG.toString());
		assertEquals("Lookup", MenuLabel.LOOKUP.toString());
		assertEquals(MenuLabel.KILL_CLOG.getLabel(), MenuLabel.KILL_CLOG.toString());
	}
}
