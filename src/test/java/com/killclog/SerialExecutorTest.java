package com.killclog;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SerialExecutorTest
{
	@Test
	public void anErrorFromOneTaskNeverLeavesTheOwnerStuck()
	{
		SerialExecutor owner = new SerialExecutor();
		List<String> ran = new ArrayList<>();
		try
		{
			owner.execute(() ->
			{
				throw new NoClassDefFoundError("a class the client never loaded");
			});
			fail("an Error is not swallowed");
		}
		catch (NoClassDefFoundError expected)
		{
			// It reaches the thread that posted it, as an Error should.
		}
		owner.execute(() -> ran.add("next"));
		assertEquals(List.of("next"), ran);
	}

	@Test
	public void aFailingTaskIsLoggedAndTheQueueRunsOn()
	{
		SerialExecutor owner = new SerialExecutor();
		List<String> ran = new ArrayList<>();
		owner.execute(() ->
		{
			owner.execute(() -> ran.add("queued behind it"));
			throw new IllegalStateException("one task failed");
		});
		assertEquals(List.of("queued behind it"), ran);
	}
}
