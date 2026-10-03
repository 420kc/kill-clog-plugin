package com.killclog;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.util.ImageUtil;

/**
 * Account badges for names. GIM badges come from the game's mod icons, which
 * the client thread loads at login; this never reads game state itself, since
 * it runs on the panel's thread.
 */
final class AccountBadgeResolver
{
	private AccountBadgeResolver()
	{
	}

	@Nullable
	static ImageIcon labelIcon(@Nullable AccountDisplay display)
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
	static BufferedImage badge(@Nullable AccountDisplay display)
	{
		if (display == null)
		{
			return null;
		}
		if (display.former() != null)
		{
			return formerBadge(display.former());
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
		return type.isGroupIronman() ? GimBadgeLoader.getGimBadge(type) : staticBadge(type);
	}

	@Nullable
	static String label(@Nullable AccountDisplay display)
	{
		return display != null ? display.label() : null;
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

	/** RuneLite's own helm with the mark of the mode left drawn over it. */
	@Nullable
	private static BufferedImage formerBadge(AccountDisplay.Former former)
	{
		BufferedImage helm = loadHiscoreResource(former.helm);
		BufferedImage overlay = loadResource(AccountBadgeResolver.class, former.overlay);
		if (helm == null || overlay == null)
		{
			return helm;
		}
		BufferedImage badge = new BufferedImage(helm.getWidth(), helm.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = badge.createGraphics();
		g.drawImage(helm, 0, 0, null);
		g.drawImage(overlay, 0, 0, null);
		g.dispose();
		return badge;
	}

	@Nullable
	private static BufferedImage loadHiscoreResource(@Nullable String resource)
	{
		return loadResource(HiscorePanel.class, resource);
	}

	@Nullable
	private static BufferedImage loadResource(Class<?> owner, @Nullable String resource)
	{
		if (resource == null)
		{
			return null;
		}
		try
		{
			return ImageUtil.loadImageResource(owner, resource);
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
