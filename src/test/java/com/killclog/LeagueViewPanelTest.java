package com.killclog;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.components.IconTextField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real panel following the world's game; no network or game client is started. */
public class LeagueViewPanelTest
{
	private final KillClogConfig config = new KillClogConfig()
	{
		@Override
		public boolean showLeaderboardSelector()
		{
			return true;
		}
	};
	private final HiscoreService hiscores = mock(HiscoreService.class);
	private final ClogService clogs = mock(ClogService.class);
	private final RuneProfileService runeProfile = mock(RuneProfileService.class);
	private final KillclogService killclog = mock(KillclogService.class);
	private KillClogPanel panel;
	private LookupSession session;

	@Before
	public void createPanel() throws Exception
	{
		when(clogs.warmCatalog()).thenReturn(new CompletableFuture<>());
		// Every remote read stays pending: the tests watch which ones start.
		when(hiscores.lookup(any(), any())).thenReturn(new CompletableFuture<>());
		when(hiscores.lookupTable(any(), any())).thenReturn(new CompletableFuture<>());
		when(clogs.lookup(any())).thenReturn(new CompletableFuture<>());
		when(runeProfile.lookup(any())).thenReturn(new CompletableFuture<>());
		when(runeProfile.lookupClog(any())).thenReturn(new CompletableFuture<>());
		when(killclog.lookupClog(any())).thenReturn(new CompletableFuture<>());
		when(killclog.lookupClog(any(), any())).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		SwingUtilities.invokeAndWait(() ->
		{
			panel = new KillClogPanel(hiscores, clogs, runeProfile, killclog, config, mock(ConfigManager.class), sprites,
				mock(ItemManager.class), mock(ClientThread.class), new SkillIconManager(), mock(Client.class));
			session = field(panel, "lookupSession", LookupSession.class);
		});
	}

	@After
	public void stopPanel() throws Exception
	{
		if (panel != null)
		{
			SwingUtilities.invokeAndWait(panel::shutdown);
		}
	}

