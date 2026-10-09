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
	private String queuedMode;
	private boolean inFlightManual;
	private final AtomicInteger generation = new AtomicInteger();

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

	int beginAttempt(boolean manual)
	{
		return beginAttempt(manual, null);
	}

	/** A queued click keeps its game over later automatic pushes; the latest click wins among clicks. */
	synchronized int beginAttempt(boolean manual, String mode)
	{
		int gen = generation.get();
		if (!inFlight.compareAndSet(false, true))
		{
			if (manual || !queuedManual)
			{
				queuedMode = mode;
			}
			queuedManual |= manual;
			queued.set(true);
			return -1;
		}
		inFlightManual = manual;
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

	/** A queued push: whether a user asked for it, and its game (null follows the world). */
	static final class Intent
	{
		final boolean manual;
		final String mode;

		Intent(boolean manual, String mode)
		{
			this.manual = manual;
			this.mode = mode;
		}
	}

	/** Null means no queued push. */
	synchronized Intent consumeQueuedIntent()
	{
		if (!queued.compareAndSet(true, false))
		{
			return null;
		}
		Intent intent = new Intent(queuedManual, queuedMode);
		queuedManual = false;
		queuedMode = null;
		return intent;
	}

	/** Automatic sync off: an automatic push not yet sent, or one queued, never goes; a click still does. */
	synchronized void cancelAutomatic()
	{
		if (inFlight.get() && !inFlightManual)
		{
			generation.incrementAndGet();
		}
		if (!queuedManual)
		{
			queued.set(false);
			queuedMode = null;
		}
	}

	/** Opt-out / shutdown: silence prior eras and forget any queued intent. */
	synchronized void cancel()
	{
		generation.incrementAndGet();
		queued.set(false);
		queuedManual = false;
		queuedMode = null;
	}
}
