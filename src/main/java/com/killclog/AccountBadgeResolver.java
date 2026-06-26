package com.killclog;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
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
	ImageIcon labelIcon(@Nullable AccountDisplay display)
	{
		BufferedImage badge = badge(display);
		if (badge == null)
		{
			return null;
		}
		AccountType type = display.accountType();
		return new ImageIcon(type != null && type.isGroupIronman() ? badge : resize(badge, 15));
	}

	@Nullable
	BufferedImage badge(@Nullable AccountDisplay display)
	{
		if (display == null)
		{
			return null;
		}
		HiscoreTable table = display.hiscoreTable();
		if (table.isSpecial())
		{
			return staticBadge(table);
		}
		AccountType type = display.accountType();
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
	static BufferedImage cachedBadge(@Nullable AccountDisplay display)
	{
		if (display == null)
		{
			return null;
		}
		HiscoreTable table = display.hiscoreTable();
		if (table.isSpecial())
		{
			return staticBadge(table);
		}
		AccountType type = display.accountType();
		if (type == null)
		{
			return null;
		}
		return type.isGroupIronman() ? ClogHelper.getGimBadge(type) : staticBadge(type);
	}

	@Nullable
	static String label(@Nullable AccountDisplay display)
	{
		return display != null ? display.label() : null;
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
		return loadHiscoreResource(resource);
	}

	@Nullable
	private static BufferedImage staticBadge(HiscoreTable table)
	{
		return loadHiscoreResource(table.badgeResource());
	}

	@Nullable
	private static BufferedImage loadHiscoreResource(@Nullable String resource)
	{
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

	private static BufferedImage resize(BufferedImage badge, int height)
	{
		int width = (int) Math.round((double) badge.getWidth() / badge.getHeight() * height);
		return ImageUtil.resizeImage(badge, width, height);
	}
}
