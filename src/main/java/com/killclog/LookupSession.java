/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the async lookup pipeline for Kill Clog: cache check, parallel hiscore +
 * clog API calls, version-stamped result gating, in-flight guard. UI is
 * downstream via {@link Listener}. Swing usage is limited to the EDT-scheduling
 * primitives ({@link javax.swing.Timer} for the cached-result reveal delay and
 * {@link javax.swing.SwingUtilities#invokeLater} for marshalling future
 * callbacks back to the EDT) — never any widget code.
 *
 * Extracted from KillClogPanel as refactor cut 1.
 */
package com.killclog;

import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsulates one player's lookup lifecycle. A panel subscribes via
 * {@link Listener} and reacts to typed events; this class never touches the UI
 * directly. Lookup version stamps protect against stale results from earlier
 * in-flight requests overwriting newer ones.
 *
 * <p>Threading model: {@link #start} is called on the EDT. Service calls
 * happen on background threads; their completion callbacks marshal back to
 * the EDT before invoking listener methods.
 *
 * <p>State is read via getters; no setter exposes session fields. All
 * mutations flow through {@link #start} and the async callbacks it spawns.
 *
 * <p>This class is intentionally NOT thread-safe at the API surface: callers
 * must invoke {@link #start} on the EDT. The {@code volatile} fields exist
 * to make version stamps + the in-flight guard observable across the
 * background threads that the underlying services hop through.
 */
@Slf4j
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

		/** Cache hit: hiscore (and possibly clog) returned from local cache without an API call. */
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

		/** The hiscore service returned a null result (player not found). */
		void onNotFound(String player);

		/** A service call threw. The error parameter is the unwrapped cause where possible. */
		void onError(String player, Throwable error);
	}

	// ── Deps ──────────────────────────────────────────────────────────────
	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final KillClogConfig config;
	private final Listener listener;
	@Nullable private NameAutocompleter nameAutocompleter;

	// ── State ─────────────────────────────────────────────────────────────
	@Nullable private HiscoreResult hiscoreResult;
	@Nullable private ClogResult clogResult;
	@Nullable private String currentLookupRsn;
	@Nullable private String clogLastChanged;

	/** Monotonic counter. Each {@link #start} increments; async callbacks compare against this to gate stale results. */
	private volatile int lookupVersion = 0;

	/** True while a service call has been fired and has not yet resolved (or been superseded). */
	private volatile boolean lookupInFlight = false;

	public LookupSession(HiscoreService hiscoreService, ClogService clogService,
		KillClogConfig config, @Nullable NameAutocompleter nameAutocompleter, Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.nameAutocompleter = nameAutocompleter;
		this.listener = listener;
	}

	/**
	 * Late-bound autocompleter setter. The panel constructs the session before
	 * its own {@link NameAutocompleter} is wired by the plugin's startUp; this
	 * lets the panel forward the eventual instance without re-constructing.
	 */
	public void setNameAutocompleter(@Nullable NameAutocompleter nameAutocompleter)
	{
		this.nameAutocompleter = nameAutocompleter;
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────

	/**
	 * Begin a lookup for {@code player}. No-op if {@code player} is empty or a
	 * lookup is already in flight (early return preserves the existing
	 * pipeline). Otherwise: cache check with a reveal-on-delay if fresh, or a
	 * full parallel hiscore + clog API fan-out if stale or missing.
	 *
	 * <p>Caller must invoke on the EDT. All listener callbacks are also on the
	 * EDT (Timer fires there; service futures are bridged via
	 * {@link SwingUtilities#invokeLater}).
	 */
	public void start(String player, @Nullable String localRsn, @Nullable AccountType localAccountType)
	{
		if (player.isEmpty() || lookupInFlight)
		{
			return;
		}

		lookupInFlight = true;
		currentLookupRsn = player;
		hiscoreResult = null;
		clogResult = null;
		clogLastChanged = null;
		final int thisLookup = ++lookupVersion;
		final boolean isSelf = localRsn != null && localRsn.equalsIgnoreCase(player);
		final boolean isFirstSelfGreeting = isSelf && !config.seenSelfGreeting();

		listener.onLookupStart(player, isSelf, isFirstSelfGreeting);

		// Self-lookups know their own account type; cascade only matters for
		// other players. GIMs only ever appear on the regular hiscores, so the
		// hiscore service must be told to skip the cascade for them.
		final AccountType knownType = isSelf ? localAccountType : null;

		final HiscoreResult cachedHiscore = hiscoreService.getCached(player);
		final ClogResult cachedClog = clogService.getCachedResult(player);
		if (cachedHiscore != null && !hiscoreService.isStale(player))
		{
			Timer revealTimer = new Timer(600, e ->
			{
				if (thisLookup != lookupVersion)
				{
					return;
				}
				hiscoreResult = cachedHiscore;
				lookupInFlight = false;
				if (nameAutocompleter != null)
				{
					nameAutocompleter.addToSearchHistory(player);
				}
				if (cachedClog != null)
				{
					clogResult = cachedClog;
					clogLastChanged = cachedClog.getLastChanged();
				}
				listener.onCachedResult(player, cachedHiscore, cachedClog, isSelf, knownType, isFirstSelfGreeting);
			});
			revealTimer.setRepeats(false);
			revealTimer.start();
			return;
		}

		final AccountType hiscoreType = knownType != null && knownType.isGroupIronman()
			? AccountType.REGULAR : knownType;
		hiscoreService.lookup(player, hiscoreType).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion)
				{
					return;
				}
				lookupInFlight = false;

				if (result == null)
				{
					lookupVersion++;
					listener.onNotFound(player);
					return;
				}

				hiscoreResult = result;
				if (nameAutocompleter != null)
				{
					nameAutocompleter.addToSearchHistory(player);
				}
				listener.onHiscoreResult(player, result, isSelf, knownType, isFirstSelfGreeting);
			})
		).exceptionally(ex ->
		{
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion)
				{
					return;
				}
				lookupVersion++;
				lookupInFlight = false;
				listener.onError(player, ex);
			});
			return null;
		});

		clogService.lookup(player).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion)
				{
					return;
				}
				clogResult = result;
				clogLastChanged = result != null ? result.getLastChanged() : null;
				listener.onClogResult(player, result, isSelf, thisLookup);
			})
		).exceptionally(ex ->
		{
			log.warn("Clog lookup failed", ex);
			return null;
		});
	}

	/**
	 * Force-cancel any in-flight lookup so the next {@link #start} call is not
	 * rejected. Bumps {@link #lookupVersion} so already-spawned async callbacks
	 * see themselves as stale and short-circuit. The original
	 * {@code KillClogPanel.onBulkCaptureComplete} pattern: when a fresh RSN is
	 * captured, we want to abandon whatever's mid-flight and start over.
	 */
	public void cancelInFlight()
	{
		if (lookupInFlight)
		{
			lookupVersion++;
			lookupInFlight = false;
		}
	}

	/**
	 * Replace the current lookup state without going through the async
	 * pipeline. Used by the comparison swap path: when the user clicks the
	 * red player in compare mode, the panel adopts the red player's already-
	 * loaded results into the solo view in one synchronous step.
	 */
	public void adoptState(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable String name)
	{
		this.hiscoreResult = hiscore;
		this.clogResult = clog;
		this.currentLookupRsn = name;
		this.clogLastChanged = clog != null ? clog.getLastChanged() : null;
	}

	// ── Read-only state ───────────────────────────────────────────────────
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
		return lookupVersion;
	}

	public boolean isLookupInFlight()
	{
		return lookupInFlight;
	}
}
