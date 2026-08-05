package com.killclog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared transport for player lookups. Every remote fetch the panel makes
 * rides one version-stamped, EDT-bridged, timeout-bounded pipeline; the
 * primary and comparison controllers own separate instances (separate version
 * spaces) and keep their own presentation. What they can no longer do is
 * disagree about transport policy: timeouts, stale-result gating, and thread
 * bridging live here once.
 *
 * <p>Threading: fetches are started on the EDT; service futures resolve on
 * background threads and every callback is bridged back through
 * {@link SwingUtilities#invokeLater} behind a version check. The volatile
 * version and in-flight fields make the guards observable across threads.
 */
@Slf4j
final class LookupFanout
{
	static final int CA_TIMEOUT_SECONDS = 10;
	static final int HISCORE_TIMEOUT_SECONDS = 15;

	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final KillclogService killclogService;

	/** Monotonic counter. Each {@link #begin} increments; callbacks compare their stamp against it. */
	private volatile int version = 0;

	/** True while a fetch generation is unresolved (or until superseded). */
	private volatile boolean inFlight = false;

	LookupFanout(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, KillclogService killclogService)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.runeProfileService = runeProfileService;
		this.killclogService = killclogService;
	}

	/** Open a new fetch generation and return its version stamp. */
	int begin()
	{
		inFlight = true;
		return ++version;
	}

	/** The current generation resolved; the next {@link #begin} is accepted. */
	void settle()
	{
		inFlight = false;
	}

	/** Abandon an in-flight generation; already-spawned callbacks see themselves stale. */
	void cancel()
	{
		if (inFlight)
		{
			version++;
			inFlight = false;
		}
	}

	/**
	 * Invalidate outstanding callbacks without an in-flight generation to
	 * abandon (state adoption on the comparison swap): late clog/CA arrivals
	 * from the replaced player must not overwrite the adopted state.
	 */
	void invalidate()
	{
		version++;
		inFlight = false;
	}

	boolean isInFlight()
	{
		return inFlight;
	}

	int version()
	{
		return version;
	}

	/** True when the stamp still names the current generation. */
	boolean current(int stamp)
	{
		return stamp == version;
	}

	/**
	 * CA tier fetch through RuneProfile (or the local cache for self).
	 * Delivered on the EDT behind the version guard; failures and timeouts log
	 * and deliver nothing, the caller simply keeps no CA surface.
	 */
	void fetchCa(String player, int stamp, Consumer<CombatAchievementResult> onResult)
	{
		runeProfileService.lookup(player)
			.orTimeout(CA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.thenAccept(ca -> onEdtIfCurrent(stamp, () -> onResult.accept(ca)))
			.exceptionally(ex ->
			{
				log.warn("CA lookup failed", ex);
				return null;
			});
	}

	/**
	 * Hiscore fetch. A null result means the player was not found; the caller
	 * owns that presentation. Errors unwrap to {@code onError}, also on the
	 * EDT behind the guard.
	 */
	void fetchHiscore(String player, @Nullable AccountType knownType, int stamp,
		Consumer<HiscoreResult> onResult, Consumer<Throwable> onError)
	{
		hiscoreService.lookup(player, knownType)
			.orTimeout(HISCORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.thenAccept(result -> onEdtIfCurrent(stamp, () -> onResult.accept(result)))
			.exceptionally(ex ->
			{
				onEdtIfCurrent(stamp, () -> onError.accept(ex));
				return null;
			});
	}

	/**
	 * All clog sources in parallel: the public provider pair races on
	 * recency, then the player's own killclog.com sync joins on coverage
	 * (fullest leads, ties prefer first-party). Self lookups skip both
	 * remote-self legs because local widget data is authoritative.
	 * {@code onError} runs on the EDT behind the guard when present; null
	 * means log-and-drop, which is the primary path's behavior.
	 */
	void fetchClog(String player, boolean isSelf, int stamp,
		Consumer<ClogResult> onResult, @Nullable Runnable onError)
	{
		CompletableFuture<ClogResult> temple = clogService.lookup(player);
		CompletableFuture<ClogResult> rp = isSelf
			? CompletableFuture.completedFuture(null)
			: runeProfileService.lookupClog(player);
		CompletableFuture<ClogResult> killclog = isSelf
			? CompletableFuture.completedFuture(null)
			: killclogService.lookupClog(player);

		ClogProviderFanout.chooseFullest(temple, rp, killclog)
			.thenAccept(result -> onEdtIfCurrent(stamp, () -> onResult.accept(result)))
			.exceptionally(ex ->
			{
				log.warn("Clog lookup failed", ex);
				if (onError != null)
				{
					onEdtIfCurrent(stamp, onError);
				}
				return null;
			});
	}

	private void onEdtIfCurrent(int stamp, Runnable action)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (current(stamp))
			{
				action.run();
			}
		});
	}
}
