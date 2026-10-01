package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankLookupTest
{
	private static String body(int rank)
	{
		return "{\"skills\":[{\"name\":\"Overall\",\"rank\":" + rank
			+ ",\"level\":2000,\"xp\":10000},{\"name\":\"Defence\",\"rank\":12,\"level\":99,\"xp\":1000}],\"activities\":[]}";
	}

	private static Response response(Interceptor.Chain chain, int code, String body)
	{
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
			.code(code).message("fixture").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
	}

	@Test
	public void knownTypeRetainsNeededTablesAndOtherRankSelectionsFetchLazily() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			calls.incrementAndGet();
			String path = chain.request().url().encodedPath();
			int rank = path.contains("ultimate") ? 1 : path.contains("hardcore") ? 2 : path.contains("ironman") ? 3 : 4;
			return response(chain, 200, body(rank));
		}).build();
		HiscoreService service = new HiscoreService(client, new Gson());
		HiscoreResult original = service.lookup("Test Player", AccountType.IRONMAN).get(3, TimeUnit.SECONDS);
		assertEquals(AccountType.IRONMAN, original.getAccountType());
		assertEquals(3, original.getOverallRank());
		assertEquals(2, calls.get());
		assertEquals(4, service.lookupRanks("test player", RankLeaderboard.NORMAL).get().getOverallRank());
		assertEquals(3, service.lookupRanks("TEST PLAYER", RankLeaderboard.IRONMAN).get().getOverallRank());
		assertEquals(2, service.lookupRanks("Test Player", RankLeaderboard.HARDCORE).get().getOverallRank());
		assertEquals(1, service.lookupRanks("Test Player", RankLeaderboard.ULTIMATE).get().getOverallRank());
		assertEquals(4, calls.get());
		assertSame(original, service.getCached("Test Player"));
		assertEquals(3, original.getOverallRank());
	}

	@Test
	public void simultaneousRankSelectionsShareOneRequest() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			calls.incrementAndGet();
			entered.countDown();
			try
			{
				if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("fixture timed out");
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IOException(e);
			}
			assertTrue(chain.request().url().encodedPath().contains("hiscore_oldschool_skiller_defence/"));
			return response(chain, 200, body(50));
		}).build();
		HiscoreService service = new HiscoreService(client, new Gson());
		CompletableFuture<HiscoreResult> first = service.lookupRanks("Test Player", RankLeaderboard.PURE);
		CompletableFuture<HiscoreResult> second;
		try
		{
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			second = service.lookupRanks("TEST PLAYER", RankLeaderboard.PURE);
			assertNotSame(first, second);
			first.complete(null);
		}
		finally
		{
			release.countDown();
		}
		assertNull(first.get());
		assertEquals(50, second.get(3, TimeUnit.SECONDS).getOverallRank());
		assertEquals(50, service.lookupRanks("Test Player", RankLeaderboard.PURE).get().getOverallRank());
		assertEquals(1, calls.get());
		assertNull(service.getCached("Test Player"));
	}

	@Test
	public void failedAndMalformedResponsesNeverBecomeRankData() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			int call = calls.incrementAndGet();
			return response(chain, call == 1 ? 404 : 200, call == 2 ? "{broken" : body(99));
		}).build();
		HiscoreService service = new HiscoreService(client, new Gson());
		assertNull(service.lookupRanks("Test", RankLeaderboard.SKILLER).get(3, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertTrue(service.notOnBoard("TEST", RankLeaderboard.SKILLER));
		assertNull("not found is remembered, not asked again",
			service.lookupRanks("Test", RankLeaderboard.SKILLER).get(3, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertEquals(99, service.lookupRanks("Test", RankLeaderboard.PURE).get(3, TimeUnit.SECONDS).getOverallRank());
		assertEquals(3, calls.get());
		assertFalse(service.notOnBoard("Test", RankLeaderboard.PURE));
	}

	@Test
	public void theLookupsOwnFanOutTellsWhichBoardsHaveARow() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			calls.incrementAndGet();
			boolean ultimate = chain.request().url().encodedPath().contains("ultimate");
			return response(chain, ultimate ? 404 : 200, ultimate ? "" : body(7));
		}).build();
		HiscoreService service = new HiscoreService(client, new Gson());
		assertNotNull(service.lookup("Ye Ol Buck", null).get(3, TimeUnit.SECONDS));
		int asked = calls.get();
		assertTrue(service.notOnBoard("ye ol buck", RankLeaderboard.ULTIMATE));
		assertFalse(service.notOnBoard("Ye Ol Buck", RankLeaderboard.HARDCORE));
		assertNull(service.lookupRanks("Ye Ol Buck", RankLeaderboard.ULTIMATE).get(3, TimeUnit.SECONDS));
		assertEquals(7, service.lookupRanks("Ye Ol Buck", RankLeaderboard.HARDCORE).get(3, TimeUnit.SECONDS).getOverallRank());
		assertEquals("both answers came from the lookup itself", asked, calls.get());
	}

	@Test
	public void anOutageIsNeverReadAsNotOnTheBoard() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 503, "");
		}).build();
		HiscoreService service = new HiscoreService(client, new Gson());
		assertNull(service.lookupRanks("Test", RankLeaderboard.ULTIMATE).get(5, TimeUnit.SECONDS));
		assertFalse(service.notOnBoard("Test", RankLeaderboard.ULTIMATE));
		int asked = calls.get();
		assertNull(service.lookupRanks("Test", RankLeaderboard.ULTIMATE).get(5, TimeUnit.SECONDS));
		assertTrue("an outage is asked again", calls.get() > asked);
	}
}
