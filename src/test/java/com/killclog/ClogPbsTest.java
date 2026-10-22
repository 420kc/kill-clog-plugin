package com.killclog;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClogPbsTest
{
	private Client client;
	private ConfigManager configManager;
	private ClogPbs pbs;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		configManager = mock(ConfigManager.class);
		pbs = new ClogPbs(configManager);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
	}

	/** The page header's lines, as the game draws them: the page name first. */
	private void page(String... lines)
	{
		Widget[] children = new Widget[lines.length];
		for (int i = 0; i < lines.length; i++)
		{
			children[i] = mock(Widget.class);
			when(children[i].getText()).thenReturn(lines[i]);
		}
		Widget header = mock(Widget.class);
		when(header.getDynamicChildren()).thenReturn(children);
		when(client.getWidget(InterfaceID.Collection.HEADER_TEXT)).thenReturn(header);
	}

	private int cycle = 1000;

	/** The server sends a line's time, as it does a client cycle before it draws that page. */
	private void sends(int varp, int ticks)
	{
		when(client.getVarpValue(varp)).thenReturn(ticks);
		when(client.getGameCycle()).thenReturn(++cycle);
		pbs.varpChanged(client, varp);
	}

	/** The log draws the page on screen: once as it opens, then once or twice more as it settles. */
	private void draws(int times)
	{
		when(client.getGameCycle()).thenReturn(++cycle);
		for (int i = 0; i < times; i++)
		{
			pbs.pageDrawn(client);
		}
		cycle += 15;
	}

	@Test
	public void aPageWithItsLineKeepsTheGamesTimeInTicks()
	{
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>",
			"Vorkath kills: <col=ffffff>500</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		draws(3);
		verify(configManager, times(1)).setRSProfileConfiguration("killclog", "pagepb.vorkath", 62.4);
	}

	@Test
	public void theGauntletsTwoLinesAndTheColosseumsName()
	{
		page("The Gauntlet", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>2:00</col>",
			"Personal Best Corrupted: <col=ffffff>6:00</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 200);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2, 600);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.gauntlet", 120.0);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.corrupted gauntlet", 360.0);

		page("Fortis Colosseum", "Obtained: <col=ffff00>1/8</col>", "Personal Best: <col=ffffff>20:25.20</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 2042);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.sol heredit", 1225.2);
	}

	@Test
	public void nothingWithoutTheLineOnAHostsLogWithoutATimeOrOnAnUnsettledPage()
	{
		page("Barrows Chests", "Obtained: <col=ffff00>20/25</col>", "Barrows Chests opened: <col=ffffff>300</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		draws(1);

		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 105);
		draws(1);
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);

		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 0);
		draws(1);

		page("The Nightmare", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>3:28</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 347);
		draws(1);

		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void aPageTheServerSentNoTimeForTakesNone()
	{
		// tribrd, 10/22: the Gauntlet, then Fortis Colosseum, where they have no record, so the server sends nothing
		// and the varp still holds the Gauntlet's time through all three of the Colosseum's draws.
		page("The Gauntlet", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>13:27.60</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 1346);
		draws(3);
		page("Fortis Colosseum", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>13:27.60</col>");
		draws(3);
		verify(configManager, times(1)).setRSProfileConfiguration("killclog", "pagepb.gauntlet", 807.6);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.sol heredit"), any());
	}

	@Test
	public void aPageWaitingWithNoRecordNeverTakesTheNextPagesTime()
	{
		// tribrd's path, 10/22: Fortis Colosseum, no record, so nothing comes; a tick later the Gauntlet, whose time
		// lands a cycle before its page draws, while the Colosseum is still on screen. 2.7.1 filed it as Sol Heredit.
		page("Fortis Colosseum", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>0:56.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 94);
		cycle += ClogPbs.ARRIVAL_CYCLES + 1;
		draws(3);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 1346);
		page("The Gauntlet", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>13:27.60</col>");
		draws(3);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.sol heredit"), any());
		verify(configManager, times(1)).setRSProfileConfiguration("killclog", "pagepb.gauntlet", 807.6);
	}

	@Test
	public void theGauntletTakesOnlyTheLineThatWasSent()
	{
		// A Gauntlet record but no Corrupted one: the second varp still holds what it last held (here a host's).
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2)).thenReturn(600);
		page("The Gauntlet", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>2:00</col>",
			"Personal Best Corrupted: <col=ffffff>6:00</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 200);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.gauntlet", 120.0);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.corrupted gauntlet"), any());
	}

	@Test
	public void aTimeIsTakenUpToFiveCyclesBeforeItsDrawAndNoEarlier()
	{
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		when(client.getGameCycle()).thenReturn(cycle + 5);
		pbs.pageDrawn(client);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.vorkath", 62.4);

		cycle += 20;
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		when(client.getGameCycle()).thenReturn(cycle + 6);
		pbs.pageDrawn(client);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.zulrah"), any());
	}

	@Test
	public void theNextPagesTimeIsNeverTheLastPages()
	{
		// Kale Neef's path, 10/18: Amoxliatl, then the Alchemical Hydra with no record, then on to Araxxor a tick
		// later. The Hydra takes nothing, and Araxxor's time is Araxxor's.
		page("Amoxliatl", "Obtained: <col=ffff00>1/5</col>", "Personal Best: <col=ffffff>0:31.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 53);
		draws(1);
		page("Alchemical Hydra", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>0:31.80</col>");
		draws(3);
		page("Araxxor", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 168);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.alchemical hydra"), any());
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.araxxor", 100.8);
	}

	@Test
	public void aTimeThatLandsAtAnyOtherMomentIsNobodys()
	{
		// A value that lands with no page drawing just after it (the log closed between the two) goes unused, and
		// the next page the player opens, one they have no record on, never takes it.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		cycle += ClogPbs.ARRIVAL_CYCLES;
		draws(1);
		page("Zulrah", "Obtained: <col=ffff00>0/9</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		draws(1);
		// A value that lands while a page is on screen isn't that page's either.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		// One value is one page's: a second page drawn right behind the first never takes it as well.
		page("Araxxor", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 168);
		pbs.pageDrawn(client);
		page("Alchemical Hydra", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		pbs.pageDrawn(client);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.araxxor", 100.8);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.alchemical hydra"), any());
	}

	@Test
	public void dylansLogReplayedClickByClick()
	{
		// 10/22's logged session, its order and its draws: every time to its own page, and nothing to the rest.
		page("Abyssal Sire", "Obtained: <col=ffff00>6/9</col>", "Abyssal Sire kills: <col=ffffff>420</col>");
		draws(3);
		page("Alchemical Hydra", "Obtained: <col=ffff00>10/11</col>", "Personal Best: <col=ffffff>1:41.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 169);
		draws(1);
		page("Barrows Chests", "Obtained: <col=ffff00>25/25</col>", "Barrows Chests opened: <col=ffffff>2,420</col>");
		draws(3);
		page("Doom of Mokhaiotl", "Obtained: <col=ffff00>6/6</col>", "Personal Best: <col=ffffff>10:09.60</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 1016);
		draws(1);
		page("Fortis Colosseum", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>34:07.20</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 3412);
		draws(1);
		page("The Gauntlet", "Obtained: <col=ffff00>5/5</col>", "Personal Best: <col=ffffff>6:06.60</col>",
			"Personal Best Corrupted: <col=ffffff>8:24.60</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 611);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2, 841);
		draws(3);
		page("Giant Mole", "Obtained: <col=ffff00>2/4</col>", "Giant Mole kills: <col=ffffff>420</col>");
		draws(3);
		// Back to the Colosseum: the same time as when it was last read, so no value comes and nothing is read.
		page("Fortis Colosseum", "Obtained: <col=ffff00>3/9</col>", "Personal Best: <col=ffffff>34:07.20</col>");
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.alchemical hydra", 101.4);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.sol heredit", 2047.2);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.gauntlet", 366.6);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.corrupted gauntlet", 504.6);
		verify(configManager, times(4)).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void theTimesKeptBeforeThePageCheckAreClearedOnceAndNeverRead()
	{
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys("killclog", "rsprofile.abc", ClogPbs.OLD_PREFIX))
			.thenReturn(java.util.List.of("clogpb.alchemical hydra", "clogpb.amoxliatl"));
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		draws(1);
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		draws(1);
		verify(configManager, times(1)).unsetConfiguration("killclog", "rsprofile.abc", "clogpb.alchemical hydra");
		verify(configManager, times(1)).unsetConfiguration("killclog", "rsprofile.abc", "clogpb.amoxliatl");
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.zulrah", 55.8);
	}
}
