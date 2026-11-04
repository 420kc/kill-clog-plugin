package com.killclog;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A Hardcore Worlds character is a Hardcore Main: its row on that table (RuneLite 1.13.6's HiscoreEndpoint.HARDCORE_MAIN)
 * when the regular board has none for it, or at least as far along as the regular row, which Jagex keeps small and
 * unranked for these characters (11/4: the board's top three were 200 on the regular table at levels 33 to 55, rank
 * -1). A lookup asks that table beside the regular board on a Hardcore world, where RuneLite's own hiscores read it,
 * and elsewhere only for a player the regular board has unranked or not at all.
 */
public class HiscoreHardcoreWorldsTest
{
	private static final String TABLE = "hiscore_oldschool_hardcore_mains";
	private static final int RANKED = 812_345;

	/**
	 * Jagex with a regular answer (a code, its rank and total XP) and a Hardcore Main one (a code and total XP); every
	 * other board has no row.
	 */
	private static HiscoreService service(List<String> asked, AtomicBoolean onHardcoreWorld, int regular, int regularRank,
		long regularXp, int hardcore, long hardcoreXp)
	{
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			asked.add(path);
			boolean there = path.startsWith("/m=" + TABLE + "/");
			int code = path.startsWith("/m=hiscore_oldschool/") ? regular : there ? hardcore : 404;
			String body = there ? HiscoreFailureTest.body(hardcoreXp)
				: HiscoreFailureTest.body(regularXp).replaceFirst("\"rank\":-1", "\"rank\":" + regularRank);
			return HiscoreFailureTest.response(chain, code, body);
		}).build(), new Gson());
		service.setOnHardcoreWorld(onHardcoreWorld::get);
		return service;
	}

	private static HiscoreService service(boolean onHardcoreWorld, int regular, int regularRank, long regularXp,
		int hardcore, long hardcoreXp)
	{
		return service(new CopyOnWriteArrayList<>(), new AtomicBoolean(onHardcoreWorld), regular, regularRank, regularXp,
			hardcore, hardcoreXp);
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
				assertHardcoreMain(onHardcoreWorld + " " + type, service(onHardcoreWorld, 404, -1, 0, 200, 1200)
					.lookup("Hc Tester", type).get(3, TimeUnit.SECONDS), 1200);
			}
		}
	}

	@Test
	public void theSmallUnrankedRegularRowGivesWayToTheHardcoreMainRowFromAnyWorld() throws Exception
	{
		// SunxSioux, the board's first, 11/4: Hardcore Main 204,437 XP; the regular table 200, rank -1, 3,207 XP.
		for (boolean onHardcoreWorld : new boolean[]{false, true})
		{
			for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
			{
				assertHardcoreMain(onHardcoreWorld + " " + type, service(onHardcoreWorld, 200, -1, 3207, 200, 204_437)
					.lookup("Hc Tester", type).get(3, TimeUnit.SECONDS), 204_437);
			}
		}
	}

	@Test
	public void aRowBehindTheRegularOneIsNotTheAccountNow() throws Exception
	{
		for (boolean onHardcoreWorld : new boolean[]{false, true})
		{
			for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
			{
				String why = onHardcoreWorld + " " + type;
				assertHardcoreMain(why, service(onHardcoreWorld, 200, -1, 1200, 200, 1200).lookup("Hc Tester", type)
					.get(3, TimeUnit.SECONDS), 1200);
				// A transfer playing on, still unranked: its regular row is the account now. A reset's fresh
				// row behind the small regular one reads the same way until it passes it, minutes of play.
				assertMain(why, service(onHardcoreWorld, 200, -1, 5000, 200, 1200).lookup("Left", type)
					.get(3, TimeUnit.SECONDS), 5000);
				assertMain(why, service(onHardcoreWorld, 200, RANKED, 5000, 200, 1200).lookup("Left Ranked", type)
					.get(3, TimeUnit.SECONDS), 5000);
				assertMain(why, service(onHardcoreWorld, 200, -1, 5000, 404, 0).lookup("Low Main", type)
					.get(3, TimeUnit.SECONDS), 5000);
			}
		}
	}

	@Test
	public void offAHardcoreWorldARankedRegularRowAsksJagexNothingMore() throws Exception
	{
		for (AccountType type : new AccountType[]{null, AccountType.REGULAR})
		{
			List<String> asked = new CopyOnWriteArrayList<>();
			assertMain(String.valueOf(type), service(asked, new AtomicBoolean(false), 200, RANKED, 1200, 200, 5000)
				.lookup("Main", type).get(3, TimeUnit.SECONDS), 1200);
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
				assertNull(service(onHardcoreWorld, 404, -1, 0, 404, 0).lookup("Nobody", type).get(3, TimeUnit.SECONDS));
				try
				{
					service(onHardcoreWorld, 404, -1, 0, 503, 0).lookup("Hc Tester", type).get(10, TimeUnit.SECONDS);
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
	public void aCachedAnswerStandsOnlyOnTheWorldItWasLookedUpOn() throws Exception
	{
		// A main on both boards with the same row: a Hardcore world reads it as a Hardcore Main, the main
		// game as a ranked main, so neither world's answer stands on the other.
		AtomicBoolean onHardcoreWorld = new AtomicBoolean(true);
		HiscoreService service = service(new CopyOnWriteArrayList<>(), onHardcoreWorld, 200, RANKED, 1200, 200, 1200);
		assertHardcoreMain("looked up on a Hardcore world", service.lookup("Both", null).get(3, TimeUnit.SECONDS), 1200);
		assertFalse(service.isStale("Both"));

		onHardcoreWorld.set(false);
		assertNull("the Hardcore world's answer, off it", service.getCached("Both"));
		assertTrue(service.isStale("Both"));
		assertMain("looked up again on the main game", service.lookup("Both", null).get(3, TimeUnit.SECONDS), 1200);
		assertEquals(HiscoreTable.STANDARD, service.getCached("Both").getHiscoreTable());

		onHardcoreWorld.set(true);
		assertNull("the main game's answer, on a Hardcore world", service.getCached("Both"));
		assertTrue(service.isStale("Both"));
	}

	@Test
	public void anAnswerLandingAfterAHopKeepsTheWorldItWasAskedOn() throws Exception
	{
		AtomicBoolean onHardcoreWorld = new AtomicBoolean(true);
		java.util.concurrent.CountDownLatch hold = new java.util.concurrent.CountDownLatch(1);
		HiscoreService service = new HiscoreService(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			try
			{
				hold.await(3, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			String path = chain.request().url().encodedPath();
			boolean known = path.startsWith("/m=hiscore_oldschool/") || path.startsWith("/m=" + TABLE + "/");
			return HiscoreFailureTest.response(chain, known ? 200 : 404, HiscoreFailureTest.body(1200)
				.replaceFirst("\"rank\":-1", "\"rank\":" + RANKED));
		}).build(), new Gson());
		service.setOnHardcoreWorld(onHardcoreWorld::get);
		java.util.concurrent.CompletableFuture<HiscoreResult> lookup = service.lookup("Both", null);
		onHardcoreWorld.set(false);
		hold.countDown();
		assertHardcoreMain("asked on the Hardcore world", lookup.get(3, TimeUnit.SECONDS), 1200);
		assertNull("kept as the Hardcore world's answer", service.getCached("Both"));
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
