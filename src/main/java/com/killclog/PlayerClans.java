package com.killclog;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

/**
 * A player's clan for the Player Summary: its name, and the icon their title in it wears in clan chat. Your own comes
 * from the game, anyone else's from RuneProfile's summary; nothing new is sent anywhere.
 */
@RequiredArgsConstructor
class PlayerClans
{
	private final Client client;
	private final ChatIconManager icons;
	private final RuneProfileService runeProfile;

	@Nullable
	String name(String player)
	{
		if (isYou(player))
		{
			ClanChannel clan = client.getClanChannel();
			return clan != null ? clan.getName() : null;
		}
		return runeProfile.clan(player);
	}

	/** The title's icon, or null when there is none or it hasn't loaded (the card then names the clan instead). */
	@Nullable
	BufferedImage icon(String player)
	{
		ClanTitle title = isYou(player) ? yourTitle() : runeProfile.clanTitle(player);
		try
		{
			return title != null ? icons.getRankImage(title) : null;
		}
		catch (RuntimeException e)
		{
			// RuneLite loads the clan icons once logged in; an unknown title has no icon.
			return null;
		}
	}

	private boolean isYou(String player)
	{
		Player you = client.getLocalPlayer();
		return you != null && you.getName() != null
			&& Text.toJagexName(you.getName()).equalsIgnoreCase(Text.toJagexName(player));
	}

	@Nullable
	private ClanTitle yourTitle()
	{
		ClanChannel clan = client.getClanChannel();
		ClanSettings settings = client.getClanSettings();
		Player you = client.getLocalPlayer();
		ClanChannelMember member = clan != null && you != null ? clan.findMember(you.getName()) : null;
		return member != null && settings != null ? settings.titleForRank(member.getRank()) : null;
	}
}
