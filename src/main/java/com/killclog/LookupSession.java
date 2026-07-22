/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the primary lookup lifecycle for Kill Clog: cache check, reveal
 * pacing, and listener choreography. The transport underneath (parallel
 * hiscore, clog, and CA fetches with version gating, EDT bridging, and
 * timeouts) is {@link LookupFanout}, shared with the comparison side. UI is
 * downstream via {@link Listener}; never widget code here.
 */
package com.killclog;

import javax.annotation.Nullable;
import javax.swing.Timer;

/**
 * One player's lookup lifecycle. Subscribers receive typed events through
 * {@link Listener}; this class never touches the UI. The owned
 * {@link LookupFanout} version-stamps every fetch so an in-flight call
 * superseded by a newer {@link #start} cannot overwrite it.
 *
 * <p>Threading: {@link #start} runs on the EDT, and every listener callback
 * arrives on the EDT (the reveal {@link Timer} fires there; service futures
 * bridge through the fanout).
 */
public class LookupSession
{
	/**
	 * Receives lifecycle events from a {@link LookupSession}. All methods
	 * are invoked on the EDT.
	 */
	public interface Listener
	{
		/** Lookup begun: UI should reset row state, set loading indicator, post a search-status line. */
		void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting);

		/** Cache hit: hiscore and possibly clog returned from service caches without an API call. */
		void onCachedResult(String player, HiscoreResult hiscore, @Nullable ClogResult clog,
			boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting);

		/** Live hiscore result arrived from the API. */
		void onHiscoreResult(String player, HiscoreResult hiscore,
			boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting);

		/**
		 * Live clog result arrived from the API (independent of hiscore timing).
		 * {@code clog} is null when the clog service returned no data for the
		 * player; the listener is responsible for the not-found UI (sync notice
		 * for self, blank notice otherwise) and for any follow-up RSN fetch.
		 */
		void onClogResult(String player, @Nullable ClogResult clog, boolean isSelf, int lookupVersion);

		/**
		 * CA tier result arrived from local cache or RuneProfile (independent of hiscore/clog timing).
		 * {@code ca} is null when the player has no CA data (not synced, or the provider was
		 * unavailable); the listener simply omits the CA surface in that case.
		 */
		void onCaResult(String player, @Nullable CombatAchievementResult ca, boolean isSelf, int lookupVersion);

		/** The hiscore service returned a null result (player not found). */
		void onNotFound(String player);

