package com.killclog;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Your Combat Achievements ride your sync (2.8.3): the tiers this login read from the game go out with a main-game
 * sync and come back to lookups from the proof view, and a card shows the fuller of a player's own sync and
 * RuneProfile, ties to the sync, as the fullest log leads.
 */
public class CombatAchievementSyncTest
{
	private static final Session LOGIN = new Session(4, "Tester", 42L);
	private static final ClogResult LOG = new ClogResult("Tester", Map.of(), Map.of(), Map.of(), null, null);

	private static Map<CombatAchievementTier, Integer> elite()
	{
		Map<CombatAchievementTier, Integer> done = new EnumMap<>(CombatAchievementTier.class);
		done.put(CombatAchievementTier.EASY, 41);
		done.put(CombatAchievementTier.MEDIUM, 64);
		done.put(CombatAchievementTier.HARD, 89);
		done.put(CombatAchievementTier.ELITE, 166);
		done.put(CombatAchievementTier.MASTER, 20);
		done.put(CombatAchievementTier.GRANDMASTER, 0);
		return done;
	}

	private static LocalCaCache captured(Session login)
	{
		LocalCaCache cache = new LocalCaCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(null);
		cache.serve(login);
		cache.cacheResult(login.getName(), login.getAccountHash(), elite());
		return cache;
	}

	private static JsonObject sent(String mode, CombatAchievementResult ca)
	{
		return new Gson().fromJson(new SyncService(null, new Gson())
			.requestBody(42L, null, LOG, Map.of(), Map.of(), mode, -1, ca), JsonObject.class);
	}

	@Test
	public void aMainGameSyncSendsEveryTierThisLoginReadWithTheGamesCount()
	{
		JsonArray rows = sent(GameMode.MAIN, captured(LOGIN).thisLogin(LOGIN)).getAsJsonArray("combat_achievements");
		assertEquals(6, rows.size());
		JsonObject master = rows.get(4).getAsJsonObject();
		assertEquals("master", master.get("name").getAsString());
		assertEquals(20, master.get("completed").getAsInt());
		assertEquals(CombatAchievementTier.MASTER.totalTasks(), master.get("total").getAsInt());
		assertEquals("the rows read back as the tier they were", CombatAchievementTier.ELITE,
			CombatAchievementResult.fromRows(rows, null).getTier());
	}

	@Test
	public void aLeagueSyncOrAnotherLoginsReadingSendsNone()
	{
		LocalCaCache cache = captured(LOGIN);
		assertFalse("a League world's sync carries none", sent("demonic-pacts", cache.thisLogin(LOGIN)).has("combat_achievements"));
		Session next = new Session(5, "Tester", 42L);
		cache.serve(next);
		assertNull("the next login sends none until it has read the game", cache.thisLogin(next));
		assertNull("and the last one's never goes with it", cache.thisLogin(LOGIN));
		cache.cacheResult("Tester", 42L, elite());
		assertNotNull("then its own reading", cache.thisLogin(next));
		assertNull("another account never sends this one's", cache.thisLogin(new Session(5, "Tester", 7L)));
		Session other = new Session(6, "Tester", 7L);
		cache.serve(other);
		cache.cacheResult("Tester", 42L, elite());
		assertNull("nor takes a reading of another account for its own", cache.thisLogin(other));
		assertFalse("no reading, no field", sent(GameMode.MAIN, null).has("combat_achievements"));
	}

	@Test
	public void aLookupReadsTheTiersTheirSyncSentAndAGarbledReadingNeverCostsTheLog()
	{
		String json = "{\"rsn\":\"Tester\",\"clog\":{\"items_by_category\":{}},\"combat_achievements\":"
			+ CombatAchievementResult.fromGame(elite(), null, null).toRows() + "}";
		assertEquals(CombatAchievementTier.ELITE,
			new KillclogService(null, new Gson(), null).parseProofView("Tester", json, "tester").getSyncedCa().getTier());

		ClogResult garbled = new KillclogService(null, new Gson(), null).parseProofView("Tester",
			"{\"clog\":{\"items_by_category\":{\"hats\":[{\"item_id\":2991,\"quantity\":1}]}},"
				+ "\"combat_achievements\":[{\"name\":\"Easy\",\"completed\":{},\"total\":41}]}", "tester");
		assertEquals("the log still reads", 1, garbled.getObtainedItems().get("hats").size());
		assertNull(garbled.getSyncedCa());

		assertNull("a sync from before 2.8.3 sent none", new KillclogService(null, new Gson(), null)
			.parseProofView("x", "{\"clog\":{\"items_by_category\":{}}}", "x").getSyncedCa());
	}

