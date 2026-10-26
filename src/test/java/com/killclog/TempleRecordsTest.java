package com.killclog;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TempleRecordsTest
{
	private static final long DAY = 24 * 60 * 60 * 1000L;

	@Test
	public void pastTheCapOnlyRecordsWhoseEveryMomentAgedOutGo()
	{
		ClogService service = new ClogService(null, new Gson(),
			new LocalClogCache(new Gson(), new NoopScheduledExecutorService()));
		long now = System.currentTimeMillis();
		fill(service, HttpUtil.CACHE_CAP, now - 2 * DAY);
		// One record past the cap: the aged ones go, the one Temple answered an hour ago stays for its day.
		ClogServiceProvenanceTest.moment(service, "held", "held", now - DAY / 24);
		Map<String, ?> records = TestViews.field(service, "temple");
		assertEquals(1, records.size());
		fill(service, HttpUtil.CACHE_CAP - 1, now - 2 * DAY);
		// Past it again: one TempleOSRS couldn't reach, with no moment left, is forgotten like the rest and reads as
		// unsynced until it's asked again.
		ClogServiceProvenanceTest.moment(service, "unreached", "unreached", true);
		assertEquals(1, records.size());
		assertTrue(records.containsKey("held"));
		assertFalse(service.templeUnanswered("unreached"));
		assertTrue(service.onTemple("held"));
		assertFalse("an aged-out name reads as never seen", service.onTemple("old 0"));
	}

	private static void fill(ClogService service, int names, long at)
	{
		for (int i = 0; i < names; i++)
		{
			ClogServiceProvenanceTest.moment(service, "old " + i, "fetched", at);
		}
	}
}
