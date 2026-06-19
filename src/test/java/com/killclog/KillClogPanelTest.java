package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogPanelTest
{
	@Test
	public void testClogTierBelowBronze()
	{
		assertNull(ClogHelper.getClogTierName(0, 1700));
		assertNull(ClogHelper.getClogTierName(99, 1700));
	}

	@Test
	public void testClogTierBronze()
	{
		assertEquals("bronze", ClogHelper.getClogTierName(100, 1700));
		assertEquals("bronze", ClogHelper.getClogTierName(200, 1700));
		assertEquals("bronze", ClogHelper.getClogTierName(299, 1700));
	}

	@Test
	public void testClogTierIron()
	{
		assertEquals("iron", ClogHelper.getClogTierName(300, 1700));
		assertEquals("iron", ClogHelper.getClogTierName(499, 1700));
	}

	@Test
	public void testClogTierSteel()
	{
		assertEquals("steel", ClogHelper.getClogTierName(500, 1700));
		assertEquals("steel", ClogHelper.getClogTierName(699, 1700));
	}

	@Test
	public void testClogTierBlack()
	{
		assertEquals("black", ClogHelper.getClogTierName(700, 1700));
		assertEquals("black", ClogHelper.getClogTierName(899, 1700));
	}

	@Test
	public void testClogTierMithril()
	{
		assertEquals("mithril", ClogHelper.getClogTierName(900, 1700));
		assertEquals("mithril", ClogHelper.getClogTierName(999, 1700));
	}

	@Test
	public void testClogTierAdamant()
	{
		assertEquals("adamant", ClogHelper.getClogTierName(1000, 1700));
		assertEquals("adamant", ClogHelper.getClogTierName(1099, 1700));
	}

	@Test
	public void testClogTierRune()
	{
		assertEquals("rune", ClogHelper.getClogTierName(1100, 1700));
		assertEquals("rune", ClogHelper.getClogTierName(1199, 1700));
	}

	@Test
	public void testClogTierDragon()
	{
		assertEquals("dragon", ClogHelper.getClogTierName(1200, 1700));
		assertEquals("dragon", ClogHelper.getClogTierName(1299, 1700));
	}

	@Test
	public void testClogTierGilded()
	{
		// 1700 total slots: 90% = 1530, rounded down to nearest 25 = 1525
		assertEquals("gilded", ClogHelper.getClogTierName(1525, 1700));
		assertEquals("gilded", ClogHelper.getClogTierName(1600, 1700));
		assertEquals("gilded", ClogHelper.getClogTierName(1700, 1700));
		// Just below gilded threshold
		assertEquals("dragon", ClogHelper.getClogTierName(1524, 1700));
	}

	@Test
	public void testClogTierGildedScalesWithTotal()
	{
		// 1800 total slots: 90% = 1620, rounded down to nearest 25 = 1600
		assertEquals("gilded", ClogHelper.getClogTierName(1600, 1800));
		assertEquals("dragon", ClogHelper.getClogTierName(1599, 1800));

		// 2000 total slots: 90% = 1800, rounded down to nearest 25 = 1800
		assertEquals("gilded", ClogHelper.getClogTierName(1800, 2000));
		assertEquals("dragon", ClogHelper.getClogTierName(1799, 2000));
	}

	@Test
	public void testClogTierExactBoundaries()
	{
		// Every tier boundary exact
		assertEquals("bronze", ClogHelper.getClogTierName(100, 1700));
		assertEquals("iron", ClogHelper.getClogTierName(300, 1700));
		assertEquals("steel", ClogHelper.getClogTierName(500, 1700));
		assertEquals("black", ClogHelper.getClogTierName(700, 1700));
		assertEquals("mithril", ClogHelper.getClogTierName(900, 1700));
		assertEquals("adamant", ClogHelper.getClogTierName(1000, 1700));
		assertEquals("rune", ClogHelper.getClogTierName(1100, 1700));
		assertEquals("dragon", ClogHelper.getClogTierName(1200, 1700));
		assertEquals("gilded", ClogHelper.getClogTierName(1525, 1700));
	}

	@Test
	public void testBossArraysInSync()
	{
		assertEquals(
			"BOSS_NAMES (HiscoreService) and BOSSES (PanelData) have different lengths",
			HiscoreService.bossCount(), PanelData.bossCount());
	}

	@Test
	public void testThirdAgeRingStaysOutOfThirdAgeBucket()
	{
		assertFalse(Arrays.stream(PanelData.THIRD_AGE_ITEMS)
			.anyMatch(id -> id == PanelData.THIRD_AGE_RING_ITEM_ID));
		assertEquals(23, PanelData.THIRD_AGE_ITEMS.length);
		assertTrue(Arrays.stream(PanelData.MASTER_RARE_ITEMS)
			.anyMatch(id -> id == PanelData.THIRD_AGE_RING_ITEM_ID));
	}

	@Test
	public void testEmptyRareBucketsUseStaticCatalogs()
	{
		ClogResult result = new ClogResult(
			"TestPlayer",
			Collections.emptyMap(),
			Collections.emptyMap(),
			new HashMap<>(),
			null,
			null
		);
		TooltipDataBuilder builder = new TooltipDataBuilder(null);

		TooltipData thirdAge = builder.buildClueRareData("3rd Age", PanelData.CLOG_THIRD_AGE, result);
		assertNotNull(thirdAge);
		assertEquals(0, thirdAge.obtainedCount);
		assertEquals(PanelData.THIRD_AGE_ITEMS.length, thirdAge.totalItems);

		TooltipData gilded = builder.buildClueRareData("Gilded", PanelData.CLOG_GILDED, result);
		assertNotNull(gilded);
		assertEquals(0, gilded.obtainedCount);
		assertEquals(PanelData.GILDED_ITEMS.length, gilded.totalItems);
	}

	@Test
	public void testRareBucketsFindItemsOutsideSyntheticCategory()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("hard_treasure_trails", Collections.singletonList(
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEM_ID, 1, null)));
		ClogResult result = new ClogResult(
			"TestPlayer",
			obtained,
			Collections.emptyMap(),
			new HashMap<>(),
			null,
			null
		);
		TooltipDataBuilder builder = new TooltipDataBuilder(null);

		TooltipData thirdAge = builder.buildClueRareData("3rd Age", PanelData.CLOG_THIRD_AGE, result);
		assertNotNull(thirdAge);
		assertEquals(1, thirdAge.obtainedCount);
		assertTrue(thirdAge.obtainedIds.contains(PanelData.THIRD_AGE_ITEM_ID));
	}

	@Test
	public void testThirdAgeBucketIgnoresMimicRing()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("mimic", Collections.singletonList(
			new ClogResult.ClogItem(PanelData.THIRD_AGE_RING_ITEM_ID, 1, null)));
		ClogResult result = new ClogResult(
			"TestPlayer",
			obtained,
			Collections.emptyMap(),
			new HashMap<>(),
			null,
			null
		);
		TooltipDataBuilder builder = new TooltipDataBuilder(null);

		TooltipData thirdAge = builder.buildClueRareData("3rd Age", PanelData.CLOG_THIRD_AGE, result);
		assertNotNull(thirdAge);
		assertEquals(0, thirdAge.obtainedCount);
		assertFalse(thirdAge.obtainedIds.contains(PanelData.THIRD_AGE_RING_ITEM_ID));
	}
}
