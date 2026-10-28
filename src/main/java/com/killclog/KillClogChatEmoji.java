package com.killclog;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Chat emoji: :killclog:, :clog:, :green:, :rune:, :dragon: and :gilded: in a chat line become their icons while the
 * setting is on. Each icon is reserved from RuneLite's chat icon manager once and kept for the life of the client, as
 * RuneLite's emoji plugin keeps its own.
 */
@Singleton
class KillClogChatEmoji
{
	static final String KILLCLOG_TRIGGER = ":killclog:";
	static final String CLOG_TRIGGER = ":clog:";
	static final String GREEN_TRIGGER = ":green:";
	static final String RUNE_TRIGGER = ":rune:";
	static final String DRAGON_TRIGGER = ":dragon:";
	static final String GILDED_TRIGGER = ":gilded:";

	private static final int INLINE_ICON_H = 14;
	private static final int KILLCLOG_ICON_H = 12;
	private static final String[] TRIGGERS = {
		KILLCLOG_TRIGGER, CLOG_TRIGGER, GREEN_TRIGGER,
		RUNE_TRIGGER, DRAGON_TRIGGER, GILDED_TRIGGER
	};

	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private ChatIconManager chatIconManager;

	// trigger -> RuneLite's chat icon id; like the chat command's item icons, kept across a disable/enable.
	private final Map<String, Integer> iconIdByTrigger = new LinkedHashMap<>();

	void rewrite(ChatMessage chatMessage)
	{
		// Before the client has its icons (a reset, until the login screen) an emoji stays text.
		if (!isChatType(chatMessage.getType()) || client.getModIcons() == null)
		{
			return;
		}

		MessageNode messageNode = chatMessage.getMessageNode();
		String message = messageNode.getValue();
		if (!containsTrigger(message))
		{
			return;
		}

		Map<String, Integer> icons = new LinkedHashMap<>();
		for (String trigger : TRIGGERS)
		{
			if (message.contains(trigger))
			{
				int iconIdx = iconIndex(trigger);
				if (iconIdx >= 0)
				{
					icons.put(trigger, iconIdx);
				}
			}
		}
		String updatedMessage = rewriteText(message, icons);
		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
		client.refreshChat();
	}

	static String rewriteText(String message, Map<String, Integer> icons)
	{
		String updated = message;
		for (Map.Entry<String, Integer> entry : icons.entrySet())
		{
			updated = updated.replace(entry.getKey(), iconTag(entry.getValue()));
		}
		return updated.equals(message) ? null : updated;
	}

	private static boolean containsTrigger(String message)
	{
		for (String trigger : TRIGGERS)
		{
			if (message.contains(trigger))
			{
				return true;
			}
		}
		return false;
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

	/** The trigger's index in the chat's icons, reserving its icon the first time; -1 when it has no image. */
	private int iconIndex(String trigger)
	{
		Integer iconId = iconIdByTrigger.get(trigger);
		if (iconId == null)
		{
			BufferedImage plugin = KILLCLOG_TRIGGER.equals(trigger) ? KillClogIcons.pluginIcon() : null;
			int itemId = itemId(trigger);
			if (plugin == null && itemId <= 0)
			{
				return -1;
			}
			// Usable at once (client thread only); the image fills it when ready, as RuneLite's emoji plugin does.
			int reserved = chatIconManager.reserveChatIcon();
			iconId = reserved;
			iconIdByTrigger.put(trigger, reserved);
			if (plugin != null)
			{
				show(reserved, resizeKillClogIcon(plugin));
			}
			else
			{
				boolean green = GREEN_TRIGGER.equals(trigger);
				AsyncBufferedImage image = itemManager.getImage(itemId, 1, false);
				image.onLoaded(() -> show(reserved, resizeInlineIcon(green ? greenIcon(image) : image)));
			}
		}
		return chatIconManager.chatIconIndex(iconId);
	}

	private static int itemId(String trigger)
	{
		switch (trigger)
		{
			case KILLCLOG_TRIGGER:
			case CLOG_TRIGGER:
			case GREEN_TRIGGER:
				return PanelData.COLLECTION_LOG_ITEM_ID;
			case RUNE_TRIGGER:
				return tierItemId("rune");
			case DRAGON_TRIGGER:
				return tierItemId("dragon");
			case GILDED_TRIGGER:
				return tierItemId("gilded");
			default:
				return -1;
		}
	}

	private void show(int iconId, BufferedImage image)
	{
		chatIconManager.updateChatIcon(iconId, image);
		client.refreshChat();
	}

	private static int tierItemId(String tier)
	{
		for (int i = 0; i < ClogHelper.CLOG_TIERS.length
			&& i < ClogHelper.CLOG_TIER_ITEM_IDS.length; i++)
		{
			if (tier.equals(ClogHelper.CLOG_TIERS[i]))
			{
				return ClogHelper.CLOG_TIER_ITEM_IDS[i];
			}
		}
		return -1;
	}

	static BufferedImage greenIcon(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0)
				{
					continue;
				}
				int red = (argb >>> 16) & 0xFF;
				int green = (argb >>> 8) & 0xFF;
				int blue = argb & 0xFF;
				int shade = Math.max(70, (red + green + blue) / 3);
				int nextGreen = Math.min(255, shade + 90);
				int next = (alpha << 24) | (shade / 8 << 16) | (nextGreen << 8) | (shade / 8);
				out.setRGB(x, y, next);
			}
		}
		return out;
	}

	static BufferedImage resizeInlineIcon(BufferedImage image)
	{
		int width = Math.max(1, Math.round(image.getWidth() * INLINE_ICON_H / (float) image.getHeight()));
		return ImageUtil.resizeImage(image, width, INLINE_ICON_H);
	}

	static BufferedImage resizeKillClogIcon(BufferedImage image)
	{
		BufferedImage scaled = ImageUtil.resizeImage(image,
			Math.max(1, Math.round(image.getWidth() * KILLCLOG_ICON_H / (float) image.getHeight())),
			KILLCLOG_ICON_H);
		BufferedImage canvas = new BufferedImage(INLINE_ICON_H, INLINE_ICON_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = canvas.createGraphics();
		try
		{
			g2.drawImage(scaled, (INLINE_ICON_H - scaled.getWidth()) / 2,
				(INLINE_ICON_H - scaled.getHeight()) / 2, null);
		}
		finally
		{
			g2.dispose();
		}
		return canvas;
	}

}
