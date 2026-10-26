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
		ClogService service = service();
		long now = System.currentTimeMillis();
		fill(service, HttpUtil.CACHE_CAP, now - 2 * DAY);
		// One record past the cap: the aged ones go, the one Temple answered an hour ago stays for its day.
		ClogServiceProvenanceTest.moment(service, "held", "held", now - DAY / 24);
		Map<String, ?> records = TestViews.field(service, "temple");
		assertEquals(1, records.size());
		assertTrue(service.onTemple("held"));
		assertFalse("an aged-out name reads as never seen", service.onTemple("old 0"));
	}

	@Test
	public void everyLiveMomentKeepsItsRecordPastTheCap()
	{
		ClogService service = service();
		long now = System.currentTimeMillis();
		ClogServiceProvenanceTest.moment(service, "fetched", "fetched", now);
		ClogServiceProvenanceTest.moment(service, "dates asked", "datesAsked", now);
		ClogServiceProvenanceTest.moment(service, "dates answered", "datesAnswered", now);
		ClogServiceProvenanceTest.moment(service, "failed", "failed", now);
		// The last aged name is the one past the cap.
		fill(service, HttpUtil.CACHE_CAP - 3, now - 2 * DAY);
		Map<String, ?> records = TestViews.field(service, "temple");
		assertEquals(4, records.size());
	}

	@Test
	public void oneTempleCouldNotReachStaysNotLoadedPastTheCap()
	{
		ClogService service = service();
		long now = System.currentTimeMillis();
		// A full day of answers keeps the map past the cap; the lookup in hand still reads as not loaded.
		for (int i = 0; i <= HttpUtil.CACHE_CAP; i++)
		{
			ClogServiceProvenanceTest.moment(service, "held " + i, "held", now - DAY / 24);
		}
		ClogServiceProvenanceTest.moment(service, "marni", "unreached", true);
		ClogServiceProvenanceTest.moment(service, "marni", "failed", now);
		assertTrue(service.templeUnanswered("marni"));
		// Another name noted meanwhile, reached or not, leaves it so.
		ClogServiceProvenanceTest.moment(service, "zezima", "unreached", true);
		ClogServiceProvenanceTest.moment(service, "own", "datesAsked", now);
		assertTrue(service.templeUnanswered("marni"));
		assertTrue(service.templeUnanswered("zezima"));
	}

	@Test
	public void aLongOutageForgetsTheOthersButNotTheOneInHand()
	{
		ClogService service = service();
		for (int i = 0; i < HttpUtil.CACHE_CAP; i++)
		{
			ClogServiceProvenanceTest.moment(service, "unreached " + i, "unreached", true);
		}
		assertTrue(service.templeUnanswered("unreached 0"));
		ClogServiceProvenanceTest.moment(service, "marni", "unreached", true);
		Map<String, ?> records = TestViews.field(service, "temple");
		assertEquals(1, records.size());
		assertFalse(service.templeUnanswered("unreached 0"));
		assertTrue(service.templeUnanswered("marni"));
	}

	@Test
	public void theLoginClearKeepsEverythingButTheFailure()
	{
		ClogService service = service();
		long now = System.currentTimeMillis();
		ClogServiceProvenanceTest.moment(service, "marni", "held", now);
		ClogServiceProvenanceTest.moment(service, "marni", "unreached", true);
		ClogServiceProvenanceTest.moment(service, "marni", "failed", now);
		service.clearTempleFailures();
		Map<String, Object> records = TestViews.field(service, "temple");
		Object record = records.get("marni");
		assertEquals(0L, (long) TestViews.field(record, "failed"));
		assertTrue(service.onTemple("marni"));
		assertTrue(service.templeUnanswered("marni"));
	}

	private static ClogService service()
	{
		return new ClogService(null, new Gson(),
			new LocalClogCache(new Gson(), new NoopScheduledExecutorService()));
	}

	private static void fill(ClogService service, int names, long at)
	{
		for (int i = 0; i < names; i++)
		{
			ClogServiceProvenanceTest.moment(service, "old " + i, "fetched", at);
		}
	}
}
