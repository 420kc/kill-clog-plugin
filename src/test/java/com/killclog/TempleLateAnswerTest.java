package com.killclog;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Map;
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
	private final AtomicInteger logRequests = new AtomicInteger();
	// What TempleOSRS's player stats answer, or null for none (404).
	private String stats;

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
	public void anAnswerLandingAsTheWaitEndsIsLateNotLost() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		CompletableFuture<ClogResult> temple = new CompletableFuture<>();
		// RuneProfile's ask holds the fan-out past TempleOSRS's wait, and TempleOSRS answers before it lets go.
		ClogProviderFanout.Answers answers = ClogProviderFanout.answers(false, () -> temple, () ->
		{
			try
			{
				Thread.sleep(1500);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			temple.complete(result("Marni", 1200));
			return CompletableFuture.completedFuture(null);
		}, () -> CompletableFuture.completedFuture(null));
		assertNull(answers.first.get(5, TimeUnit.SECONDS));
		assertEquals("Marni", answers.late.get(5, TimeUnit.SECONDS).getPlayerName());
	}

	@Test
	public void twoLookupsOfOnePlayerAskTempleOnceAndWaitAsOne() throws Exception
	{
		CountDownLatch answer = new CountDownLatch(1);
		ClogService service = service(200, "{\"error\":{\"Code\":402,\"Message\":\"Player has not synced\"}}", answer);
		CompletableFuture<ClogResult> one = service.lookup("Marni");
		CompletableFuture<ClogResult> two = service.lookup("marni");
		assertTrue(service.templeUnanswered("Marni"));
		answer.countDown();
		one.get(5, TimeUnit.SECONDS);
		two.get(5, TimeUnit.SECONDS);
		assertEquals("one ask", 1, logRequests.get());
		assertFalse(service.templeUnanswered("Marni"));
	}

	@Test
	public void aLateAnswerThatLosesToAFullerLogStillBringsTemplesFigures() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		CompletableFuture<ClogResult> temple = new CompletableFuture<>();
		ClogProviderFanout.Answers answers = ClogProviderFanout.answers(false, () -> temple,
			() -> CompletableFuture.completedFuture(result("From RuneProfile", 1200)),
			() -> CompletableFuture.completedFuture(null));
		assertEquals("From RuneProfile", answers.first.get(5, TimeUnit.SECONDS).getPlayerName());
		ClogResult templeLog = result("From Temple", 300);
		templeLog.setTempleEhp(123.5);
		templeLog.setTempleEhc(456.5);
		temple.complete(templeLog);
		ClogResult late = answers.late.get(5, TimeUnit.SECONDS);
		assertEquals("the fuller log still leads", "From RuneProfile", late.getPlayerName());
		assertEquals(123.5, late.getTempleEhp(), 0);
		assertEquals(456.5, late.getTempleEhc(), 0);
	}

	@Test
	public void aLateLogAlreadyInHandIsNeverOverwrittenByTheFirstAnswer() throws Exception
	{
		ClogProviderFanout.providerTimeoutSeconds = 1;
		LookupTestFixture fixture = new LookupTestFixture();
		CompletableFuture<ClogResult> temple = new CompletableFuture<>();
		fixture.clogs.put("Marni", temple);
		// RuneProfile's ask holds the fan-out past TempleOSRS's wait; TempleOSRS answers meanwhile, so the late log
		// is ready before the first answer is handed on.
		fixture.runeProfileClog = () ->
		{
			try
			{
				Thread.sleep(1500);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			temple.complete(LookupTestFixture.clog("Marni"));
			return CompletableFuture.completedFuture(null);
		};
		LookupTestFixture.edt(() -> fixture.primary.start("Marni", "Blue", AccountType.REGULAR));
		fixture.hiscores.get("Marni").complete(LookupTestFixture.hiscore(7));
		awaitClogResults(fixture, 2);
		LookupTestFixture.edt(() -> assertEquals("Marni", fixture.primary.getClogResult().getPlayerName()));
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

	@Test
	public void aTempleThatCouldNotBeReachedHasNotLoadedUntilItAnswers() throws Exception
	{
		AtomicInteger code = new AtomicInteger(503);
		ClogService service = service(code, "{\"error\":{\"Code\":402,\"Message\":\"Player has not synced\"}}", null);
		service.lookup("Marni").get(5, TimeUnit.SECONDS);
		service.clearTempleFailures();
		service.lookup("Marni").get(5, TimeUnit.SECONDS);
		assertTrue("a second miss is still a miss, however long ago the first", service.templeUnanswered("Marni"));
		code.set(200);
		service.clearTempleFailures();
		service.lookup("Marni").get(5, TimeUnit.SECONDS);
		assertFalse("its answer, that it has none, is an answer", service.templeUnanswered("Marni"));
	}

	// The lane's own writes, through a real lookup (QA 10/26, B251: held, figures and failed were proven only by moments
	// set by hand).
	@Test
	public void theLaneHoldsFetchesAndKeepsFiguresThroughARealLookup() throws Exception
	{
		String log = "{\"data\":{\"player_name_with_capitalization\":\"Marni\",\"game_mode\":1,"
			+ "\"last_changed\":\"2026-09-06 12:00:00\",\"ehc\":3377.9,\"ehc_im\":3417.4,"
			+ "\"items\":{\"zulrah\":[{\"id\":1,\"count\":99,\"date\":\"2026-09-06 12:00:00\"}]}}}";
		stats = "{\"data\":{\"info\":{\"Game mode\":1},\"Overall_ehp\":3043.1}}";
		ClogService service = service(200, log, null);
		assertFalse(service.onTemple("Marni"));
		ClogResult first = service.lookup("Marni").get(5, TimeUnit.SECONDS);
		assertTrue(first.isFromTemple());
		assertEquals(3043.1, first.getTempleEhp(), 1e-9);
		assertEquals(3417.4, first.getTempleEhc(), 1e-9);
		assertTrue("the lane's held write, whatever capitals", service.onTemple("MARNI"));
		assertFalse(service.templeUnanswered("Marni"));
		// A repeat inside the TTL is the local cache with the lane's figures, no second ask.
		ClogResult again = service.lookup("Marni").get(5, TimeUnit.SECONDS);
		assertTrue(again.isFromTemple());
		assertEquals(3043.1, again.getTempleEhp(), 1e-9);
		assertEquals(1, logRequests.get());
		assertEquals(3043.1, service.getCachedResult("Marni").getTempleEhp(), 1e-9);
	}

	@Test
	public void aLaneFailureWritesFailedAndSkipsTempleForTheCooldown() throws Exception
	{
		AtomicInteger code = new AtomicInteger(503);
		ClogService service = service(code, "", null);
		assertNull(service.lookup("Marni").get(5, TimeUnit.SECONDS));
		Map<String, Object> records = TestViews.field(service, "temple");
		assertTrue("the lane's failed write", (long) TestViews.field(records.get("marni"), "failed") > 0);
		code.set(200);
		assertNull("skipped inside the cooldown", service.lookup("Marni").get(5, TimeUnit.SECONDS));
		assertEquals(1, logRequests.get());
		assertTrue(service.templeUnanswered("Marni"));
	}

	/** A service whose TempleOSRS answers every log request with this code and body, once {@code answer} opens. */
	private ClogService service(int code, String body, CountDownLatch answer) throws Exception
	{
		return service(new AtomicInteger(code), body, answer);
	}

	private ClogService service(AtomicInteger code, String body, CountDownLatch answer) throws Exception
	{
		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				boolean log = chain.request().url().encodedPath().endsWith("player_collection_log.php");
				boolean player = chain.request().url().encodedPath().endsWith("player_stats.php") && stats != null;
				if (log)
				{
					logRequests.incrementAndGet();
				}
				if (log && answer != null)
				{
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
					.code(log ? code.get() : player ? 200 : 404)
					.message("answer")
					.body(ResponseBody.create(MediaType.parse("application/json"), log ? body : player ? stats : ""))
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
