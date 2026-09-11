package com.killclog;

import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
		assertNull(service.lookup("Test", null).get(3, TimeUnit.SECONDS));
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

	private static HiscoreService service(Interceptor interceptor)
	{
		return new HiscoreService(new OkHttpClient.Builder().addInterceptor(interceptor).build(), new Gson());
	}
}
