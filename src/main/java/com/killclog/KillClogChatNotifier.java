package com.killclog;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

/**
 * Kill Clog's chat lines: setup help always shows; drops, sync results and warnings follow the chat setting; a line
 * sent before your character exists waits for it.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
final class KillClogChatNotifier
{
	private final Client client;
	private final KillClogConfig config;
	private final ClientThread clientThread;
	// Lines waiting for your character, oldest first. Every line is sent from the client thread, so this is its alone.
	private final Deque<String> waiting = new ArrayDeque<>();

	void send(ChatNotice notice, String message)
	{
		if (!show(notice))
		{
			return;
		}

		// A line waits for your character, behind any line before it, through a hop or a lost connection: other plugins
		// read the character on every game message, and RuneLite's Loot Tracker threw on one that came first (10/28).
		// The login screen drops what waits. Client thread.
		waiting.add("<col=4caf6e>Kill Clog:</col> " + message);
		if (waiting.size() == 1)
		{
			clientThread.invoke(this::sendWaiting);
		}
	}

	/** True once what waits is sent, or dropped at the login screen; until then the client thread asks again. */
	private boolean sendWaiting()
	{
		if (client.getLocalPlayer() == null)
		{
			if (client.getGameState() != GameState.LOGIN_SCREEN)
			{
				return false;
			}
			waiting.clear();
			return true;
		}
		while (!waiting.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", waiting.poll(), null);
		}
		return true;
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
