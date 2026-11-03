package com.killclog;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A Hardcore Worlds character is a Hardcore Main: its row on that table (RuneLite 1.13.6's HiscoreEndpoint.HARDCORE_MAIN)
 * when the regular board has none for it, or the same row on both. A lookup asks that table beside the regular board
 * on a Hardcore world, where RuneLite's own hiscores read it, and elsewhere only after the regular board's "not found".
 */
public class HiscoreHardcoreWorldsTest
{
	private static final String TABLE = "hiscore_oldschool_hardcore_mains";

	/** Jagex with a regular and a Hardcore Main answer (a code, and the row's total XP); every other board has no row. */
	private static HiscoreService service(List<String> asked, boolean onHardcoreWorld, int regular, long regularXp,
		int hardcore, long hardcoreXp)
	{
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			asked.add(path);
			boolean there = path.startsWith("/m=" + TABLE + "/");
			int code = path.startsWith("/m=hiscore_oldschool/") ? regular : there ? hardcore : 404;
			return HiscoreFailureTest.response(chain, code, HiscoreFailureTest.body(there ? hardcoreXp : regularXp));
		}).build(), new Gson());
		service.setOnHardcoreWorld(onHardcoreWorld);
		return service;
	}

	private static HiscoreService service(boolean onHardcoreWorld, int regular, long regularXp, int hardcore,
		long hardcoreXp)
	{
		return service(new CopyOnWriteArrayList<>(), onHardcoreWorld, regular, regularXp, hardcore, hardcoreXp);
	}

	private static void assertHardcoreMain(String why, HiscoreResult result, long xp)
	{
		assertNotNull(why, result);
		assertEquals(why, xp, result.getTotalXp());
		assertEquals(why, AccountType.REGULAR, result.getAccountType());
		assertEquals(why, HiscoreTable.HARDCORE_MAIN, result.getHiscoreTable());
		assertEquals(why, RankLeaderboard.HARDCORE_MAIN, RankLeaderboard.nativeOf(result));
		assertEquals(why, "Hardcore Main", AccountDisplay.of(result.getAccountType(), result.getHiscoreTable()).label());
	}

	private static void assertMain(String why, HiscoreResult result, long xp)
	{
		assertNotNull(why, result);
		assertEquals(why, xp, result.getTotalXp());
		assertEquals(why, HiscoreTable.STANDARD, result.getHiscoreTable());
		assertEquals(why, RankLeaderboard.NORMAL, RankLeaderboard.nativeOf(result));
	}

	@Test
	public void aCharacterOnlyThereIsAHardcoreMainFromAnyWorld() throws Exception
	{
		for (boolean onHardcoreWorld : new boolean[]{false, true})
		{
			for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
			{
				assertHardcoreMain(onHardcoreWorld + " " + type, service(onHardcoreWorld, 404, 0, 200, 1200)
					.lookup("Hc Tester", type).get(3, TimeUnit.SECONDS), 1200);
			}
		}
	}

	@Test
	public void onAHardcoreWorldTheSameRowOnBothIsAHardcoreMainAndARowAheadIsAMain() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			assertHardcoreMain(String.valueOf(type), service(true, 200, 1200, 200, 1200).lookup("Hc Tester", type)
				.get(3, TimeUnit.SECONDS), 1200);
			assertMain(String.valueOf(type), service(true, 200, 5000, 200, 1200).lookup("Left", type)
				.get(3, TimeUnit.SECONDS), 5000);
			assertMain(String.valueOf(type), service(true, 200, 5000, 404, 0).lookup("Main", type)
				.get(3, TimeUnit.SECONDS), 5000);
		}
	}

	@Test
	public void offAHardcoreWorldARegularRowAsksJagexNothingMore() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			List<String> asked = new CopyOnWriteArrayList<>();
			assertMain(String.valueOf(type), service(asked, false, 200, 1200, 200, 1200).lookup("Main", type)
				.get(3, TimeUnit.SECONDS), 1200);
			assertTrue(asked.toString(), asked.stream().noneMatch(path -> path.contains(TABLE)));
		}
	}

	@Test
	public void notFoundStandsOnlyWhenBothSaySo() throws Exception
	{
		for (boolean onHardcoreWorld : new boolean[]{false, true})
		{
			for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
			{
				assertNull(service(onHardcoreWorld, 404, 0, 404, 0).lookup("Nobody", type).get(3, TimeUnit.SECONDS));
				try
				{
					service(onHardcoreWorld, 404, 0, 503, 0).lookup("Hc Tester", type).get(10, TimeUnit.SECONDS);
					fail("a table that answers nothing never says not found");
				}
				catch (ExecutionException e)
				{
					assertEquals(HiscoreService.DOWN_MESSAGE, HiscoreService.failureText(e));
				}
			}
		}
	}

	@Test
	public void theBoardCarriesRuneLitesTableAndIcon()
	{
		assertEquals(TABLE, RankLeaderboard.HARDCORE_MAIN.endpoint);
		assertEquals(TABLE, HiscoreTable.HARDCORE_MAIN.hiscoreKey());
		assertEquals("hardcore_main.png", HiscoreTable.HARDCORE_MAIN.badgeResource());
		assertEquals(18, AccountBadgeResolver.hiscoreIcon(RankLeaderboard.HARDCORE_MAIN.icon).getWidth());
	}
}
