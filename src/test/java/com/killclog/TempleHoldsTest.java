package com.killclog;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TempleHoldsTest
{
	@Test
	public void templeIsCreditedForADayAfterItAnswersWithAPlayersLog() throws Exception
	{
		ClogService service = new ClogService(null, new Gson(),
			new LocalClogCache(new Gson(), new NoopScheduledExecutorService()));
		assertFalse(service.onTemple("Marni"));
		ClogServiceProvenanceTest.moment(service, "marni", "held", System.currentTimeMillis());
		assertTrue("whatever capitals the lookup used", service.onTemple("MARNI"));
		assertFalse(service.onTemple(null));
	}
}
