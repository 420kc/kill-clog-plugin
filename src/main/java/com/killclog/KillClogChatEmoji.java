package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@Singleton
class KillClogChatEmoji
{
	static final String KILLCLOG_TRIGGER = ":killclog:";
	static final String CLOG_TRIGGER = ":clog:";

	private static final int COLLECTION_LOG_ITEM_ID = 22711;
	private static final int INLINE_ICON_H = 14;

	@Inject private Client client;
	@Inject private ItemManager itemManager;

	private Integer killClogIconIdx;
	private Integer clogIconIdx;

	void rewrite(ChatMessage chatMessage)
	{
		if (!isChatType(chatMessage.getType()))
		{
			return;
		}

		MessageNode messageNode = chatMessage.getMessageNode();
		String message = messageNode.getValue();
		if (!message.contains(KILLCLOG_TRIGGER) && !message.contains(CLOG_TRIGGER))
		{
			return;
		}

		Integer killClogIdx = message.contains(KILLCLOG_TRIGGER) ? killClogIcon() : null;
		Integer clogIdx = message.contains(CLOG_TRIGGER) ? clogIcon() : null;
		String updatedMessage = rewriteText(message, killClogIdx, clogIdx);
		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
		client.refreshChat();
	}

	static String rewriteText(String message, Integer killClogIdx, Integer clogIdx)
	{
		String updated = message;
		if (killClogIdx != null)
		{
			updated = updated.replace(KILLCLOG_TRIGGER, iconTag(killClogIdx));
		}
		if (clogIdx != null)
		{
			updated = updated.replace(CLOG_TRIGGER, iconTag(clogIdx));
		}
		return updated.equals(message) ? null : updated;
	}

	private static String iconTag(int iconIdx)
	{
		return "<img=" + iconIdx + ">";
	}

	private static boolean isChatType(ChatMessageType type)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				return true;
			default:
				return false;
		}
	}

	private Integer killClogIcon()
	{
		if (killClogIconIdx == null)
		{
			BufferedImage image = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			killClogIconIdx = registerIcon(resizeInlineIcon(image));
		}
		return killClogIconIdx;
	}

	private Integer clogIcon()
	{
		if (clogIconIdx == null)
		{
			int slot = reserveIconSlot();
			if (slot == -1)
			{
				return null;
			}
			clogIconIdx = slot;

			BufferedImage image = itemManager.getImage(COLLECTION_LOG_ITEM_ID, 1, false);
			if (image instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) image).onLoaded(() ->
					writeIcon(slot, resizeInlineIcon(image)));
			}
			else
			{
				writeIcon(slot, resizeInlineIcon(image));
			}
		}
		return clogIconIdx;
	}

	static BufferedImage resizeInlineIcon(BufferedImage image)
	{
		int width = Math.max(1, Math.round(image.getWidth() * INLINE_ICON_H / (float) image.getHeight()));
		return ImageUtil.resizeImage(image, width, INLINE_ICON_H);
	}

	private Integer registerIcon(BufferedImage image)
	{
		int slot = reserveIconSlot();
		if (slot == -1)
		{
			return null;
		}
		writeIcon(slot, image);
		return slot;
	}

	private int reserveIconSlot()
	{
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null)
		{
			return -1;
		}

		int slot = modIcons.length;
		IndexedSprite[] grown = Arrays.copyOf(modIcons, slot + 1);
		client.setModIcons(grown);
		return slot;
	}

	private void writeIcon(int slot, BufferedImage image)
	{
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null || slot >= modIcons.length)
		{
			return;
		}

		modIcons[slot] = ImageUtil.getImageIndexedSprite(image, client);
		client.refreshChat();
	}

	void clear()
	{
		killClogIconIdx = null;
		clogIconIdx = null;
	}
}
