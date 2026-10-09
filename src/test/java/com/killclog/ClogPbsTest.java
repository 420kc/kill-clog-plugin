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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

	@Test
	public void aPageWithItsLineKeepsTheGamesTimeInTicks()
	{
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>",
			"Vorkath kills: <col=ffffff>500</col>");
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(104);
		pbs.pageDrawn(client);
		verify(configManager).setRSProfileConfiguration("killclog", "clogpb.vorkath", 62.4);
	}

	@Test
	public void theGauntletsTwoLinesAndTheColosseumsName()
	{
		page("The Gauntlet", "Obtained: <col=ffff00>3/9</col>", "Personal Best Corrupted: <col=ffffff>6:00</col>",
			"Personal Best: <col=ffffff>2:00</col>");
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(200);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2)).thenReturn(600);
		pbs.pageDrawn(client);
		verify(configManager).setRSProfileConfiguration("killclog", "clogpb.gauntlet", 120.0);
		verify(configManager).setRSProfileConfiguration("killclog", "clogpb.corrupted gauntlet", 360.0);

		page("Fortis Colosseum", "Obtained: <col=ffff00>1/8</col>", "Personal Best: <col=ffffff>20:25.20</col>");
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(2042);
		pbs.pageDrawn(client);
		verify(configManager).setRSProfileConfiguration("killclog", "clogpb.sol heredit", 1225.2);
	}

	@Test
	public void nothingWithoutTheLineOnAHostsLogWithoutATimeOrOnAnUnsettledPage()
	{
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(104);
		page("Barrows Chests", "Obtained: <col=ffff00>20/25</col>", "Barrows Chests opened: <col=ffffff>300</col>");
		pbs.pageDrawn(client);

		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		pbs.pageDrawn(client);
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);

		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(0);
		pbs.pageDrawn(client);

		page("The Nightmare", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>3:28</col>");
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(347);
		pbs.pageDrawn(client);

		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}
}
