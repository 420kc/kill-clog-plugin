/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the async lookup pipeline for Kill Clog: cache check, parallel hiscore +
 * clog API calls, version-stamped result gating, in-flight guard. Pure data +
 * lifecycle. Zero Swing imports; UI is downstream via {@link Listener}.
 *
 * Extracted from KillClogPanel as refactor cut 1.
 */
package com.killclog;

import javax.annotation.Nullable;

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

		/** Live clog result arrived from the API (independent of hiscore timing). */
		void onClogResult(String player, ClogResult clog, boolean isSelf, int lookupVersion);

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
	 * Begin a lookup for {@code player}. No-op if a lookup is already in
	 * flight (early return preserves the existing pipeline). Otherwise:
	 * cache check + reveal-on-delay if fresh, full API fan-out if stale.
	 *
	 * <p>TODO(refactor-cut-1): migrate body of KillClogPanel.doLookup() here
	 * with mechanical translation of direct UI calls into listener events.
	 * Tracked in REFACTOR-CUT-1.md.
	 */
	public void start(String player, @Nullable String localRsn, @Nullable AccountType localAccountType)
	{
		throw new UnsupportedOperationException("LookupSession.start not yet wired; tracked in REFACTOR-CUT-1.md");
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
