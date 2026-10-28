package com.killclog;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class KillClogChatNotifierTest
{
	private final ChatMessageManager chat = mock(ChatMessageManager.class);

	@Test
	public void aLineGoesThroughRuneLitesChatManagerAsAPluginsLine()
	{
		new KillClogChatNotifier(chat, new KillClogConfig()
		{
		}).send(ChatNotice.SYNC_HELP, "Right-click menu is full.");
		ArgumentCaptor<QueuedMessage> line = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(line.capture());
		// Not a game message, so the game-message readers (RuneLite's Loot Tracker) never parse it.
		assertEquals(ChatMessageType.CONSOLE, line.getValue().getType());
		assertEquals("<col=4caf6e>Kill Clog:</col> Right-click menu is full.", line.getValue().getRuneLiteFormattedMessage());
	}

	@Test
	public void theChatSettingHidesDropsAndResultsButNeverSetupHelp()
	{
		KillClogChatNotifier quiet = new KillClogChatNotifier(chat, new KillClogConfig()
		{
			@Override
			public boolean autosyncChatMessages()
			{
				return false;
			}
		});
		quiet.send(ChatNotice.SYNC_RESULT, "Synced 12 items");
		quiet.send(ChatNotice.NEW_DROP, "Added Twisted bow to Kill Clog");
		verify(chat, never()).queue(any());
		quiet.send(ChatNotice.SYNC_HELP, "Open your Collection Log to set up Kill Clog automatically.");
		verify(chat).queue(any());
	}
}
