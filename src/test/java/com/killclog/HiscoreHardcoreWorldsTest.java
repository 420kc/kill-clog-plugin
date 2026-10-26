package com.killclog;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/** A Hardcore Worlds character's row comes from the table killclog.com names, only when the regular one has none. */
public class HiscoreHardcoreWorldsTest
{
	private static final String TABLE = "hiscore_oldschool_hardcore_worlds";

	private static HiscoreService service(List<String> asked, int regular, int hardcore, String named)
	{
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			asked.add(path);
			boolean there = path.startsWith("/m=" + TABLE + "/");
			int code = path.startsWith("/m=hiscore_oldschool/") ? regular : there ? hardcore : 404;
			return HiscoreFailureTest.response(chain, code, HiscoreFailureTest.body(there ? 1200 : 5000));
		}).build(), new Gson());
		service.setHardcoreTable(() -> named);
		return service;
	}

	@Test
	public void aCharacterOnlyThereReadsFromIt() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			List<String> asked = new CopyOnWriteArrayList<>();
			HiscoreResult result = service(asked, 404, 200, TABLE).lookup("Hc Tester", type).get(3, TimeUnit.SECONDS);
			assertNotNull(String.valueOf(type), result);
			assertEquals(1200, result.getTotalXp());
			assertEquals(AccountType.REGULAR, result.getAccountType());
		}
	}

	@Test
	public void theRegularRowWinsAndNoTableMeansNoAsk() throws Exception
	{
		List<String> asked = new CopyOnWriteArrayList<>();
		assertEquals(5000, service(asked, 200, 200, TABLE).lookup("Main", AccountType.REGULAR)
			.get(3, TimeUnit.SECONDS).getTotalXp());
		assertTrue(asked.toString(), asked.stream().noneMatch(path -> path.contains(TABLE)));

		asked.clear();
		assertNull(service(asked, 404, 200, null).lookup("Nobody", AccountType.REGULAR).get(3, TimeUnit.SECONDS));
		assertTrue(asked.toString(), asked.stream().noneMatch(path -> path.contains(TABLE)));
	}

	@Test
	public void notFoundStandsOnlyWhenBothSaySo() throws Exception
	{
		assertNull(service(new CopyOnWriteArrayList<>(), 404, 404, TABLE).lookup("Nobody", AccountType.REGULAR)
			.get(3, TimeUnit.SECONDS));
		try
		{
			service(new CopyOnWriteArrayList<>(), 404, 503, TABLE).lookup("Hc Tester", AccountType.REGULAR)
				.get(10, TimeUnit.SECONDS);
			fail("a table that answers nothing never says not found");
		}
		catch (ExecutionException e)
		{
			assertEquals(HiscoreService.DOWN_MESSAGE, HiscoreService.failureText(e));
		}
	}
}
