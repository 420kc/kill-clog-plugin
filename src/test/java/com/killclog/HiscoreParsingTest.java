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
		service = new HiscoreService(null);
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
		assertEquals(420, result.getKc("Abyssal Sire"));
		// Boss beyond the 5 provided is missing.
		assertEquals(-1, result.getKc("Zulrah"));
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
		// Extra lines are ignored.
		assertEquals(420, result.getKc("Zulrah"));
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

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertNotNull(result);
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

		HiscoreResult result = service.parseHiscoreBody(sb.toString(), AccountType.REGULAR);
		assertEquals(-1, result.getKc("Abyssal Sire"));
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

	private String buildCsv(int overallRank, int totalLevel, long totalXp)
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
		// Lines 45+: Bosses (all 420 kc)
		for (int i = 0; i < HiscoreService.bossCount(); i++)
		{
			sb.append("50,420\n");
		}
		return sb.toString();
	}
}
