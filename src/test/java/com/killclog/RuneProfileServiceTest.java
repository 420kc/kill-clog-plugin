package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Parsing and cache-path tests for the RuneProfile CA + collection log responses.
 * Network behaviour is not exercised; cache tests short-circuit before the HTTP client.
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
			+ "{\"name\":\"Hard\",\"completed\":85,\"total\":85},"
			+ "{\"name\":\"Elite\",\"completed\":162,\"total\":162},"
			+ "{\"name\":\"Master\",\"completed\":168,\"total\":168},"
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
	public void testParseClogNoObtainedItems()
	{
		// Zero-quantity items keep the catalog category but do not create obtained rows.
		String json = "{\"obtained\":0,\"total\":50,\"tabs\":["
			+ "{\"name\":\"Bosses\",\"pages\":["
			+ "{\"name\":\"Cerberus\",\"items\":["
			+ "{\"id\":1,\"name\":\"Primordial crystal\",\"quantity\":0},"
			+ "{\"id\":2,\"name\":\"Pegasian crystal\",\"quantity\":0}"
			+ "]}"
			+ "]}"
			+ "]}";
		ClogResult r = service.parseCollectionLog("dry", json);
		assertNotNull(r);
		assertEquals(2, r.getCategoryItems().get("cerberus").size());
		assertFalse(r.getObtainedItems().containsKey("cerberus"));
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
}
