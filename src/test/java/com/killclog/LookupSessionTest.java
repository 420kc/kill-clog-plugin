package com.killclog;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.killclog.LookupTestFixture.*;

public class LookupSessionTest
{
	@Test
	public void localRefreshPreservesStatsCaAndComparisonWithoutStartingLookups() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		HiscoreResult stats = fixture.primary.getHiscoreResult();
		CombatAchievementResult ca = fixture.primary.getCaResult();
		ClogResult updated = clog("Blue").withLocalSource(true);
		fixture.cachedClogs.put("Blue", updated);
		edt(() -> fixture.comparison.doCompareLookup("Red", "Blue"));
		fixture.hiscores.get("Red").complete(hiscore(2));
		edt(() -> assertNotNull(fixture.clogs.get("Red")));
		fixture.clogs.get("Red").complete(clog("Red"));
		fixture.cas.get("Red").complete(ca(2));
		edt(() ->
		{
			assertTrue(fixture.comparison.isComparisonMode());
			fixture.primary.refreshLocalClog("Blue", "Blue");
			assertSame(updated, fixture.primary.getClogResult());
			assertSame(stats, fixture.primary.getHiscoreResult());
			assertSame(ca, fixture.primary.getCaResult());
			assertTrue(fixture.comparison.isComparisonMode());
			assertEquals("Red", fixture.comparison.getCompareClogResult().getPlayerName());
		});
		assertEquals(0, fixture.events("onLookupStart"));
		assertEquals(1, fixture.events("onClogResult"));
		assertFalse(fixture.clogs.containsKey("Blue"));
		assertFalse(fixture.hiscores.containsKey("Blue"));
	}

	@Test
	public void localRefreshDoesNotReplaceAnotherPlayerOrAnUnsettledCache() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		ClogResult original = fixture.primary.getClogResult();
		fixture.cachedClogs.put("Red", clog("Red").withLocalSource(true));
		fixture.cachedClogs.put("Blue", clog("Blue"));
		edt(() ->
		{
			fixture.primary.refreshLocalClog("Red", "Red");
			fixture.primary.refreshLocalClog("Blue", "Red");
			fixture.primary.refreshLocalClog("Blue", "Blue");
			assertSame(original, fixture.primary.getClogResult());
			assertEquals("Blue", fixture.primary.getCurrentLookupRsn());
		});
		assertEquals(0, fixture.events("onClogResult"));
	}

	@Test
	public void localCaptureBeatsLateClogWithoutCancellingStatsOrCa() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Blue", "Blue", AccountType.REGULAR));
		ClogResult updated = clog("Blue").withLocalSource(true);
		fixture.cachedClogs.put("Blue", updated);
		edt(() -> fixture.primary.refreshLocalClog("Blue", "Blue"));
		fixture.clogs.get("Blue").complete(clog("Blue"));
		fixture.hiscores.get("Blue").complete(hiscore(7));
		fixture.cas.get("Blue").complete(ca(3));
		edt(() ->
		{
			assertSame(updated, fixture.primary.getClogResult());
			assertEquals(7, fixture.primary.getHiscoreResult().getTotalLevel());
			assertNotNull(fixture.primary.getCaResult());
		});
		assertEquals(1, fixture.events("onLookupStart"));
		assertEquals(1, fixture.events("onHiscoreResult"));
		assertEquals(1, fixture.events("onCaResult"));
	}

	@Test
	public void notFoundClearsEarlierClogAndRestoresDormantState() throws Exception
	{
		assertFailedLookup(true, false);
	}

	@Test
	public void notFoundRejectsLateClogAndCa() throws Exception
	{
		assertFailedLookup(false, false);
	}

	@Test
	public void failedLookupAlsoClearsEarlierClog() throws Exception
	{
		assertFailedLookup(true, true);
	}

	private void assertFailedLookup(boolean clogFirst, boolean error) throws Exception
	{
		CompletableFuture<HiscoreResult> hiscore = new CompletableFuture<>();
		CompletableFuture<ClogResult> clog = new CompletableFuture<>();
		CompletableFuture<CombatAchievementResult> ca = new CompletableFuture<>();
		HiscoreService hiscores = new HiscoreService(null, null)
		{
			@Override
			public CompletableFuture<HiscoreResult> lookup(String name, AccountType type)
			{
				return hiscore;
			}
		};
		ClogService clogs = new ClogService(null, null, null)
		{
			@Override
			public ClogResult getCachedResult(String name)
			{
				return null;
			}

			@Override
			public CompletableFuture<ClogResult> lookup(String name)
			{
				return clog;
			}
		};
		RuneProfileService runeProfile = new RuneProfileService(null, null, null)
		{
			@Override
			public CompletableFuture<CombatAchievementResult> lookup(String name)
			{
				return ca;
			}
		};
		AtomicInteger failures = new AtomicInteger();
		AtomicInteger clogCallbacks = new AtomicInteger();
		AtomicInteger caCallbacks = new AtomicInteger();
		LookupSession[] holder = new LookupSession[1];
		LookupSession.Listener listener = (LookupSession.Listener) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{LookupSession.Listener.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals(error ? "onError" : "onNotFound"))
				{
					assertDormant(holder[0]);
					failures.incrementAndGet();
				}
				if (method.getName().equals("onClogResult")) clogCallbacks.incrementAndGet();
				if (method.getName().equals("onCaResult")) caCallbacks.incrementAndGet();
				return null;
			});
		KillClogConfig config = new KillClogConfig()
		{
		};
		LookupSession session = new LookupSession(hiscores, clogs, runeProfile, null,
			config, null, listener);
		holder[0] = session;
		ClogResult partial = new ClogResult("Missing", Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), "2026-09-10", null);
		SwingUtilities.invokeAndWait(() -> session.start("Missing", "Missing", AccountType.REGULAR));
		if (clogFirst)
		{
			clog.complete(partial);
			SwingUtilities.invokeAndWait(() -> assertEquals(partial.getPlayerName(), session.getClogResult().getPlayerName()));
		}
		if (error) hiscore.completeExceptionally(new IllegalStateException("test failure"));
		else hiscore.complete(null);
		SwingUtilities.invokeAndWait(() -> assertDormant(session));
		clog.complete(partial);
		ca.complete(null);
		SwingUtilities.invokeAndWait(() -> assertDormant(session));
		assertEquals(1, failures.get());
		assertEquals(clogFirst ? 1 : 0, clogCallbacks.get());
		assertEquals(0, caCallbacks.get());
	}

	private static void assertDormant(LookupSession session)
	{
		assertFalse(session.isLookupInFlight());
		assertNull(session.getCurrentLookupRsn());
		assertNull(session.getHiscoreResult());
		assertNull(session.getClogResult());
		assertNull(session.getClogLastChanged());
		assertNull(session.getCaResult());
	}
}
