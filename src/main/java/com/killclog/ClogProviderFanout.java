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
