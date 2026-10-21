package com.killclog;

import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The killclog.com sync's one slot: one attempt in the air at a time, numbered, until its round trip completes; the
 * push asked for meanwhile, held whole (several go as one, by the push's own rule) and handed over once the slot frees;
 * and the era that an opt-out, a logout or a shutdown moves on, so anything begun before can never claim the slot,
 * commit its request or speak. Every release names its attempt, so a stale one can never free a newer one's slot.
 * Its own lock, so the final request enqueue and a cancellation have one total order.
 */
final class KillclogSyncGate<P>
{
	static final int BUSY = -1;
	static final int STALE = -2;

	// Which pushes Automatic sync turned off leaves alone, and how two waiting go as one.
	private final Predicate<P> deliberate;
	private final BinaryOperator<P> merge;
	private int era;
	private int attempts;
	// The attempt in the air (0 for none), its era and push, and whether Automatic sync turned off silenced it.
	private int inFlight;
	private int inFlightEra;
	private P inFlightPush;
	private boolean silenced;
	private P queued;

	KillclogSyncGate(Predicate<P> deliberate, BinaryOperator<P> merge)
	{
		this.deliberate = deliberate;
		this.merge = merge;
	}

	/** The era now; a push carries the one it was asked in. */
	synchronized int era()
	{
		return era;
	}

	/**
	 * Claim the slot for a push asked in {@code pushEra}: the attempt's number, or {@link #STALE} when its era has
	 * passed (it is dropped), or {@link #BUSY} while another attempt is in the air (it waits, merged with any already
	 * waiting).
	 */
	synchronized int begin(P push, int pushEra)
	{
		if (pushEra != era)
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
		inFlightPush = push;
		silenced = false;
		return inFlight;
	}

	/** Whether this attempt is the one in the air, in its era, not silenced: its work may go on. */
	synchronized boolean live(int attempt)
	{
		return attempt == inFlight && inFlightEra == era && !silenced;
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
		inFlightPush = null;
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
	 * Automatic sync off: an automatic push waiting never goes, and one in the air is silenced; a deliberate one goes
	 * on. The silenced attempt's number, or 0.
	 */
	synchronized int silenceAutomatic()
	{
		int quieted = 0;
		if (inFlight != 0 && !silenced && !deliberate.test(inFlightPush))
		{
			silenced = true;
			quieted = inFlight;
		}
		if (queued != null && !deliberate.test(queued))
		{
			queued = null;
		}
		return quieted;
	}

	/** Opt-out, logout, shutdown: the era moves on, so nothing begun before claims, commits or speaks. */
	synchronized void cancel()
	{
		era++;
		queued = null;
	}
}
