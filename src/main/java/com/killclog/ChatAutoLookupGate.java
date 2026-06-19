package com.killclog;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

final class ChatAutoLookupGate
{
	private static final long AUTOSYNC_INTERVAL_MS = 5 * 60 * 1000;
	private static final Set<ChatMessageType> AUTOSYNC_CHAT_TYPES = EnumSet.of(
		ChatMessageType.PUBLICCHAT,
		ChatMessageType.FRIENDSCHAT,
		ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.AUTOTYPER
	);

	private long lastAutoSyncMs;

	boolean shouldRefresh(ChatMessage event, String localName, String displayedName, long now)
	{
		if (!AUTOSYNC_CHAT_TYPES.contains(event.getType()))
		{
			return false;
		}
		String sender = event.getName();
		if (sender == null || !sender.equalsIgnoreCase(localName))
		{
			return false;
		}
		if (now - lastAutoSyncMs < AUTOSYNC_INTERVAL_MS)
		{
			return false;
		}
		if (displayedName == null || !displayedName.equalsIgnoreCase(localName))
		{
			return false;
		}

		lastAutoSyncMs = now;
		return true;
	}
}
