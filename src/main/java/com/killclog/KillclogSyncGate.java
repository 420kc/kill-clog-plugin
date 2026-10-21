package com.killclog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

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
	private Session queuedSession;
	private boolean inFlightManual;
	private final AtomicInteger generation = new AtomicInteger();

	/**
	 * Claim the single-flight slot.
	 *
	 * @return the generation to carry through the attempt, or -1 when a
	 *         request is already in flight - the intent is remembered and
	 *         {@link #consumeQueued()} will report it once the slot frees. A queued click keeps its game over later
	 *         automatic pushes; the latest click wins among clicks.
	 */
	synchronized int beginAttempt(boolean manual, String mode, Session session)
	{
		int gen = generation.get();
		if (!inFlight.compareAndSet(false, true))
		{
			if (manual || !queuedManual)
			{
				queuedMode = mode;
				queuedSession = session;
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

	/** A queued push: whether a user asked for it, its game (null follows the world), and the login it is for. */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Intent
	{
		final boolean manual;
		final String mode;
		final Session session;
	}

	/** Null means no queued push. */
	synchronized Intent consumeQueuedIntent()
	{
		if (!queued.compareAndSet(true, false))
		{
			return null;
		}
		Intent intent = new Intent(queuedManual, queuedMode, queuedSession);
		queuedManual = false;
		queuedMode = null;
		queuedSession = null;
		return intent;
	}

	/**
	 * Automatic sync off: an automatic push not yet sent, or one queued, never goes; a click still does. True when it
	 * silenced an automatic push already in the air.
	 */
	synchronized boolean cancelAutomatic()
	{
		boolean silenced = inFlight.get() && !inFlightManual;
		if (silenced)
		{
			generation.incrementAndGet();
		}
		if (!queuedManual)
		{
			queued.set(false);
			queuedMode = null;
			queuedSession = null;
		}
		return silenced;
	}

	/** Opt-out / shutdown: silence prior eras and forget any queued intent. */
	synchronized void cancel()
	{
		generation.incrementAndGet();
		queued.set(false);
		queuedManual = false;
		queuedMode = null;
		queuedSession = null;
	}
}
