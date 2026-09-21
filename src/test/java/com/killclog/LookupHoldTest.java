package com.killclog;

import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.Test;
import static com.killclog.LookupTestFixture.*;
import static org.junit.Assert.*;

/** The display hold: a finished hiscore waits briefly for its clog, and only for its own lookup. */
public class LookupHoldTest
{
	// A timer or late callback from lookup N must neither reveal nor clear lookup N+1's hold.
	@Test
	public void staleReleaseFromAnEarlierLookupCannotTouchTheNextHold() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Red", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Red").complete(hiscore(7));
		List<ActionListener> redRelease = new ArrayList<>();
		edt(() -> redRelease.addAll(Arrays.asList(holdTimer(fixture.primary).getActionListeners())));

		edt(() -> fixture.primary.start("Green", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Green").complete(hiscore(9));
		edt(() -> assertNotNull("Green should be held", holdTimer(fixture.primary)));

		// Lookup Red's release arrives late (an already-queued timer event, a late callback).
		edt(() -> redRelease.forEach(listener -> listener.actionPerformed(null)));
		assertEquals("a stale release must not reveal the next lookup early",
			0, fixture.events("onHiscoreResult"));

		fixture.clogs.get("Green").complete(clog("Green"));
		edt(() ->
		{
			assertNotNull("Green's held hiscore was destroyed by Red's stale release",
				fixture.primary.getHiscoreResult());
			assertEquals(9, fixture.primary.getHiscoreResult().getTotalLevel());
		});
		assertEquals(1, fixture.events("onHiscoreResult"));
	}

	// The hold re-arms for every lookup, not only the first of the session.
	@Test
	public void everyLookupHoldsAgainAfterAnEarlierOneSettled() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Red", "Blue", AccountType.REGULAR));
		fixture.clogs.get("Red").complete(clog("Red"));
		fixture.hiscores.get("Red").complete(hiscore(7));
		edt(() -> assertEquals(7, fixture.primary.getHiscoreResult().getTotalLevel()));

		edt(() -> fixture.primary.start("Green", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Green").complete(hiscore(9));
		edt(() -> assertNull("Green must wait for its own clog", fixture.primary.getHiscoreResult()));
		assertEquals(1, fixture.events("onHiscoreResult"));

		fixture.clogs.get("Green").complete(clog("Green"));
		edt(() -> assertEquals(9, fixture.primary.getHiscoreResult().getTotalLevel()));
		assertEquals(2, fixture.events("onHiscoreResult"));
	}

	// A local capture that lands during the hold is a settled clog: show the hiscore first.
	@Test
	public void localCaptureDuringTheHoldReleasesItHiscoreFirst() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Blue", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Blue").complete(hiscore(7));
		edt(() -> assertNull(fixture.primary.getHiscoreResult()));
		fixture.cachedClogs.put("Blue", clog("Blue").withLocalSource(true));
		edt(() -> fixture.primary.refreshLocalClog("Blue", "Blue"));
		edt(() -> assertEquals(7, fixture.primary.getHiscoreResult().getTotalLevel()));
		assertEquals(Arrays.asList("onLookupStart", "onHiscoreResult", "onClogResult"), fixture.order);
	}

	// The hold really is a one-shot running timer of CLOG_HOLD_MS.
	@Test
	public void holdIsARunningOneShotTimerOfTheDocumentedLength() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Red", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Red").complete(hiscore(7));
		edt(() ->
		{
			Timer timer = holdTimer(fixture.primary);
			assertNotNull(timer);
			assertTrue("hold timer must be started", timer.isRunning());
			assertFalse("hold timer must fire once", timer.isRepeats());
			assertEquals(LookupSession.CLOG_HOLD_MS, timer.getInitialDelay());
			assertTrue("a slow provider may not hide a finished hiscore for long",
				LookupSession.CLOG_HOLD_MS <= 3000);
			fixture.primary.reset();
		});
	}

	// Cancel, reset and a new search stop the timer and drop the held reveal.
	@Test
	public void cancelResetAndNewSearchStopTheHoldTimer() throws Exception
	{
		for (String how : new String[]{"cancel", "reset", "start"})
		{
			LookupTestFixture fixture = new LookupTestFixture();
			edt(() -> fixture.primary.start("Red", "Blue", AccountType.REGULAR));
			fixture.hiscores.get("Red").complete(hiscore(7));
			Timer[] held = new Timer[1];
			edt(() ->
			{
				held[0] = holdTimer(fixture.primary);
				assertTrue(held[0].isRunning());
				if (how.equals("cancel")) fixture.primary.cancelInFlight();
				else if (how.equals("reset")) fixture.primary.reset();
				else fixture.primary.start("Green", "Blue", AccountType.REGULAR);
				assertFalse(how + " must stop the hold timer", held[0].isRunning());
				assertNull(how + " must drop the timer", holdTimer(fixture.primary));
				assertNull(how + " must drop the held reveal", field(fixture.primary, "heldReveal"));
			});
			assertEquals(0, fixture.events("onHiscoreResult"));
		}
	}

	// A clog lane that FAILS (not merely returns nothing) still releases the held hiscore.
	@Test
	public void failedClogLaneReleasesTheHeldHiscore() throws Exception
	{
		CompletableFuture<HiscoreResult> hiscore = new CompletableFuture<>();
		List<String> order = new ArrayList<>();
		HiscoreService hiscores = new HiscoreService(null, null)
		{
			@Override
			public CompletableFuture<HiscoreResult> lookup(String name, AccountType type)
			{
				return hiscore;
			}
		};
		// Two provider results with no item map: the combine throws, which is
		// the only way the fan-out's own error path is reached.
		ClogResult broken = new ClogResult("Red", null, Map.of(), Map.of(), null, null);
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
				return CompletableFuture.completedFuture(broken);
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
				return CompletableFuture.completedFuture(broken);
			}
		};
		LookupSession.Listener listener = (LookupSession.Listener) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{LookupSession.Listener.class},
			(proxy, method, args) ->
			{
				order.add(method.getName());
				return null;
			});
		LookupSession session = new LookupSession(hiscores, clogs, runeProfile, null,
			new KillClogConfig()
			{
			}, listener);
		edt(() -> session.start("Red", "Blue", AccountType.REGULAR));
		hiscore.complete(hiscore(7));
		edt(() ->
		{
		});
		edt(() ->
		{
			assertNotNull("a failed clog lane left the hiscore held", session.getHiscoreResult());
			assertNull("the failed lane released the hold, it did not time out", holdTimer(session));
		});
		assertEquals(Arrays.asList("onLookupStart", "onHiscoreResult"), order);
	}

	static Timer holdTimer(LookupSession session)
	{
		return (Timer) field(session, "holdTimer");
	}

	static Object field(LookupSession session, String name)
	{
		try
		{
			Field field = LookupSession.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(session);
		}
		catch (ReflectiveOperationException ex)
		{
			throw new AssertionError(ex);
		}
	}

	static void flush() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}
}
