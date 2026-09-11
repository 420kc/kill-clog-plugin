package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ClogProviderFanoutTest
{
	@Test
	public void selfLookupNeverStartsRemoteSelfLegsEvenWithoutLocalData()
	{
		AtomicInteger remoteCalls = new AtomicInteger();
		ClogResult local = result("Local", 0);
		for (ClogResult own : new ClogResult[]{local, null})
		{
			ClogResult selected = ClogProviderFanout.lookup(true,
				() -> CompletableFuture.completedFuture(own),
				() ->
				{
					remoteCalls.incrementAndGet();
					return CompletableFuture.completedFuture(result("RP", 0));
				},
				() ->
				{
					remoteCalls.incrementAndGet();
					return CompletableFuture.completedFuture(result("KC", 0));
				}).join();
			assertEquals(own == null ? null : "Local", selected == null ? null : selected.getPlayerName());
		}
		assertEquals(0, remoteCalls.get());
	}

	@Test
	public void publicLookupUsesAllThreeSourcesAndSurvivesSynchronousFailure()
	{
		AtomicInteger remoteCalls = new AtomicInteger();
		ClogResult selected = ClogProviderFanout.lookup(false,
			() ->
			{
				throw new IllegalStateException("Temple unavailable");
			},
			() ->
			{
				remoteCalls.incrementAndGet();
				return CompletableFuture.completedFuture(null);
			},
			() ->
			{
				remoteCalls.incrementAndGet();
				return CompletableFuture.completedFuture(result("KC", 0));
			}).join();
		assertEquals("KC", selected.getPlayerName());
		assertEquals(2, remoteCalls.get());
	}

	@Test
	public void publicLookupCanUseRuneProfileWithoutTempleOrFirstParty()
	{
		ClogResult selected = ClogProviderFanout.lookup(false,
			() -> CompletableFuture.completedFuture(null),
			() -> CompletableFuture.completedFuture(result("RP", 0)),
			() -> CompletableFuture.completedFuture(null)).join();
		assertEquals("RP", selected.getPlayerName());
	}
	@Test
	public void testTempleResultSurvivesRuneProfileTimeout() throws Exception
	{
		ClogResult temple = result("Temple", 4);
		CompletableFuture<ClogResult> hangingRp = new CompletableFuture<>();

		ClogResult picked = ClogProviderFanout.chooseFreshest(
			CompletableFuture.completedFuture(temple),
			hangingRp,
			25,
			TimeUnit.MILLISECONDS
		).get(1, TimeUnit.SECONDS);

		assertEquals("Temple", picked.getPlayerName());
		assertFalse(hangingRp.isDone());
		ClogResult late = result("Late RP", 10);
		hangingRp.complete(late);
		assertSame(late, hangingRp.join());
	}

	@Test
	public void testRuneProfileResultSurvivesTempleTimeout() throws Exception
	{
		ClogResult runeProfile = result("RuneProfile", 10);
		CompletableFuture<ClogResult> hangingTemple = new CompletableFuture<>();

		ClogResult picked = ClogProviderFanout.chooseFreshest(
			hangingTemple,
			CompletableFuture.completedFuture(runeProfile),
			25,
			TimeUnit.MILLISECONDS
		).get(1, TimeUnit.SECONDS);

		assertEquals("RuneProfile", picked.getPlayerName());
		assertFalse(hangingTemple.isDone());
	}

	@Test
	public void testProviderExceptionDegradesToOtherResult() throws Exception
	{
		ClogResult runeProfile = result("RuneProfile", 10);
		CompletableFuture<ClogResult> failedTemple = new CompletableFuture<>();
		failedTemple.completeExceptionally(new IllegalStateException("provider failed"));

		ClogResult picked = ClogProviderFanout.chooseFreshest(
			failedTemple,
			CompletableFuture.completedFuture(runeProfile),
			25,
			TimeUnit.MILLISECONDS
		).get(1, TimeUnit.SECONDS);

		assertEquals("RuneProfile", picked.getPlayerName());
	}

	private static ClogResult result(String name, int uniqueObtained)
	{
		ClogResult result = new ClogResult(
			name,
			Collections.emptyMap(),
			Collections.emptyMap(),
			Collections.emptyMap(),
			null,
			null
		);
		result.setUniqueObtained(uniqueObtained);
		return result;
	}
}
