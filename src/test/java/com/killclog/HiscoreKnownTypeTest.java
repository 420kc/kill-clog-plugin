package com.killclog;

import com.google.gson.Gson;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class HiscoreKnownTypeTest
{
	@Test
	public void knownTypeUsesOnlyRegularAndItsOwnRankTable() throws Exception
	{
		for (AccountType type : AccountType.values())
		{
			AtomicInteger calls = new AtomicInteger();
			HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
			{
				calls.incrementAndGet();
				boolean regular = chain.request().url().encodedPath().startsWith("/m=hiscore_oldschool/");
				return HiscoreFailureTest.response(chain, 200,
					HiscoreFailureTest.body(regular ? 2000 : 1000).replace("\"rank\":-1", "\"rank\":42"));
			}).build(), new Gson());
			HiscoreResult result = service.lookup("Test", type).get(3, TimeUnit.SECONDS);
			assertEquals(type, result.getAccountType());
			assertEquals(2000, result.getTotalXp());
			assertEquals(42, result.getOverallRank());
			assertEquals(type == AccountType.REGULAR || type.isGroupIronman() ? 1 : 2, calls.get());
		}
	}

	@Test
	public void absentSelectedRankTableKeepsFreshStatsWithBlankRanks() throws Exception
	{
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			boolean regular = chain.request().url().encodedPath().startsWith("/m=hiscore_oldschool/");
			return HiscoreFailureTest.response(chain, regular ? 200 : 404, HiscoreFailureTest.body(3000));
		}).build(), new Gson());
		HiscoreResult result = service.lookup("Test", AccountType.IRONMAN).get(3, TimeUnit.SECONDS);
		assertEquals(AccountType.IRONMAN, result.getAccountType());
		assertEquals(3000, result.getTotalXp());
		assertEquals(-1, result.getOverallRank());
	}

	@Test
	public void regularOutageStillAllowsKnownSpecialtyStats() throws Exception
	{
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			boolean regular = chain.request().url().encodedPath().startsWith("/m=hiscore_oldschool/");
			return HiscoreFailureTest.response(chain, regular ? 503 : 200, HiscoreFailureTest.body(3000));
		}).build(), new Gson());
		assertEquals(3000, service.lookup("Test", AccountType.ULTIMATE_IRONMAN)
			.get(3, TimeUnit.SECONDS).getTotalXp());
	}

	@Test
	public void manualRetryIsFreshAndCacheKeysIgnoreDefaultLocale() throws Exception
	{
		Locale previous = Locale.getDefault();
		try
		{
			Locale.setDefault(new Locale("tr", "TR"));
			AtomicInteger calls = new AtomicInteger();
			HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
				HiscoreFailureTest.response(chain, calls.incrementAndGet() == 1 ? 404 : 200,
					HiscoreFailureTest.body(3000))).build(), new Gson());
			assertNull(service.lookup("IRON", AccountType.REGULAR).get(3, TimeUnit.SECONDS));
			HiscoreResult result = service.lookup("IRON", AccountType.REGULAR).get(3, TimeUnit.SECONDS);
			assertSame(result, service.getCached("iron"));
			assertFalse(service.isStale("iron"));
			service.markDirty("IRON");
			assertTrue(service.isStale("iron"));
			assertEquals(2, calls.get());
		}
		finally
		{
			Locale.setDefault(previous);
		}
	}
}
