package com.killclog;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LiveClogSyncTest
{
	private static final int FANG = 33634;
	private static final int CLUE_ITEM = 23285;
	private static final int PET = 30000;
	private static final int DEVIL_ITEM = 30001;
	private static final int UNPAGED = 30002;
	private static final int GAUNTLET_ITEM = 30003;

	@Test
	public void theGameCountersWaitForASettledWorld()
	{
		Client client = mock(Client.class);
		Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);
		// Right after a hop the counters may still be the last world's.
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(900);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)).thenReturn(1600);
		ClogIndex index = mock(ClogIndex.class);
		when(index.ensureParsed(any(), any())).thenReturn(true);
		when(index.itemIdsForName(any())).thenReturn(List.of(20997));
		LocalClogCache cache = mock(LocalClogCache.class);
		when(cache.serving("Tester")).thenReturn(true);
		when(cache.hasDataFor("Tester")).thenReturn(true);
		LiveClogSync sync = new LiveClogSync();

		sync.handleUnlock("Twisted bow", 12, 1600, client, null, index, cache, mock(KillClogChatNotifier.class), name ->
		{
		}, false);
		verify(cache).updateTotalsUpward("Tester", 12, 1600);
		sync.handleUnlock("Twisted bow", 12, 1600, client, null, index, cache, mock(KillClogChatNotifier.class), name ->
		{
		}, true);
		verify(cache).updateTotalsUpward("Tester", 900, 1600);
	}

	// Dylan 11/14: "Your Maggot King kill count is: 603." at 8:56:57, the Elder venator fang's "New item added" at
	// 8:57:00, past the old five ticks, so the fang was saved with no kill count and !kc had nothing to say.
	@Test
	public void aBossDropSecondsAfterItsKillCarriesTheKill()
	{
		LocalClogCache cache = unlockAfter("Maggot King", 6, FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603), eq("Maggot King"));
	}

	// Maggot King's loot comes when its corpse is searched, as long after the kill as the player likes (up to 259 s in
	// his logs): an item from a boss's own page that the game credited to nobody takes the boss's latest kill, no clock.
	@Test
	public void aDropFromItsOwnPageTakesTheBossesLatestKillHoweverLongAfter()
	{
		verify(unlockAfter("Maggot King", 50_000, FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// Dylan 11/15: the game credits each loot to the NPC that gave it as you receive it (11/14's fang: credited to Maggot
	// King the second it unlocked); the item takes that NPC's latest kill, however long the loot waited.
	@Test
	public void lootTheGameCreditsTakesItsNpcsLatestKillHoweverLongItWaited()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.rememberKill(new ClogUnlockParser.KillContext("Zulrah", 50), 1100);
		sync.credit("Maggot King", List.of("Elder venator fang"), 60_000);
		// An item no page names yet, so only the credit can give it Maggot King's kill, not Zulrah's after it.
		verify(unlock(sync, clientAt(60_000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// A plain smoke devil drops the Occult necklace too, and it sits on Thermy's page: credited to the smoke devil, which
	// has no kill count, it takes none, Thermy's kill a moment before notwithstanding.
	@Test
	public void lootCreditedToAPlainMonsterTakesNoKillThoughItsPageIsABosses()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Thermonuclear smoke devil", 603), 1000);
		sync.credit("Smoke devil", List.of("Elder venator fang"), 1100);
		verify(unlock(sync, clientAt(1110), DEVIL_ITEM)).mergeObtainedItem(eq("Tester"), eq(DEVIL_ITEM), any(), any(),
			eq(0), isNull());
	}

	// Loot credited to an NPC named apart from its kill message (Grotesque Guardians fight Dusk) lands with that kill.
	@Test
	public void lootCreditedWithAKillNamedApartTakesIt()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Grotesque Guardians", 603), 1000);
		sync.credit("Dusk", List.of("Elder venator fang"), 1002);
		verify(unlock(sync, clientAt(9000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Grotesque Guardians"));
	}

	// A page's name and its kill message's can differ by a leading "the": The Gauntlet's page, "Your Gauntlet completion
	// count".
	@Test
	public void aPageAndItsKillMatchWithOrWithoutTheir()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Gauntlet", 12), 1000);
		verify(unlock(sync, clientAt(5000), GAUNTLET_ITEM)).mergeObtainedItem(eq("Tester"), eq(GAUNTLET_ITEM), any(),
			any(), eq(12), eq("Gauntlet"));
	}

	// An unlock credited to nobody and off any boss's page with a kill (a casket after a kill) takes only the count
	// message it arrived with: within five ticks, as a chest's reward does; past them, bare.
	@Test
	public void anItemFromAnotherPageTakesOnlyTheKillItArrivedWith()
	{
		verify(unlockAfter("Maggot King", 6, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(0), isNull());
		verify(unlockAfter("Maggot King", 5, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(603), eq("Maggot King"));
	}

	// The game's message and its page can differ in case: "Thermonuclear smoke devil" against its page's name.
	@Test
	public void theBossAndItsPageMatchWhateverTheirCase()
	{
		verify(unlockAfter("Thermonuclear smoke devil", 20, DEVIL_ITEM)).mergeObtainedItem(eq("Tester"), eq(DEVIL_ITEM),
			any(), any(), eq(603), eq("Thermonuclear smoke devil"));
	}

	// A pet sits on All Pets as well as its boss's page: one of its pages being the boss's is enough.
	@Test
	public void anItemOnSeveralPagesTakesTheKillWhenOneIsTheBossPage()
	{
		verify(unlockAfter("Maggot King", 20, PET)).mergeObtainedItem(eq("Tester"), eq(PET), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// Unchanged by the longer window: a tick count behind the kill's is inside the five ticks, as it always was.
	@Test
	public void aTickCountBehindTheKillsStillTakesIt()
	{
		verify(unlockAfter("Maggot King", -3, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(603), eq("Maggot King"));
	}

	// An item the index knows no page for (a page list not read yet) stays bare past five ticks, without failing.
	@Test
	public void anItemWithNoPagesKnownStaysBare()
	{
		verify(unlockAfter("Maggot King", 20, UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(),
			eq(0), isNull());
	}

	@Test
	public void anUnlockWithNoKillBeforeItHasNone()
	{
		LiveClogSync sync = new LiveClogSync();
		LocalClogCache cache = unlock(sync, mock(Client.class), FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	// The plugin forgets its kills and credits at the login screen and on shutdown (KillClogPlugin), so another
	// account's unlock never takes them.
	@Test
	public void aResetForgetsKillsAndCredits()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.credit("Maggot King", List.of("Elder venator fang"), 1000);
		sync.reset();
		verify(unlock(sync, clientAt(1001), FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	private static Client clientAt(int tick)
	{
		Client client = mock(Client.class);
		when(client.getTickCount()).thenReturn(tick);
		return client;
	}

	/** Kill 603 of `boss`, then the item's unlock `ticks` later. */
	private static LocalClogCache unlockAfter(String boss, int ticks, int itemId)
	{
		Client client = mock(Client.class);
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext(boss, 603), 1000);
		when(client.getTickCount()).thenReturn(1000 + ticks);
		return unlock(sync, client, itemId);
	}

	private static LocalClogCache unlock(LiveClogSync sync, Client client, int itemId)
	{
		Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);
		ClogIndex index = mock(ClogIndex.class);
		when(index.ensureParsed(any(), any())).thenReturn(true);
		when(index.itemIdsForName(any())).thenReturn(List.of(itemId));
		when(index.categoryKeysForItem(FANG)).thenReturn(List.of("maggot_king"));
		when(index.categoryKeysForItem(CLUE_ITEM)).thenReturn(List.of("beginner_treasure_trails"));
		when(index.categoryKeysForItem(PET)).thenReturn(List.of("all_pets", "maggot_king"));
		when(index.categoryKeysForItem(DEVIL_ITEM)).thenReturn(List.of("thermonuclear_smoke_devil"));
		when(index.categoryKeysForItem(UNPAGED)).thenReturn(null);
		when(index.categoryKeysForItem(GAUNTLET_ITEM)).thenReturn(List.of("the_gauntlet"));
		when(index.pageName("the_gauntlet")).thenReturn("The Gauntlet");
		when(index.pageName("maggot_king")).thenReturn("Maggot King");
		when(index.pageName("all_pets")).thenReturn("All Pets");
		when(index.pageName("beginner_treasure_trails")).thenReturn("Beginner Treasure Trails");
		when(index.pageName("thermonuclear_smoke_devil")).thenReturn("Thermonuclear Smoke Devil");
		LocalClogCache cache = mock(LocalClogCache.class);
		when(cache.serving("Tester")).thenReturn(true);
		when(cache.hasDataFor("Tester")).thenReturn(true);
		when(cache.mergeObtainedItem(anyString(), anyInt(), any(), any(), anyInt(), any())).thenReturn(true);
		sync.handleUnlock("Elder venator fang", 1232, 1734, client, null, index, cache, mock(KillClogChatNotifier.class),
			name ->
			{
			}, true);
		return cache;
	}
}
