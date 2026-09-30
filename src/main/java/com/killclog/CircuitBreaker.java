package com.killclog;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * One provider's circuit breaker: five failures inside a minute trip it, and it
 * then short-circuits for a cooldown that doubles on each trip, up to 30 minutes.
 */
@Slf4j
final class CircuitBreaker
{
	private static final int THRESHOLD = 5;
	private static final long WINDOW_MS = 60 * 1000;
	private static final long COOLDOWN_MS = 60 * 1000;
	private static final long MAX_COOLDOWN_MS = 30 * 60 * 1000;

	private final String provider;
	private final long[] recentFailures = new long[THRESHOLD];
	private int failureIndex;
	private volatile long trippedAt;
	private volatile long cooldownMs = COOLDOWN_MS;

	CircuitBreaker(String provider)
	{
		this.provider = provider;
	}

	synchronized void failure()
	{
		long now = System.currentTimeMillis();
		recentFailures[failureIndex] = now;
		failureIndex = (failureIndex + 1) % THRESHOLD;
		// The next slot holds the oldest of the last five failures.
		long oldest = recentFailures[failureIndex];
		if (oldest > 0 && now - oldest <= WINDOW_MS)
		{
			trippedAt = now;
			cooldownMs = Math.min(cooldownMs * 2, MAX_COOLDOWN_MS);
			log.warn("{} circuit breaker tripped ({} failures in {}s), cooldown {}s",
				provider, THRESHOLD, WINDOW_MS / 1000, cooldownMs / 1000);
		}
	}

	/** Closes a tripped or escalated breaker; otherwise recent failures keep counting. */
	synchronized void success()
	{
		if (trippedAt > 0 || cooldownMs > COOLDOWN_MS)
		{
			reset();
		}
	}

	/** Forget every failure, close the breaker and drop the cooldown to its first tier. */
	synchronized void reset()
	{
		Arrays.fill(recentFailures, 0);
		failureIndex = 0;
		trippedAt = 0;
		cooldownMs = COOLDOWN_MS;
	}

	boolean isOpen()
	{
		long tripped = trippedAt;
		return tripped > 0 && System.currentTimeMillis() - tripped < cooldownMs;
	}
}
