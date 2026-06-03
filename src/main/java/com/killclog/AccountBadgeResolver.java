package com.killclog;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.util.ImageUtil;

final class AccountBadgeResolver
{
	private final Client client;

	AccountBadgeResolver(Client client)
	{
		this.client = client;
	}

	@Nullable
	BufferedImage badge(@Nullable AccountType type)
	{
		if (type == null)
		{
			return null;
		}
		if (type.isGroupIronman())
		{
			return gimBadge(type);
		}
		return staticBadge(type);
	}

	@Nullable
	static BufferedImage cachedBadge(@Nullable AccountType type)
	{
		if (type == null)
		{
			return null;
		}
		return type.isGroupIronman() ? ClogHelper.getGimBadge(type) : staticBadge(type);
	}

	@Nullable
	private BufferedImage gimBadge(AccountType type)
	{
		BufferedImage badge = ClogHelper.getGimBadge(type);
		if (badge != null)
		{
			return badge;
		}

		int index = ClogHelper.gimModiconIndex(type);
		IndexedSprite[] modIcons = client.getModIcons();
		if (index < 0 || modIcons == null || modIcons.length <= index)
		{
			return null;
		}
		badge = ClogHelper.indexedSpriteToImage(modIcons[index]);
		if (badge != null)
		{
			ClogHelper.setGimBadge(type, badge);
		}
		return badge;
	}

	@Nullable
	private static BufferedImage staticBadge(AccountType type)
	{
		String resource = ClogHelper.accountBadgeResource(type);
		if (resource == null)
		{
			return null;
		}
		try
		{
			return ImageUtil.loadImageResource(HiscorePanel.class, resource);
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
