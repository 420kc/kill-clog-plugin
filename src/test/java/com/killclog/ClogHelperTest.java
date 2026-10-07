package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogHelperTest
{
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
		// Just below 10k: no "k".
		assertEquals("9999", ClogHelper.formatKc(9999));
		// Exactly 10k: use "k".
		assertEquals("10k", ClogHelper.formatKc(10000));
		// Just below 1m: still use "k".
		assertEquals("999k", ClogHelper.formatKc(999999));
		// Exactly 1m: use "m".
		assertEquals("1m", ClogHelper.formatKc(1000000));
	}

	@Test
	public void testFormatKcNegative()
	{
		// Unranked bosses show -1.
		String result = ClogHelper.formatKc(-1);
		assertNotNull(result);
	}

	@Test
	public void testClogTierZeroSlots()
	{
		// Empty catalogs still resolve to a tier without crashing.
		String tier = ClogHelper.getClogTierName(0, 0);
		assertNotNull(tier);
	}

	@Test
	public void testClogTierObtainedExceedsTotal()
	{
		// Defensive path for malformed provider totals.
		String tier = ClogHelper.getClogTierName(2000, 1700);
		assertEquals("gilded", tier);
	}

	@Test
	public void testClogTierOneSlot()
	{
		// Tiny catalogs round the gilded threshold down to zero.
		String tier = ClogHelper.getClogTierName(1, 1);
		assertEquals("gilded", tier);
	}

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
		// Items outside the category definition do not count.
		Map<String, List<Integer>> cats = new HashMap<>();
		cats.put("zulrah", Arrays.asList(100, 200, 300));

		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("zulrah", Arrays.asList(new ClogResult.ClogItem(999, 1, null)));

		ClogResult result = makeClogResult(cats, obtained);
		int[] counts = ClogHelper.clogCounts("zulrah", result);
		assertEquals(0, counts[0]); // 999 not in category, so 0 obtained
		assertEquals(3, counts[1]);
	}

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

	@Test
	public void testCountObtainedEmpty()
	{
		assertEquals(0, ClogHelper.countObtained(Collections.emptyList(), new HashSet<>()));
	}

	@Test
	public void testCountObtainedDuplicateIds()
	{
		// Duplicate catalog ids count by slot.
		List<Integer> items = Arrays.asList(100, 100, 200);
		Set<Integer> obtained = new HashSet<>(Arrays.asList(100));
		// 100 appears twice in allItems, so both entries count.
		assertEquals(2, ClogHelper.countObtained(items, obtained));
	}

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
		// Apostrophes and punctuation normalize through the generic path.
		assertEquals("some_boss", ClogService.bossToCategory("Some: Boss!"));
		assertEquals("test_boss", ClogService.bossToCategory("  Test Boss  "));
	}

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
		// Longer than 4 chars: leftPad returns as-is.
		assertEquals("12345", ClogHelper.pad("12345"));
	}

	@Test
	public void theTierLadderReadsEveryRangeAndLeavesGildedOpen()
	{
		// Gilded starts at (total * 0.9) rounded down to 25s: 1350 for 1500 slots.
		String[] labels = ClogHelper.tierLabels(1500, 1500);
		assertEquals(ClogHelper.CLOG_TIERS.length, labels.length);
		assertEquals("Bronze: 100-299", labels[0]);
		assertEquals("Rune: 1,100-1,199", labels[6]);
		assertEquals("Dragon: 1,200-1,349", labels[7]);
		assertEquals("Gilded: 1,350+", labels[8]);
	}

	@Test
	public void aTierStillAheadSaysHowFarOffItIs()
	{
		String[] labels = ClogHelper.tierLabels(1182, 1500);
		// Reached tiers, the current one included, read as a plain range.
		assertEquals("Bronze: 100-299", labels[0]);
		assertEquals("Rune: 1,100-1,199", labels[6]);
		assertEquals("Dragon: 1,200-1,349 (18 more)", labels[7]);
		// Landing exactly on a tier reaches it: nothing is left to go.
		assertEquals("Dragon: 1,200-1,349", ClogHelper.tierLabels(1200, 1500)[7]);
		assertEquals("Dragon: 1,200-1,349 (1 more)", ClogHelper.tierLabels(1199, 1500)[7]);
		// A log too small for its own thresholds still never calls a tier it lights "ahead".
		for (String label : ClogHelper.tierLabels(950, 1000))
		{
			assertFalse(label, label.endsWith("more)"));
		}
		assertEquals("Gilded: 1,350+ (168 more)", labels[8]);
		assertEquals("Bronze: 100-299 (100 more)", ClogHelper.tierLabels(0, 1500)[0]);
		assertEquals("Gilded: 1,350+ (1,350 more)", ClogHelper.tierLabels(0, 1500)[8]);
	}

	@Test
	public void aTierIsNamedFromTheSameThresholdsTheLadderShows()
	{
		for (int tier = 0; tier < ClogHelper.CLOG_TIERS.length; tier++)
		{
			int start = ClogHelper.tierThreshold(tier, 1500);
			assertEquals(ClogHelper.CLOG_TIERS[tier], ClogHelper.getClogTierName(start, 1500));
			assertEquals(tier == 0 ? null : ClogHelper.CLOG_TIERS[tier - 1],
				ClogHelper.getClogTierName(start - 1, 1500));
			assertEquals(tier, ClogHelper.tierIndex(start, 1500));
			assertEquals(tier - 1, ClogHelper.tierIndex(start - 1, 1500));
		}
	}

	private ClogResult makeClogResult(
		Map<String, List<Integer>> categoryItems,
		Map<String, List<ClogResult.ClogItem>> obtainedItems)
	{
		return new ClogResult(
			"TestPlayer",
			obtainedItems,
			categoryItems,
			new HashMap<>(),
			null,
			null
		);
	}

	@Test
	public void aRaidsHardModeReadsOnTheRaidsOwnCard()
	{
		assertEquals(HiscoreSkill.CHAMBERS_OF_XERIC, PanelData.card(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE));
		assertEquals(HiscoreSkill.TOMBS_OF_AMASCUT, PanelData.card(HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT));
		assertEquals(HiscoreSkill.ZULRAH, PanelData.card(HiscoreSkill.ZULRAH));

		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 730);
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE.getName(), 110);
		Map<String, Integer> ranks = new HashMap<>();
		ranks.put(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE.getName(), 1204);
		HiscoreResult result = new HiscoreResult(AccountType.REGULAR, kills, ranks,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1);
		TooltipData data = TooltipData.builder().name("Chambers of Xeric").totalItems(23).kc(730).build();
		TooltipData raid = TooltipDataBuilder.withHardMode(data, HiscoreSkill.CHAMBERS_OF_XERIC, result,
			name -> name.equals(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE.getName()) ? "28:01" : null);
		assertEquals("CM", raid.hardLabel);
		assertEquals(110, raid.hardKc);
		assertEquals("28:01", raid.hardPb);
		assertEquals(1204, raid.hardRank);
		assertSame(data, TooltipDataBuilder.withHardMode(data, HiscoreSkill.ZULRAH, result, name -> "1:00"));

		// The hard mode is one more line under the raid's own KC and PB.
		ImgTooltip plain = new ImgTooltip(5);
		plain.setTitle("Chambers of Xeric");
		plain.setInfoLine("KC: ", "730", java.awt.Color.WHITE);
		ImgTooltip both = new ImgTooltip(5);
		both.setTitle("Chambers of Xeric");
		both.setInfoLine("KC: ", "730", java.awt.Color.WHITE);
		ClogHelper.addHardModeLine(both, raid, true, true);
		assertEquals(NativeTooltip.LINE_HEIGHT, both.getHeaderHeight() - plain.getHeaderHeight());
		ClogHelper.addHardModeLine(plain, raid, false, false);
		assertEquals(both.getHeaderHeight() - NativeTooltip.LINE_HEIGHT, plain.getHeaderHeight());
	}

	@Test
	public void theRareShelfFillsAsTheProfileCardDoes()
	{
		// A trophy, five 3rd age pieces and two megarares: the megarares keep their room among six.
		List<ClogResult.ClogItem> held = new java.util.ArrayList<>();
		for (int id : new int[]{20590, 10350, 10348, 10346, 23242, 10352})
		{
			held.add(new ClogResult.ClogItem(id, 1, null));
		}
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("misc", held);
		obtained.put("chambers_of_xeric", Collections.singletonList(new ClogResult.ClogItem(20997, 2, null)));
		obtained.put("theatre_of_blood", Collections.singletonList(new ClogResult.ClogItem(22486, 1, null)));
		ClogResult clog = new ClogResult("Tester", obtained, Collections.emptyMap(), Collections.emptyMap(), null, null);

		List<Integer> shelf = new java.util.ArrayList<>();
		for (ClogResult.ClogItem item : ClogHelper.rareItems(clog))
		{
			shelf.add(item.getId());
		}
		assertEquals(Arrays.asList(20590, 10350, 10348, 10346, 20997, 22486), shelf);
	}
}
