package com.killclog;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A first lookup while TempleOSRS is slow to answer (10/24, Marni: no log and no pets until a search three minutes
 * later): its answer still lands in the open lookup, and until it does the card says the log hasn't loaded, never that
 * the player has none.
 */
public class TempleLateAnswerTest
{
	private final long timeout = ClogProviderFanout.providerTimeoutSeconds;

	@After
	public void restore()
	{
		ClogProviderFanout.providerTimeoutSeconds = timeout;
	}

	@Test
	public void templeAnsweringAfterTheWaitLandsInTheLookup() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		CompletableFuture<ClogResult> temple = new CompletableFuture<>();
		ClogProviderFanout.Answers answers = ClogProviderFanout.answers(false, () -> temple,
			() -> CompletableFuture.completedFuture(null), () -> CompletableFuture.completedFuture(null));
		assertNull("the wait ends with no log", answers.first.get(5, TimeUnit.SECONDS));
		assertFalse(answers.late.isDone());
		temple.complete(result("Marni", 1200));
		assertEquals("Marni", answers.late.get(5, TimeUnit.SECONDS).getPlayerName());
	}

	@Test
	public void aLateAnswerJoinsTheOthersByTheUsualRules() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		CompletableFuture<ClogResult> temple = new CompletableFuture<>();
		ClogResult runeProfile = result("From RuneProfile", 800);
		ClogProviderFanout.Answers answers = ClogProviderFanout.answers(false, () -> temple,
			() -> CompletableFuture.completedFuture(runeProfile), () -> CompletableFuture.completedFuture(null));
		assertEquals("From RuneProfile", answers.first.get(5, TimeUnit.SECONDS).getPlayerName());
		temple.complete(result("From Temple", 1200));
		assertEquals("the fuller log wins, as in time", "From Temple",
			answers.late.get(5, TimeUnit.SECONDS).getPlayerName());
	}

	@Test
	public void theOpenLookupShowsTheLateLogAndANewOneNeverDoes() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		LookupTestFixture fixture = new LookupTestFixture();
		LookupTestFixture.edt(() -> fixture.primary.start("Marni", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Marni").complete(LookupTestFixture.hiscore(7));
		awaitClogResults(fixture, 1);
		LookupTestFixture.edt(() -> assertNull(fixture.primary.getClogResult()));
		fixture.clogs.get("Marni").complete(LookupTestFixture.clog("Marni"));
		awaitClogResults(fixture, 2);
		LookupTestFixture.edt(() -> assertEquals("Marni", fixture.primary.getClogResult().getPlayerName()));

		// A late answer for a lookup already left behind stays out of the next one.
		LookupTestFixture.edt(() -> fixture.primary.start("Zezima", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Zezima").complete(LookupTestFixture.hiscore(9));
		awaitClogResults(fixture, 3);
		LookupTestFixture.edt(() -> fixture.primary.start("Lynx", "Blue", AccountType.REGULAR));
		fixture.clogs.get("Zezima").complete(LookupTestFixture.clog("Zezima"));
		Thread.sleep(200);
		LookupTestFixture.edt(() -> fixture.events("flush"));
		assertEquals(3, fixture.events("onClogResult"));
	}

	private static void awaitClogResults(LookupTestFixture fixture, int count) throws Exception
	{
		long until = System.currentTimeMillis() + 5000;
		while (fixture.events("onClogResult") < count && System.currentTimeMillis() < until)
		{
			Thread.sleep(20);
			LookupTestFixture.edt(() -> fixture.events("flush"));
		}
		assertEquals(count, fixture.events("onClogResult"));
	}

	@Test
	public void templeInTimeHasNothingLate() throws Exception
	{
		ClogProviderFanout.Answers answers = ClogProviderFanout.answers(false,
			() -> CompletableFuture.completedFuture(result("Marni", 1200)),
			() -> CompletableFuture.completedFuture(null), () -> CompletableFuture.completedFuture(null));
		assertEquals("Marni", answers.first.get(5, TimeUnit.SECONDS).getPlayerName());
		assertNull(answers.late.get(5, TimeUnit.SECONDS));
	}

	@Test
	public void aLogStillBeingAskedForHasNotLoaded() throws Exception
	{
		CountDownLatch answer = new CountDownLatch(1);
		ClogService service = service(200, "{\"error\":{\"Code\":402,\"Message\":\"Player has not synced\"}}", answer);
		CompletableFuture<ClogResult> asked = service.lookup("Marni");
		assertTrue("while TempleOSRS is asked", service.templeUnanswered("marni"));
		answer.countDown();
		asked.get(5, TimeUnit.SECONDS);
		assertFalse("an answer that it has none is an answer", service.templeUnanswered("Marni"));
	}

	@Test
	public void aTempleThatCouldNotBeReachedHasNotLoaded() throws Exception
	{
		ClogService service = service(503, "", null);
		assertNull(service.lookup("Marni").get(5, TimeUnit.SECONDS));
		assertTrue(service.templeUnanswered("Marni"));
		assertFalse("other players are untouched", service.templeUnanswered("Zezima"));
	}

	/** A service whose TempleOSRS answers every log request with this code and body, once {@code answer} opens. */
	private static ClogService service(int code, String body, CountDownLatch answer) throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				boolean log = chain.request().url().encodedPath().endsWith("player_collection_log.php");
				if (log && answer != null)
				{
					requests.incrementAndGet();
					try
					{
						answer.await(5, TimeUnit.SECONDS);
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
				}
				return new Response.Builder()
					.request(chain.request())
					.protocol(Protocol.HTTP_1_1)
					.code(log ? code : 404)
					.message("answer")
					.body(ResponseBody.create(MediaType.parse("application/json"), log ? body : ""))
					.build();
			})
			.build();
		return new ClogService(client, new Gson(), LocalClogCacheTest.ready());
	}

	private static ClogResult result(String name, int uniqueObtained)
	{
		ClogResult result = new ClogResult(name, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			null, null);
		result.setUniqueObtained(uniqueObtained);
		return result;
	}
}
