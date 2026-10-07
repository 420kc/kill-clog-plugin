package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class WorldSessionTest
{
	private final AtomicReference<String> mode = new AtomicReference<>();
	private final AtomicReference<String> active = new AtomicReference<>();
	private final LocalClogCache main = mock(LocalClogCache.class);
	private final List<String> openedFor = new ArrayList<>();
	private final List<LocalClogCache> wired = new ArrayList<>();
	private final WorldSession session = new WorldSession(mode::get, active::get, () -> main, game ->
	{
		openedFor.add(game);
		return mock(LocalClogCache.class);
	}, wired::add);
	private final List<String> shown = new ArrayList<>();
	private final WorldSession.Shown panel = (league, log, running) ->
		shown.add(league + " " + (log != null ? "log" : "-") + " " + running);

	@Test
	public void aMainWorldSettlesOnItsTenthTickAlone()
	{
		mode.set(GameMode.MAIN);
		for (int i = 1; i < WorldSession.SETTLED_TICKS; i++)
		{
			assertFalse(session.tick());
		}
		assertTrue(session.tick());
		assertTrue(session.mainSettled());
		assertFalse("only the tick it settles on", session.tick());
	}

	@Test
	public void anUnknownWorldOrAnUnsettleStartsTheCountOver()
	{
		mode.set(GameMode.MAIN);
		for (int i = 0; i < 5; i++)
		{
			session.tick();
		}
		mode.set(null);
		assertFalse(session.tick());
		assertFalse(session.settled());

		mode.set(GameMode.MAIN);
		for (int i = 0; i < 5; i++)
		{
			session.tick();
		}
		session.unsettle();
		for (int i = 1; i < WorldSession.SETTLED_TICKS; i++)
		{
			assertFalse(session.tick());
		}
		assertTrue(session.tick());
	}

	@Test
	public void aLeagueWorldSettlesWithoutTheMainGamesSignal()
	{
		mode.set("leagues-vi");
		for (int i = 0; i < WorldSession.SETTLED_TICKS + 2; i++)
		{
			assertFalse(session.tick());
		}
		assertTrue(session.settled());
		assertFalse(session.mainSettled());
	}

	@Test
	public void aLeaguesStoreOpensOnceAndClosesWhenAnotherRuns()
	{
		assertSame(main, session.cacheFor(GameMode.MAIN));
		LocalClogCache first = session.cacheFor("leagues-v");
		assertSame(first, session.cacheFor("leagues-v"));
		assertEquals(Collections.singletonList("leagues-v"), openedFor);
		assertEquals(Collections.singletonList(first), wired);

		LocalClogCache second = session.cacheFor("leagues-vi");
		verify(first).close();
		verify(second, never()).close();
		assertEquals(Arrays.asList(first, second), wired);

		session.close();
		verify(second).close();
		// Forgotten once closed: the same League opens a fresh store.
		assertNotSame(second, session.cacheFor("leagues-vi"));
	}

	@Test
	public void capturesGoToTheWorldsOwnGame()
	{
		assertNull(session.captureCache());
		mode.set(GameMode.MAIN);
		assertSame(main, session.captureCache());
		mode.set("leagues-vi");
		assertSame(session.cacheFor("leagues-vi"), session.captureCache());
	}

	@Test
	public void thePanelHearsOnlyOfAChangeAndShowAgainForcesTheNext()
	{
		mode.set(GameMode.MAIN);
		session.follow(panel);
		session.follow(panel);
		assertEquals(Collections.singletonList("null - null"), shown);

		session.showAgain();
		session.follow(panel);
		assertEquals(2, shown.size());

		mode.set("leagues-vi");
		active.set("leagues-vi");
		session.follow(panel);
		assertEquals("leagues-vi log leagues-vi", shown.get(2));
	}

	@Test
	public void aLogoutAfterALeagueStartsHandsBackTheMainGame()
	{
		mode.set(GameMode.MAIN);
		session.follow(panel);
		// The League starts while the player stands on a main world: the panel hears of it.
		active.set("leagues-vi");
		session.follow(panel);
		mode.set(null);
		session.follow(panel);
		assertEquals(Arrays.asList("null - null", "null - leagues-vi", "null - leagues-vi"), shown);
	}
}
