package com.killclog;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncOutcomeTest
{
	private final SyncService service = new SyncService(null, new Gson(), null);

	@Test
	public void aRestartingServerIsWorthOneSpreadOutRetry()
	{
		for (int code : new int[]{502, 503, 504})
		{
			for (int attempt = 0; attempt < 200; attempt++)
			{
				SyncService.SyncResult result = outcome(code, null);
				assertFalse(result.ok);
				assertTrue(result.retryAdvised);
				assertTrue(result.retryAfterSeconds >= 15 && result.retryAfterSeconds <= 30);
				// Still the honest message, for when the retry fails too.
				assertEquals("Collection log publication failed (HTTP " + code + ").", result.message);
			}
		}
	}

	@Test
	public void otherFailuresAreReportedAsTheyAre()
	{
		for (int code : new int[]{-1, 400, 401, 429, 500, 501, 505})
		{
			SyncService.SyncResult result = outcome(code, "{}");
			assertFalse(result.ok);
			assertFalse(String.valueOf(code), result.retryAdvised);
			assertEquals(0, result.retryAfterSeconds);
		}
		assertFalse(outcome(451, null).retryAdvised);
		assertFalse(outcome(409, "{\"error\":\"account_hash_mismatch\"}").retryAdvised);
	}

	@Test
	public void contentionKeepsTheServersOwnDelay()
	{
		SyncService.SyncResult result = outcome(409, "{\"error\":\"sync_in_flight\",\"retry_after_seconds\":4}");
		assertTrue(result.retryAdvised);
		assertEquals(4, result.retryAfterSeconds);
	}

	@Test
	public void aPublishedLogSaysWhatWent()
	{
		SyncService.SyncResult result = outcome(200, "{}");
		assertTrue(result.ok);
		assertFalse(result.retryAdvised);
		assertEquals("Collection log published! (12 items, 3 pbs).", result.message);
	}

	private SyncService.SyncResult outcome(int code, String body)
	{
		return service.outcome(new HttpUtil.HttpResult(code, body), "Probe", 12, 3);
	}
}
