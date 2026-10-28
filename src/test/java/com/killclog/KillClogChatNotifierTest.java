package com.killclog;

import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KillClogChatNotifierTest
{
	private static final String LINE = "<col=4caf6e>Kill Clog:</col> Right-click menu is full.";

	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final KillClogChatNotifier notifier = new KillClogChatNotifier(client, new KillClogConfig()
	{
	}, clientThread);

	@Test
	public void aLineGoesStraightToChatOnceYourCharacterIsThere()
	{
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		notifier.send(ChatNotice.SYNC_HELP, "Right-click menu is full.");
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", LINE, null);
		verify(clientThread, never()).invokeLater(any(BooleanSupplier.class));
	}

	@Test
	public void aLineSentAsTheGameLogsInWaitsForYourCharacter()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		notifier.send(ChatNotice.SYNC_HELP, "Right-click menu is full.");
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
		BooleanSupplier waiting = waiting();
		assertFalse("no character yet: it waits another tick", waiting.getAsBoolean());

		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		assertTrue(waiting.getAsBoolean());
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", LINE, null);
	}

	@Test
	public void aLogoutBeforeYourCharacterArrivesDropsTheLine()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		notifier.send(ChatNotice.SYNC_HELP, "Right-click menu is full.");
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		assertTrue("done waiting", waiting().getAsBoolean());
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
	}

	private BooleanSupplier waiting()
	{
		ArgumentCaptor<BooleanSupplier> task = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(clientThread).invokeLater(task.capture());
		return task.getValue();
	}
}
