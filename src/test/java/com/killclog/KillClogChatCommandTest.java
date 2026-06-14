package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogChatCommandTest
{
	@Test
	public void testCommandHeaderIncludesBossKc()
	{
		assertEquals("Vorkath: 420 kc, 2/14",
			KillClogChatCommand.buildCommandHeader("Vorkath", 420, 2, 14, false));
		assertEquals("Vorkath: 420 kc, 12/14 missing",
			KillClogChatCommand.buildCommandHeader("Vorkath", 420, 12, 14, true));
	}

	@Test
	public void testCommandHeaderOmitsUnknownBossKc()
	{
		assertEquals("Vorkath: 2/14",
			KillClogChatCommand.buildCommandHeader("Vorkath", -1, 2, 14, false));
		assertEquals("Vorkath: 12/14 missing",
			KillClogChatCommand.buildCommandHeader("Vorkath", -1, 12, 14, true));
	}

	@Test
	public void testCompleteHeaderIncludesBossKcWhenKnown()
	{
		assertEquals("Vorkath: 420 kc, complete",
			KillClogChatCommand.buildCompleteHeader("Vorkath", 420));
		assertEquals("Vorkath: complete",
			KillClogChatCommand.buildCompleteHeader("Vorkath", -1));
	}
}
