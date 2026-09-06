package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogPanelTest
{
	@Test
	public void testStatusRowReservesItsHeight()
	{
		JPanel row = new JPanel();
		KillClogPanel.reserveStatusRowHeight(row, 12);
		assertEquals(16, row.getMinimumSize().height);
		assertEquals(16, row.getPreferredSize().height);
		assertEquals(16, row.getMaximumSize().height);

		KillClogPanel.reserveStatusRowHeight(row, 20);
		assertEquals(22, row.getMinimumSize().height);
		assertEquals(22, row.getPreferredSize().height);
		assertEquals(22, row.getMaximumSize().height);
	}

	@Test
	public void testSyncSuccessOnlyClearsSyncOwnedStatus()
	{
		assertTrue(KillClogPanel.isSyncOwnedStatus("sync to killclog.com"));
		assertTrue(KillClogPanel.isSyncOwnedStatus("syncing..."));
		assertTrue(KillClogPanel.isSyncOwnedStatus("retrying..."));
		assertTrue(KillClogPanel.isSyncOwnedStatus("sync failed"));
		assertFalse(KillClogPanel.isSyncOwnedStatus("synced!"));
		assertFalse(KillClogPanel.isSyncOwnedStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS));
		assertFalse(KillClogPanel.isSyncOwnedStatus("player not found"));

		assertTrue(KillClogPanel.canFlashSyncSuccess(" "));
		assertTrue(KillClogPanel.canFlashSyncSuccess("syncing..."));
		assertTrue(KillClogPanel.canFlashSyncSuccess("sync to killclog.com"));
		assertFalse(KillClogPanel.canFlashSyncSuccess("publish character"));
		assertFalse(KillClogPanel.canFlashSyncSuccess(KillClogPlugin.CHARACTER_RENDERING_STATUS));
		assertFalse(KillClogPanel.canFlashSyncSuccess("player not found"));
	}

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
			HiscoreService.bossCount(),
			PanelData.bossCount());
	}

	@Test
	public void testMadAngelOwnsItsCell()
	{
		// The enum path owns the cell outright; the category key reads clog
		// data with the game's "The" prefix.
		assertTrue(java.util.Arrays.asList(PanelData.BOSSES)
			.contains(net.runelite.client.hiscore.HiscoreSkill.MAD_ANGEL));
		assertEquals("the_mad_angel", ClogService.bossToCategory("Mad Angel"));
	}

	@Test
	public void testMaggotKingPromotedIntoBossGrid()
	{
		// RuneLite 1.12.32 shipped the enum; the boss rides the base list now.
		// Vanilla declares it BEFORE Mimic (display), while Jagex's CSV rows
		// carry it after - HiscoreParsingTest holds the parse-order side.
		java.util.List<net.runelite.client.hiscore.HiscoreSkill> bosses =
			java.util.Arrays.asList(PanelData.BOSSES);
		int mimic = bosses.indexOf(net.runelite.client.hiscore.HiscoreSkill.MIMIC);
		assertEquals(mimic - 1, bosses.indexOf(net.runelite.client.hiscore.HiscoreSkill.MAGGOT_KING));
		assertEquals("maggot_king", ClogService.bossToCategory("Maggot King"));
	}

	@Test
	public void testBossGridFollowsVanillaEnumOrder()
	{
		// The panel promises vanilla RuneLite's hiscore layout, which is enum
		// declaration order - a strictly ascending ordinal walk.
		for (int i = 1; i < PanelData.BOSSES.length; i++)
		{
			assertTrue("Boss grid order drift at index " + i,
				PanelData.BOSSES[i].ordinal() > PanelData.BOSSES[i - 1].ordinal());
		}
	}

	@Test
	public void testBossGridAndCsvNamesAgree()
	{
		// Display order and CSV row order may diverge (name-keyed lookups
		// bridge them), but the two lists must always name the same bosses.
		java.util.Set<String> csvNames =
			new java.util.HashSet<>(java.util.Arrays.asList(HiscoreService.bossNames()));
		java.util.Set<String> panelNames = new java.util.HashSet<>();
		for (int i = 0; i < PanelData.BOSSES.length; i++)
		{
			String displayName = PanelData.BOSSES[i].getName();
			panelNames.add(PanelData.NAME_OVERRIDES.getOrDefault(displayName, displayName));
		}
		assertEquals(csvNames, panelNames);
		assertEquals(HiscoreService.bossNames().length,
			PanelData.BOSSES.length);
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
