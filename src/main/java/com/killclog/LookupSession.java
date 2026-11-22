/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 */
package com.killclog;

import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.swing.Timer;
import lombok.Getter;

/**
 * One lookup of a player for the panel: it answers from the services' caches while they are fresh and asks the
 * hiscores, the log and the Combat Achievements otherwise, holds a finished hiscore briefly for its log, and tells its
 * {@link Listener} each step. It never touches a widget. Each request carries the stamp of the lookup it was made for
 * ({@link LookupFanout}), so one a newer {@link #start} has overtaken can't draw over it.
 *
 * {@link #start} runs on the Swing thread, and every listener call arrives there.
 */
public class LookupSession
{
	/** What a lookup tells the panel, always on the Swing thread. */
	public interface Listener
	{
		/** A lookup began: the panel clears the last one and says it is searching. */
		void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting);

		/**
		 * The hiscore service still held a fresh answer, so the hiscores weren't asked: that answer, and the log if one
		 * is held.
		 */
		void onCachedResult(String player, HiscoreResult hiscore, @Nullable ClogResult clog,
			@Nullable AccountType knownType);

		/** The hiscores answered. */
		void onHiscoreResult(String player, HiscoreResult hiscore, @Nullable AccountType knownType);

		/**
		 * The log arrived, whenever the hiscores do. {@code clog} is null when no source has this player's log: the
		 * listener words that (the setup notice for your own, nothing for anyone else's) and asks for the player's name
		 * as they write it.
		 */
		void onClogResult(String player, @Nullable ClogResult clog, boolean isSelf, int lookupVersion);

		/**
		 * The Combat Achievements arrived, whenever the others do, from your own game or RuneProfile. {@code ca} is
		 * null when neither has any (never synced to RuneProfile, or it didn't answer); a card then shows the tiers the
		 * player's Kill Clog sync carries, or none.
		 */
		void onCaResult(String player, @Nullable CombatAchievementResult ca, boolean isSelf, int lookupVersion);

		/** The hiscores have no such player. */
		void onNotFound(String player);

		/** A request failed; {@code error} is its cause where one could be found. */
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
	@Getter
	@Nullable private CombatAchievementResult caResult;
	@Getter
	@Nullable private String currentLookupRsn;
	private int localClogRevision;
	private UnaryOperator<HiscoreResult> rankView = UnaryOperator.identity();

	/** Longest a finished hiscore waits for its clog before showing uncolored. */
	static final int CLOG_HOLD_MS = 2500;
	private boolean clogSettled;
	@Nullable private Runnable heldReveal;
	/** The lookup the held reveal belongs to; -1 while nothing is held. */
	private int heldLookup = -1;
	@Nullable private Timer holdTimer;

	public LookupSession(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, KillclogService killclogService,
		KillClogConfig config, Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.listener = listener;
		this.fanout = new LookupFanout(hiscoreService, clogService, runeProfileService, killclogService);
	}

	/** Which game the next lookups read: a League, or null for the main game. */
	void readLeague(@Nullable String league, @Nullable LocalClogCache leagueLog)
	{
		fanout.readLeague(league, leagueLog);
	}

	/** Whether this player syncs to Kill Clog, by its sync list. */
	boolean onKillclog(String player)
	{
		return fanout.onKillclog(player);
	}

