package com.killclog;

import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * The killclog.com sync's one slot: one attempt in the air at a time, numbered, until its round trip completes; the
 * push asked for meanwhile, held whole (several go as one, by the push's own rule) and handed over once the slot frees;
 * and the era that an opt-out, a logout or a shutdown moves on, so anything begun before can never claim the slot,
 * commit its request or speak; and Automatic sync's own era, which turning it off moves, so an automatic push asked
 * before can never claim, stay live or commit either. Every release names its attempt, so a stale one can never free
 * a newer one's slot.
 * Its own lock, so the final request enqueue and a cancellation have one total order.
 */
final class KillclogSyncGate<P>
{
	static final int BUSY = -1;
	static final int STALE = -2;

	// How two pushes waiting go as one.
	private final BinaryOperator<P> merge;
	private int era;
	private int automaticEra;
	private int attempts;
	// The attempt in the air (0 for none), its eras (the automatic one NONE for a deliberate push) and its push.
	private int inFlight;
	private int inFlightEra;
	private int inFlightAutomaticEra;
	private P queued;
	static final int DELIBERATE = -1;

	KillclogSyncGate(BinaryOperator<P> merge)
	{
		this.merge = merge;
	}

	/** The era now; a push carries the one it was asked in. */
	synchronized int era()
	{
		return era;
	}

	/** Automatic sync's era now; an automatic push carries the one it was asked in. */
	synchronized int automaticEra()
	{
		return automaticEra;
	}

	/**
	 * Claim the slot for a push asked in {@code pushEra} and, for an automatic one, {@code pushAutomaticEra}
	 * ({@link #DELIBERATE} for a click's or a character's): the attempt's number, or {@link #STALE} when an era it was
	 * asked in has passed (it is dropped), or {@link #BUSY} while another attempt is in the air (it waits, merged with
	 * any already waiting).
	 */
	synchronized int begin(P push, int pushEra, int pushAutomaticEra)
	{
		if (pushEra != era || pushAutomaticEra != DELIBERATE && pushAutomaticEra != automaticEra)
		{
			return STALE;
		}
		if (inFlight != 0)
		{
			queued = queued == null ? push : merge.apply(queued, push);
			return BUSY;
		}
		inFlight = ++attempts;
		inFlightEra = era;
		inFlightAutomaticEra = pushAutomaticEra;
		return inFlight;
	}

	/** Whether this attempt is the one in the air, in its eras: its work may go on. */
	synchronized boolean live(int attempt)
	{
		return attempt == inFlight && inFlightEra == era
			&& (inFlightAutomaticEra == DELIBERATE || inFlightAutomaticEra == automaticEra);
	}

	/**
	 * Commit the final non-blocking request enqueue only while this attempt is live. One lock with {@link #cancel()},
	 * so an opt-out has one total order with the enqueue: before means no request; after means the request already
	 * enqueued stays silent on completion.
	 */
	synchronized <T> T commitIfCurrent(int attempt, Supplier<T> commit)
	{
		return live(attempt) ? commit.get() : null;
	}

	/** Whether an attempt holds the slot. */
	synchronized boolean busy()
	{
		return inFlight != 0;
	}

	/**
	 * The attempt's round trip is over, or it never went: its slot frees, if it is still the one holding it. True when
	 * it was live (its outcome may be acted on and said).
	 */
	synchronized boolean release(int attempt)
	{
		if (attempt != inFlight)
		{
			return false;
		}
		boolean wasLive = live(attempt);
		inFlight = 0;
		return wasLive;
	}

	/** The push that waited for the slot, no longer waiting; null for none. */
	synchronized P takeQueued()
	{
		P push = queued;
		queued = null;
		return push;
	}

	/**
	 * Automatic sync off: its era moves, so no automatic push asked before claims, stays live or commits; one waiting
	 * for the slot is refused when it comes to claim it. The automatic attempt this silenced, or 0.
	 */
	synchronized int withdrawAutomatic()
	{
		boolean quieted = inFlight != 0 && inFlightAutomaticEra == automaticEra;
		automaticEra++;
		return quieted ? inFlight : 0;
	}

	/** Opt-out, logout, shutdown: the era moves on, so nothing begun before claims, commits or speaks; the new era. */
	synchronized int cancel()
	{
		queued = null;
		return ++era;
	}
}
