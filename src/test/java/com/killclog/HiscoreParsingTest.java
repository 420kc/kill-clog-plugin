package com.killclog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Edge-case tests for hiscore parsing.
 * Tests parseHiscoreBody, calcCmbLvlExact, and extractTotalXp directly.
 */
public class HiscoreParsingTest
{
	// Just the Overall row, as an unranked or brand-new account's answer can be.
	static final String OVERALL_ONLY = "{\"skills\":[" + skill("Overall", 1, 2277, 200000000L) + "],\"activities\":[]}";

	private HiscoreService service;

	@Before
	public void setUp()
	{
		// Null httpClient: these tests exercise parsing only.
		service = new HiscoreService(null, new com.google.gson.Gson());
	}

	@Test
	public void testExtractTotalXpNormal()
	{
		assertEquals(200000000L, service.extractTotalXp(OVERALL_ONLY));
	}

	@Test
	public void testExtractTotalXpNull()
	{
		assertEquals(-1, service.extractTotalXp(null));
	}

	@Test
	public void testExtractTotalXpEmpty()
	{
		assertEquals(-1, service.extractTotalXp(""));
	}

	@Test
	public void testExtractTotalXpMalformed()
	{
		// An Overall row without its xp.
		assertEquals(-1, service.extractTotalXp("{\"skills\":[{\"name\":\"Overall\",\"rank\":1,\"level\":2277}]}"));
		assertEquals(-1, service.extractTotalXp("{\"skills\":[],\"activities\":[]}"));
		assertEquals(-1, service.extractTotalXp("{broken"));
	}

	@Test
	public void testExtractTotalXpNonNumeric()
	{
		assertEquals(-1, service.extractTotalXp(OVERALL_ONLY.replace("200000000", "\"abc\"")));
	}

	@Test
	public void testExtractTotalXpReadsNoCsv()
	{
		// The CSV transport is retired: a positional body reads as nothing, never as a total.
		assertEquals(-1, service.extractTotalXp("1,2277,200000000\n99,99,13034431"));
	}

	@Test
	public void testParseNormalBody()
	{
		HiscoreResult result = service.parseHiscoreBody(body(69, 2277, 4600000000L), AccountType.REGULAR);

		assertNotNull(result);
		assertEquals(AccountType.REGULAR, result.getAccountType());
		assertEquals(2277, result.getTotalLevel());
		assertEquals(4600000000L, result.getTotalXp());
		assertEquals(69, result.getOverallRank());
		assertEquals(1, result.getSkillRank("attack"));
		assertEquals(99, result.getSkillLevel("attack"));
		assertEquals(13034431L, result.getSkillXp("attack"));
		assertEquals(50, result.getRank("Zulrah"));
		assertEquals(7, result.getActivityRank("LMS - Rank"));
	}