	/** The game's own pages, which every log these lookups show is laid on. */
	void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		fanout.setClogIndex(clogIndex);
	}

	@Nullable
	String league()
	{
		return fanout.getLeague();
	}

	/** Setup applies only to a log this client keeps; a League read from another world has none here. */
	boolean readsOwnLog(@Nullable String player)
	{
		return player != null && fanout.readsOwnLog(player) && !frozen(getHiscoreResult());
	}

	/** Setup is offered for your own name's log, when this client keeps it. */
	boolean offersSetup(@Nullable String localRsn, @Nullable String name)
	{
		return RsnInputPolicy.sameName(localRsn, name) && readsOwnLog(name);
	}

	/** Wire (or rewire) the autocompleter that records search history on each successful lookup. */
	public void setNameAutocompleter(@Nullable NameAutocompleter nameAutocompleter)
	{
		this.nameAutocompleter = nameAutocompleter;
	}

	// Lifecycle

	/**
	 * Begin a lookup for {@code player}, in place of any still in flight: its stamp goes stale, so nothing it fetched
	 * paints (a right-click lookup on someone new never shows the last name's results under the new one). An empty name
	 * does nothing. A fresh cached hiscore is shown after a short pause; otherwise the hiscores are asked. The log and
	 * the Combat Achievements are asked either way.
	 *
	 * Call it on the Swing thread.
	 */
	public void start(String player, @Nullable String localRsn, @Nullable AccountType localAccountType)
	{
		if (player.isEmpty())
		{
			return;
		}

		currentLookupRsn = player;
		hiscoreResult = null;
		clogResult = null;
		caResult = null;
		clearHold();
		clogSettled = false;
		final int thisLookup = fanout.begin();
		final boolean isSelf = RsnInputPolicy.sameName(localRsn, player);
		final boolean isFirstSelfGreeting = isSelf && !config.seenSelfGreeting();

		listener.onLookupStart(player, isSelf, isFirstSelfGreeting);

		askCa(player, isSelf, thisLookup);

		// Your own account type comes from the game, so the hiscores needn't try each board to learn it; they must not
		// for a Group Ironman, who is on the regular board only.
		final AccountType knownType = isSelf ? localAccountType : null;

		// The shared cache holds main-game rows only.
		final HiscoreResult cachedHiscore = fanout.getLeague() == null ? hiscoreService.getCached(player) : null;
		final ClogResult cachedClog = fanout.onGamePages(clogService.getCachedResult(player));
		if (cachedHiscore != null && !hiscoreService.isStale(player))
		{
			startClogLookup(player, isSelf, thisLookup);
			Timer revealTimer = new Timer(600, e ->
			{
				if (!fanout.current(thisLookup))
				{
					return;
				}
				fanout.settle();
				revealWithClog(thisLookup, cachedClog != null, () ->
				{
					showHiscore(player, cachedHiscore);
					ClogResult displayClog = clogResult;
					if (displayClog == null && cachedClog != null)
					{
						clogResult = cachedClog;
						displayClog = cachedClog;
					}
					listener.onCachedResult(player, cachedHiscore, displayClog, knownType);
				});
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
				// Nothing still in the air for the log or the Combat Achievements may answer: a player not found must
				// not show part of one afterwards.
				adoptState(null, null, null, null);
				listener.onNotFound(player);
				return;
			}
			// Settled now, so a new search is never blocked by the hold below.
			fanout.settle();
			revealWithClog(thisLookup, false, () ->
			{
				showHiscore(player, result);
				listener.onHiscoreResult(player, result, knownType);
			});
		}, ex ->
		{
			adoptState(null, null, null, null);
			listener.onError(player, ex);
		});

		startClogLookup(player, isSelf, thisLookup);
	}

	/** Whether this player's log hasn't loaded yet, rather than isn't there ({@link ClogService#templeUnanswered}). */
	boolean logUnanswered(@Nullable String player)
	{
		return clogService.templeUnanswered(player);
	}

	/** The hiscore this lookup shows, its name kept in the search history. */
	private void showHiscore(String player, HiscoreResult result)
	{
		hiscoreResult = result;
		if (nameAutocompleter != null)
		{
			nameAutocompleter.addToSearchHistory(player);
		}
	}

	/**
	 * Asks for the log. A failure leaves it empty. Every outcome settles the log, so a held hiscore is never left
	 * waiting.
	 */
	private void startClogLookup(String player, boolean isSelf, int thisLookup)
	{
		final int revisionAtFire = localClogRevision;
		fanout.fetchClog(player, isSelf, thisLookup, result ->
		{
			// A completed local capture supersedes a result already in flight.
			if (isSelf && revisionAtFire != localClogRevision)
			{
				result = fanout.ownLog(player);
				if (result == null || !result.isFromLocal())
				{
					settleClog(thisLookup);
					return;
				}
			}
			clogResult = result;
			settleClog(thisLookup);
			listener.onClogResult(player, result, isSelf, thisLookup);
		}, () -> settleClog(thisLookup));
	}

	/**
	 * Show a finished hiscore together with its log, so cells never paint white and recolor a moment later. A slow
	 * source delays it by at most {@link #CLOG_HOLD_MS}; after that the hiscore shows alone and the log colors it when
	 * it arrives.
	 */
	private void revealWithClog(int thisLookup, boolean clogReady, Runnable reveal)
	{
		if (clogSettled || clogReady)
		{
			reveal.run();
			return;
		}
		heldReveal = reveal;
		heldLookup = thisLookup;
		holdTimer = new Timer(CLOG_HOLD_MS, e -> releaseHold(thisLookup));
		holdTimer.setRepeats(false);
		holdTimer.start();
	}

	/** The clog lane resolved, with data or without: release a held hiscore first. */
	private void settleClog(int thisLookup)
	{
		clogSettled = true;
		releaseHold(thisLookup);
	}

	private void releaseHold(int thisLookup)
	{
		// A late release from an earlier lookup must leave the next one's hold alone.
		if (heldLookup != thisLookup)
		{
			return;
		}
		Runnable reveal = heldReveal;
		clearHold();
		if (fanout.current(thisLookup))
		{
			reveal.run();
		}
	}

	private void clearHold()
	{
		if (holdTimer != null)
		{
			holdTimer.stop();
		}
		holdTimer = null;
		heldReveal = null;
		heldLookup = -1;
	}

	/**
	 * The Combat Achievements, asked alongside the hiscore and the log (and on the cached path), waiting on neither.
	 * RuneProfileService keeps its own cache and never asks twice at once.
	 */
	private void askCa(String player, boolean isSelf, int lookup)
	{
		fanout.fetchCa(player, lookup, ca ->
		{
			caResult = ca;
			listener.onCaResult(player, ca, isSelf, lookup);
		});
	}

	/** Whether the lookup on screen is of the player logged in. */
	private boolean showsYou(String player, @Nullable String localRsn)
	{
		return RsnInputPolicy.sameName(localRsn, player) && RsnInputPolicy.sameName(currentLookupRsn, player);
	}

	/**
	 * Take your own Combat Achievements as the game has them now, with no new lookup. A login looks you up at once,
	 * while the game's tiers are read only once the world has settled, so that lookup began with last session's.
	 */
	void refreshLocalCa(String player, @Nullable String localRsn)
	{
		if (showsYou(player, localRsn))
		{
			askCa(player, true, fanout.version());
		}
	}

	/** Show your own log as it is now, on the Swing thread, with no new lookup. */
	void refreshLocalClog(String player, @Nullable String localRsn)
	{
		if (!showsYou(player, localRsn)) return;
		ClogResult local = fanout.ownLog(player);
		if (local == null || !local.isFromLocal()) return;
		localClogRevision++;
		clogResult = local;
		settleClog(fanout.version());
		listener.onClogResult(player, local, true, fanout.version());
	}

	/** Abandon any lookup in flight: its stamp goes stale, so whatever it still has in the air draws nothing. */
	public void cancelInFlight()
	{
		clearHold();
		fanout.invalidate();
	}

	/**
	 * Take results already loaded as the lookup's own, with no request made; all nulls clears it, as a failure or a
	 * shutdown does.
	 */
	public void adoptState(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable CombatAchievementResult ca, @Nullable String name)
	{
		// Nothing still in the air for the player being replaced may answer: its log and Combat Achievements can
		// outlast its hiscore, and a late one would draw over what was just adopted.
		clearHold();
		fanout.invalidate();
		this.hiscoreResult = hiscore;
		this.clogResult = clog;
		this.caResult = ca;
		this.currentLookupRsn = name;
	}

	/** Quietly discard the session before panel shutdown or reuse. */
	public void reset()
	{
		adoptState(null, null, null, null);
	}

	// Read-only state
	@Nullable
	public HiscoreResult getHiscoreResult()
	{
		return rankView(hiscoreResult);
	}

	HiscoreResult getNativeHiscoreResult()
	{
		return hiscoreResult;
	}

	void setRankView(UnaryOperator<HiscoreResult> rankView)
	{
		this.rankView = rankView;
	}

	HiscoreResult rankView(HiscoreResult result)
	{
		return result != null ? rankView.apply(result) : null;
	}

	/** None beside a frozen row: today's log would read as earned by the stats of then. */
	@Nullable
	public ClogResult getClogResult()
	{
		return frozen(getHiscoreResult()) ? null : clogResult;
	}

	/** When the shown log last changed, as its provider dates it. */
	@Nullable
	String getClogLastChanged()
	{
		return clogResult != null ? clogResult.getLastChanged() : null;
	}

	ClogResult getNativeClogResult()
	{
		return clogResult;
	}

	static boolean frozen(@Nullable HiscoreResult view)
	{
		return view != null && view.isFrozen();
	}

	public int getLookupVersion()
	{
		return fanout.version();
	}

}
