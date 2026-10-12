package com.killclog;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
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
	// Your own, as the game showed it on the last tick. The card is built on Swing's thread, where the
	// client's clan state must not be read, so it reads this instead.
	private volatile Yours yours = Yours.NONE;

	/** Reads your name, clan and title from the game; client thread only, each tick. */
	void refreshClientSnapshot()
	{
		Player you = client.getLocalPlayer();
		String name = you != null ? you.getName() : null;
		ClanSettings settings = client.getClanSettings();
		ClanChannel channel = client.getClanChannel();
		// Membership lives in the clan's settings, so it stands without the clan's chat channel joined; the channel
		// names the clan only while the settings haven't loaded.
		ClanMember member = settings != null && name != null ? settings.findMember(name) : null;
		ClanChannelMember inChannel = member == null && channel != null && name != null ? channel.findMember(name) : null;
		String clan = member != null ? settings.getName() : channel != null ? channel.getName() : null;
		ClanRank rank = member != null ? member.getRank() : inChannel != null ? inChannel.getRank() : null;
		yours = new Yours(name, clan, rank != null && settings != null ? settings.titleForRank(rank) : null);
	}

	void clearClientSnapshot()
	{
		yours = Yours.NONE;
	}

	@Nullable
	String name(String player)
	{
		Yours you = yours;
		return you.is(player) ? you.clan : runeProfile.clan(player);
	}

	/** The title's icon, or null when there is none or it hasn't loaded (the card then names the clan instead). */
	@Nullable
	BufferedImage icon(String player)
	{
		Yours you = yours;
		ClanTitle title = you.is(player) ? you.title : runeProfile.clanTitle(player);
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

	@RequiredArgsConstructor
	private static final class Yours
	{
		static final Yours NONE = new Yours(null, null, null);

		final String name;
		final String clan;
		final ClanTitle title;

		boolean is(String player)
		{
			return name != null && player != null && Text.toJagexName(name).equalsIgnoreCase(Text.toJagexName(player));
		}
	}
}