		/** A service call threw. The error parameter is the unwrapped cause where possible. */
		void onError(String player, Throwable error);
	}

	// Deps
	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final KillClogConfig config;
	private final Listener listener;
	private final LookupFanout fanout;
	@Nullable private NameAutocompleter nameAutocompleter;

	// State
	@Nullable private HiscoreResult hiscoreResult;
	@Nullable private ClogResult clogResult;
	@Nullable private CombatAchievementResult caResult;
	@Nullable private String currentLookupRsn;
	@Nullable private String clogLastChanged;

	public LookupSession(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, KillClogConfig config,
		@Nullable NameAutocompleter nameAutocompleter, Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.nameAutocompleter = nameAutocompleter;
		this.listener = listener;
		this.fanout = new LookupFanout(hiscoreService, clogService, runeProfileService);
	}

	/** Wire (or rewire) the autocompleter that records search history on each successful lookup. */
	public void setNameAutocompleter(@Nullable NameAutocompleter nameAutocompleter)
	{
		this.nameAutocompleter = nameAutocompleter;
	}

	// Lifecycle

	/**
	 * Begin a lookup for {@code player}. No-op if {@code player} is empty or a
	 * lookup is already in flight (early return preserves the existing
	 * pipeline). Otherwise: cache check with a reveal-on-delay if fresh, or a
	 * full parallel hiscore + public clog provider fan-out if stale or missing.
	 *
	 * <p>Caller must invoke on the EDT. All listener callbacks are also on the
	 * EDT.
	 */
	public void start(String player, @Nullable String localRsn, @Nullable AccountType localAccountType)
	{
		if (player.isEmpty() || fanout.isInFlight())
		{
			return;
		}

		currentLookupRsn = player;
		hiscoreResult = null;
		clogResult = null;
		caResult = null;
		clogLastChanged = null;
		final int thisLookup = fanout.begin();
		final boolean isSelf = localRsn != null && localRsn.equalsIgnoreCase(player);
		final boolean isFirstSelfGreeting = isSelf && !config.seenSelfGreeting();

		listener.onLookupStart(player, isSelf, isFirstSelfGreeting);

		// CA tier fetch runs in parallel with the hiscore/clog fan-out (and through the cache
		// path), independent of either. RuneProfileService owns its own freshness + dedup.
		fanout.fetchCa(player, thisLookup, ca ->
		{
			caResult = ca;
			listener.onCaResult(player, ca, isSelf, thisLookup);
		});

		// Self-lookups know their own account type; cascade only matters for
		// other players. GIMs only ever appear on the regular hiscores, so the
		// hiscore service must be told to skip the cascade for them.
		final AccountType knownType = isSelf ? localAccountType : null;

		final HiscoreResult cachedHiscore = hiscoreService.getCached(player);
		final ClogResult cachedClog = clogService.getCachedResult(player);
		if (cachedHiscore != null && !hiscoreService.isStale(player))
		{
			startClogLookup(player, isSelf, thisLookup);
			Timer revealTimer = new Timer(600, e ->
			{
				if (!fanout.current(thisLookup))
				{
					return;
				}
				hiscoreResult = cachedHiscore;
				fanout.settle();
				if (nameAutocompleter != null)
				{
					nameAutocompleter.addToSearchHistory(player);
				}
				ClogResult displayClog = clogResult;
				if (displayClog == null && cachedClog != null)
				{
					clogResult = cachedClog;
					clogLastChanged = cachedClog.getLastChanged();
					displayClog = cachedClog;
				}
				listener.onCachedResult(player, cachedHiscore, displayClog, isSelf, knownType, isFirstSelfGreeting);
			});
			revealTimer.setRepeats(false);
			revealTimer.start();
			return;
		}

		final AccountType hiscoreType = knownType != null && knownType.isGroupIronman()
			? AccountType.REGULAR : knownType;
		fanout.fetchHiscore(player, hiscoreType, thisLookup, result ->
		{
			if (result == null)
			{
				// Invalidate the still-in-flight clog/CA lanes: a not-found
				// must not paint partial data afterwards.
				fanout.invalidate();
				listener.onNotFound(player);
				return;
			}
			fanout.settle();
			hiscoreResult = result;
			if (nameAutocompleter != null)
			{
				nameAutocompleter.addToSearchHistory(player);
			}
			listener.onHiscoreResult(player, result, isSelf, knownType, isFirstSelfGreeting);
		}, ex ->
		{
			fanout.invalidate();
			listener.onError(player, ex);
		});

		startClogLookup(player, isSelf, thisLookup);
	}

	/** Clog fan-out via the shared transport; failures log and keep the surface empty. */
	private void startClogLookup(String player, boolean isSelf, int thisLookup)
	{
		fanout.fetchClog(player, isSelf, thisLookup, result ->
		{
			clogResult = result;
			clogLastChanged = result != null ? result.getLastChanged() : null;
			listener.onClogResult(player, result, isSelf, thisLookup);
		}, null);
	}

	/**
	 * Abandon any in-flight lookup so the next {@link #start} is accepted.
	 * Bumps the version stamp so already-spawned async callbacks see
	 * themselves as stale and short-circuit.
	 */
	public void cancelInFlight()
	{
		fanout.cancel();
	}

	/**
	 * Adopt already-loaded results as the current lookup state, bypassing the
	 * async pipeline. Used by the comparison swap: clicking the red player
	 * promotes their results into the primary slot in one synchronous step.
	 */
	public void adoptState(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable CombatAchievementResult ca, @Nullable String name)
	{
		// Invalidate any still-in-flight callbacks from the player being
		// replaced: their clog/CA lanes can outlive the hiscore result, and
		// without this bump a late arrival would overwrite the adopted state.
		fanout.invalidate();
		this.hiscoreResult = hiscore;
		this.clogResult = clog;
		this.caResult = ca;
		this.currentLookupRsn = name;
		this.clogLastChanged = clog != null ? clog.getLastChanged() : null;
	}

	// Read-only state
	@Nullable
	public HiscoreResult getHiscoreResult()
	{
		return hiscoreResult;
	}

	@Nullable
	public ClogResult getClogResult()
	{
		return clogResult;
	}

	@Nullable
	public CombatAchievementResult getCaResult()
	{
		return caResult;
	}

	@Nullable
	public String getCurrentLookupRsn()
	{
		return currentLookupRsn;
	}

	@Nullable
	public String getClogLastChanged()
	{
		return clogLastChanged;
	}

	public int getLookupVersion()
	{
		return fanout.version();
	}

	public boolean isLookupInFlight()
	{
		return fanout.isInFlight();
	}
}
