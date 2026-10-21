package com.killclog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * State machine for the killclog.com sync push: single-flight until the HTTP
 * round trip completes, a generation stamp so cancelled eras stay silent, and
 * the push asked for while the slot is occupied (opt-out then opt-in during a
 * live request), held whole and fired as soon as the slot frees instead of
 * being dropped. Several asked meanwhile go as one, by the push's own rule.
 */
final class KillclogSyncGate<P>
{
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private final AtomicInteger generation = new AtomicInteger();
	// Which pushes Automatic sync turned off leaves alone, and how two waiting go as one.
	private final Predicate<P> deliberate;
	private final BinaryOperator<P> merge;
	private P inFlightPush;
	private P queued;

	KillclogSyncGate(Predicate<P> deliberate, BinaryOperator<P> merge)
	{
		this.deliberate = deliberate;
		this.merge = merge;
	}

	/**
	 * Claim the single-flight slot.
	 *
	 * @return the generation to carry through the attempt, or -1 when a request is already in flight: the push waits,
	 *         merged with any already waiting, and {@link #consumeQueued()} hands it over once the slot frees.
	 */
	synchronized int beginAttempt(P push)
	{
		int gen = generation.get();
		if (!inFlight.compareAndSet(false, true))
		{
			queued = queued == null ? push : merge.apply(queued, push);
			return -1;
		}
		inFlightPush = push;
		return gen;
	}

	/** Whether an attempt holds the slot. */
	boolean busy()
	{
		return inFlight.get();
	}

	/** Whether deferred pre-dispatch work still belongs to the live era. */
	boolean isCurrent(int gen)
	{
		return gen == generation.get();
	}

	/**
	 * Commit the final non-blocking request enqueue only while this attempt's
	 * generation is still authorized. Synchronized with {@link #cancel()} so
	 * opt-out has one total order with the enqueue: before means no request;
	 * after means the already-enqueued request remains silent on completion.
	 */
	synchronized <T> T commitIfCurrent(int gen, Supplier<T> commit)
	{
		if (gen != generation.get())
		{
			return null;
		}
		return commit.get();
	}

	/** Release the slot without a round trip (unusable state: no rsn/hash). */
	void abortAttempt()
	{
		inFlight.set(false);
	}

	/**
	 * Release the slot after a round trip.
	 *
	 * @return true when the completing attempt belongs to the current
	 *         generation (its feedback may be surfaced).
	 */
	boolean complete(int gen)
	{
		inFlight.set(false);
		return gen == generation.get();
	}

	/** The push that waited for the slot, no longer waiting; null for none. */
	synchronized P consumeQueued()
	{
		P push = queued;
		queued = null;
		return push;
	}

	/**
	 * Automatic sync off: an automatic push not yet sent, or one waiting, never goes; a deliberate one still does.
	 * True when it silenced an automatic push already in the air.
	 */
	synchronized boolean cancelAutomatic()
	{
		boolean silenced = inFlight.get() && inFlightPush != null && !deliberate.test(inFlightPush);
		if (silenced)
		{
			generation.incrementAndGet();
		}
		if (queued != null && !deliberate.test(queued))
		{
			queued = null;
		}
		return silenced;
	}

	/** Opt-out / shutdown: silence prior eras and forget any queued push. */
	synchronized void cancel()
	{
		generation.incrementAndGet();
		queued = null;
	}
}
