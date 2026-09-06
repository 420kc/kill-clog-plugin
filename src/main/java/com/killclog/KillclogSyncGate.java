package com.killclog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * State machine for the killclog.com sync push: single-flight until the HTTP
 * round trip completes, a generation stamp so cancelled eras stay silent, and
 * a queued-intent so a push requested while the slot is occupied (opt-out then
 * opt-in during a live request) fires as soon as the slot frees instead of
 * being dropped.
 */
final class KillclogSyncGate
{
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private final AtomicBoolean queued = new AtomicBoolean();
	private boolean queuedManual;
	private final AtomicInteger generation = new AtomicInteger();
	// One server-advised contention retry per episode: consumed by the first
	// 409, restored at every terminal outcome (success, failure, abort,
	// cancel) so a later independent episode always starts with its credit.
	private final AtomicBoolean retryCredit = new AtomicBoolean(true);

	/**
	 * Claim the single-flight slot.
	 *
	 * @return the generation to carry through the attempt, or -1 when a
	 *         request is already in flight - the intent is remembered and
	 *         {@link #consumeQueued()} will report it once the slot frees.
	 */
	int beginAttempt()
	{
		return beginAttempt(false);
	}

	synchronized int beginAttempt(boolean manual)
	{
		int gen = generation.get();
		if (!inFlight.compareAndSet(false, true))
		{
			queuedManual |= manual;
			queued.set(true);
			return -1;
		}
		return gen;
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
		retryCredit.set(true);
	}

	/**
	 * Claim the one contention-retry this episode is allowed.
	 *
	 * @return true exactly once between terminal outcomes - a second 409 in
	 *         the same episode gets false and must surface as a failure.
	 */
	boolean consumeRetryCredit()
	{
		return retryCredit.compareAndSet(true, false);
	}

	/** A terminal outcome ends the episode; the next one starts with credit. */
	void restoreRetryCredit()
	{
		retryCredit.set(true);
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

	/** @return true exactly once per remembered push intent. */
	boolean consumeQueued()
	{
		return consumeQueuedIntent() != null;
	}

	/** Null means no queued push; otherwise preserve whether a user requested it. */
	synchronized Boolean consumeQueuedIntent()
	{
		if (!queued.compareAndSet(true, false))
		{
			return null;
		}
		boolean manual = queuedManual;
		queuedManual = false;
		return manual;
	}

	/** Opt-out / shutdown: silence prior eras and forget any queued intent. */
	synchronized void cancel()
	{
		generation.incrementAndGet();
		queued.set(false);
		queuedManual = false;
		retryCredit.set(true);
	}
}
