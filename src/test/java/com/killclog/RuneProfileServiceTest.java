package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
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
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Parsing, cache-path, and intercepted HTTP tests for RuneProfile CA + collection log responses.
 */
public class RuneProfileServiceTest
{
	private RuneProfileService service;

	@Before
	public void setUp()
	{
		service = new RuneProfileService(null, new Gson(), null);
	}

	@Test
	public void testParseAccountSummaryCarriesAccountTypeAndCa()
	{
		String json = "{\"username\":\"gim\",\"accountType\":"
			+ "{\"id\":5,\"key\":\"hardcore_group_ironman\",\"name\":\"Hardcore Group Ironman\"},"
			+ "\"combatAchievements\":["
			+ "{\"id\":1,\"name\":\"Easy\",\"completed\":41,\"total\":41},"
			+ "{\"id\":2,\"name\":\"Medium\",\"completed\":12,\"total\":60}"
			+ "]}";
		RuneProfileService.RuneProfileSummary summary = service.parseAccountSummary(json);
		assertNotNull(summary);
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN, summary.accountType);
		assertNotNull(summary.combatAchievements);
		assertEquals(CombatAchievementTier.EASY, summary.combatAchievements.getTier());
		assertEquals(41, summary.combatAchievements.getCompleted(CombatAchievementTier.EASY));
	}

	@Test
	public void testParseAccountSummaryFallsBackToId()
	{
		String json = "{\"accountType\":{\"id\":4,\"key\":\"new-gim-key\"},\"combatAchievements\":[]}";
		RuneProfileService.RuneProfileSummary summary = service.parseAccountSummary(json);
		assertNotNull(summary);
		assertEquals(AccountType.GROUP_IRONMAN, summary.accountType);
	}

	@Test
	public void testParseAccountSummaryNameOnlyAccountType()
	{
		String json = "{\"accountType\":{\"name\":\"Ultimate Ironman\"},\"combatAchievements\":[]}";
		RuneProfileService.RuneProfileSummary summary = service.parseAccountSummary(json);
		assertNotNull(summary);
		assertEquals(AccountType.ULTIMATE_IRONMAN, summary.accountType);
	}

	@Test
	public void testSummary404CooldownKeepsCachedSuccess() throws Exception
	{
		CombatAchievementResult ca = CombatAchievementResult.of(
			Collections.singletonMap(CombatAchievementTier.EASY, 41),
			Collections.singletonMap(CombatAchievementTier.EASY, 41)
		);
		RuneProfileService.RuneProfileSummary cached =
			new RuneProfileService.RuneProfileSummary(AccountType.GROUP_IRONMAN, ca);

		summaryCache().put("cached", cached);
		longCache("summaryFetchTimes").put("cached", 0L);
		longCache("summaryNotFoundTimes").put("cached", System.currentTimeMillis());

		assertSame(ca, service.lookup("cached").join());
	}

	@Test
	public void testCachedSummaryHealsAgainstLiveCatalog() throws Exception
	{
		// The release-day cache hole: a Grandmaster verdict computed before
		// the catalog captured sits in the summary cache for its TTL. Reads
		// must rebase it against the current catalog with no new HTTP
		// response (the null http client here would fail any request).
		Map<CombatAchievementTier, Integer> full = new EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			full.put(tier, tier.totalTasks());
		}
		CombatAchievementResult stale = CombatAchievementResult.of(full, full);
		assertEquals(CombatAchievementTier.GRANDMASTER, stale.getTier());

		summaryCache().put("cbc",
			new RuneProfileService.RuneProfileSummary(AccountType.GROUP_IRONMAN, stale));
		longCache("summaryFetchTimes").put("cbc", System.currentTimeMillis());

		Map<CombatAchievementTier, Integer> expanded = new EnumMap<>(full);
		expanded.put(CombatAchievementTier.GRANDMASTER,
			full.get(CombatAchievementTier.GRANDMASTER) + 8);
		service.setCaCatalog(CaCatalog.withTotals(expanded));

		CombatAchievementResult healed = service.lookup("cbc").join();
		assertEquals(CombatAchievementTier.MASTER, healed.getTier());
		assertFalse(healed.isAllComplete());
		assertEquals(CombatAchievementTier.MASTER, service.getCached("cbc").getTier());
	}

	@Test
	public void testClog404CooldownKeepsCachedSuccess() throws Exception
	{
		ClogResult cached = new ClogResult(
			"cached",
			Collections.emptyMap(),
			Collections.emptyMap(),
			Collections.emptyMap(),
			null,
			AccountType.GROUP_IRONMAN
		);

		clogCache().put("cached", cached);
		longCache("clogFetchTimes").put("cached", 0L);
		longCache("clogNotFoundTimes").put("cached", System.currentTimeMillis());

		assertSame(cached, service.lookupClog("cached").join());
	}

	@Test
	public void testParsePointsAndTier()
	{
		String json = "{\"data\":["
			+ "{\"id\":1,\"name\":\"Easy\",\"completed\":41,\"total\":41},"
			+ "{\"id\":2,\"name\":\"Medium\",\"completed\":60,\"total\":60}"
			+ "]}";
		CombatAchievementResult r = service.parseCombatAchievements(json);
		assertNotNull(r);
		assertEquals(161, r.getTotalPoints());
		assertEquals(CombatAchievementTier.MEDIUM, r.getTier());
		assertEquals(CombatAchievementReward.MEDIUM, r.getReward());
		assertEquals(41, r.getCompleted(CombatAchievementTier.EASY));
		assertEquals(60, r.getTotal(CombatAchievementTier.MEDIUM));
	}

	@Test
	public void testParseGrandmasterReward()
	{
		String json = "{\"data\":["
			+ "{\"name\":\"Easy\",\"completed\":41,\"total\":41},"
			+ "{\"name\":\"Medium\",\"completed\":60,\"total\":60},"
			+ "{\"name\":\"Hard\",\"completed\":86,\"total\":86},"
			+ "{\"name\":\"Elite\",\"completed\":164,\"total\":164},"
			+ "{\"name\":\"Master\",\"completed\":174,\"total\":174},"
			+ "{\"name\":\"Grandmaster\",\"completed\":121,\"total\":121}"
			+ "]}";
		CombatAchievementResult r = service.parseCombatAchievements(json);
		assertNotNull(r);
		assertEquals(CombatAchievementTier.GRANDMASTER, r.getTier());
		assertEquals(CombatAchievementReward.GRANDMASTER, r.getReward());
		assertTrue(r.isAllComplete());
	}

	@Test
	public void testParseMissingCompletedTreatedAsZero()
	{
		String json = "{\"data\":[{\"name\":\"Easy\",\"total\":33}]}";
		CombatAchievementResult r = service.parseCombatAchievements(json);
		assertNotNull(r);
		assertEquals(0, r.getCompleted(CombatAchievementTier.EASY));
		assertEquals(0, r.getTotalPoints());
		assertNull(r.getTier());
	}

	@Test
	public void testParseMissingDataKey()
	{
		assertNull(service.parseCombatAchievements("{\"error\":\"not found\"}"));
	}

	@Test
	public void testParseDataNotArray()
	{
		assertNull(service.parseCombatAchievements("{\"data\":{}}"));
	}

	@Test
	public void testParseEmptyData()
	{
		assertNull(service.parseCombatAchievements("{\"data\":[]}"));
	}

	@Test
	public void testParseUnrecognizedTiersOnly()
	{
		assertNull(service.parseCombatAchievements("{\"data\":[{\"name\":\"Legendary\",\"completed\":5}]}"));
	}

	@Test
	public void testParseGarbage()
	{
		assertNull(service.parseCombatAchievements("not json"));
		assertNull(service.parseCombatAchievements(""));
	}

	@Test
	public void testParseClogBasic()
	{
		String json = "{\"obtained\":42,\"total\":1500,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Zulrah\",\"items\":["
			+ "{\"id\":12921,\"name\":\"Tanzanite fang\",\"quantity\":2},"
			+ "{\"id\":12922,\"name\":\"Magic fang\",\"quantity\":0},"
			+ "{\"id\":12927,\"name\":\"Serpentine visage\",\"quantity\":1}"
			+ "]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("TestPlayer", json);
		assertNotNull(r);
		assertTrue(r.isFromRuneProfile());
		assertFalse(r.isFromTemple());
		assertFalse(r.isFromKillclog());
		assertEquals("TestPlayer", r.getPlayerName());
		assertEquals(42, r.getUniqueObtained());
		assertEquals(1500, r.getUniqueTotal());

		// Category key: "Zulrah" -> "zulrah"
		Map<String, List<Integer>> cats = r.getCategoryItems();
		assertTrue(cats.containsKey("zulrah"));
		assertEquals(3, cats.get("zulrah").size());

		// Only items with quantity > 0 appear in obtainedItems.
		Map<String, List<ClogResult.ClogItem>> obtained = r.getObtainedItems();
		assertTrue(obtained.containsKey("zulrah"));
		assertEquals(2, obtained.get("zulrah").size());
		assertEquals(12921, obtained.get("zulrah").get(0).getId());
		assertEquals(2, obtained.get("zulrah").get(0).getCount());
	}

	@Test
	public void testParseClogMultipleTabs()
	{
		String json = "{\"obtained\":5,\"total\":100,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Vorkath\",\"items\":[{\"id\":1,\"name\":\"Dragonbone necklace\",\"quantity\":1}]}"
			+ "]},"
			+ "{\"name\":\"Raids\",\"pages\":["
			+ "{\"name\":\"Chambers of Xeric\",\"items\":["
			+ "{\"id\":2,\"name\":\"Twisted bow\",\"quantity\":0},"
			+ "{\"id\":3,\"name\":\"Dragon claws\",\"quantity\":1}"
			+ "]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("multi", json);
		assertNotNull(r);
		assertEquals(2, r.getCategoryItems().size());
		assertTrue(r.getCategoryItems().containsKey("vorkath"));
		assertTrue(r.getCategoryItems().containsKey("chambers_of_xeric"));
		assertEquals(1, r.getObtainedItems().get("vorkath").size());
		assertEquals(1, r.getObtainedItems().get("chambers_of_xeric").size());
	}

	@Test
	public void testParseClogAllZeroCatalogIsUnsynced()
	{
		// RuneProfile returns this full catalog skeleton when no player snapshot exists.
		String json = "{\"obtained\":0,\"total\":50,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":["
			+ "{\"id\":1,\"name\":\"Primordial crystal\",\"quantity\":0},"
			+ "{\"id\":2,\"name\":\"Pegasian crystal\",\"quantity\":0}"
			+ "]}"
			+ "]}"
			+ "]}";
		assertNull(service.parseCollectionLog("dry", json));
	}

	@Test
	public void testParseClogRootZeroWithPositiveItemRemainsData()
	{
		String json = "{\"obtained\":0,\"total\":50,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":["
			+ "{\"id\":1,\"name\":\"Primordial crystal\",\"quantity\":1}"
			+ "]}"
			+ "]}"
			+ "]}";
		ClogResult result = service.parseCollectionLog("inconsistent-root", json);
		assertNotNull(result);
		assertTrue(result.isFromRuneProfile());
		assertEquals(1, result.getObtainedItems().get("cerberus").size());
	}

	@Test
	public void testParseClogPositiveRootWithoutPositiveItemsIsInvalid()
	{
		String json = "{\"obtained\":1,\"total\":50,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":["
			+ "{\"id\":1,\"name\":\"Primordial crystal\",\"quantity\":0}"
			+ "]}"
			+ "]}"
			+ "]}";
		assertNull(service.parseCollectionLog("invalid", json));
	}

	@Test
	public void testAllZeroClogIsHealthyNegativeAndKeepsAccountIdentity() throws Exception
	{
		AtomicInteger summaryRequests = new AtomicInteger();
		AtomicInteger clogRequests = new AtomicInteger();
		CountDownLatch clogStarted = new CountDownLatch(1);
		CountDownLatch releaseClog = new CountDownLatch(1);
		String summaryJson = "{\"accountType\":{\"id\":6,"
			+ "\"key\":\"unranked_group_ironman\",\"name\":\"Unranked Group Ironman\"},"
			+ "\"combatAchievements\":[]}";
		String zeroClogJson = "{\"obtained\":0,\"total\":1717,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":["
			+ "{\"id\":1,\"name\":\"Primordial crystal\",\"quantity\":0}"
			+ "]}"
			+ "]}"
			+ "]}";
		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				boolean clog = chain.request().url().encodedPath().endsWith("/collection-log");
				String body = clog ? zeroClogJson : summaryJson;
				(clog ? clogRequests : summaryRequests).incrementAndGet();
				if (clog)
				{
					clogStarted.countDown();
					try
					{
						if (!releaseClog.await(5, TimeUnit.SECONDS))
						{
							throw new IOException("Timed out waiting to release clog response");
						}
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						throw new IOException(e);
					}
				}
				return new Response.Builder()
					.request(chain.request())
					.protocol(Protocol.HTTP_1_1)
					.code(200)
					.message("OK")
					.body(ResponseBody.create(MediaType.parse("application/json"), body))
					.build();
			})
			.build();
		service = new RuneProfileService(client, new Gson(), null);

		try
		{
			service.lookup("BigWhiteCroc").join();
			assertEquals(AccountType.UNRANKED_GROUP_IRONMAN,
				service.getCachedAccountType("BigWhiteCroc"));

			ClogResult stale = new ClogResult("BigWhiteCroc", Collections.emptyMap(),
				Collections.emptyMap(), Collections.emptyMap(), null, null);
			clogCache().put("bigwhitecroc", stale);
			longCache("clogFetchTimes").put("bigwhitecroc", 0L);
			longCache("clogFailures").put("bigwhitecroc", 0L);
			recordBreakerFailure();
			recordBreakerFailure();
			assertTrue(hasRecentFailure());

			CompletableFuture<ClogResult> lookup = service.lookupClog("BigWhiteCroc");
			assertTrue(clogStarted.await(5, TimeUnit.SECONDS));
			releaseClog.countDown();
			assertNull(lookup.join());
			assertFalse(clogCache().containsKey("bigwhitecroc"));
			assertFalse(longCache("clogFetchTimes").containsKey("bigwhitecroc"));
			assertFalse(longCache("clogFailures").containsKey("bigwhitecroc"));
			assertNotNull(longCache("clogNotFoundTimes").get("bigwhitecroc"));
			assertEquals(AccountType.UNRANKED_GROUP_IRONMAN,
				service.getCachedAccountType("BigWhiteCroc"));

			// The negative cache serves the second lookup without another request.
			assertNull(service.lookupClog("BigWhiteCroc").join());
			assertEquals(1, summaryRequests.get());
			assertEquals(1, clogRequests.get());
			for (long failure : recentFailures())
			{
				assertEquals(0L, failure);
			}
		}
		finally
		{
			client.dispatcher().executorService().shutdownNow();
			client.connectionPool().evictAll();
		}
	}

	@Test
	public void testParseClogItemNamesResolved()
	{
		String json = "{\"obtained\":1,\"total\":10,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Zulrah\",\"items\":["
			+ "{\"id\":12921,\"name\":\"Tanzanite fang\",\"quantity\":1},"
			+ "{\"id\":12922,\"quantity\":0}"  // missing name field
			+ "]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("names", json);
		assertNotNull(r);
		// Item 12921 has a name and is considered resolved.
		assertTrue(r.isItemResolved(12921));
		// Item 12922 has no name field and remains unresolved.
		assertFalse(r.isItemResolved(12922));
	}

	@Test
	public void testParseClogProviderAccountType()
	{
		String json = "{\"obtained\":1,\"total\":10,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Graardor\",\"items\":[{\"id\":1,\"name\":\"B\",\"quantity\":1}]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("gim", json, AccountType.GROUP_IRONMAN);
		assertNotNull(r);
		assertEquals(AccountType.GROUP_IRONMAN, r.getProviderAccountType());
	}

	@Test
	public void testParseClogNullLastChanged()
	{
		String json = "{\"obtained\":1,\"total\":10,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Graardor\",\"items\":[{\"id\":1,\"name\":\"B\",\"quantity\":1}]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("nodate", json);
		assertNotNull(r);
		assertNull(r.getLastChanged());
		assertNull(r.getProviderAccountType());
	}

	@Test
	public void testParseClogEmptyTabs()
	{
		assertNull(service.parseCollectionLog("empty", "{\"obtained\":0,\"total\":0,\"tabs\":[]}"));
	}

	@Test
	public void testParseClogEmptyCatalogPageIsInvalid()
	{
		String json = "{\"obtained\":0,\"total\":50,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":[]}"
			+ "]}"
			+ "]}";
		assertNull(service.parseCollectionLog("empty-page", json));
	}

	@Test
	public void testParseClogMissingTabs()
	{
		assertNull(service.parseCollectionLog("none", "{\"obtained\":0,\"total\":0}"));
	}

	@Test
	public void testParseClogTabsNotArray()
	{
		assertNull(service.parseCollectionLog("bad", "{\"tabs\":{}}"));
	}

	@Test
	public void testParseClogGarbage()
	{
		assertNull(service.parseCollectionLog("x", "not json at all"));
		assertNull(service.parseCollectionLog("x", ""));
	}

	@Test
	public void testParseClogMalformedItems()
	{
		// Non-object item entries are skipped.
		String json = "{\"obtained\":1,\"total\":5,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Zilyana\",\"items\":[42,{\"id\":1,\"name\":\"A\",\"quantity\":1}]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("partial", json);
		assertNotNull(r);
		assertEquals(1, r.getObtainedItems().get("zilyana").size());
	}

	@Test
	public void testParseClogPageMissingName()
	{
		// Pages without names are ignored.
		String json = "{\"obtained\":0,\"total\":0,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"items\":[{\"id\":1,\"name\":\"X\",\"quantity\":1}]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("nopage", json);
		// No valid pages means empty categories, so parsing returns null.
		assertNull(r);
	}

	@Test
	public void testNormalizePageKeySimple()
	{
		assertEquals("zulrah", RuneProfileService.normalizePageKey("Zulrah"));
		assertEquals("vorkath", RuneProfileService.normalizePageKey("Vorkath"));
		assertEquals("cerberus", RuneProfileService.normalizePageKey("Cerberus"));
	}

	@Test
	public void testNormalizePageKeySpaces()
	{
		assertEquals("dagannoth_kings", RuneProfileService.normalizePageKey("Dagannoth Kings"));
		assertEquals("chambers_of_xeric", RuneProfileService.normalizePageKey("Chambers of Xeric"));
		assertEquals("theatre_of_blood", RuneProfileService.normalizePageKey("Theatre of Blood"));
	}

	@Test
	public void testNormalizePageKeyApostrophe()
	{
		// Kree'arra has an override because naive normalization gives "kreearra".
		assertEquals("kree_arra", RuneProfileService.normalizePageKey("Kree'arra"));
		// Other apostrophes strip normally, joining adjacent characters.
		assertEquals("kril_tsutsaroth", RuneProfileService.normalizePageKey("K'ril Tsutsaroth"));
	}

	@Test
	public void testNormalizePageKeyThePrefix()
	{
		// "The Hueycoatl" and "The Royal Titans" strip "The" by override.
		assertEquals("hueycoatl", RuneProfileService.normalizePageKey("The Hueycoatl"));
		assertEquals("royal_titans", RuneProfileService.normalizePageKey("The Royal Titans"));
		// Other "The" names keep it through normal normalization.
		assertEquals("the_nightmare", RuneProfileService.normalizePageKey("The Nightmare"));
		assertEquals("the_gauntlet", RuneProfileService.normalizePageKey("The Gauntlet"));
	}

	@Test
	public void testNormalizePageKeyCommanderZilyana()
	{
		assertEquals("commander_zilyana", RuneProfileService.normalizePageKey("Commander Zilyana"));
	}

	@Test
	public void testNormalizePageKeySpecialChars()
	{
		// Parentheses, colons, hyphens -> underscore, then trailing underscores trimmed
		assertEquals("fortis_colosseum", RuneProfileService.normalizePageKey("Fortis Colosseum"));
		assertEquals("tombs_of_amascut", RuneProfileService.normalizePageKey("Tombs of Amascut"));
	}

	@Test
	public void testNormalizePageKeySharesBossCategoryCanon()
	{
		assertEquals("callisto_and_artio", RuneProfileService.normalizePageKey("Artio"));
		assertEquals("callisto_and_artio", RuneProfileService.normalizePageKey("Callisto"));
		assertEquals("vetion_and_calvarion", RuneProfileService.normalizePageKey("Cal'varion"));
		assertEquals("vetion_and_calvarion", RuneProfileService.normalizePageKey("Vet'ion"));
		assertEquals("venenatis_and_spindel", RuneProfileService.normalizePageKey("Venenatis"));
		assertEquals("venenatis_and_spindel", RuneProfileService.normalizePageKey("Spindel"));
		assertEquals("chambers_of_xeric", RuneProfileService.normalizePageKey("Chambers of Xeric: Challenge Mode"));
		assertEquals("theatre_of_blood", RuneProfileService.normalizePageKey("Theatre of Blood: Hard Mode"));
		assertEquals("tombs_of_amascut", RuneProfileService.normalizePageKey("Tombs of Amascut: Expert Mode"));
		assertEquals("the_fight_caves", RuneProfileService.normalizePageKey("TzTok-Jad"));
		assertEquals("the_inferno", RuneProfileService.normalizePageKey("TzKal-Zuk"));
		assertEquals("the_nightmare", RuneProfileService.normalizePageKey("Phosani's Nightmare"));
		assertEquals("the_gauntlet", RuneProfileService.normalizePageKey("The Corrupted Gauntlet"));
		assertEquals("moons_of_peril", RuneProfileService.normalizePageKey("Lunar Chests"));
	}

	@SuppressWarnings("unchecked")
	private <T> Map<String, T> cache(String fieldName) throws Exception
	{
		Field field = RuneProfileService.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Map<String, T>) field.get(service);
	}

	private Map<String, RuneProfileService.RuneProfileSummary> summaryCache() throws Exception
	{
		return cache("summaryCache");
	}

	private Map<String, ClogResult> clogCache() throws Exception
	{
		return cache("clogCache");
	}

	private Map<String, Long> longCache(String fieldName) throws Exception
	{
		return cache(fieldName);
	}

	private long[] recentFailures() throws Exception
	{
		Field field = RuneProfileService.class.getDeclaredField("recentFailures");
		field.setAccessible(true);
		return (long[]) field.get(service);
	}

	private boolean hasRecentFailure() throws Exception
	{
		for (long failure : recentFailures())
		{
			if (failure != 0L)
			{
				return true;
			}
		}
		return false;
	}

	private void recordBreakerFailure() throws Exception
	{
		Method method = RuneProfileService.class.getDeclaredMethod("recordBreakerFailure");
		method.setAccessible(true);
		method.invoke(service);
	}
}
