package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntConsumer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Lookups complete on many threads at once; no cache may grow past its cap for it. */
public class CacheCapRaceTest
{
	private static final int THREADS = 8;
	private static final int EACH = 2_000;

	@Test
	public void aLaneStaysAtTheCapUnderConcurrentResults() throws Exception
	{
		HttpUtil.Lane<String> lane = new HttpUtil.Lane<>(new CircuitBreaker("test"));
		race(i -> lane.ok("name" + i, "value" + i));
		assertEquals(HttpUtil.CACHE_CAP, lane.values.size());
		assertEquals(HttpUtil.CACHE_CAP, lane.fetched.size());
	}

	@Test
	public void hiscoresStayAtTheCapUnderConcurrentResults() throws Exception
	{
		HiscoreService service = new HiscoreService(null, new Gson());
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
		long now = System.currentTimeMillis();
		race(i -> service.cacheResult("name" + i, result, now + i % 7));
		int cached = 0;
		for (int i = 0; i < THREADS * EACH; i++)
		{
			cached += service.getCached("name" + i) != null ? 1 : 0;
		}
		assertEquals(HttpUtil.CACHE_CAP, cached);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void everyCachedProofViewKeepsItsPbsUnderConcurrentResults() throws Exception
	{
		KillclogService service = new KillclogService(null, new Gson(), null);
		race(i ->
		{
			String name = "player " + i;
			service.onProofViewResponse(200, "{\"rsn\":\"" + name + "\",\"pbs\":{\"Zulrah\":58.2},"
				+ "\"clog\":{\"items_by_category\":{\"zulrah\":[{\"item_id\":1,\"quantity\":1}]}}}", name, name);
		});
		Field field = KillclogService.class.getDeclaredField("clogs");
		field.setAccessible(true);
		HttpUtil.Lane<ClogResult> clogs = (HttpUtil.Lane<ClogResult>) field.get(service);
		assertEquals(HttpUtil.CACHE_CAP, clogs.values.size());
		for (String name : clogs.values.keySet())
		{
			assertNotNull(name + " keeps their pbs", service.pbText(name, "Zulrah"));
		}
		int withPbs = 0;
		for (int i = 0; i < THREADS * EACH; i++)
		{
			withPbs += service.pbText("player " + i, "Zulrah") != null ? 1 : 0;
		}
		assertEquals("pbs leave with their clog result", HttpUtil.CACHE_CAP, withPbs);
	}

	/** Every thread inserts its own names, all released at once. */
	private static void race(IntConsumer insert) throws Exception
	{
		CountDownLatch start = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		List<Throwable> errors = new ArrayList<>();
		for (int t = 0; t < THREADS; t++)
		{
			int first = t * EACH;
			Thread thread = new Thread(() ->
			{
				try
				{
					start.await();
					for (int i = first; i < first + EACH; i++)
					{
						insert.accept(i);
					}
				}
				catch (Throwable e)
				{
					synchronized (errors)
					{
						errors.add(e);
					}
				}
			});
			thread.start();
			threads.add(thread);
		}
		start.countDown();
		for (Thread thread : threads)
		{
			thread.join();
		}
		assertTrue(errors.toString(), errors.isEmpty());
	}
}