	@Test
	public void testParseBossKills()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 2277, 4600000000L), AccountType.REGULAR);

		// Every boss the hiscores carry gets its row.
		assertEquals(420, result.getKc("Abyssal Sire"));
		assertEquals(420, result.getKc("Zulrah"));
		assertEquals(HiscoreService.bossCount(), result.getBossKills().size());
	}

	@Test
	public void testParseActivityScores()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 2277, 4600000000L), AccountType.REGULAR);

		// Activities all get score = 100, and never land among the bosses.
		assertEquals(100, result.getActivityScore("LMS - Rank"));
		assertEquals(100, result.getActivityScore("Soul Wars Zeal"));
		assertEquals(100, result.getActivityScore("Colosseum Glory"));
		assertFalse(result.getBossKills().containsKey("Colosseum Glory"));
	}

	@Test
	public void testParseCombatLevel()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 2277, 4600000000L), AccountType.REGULAR);

		// All skills at 99 produce combat level 126.
		assertEquals(126, result.getCombatLevel());
	}

	@Test
	public void testParseUnrankedBoss()
	{
		// Jagex hiscores use -1 for unranked.
		String body = body(1, 2277, 4600000000L).replace(activity("Abyssal Sire", 50, 420),
			activity("Abyssal Sire", -1, -1));
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);
		assertEquals(-1, result.getKc("Abyssal Sire"));
		assertEquals(-1, result.getRank("Abyssal Sire"));
		assertTrue(result.hasBossRow("Abyssal Sire"));
		assertEquals(420, result.getKc("Alchemical Hydra"));
	}

	@Test
	public void testParseMinimalBody()
	{
		// Just the overall row, nothing else.
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		assertNotNull(result);
		assertEquals(2277, result.getTotalLevel());
		assertEquals(200000000L, result.getTotalXp());
		assertEquals(-1, result.getKc("Zulrah"));
		assertEquals(-1, result.getCombatLevelExact(), 0);
	}

	@Test
	public void testCalcCmbLvlMaxed()
	{
		assertEquals(126, service.parseHiscoreBody(body(1, 2277, 1, 99, 99, 99, 99, 99, 99, 99),
			AccountType.REGULAR).getCombatLevel());
	}

	@Test
	public void testCalcCmbLvlFresh()
	{
		// Level 1 in every combat skill except 10 HP.
		assertEquals(3, service.parseHiscoreBody(body(1, 32, 1, 1, 1, 1, 10, 1, 1, 1),
			AccountType.REGULAR).getCombatLevel());
	}

	@Test
	public void exactCombatLevelKeepsItsFractionAndFloorsToTheCellValue()
	{
		java.util.Map<String, Integer> levels = new java.util.HashMap<>();
		levels.put("attack", 70);
		levels.put("strength", 75);
		levels.put("defence", 70);
		levels.put("hitpoints", 78);
		levels.put("ranged", 80);
		levels.put("prayer", 55);
		levels.put("magic", 77);
		// 0.25 * (70 + 78 + 27) + 0.325 * (70 + 75) = 43.75 + 47.125
		assertEquals(90.875, service.calcCmbLvlExact(levels), 1e-9);
		assertEquals(90, TestViews.combatLevelFromLevels(service, levels));

		HiscoreResult result = TestViews.hiscore(AccountType.REGULAR, null, null, null, null,
			levels, 0, 0L, service.calcCmbLvlExact(levels), -1);
		assertEquals(90, result.getCombatLevel());
		assertEquals(90.875, result.getCombatLevelExact(), 1e-9);
		assertEquals(90.875, result.withRanks(null).getCombatLevelExact(), 1e-9);

		levels.remove("magic");
		assertEquals(-1, service.calcCmbLvlExact(levels), 0);
		assertEquals(-1, TestViews.combatLevelFromLevels(service, levels));
	}

	@Test
	public void theExactCombatLevelReadsAsItsOwnFormulaDid()
	{
		// RuneLite's precise combat level now does the sum; every level from 1 to 99 in each skill reads as the
		// plugin's own formula read it, bit for bit.
		java.util.Random random = new java.util.Random(2410);
		String[] skills = {"attack", "strength", "defence", "hitpoints", "ranged", "prayer", "magic"};
		for (int round = 0; round < 200_000; round++)
		{
			java.util.Map<String, Integer> levels = new java.util.HashMap<>();
			for (String skill : skills)
			{
				levels.put(skill, round < 99 * 7 ? (skill.equals(skills[round / 99]) ? round % 99 + 1 : 1)
					: 1 + random.nextInt(99));
			}
			assertEquals(levels.toString(), Double.doubleToLongBits(formerFormula(levels)),
				Double.doubleToLongBits(service.calcCmbLvlExact(levels)));
		}
	}

	private static double formerFormula(java.util.Map<String, Integer> levels)
	{
		double base = 0.25 * (levels.get("defence") + levels.get("hitpoints") + Math.floor(levels.get("prayer") / 2.0));
		double melee = 0.325 * (levels.get("attack") + levels.get("strength"));
		double range = 0.325 * (Math.floor(levels.get("ranged") * 3.0 / 2.0));
		double mage = 0.325 * (Math.floor(levels.get("magic") * 3.0 / 2.0));
		return base + Math.max(melee, Math.max(range, mage));
	}

	@Test
	public void testCalcCmbLvlMissingCombatSkill()
	{
		// An answer without Magic cannot give a combat level.
		String body = body(1, 2277, 4600000000L).replace(skill("Magic", 1, 99, 13034431L) + ",", "");
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);
		assertEquals(-1, result.getSkillLevel("magic"));
		assertEquals(-1, result.getCombatLevel());
	}

	@Test
	public void testParsePreservesAccountType()
	{
		String body = body(1, 2277, 4600000000L);
		assertEquals(AccountType.IRONMAN,
			service.parseHiscoreBody(body, AccountType.IRONMAN).getAccountType());
		assertEquals(AccountType.HARDCORE_IRONMAN,
			service.parseHiscoreBody(body, AccountType.HARDCORE_IRONMAN).getAccountType());
		assertEquals(AccountType.ULTIMATE_IRONMAN,
			service.parseHiscoreBody(body, AccountType.ULTIMATE_IRONMAN).getAccountType());
	}

	@Test
	public void testParsePreservesHiscoreTable()
	{
		String body = body(1, 2277, 4600000000L);
		assertEquals(HiscoreTable.STANDARD,
			service.parseHiscoreBody(body, AccountType.REGULAR).getHiscoreTable());
		assertEquals(HiscoreTable.ONE_DEFENCE,
			service.parseHiscoreBody(body, AccountType.REGULAR,
				HiscoreTable.ONE_DEFENCE).getHiscoreTable());
	}

	@Test
	public void testDetectsOneDefenceTableForRegularPure()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 1500, 1, 60, 1, 80, 90, 85, 52, 94),
			AccountType.REGULAR);

		assertEquals(HiscoreTable.ONE_DEFENCE, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDetectsSkillerTableForRegularSkiller()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 1500, 1, 1, 1, 1, 10, 1, 1, 1),
			AccountType.REGULAR);

		assertEquals(HiscoreTable.SKILLER, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDoesNotRefineIronPure()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 1500, 1, 60, 1, 80, 90, 85, 52, 94),
			AccountType.IRONMAN);

		assertEquals(HiscoreTable.STANDARD, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDoesNotRefineRegularMain()
	{
		HiscoreResult result = service.parseHiscoreBody(body(1, 2277, 1, 99, 99, 99, 99, 99, 99, 99),
			AccountType.REGULAR);

		assertEquals(HiscoreTable.STANDARD, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testAllPanelBossesMapToValidHiscoreNames()
	{
		Set<String> hiscoreNames = new HashSet<>(Arrays.asList(HiscoreService.bossNames()));

		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			String displayName = boss.getName();
			String hiscoreName = PanelData.hiscoreName(displayName);
			assertTrue(
				"Boss '" + displayName + "' (mapped to '" + hiscoreName + "') not found in BOSS_NAMES[]. " +
				"Add a NAME_OVERRIDES entry if HiscoreSkill.getName() differs from the Jagex hiscore name.",
				hiscoreNames.contains(hiscoreName)
			);
		}
	}

	@Test
	public void testEveryRuneLiteBossAppearsInPanel()
	{
		// The reverse direction of the mapping test above: when RuneLite's enum
		// grows a boss (the Maggot King pattern, next The Mad Angel), this
		// screams until PanelData carries it. Completeness gate from the
		// 2026-07-16 pre-Mad-Angel hardening review.
		Set<HiscoreSkill> panel = new HashSet<>(Arrays.asList(PanelData.BOSSES));
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == net.runelite.client.hiscore.HiscoreSkillType.BOSS)
			{
				assertTrue(
					"RuneLite knows boss '" + skill.getName() + "' but PanelData.BOSSES does not. " +
					"Add it (and its hiscore name, sprite, EHB rate) before release.",
					panel.contains(skill)
				);
			}
		}
	}

	/**
	 * A JSON hiscores answer: Overall, then every skill (the leading ones at {@code levels}
	 * in hiscore order, attack first; the rest 99), every activity at 100 and every boss at 420.
	 */
	private static String body(int overallRank, int totalLevel, long totalXp, int... levels)
	{
		StringBuilder sb = new StringBuilder("{\"skills\":[").append(skill("Overall", overallRank, totalLevel, totalXp));
		String[] skills = HiscoreService.skillNames();
		for (int i = 0; i < skills.length; i++)
		{
			String name = Character.toUpperCase(skills[i].charAt(0)) + skills[i].substring(1);
			sb.append(',').append(skill(name, 1, i < levels.length ? levels[i] : 99, 13034431L));
		}
		sb.append("],\"activities\":[");
		String separator = "";
		for (String name : TestViews.<String[]>field(HiscoreService.class, "ACTIVITY_NAMES"))
		{
			sb.append(separator).append(activity(name, 7, 100));
			separator = ",";
		}
		for (String name : HiscoreService.bossNames())
		{
			sb.append(',').append(activity(name, 50, 420));
		}
		return sb.append("]}").toString();
	}

	private static String skill(String name, int rank, int level, long xp)
	{
		return "{\"name\":\"" + name + "\",\"rank\":" + rank + ",\"level\":" + level + ",\"xp\":" + xp + "}";
	}

	private static String activity(String name, int rank, int score)
	{
		return "{\"name\":\"" + name + "\",\"rank\":" + rank + ",\"score\":" + score + "}";
	}

	@Test
	public void testMarkDirtyForcesStaleDespiteFreshCache()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.cacheResult("Zezima", result, now);
		assertFalse(service.isStale("Zezima"));

		service.markDirty("Zezima", now);
		assertTrue(service.isStale("Zezima"));
	}

	@Test
	public void testDirtyClearsWhenFetchLandsAfterSettleWindow()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.markDirty("Zezima", now);
		service.cacheResult("Zezima", result, now + 10_000);
		assertFalse(service.isStale("Zezima"));
	}

	@Test
	public void testDirtySurvivesFetchInsideSettleWindow()
	{
		// The row lands server-side seconds after a hop: a fetch inside the
		// settle window may still be pre-hop, so the mark must survive it.
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.markDirty("Zezima", now);
		service.cacheResult("Zezima", result, now + 3_000);
		assertTrue(service.isStale("Zezima"));
	}

	@Test
	public void testMarkDirtyIsCaseInsensitive()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.cacheResult("Zezima", result, now);
		service.markDirty("ZEZIMA", now);
		assertTrue(service.isStale("zezima"));
	}

	@Test
	public void testFetchClearsOnlyTheMarkItObserved()
	{
		// A second hop can re-mark while the first fetch is completing; the
		// stale fetch must not clear the newer mark. The cache is seeded
		// fresh so the assertion can only pass through the surviving mark.
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.cacheResult("Zezima", result, now);
		service.markDirty("Zezima", now);
		service.markDirty("Zezima", now + 5_000);
		service.clearMarkIfSettled("zezima", now, now + 10_000);
		assertTrue(service.isStale("Zezima"));
	}

	@Test
	public void testMarkDirtyLeavesOtherPlayersAlone()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody(OVERALL_ONLY, AccountType.REGULAR);
		service.cacheResult("Woox", result, now);
		service.markDirty("Zezima", now);
		assertFalse(service.isStale("Woox"));
	}
}
