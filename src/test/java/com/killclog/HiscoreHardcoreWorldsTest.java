package com.killclog;

import com.google.gson.Gson;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A Hardcore Worlds character is a Hardcore Main: its row on that table (RuneLite 1.13.6's HiscoreEndpoint.HARDCORE_MAIN)
 * when the regular board has none for it, or the same row on both. It wears the board's badge and opens on that board.
 */
public class HiscoreHardcoreWorldsTest
{
	private static final String TABLE = "hiscore_oldschool_hardcore_mains";

	/** Jagex with a regular and a Hardcore Main answer (a code, and the row's total XP); every other board has no row. */
	private static HiscoreService service(int regular, long regularXp, int hardcore, long hardcoreXp)
	{
		return new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			boolean there = path.startsWith("/m=" + TABLE + "/");
			int code = path.startsWith("/m=hiscore_oldschool/") ? regular : there ? hardcore : 404;
			return HiscoreFailureTest.response(chain, code, HiscoreFailureTest.body(there ? hardcoreXp : regularXp));
		}).build(), new Gson());
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

	@Test
	public void aCharacterOnlyThereIsAHardcoreMain() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			assertHardcoreMain(String.valueOf(type), service(404, 0, 200, 1200).lookup("Hc Tester", type)
				.get(3, TimeUnit.SECONDS), 1200);
		}
	}

	@Test
	public void theSameRowOnBothBoardsIsAHardcoreMain() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			assertHardcoreMain(String.valueOf(type), service(200, 1200, 200, 1200).lookup("Hc Tester", type)
				.get(3, TimeUnit.SECONDS), 1200);
		}
	}

	@Test
	public void aRegularRowAheadOrAloneIsAMain() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			HiscoreResult ahead = service(200, 5000, 200, 1200).lookup("Left", type).get(3, TimeUnit.SECONDS);
			assertEquals(5000, ahead.getTotalXp());
			assertEquals(HiscoreTable.STANDARD, ahead.getHiscoreTable());
			assertEquals(RankLeaderboard.NORMAL, RankLeaderboard.nativeOf(ahead));

			HiscoreResult alone = service(200, 5000, 404, 0).lookup("Main", type).get(3, TimeUnit.SECONDS);
			assertEquals(5000, alone.getTotalXp());
			assertEquals(HiscoreTable.STANDARD, alone.getHiscoreTable());
		}
	}

	@Test
	public void notFoundStandsOnlyWhenBothSaySo() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			assertNull(service(404, 0, 404, 0).lookup("Nobody", type).get(3, TimeUnit.SECONDS));
			try
			{
				service(404, 0, 503, 0).lookup("Hc Tester", type).get(10, TimeUnit.SECONDS);
				fail("a table that answers nothing never says not found");
			}
			catch (ExecutionException e)
			{
				assertEquals(HiscoreService.DOWN_MESSAGE, HiscoreService.failureText(e));
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
