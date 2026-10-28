package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KillClogChatNotifierTest
{
	private static final String PREFIX = "<col=4caf6e>Kill Clog:</col> ";

	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	// What the client thread asks again on later ticks, as the real one keeps a BooleanSupplier that said false.
	private final List<BooleanSupplier> later = new ArrayList<>();
	private final KillClogChatNotifier notifier = new KillClogChatNotifier(client, new KillClogConfig()
	{
	}, clientThread);

	@Before
	public void runsOnTheClientThread()
	{
		doAnswer(invocation ->
		{
			BooleanSupplier task = invocation.getArgument(0);
			if (!task.getAsBoolean())
			{
				later.add(task);
			}
			return null;
		}).when(clientThread).invoke(any(BooleanSupplier.class));
	}

	@Test
	public void aLineGoesStraightToChatOnceYourCharacterIsThere()
	{
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		notifier.send(ChatNotice.SYNC_HELP, "one");
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "one", null);
		assertEquals(0, later.size());
	}

	@Test
	public void linesSentBeforeYourCharacterWaitForItInTheOrderSent()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		notifier.send(ChatNotice.SYNC_HELP, "one");
		notifier.send(ChatNotice.SYNC_HELP, "two");
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
		assertEquals("one wait for the queue, not one a line", 1, later.size());

		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		later.get(0).getAsBoolean();
		InOrder order = inOrder(client);
		order.verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "one", null);
		order.verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "two", null);

		notifier.send(ChatNotice.SYNC_HELP, "three");
		order.verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "three", null);
	}

	@Test
	public void aLineWaitsThroughAHopOrALostConnection()
	{
		when(client.getGameState()).thenReturn(GameState.HOPPING);
		notifier.send(ChatNotice.SYNC_RESULT, "Synced 12 items");
		when(client.getGameState()).thenReturn(GameState.CONNECTION_LOST);
		assertEquals(false, later.get(0).getAsBoolean());
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		assertEquals(true, later.get(0).getAsBoolean());
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "Synced 12 items", null);
	}

	@Test
	public void aLineWaitingWhenThePluginStopsNeverShowsAndARestartBeginsClean()
	{
		when(client.getGameState()).thenReturn(GameState.HOPPING);
		notifier.send(ChatNotice.SYNC_RESULT, "before the disable");
		notifier.stop();
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		assertEquals(true, later.get(0).getAsBoolean());
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());

		when(client.getLocalPlayer()).thenReturn(null);
		notifier.start();
		notifier.send(ChatNotice.SYNC_RESULT, "old");
		notifier.stop();
		notifier.start();
		notifier.send(ChatNotice.SYNC_RESULT, "new");
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		later.forEach(BooleanSupplier::getAsBoolean);
		verify(client, never()).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "old", null);
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "new", null);
	}

	@Test
	public void theLoginScreenDropsWhatWaits()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		notifier.send(ChatNotice.SYNC_HELP, "one");
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		assertEquals(true, later.get(0).getAsBoolean());

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		notifier.send(ChatNotice.SYNC_HELP, "two");
		verify(client, never()).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "one", null);
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + "two", null);
	}
}
