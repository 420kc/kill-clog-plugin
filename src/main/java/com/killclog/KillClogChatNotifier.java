package com.killclog;

import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/**
 * Kill Clog's chat lines: setup help always shows; drops, sync results and warnings follow the chat setting. Each goes
 * through RuneLite's chat manager as a plugin's own line, the way RuneLite's plugins and the Hub's largest send theirs.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
final class KillClogChatNotifier
{
	private final ChatMessageManager chatMessageManager;
	private final KillClogConfig config;

	void send(ChatNotice notice, String message)
	{
		if (!show(notice))
		{
			return;
		}

		// Queued from any thread and shown in order from the client's main loop. A plugin's line, not the game's: the
		// game-message readers (RuneLite's Loot Tracker threw on one of Kill Clog's at login, 10/28) leave it alone.
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("<col=4caf6e>Kill Clog:</col> " + message)
			.build());
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