	@Test
	public void aWorldChangeReadsTheShownPlayerAgainInTheNewGame() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			field(panel, "searchBar", IconTextField.class).setText("Friend");
			panel.doLookup();
			// Still loading when the world changes, and mid-way into a comparison entry.
			setField(field(panel, "searchRowController", SearchRowController.class), "compareEntryMode", true);
			panel.followWorld("demonic-pacts", null, "demonic-pacts");
		});
		verify(hiscores).lookup("Friend", null);
		verify(hiscores).lookupTable("Friend", HiscoreService.LEAGUE_TABLE);
		verify(killclog).lookupClog("Friend", "demonic-pacts");
		assertEquals("demonic-pacts", session.league());
		LookupFanout comparison = field(field(panel, "comparison", ComparisonController.class), "fanout", LookupFanout.class);
		assertEquals("the comparison reads the same game", "demonic-pacts", comparison.getLeague());
		assertFalse("a comparison entry ends",
			field(field(panel, "searchRowController", SearchRowController.class), "compareEntryMode", Boolean.class));
	}

	@Test
	public void aWorldChangeEndsAComparison() throws Exception
	{
		ComparisonController comparison = field(panel, "comparison", ComparisonController.class);
		SwingUtilities.invokeAndWait(() ->
		{
			field(panel, "searchBar", IconTextField.class).setText("Friend");
			panel.doLookup();
			setField(comparison, "comparisonMode", true);
			panel.followWorld("demonic-pacts", null, "demonic-pacts");
			assertFalse("the other side would still be the last game's", comparison.isComparisonMode());
		});
		verify(hiscores).lookupTable("Friend", HiscoreService.LEAGUE_TABLE);
	}

	@Test
	public void theLeaguesSwitchShowsWhileALeagueRunsAndFlipsUntilTheWorldChanges() throws Exception
	{
		javax.swing.JLabel toggle = field(panel, "leagueSwitch", javax.swing.JLabel.class);
		javax.swing.ImageIcon lit = field(panel, "leagueBadge", javax.swing.ImageIcon.class);
		java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(toggle, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, 5, 5, 1, false);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.followWorld(null, null, null);
			assertFalse("no League running, no switch", toggle.isVisible());

			field(panel, "searchBar", IconTextField.class).setText("Friend");
			panel.doLookup();
			panel.followWorld(null, null, "demonic-pacts");
			assertTrue(toggle.isVisible());
			assertNull("a main world reads the main game", session.league());
			assertFalse(lit == toggle.getIcon());
			assertEquals("Main game stats", toggle.getToolTipText());

			for (java.awt.event.MouseListener listener : toggle.getMouseListeners())
			{
				listener.mousePressed(press);
			}
			assertEquals("demonic-pacts", session.league());
			LookupFanout comparison = field(field(panel, "comparison", ComparisonController.class), "fanout", LookupFanout.class);
			assertEquals("the comparison follows a flip too", "demonic-pacts", comparison.getLeague());
			assertTrue(lit == toggle.getIcon());
			assertEquals("League stats", toggle.getToolTipText());

			LocalClogCache own = mock(LocalClogCache.class);
			panel.followWorld("demonic-pacts", own, "demonic-pacts");
			assertEquals("a League world reads its League", "demonic-pacts", session.league());
			for (java.awt.event.MouseListener listener : toggle.getMouseListeners())
			{
				listener.mousePressed(press);
			}
			assertNull("flipped back to the main game", session.league());
			panel.followWorld("demonic-pacts", own, "demonic-pacts");
			assertEquals("a world change ends the flip", "demonic-pacts", session.league());
		});
		// Read again on each switch into the League, and on the flip back to main.
		verify(hiscores, org.mockito.Mockito.times(2)).lookupTable("Friend", HiscoreService.LEAGUE_TABLE);
		verify(hiscores, org.mockito.Mockito.times(2)).lookup("Friend", null);
	}

	@Test
	public void withNoLeagueRunningTheSearchRowKeepsItsLayout() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			javax.swing.JPanel row = field(panel, "searchRow", javax.swing.JPanel.class);
			IconTextField searchBar = field(panel, "searchBar", IconTextField.class);
			row.setSize(200, 30);
			panel.followWorld(null, null, null);
			row.doLayout();
			assertEquals("the search bar keeps the whole row", 200, searchBar.getWidth());
			panel.followWorld(null, null, "demonic-pacts");
			row.doLayout();
			assertEquals(178, searchBar.getWidth());
		});
	}

	@Test
	public void setupShowsOnlyForALogThisClientKeeps() throws Exception
	{
		when(clogs.lookupRsn(any())).thenReturn(new CompletableFuture<>());
		javax.swing.JComponent notice = field(panel, "clogNotice", javax.swing.JComponent.class);
		javax.swing.JLabel toggle = field(panel, "leagueSwitch", javax.swing.JLabel.class);
		java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(toggle, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, 5, 5, 1, false);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.setLoggedInPlayer("Me", AccountType.REGULAR);
			panel.followWorld(null, null, "demonic-pacts");
			panel.onClogResult("Me", null, true, session.getLookupVersion());
			assertTrue("your main log is set up here", notice.isVisible());

			for (java.awt.event.MouseListener listener : toggle.getMouseListeners())
			{
				listener.mousePressed(press);
			}
			panel.onClogResult("Me", null, true, session.getLookupVersion());
			assertFalse("a League read from a main world has nothing to set up here", notice.isVisible());

			panel.followWorld("demonic-pacts", mock(LocalClogCache.class), "demonic-pacts");
			panel.onClogResult("Me", null, true, session.getLookupVersion());
			assertTrue("on the League's own world, its log is set up here", notice.isVisible());
		});
	}

	@Test
	public void yourTooltipsInALeagueFromAnotherWorldShowItsEmptyLog() throws Exception
	{
		ClogIndex index = new ClogIndex();
		index.publishForTest(Map.of("zulrah", java.util.List.of(1, 2, 3)), Map.of());
		Cells cells = field(panel, "cells", Cells.class);
		Map<?, ?> tooltips = field(cells, "tooltipDataMap", Map.class);
		java.util.Map<String, Integer> kills = new java.util.HashMap<>();
		kills.put("Zulrah", 57);
		HiscoreResult row = new HiscoreResult(AccountType.REGULAR, kills, new java.util.HashMap<>(), new java.util.HashMap<>(),
			new java.util.HashMap<>(), new java.util.HashMap<>(), 100, 1000L, 30, 1);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.setClogIndex(index);
			session.adoptState(row, null, null, "Me");
			cells.rebuildPrimaryTooltips("Me");
			assertTrue("your main log is waiting for setup", tooltips.isEmpty());
			session.readLeague("demonic-pacts", null);
			cells.rebuildPrimaryTooltips("Me");
			assertFalse("a League read from another world shows its empty log, like anyone's", tooltips.isEmpty());
		});
	}

	@Test
	public void yourHoverCardsOfferSetupOnlyForALogThisClientKeeps() throws Exception
	{
		Method summary = KillClogPanel.class.getDeclaredMethod("buildClogSummaryTooltip",
			javax.swing.JComponent.class, HiscoreResult.class, ClogResult.class, String.class, String.class);
		summary.setAccessible(true);
		Method cell = KillClogPanel.class.getDeclaredMethod("makeSpriteTooltip", javax.swing.JLabel.class, TooltipData.class,
			int.class, String.class, boolean.class, HiscoreResult.class, String.class);
		cell.setAccessible(true);
		HiscoreResult row = new HiscoreResult(AccountType.REGULAR, new java.util.HashMap<>(), new java.util.HashMap<>(),
			new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), 100, 1000L, 30, 1);
		javax.swing.JLabel owner = new javax.swing.JLabel();
		new javax.swing.JPanel().add(owner);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				panel.setLoggedInPlayer("Me", AccountType.REGULAR);
				assertTrue("your main log offers setup",
					field(summary.invoke(panel, owner, row, null, "Me", null), "firstTimeSetup", Boolean.class));
				String mainNotice = field(cell.invoke(panel, owner, null, 4, "Zulrah", false, row, "Me"), "notice", String.class);
				session.readLeague("demonic-pacts", null);
				assertFalse("a League read from another world never does",
					field(summary.invoke(panel, owner, row, null, "Me", null), "firstTimeSetup", Boolean.class));
				String leagueNotice = field(cell.invoke(panel, owner, null, 4, "Zulrah", false, row, "Me"), "notice", String.class);
				assertTrue(mainNotice, mainNotice.contains("setup"));
				assertFalse(leagueNotice, leagueNotice.contains("setup"));
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	@Test
	public void everyNameInALeagueViewWearsTheLeaguesBadge() throws Exception
	{
		javax.swing.ImageIcon badge = field(panel, "leagueBadge", javax.swing.ImageIcon.class);
		javax.swing.JLabel name = new javax.swing.JLabel();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.applyBadge(name, null);
			assertNull(name.getIcon());
			panel.followWorld("demonic-pacts", null, "demonic-pacts");
			panel.applyBadge(name, null);
			assertTrue(badge == name.getIcon());
		});
	}

	@Test
	public void aDeadHardcoresBoardHidesTodaysLogAndSaysSo() throws Exception
	{
		PanelStatusRow status = field(panel, "statusRow", PanelStatusRow.class);
		RankSelector ranks = field(panel, "rankSelector", RankSelector.class);
		Method update = KillClogPanel.class.getDeclaredMethod("updateRankPlayers");
		update.setAccessible(true);
		HiscoreResult now = RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		HiscoreResult atDeath = RankSelectorTest.result(AccountType.REGULAR, HiscoreTable.STANDARD, 50, 5000);
		ClogResult log = new ClogResult("Friend", Map.of(), Map.of(), Map.of(), null, null);
		when(hiscores.lookupRanks(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
		when(hiscores.lookupRanks("Friend", RankLeaderboard.HARDCORE))
			.thenReturn(CompletableFuture.completedFuture(atDeath));
		when(hiscores.notOnBoard("Friend", RankLeaderboard.ULTIMATE)).thenReturn(true);
		JLabel totalLevel = field(panel, "totalLvlCell", JLabel.class);
		SwingUtilities.invokeAndWait(() ->
		{
			session.adoptState(now, log, null, "Friend");
			invoke(update);
			ranks.select(RankLeaderboard.HARDCORE);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(5000, session.getHiscoreResult().getTotalXp());
			assertNull(session.getClogResult());
			assertEquals("Hardcore collection log not recorded", status.statusText());
			ranks.select(RankLeaderboard.ULTIMATE);
			assertEquals(-1, session.getHiscoreResult().getKc("Zulrah"));
			assertNull(session.getClogResult());
			assertEquals("Not on this leaderboard", status.statusText());
			assertEquals("--", totalLevel.getText().trim());
			ranks.select(RankLeaderboard.IRONMAN);
			assertSame(log, session.getClogResult());
			assertEquals(" ", status.statusText());
			assertEquals("2277", totalLevel.getText().trim());
		});
	}

	@Test
	public void aComparisonReadsEachPlayersOwnBoard() throws Exception
	{
		PanelStatusRow status = field(panel, "statusRow", PanelStatusRow.class);
		RankSelector ranks = field(panel, "rankSelector", RankSelector.class);
		ComparisonController comparison = field(panel, "comparison", ComparisonController.class);
		Method update = KillClogPanel.class.getDeclaredMethod("updateRankPlayers");
		update.setAccessible(true);
		Constructor<?> compared = Class.forName("com.killclog.ComparisonController$ComparedPlayer").getDeclaredConstructors()[0];
		compared.setAccessible(true);
		HiscoreResult buck = RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		HiscoreResult exo = RankSelectorTest.result(AccountType.REGULAR, HiscoreTable.STANDARD, 20, 20000);
		when(hiscores.lookupRanks(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
		when(hiscores.lookupRanks("Ye Ol Buck", RankLeaderboard.HARDCORE)).thenReturn(CompletableFuture.completedFuture(
			RankSelectorTest.result(AccountType.REGULAR, HiscoreTable.STANDARD, 50, 5000)));
		when(hiscores.notOnBoard("Exo", RankLeaderboard.IRONMAN)).thenReturn(true);
		SwingUtilities.invokeAndWait(() ->
		{
			session.adoptState(buck, null, null, "Ye Ol Buck");
			invoke(update);
			ranks.select(RankLeaderboard.HARDCORE);
			assertTrue(session.getHiscoreResult().isFrozen());
			setField(comparison, "compared", newInstance(compared, exo));
			setField(comparison, "comparisonMode", true);
			panel.onComparisonEnter("Exo");
			// An Ironman meets a main on their own boards; the selector steps aside.
			assertFalse(ranks.isVisible());
			assertSame(buck, session.getHiscoreResult());
			assertSame(exo, comparison.getCompareHiscoreResult());
			assertEquals(" ", status.statusText());
			setField(comparison, "comparisonMode", false);
			panel.onComparisonExit();
			assertTrue(ranks.isVisible());
			assertEquals(RankLeaderboard.IRONMAN, ranks.active());
		});
	}

	private static Object newInstance(Constructor<?> compared, HiscoreResult hiscore)
	{
		try
		{
			return compared.newInstance(1, hiscore, null, null, "Exo");
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void virtualLevelsKeepBothPlayersLevelsInAComparison() throws Exception
	{
		ComparisonController comparison = field(panel, "comparison", ComparisonController.class);
		JLabel combat = field(panel, "combatCell", JLabel.class);
		JLabel total = field(panel, "totalLvlCell", JLabel.class);
		Constructor<?> compared = Class.forName("com.killclog.ComparisonController$ComparedPlayer").getDeclaredConstructors()[0];
		compared.setAccessible(true);
		Object rival = compared.newInstance(1, new HiscoreResult(AccountType.REGULAR, Map.of(), Map.of(), Map.of(), Map.of(),
			Map.of(), 1500, 50000, 100, 30), null, null, "Rival");
		SwingUtilities.invokeAndWait(() ->
		{
			session.adoptState(RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000), null, null, "Friend");
			setField(comparison, "compared", rival);
			setField(comparison, "comparisonMode", true);
			panel.onComparisonEnter("Rival");
			panel.onConfigChanged("virtualLevels");
			assertTrue(combat.getText(), combat.getText().contains("126") && combat.getText().contains("100"));
			assertTrue(total.getText(), total.getText().contains("2277") && total.getText().contains("1500"));
		});
	}

	@Test
	public void leaderboardsHideInALeagueView() throws Exception
	{
		RankSelector ranks = field(panel, "rankSelector", RankSelector.class);
		Method update = KillClogPanel.class.getDeclaredMethod("updateRankPlayers");
		update.setAccessible(true);
		SwingUtilities.invokeAndWait(() ->
		{
			invoke(update);
			assertTrue(ranks.isVisible());
			panel.followWorld("demonic-pacts", null, "demonic-pacts");
			invoke(update);
			assertFalse(ranks.isVisible());
		});
	}

	@Test
	public void bossPbsComeFromTheViewedGame() throws Exception
	{
		Cells cells = field(panel, "cells", Cells.class);
		ClogResult friend = new ClogResult("Friend", Map.of(), Map.of(), Map.of(), null, null);
		when(killclog.pbText("Friend", "Zulrah")).thenReturn("0:58.20");
		when(killclog.pbText(KillclogService.modeKey("demonic-pacts", "Friend"), "Zulrah")).thenReturn("1:01.20");
		SwingUtilities.invokeAndWait(() ->
		{
			panel.setSelfPb((league, boss) -> league + " " + boss);
			session.adoptState(null, friend, null, "Friend");
			assertEquals("0:58.20", cells.pbFor(false, "Zulrah"));
			assertEquals("null Zulrah", cells.pbFor(true, "Zulrah"));
			session.readLeague("demonic-pacts", null);
			assertEquals("1:01.20", cells.pbFor(false, "Zulrah"));
			assertEquals("demonic-pacts Zulrah", cells.pbFor(true, "Zulrah"));
			session.adoptState(null, null, null, "Friend");
			assertNull(cells.pbFor(false, "Zulrah"));
		});
	}

	private void invoke(Method method)
	{
		try
		{
			method.invoke(panel);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static <T> T field(Object target, String name, Class<T> type)
	{
		try
		{
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(target));
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void setField(Object target, String name, Object value)
	{
		try
		{
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
