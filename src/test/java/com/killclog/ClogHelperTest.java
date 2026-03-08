package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogHelperTest
{
	// ---------------------------------------------------------------
	// formatKc
	// ---------------------------------------------------------------

	@Test
	public void testFormatKcSmall()
	{
		assertEquals("0", ClogHelper.formatKc(0));
		assertEquals("1", ClogHelper.formatKc(1));
		assertEquals("420", ClogHelper.formatKc(420));
		assertEquals("9999", ClogHelper.formatKc(9999));
	}

	@Test
	public void testFormatKcThousands()
	{
		assertEquals("10k", ClogHelper.formatKc(10000));
		assertEquals("50k", ClogHelper.formatKc(50000));
		assertEquals("999k", ClogHelper.formatKc(999999));
	}

	@Test
	public void testFormatKcMillions()
	{
		assertEquals("1m", ClogHelper.formatKc(1000000));
		assertEquals("5m", ClogHelper.formatKc(5000000));
		assertEquals("99m", ClogHelper.formatKc(99000000));
	}

	@Test
	public void testFormatKcBoundaries()
	{
		// Just below 10k — should NOT use "k"
		assertEquals("9999", ClogHelper.formatKc(9999));
		// Exactly 10k — should use "k"
		assertEquals("10k", ClogHelper.formatKc(10000));
		// Just below 1m — should still use "k"
		assertEquals("999k", ClogHelper.formatKc(999999));
		// Exactly 1m — should use "m"
		assertEquals("1m", ClogHelper.formatKc(1000000));
	}

	@Test
	public void testFormatKcNegative()
	{
		// Unranked bosses show -1 — formatKc should handle gracefully
		String result = ClogHelper.formatKc(-1);
		assertNotNull(result);
	}

	// ---------------------------------------------------------------
	// getClogTierName edge cases
	// ---------------------------------------------------------------

	@Test
	public void testClogTierZeroSlots()
	{
		// totalSlots = 0: gildedThreshold = 0, so 0 obtained matches gilded
		String tier = ClogHelper.getClogTierName(0, 0);
		// Should not crash
		assertNotNull(tier);
	}

	@Test
	public void testClogTierObtainedExceedsTotal()
	{
		// Shouldn't happen in practice, but shouldn't crash
		String tier = ClogHelper.getClogTierName(2000, 1700);
		assertEquals("gilded", tier);
	}

	@Test
	public void testClogTierOneSlot()
	{
		// Edge: tiny clog
		String tier = ClogHelper.getClogTierName(1, 1);
		// 90% of 1 = 0, rounded to nearest 25 = 0, so gilded at 0+
		assertEquals("gilded", tier);
	}

	// ---------------------------------------------------------------
	// clogCounts
	// ---------------------------------------------------------------

	@Test
	public void testClogCountsMissingCategory()
	{
		ClogResult result = makeClogResult(Collections.emptyMap(), Collections.emptyMap());
		assertNull(ClogHelper.clogCounts("nonexistent", result));
	}

	@Test
	public void testClogCountsEmptyCategory()
	{
		Map<String, List<Integer>> cats = new HashMap<>();
		cats.put("empty_boss", Collections.emptyList());
		ClogResult result = makeClogResult(cats, Collections.emptyMap());
		assertNull(ClogHelper.clogCounts("empty_boss", result));
	}

	@Test
	public void testClogCountsPartialCompletion()
	{
		Map<String, List<Integer>> cats = new HashMap<>();
		cats.put("zulrah", Arrays.asList(100, 200, 300));

		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("zulrah", Arrays.asList(new ClogResult.ClogItem(100, 1, null)));

		ClogResult result = makeClogResult(cats, obtained);
		int[] counts = ClogHelper.clogCounts("zulrah", result);
		assertNotNull(counts);
		assertEquals(1, counts[0]); // obtained
		assertEquals(3, counts[1]); // total
	}

	@Test
	public void testClogCountsObtainedItemNotInCategory()
	{
		// Player has item 999 which isn't in the category definition
		Map<String, List<Integer>> cats = new HashMap<>();
		cats.put("zulrah", Arrays.asList(100, 200, 300));

		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("zulrah", Arrays.asList(new ClogResult.ClogItem(999, 1, null)));

		ClogResult result = makeClogResult(cats, obtained);
		int[] counts = ClogHelper.clogCounts("zulrah", result);
		assertEquals(0, counts[0]); // 999 not in category, so 0 obtained
		assertEquals(3, counts[1]);
	}

	// ---------------------------------------------------------------
	// getObtainedIds
	// ---------------------------------------------------------------

	@Test
	public void testGetObtainedIdsNullResult()
	{
		Set<Integer> ids = ClogHelper.getObtainedIds("anything", null);
		assertTrue(ids.isEmpty());
	}

	@Test
	public void testGetObtainedIdsMissingCategory()
	{
		ClogResult result = makeClogResult(Collections.emptyMap(), Collections.emptyMap());
		Set<Integer> ids = ClogHelper.getObtainedIds("nonexistent", result);
		assertTrue(ids.isEmpty());
	}

	// ---------------------------------------------------------------
	// countObtained
	// ---------------------------------------------------------------

	@Test
	public void testCountObtainedEmpty()
	{
		assertEquals(0, ClogHelper.countObtained(Collections.emptyList(), new HashSet<>()));
	}

	@Test
	public void testCountObtainedDuplicateIds()
	{
		// allItems has duplicate IDs (shouldn't happen, but shouldn't crash)
		List<Integer> items = Arrays.asList(100, 100, 200);
		Set<Integer> obtained = new HashSet<>(Arrays.asList(100));
		// 100 appears twice in allItems, both match — counts as 2
		assertEquals(2, ClogHelper.countObtained(items, obtained));
	}

	// ---------------------------------------------------------------
	// bossToCategory (ClogService)
	// ---------------------------------------------------------------

	@Test
	public void testBossToCategoryOverrides()
	{
		assertEquals("callisto_and_artio", ClogService.bossToCategory("Artio"));
		assertEquals("callisto_and_artio", ClogService.bossToCategory("Callisto"));
		assertEquals("dagannoth_kings", ClogService.bossToCategory("Dagannoth Prime"));
		assertEquals("chambers_of_xeric", ClogService.bossToCategory("Chambers of Xeric: Challenge Mode"));
		assertEquals("the_nightmare", ClogService.bossToCategory("Phosani's Nightmare"));
		assertEquals("the_gauntlet", ClogService.bossToCategory("The Corrupted Gauntlet"));
		assertEquals("fortis_colosseum", ClogService.bossToCategory("Sol Heredit"));
	}

	@Test
	public void testBossToCategoryAutoConvert()
	{
		assertEquals("zulrah", ClogService.bossToCategory("Zulrah"));
		assertEquals("vorkath", ClogService.bossToCategory("Vorkath"));
		assertEquals("giant_mole", ClogService.bossToCategory("Giant Mole"));
		assertEquals("kril_tsutsaroth", ClogService.bossToCategory("K'ril Tsutsaroth"));
	}

	@Test
	public void testBossToCategorySpecialChars()
	{
		// Apostrophes, colons stripped by regex
		assertEquals("some_boss", ClogService.bossToCategory("Some: Boss!"));
		assertEquals("test_boss", ClogService.bossToCategory("  Test Boss  "));
	}

	// ---------------------------------------------------------------
	// pad
	// ---------------------------------------------------------------

	@Test
	public void testPadShort()
	{
		assertEquals("   1", ClogHelper.pad("1"));
		assertEquals("  42", ClogHelper.pad("42"));
		assertEquals(" 420", ClogHelper.pad("420"));
	}

	@Test
	public void testPadExact()
	{
		assertEquals("1234", ClogHelper.pad("1234"));
	}

	@Test
	public void testPadLong()
	{
		// Longer than 4 chars — leftPad returns as-is
		assertEquals("12345", ClogHelper.pad("12345"));
	}

	// ---------------------------------------------------------------
	// Helper to build ClogResult without nulls
	// ---------------------------------------------------------------

	private ClogResult makeClogResult(
		Map<String, List<Integer>> categoryItems,
		Map<String, List<ClogResult.ClogItem>> obtainedItems)
	{
		return new ClogResult(
			"TestPlayer",
			obtainedItems,
			categoryItems,
			new HashMap<>(),
			null
		);
	}
}
