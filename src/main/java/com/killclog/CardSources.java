package com.killclog;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The sites that hold a player's log, as icons: the Collection Log card's footer names them, and the Player Summary's
 * top bar repeats them as links to the player's pages (Dylan, 2026-11-14). Kill Clog's chalice first (your own log
 * read from the game when you don't sync), then TempleOSRS and RuneProfile; each names itself while hovered and opens
 * the player's page there.
 */
final class CardSources
{
	static final int ICON = 13;
	static final int GAP = 5;
	private static final int HIT_PAD = 2;
	private static final String TEMPLE = "TempleOSRS";
	private static final String RUNEPROFILE = "RuneProfile";
	private static final String KILLCLOG = "Kill Clog";
	private static final String LOCAL = "Local Collection Log";

	private CardSources()
	{
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Source
	{
		final String name;
		final BufferedImage icon;
		@Nullable
		final String url;
	}

	/**
	 * Every site that holds this player's log, whichever one's log the card shows (Dylan, 2026-10-12): one this lookup
	 * heard from, or one known to hold them by RuneProfile's profile, Temple's last answer or Kill Clog's sync list, since
	 * a request can be skipped (your own log) or time out. Your own log read from the game is Kill Clog's when you sync.
	 * Each site's name follows {@code prefix}, as "View on Kill Clog".
	 */
	static List<Source> of(ClogResult result, boolean onTemple, boolean onRuneProfile, boolean onKillclog, String prefix)
	{
		boolean killclog = result.isFromKillclog() || onKillclog;
		return of(result.isFromTemple() || onTemple, result.isFromRuneProfile() || onRuneProfile, killclog,
			result.isFromLocal() && !killclog, result.getPlayerName(), prefix);
	}

	static List<Source> of(boolean temple, boolean runeProfile, boolean killclog, boolean local, @Nullable String player,
		String prefix)
	{
		List<Source> sources = new ArrayList<>(3);
		if (killclog || local)
		{
			sources.add(new Source(local ? LOCAL : prefix + KILLCLOG, KillClogIcons.killClogSourceIcon(ICON),
				local ? null : link(player, TooltipItemLink::killclogUrl)));
		}
		if (temple)
		{
			sources.add(new Source(prefix + TEMPLE, KillClogIcons.templeSourceIcon(ICON), link(player, TooltipItemLink::templeUrl)));
		}
		if (runeProfile)
		{
			sources.add(new Source(prefix + RUNEPROFILE, KillClogIcons.runeProfileSourceIcon(ICON),
				link(player, TooltipItemLink::runeProfileUrl)));
		}
		return sources;
	}

	@Nullable
	private static String link(@Nullable String player, UnaryOperator<String> page)
	{
		return player != null && !player.isEmpty() ? page.apply(player) : null;
	}

	static int width(int count)
	{
		return count > 0 ? count * ICON + (count - 1) * GAP : 0;
	}

	/**
	 * The icons in a row from {@code x}, dim until hovered (Dylan, 2026-11-14), each naming itself in {@code section}
	 * while hovered and opening its page. One lights only where a press opens its page: everything clickable answers
	 * the hover, and nothing else does.
	 */
	static void draw(CardBody.Ctx c, List<Source> sources, int x, int y, int section)
	{
		Composite solid = c.g.getComposite();
		for (int i = 0; i < sources.size(); i++)
		{
			Source source = sources.get(i);
			int iconX = x + i * (ICON + GAP);
			if (source.icon != null)
			{
				c.g.setComposite(c.card.itemHover.opens(section, source.name, source.url) ? solid : KillClogColors.DIM);
				c.g.drawImage(source.icon, iconX, y, null);
				c.g.setComposite(solid);
			}
			c.hits.add(new TooltipItemHover.HitBox(section, 0, source.name,
				new Rectangle(iconX - HIT_PAD, y - HIT_PAD, ICON + HIT_PAD * 2, ICON + HIT_PAD * 2), true, source.url));
		}
	}
}
