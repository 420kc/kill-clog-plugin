package com.killclog;

import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.game.ChatIconManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PlayerClansTest
{
	private final Client client = mock(Client.class);
	private final ChatIconManager icons = mock(ChatIconManager.class);
	private final RuneProfileService runeProfile = mock(RuneProfileService.class);
	private boolean settled = true;
	private final PlayerClans clans = new PlayerClans(client, icons, runeProfile, () -> settled);

	@Test
	public void theCardReadsYourClanWithoutTouchingTheClient()
	{
		// The card is built on Swing's thread, where RuneLite asserts that clan state is read on its own
		// thread (a dev client stops the card there). The tick's snapshot carries everything it needs.
		ClanTitle title = new ClanTitle(100, "Administrator");
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		youAreIn("420 kc", "Clogged", title);
		when(icons.getRankImage(title)).thenReturn(image);

		clans.refreshClientSnapshot();
		clearInvocations(client);

		assertEquals("Clogged", clans.name("420_kc", null));
		assertSame(image, clans.icon("420 KC", null));
		verifyNoInteractions(client);
	}

	@Test
	public void yourClanStandsWithoutItsChatChannel()
	{
		// Membership is in the clan's settings: a player who left the clan's chat channel is still in the clan.
		ClanTitle title = new ClanTitle(100, "Administrator");
		Player you = mock(Player.class);
		when(you.getName()).thenReturn("420 kc");
		ClanMember member = mock(ClanMember.class);
		when(member.getRank()).thenReturn(ClanRank.ADMINISTRATOR);
		ClanSettings settings = mock(ClanSettings.class);
		when(settings.getName()).thenReturn("Clogged");
		when(settings.findMember("420 kc")).thenReturn(member);
		when(settings.titleForRank(ClanRank.ADMINISTRATOR)).thenReturn(title);
		when(client.getLocalPlayer()).thenReturn(you);
		when(client.getClanSettings()).thenReturn(settings);
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		when(icons.getRankImage(title)).thenReturn(image);

		clans.refreshClientSnapshot();

		assertEquals("Clogged", clans.name("420 kc", null));
		assertSame(image, clans.icon("420 kc", null));
	}

	@Test
	public void anyoneElsesClanComesFromRuneProfile()
	{
		youAreIn("420 kc", "Clogged", new ClanTitle(100, "Administrator"));
		when(runeProfile.clan("based batt")).thenReturn("Another Clan");
		clans.refreshClientSnapshot();
		assertEquals("Another Clan", clans.name("based batt", null));
	}

	@Test
	public void afterLogoutYourOwnNameNoLongerReadsAsYours()
	{
		youAreIn("420 kc", "Clogged", new ClanTitle(100, "Administrator"));
		clans.refreshClientSnapshot();
		clans.clearClientSnapshot();
		assertNull(clans.name("420 kc", null));
		assertNull(clans.icon("420 kc", null));
	}

	@Test
	public void anotherPlayersOwnSyncNamesTheirClanBeforeRuneProfile()
	{
		when(runeProfile.clan("A q p Weed")).thenReturn("Old Clan");
		when(runeProfile.clanTitle("A q p Weed")).thenReturn(new ClanTitle(12, "Old"));
		ClogResult synced = new ClogResult("A q p Weed", java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), null, AccountType.IRONMAN);
		ClanTitle recruit = new ClanTitle(24, "Recruit");
		synced.setSyncedClan("Iron Clan");
		synced.setSyncedClanKnown(true);
		synced.setSyncedClanTitle(recruit);
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		when(icons.getRankImage(recruit)).thenReturn(image);

		assertEquals("Iron Clan", clans.name("A q p Weed", synced));
		assertSame(image, clans.icon("A q p Weed", synced));
		assertEquals("without their sync, RuneProfile's", "Old Clan", clans.name("A q p Weed", null));
	}

	@Test
	public void yourSyncCarriesYourClanOrNoneOnlyOnceTheWorldHasSettled()
	{
		youAreIn("420 kc", "Clogged", new ClanTitle(100, "Administrator"));
		ClanMember member = mock(ClanMember.class);
		when(member.getRank()).thenReturn(ClanRank.ADMINISTRATOR);
		when(client.getClanSettings().getName()).thenReturn("Clogged");
		when(client.getClanSettings().findMember("420 kc")).thenReturn(member);

		settled = false;
		clans.refreshClientSnapshot();
		assertNull("before the world settles the game hasn't said", clans.forSync("420 kc"));

		settled = true;
		clans.refreshClientSnapshot();
		assertEquals("Clogged", clans.forSync("420 kc").clan);
		assertEquals(100, clans.forSync("420 kc").title.getId());

		when(client.getClanSettings()).thenReturn(null);
		when(client.getClanChannel()).thenReturn(null);
		clans.refreshClientSnapshot();
		assertNull("in no clan, the sync says none", clans.forSync("420 kc").clan);

		clans.clearClientSnapshot();
		assertNull(clans.forSync("420 kc"));
	}

	private void youAreIn(String name, String clanName, ClanTitle title)
	{
		Player you = mock(Player.class);
		when(you.getName()).thenReturn(name);
		ClanChannelMember member = mock(ClanChannelMember.class);
		when(member.getRank()).thenReturn(ClanRank.ADMINISTRATOR);
		ClanChannel channel = mock(ClanChannel.class);
		when(channel.getName()).thenReturn(clanName);
		when(channel.findMember(name)).thenReturn(member);
		ClanSettings settings = mock(ClanSettings.class);
		when(settings.titleForRank(ClanRank.ADMINISTRATOR)).thenReturn(title);
		when(client.getLocalPlayer()).thenReturn(you);
		when(client.getClanChannel()).thenReturn(channel);
		when(client.getClanSettings()).thenReturn(settings);
	}
}
