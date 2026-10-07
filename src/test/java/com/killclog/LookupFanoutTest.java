package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LookupFanoutTest
{
	@Test
	public void testGenerationStateMachine()
	{
		LookupFanout fanout = new LookupFanout(null, null, null, null);
		assertFalse(fanout.isInFlight());

		int first = fanout.begin();
		assertTrue(fanout.isInFlight());
		assertTrue(fanout.current(first));

		fanout.settle();
		assertFalse(fanout.isInFlight());
		// Settling resolves the generation without staling its callbacks:
		// clog and CA lanes may still deliver after the hiscore resolves.
		assertTrue(fanout.current(first));

		int second = fanout.begin();
		assertFalse(fanout.current(first));
		assertTrue(fanout.current(second));

		// Cancel abandons an in-flight generation and stales its callbacks.
		fanout.invalidate();
		assertFalse(fanout.isInFlight());
		assertFalse(fanout.current(second));

		// Optional legs can outlive settlement and must also be invalidated.
		int third = fanout.begin();
		fanout.settle();
		fanout.invalidate();
		assertFalse(fanout.current(third));
	}

	@Test
	public void aLeagueReadsLeagueHiscoresYourOwnLogAndKillClogOnly() throws Exception
	{
		HiscoreService hiscores = mock(HiscoreService.class);
		ClogService clogs = mock(ClogService.class);
		RuneProfileService runeProfile = mock(RuneProfileService.class);
		KillclogService killclog = mock(KillclogService.class);
		LocalClogCache own = mock(LocalClogCache.class);
		HiscoreResult row = mock(HiscoreResult.class);
		ClogResult theirs = mock(ClogResult.class);
		ClogResult mine = mock(ClogResult.class);
		when(hiscores.lookupLeague("Friend")).thenReturn(CompletableFuture.completedFuture(row));
		when(killclog.lookupClog("Friend", "demonic-pacts")).thenReturn(CompletableFuture.completedFuture(theirs));
		when(clogs.lookupLocal(own, "Me")).thenReturn(CompletableFuture.completedFuture(mine));
		LookupFanout fanout = new LookupFanout(hiscores, clogs, runeProfile, killclog);
		fanout.readLeague("demonic-pacts", own);
		int stamp = fanout.begin();
		List<Object> shown = new ArrayList<>();
		fanout.fetchHiscore("Friend", null, stamp, shown::add, error -> fail(error.toString()));
		fanout.fetchClog("Friend", false, stamp, shown::add, null);
		fanout.fetchClog("Me", true, stamp, shown::add, null);
		fanout.fetchCa("Friend", stamp, shown::add);
		SwingUtilities.invokeAndWait(() ->
		{
		});
		assertEquals(List.of(row, theirs, mine), shown);
		verify(hiscores, never()).lookup(any(), any());
		verify(clogs, never()).lookup(any());
		verify(killclog, never()).lookupClog("Friend");
		verifyNoInteractions(runeProfile);

		when(hiscores.lookup("Friend", null)).thenReturn(new CompletableFuture<>());
		fanout.readLeague(null, null);
		fanout.fetchHiscore("Friend", null, stamp, shown::add, error -> fail(error.toString()));
		verify(hiscores).lookup("Friend", null);
	}

	@Test
	public void yourOwnLogIsTheViewedGamesAndOnlyYours()
	{
		ClogService clogs = mock(ClogService.class);
		LocalClogCache own = mock(LocalClogCache.class);
		ClogResult main = mock(ClogResult.class);
		ClogResult league = mock(ClogResult.class);
		when(clogs.getCachedResult("Me")).thenReturn(main);
		when(clogs.localResult(own, "Me")).thenReturn(league);
		LookupFanout fanout = new LookupFanout(null, clogs, null, null);
		assertSame(main, fanout.ownLog("Me"));
		fanout.readLeague("demonic-pacts", own);
		assertSame(league, fanout.ownLog("Me"));
		fanout.readLeague("demonic-pacts", null);
		assertNull("a League read from another world has no log of yours here", fanout.ownLog("Me"));

		ClogService real = new ClogService(null, null, null);
		when(own.hasDataFor(any())).thenReturn(true);
		when(own.isActivePlayer("Me")).thenReturn(true);
		ClogResult logged = new ClogResult("Me", java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), null, null);
		when(own.toClogResult(any(), any())).thenReturn(logged);
		assertTrue(real.localResult(own, "Me").isFromLocal());
		assertNull("another player's name never reads your log", real.localResult(own, "Other"));
	}

	@Test
	public void testTransportPolicyPins()
	{
		// The compare side once lost these by re-implementing transport;
		// timeout policy now has exactly one home.
		assertEquals(10, LookupFanout.CA_TIMEOUT_SECONDS);
		assertEquals(15, LookupFanout.HISCORE_TIMEOUT_SECONDS);
	}

	@Test
	public void aLookupJagexNeverAnswersReadsAsTheHiscoresBeingDown() throws Exception
	{
		// Jagex holds the request open past the lookup's own deadline.
		HiscoreService silent = org.mockito.Mockito.mock(HiscoreService.class);
		org.mockito.Mockito.when(silent.lookup("Quiet", null)).thenReturn(new CompletableFuture<>());
		LookupFanout fanout = new LookupFanout(silent, null, null, null);
		java.util.concurrent.CountDownLatch delivered = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicReference<String> shown = new java.util.concurrent.atomic.AtomicReference<>();
		javax.swing.SwingUtilities.invokeAndWait(() -> fanout.fetchHiscore("Quiet", null, fanout.begin(),
			result -> delivered.countDown(), error ->
			{
				shown.set(HiscoreService.failureText(error));
				delivered.countDown();
			}));
		org.junit.Assert.assertTrue(delivered.await(LookupFanout.HISCORE_TIMEOUT_SECONDS + 3, java.util.concurrent.TimeUnit.SECONDS));
		assertEquals(HiscoreService.DOWN_MESSAGE, shown.get());
	}
}
