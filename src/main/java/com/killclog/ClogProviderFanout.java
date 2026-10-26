package com.killclog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class ClogProviderFanout
{
	// Every provider leg gets this long; tests shorten it.
	static long providerTimeoutSeconds = 7;

	private ClogProviderFanout()
	{
	}

	/** Shared by panel and chat; remote-self legs never replace local observations. */
	static CompletableFuture<ClogResult> lookup(boolean isSelf,
		Supplier<CompletableFuture<ClogResult>> temple,
		Supplier<CompletableFuture<ClogResult>> runeProfile,
		Supplier<CompletableFuture<ClogResult>> killclog)
	{
		return answers(isSelf, temple, runeProfile, killclog).first;
	}

	/** A lookup's log as the wait ends, and again if TempleOSRS answers only after it. */
	static final class Answers
	{
		final CompletableFuture<ClogResult> first;
		/**
		 * TempleOSRS's answer folded in with the others', when it came after the wait (10/24: a log that missed the
		 * wait only reached the cache, so the open lookup showed none until a second search); null when it came in
		 * time, or lost to RuneProfile's fresher log and so adds nothing.
		 */
		final CompletableFuture<ClogResult> late;

		private Answers(CompletableFuture<ClogResult> first, CompletableFuture<ClogResult> late)
		{
			this.first = first;
			this.late = late;
		}
	}

	static Answers answers(boolean isSelf,
		Supplier<CompletableFuture<ClogResult>> temple,
		Supplier<CompletableFuture<ClogResult>> runeProfile,
		Supplier<CompletableFuture<ClogResult>> killclog)
	{
		CompletableFuture<ClogResult> templeAnswer = fetch(temple);
		CompletableFuture<ClogResult> rp = bounded(isSelf ? CompletableFuture.completedFuture(null) : fetch(runeProfile));
		CompletableFuture<ClogResult> kc = bounded(isSelf ? CompletableFuture.completedFuture(null) : fetch(killclog));
		CompletableFuture<ClogResult> first = chooseFullest(templeAnswer, rp, kc);
		CompletableFuture<ClogResult> late = first.thenCompose(shown -> templeAnswer.isDone()
			? CompletableFuture.completedFuture(null)
			: templeAnswer.exceptionally(ex -> null).thenApply(answer ->
				answer == null || ClogResult.chooseFreshest(answer, rp.join()) != answer ? null
					: ClogResult.pickFullest(ClogResult.pickFreshest(answer, rp.join()), kc.join())));
		return new Answers(first, late);
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
		return chooseFreshest(temple, runeProfile, providerTimeoutSeconds, TimeUnit.SECONDS);
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
				providerOrNull(killclog, providerTimeoutSeconds, TimeUnit.SECONDS),
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

	/** One provider's answer within the time every provider gets, or none. */
	static CompletableFuture<ClogResult> bounded(CompletableFuture<ClogResult> provider)
	{
		return providerOrNull(provider, providerTimeoutSeconds, TimeUnit.SECONDS);
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
