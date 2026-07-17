package com.killclog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

	@Test
	public void testChatCanonMatchesHiscoreBossNames()
	{
		// The alias table carries its own copy of the boss canon. This pins it
		// to the hiscore list in both directions so the duplicate can never
		// drift (2026-07-16 pre-Mad-Angel hardening review).
		Set<String> csvNames = new HashSet<>(Arrays.asList(HiscoreService.bossNames()));
		for (String name : csvNames)
		{
			assertEquals("Hiscore boss missing from chat canon: " + name,
				name, KillClogChatCommand.aliases().get(KillClogChatCommand.normalize(name)));
		}
		for (String canonical : KillClogChatCommand.aliases().values())
		{
			assertTrue("Chat canon points at a boss the hiscores do not know: " + canonical,
				csvNames.contains(canonical));
		}
	}
}
