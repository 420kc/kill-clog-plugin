package com.killclog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Edge-case tests for hiscore CSV parsing.
 * Tests parseHiscoreBody, calcCmbLvl, and extractTotalXp directly.
 */
public class HiscoreParsingTest
{
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
		assertEquals(200000000L, service.extractTotalXp("1,2277,200000000\n99,99,13034431"));
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
		// Only 2 columns instead of 3.
		assertEquals(-1, service.extractTotalXp("1,2277"));
	}

	@Test
	public void testExtractTotalXpNonNumeric()
	{
		assertEquals(-1, service.extractTotalXp("1,2277,abc"));
	}

	@Test
	public void testParseNormalBody()
	{
		String body = buildCsv(69, 2277, 4600000000L);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertNotNull(result);
		assertEquals(AccountType.REGULAR, result.getAccountType());
		assertEquals(2277, result.getTotalLevel());
		assertEquals(4600000000L, result.getTotalXp());
		assertEquals(69, result.getOverallRank());
		assertEquals(1, result.getSkillRank("attack"));
		assertEquals(99, result.getSkillLevel("attack"));
		assertEquals(13034431L, result.getSkillXp("attack"));
	}

	@Test
	public void testParseBossKills()
	{
		String body = buildCsv(1, 2277, 4600000000L);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		// First boss (Abyssal Sire) gets KC = 420.
		assertEquals(420, result.getKc("Abyssal Sire"));
		// Last boss (Zulrah) gets KC = 420.
		assertEquals(420, result.getKc("Zulrah"));
	}

	@Test
	public void testMaggotKingRowParsesInHiscoreOrder()
	{
		String[] bossNames = HiscoreService.bossNames();
		// Wyrmscraig re-alphabetized the trio: Mad Angel, Maggot King, Mimic.
		int mimicIndex = Arrays.asList(bossNames).indexOf("Mimic");
		assertEquals(mimicIndex - 1, Arrays.asList(bossNames).indexOf("Maggot King"));
		assertEquals(mimicIndex - 2, Arrays.asList(bossNames).indexOf("Mad Angel"));
		assertEquals(mimicIndex + 1, Arrays.asList(bossNames).indexOf("Nex"));

		String body = buildCsvWithBossNames(1, 2277, 4600000000L, bossNames);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertEquals(expectedBossKc(bossNames, "Lunar Chests"), result.getKc("Lunar Chests"));
		assertEquals(expectedBossKc(bossNames, "Maggot King"), result.getKc("Maggot King"));
		assertEquals(expectedBossKc(bossNames, "Mimic"), result.getKc("Mimic"));
		assertEquals(expectedBossKc(bossNames, "Nex"), result.getKc("Nex"));
		assertEquals(expectedBossKc(bossNames, "Zulrah"), result.getKc("Zulrah"));
	}

	@Test
	public void testParseActivityScores()
	{
		String body = buildCsv(1, 2277, 4600000000L);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		// Activities all get score = 100.
		assertEquals(100, result.getActivityScore("LMS - Rank"));
		assertEquals(100, result.getActivityScore("Soul Wars Zeal"));
		assertEquals(100, result.getActivityScore("Colosseum Glory"));
	}

	@Test
	public void testParseCombatLevel()
	{
		String body = buildCsv(1, 2277, 4600000000L);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		// All skills at 99 produce combat level 126.
		assertEquals(126, result.getCombatLevel());
	}

	@Test
	public void testParseTruncatedNoBosses()
	{
		// Only overall + 24 skills + 20 activities = 45 lines, zero boss lines.
		StringBuilder sb = new StringBuilder();
		sb.append("1,2277,4600000000\n");
		for (int i = 0; i < 24; i++)
		{
			sb.append("1,99,13034431\n");
		}
		for (int i = 0; i < 20; i++)
		{
			sb.append("1,100\n");
		}

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertNotNull(result);
		// Boss KCs are missing.
		assertEquals(-1, result.getKc("Zulrah"));
		// Activities still parse.
		assertEquals(100, result.getActivityScore("LMS - Rank"));
	}

	@Test
	public void testParseTruncatedPartialBosses()
	{
		// 45 + 5 boss lines (only first 5 bosses).
		StringBuilder sb = new StringBuilder();
		sb.append("1,2277,4600000000\n");
		for (int i = 0; i < 24; i++)
		{
			sb.append("1,99,13034431\n");
		}
		for (int i = 0; i < 20; i++)
		{
			sb.append("1,100\n");
		}
		for (int i = 0; i < 5; i++)
		{
			sb.append("50,420\n");
		}

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertNotNull(result);
		// Contract flip (2026-07-29): this CSV path only runs when JSON
		// is down, and visibly imperfect beats blank. The five present rows
		// parse best-effort, the flag drives the misalignment notice, and
		// bosses beyond the truncation stay absent.
		assertTrue(result.isBossSectionShifted());
		assertEquals(420, result.getKc("Abyssal Sire"));
		assertEquals(-1, result.getKc("Zulrah"));
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testParseExtraLinesAtEnd()
	{
		// Normal CSV plus 10 extra lines at the end.
		String body = buildCsv(1, 2277, 4600000000L);
		for (int i = 0; i < 10; i++)
		{
			body += "999,999\n";
		}

		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);
		assertNotNull(result);
		// Contract flip (2026-07-29): best-effort with the flag set.
		// Known boss positions still line up here (the growth is at the tail),
		// so values parse correctly while the notice warns about the mismatch.
		assertTrue(result.isBossSectionShifted());
		assertEquals(420, result.getKc("Zulrah"));
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testParseMalformedBossLine()
	{
		// Build normal CSV, but corrupt one boss line
		StringBuilder sb = new StringBuilder();
		sb.append("1,2277,4600000000\n");
		for (int i = 0; i < 24; i++)
		{
			sb.append("1,99,13034431\n");
		}
		for (int i = 0; i < 20; i++)
		{
			sb.append("1,100\n");
		}
		// First boss: malformed.
		sb.append("not_a_number,abc\n");
		// Second boss: valid.
		sb.append("50,420\n");
		// Remaining bosses valid, keeping the line count exact: a malformed
		// line with the right count is not a format shift, so per-boss
		// best-effort parsing still applies.
		for (int i = 2; i < HiscoreService.bossCount(); i++)
		{
			sb.append("50,420\n");
		}

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertNotNull(result);
		assertFalse(result.isBossSectionShifted());
		assertEquals(-1, result.getKc("Abyssal Sire")); // malformed -> -1
		assertEquals(420, result.getKc("Alchemical Hydra")); // valid
	}

	@Test
	public void testParseUnrankedBoss()
	{
		// Jagex hiscores use -1 for unranked.
		StringBuilder sb = new StringBuilder();
		sb.append("1,2277,4600000000\n");
		for (int i = 0; i < 24; i++)
		{
			sb.append("1,99,13034431\n");
		}
		for (int i = 0; i < 20; i++)
		{
			sb.append("-1,-1\n");
		}
		sb.append("-1,-1\n"); // Abyssal Sire unranked
		// Remaining bosses ranked, keeping the line count exact so the
		// fail-closed guard stays out of this test's way.
		for (int i = 1; i < HiscoreService.bossCount(); i++)
		{
			sb.append("50,420\n");
		}

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertFalse(result.isBossSectionShifted());
		assertEquals(-1, result.getKc("Abyssal Sire"));
		assertEquals(420, result.getKc("Alchemical Hydra"));
	}

	@Test
	public void testParseMinimalBody()
	{
		// Just the overall line, nothing else.
		HiscoreResult result = service.parseHiscoreBody("1,126,100000", AccountType.REGULAR);
		assertNotNull(result);
		assertEquals(126, result.getTotalLevel());
		assertEquals(100000L, result.getTotalXp());
	}

	@Test
	public void testParseSingleLine()
	{
		// Body with no newlines.
		HiscoreResult result = service.parseHiscoreBody("1,2277,4600000000", AccountType.REGULAR);
		assertNotNull(result);
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testCalcCmbLvlMaxed()
	{
		String[] lines = new String[8];
		lines[0] = "1,2277,4600000000";
		// Skills 1-7: attack, defence, strength, hp, ranged, prayer, magic
		for (int i = 1; i <= 7; i++)
		{
			lines[i] = "1,99,13034431";
		}
		assertEquals(126, service.calcCmbLvl(lines));
	}

	@Test
	public void testCalcCmbLvlFresh()
	{
		// Level 1 in everything except 10 HP
		String[] lines = new String[8];
		lines[0] = "0,32,0";
		lines[1] = "0,1,0"; // attack
		lines[2] = "0,1,0"; // defence
		lines[3] = "0,1,0"; // strength
		lines[4] = "0,10,0"; // hp
		lines[5] = "0,1,0"; // ranged
		lines[6] = "0,1,0"; // prayer
		lines[7] = "0,1,0"; // magic
		assertEquals(3, service.calcCmbLvl(lines));
	}

	@Test
	public void testCalcCmbLvlTooFewLines()
	{
		// Only 3 lines, so skills 1-7 cannot be read.
		String[] lines = {"1,2277,0", "1,99,0", "1,99,0"};
		assertEquals(-1, service.calcCmbLvl(lines));
	}

	@Test
	public void testParsePreservesAccountType()
	{
		String body = buildCsv(1, 2277, 4600000000L);
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
		String body = buildCsv(1, 2277, 4600000000L);
		assertEquals(HiscoreTable.STANDARD,
			service.parseHiscoreBody(body, AccountType.REGULAR).getHiscoreTable());
		assertEquals(HiscoreTable.ONE_DEFENCE,
			service.parseHiscoreBody(body, AccountType.REGULAR,
				HiscoreTable.ONE_DEFENCE).getHiscoreTable());
	}

	@Test
	public void testDetectsOneDefenceTableForRegularPure()
	{
		String body = buildCsvWithCombatLevels(60, 1, 80, 90, 85, 52, 94);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertEquals(HiscoreTable.ONE_DEFENCE, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDetectsSkillerTableForRegularSkiller()
	{
		String body = buildCsvWithCombatLevels(1, 1, 1, 10, 1, 1, 1);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertEquals(HiscoreTable.SKILLER, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDoesNotRefineIronPure()
	{
		String body = buildCsvWithCombatLevels(60, 1, 80, 90, 85, 52, 94);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.IRONMAN);

		assertEquals(HiscoreTable.STANDARD, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testDoesNotRefineRegularMain()
	{
		String body = buildCsvWithCombatLevels(99, 99, 99, 99, 99, 99, 99);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertEquals(HiscoreTable.STANDARD, service.detectSpecialHiscoreTable(result));
	}

	@Test
	public void testAllPanelBossesMapToValidCsvNames()
	{
		Set<String> csvNames = new HashSet<>(Arrays.asList(HiscoreService.bossNames()));

		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			String displayName = boss.getName();
			String csvName = PanelData.NAME_OVERRIDES.getOrDefault(displayName, displayName);
			assertTrue(
				"Boss '" + displayName + "' (mapped to '" + csvName + "') not found in BOSS_NAMES[]. " +
				"Add a NAME_OVERRIDES entry if HiscoreSkill.getName() differs from the Jagex CSV name.",
				csvNames.contains(csvName)
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
					"Add it (and its CSV position, sprite, EHB rate) before release.",
					panel.contains(skill)
				);
			}
		}
	}

	@Test
	public void testShiftedCsvParsesBestEffortAndFlags()
	{
		// One extra row simulating a new boss in the block. This fallback path
		// renders best-effort with the flag set, and the tooltip notice warns
		// the reader, instead of blanking the section for the whole update
		// window.
		String body = buildCsv(69, 2277, 4600000000L) + "50,420\n";
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertTrue(result.isBossSectionShifted());
		assertEquals(HiscoreService.bossCount(), result.getBossKills().size());
		assertEquals(2277, result.getTotalLevel());
	}

	@Test
	public void testUnshiftedCsvIsNotFlagged()
	{
		String body = buildCsv(69, 2277, 4600000000L);
		HiscoreResult result = service.parseHiscoreBody(body, AccountType.REGULAR);

		assertFalse(result.isBossSectionShifted());
		assertEquals(HiscoreService.bossCount(), result.getBossKills().size());
	}

	// Wyrmscraig promotion (2026-07-29). Jagex inserted Mad Angel AND
	// re-alphabetized its neighbors (Maggot King now parses before Mimic), so
	// the whole list was promoted from a live response. These pin the observed
	// order; if they fail, the list drifted from what Jagex serves.

	@Test
	public void testWyrmscraigBossOrderPinned()
	{
		String[] names = HiscoreService.bossNames();
		assertEquals(71, names.length);
		assertEquals("Lunar Chests", names[33]);
		assertEquals("Mad Angel", names[34]);
		assertEquals("Maggot King", names[35]);
		assertEquals("Mimic", names[36]);
		assertEquals("Zulrah", names[70]);
	}

	@Test
	public void testMadAngelKcParsesFromCurrentShapeCsv()
	{
		String csv = buildCsvWithBossNames(1, 2277, 4600000000L, HiscoreService.bossNames());
		HiscoreResult result = service.parseHiscoreBody(csv, AccountType.REGULAR);

		assertFalse(result.isBossSectionShifted());
		// buildCsvWithBossNames writes kc = (index+1)*10 per row.
		assertEquals(350, result.getKc("Mad Angel"));
		assertEquals(360, result.getKc("Maggot King"));
		assertEquals(370, result.getKc("Mimic"));
	}

	private String buildCsv(int overallRank, int totalLevel, long totalXp)
	{
		StringBuilder sb = buildCsvPrefix(overallRank, totalLevel, totalXp);
		// Lines 45+: Bosses (all 420 kc)
		for (int i = 0; i < HiscoreService.bossCount(); i++)
		{
			sb.append("50,420\n");
		}
		return sb.toString();
	}

	private String buildCsvWithBossNames(int overallRank, int totalLevel, long totalXp, String[] bossNames)
	{
		StringBuilder sb = buildCsvPrefix(overallRank, totalLevel, totalXp);
		for (int i = 0; i < bossNames.length; i++)
		{
			sb.append("50,").append((i + 1) * 10).append("\n");
		}
		return sb.toString();
	}

	private StringBuilder buildCsvPrefix(int overallRank, int totalLevel, long totalXp)
	{
		StringBuilder sb = new StringBuilder();
		// Line 0: Overall
		sb.append(overallRank).append(",").append(totalLevel).append(",").append(totalXp).append("\n");
		// Lines 1-24: 24 skills (all 99)
		for (int i = 0; i < 24; i++)
		{
			sb.append("1,99,13034431\n");
		}
		// Lines 25-44: 20 activities
		for (int i = 0; i < 20; i++)
		{
			sb.append("1,100\n");
		}
		return sb;
	}

	private String buildCsvWithCombatLevels(int attack, int defence, int strength,
		int hitpoints, int ranged, int prayer, int magic)
	{
		int[] levels = new int[24];
		Arrays.fill(levels, 70);
		levels[0] = attack;
		levels[1] = defence;
		levels[2] = strength;
		levels[3] = hitpoints;
		levels[4] = ranged;
		levels[5] = prayer;
		levels[6] = magic;

		int totalLevel = 0;
		for (int level : levels)
		{
			totalLevel += level;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("1,").append(totalLevel).append(",1000000\n");
		for (int level : levels)
		{
			sb.append("1,").append(level).append(",1\n");
		}
		for (int i = 0; i < 20; i++)
		{
			sb.append("1,100\n");
		}
		for (int i = 0; i < HiscoreService.bossCount(); i++)
		{
			sb.append("50,420\n");
		}
		return sb.toString();
	}

	private int expectedBossKc(String[] bossNames, String bossName)
	{
		int index = Arrays.asList(bossNames).indexOf(bossName);
		assertTrue("Boss not present in test CSV: " + bossName, index >= 0);
		return (index + 1) * 10;
	}

	@Test
	public void testMarkDirtyForcesStaleDespiteFreshCache()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
		service.cacheResult("Zezima", result, now);
		assertFalse(service.isStale("Zezima"));

		service.markDirty("Zezima", now);
		assertTrue(service.isStale("Zezima"));
	}

	@Test
	public void testDirtyClearsWhenFetchLandsAfterSettleWindow()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
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
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
		service.markDirty("Zezima", now);
		service.cacheResult("Zezima", result, now + 3_000);
		assertTrue(service.isStale("Zezima"));
	}

	@Test
	public void testMarkDirtyIsCaseInsensitive()
	{
		long now = System.currentTimeMillis();
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
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
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
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
		HiscoreResult result = service.parseHiscoreBody("1,2277,200000000", AccountType.REGULAR);
		service.cacheResult("Woox", result, now);
		service.markDirty("Zezima", now);
		assertFalse(service.isStale("Woox"));
	}
}
