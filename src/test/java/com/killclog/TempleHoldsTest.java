package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Method;
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
		Method held = ClogService.class.getDeclaredMethod("heldByTemple", String.class);
		held.setAccessible(true);
		held.invoke(service, "marni");
		assertTrue("whatever capitals the lookup used", service.onTemple("MARNI"));
		assertFalse(service.onTemple(null));
	}
}
