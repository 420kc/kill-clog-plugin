package com.killclog;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LiveClogSyncTest
{
	@Test
	public void theGameCountersWaitForASettledWorld()
	{
		Client client = mock(Client.class);
		Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);
		// Right after a hop the counters may still be the last world's.
		when(client.getVarpValue(ClogVarps.OBTAINED)).thenReturn(900);
		when(client.getVarpValue(ClogVarps.TOTAL)).thenReturn(1600);
		ClogIndex index = mock(ClogIndex.class);
		when(index.ensureParsed(any(), any())).thenReturn(true);
		when(index.itemIdsForName(any())).thenReturn(List.of(20997));
		LocalClogCache cache = mock(LocalClogCache.class);
		when(cache.setActivePlayer("Tester")).thenReturn(true);
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
}