	@Test
	public void theSyncedTiersSurviveACombineWhicheverLogWins()
	{
		CombatAchievementResult tiers = CombatAchievementResult.of(elite(), null);
		ClogResult synced = new ClogResult("Tester", Map.of(), Map.of(), Map.of(), null, AccountType.IRONMAN);
		synced.setSyncedCa(tiers);
		ClogResult provider = new ClogResult("Tester", Map.of("hats", List.of(new ClogResult.ClogItem(2991, 1, null))),
			Map.of(), Map.of(), null, AccountType.IRONMAN);
		assertSame("a provider's fuller log keeps them", tiers, ClogResult.pickFullest(provider, synced).getSyncedCa());
		ClogResult fuller = new ClogResult("Tester", Map.of("hats", List.of(new ClogResult.ClogItem(2991, 1, null),
			new ClogResult.ClogItem(2992, 1, null))), Map.of(), Map.of(), null, AccountType.IRONMAN);
		fuller.setSyncedCa(tiers);
		assertSame("and so does their own", tiers, ClogResult.pickFullest(provider, fuller).getSyncedCa());
	}

	@Test
	public void aCardShowsYourOwnGamesTiersThenTheFullerOfTheirSyncAndRuneProfile()
	{
		CombatAchievementResult elite = CombatAchievementResult.of(elite(), null);
		CombatAchievementResult easy = CombatAchievementResult.of(Map.of(CombatAchievementTier.EASY, 41), null);
		CombatAchievementResult yours = CombatAchievementResult.fromGame(Map.of(CombatAchievementTier.EASY, 41), null, null);
		assertSame("yours from your own game, even beside a fuller sync", yours, CombatAchievementResult.shown(yours, elite));
		CombatAchievementResult local = captured(LOGIN).getCached("Tester");
		assertSame("which is what this client reads for you", local, CombatAchievementResult.shown(local, elite));

		// RuneProfile's side as its own parser reads it.
		CombatAchievementResult runeProfile = CombatAchievementResult.fromRows(new Gson().fromJson(
			"[{\"name\":\"Easy\",\"completed\":41,\"total\":41}]", JsonArray.class), null);
		CombatAchievementResult fullerRuneProfile = CombatAchievementResult.fromRows(
			CombatAchievementResult.fromGame(elite(), null, null).toRows(), null);
		assertSame("their sync when it's fuller", elite, CombatAchievementResult.shown(runeProfile, elite));
		assertSame("a tie goes to their sync", easy, CombatAchievementResult.shown(runeProfile, easy));
		assertSame("RuneProfile's when it's fuller: a sync they stopped never stands over it", fullerRuneProfile,
			CombatAchievementResult.shown(fullerRuneProfile, easy));
		assertSame("before RuneProfile answers", elite, CombatAchievementResult.shown(null, elite));
		assertSame("RuneProfile's when their sync sent none", runeProfile, CombatAchievementResult.shown(runeProfile, null));
		assertNull(CombatAchievementResult.shown(null, null));
	}

	@Test
	public void theRowsCarryTheGamesLiveCountNotThePluginsOwn()
	{
		CaCatalog catalog = new CaCatalog();
		net.runelite.api.Client client = org.mockito.Mockito.mock(net.runelite.api.Client.class);
		int threshold = 0;
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			// A release gave Master one more task than the plugin's count.
			threshold += (tier == CombatAchievementTier.MASTER ? 174 : tier.totalTasks()) * tier.pointsPerTask();
			org.mockito.Mockito.when(client.getVarbitValue(tier.thresholdVarbit)).thenReturn(threshold);
		}
		assertTrue(catalog.capture(client));
		LocalCaCache cache = new LocalCaCache(new Gson(), new InlineScheduledExecutorService());
		TestViews.set(cache, "caCatalog", catalog);
		cache.open(null);
		cache.serve(LOGIN);
		cache.cacheResult("Tester", 42L, elite());
		JsonObject master = sent(GameMode.MAIN, cache.thisLogin(LOGIN)).getAsJsonArray("combat_achievements").get(4)
			.getAsJsonObject();
		assertEquals(174, master.get("total").getAsInt());
	}
}
