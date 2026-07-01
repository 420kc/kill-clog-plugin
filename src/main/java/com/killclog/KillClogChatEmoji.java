package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
	static final String GREEN_TRIGGER = ":green:";
	static final String RUNE_TRIGGER = ":rune:";
	static final String DRAGON_TRIGGER = ":dragon:";
	static final String GILDED_TRIGGER = ":gilded:";

	private static final int COLLECTION_LOG_ITEM_ID = 22711;
	private static final int INLINE_ICON_H = 14;
	private static final String[] TRIGGERS = {
		KILLCLOG_TRIGGER, CLOG_TRIGGER, GREEN_TRIGGER,
		RUNE_TRIGGER, DRAGON_TRIGGER, GILDED_TRIGGER
	};

	@Inject private Client client;
	@Inject private ItemManager itemManager;

	private final Map<String, Integer> iconIdxByTrigger = new LinkedHashMap<>();

	void rewrite(ChatMessage chatMessage)
	{
		if (!isChatType(chatMessage.getType()))
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
				Integer iconIdx = iconFor(trigger);
				if (iconIdx != null)
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

	static String rewriteText(String message, Integer killClogIdx, Integer clogIdx)
	{
		Map<String, Integer> icons = new LinkedHashMap<>();
		if (killClogIdx != null)
		{
			icons.put(KILLCLOG_TRIGGER, killClogIdx);
		}
		if (clogIdx != null)
		{
			icons.put(CLOG_TRIGGER, clogIdx);
		}
		return rewriteText(message, icons);
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

	private Integer killClogIcon()
	{
		Integer cached = iconIdxByTrigger.get(KILLCLOG_TRIGGER);
		if (cached == null)
		{
			BufferedImage image = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			cached = registerIcon(resizeInlineIcon(image));
			if (cached != null)
			{
				iconIdxByTrigger.put(KILLCLOG_TRIGGER, cached);
			}
		}
		return cached;
	}

	private Integer iconFor(String trigger)
	{
		switch (trigger)
		{
			case KILLCLOG_TRIGGER:
				return killClogIcon();
			case CLOG_TRIGGER:
				return itemIcon(CLOG_TRIGGER, COLLECTION_LOG_ITEM_ID, false);
			case GREEN_TRIGGER:
				return itemIcon(GREEN_TRIGGER, COLLECTION_LOG_ITEM_ID, true);
			case RUNE_TRIGGER:
				return itemIcon(RUNE_TRIGGER, tierItemId("rune"), false);
			case DRAGON_TRIGGER:
				return itemIcon(DRAGON_TRIGGER, tierItemId("dragon"), false);
			case GILDED_TRIGGER:
				return itemIcon(GILDED_TRIGGER, tierItemId("gilded"), false);
			default:
				return null;
		}
	}

	private Integer itemIcon(String trigger, int itemId, boolean green)
	{
		Integer cached = iconIdxByTrigger.get(trigger);
		if (cached != null || itemId <= 0)
		{
			return cached;
		}

		int slot = reserveIconSlot();
		if (slot == -1)
		{
			return null;
		}
		iconIdxByTrigger.put(trigger, slot);

		BufferedImage image = itemManager.getImage(itemId, 1, false);
		if (image instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) image).onLoaded(() ->
				writeIcon(slot, resizeInlineIcon(green ? greenIcon(image) : image)));
		}
		else
		{
			writeIcon(slot, resizeInlineIcon(green ? greenIcon(image) : image));
		}
		return slot;
	}

	private static int tierItemId(String tier)
	{
		for (int i = 0; i < ClogHelper.CLOG_TIERS.length
			&& i < PanelData.CLOG_TIER_ITEM_IDS.length; i++)
		{
			if (tier.equals(ClogHelper.CLOG_TIERS[i]))
			{
				return PanelData.CLOG_TIER_ITEM_IDS[i];
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
		iconIdxByTrigger.clear();
	}
}
