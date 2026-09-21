package com.killclog;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static com.killclog.LookupTestFixture.*;
import static org.junit.Assert.*;

/** The cached-hiscore branch of a lookup goes through the same display hold. */
public class LookupCachedHoldTest
{
	private final Map<String, CompletableFuture<ClogResult>> clogs = new HashMap<>();
	private final Map<String, ClogResult> cachedClogs = new HashMap<>();
	private final List<String> order = new ArrayList<>();

	private LookupSession session()
	{
		HiscoreService hiscores = new HiscoreService(null, null)
		{
			@Override
			public HiscoreResult getCached(String name)
			{
				return hiscore(5);
			}

			@Override
			public boolean isStale(String name)
			{
				return false;
			}
		};
		ClogService clogService = new ClogService(null, null, null)
		{
			@Override
			public ClogResult getCachedResult(String name)
			{
				return cachedClogs.get(name);
			}

			@Override
			public CompletableFuture<ClogResult> lookup(String name)
			{
				return clogs.computeIfAbsent(name, ignored -> new CompletableFuture<>());
			}
		};
		RuneProfileService runeProfile = new RuneProfileService(null, null, null)
		{
			@Override
			public CompletableFuture<CombatAchievementResult> lookup(String name)
			{
				return new CompletableFuture<>();
			}

			@Override
			public CompletableFuture<ClogResult> lookupClog(String name)
			{
				return CompletableFuture.completedFuture(null);
			}
		};
		LookupSession.Listener listener = (LookupSession.Listener) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{LookupSession.Listener.class},
			(proxy, method, args) ->
			{
				order.add(method.getName());
				return null;
			});
		return new LookupSession(hiscores, clogService, runeProfile, null,
			new KillClogConfig()
			{
			}, listener);
	}

	/** The 600 ms cached reveal is a local one-shot timer; wait it out in real time. */
	private static void awaitCachedReveal() throws Exception
	{
		Thread.sleep(900);
		edt(() ->
		{
		});
	}

	// Cached hiscore + cached clog reveal together, with no hold.
	@Test
	public void cachedHiscoreWithACachedClogIsShownWithoutAHold() throws Exception
	{
		LookupSession session = session();
		cachedClogs.put("Red", clog("Red"));
		edt(() -> session.start("Red", "Blue", AccountType.REGULAR));
		edt(() -> assertTrue("in flight until the cached reveal", session.isLookupInFlight()));
		awaitCachedReveal();
		edt(() ->
		{
			assertEquals(5, session.getHiscoreResult().getTotalLevel());
			assertEquals("Red", session.getClogResult().getPlayerName());
			assertFalse(session.isLookupInFlight());
			assertNull(LookupHoldTest.holdTimer(session));
		});
		assertEquals(Arrays.asList("onLookupStart", "onCachedResult"), order);
	}

	// Cached hiscore with no clog yet is held, never blocks a new search, and shows with its clog.
	@Test
	public void cachedHiscoreWithoutAClogIsHeldUntilTheClogLands() throws Exception
	{
		LookupSession session = session();
		edt(() -> session.start("Red", "Blue", AccountType.REGULAR));
		awaitCachedReveal();
		edt(() ->
		{
			assertNull("held, not shown", session.getHiscoreResult());
			assertFalse("the hold must not block a new search", session.isLookupInFlight());
			assertNotNull(LookupHoldTest.holdTimer(session));
		});
		assertEquals(Arrays.asList("onLookupStart"), order);
		clogs.get("Red").complete(clog("Red"));
		edt(() -> assertEquals(5, session.getHiscoreResult().getTotalLevel()));
		assertEquals(Arrays.asList("onLookupStart", "onCachedResult", "onClogResult"), order);
	}

	// A cancelled cached lookup never reveals when its 600 ms timer fires late.
	@Test
	public void cancelledCachedLookupNeverReveals() throws Exception
	{
		LookupSession session = session();
		cachedClogs.put("Red", clog("Red"));
		edt(() ->
		{
			session.start("Red", "Blue", AccountType.REGULAR);
			session.cancelInFlight();
		});
		awaitCachedReveal();
		edt(() -> assertNull(session.getHiscoreResult()));
		assertEquals(Arrays.asList("onLookupStart"), order);
	}
}
