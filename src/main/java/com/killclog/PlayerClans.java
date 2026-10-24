package com.killclog;

import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
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
 * from the game, anyone else's from their Kill Clog sync, else RuneProfile's summary. Your sync carries yours (2.6.3).
 */
@RequiredArgsConstructor
class PlayerClans
{
	private final Client client;
	private final ChatIconManager icons;
	private final RuneProfileService runeProfile;
	// Your own, as the game showed it on the last tick. The card is built on Swing's thread, where the
	// client's clan state must not be read, so it reads this instead.
	// Whether this main-game world has settled since login or a hop: until then the game hasn't said you're in no clan.
	private final BooleanSupplier settled;
	// Share Clan (2.7.3, a player's ask): off, your sync says you're in no clan, so no Kill Clog surface names it.
	private final BooleanSupplier shared;
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
		yours = new Yours(name, clan, rank != null && settings != null ? settings.titleForRank(rank) : null,
			settled.getAsBoolean() && (member != null || settings == null && channel == null));
	}

	void clearClientSnapshot()
	{
		yours = Yours.NONE;
	}

	/**
	 * Your clan for a sync of {@code player}, its clan null when you're in none; null while the game hasn't said
	 * since the world last settled, or when the snapshot is another account's.
	 */
	@Nullable
	Yours forSync(String player)
	{
		Yours you = yours;
		if (!you.is(player))
		{
			return null;
		}
		return !shared.getAsBoolean() ? new Yours(you.name, null, null, true) : you.known ? you : null;
	}

	@Nullable
	String name(String player, @Nullable ClogResult clog)
	{
		Yours you = yours;
		// Share Clan off, your own card shows none too, so it never looks like it's being shared.
		return you.is(player) ? (shared.getAsBoolean() ? you.clan : null)
			: synced(clog) ? clog.getSyncedClan() : runeProfile.clan(player);
	}

	/** The title's icon, or null when there is none or it hasn't loaded (the card then names the clan instead). */
	@Nullable
	BufferedImage icon(String player, @Nullable ClogResult clog)
	{
		Yours you = yours;
		ClanTitle title = you.is(player) ? (shared.getAsBoolean() ? you.title : null)
			: synced(clog) ? clog.getSyncedClanTitle() : runeProfile.clanTitle(player);
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

	private static boolean synced(@Nullable ClogResult clog)
	{
		return clog != null && clog.isSyncedClanKnown();
	}

	@RequiredArgsConstructor
	static final class Yours
	{
		static final Yours NONE = new Yours(null, null, null, false);

		final String name;
		final String clan;
		final ClanTitle title;
		// The game has said: in this clan, or in none.
		final boolean known;

		boolean is(String player)
		{
			return name != null && player != null && Text.toJagexName(name).equalsIgnoreCase(Text.toJagexName(player));
		}
	}
}
