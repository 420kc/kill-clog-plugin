package com.killclog;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs what it is given one at a time, in the order given, on whichever thread finds it idle: the single owner of
 * state that many threads post to, so no two of its tasks ever interleave and none needs a lock of its own. A task
 * posted while one runs (by that task or another thread) waits its turn and runs before the owner goes idle. Its
 * tasks must be short and never wait on another thread.
 */
@Slf4j
final class SerialExecutor implements Executor
{
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private boolean running;

	@Override
	public void execute(Runnable task)
	{
		synchronized (tasks)
		{
			tasks.add(task);
			if (running)
			{
				return;
			}
			running = true;
		}
		while (true)
		{
			Runnable next;
			synchronized (tasks)
			{
				next = tasks.poll();
				if (next == null)
				{
					running = false;
					return;
				}
			}
			try
			{
				next.run();
			}
			catch (RuntimeException e)
			{
				log.warn("publication task failed", e);
			}
		}
	}
}
