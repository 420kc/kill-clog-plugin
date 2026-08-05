package com.killclog;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class ClogProviderFanoutTest
{
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
