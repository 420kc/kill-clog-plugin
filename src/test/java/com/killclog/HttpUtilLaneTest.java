package com.killclog;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpUtilLaneTest
{
	@Test
	public void aLaneHoldsAtMostTheCapAndTheStalestNameGoesFirst()
	{
		HttpUtil.Lane<String> lane = new HttpUtil.Lane<>(new CircuitBreaker("test"));
		for (int i = 0; i <= HttpUtil.CACHE_CAP; i++)
		{
			lane.ok("name" + i, "value" + i);
			lane.fetched.put("name" + i, 1_000L + i);
		}
		lane.ok("fresh", "value");
		assertEquals(HttpUtil.CACHE_CAP, lane.values.size());
		assertFalse("the name fetched longest ago went", lane.values.containsKey("name0"));
		assertFalse(lane.fetched.containsKey("name0"));
		assertTrue(lane.values.containsKey("fresh"));
	}

	@Test
	public void pastTheCapOnlyExpiredStampsGo()
	{
		Map<String, Long> stamps = new HashMap<>();
		long now = System.currentTimeMillis();
		for (int i = 0; i < HttpUtil.CACHE_CAP; i++)
		{
			stamps.put("old" + i, now - 10_000L);
		}
		stamps.put("live", now);
		HttpUtil.prune(stamps, 5_000L);
		assertEquals(1, stamps.size());
		assertTrue(stamps.containsKey("live"));

		Map<String, Long> few = new HashMap<>(Map.of("old", now - 10_000L));
		HttpUtil.prune(few, 5_000L);
		assertTrue("under the cap nothing is touched", few.containsKey("old"));
	}

	@Test
	public void aMissingNameKeepsWhatTheLaneHeld()
	{
		HttpUtil.Lane<String> lane = new HttpUtil.Lane<>(new CircuitBreaker("test"));
		lane.ok("name", "value");
		assertEquals("value", lane.missing("name"));
		assertTrue(lane.hold("name"));
	}
}
