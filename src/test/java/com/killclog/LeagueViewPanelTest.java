package com.killclog;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
