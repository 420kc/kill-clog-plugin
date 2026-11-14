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
		LocalClogCache cache = unlockAfter(6, FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603), eq("Maggot King"));
	}

	@Test
	public void aDropFromItsOwnPageWaitsUpToThirtySeconds()
	{
		verify(unlockAfter(50, FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603), eq("Maggot King"));
		verify(unlockAfter(51, FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	// A casket or a skilling unlock right after a kill is no drop of that boss's: past the five ticks, it stays bare.
	@Test
	public void anItemFromAnotherPageNeverTakesTheKillPastFiveTicks()
	{
		verify(unlockAfter(6, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(), eq(0), isNull());
		verify(unlockAfter(5, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(), eq(603),
			eq("Maggot King"));
	}

	@Test
	public void anUnlockWithNoKillBeforeItHasNone()
	{
		LiveClogSync sync = new LiveClogSync();
		LocalClogCache cache = unlock(sync, mock(Client.class), FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	/** Kill 603 of Maggot King, then the item's unlock `ticks` later. */
	private static LocalClogCache unlockAfter(int ticks, int itemId)
	{
		Client client = mock(Client.class);
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
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
		when(index.pageName("maggot_king")).thenReturn("Maggot King");
		when(index.pageName("beginner_treasure_trails")).thenReturn("Beginner Treasure Trails");
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
