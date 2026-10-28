package com.killclog;

import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
final class KillClogChatNotifier
{
	private final Client client;
	private final KillClogConfig config;
	private final ClientThread clientThread;

	void send(ChatNotice notice, String message)
	{
		if (!show(notice))
		{
			return;
		}

		String line = "<col=4caf6e>Kill Clog:</col> " + message;
		if (client.getLocalPlayer() != null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
			return;
		}
		// A line sent as the game logs in waits for your character: other plugins read it on every game message, and
		// RuneLite's Loot Tracker threw on one that came first (10/28). A logout before then drops it.
		clientThread.invokeLater(() ->
		{
			if (client.getLocalPlayer() == null)
			{
				GameState state = client.getGameState();
				return state != GameState.LOGGED_IN && state != GameState.LOADING;
			}
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
			return true;
		});
	}

	private boolean show(ChatNotice notice)
	{
		switch (notice)
		{
			case NEW_DROP:
			case SYNC_RESULT:
			case WARNING:
				return config.autosyncChatMessages();
			case SYNC_HELP:
				// Always on: the guidance lines are the only path out of an
				// unsynced state, and a muted user cannot know they muted them.
				return true;
			default:
				return true;
		}
	}
}
