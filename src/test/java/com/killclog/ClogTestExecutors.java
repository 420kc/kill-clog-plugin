package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Shared disk-writer doubles for the cache tests. Each models one timing
 *  shape of the single writer thread; every class here is deterministic and
 *  never spawns real async work. This file intentionally holds several
 *  package-private top-level classes so call sites read like the real thing. */
final class ClogTestExecutors
{
	private ClogTestExecutors()
	{
	}
}

/** Runs everything inline: disk tasks execute synchronously so tests can
 *  assert real files in a temp dir. */
final class InlineScheduledExecutorService extends ScheduledThreadPoolExecutor
{
	InlineScheduledExecutorService()
	{
		super(1);
	}

	@Override
	public void execute(Runnable command)
	{
		command.run();
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		command.run();
		return new CompletedScheduledFuture();
	}
}

/** execute() runs inline; scheduled (debounced) tasks NEVER fire on their
 *  own - they stay queued so the drain path can be exercised. */
final class DeferredScheduledExecutorService extends ScheduledThreadPoolExecutor
{
	DeferredScheduledExecutorService()
	{
		super(1);
	}

	@Override
	public void execute(Runnable command)
	{
		command.run();
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		return new CompletedScheduledFuture();
	}
}

/** The inverse of CapturingScheduledExecutorService: debounced (scheduled)
 *  saves queue until fired by hand while execute() runs inline, modeling
 *  a pending capture save outliving an identity change. */
final class CapturingScheduledDebounceService extends ScheduledThreadPoolExecutor
{
	private final List<Runnable> queued = new ArrayList<>();

	CapturingScheduledDebounceService()
	{
		super(1);
	}

	@Override
	public void execute(Runnable command)
	{
		command.run();
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		queued.add(command);
		return new CompletedScheduledFuture();
	}

	void runQueued()
	{
		List<Runnable> toRun = new ArrayList<>(queued);
		queued.clear();
		for (Runnable r : toRun)
		{
			r.run();
		}
	}
}

/** Debounced saves run inline; execute() calls queue until runQueued(),
 *  modeling the gap between a decision and its disk task. */
final class CapturingScheduledExecutorService extends ScheduledThreadPoolExecutor
{
	private final List<Runnable> queued = new ArrayList<>();

	CapturingScheduledExecutorService()
	{
		super(1);
	}

	@Override
	public void execute(Runnable command)
	{
		queued.add(command);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		command.run();
		return new CompletedScheduledFuture();
	}

	void runQueued()
	{
		List<Runnable> toRun = new ArrayList<>(queued);
		queued.clear();
		for (Runnable r : toRun)
		{
			r.run();
		}
	}
}

final class NoopScheduledExecutorService extends ScheduledThreadPoolExecutor
{
	NoopScheduledExecutorService()
	{
		super(1, r ->
		{
			Thread t = new Thread(r, "kill-clog-test-disk");
			t.setDaemon(true);
			return t;
		});
		shutdown();
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		return new CompletedScheduledFuture();
	}
}

final class CompletedScheduledFuture implements ScheduledFuture<Object>
{
	@Override
	public long getDelay(TimeUnit unit)
	{
		return 0;
	}

	@Override
	public int compareTo(Delayed other)
	{
		return 0;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning)
	{
		return false;
	}

	@Override
	public boolean isCancelled()
	{
		return false;
	}

	@Override
	public boolean isDone()
	{
		return true;
	}

	@Override
	public Object get()
	{
		return null;
	}

	@Override
	public Object get(long timeout, TimeUnit unit)
	{
		return null;
	}
}
