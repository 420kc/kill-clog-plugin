package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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

public class HiscoreFailureTest
{
	private static final String REGULAR = "/m=hiscore_oldschool/";
	private static final String IRON = "/m=hiscore_oldschool_ironman/";
	private static final String HARDCORE = "/m=hiscore_oldschool_hardcore_ironman/";

	static String body(long xp)
	{
		return "{\"skills\":[{\"name\":\"Overall\",\"rank\":-1,\"level\":100,\"xp\":" + xp
			+ "},{\"name\":\"Defence\",\"rank\":2,\"level\":50,\"xp\":1000}],\"activities\":[]}";
	}

	static Response response(Interceptor.Chain chain, int code, String body)
	{
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
			.code(code).message("fixture").body(ResponseBody.create(MediaType.parse("text/plain"), body)).build();
	}

	@Test
	public void definiteAbsenceStopsEachTableAfterOneJsonRequest() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		HiscoreService service = service(chain ->
		{
			calls.incrementAndGet();
			assertTrue(chain.request().url().encodedPath().endsWith(".json"));
			return response(chain, 404, "");
		});
		assertNull(service.lookup("Missing", null).get(3, TimeUnit.SECONDS));
		assertEquals(4, calls.get());
	}

	@Test
	public void validZeroAndUnrankedRowsSurviveMissingSpecialtyTables() throws Exception
	{
		for (long xp : new long[]{0, -1})
		{
			AtomicInteger calls = new AtomicInteger();
			HiscoreService service = service(chain ->
			{
				calls.incrementAndGet();
				boolean regular = chain.request().url().encodedPath().startsWith(REGULAR);
				return response(chain, regular ? 200 : 404, regular ? body(xp) : "");
			});
			HiscoreResult result = service.lookup("Test", null).get(3, TimeUnit.SECONDS);
			assertNotNull(result);
			assertEquals(xp, result.getTotalXp());
			assertEquals(-1, result.getOverallRank());
			assertEquals(4, calls.get());
		}
	}

	@Test
	public void malformedJsonAndSchemaUseCsvAndRetainShiftWarning() throws Exception
	{
		for (String broken : new String[]{"{broken", "{}", "{\"skills\":[],\"activities\":[]}"})
		{
			AtomicInteger calls = new AtomicInteger();
			HiscoreService service = service(chain ->
			{
				calls.incrementAndGet();
				String path = chain.request().url().encodedPath();
				if (!path.startsWith(REGULAR)) return response(chain, 404, "");
				return response(chain, 200, path.endsWith(".json") ? broken : "-1,100,12345\n-1,1,0");
			});
			HiscoreResult result = service.lookup("Test", null).get(3, TimeUnit.SECONDS);
			assertEquals(12345, result.getTotalXp());
			assertTrue(result.isBossSectionShifted());
			assertEquals(5, calls.get());
		}
	}

	@Test
	public void retryOnlyFailedTableAndNeverTreatFallback404AsAbsence() throws Exception
	{
		for (int failure : new int[]{429, 502, 503, 200})
		{
			Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
			HiscoreService service = service(chain ->
			{
				String path = chain.request().url().encodedPath();
				int count = calls.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
				if (!path.startsWith(REGULAR)) return response(chain, 404, "");
				if (path.endsWith(".ws")) return response(chain, 404, "");
				return response(chain, count == 1 ? failure : 200, count == 1 ? "{bad" : body(5000));
			});
			assertEquals(5000, service.lookup("Test", null).get(3, TimeUnit.SECONDS).getTotalXp());
			assertEquals(6, calls.values().stream().mapToInt(AtomicInteger::get).sum());
			assertEquals(2, calls.get(REGULAR + "index_lite.json").get());
		}
	}

	@Test
	public void persistentFailureHasFinitePerTableBudget() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		HiscoreService service = service(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 503, "");
		});
		assertDown(service.lookup("Test", null));
		assertEquals(16, calls.get());
		assertNull(service.getCached("Test"));
	}

	@Test
	public void frozenHardcoreCannotOverrideFreshRegularAndIronRows() throws Exception
	{
		HiscoreService service = service(chain ->
		{
			String path = chain.request().url().encodedPath();
			if (path.startsWith(HARDCORE)) return response(chain, 200, body(1000));
			if (path.startsWith(REGULAR) || path.startsWith(IRON)) return response(chain, 200, body(2000));
			return response(chain, 404, "");
		});
		HiscoreResult result = service.lookup("Test", null).get(3, TimeUnit.SECONDS);
		assertEquals(AccountType.IRONMAN, result.getAccountType());
		assertEquals(2000, result.getTotalXp());
	}

	@Test
	public void failedOptionalRefinementRetainsValidBaseRow() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		HiscoreService service = service(chain ->
		{
			calls.incrementAndGet();
			String path = chain.request().url().encodedPath();
			String pure = body(1000).replace("\"level\":50", "\"level\":1")
				.replace("],\"activities\"", ",{\"name\":\"Attack\",\"rank\":2,\"level\":50,\"xp\":500}],\"activities\"");
			return response(chain, path.startsWith(REGULAR) ? 200 : 404, pure);
		});
		HiscoreResult result = service.lookup("Test", null).get(3, TimeUnit.SECONDS);
		assertEquals(1000, result.getTotalXp());
		assertEquals(HiscoreTable.STANDARD, result.getHiscoreTable());
		assertEquals(5, calls.get());
	}

	@Test
	public void eachLookupRecordsWhetherItsOwnAnswersMakeAnIronman() throws Exception
	{
		// Dead Hardcore: regular and Ironman match, the Hardcore row trails.
		HiscoreResult dead = service(boards(2000, 2000, 1000)).lookup("Test", null).get(3, TimeUnit.SECONDS);
		assertEquals(AccountType.IRONMAN, dead.getAccountType());
		assertEquals(Boolean.TRUE, dead.getIronmanNow());
		// De-ironed: the Ironman row trails the regular one.
		HiscoreResult deIroned = service(boards(2000, 1000, 404)).lookup("Test", null).get(3, TimeUnit.SECONDS);
		assertEquals(AccountType.REGULAR, deIroned.getAccountType());
		assertEquals(Boolean.FALSE, deIroned.getIronmanNow());
		// Never an Ironman: nothing to claim either way.
		assertNull(service(boards(2000, 404, 404)).lookup("Test", null).get(3, TimeUnit.SECONDS).getIronmanNow());
		// An Ironman row ahead of the regular one is a mismatched read, and says nothing either.
		assertNull(service(boards(2000, 2500, 404)).lookup("Test", null).get(3, TimeUnit.SECONDS).getIronmanNow());
		// RuneLite's own type answers it for a self lookup.
		assertEquals(Boolean.TRUE, service(boards(2000, 2000, 1000))
			.lookup("Test", AccountType.IRONMAN).get(3, TimeUnit.SECONDS).getIronmanNow());
		assertEquals(Boolean.FALSE, service(boards(2000, 1000, 404))
			.lookup("Test", AccountType.REGULAR).get(3, TimeUnit.SECONDS).getIronmanNow());
	}

	@Test
	public void anUnansweredIdentityBoardClaimsNoFormerMode() throws Exception
	{
		HiscoreResult hardcoreAtDeath = new HiscoreResult(AccountType.HARDCORE_IRONMAN, java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), 100, 1000, 0, -1);

		// A dead Hardcore whose Ironman board fails reads as a main, but is never called de-ironed.
		HiscoreResult guessedMain = service(boards(2000, 500, 1000)).lookup("Test", null).get(5, TimeUnit.SECONDS);
		assertEquals(AccountType.REGULAR, guessedMain.getAccountType());
		assertNull(guessedMain.getIronmanNow());
		AccountDisplay main = AccountDisplay.of(guessedMain.getAccountType(), HiscoreTable.STANDARD);
		assertSame(main, main.shownOn(guessedMain.withRow(hardcoreAtDeath), RankLeaderboard.HARDCORE));

		// A de-ironed main whose regular board fails reads as an Ironman, but is never called a dead Hardcore.
		HiscoreResult guessedIron = service(boards(500, 1500, 1000)).lookup("Test", null).get(5, TimeUnit.SECONDS);
		assertEquals(AccountType.IRONMAN, guessedIron.getAccountType());
		assertNull(guessedIron.getIronmanNow());
		AccountDisplay iron = AccountDisplay.of(guessedIron.getAccountType(), HiscoreTable.STANDARD);
		assertSame(iron, iron.shownOn(guessedIron.withRow(hardcoreAtDeath), RankLeaderboard.HARDCORE));
	}

	@Test
	public void aWarmCacheNeverAnswersForALaterLookup() throws Exception
	{
		// First lookup: a dead Hardcore, every board answering.
		int[] answers = {2000, 2000, 1000};
		HiscoreService service = service(chain -> boards(answers[0], answers[1], answers[2]).intercept(chain));
		HiscoreResult first = service.lookup("Test", null).get(3, TimeUnit.SECONDS);
		assertEquals(Boolean.TRUE, first.getIronmanNow());

		// Later the player has trained and the Ironman board fails. The earlier Ironman row is
		// still cached, but this lookup's identity rests only on its own answers.
		answers[0] = 3000;
		answers[1] = 500;
		HiscoreResult later = service.lookup("Test", null).get(5, TimeUnit.SECONDS);
		assertEquals(AccountType.REGULAR, later.getAccountType());
		assertNull(later.getIronmanNow());
		assertEquals(Boolean.TRUE, first.getIronmanNow());
	}

	@Test
	public void jagexBeingDownIsNeverAMissingPlayer() throws Exception
	{
		// Down for the weekly update: every board fails, times out, shows a maintenance page or refuses.
		Interceptor[] outages = {
			chain -> response(chain, 503, ""),
			chain ->
			{
				throw new IOException("timed out");
			},
			chain -> response(chain, 200, "<html>We are updating the game</html>"),
			chain -> response(chain, 403, "Forbidden"),
		};
		for (Interceptor outage : outages)
		{
			for (AccountType known : new AccountType[]{null, AccountType.REGULAR, AccountType.IRONMAN})
			{
				assertDown(service(outage).lookup("Test", known));
			}
			assertDown(service(outage).lookupLeague("Test"));
		}
		assertEquals("Lookup failed", HiscoreService.failureText(new IllegalStateException("anything else")));
	}

	@Test
	public void onlyTheBoardsOwnNotFoundIsAMissingPlayer() throws Exception
	{
		Interceptor absent = chain -> response(chain, 404, "");
		for (AccountType known : new AccountType[]{null, AccountType.REGULAR, AccountType.IRONMAN})
		{
			assertNull(service(absent).lookup("Missing", known).get(3, TimeUnit.SECONDS));
		}
		assertNull(service(absent).lookupLeague("Missing").get(3, TimeUnit.SECONDS));
		// A League row still answers.
		assertEquals(5000, service(chain -> response(chain, 200, body(5000))).lookupLeague("Test")
			.get(3, TimeUnit.SECONDS).getTotalXp());
	}

	private static void assertDown(CompletableFuture<HiscoreResult> lookup) throws Exception
	{
		try
		{
			lookup.get(5, TimeUnit.SECONDS);
			fail("an outage read as a lookup's answer");
		}
		catch (ExecutionException e)
		{
			assertEquals(HiscoreService.DOWN_MESSAGE, HiscoreService.failureText(e));
		}
	}

	/** Regular, Ironman and Hardcore answers by total XP; 404 is no row, 500 is a board that fails. */
	private static Interceptor boards(int regular, int iron, int hardcore)
	{
		return chain ->
		{
			String path = chain.request().url().encodedPath();
			int value = path.startsWith(REGULAR) ? regular : path.startsWith(IRON) ? iron
				: path.startsWith(HARDCORE) ? hardcore : 404;
			return value == 404 || value == 500 ? response(chain, value, "") : response(chain, 200, body(value));
		};
	}

	private static HiscoreService service(Interceptor interceptor)
	{
		return new HiscoreService(new OkHttpClient.Builder().addInterceptor(interceptor).build(), new Gson());
	}
}
