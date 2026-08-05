package com.killclog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class ClogProviderFanout
{
	private static final long PROVIDER_TIMEOUT_SECONDS = 7;

	private ClogProviderFanout()
	{
	}

	static CompletableFuture<ClogResult> chooseFreshest(
		CompletableFuture<ClogResult> temple,
		CompletableFuture<ClogResult> runeProfile)
	{
		return chooseFreshest(temple, runeProfile, PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * All three legs: the provider pair races on recency as before, then the
	 * player's own killclog.com sync joins on coverage -- fullest leads, ties
	 * prefer first-party ({@link ClogResult#pickFullest}).
	 */
	static CompletableFuture<ClogResult> chooseFullest(
		CompletableFuture<ClogResult> temple,
		CompletableFuture<ClogResult> runeProfile,
		CompletableFuture<ClogResult> killclog)
	{
		return chooseFreshest(temple, runeProfile)
			.thenCombine(
				providerOrNull(killclog, PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS),
				ClogResult::pickFullest);
	}

	static CompletableFuture<ClogResult> chooseFreshest(
		CompletableFuture<ClogResult> temple,
		CompletableFuture<ClogResult> runeProfile,
		long timeout,
		TimeUnit unit)
	{
		return providerOrNull(temple, timeout, unit)
			.thenCombine(providerOrNull(runeProfile, timeout, unit), ClogResult::pickFreshest);
	}

	private static CompletableFuture<ClogResult> providerOrNull(
		CompletableFuture<ClogResult> future,
		long timeout,
		TimeUnit unit)
	{
		return future
			.completeOnTimeout(null, timeout, unit)
			.exceptionally(ex -> null);
	}
}
