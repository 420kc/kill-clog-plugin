package com.killclog;

import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * What every lookup of a player shares: each request is stamped with the lookup it belongs to, has a time limit, and
 * hands its answer to the Swing thread only while that lookup is still the current one. The panel's lookup and the
 * comparison's each own one, counting their lookups apart, and each decides for itself what to show.
 *
 * Requests start on the Swing thread and the services answer on their own; {@code version} and {@code inFlight} are
 * volatile so both sides read them.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class LookupFanout
{
	static final int CA_TIMEOUT_SECONDS = 10;
	// Tests shorten it.
	static int hiscoreTimeoutSeconds = 15;

	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final KillclogService killclogService;

	/** Monotonic counter. Each {@link #begin} increments; callbacks compare their stamp against it. */
	private volatile int version = 0;

	/** True while a fetch generation is unresolved (or until superseded). */
	@Getter(AccessLevel.PACKAGE)
	private volatile boolean inFlight = false;

	// EDT-owned: the League these lookups read (null: the main game) and the player's own
	// open log for it. League reads come from League Hiscores and Kill Clog only.
	@Getter(AccessLevel.PACKAGE)
	@Nullable private String league;
	@Nullable private LocalClogCache leagueLog;
	// The game's own pages once the client has read the log: every log a lookup shows is laid on them.
	@Setter(AccessLevel.PACKAGE)
	@Nullable private volatile ClogIndex clogIndex;

	void readLeague(@Nullable String league, @Nullable LocalClogCache leagueLog)
	{
		this.league = league;
		this.leagueLog = leagueLog;
	}

	/**
	 * Whether this client keeps this player's log for the game read here: the main game's while the main store serves
	 * them (not yet on a login made straight onto a League world), or the League's on its own world.
	 */
	boolean readsOwnLog(String player)
	{
		return league == null ? clogService.keepsOwnLog(player) : leagueLog != null;
	}

	/** Your own log for the game these lookups read, as it is now. */
	@Nullable
	ClogResult ownLog(String player)
	{
		return onGamePages(league == null ? clogService.getCachedResult(player)
			: leagueLog != null ? clogService.localResult(leagueLog, player) : null);
	}

	/** Whether this player syncs to Kill Clog, by its sync list. */
	boolean onKillclog(String player)
	{
		return killclogService.isSynced(player);
	}

	/** A log on the game's own pages ({@link ClogIndex#laid}). */
	@Nullable
	ClogResult onGamePages(@Nullable ClogResult clog)
	{
		ClogIndex index = clogIndex;
		return index != null ? index.laid(clog) : clog;
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

	/** Nothing asked so far may answer any more, the Combat Achievements and a late TempleOSRS log included. */
	void invalidate()
	{
		version++;
		inFlight = false;
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
	 * Combat Achievements, from RuneProfile or your own from the game; a League has none. A failure or a timeout is
	 * logged and answers nothing, so the cards show none.
	 */
	void fetchCa(String player, int stamp, Consumer<CombatAchievementResult> onResult)
	{
		if (league != null)
		{
			return;
		}
		runeProfileService.lookup(player)
			.copy()
			.orTimeout(CA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.thenAccept(ca -> onEdtIfCurrent(stamp, () -> onResult.accept(ca)))
			.exceptionally(ex ->
			{
				log.warn("CA lookup failed", ex);
				return null;
			});
	}

	/**
	 * The hiscores. A null answer means no such player, which the caller words; a failure goes to {@code onError}. Each
	 * reaches the Swing thread only while its lookup is current.
	 */
	void fetchHiscore(String player, @Nullable AccountType knownType, int stamp,
		Consumer<HiscoreResult> onResult, Consumer<Throwable> onError)
	{
		(league != null ? hiscoreService.lookupLeague(player)
			: hiscoreService.lookup(player, knownType))
			.copy()
			.orTimeout(hiscoreTimeoutSeconds, TimeUnit.SECONDS)
			.thenAccept(result -> onEdtIfCurrent(stamp, () -> onResult.accept(result)))
			.exceptionally(ex ->
			{
				onEdtIfCurrent(stamp, () -> onError.accept(ex));
				return null;
			});
	}

	/**
	 * The player's log from every source at once, chosen as {@link ClogProviderFanout} rules; a League's comes from
	 * killclog.com alone. Where this client keeps your own log, RuneProfile and killclog.com aren't asked for it: what
	 * the game showed you stands. A failure is logged and, when {@code onError} is given, told on the Swing thread
	 * while the lookup is current; the panel's lookup uses it to show a hiscore it was holding for the log.
	 */
	void fetchClog(String player, boolean isSelf, int stamp,
		Consumer<ClogResult> onResult, @Nullable Runnable onError)
	{
		// Your own log stands in for the remote sites only where this client keeps it.
		boolean own = isSelf && readsOwnLog(player);
		ClogProviderFanout.Answers answers = league == null ? ClogProviderFanout.answers(own,
			() -> clogService.lookup(player), () -> runeProfileService.lookupClog(player),
			() -> killclogService.lookupClog(player)) : null;
		CompletableFuture<Void> delivered = (answers != null ? answers.first
			: ClogProviderFanout.bounded(own ? clogService.lookupLocal(leagueLog, player)
			: killclogService.lookupClog(player, league)))
			.thenApply(this::onGamePages)
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
		if (answers != null)
		{
			// A late TempleOSRS answer lands in the open lookup after the first, never before it.
			delivered.thenCompose(done -> answers.late).thenApply(this::onGamePages).thenAccept(late ->
			{
				if (late != null)
				{
					onEdtIfCurrent(stamp, () -> onResult.accept(late));
				}
			});
		}
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
