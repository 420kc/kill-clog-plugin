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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
	private final java.util.List<String> counts = new java.util.ArrayList<>();
	// The player's own counts, as their log holds them.
	private final java.util.Map<String, Integer> held = new java.util.HashMap<>();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		configManager = mock(ConfigManager.class);
		pbs = new ClogPbs(configManager, (label, count) -> counts.add(label + "=" + count), held::get);
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
	// Whether the cycle the next line lands in has begun: one page's lines land together.
	private boolean landing;

	/** The server sends a line's value, a page's lines in one client cycle, the cycle before it draws that page. */
	private void sends(int varp, int ticks)
	{
		if (!landing)
		{
			cycle++;
			landing = true;
		}
		when(client.getVarpValue(varp)).thenReturn(ticks);
		when(client.getGameCycle()).thenReturn(cycle);
		pbs.varpChanged(client, varp);
	}

	/**
	 * The log draws the page on screen in the next cycle, as the Bosses tab does: once as it opens, then once or twice
	 * more as it settles. Half a tick on, the game tick settles what it read.
	 */
	private void draws(int times)
	{
		cycle++;
		drawsNow(times);
		ticks();
	}

	/** Half a tick passes with no page drawn, and the game tick settles the page on screen. */
	private void ticks()
	{
		cycle += 15;
		pbs.settle(cycle);
	}

	/**
	 * The log draws the page in the cycle the clock stands at, the cycle its numbers land in on every tab but Bosses;
	 * the next line sent lands in the cycle after.
	 */
	private void drawsNow(int times)
	{
		landing = false;
		when(client.getGameCycle()).thenReturn(cycle);
		for (int i = 0; i < times; i++)
		{
			pbs.pageDrawn(client);
		}
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
		// A player's path, 10/22: the Gauntlet, then Fortis Colosseum, where they have no record, so the server sends nothing
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
		// The same player's path, 10/22: Fortis Colosseum, no record, so nothing comes; a tick later the Gauntlet, whose time
		// lands a cycle before its page draws, while the Colosseum is still on screen. 2.7.1 filed it as Sol Heredit.
		page("Fortis Colosseum", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>0:56.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 94);
		cycle += 6;
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
	public void aTimeIsTakenFromTheCycleItsPageDrawsInOrTheOneBefore()
	{
		// Two cycles before the draw is some other moment's value.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		cycle++;
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		// The cycle before: a row on the Bosses tab (the first log, 23 draws of 23).
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		draws(3);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.zulrah", 55.8);

		// The same cycle: a row on any other tab, and a tab's own first page (the second log).
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		drawsNow(3);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.vorkath"), any());
		ticks();
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.vorkath", 62.4);
	}

	@Test
	public void twoBossesClickedInsideATickReadNothingAndEachReadsWhenClickedAlone()
	{
		// The first log, three times over: the Alchemical Hydra then Amoxliatl, clicked inside one tick. The Hydra's
		// numbers land, Amoxliatl's land a cycle later, and only then does the Hydra draw, with Amoxliatl's time and
		// kills in the game's own header; Amoxliatl draws the cycle after. 2.8.3 filed 0:30 under the Hydra.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 169);
		landing = false;
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 50);
		page("Alchemical Hydra", "Obtained: <col=ffff00>0/11</col>", "Personal Best: <col=ffffff>0:30.00</col>",
			"Alchemical Hydra kills: <col=ffffff>420</col>");
		drawsNow(3);
		page("Amoxliatl", "Obtained: <col=ffff00>3/4</col>", "Personal Best: <col=ffffff>0:30.00</col>",
			"Amoxliatl kills: <col=ffffff>420</col>");
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		// A tick later the Hydra alone, as the log goes on: its own time.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 169);
		page("Alchemical Hydra", "Obtained: <col=ffff00>10/11</col>", "Personal Best: <col=ffffff>1:41.40</col>");
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.alchemical hydra", 101.4);
		verify(configManager, times(1)).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void aPageSentNothingNeverTakesTheNextPagesTimeHoweverFastTheClicks()
	{
		// The first log's Abyssal Sire then Amoxliatl: nothing lands for the first page, Amoxliatl's time lands in the
		// cycle the first page draws, and Amoxliatl draws the cycle after. A page with the line and no record stands
		// where the Sire does: it is sent nothing and is drawn showing the next page's time. 2.8.3 kept it there.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 50);
		page("Alchemical Hydra", "Obtained: <col=ffff00>0/11</col>", "Personal Best: <col=ffffff>0:30.00</col>");
		drawsNow(3);
		page("Amoxliatl", "Obtained: <col=ffff00>3/4</col>", "Personal Best: <col=ffffff>0:30.00</col>");
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void twoRaidsClickedInsideATickReadNothingAndEachReadsWhenClickedAlone()
	{
		// The second log, on the Raids tab: the Theatre of Blood then the Tombs of Amascut inside one tick. Both pages'
		// numbers land in one cycle and both pages draw in it, the Theatre under the Tombs' counts. 2.8.3 read the
		// Tombs' 19 Entry completions as the Theatre's.
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 5);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 6);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 430);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 101);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 19);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 263);
		page("Theatre of Blood", "Obtained: <col=ffff00>0/17</col>", "Theatre of Blood completions: <col=ffffff>263</col>",
			"Theatre of Blood (Entry) completions: <col=ffffff>19</col>");
		drawsNow(1);
		page("Tombs of Amascut", "Obtained: <col=ffff00>16/27</col>", "Tombs of Amascut completions: <col=ffffff>263</col>",
			"Tombs of Amascut (Entry) completions: <col=ffffff>19</col>");
		drawsNow(3);
		ticks();
		assertTrue(counts.toString(), counts.isEmpty());

		// Each clicked alone, as the same log has them: its numbers land in the cycle it draws.
		page("Theatre of Blood", "Obtained: <col=ffff00>7/17</col>", "Theatre of Blood completions: <col=ffffff>430</col>",
			"Theatre of Blood (Entry) completions: <col=ffffff>6</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 5);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 6);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 430);
		drawsNow(3);
		ticks();
		page("Tombs of Amascut", "Obtained: <col=ffff00>16/27</col>", "Tombs of Amascut completions: <col=ffffff>263</col>",
			"Tombs of Amascut (Entry) completions: <col=ffffff>19</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 101);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 19);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 263);
		drawsNow(3);
		ticks();
		assertEquals(java.util.List.of("Theatre of Blood (Entry) completions=6", "Tombs of Amascut (Entry) completions=19"),
			counts);
	}

	@Test
	public void aPageDrawnWithinTheQuietCyclesOfAnotherReadsNothingAndOneJustPastThemReads()
	{
		// Two pages drawn QUIET cycles apart: neither. The first one's wait ends when the second draws.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		drawsNow(1);
		cycle += ClogPbs.QUIET - 1;
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		drawsNow(1);
		ticks();
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		// One cycle more between them and both read: the first as the second draws, the second at the next tick.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		drawsNow(1);
		cycle += ClogPbs.QUIET;
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		drawsNow(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.vorkath", 62.4);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.zulrah"), any());
		ticks();
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.zulrah", 55.8);
	}

	@Test
	public void aLineThatChangesAgainWhileItsPageWaitsOrAPageNoTickSettledReadsNothing()
	{
		// Another value for the line before the wait ends: nothing says which is the page's.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		drawsNow(1);
		cycle++;
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 105);
		ticks();
		// A page whose wait no tick ended (a logout inside the tick) is dropped, not kept for whoever logs in next.
		page("Zulrah", "Obtained: <col=ffff00>4/9</col>", "Personal Best: <col=ffffff>0:55.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		drawsNow(1);
		cycle += 5000;
		pbs.settle(cycle);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void twoValuesForOneLineAboutOneDrawAreNobodys()
	{
		// In one cycle, or one in each of the draw's two cycles: nothing says which page each was sent for.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 169);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 50);
		page("Alchemical Hydra", "Obtained: <col=ffff00>10/11</col>", "Personal Best: <col=ffffff>0:30.00</col>");
		draws(1);
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 169);
		landing = false;
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 98);
		page("Araxxor", "Obtained: <col=ffff00>10/10</col>", "Personal Best: <col=ffffff>0:58.80</col>");
		drawsNow(1);
		ticks();
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
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
	public void aPageNeverTakesTheTimeThePageBeforeHolds()
	{
		// Medium CoX, 11/6 on 2.7.7: Amoxliatl's 0:28.80, then Araxxor, a page they have no record on, which the game
		// still draws with that time, and the time arriving just before Araxxor drew. The timing alone took it.
		page("Amoxliatl", "Obtained: <col=ffff00>2/5</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 48);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.amoxliatl", 28.8);
		when(configManager.getRSProfileConfiguration("killclog", "pagepb.amoxliatl", double.class)).thenReturn(28.8);
		page("Araxxor", "Obtained: <col=ffff00>0/9</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 48);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.araxxor"), any());

		// Another player's way, 10/26 on 2.7.2: up to the Alchemical Hydra, the time the page before holds from its kills.
		when(configManager.getRSProfileConfiguration("killclog", "pagepb.amoxliatl", double.class)).thenReturn(null);
		when(configManager.getRSProfileConfiguration("killclog", "livepb.amoxliatl", double.class)).thenReturn(28.8);
		page("Amoxliatl", "Obtained: <col=ffff00>2/5</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		draws(1);
		page("Alchemical Hydra", "Obtained: <col=ffff00>3/11</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 48);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.alchemical hydra"), any());

		// A time of the page's own, different from the page before's, is read as ever.
		page("Araxxor", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 168);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.araxxor", 100.8);
	}

	@Test
	public void thePageBeforesTimeCountsWithoutItsTenthsAndFromTheAdventureLog()
	{
		// Kills with precise timing off keep "0:29" for Amoxliatl's 0:28.80: the same record.
		when(configManager.getRSProfileConfiguration("killclog", "livepb.amoxliatl", double.class)).thenReturn(29.0);
		page("Amoxliatl", "Obtained: <col=ffff00>2/5</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		draws(1);
		page("Araxxor", "Obtained: <col=ffff00>0/9</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 48);
		draws(1);
		// Held only from the Adventure Log.
		when(configManager.getRSProfileConfiguration("killclog", "livepb.amoxliatl", double.class)).thenReturn(null);
		when(configManager.getRSProfileConfiguration("killclog", "advlogpb.araxxor", double.class)).thenReturn(28.8);
		page("Alchemical Hydra", "Obtained: <col=ffff00>3/11</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 48);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.araxxor"), any());
		verify(configManager, never()).setRSProfileConfiguration(eq("killclog"), eq("pagepb.alchemical hydra"), any());

		// One tick apart is another time: Amoxliatl 0:28.80, then Araxxor's own 0:29.40.
		when(configManager.getRSProfileConfiguration("killclog", "pagepb.amoxliatl", double.class)).thenReturn(28.8);
		page("Amoxliatl", "Obtained: <col=ffff00>2/5</col>", "Personal Best: <col=ffffff>0:28.80</col>");
		draws(1);
		page("Araxxor", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>0:29.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 49);
		draws(1);
		verify(configManager).setRSProfileConfiguration("killclog", "pagepb.araxxor", 29.4);
	}

	@Test
	public void aTimeThatLandsAtAnyOtherMomentIsNobodys()
	{
		// A value that lands with no page drawing just after it (the log closed between the two) goes unused, and
		// the next page the player opens, one they have no record on, never takes it.
		page("Vorkath", "Obtained: <col=ffff00>5/6</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 104);
		cycle += 5;
		draws(1);
		page("Zulrah", "Obtained: <col=ffff00>0/9</col>", "Personal Best: <col=ffffff>1:02.40</col>");
		draws(1);
		// A value that lands while a page is on screen isn't that page's either.
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 93);
		draws(1);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		// A second page drawn right behind the first: the value is neither's.
		page("Araxxor", "Obtained: <col=ffff00>2/9</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 168);
		cycle++;
		drawsNow(1);
		page("Alchemical Hydra", "Obtained: <col=ffff00>0/8</col>", "Personal Best: <col=ffffff>1:40.80</col>");
		drawsNow(1);
		ticks();
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
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
	@Test
	public void aPagesCountIsReadAsItsPageOpensFromTheLineItsVarpCarries()
	{
		page("Revenants", "Obtained: <col=ffff00>4/14</col>", "Revenant kills: <col=ffffff>1,204</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1204);
		draws(3);
		// The raid's own count is the hiscores', its hard mode's too: only the Entry line, from the second varp.
		page("Theatre of Blood", "Obtained: <col=ffff00>5/17</col>",
			"Theatre of Blood (Hard) completions: <col=ffffff>3</col>",
			"Theatre of Blood (Entry) completions: <col=ffffff>12</col>", "Theatre of Blood completions: <col=ffffff>40</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 40);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 12);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 3);
		draws(1);
		// Demonic Gorillas, never the Tortured ones on the same page.
		page("Glough's Experiments", "Obtained: <col=ffff00>2/5</col>", "Tortured Gorilla kills: <col=ffffff>9</col>",
			"Demonic Gorilla kills: <col=ffffff>310</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 9);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 310);
		draws(1);
		assertEquals(java.util.List.of("Revenant kills=1204", "Theatre of Blood (Entry) completions=12",
			"Demonic Gorilla kills=310"), counts);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void aCountTheServerDidntSendJustBeforeItsPageDrewIsNobodys()
	{
		// The page before's count still in the varp: none sent for this page, so nothing is read.
		page("Abyssal Sire", "Obtained: <col=ffff00>6/9</col>", "Abyssal Sire kills: <col=ffffff>420</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 420);
		draws(3);
		page("Revenants", "Obtained: <col=ffff00>0/14</col>", "Revenant kills: <col=ffffff>420</col>");
		draws(3);
		// One that lands while a page is on screen, or long before it drew.
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 3);
		draws(1);
		page("Tormented Demons", "Obtained: <col=ffff00>1/4</col>", "Tormented Demon kills: <col=ffffff>88</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 88);
		cycle += 5;
		draws(1);
		// A host's log in their house.
		page("Hunter Guild", "Obtained: <col=ffff00>3/13</col>", "Rumours Completed: <col=ffffff>150</col>");
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 150);
		draws(1);
		assertTrue(counts.toString(), counts.isEmpty());

		// The player's own, sent just before it drew.
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);
		page("Revenants", "Obtained: <col=ffff00>0/14</col>", "Revenant kills: <col=ffffff>420</col>");
		draws(1);
		page("Hunter Guild", "Obtained: <col=ffff00>3/13</col>", "Rumours Completed: <col=ffffff>151</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 151);
		draws(1);
		assertEquals(java.util.List.of("Rumours Completed=151"), counts);
	}

	@Test
	public void aTabsFirstPageAndARowClickedRightAfterItReadNothing()
	{
		// The second log's Other tab: Glough's Experiments and Hunter Guild drawn by turns in one cycle, the first under
		// the Guild's 188 rumours as "Tortured Gorilla kills: 188".
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 2210);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 0);
		landing = false;
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 0);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 188);
		cycle++;
		for (int turn = 0; turn < 2; turn++)
		{
			page("Glough's Experiments", "Obtained: <col=ffff00>0/6</col>", "Demonic Gorilla kills: <col=ffffff>0</col>",
				"Tortured Gorilla kills: <col=ffffff>188</col>");
			drawsNow(1);
			page("Hunter Guild", "Obtained: <col=ffff00>5/6</col>", "Rumours Completed: <col=ffffff>188</col>");
			drawsNow(1);
		}
		ticks();
		assertTrue(counts.toString(), counts.isEmpty());
	}

	@Test
	public void aCountIsNeverThePageBeforesNumber()
	{
		// The Abyssal Sire drawn at 1,204 kills, then Tormented Demons, with 1,204 landing just before it drew.
		page("Abyssal Sire", "Obtained: <col=ffff00>6/9</col>", "Abyssal Sire kills: <col=ffffff>1,204</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1204);
		draws(1);
		page("Tormented Demons", "Obtained: <col=ffff00>1/4</col>", "Tormented Demon kills: <col=ffffff>1,204</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1204);
		draws(1);
		assertTrue(counts.toString(), counts.isEmpty());

		// The Theatre's Entry count as the log holds it, its page drawn before that count came: the Tombs never take it.
		held.put("Theatre of Blood (Entry) completions", 6);
		page("Theatre of Blood", "Obtained: <col=ffff00>5/17</col>",
			"Theatre of Blood (Entry) completions: <col=ffffff>0</col>");
		draws(1);
		page("Tombs of Amascut", "Obtained: <col=ffff00>3/20</col>",
			"Tombs of Amascut (Entry) completions: <col=ffffff>6</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 6);
		draws(1);
		assertTrue(counts.toString(), counts.isEmpty());

		// A number of the page's own is read as ever.
		page("Theatre of Blood", "Obtained: <col=ffff00>5/17</col>",
			"Theatre of Blood (Entry) completions: <col=ffffff>7</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 7);
		draws(1);
		page("Tombs of Amascut", "Obtained: <col=ffff00>3/20</col>",
			"Tombs of Amascut (Entry) completions: <col=ffffff>19</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 19);
		draws(1);
		assertEquals(java.util.List.of("Theatre of Blood (Entry) completions=7",
			"Tombs of Amascut (Entry) completions=19"), counts);
	}

	@Test
	public void twoPointEightPointTwosCountsAreReadOffTheirPagesThroughTheReader()
	{
		// Each of 11/13's rows from the line its varp carries, one page after another as a player browses them.
		page("Mastering Mixology", "Obtained: <col=ffff00>3/7</col>", "Orders fulfilled: <col=ffffff>2,501</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 2501);
		draws(2);
		page("Giants' Foundry", "Obtained: <col=ffff00>1/6</col>", "Swords created: <col=ffffff>0</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 0);
		draws(1);
		page("Hallowed Sepulchre", "Obtained: <col=ffff00>2/11</col>",
			"Grand Hallowed Coffins opened: <col=ffffff>77</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 77);
		draws(1);
		page("Soul Wars", "Obtained: <col=ffff00>4/12</col>", "Spoils of war opened: <col=ffffff>40</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 40);
		draws(1);
		page("Wintertodt", "Obtained: <col=ffff00>8/13</col>", "Wintertodt kills: <col=ffffff>500</col>",
			"Rewards claimed: <col=ffffff>310</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 500);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 310);
		draws(1);
		page("Tempoross", "Obtained: <col=ffff00>5/8</col>", "Tempoross kills: <col=ffffff>420</col>",
			"Reward permits claimed: <col=ffffff>900</col>", "Personal Best: <col=ffffff>3:10.20</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 420);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 900);
		draws(1);
		page("Guardians of the Rift", "Obtained: <col=ffff00>6/12</col>", "Rifts closed: <col=ffffff>1,800</col>",
			"Rifts searches: <col=ffffff>1,402</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1800);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 1402);
		draws(1);
		// Doom's page reads its deepest delve, but never its Personal Best, which doesn't say which delve it times.
		page("Doom of Mokhaiotl", "Obtained: <col=ffff00>2/7</col>", "Total delves: <col=ffffff>40</col>",
			"Deep delves: <col=ffffff>12</col>", "Deepest delve: <col=ffffff>9</col>",
			"Personal Best: <col=ffffff>2:01.80</col>");
		sends(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, 203);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 40);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 12);
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT3, 9);
		draws(1);
		// Gnome Restaurant's deliveries aren't on these varps, so whatever lands there is never theirs.
		page("Gnome Restaurant", "Obtained: <col=ffff00>3/9</col>", "Gnome restaurant hard deliveries: <col=ffffff>31</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 0);
		draws(1);
		assertEquals(java.util.List.of("Orders fulfilled=2501", "Swords created=0", "Grand Hallowed Coffins opened=77",
			"Spoils of war opened=40", "Rewards claimed=310", "Reward permits claimed=900", "Rifts searches=1402",
			"Deepest delve=9"), counts);
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void anUnsettledPageStillCountsAsThePageBefore()
	{
		// The Nightmare's page reads nothing of its own, but its count line is still what the next page compares.
		page("Revenants", "Obtained: <col=ffff00>4/14</col>", "Revenant kills: <col=ffffff>50</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 50);
		draws(1);
		page("The Nightmare", "Obtained: <col=ffff00>2/9</col>", "The Nightmare kills: <col=ffffff>1,204</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1204);
		draws(1);
		page("Tormented Demons", "Obtained: <col=ffff00>1/4</col>", "Tormented Demon kills: <col=ffffff>1,204</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT, 1204);
		draws(1);
		assertEquals(java.util.List.of("Revenant kills=50"), counts);
	}

	@Test
	public void thePageBeforesHeldCountGuardsOnlyItsOwnLine()
	{
		// The Revenants' kills ride the first count line; Demonic Gorilla kills the second, so 57 there is the page's own.
		held.put("Revenant kills", 57);
		page("Revenants", "Obtained: <col=ffff00>4/14</col>", "Revenant kills: <col=ffffff>57</col>");
		draws(1);
		page("Glough's Experiments", "Obtained: <col=ffff00>2/5</col>", "Demonic Gorilla kills: <col=ffffff>57</col>");
		sends(VarPlayerID.COLLECTION_CATEGORY_COUNT2, 57);
		draws(1);
		assertEquals(java.util.List.of("Demonic Gorilla kills=57"), counts);
	}
}
