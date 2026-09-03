package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogChatCommandTest
{
	@Test
	public void testKcReceivedTextKeepsExactCounts()
	{
		assertEquals("Elder venator fang received on 421 kc",
			KillClogChatCommand.kcReceivedText("Elder venator fang", 421));
		// Provenance never rounds: no 12k/1m shorthand above 9,999.
		assertEquals("Scurry received on 12,345 kc",
			KillClogChatCommand.kcReceivedText("Scurry", 12345));
	}

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
	public void testObtainedItemIconShowsExactDuplicateQuantity()
	{
		assertEquals("<img=42>", KillClogChatCommand.formatItemIcon(42, 1));
		assertEquals("<img=42>x3", KillClogChatCommand.formatItemIcon(42, 3));
		assertEquals("<img=42>x12345", KillClogChatCommand.formatItemIcon(42, 12345));
	}

	@Test
	public void testLogCompatibilityMatchesOnlyWholeCommandsInPlayerChat()
	{
		assertTrue(KillClogChatCommand.isCompatibleLogCommand(
			ChatMessageType.PUBLICCHAT, "!log Vorkath"));
		assertTrue(KillClogChatCommand.isCompatibleLogCommand(
			ChatMessageType.CLAN_CHAT, "!LOG missing Vorkath"));
		assertFalse(KillClogChatCommand.isCompatibleLogCommand(
			ChatMessageType.GAMEMESSAGE, "!log Vorkath"));
		assertFalse(KillClogChatCommand.isCompatibleLogCommand(
			ChatMessageType.PUBLICCHAT, "!logger Vorkath"));
		assertFalse(KillClogChatCommand.isCompatibleLogCommand(
			ChatMessageType.PUBLICCHAT, "hello !log Vorkath"));
	}

	@Test
	public void testLogCompatibilityDelegatesToKillClogCommands()
	{
		assertEquals("!kclog Vorkath", KillClogChatCommand.toKillClogCommand("!log Vorkath"));
		assertEquals("!missing Vorkath",
			KillClogChatCommand.toKillClogCommand("!log missing Vorkath"));
		assertEquals("!missing Vorkath",
			KillClogChatCommand.toKillClogCommand("!LOG MiSsInG   Vorkath"));
		assertNull(KillClogChatCommand.toKillClogCommand("!log"));
		assertNull(KillClogChatCommand.toKillClogCommand("!log missing"));
	}

	@Test
	public void testTotalsFallBackToCatalogForUnknownCategories()
	{
		// A cache bulk-synced before a page existed has no entry for it, so
		// the command used to answer "no clog items found" for real content.
		// The in-game catalog fills the total; captured entries always win.
		Map<String, List<Integer>> catalog = new HashMap<>();
		catalog.put("maggotking", Arrays.asList(1, 2, 3));

		assertEquals(Arrays.asList(1, 2, 3), KillClogChatCommand.totalsWithCatalogFallback(
			Collections.emptyList(), catalog, "maggotking"));
		assertEquals(Arrays.asList(9), KillClogChatCommand.totalsWithCatalogFallback(
			Arrays.asList(9), catalog, "maggotking"));
		// No parsed catalog or a category it also lacks keeps the empty list,
		// which still renders "no clog items found".
		assertEquals(Collections.emptyList(), KillClogChatCommand.totalsWithCatalogFallback(
			Collections.emptyList(), null, "maggotking"));
		assertEquals(Collections.emptyList(), KillClogChatCommand.totalsWithCatalogFallback(
			Collections.emptyList(), catalog, "vorkath"));
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
