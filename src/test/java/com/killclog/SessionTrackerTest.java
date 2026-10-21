package com.killclog;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who is logged in, decided in one place: each login, hop, logout and account switch, as the game hands them over. */
public class SessionTrackerTest
{
	private final Client client = mock(Client.class);
	private final Player player = mock(Player.class);
	private final SessionTracker tracker = new SessionTracker();
	private final List<String> changes = new ArrayList<>();

	{
		tracker.addListener((previous, now) -> changes.add(previous.getNumber() + ">" + now.getNumber() + " " + now.getName()));
	}

	private void game(GameState state, String name, long hash)
	{
		when(client.getGameState()).thenReturn(state);
		when(client.getLocalPlayer()).thenReturn(name == null ? null : player);
		when(player.getName()).thenReturn(name);
		when(client.getAccountHash()).thenReturn(hash);
		tracker.update(client);
	}

	@Test
	public void aLoginBecomesASessionOnlyOnceItsNameAndAccountHaveBothArrived()
	{
		assertFalse(tracker.current().loggedIn());
		game(GameState.LOGGED_IN, null, 7);
		game(GameState.LOGGED_IN, "420 kc", -1);
		assertTrue(changes.isEmpty());
		game(GameState.LOGGED_IN, "420 kc", 7);
		assertEquals(List.of("0>1 420 kc"), changes);
		assertEquals(7, tracker.current().getAccountHash());
		// Every tick after asks again and changes nothing.
		game(GameState.LOGGED_IN, "420 kc", 7);
		assertEquals(1, changes.size());
	}

	@Test
	public void aHopOrADroppedConnectionKeepsTheSessionAndTheLoginScreenEndsIt()
	{
		game(GameState.LOGGED_IN, "420 kc", 7);
		Session login = tracker.current();
		for (GameState state : new GameState[]{GameState.HOPPING, GameState.LOADING, GameState.CONNECTION_LOST})
		{
			game(state, null, -1);
		}
		game(GameState.LOGGED_IN, "420 kc", 7);
		assertTrue("the same login", tracker.isCurrent(login));

		game(GameState.LOGIN_SCREEN, null, -1);
		assertFalse(tracker.isCurrent(login));
		assertFalse(tracker.current().loggedIn());
		assertNull(tracker.current().getName());
		assertEquals(List.of("0>1 420 kc", "1>2 null"), changes);
		// A second login screen is the same logout.
		game(GameState.LOGIN_SCREEN, null, -1);
		assertEquals(2, changes.size());
	}

	@Test
	public void theNextLoginIsANewSessionEvenOnTheSameAccount()
	{
		game(GameState.LOGGED_IN, "420 kc", 7);
		Session first = tracker.current();
		game(GameState.LOGIN_SCREEN, null, -1);
		game(GameState.LOGGED_IN, "420 kc", 7);
		assertFalse("work from the first login is never current again", tracker.isCurrent(first));
		assertEquals(3, tracker.current().getNumber());
	}

	@Test
	public void anotherAccountOrNameIsANewSessionButTheGamesOwnSpellingIsNot()
	{
		game(GameState.LOGGED_IN, "Smokington", 9);
		game(GameState.LOGGED_IN, "Smokington", 10);
		assertEquals(2, tracker.current().getNumber());
		game(GameState.LOGGED_IN, "Kill Clog", 10);
		assertEquals(3, tracker.current().getNumber());
		game(GameState.LOGGED_IN, "Kill Clog", 10);
		assertEquals(3, tracker.current().getNumber());
	}

	@Test
	public void listenersHearEachChangeInTheOrderTheyWereAdded()
	{
		List<String> order = new ArrayList<>();
		tracker.addListener((previous, now) -> order.add("store"));
		tracker.addListener((previous, now) -> order.add("panel"));
		game(GameState.LOGGED_IN, "420 kc", 7);
		assertEquals(List.of("store", "panel"), order);
	}
}
