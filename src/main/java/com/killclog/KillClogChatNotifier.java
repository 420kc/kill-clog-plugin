package com.killclog;

import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

final class KillClogChatNotifier
{
	private final Client client;
	private final KillClogConfig config;

	@Inject
	KillClogChatNotifier(Client client, KillClogConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void send(ChatNotice notice, String message)
	{
		if (!show(notice))
		{
			return;
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=4caf6e>Kill Clog:</col> " + message,
			null);
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
