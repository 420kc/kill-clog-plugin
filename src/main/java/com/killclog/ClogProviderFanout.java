package com.killclog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class ClogProviderFanout
{
	private static final long PROVIDER_TIMEOUT_SECONDS = 7;

	private ClogProviderFanout()
	{
	}

	/** Shared by panel and chat; remote-self legs never replace local observations. */
	static CompletableFuture<ClogResult> lookup(boolean isSelf,
		Supplier<CompletableFuture<ClogResult>> temple,
		Supplier<CompletableFuture<ClogResult>> runeProfile,
		Supplier<CompletableFuture<ClogResult>> killclog)
	{
		return chooseFullest(fetch(temple),
			isSelf ? CompletableFuture.completedFuture(null) : fetch(runeProfile),
			isSelf ? CompletableFuture.completedFuture(null) : fetch(killclog));
	}

	private static CompletableFuture<ClogResult> fetch(Supplier<CompletableFuture<ClogResult>> provider)
	{
		try
		{
			return provider.get();
		}
		catch (Exception ex)
		{
			return CompletableFuture.completedFuture(null);
		}
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
		return future.copy()
			.completeOnTimeout(null, timeout, unit)
			.exceptionally(ex -> null);
	}
}
