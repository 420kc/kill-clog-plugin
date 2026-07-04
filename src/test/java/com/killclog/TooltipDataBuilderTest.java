package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TooltipDataBuilderTest
{
	private final TooltipDataBuilder builder = new TooltipDataBuilder(null);

	private static ClogResult clogWith(Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained)
	{
		return new ClogResult("Tester", obtained, categories, new HashMap<>(), null, null);
	}

	private static ClogResult vorkathClog()
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("vorkath", Arrays.asList(22106, 21907, 22006));
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("vorkath", Arrays.asList(
			new ClogResult.ClogItem(22106, 2, null),
			new ClogResult.ClogItem(21907, 1, null)));
		return clogWith(categories, obtained);
	}

	@Test
	public void testBuildTooltipDataCountsAgainstCatalog()
	{
		TooltipData data = builder.buildTooltipData("Vorkath", "vorkath", 42, vorkathClog());

		assertNotNull(data);
		assertEquals("Vorkath", data.name);
		assertEquals(42, data.rank);
		assertEquals(2, data.obtainedCount);
		assertEquals(3, data.totalItems);
		assertTrue(data.rankTracked);
		assertEquals(Integer.valueOf(2), data.obtainedCounts.get(22106));
	}

	@Test
	public void testBuildTooltipDataMissingCategoryIsNull()
	{
		assertNull(builder.buildTooltipData("Zulrah", "zulrah", 1, vorkathClog()));
		assertNull(builder.buildTooltipData("Vorkath", "vorkath", 1, null));
	}

	@Test
	public void testBuildClueRareDataFallsBackToStaticCatalog()
	{
		// No third_age category in the result: the fixed catalog supplies the
		// grid and obtained items are gathered from every category globally.
		Map<String, List<Integer>> categories = new HashMap<>();
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		int thirdAgeItem = PanelData.THIRD_AGE_ITEMS[0];
		obtained.put("hard_treasure_trails", Collections.singletonList(
			new ClogResult.ClogItem(thirdAgeItem, 1, null)));

		TooltipData data = builder.buildClueRareData("3rd Age",
			PanelData.CLOG_THIRD_AGE, clogWith(categories, obtained));

		assertNotNull(data);
		assertEquals(PanelData.THIRD_AGE_ITEMS.length, data.totalItems);
		assertEquals(1, data.obtainedCount);
		assertTrue(data.obtainedIds.contains(thirdAgeItem));
		assertFalse(data.rankTracked);
	}

	@Test
	public void testBuildCustomRareDataScansAllCategoriesAndKeepsMaxCount()
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("vorkath", Collections.singletonList(new ClogResult.ClogItem(995, 3, null)));
		obtained.put("zulrah", Collections.singletonList(new ClogResult.ClogItem(995, 7, null)));

		TooltipData data = builder.buildCustomRareData("Hard Rare",
			new int[]{995, 4151}, clogWith(categories, obtained));

		assertNotNull(data);
		assertEquals(1, data.obtainedCount);
		assertEquals(2, data.totalItems);
		assertEquals(Integer.valueOf(7), data.obtainedCounts.get(995));
		assertFalse(data.obtainedIds.contains(4151));
	}

	@Test
	public void testBuildUnsyncedTooltipDataRidesTheCatalog()
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("vorkath", Arrays.asList(22106, 21907));
		ClogResult catalog = clogWith(categories, new HashMap<>());

		TooltipData data = builder.buildUnsyncedTooltipData(
			"Vorkath", "vorkath", 42, "Kills: ", 1483, catalog);

		assertNotNull(data);
		assertEquals(-1, data.obtainedCount);
		assertEquals(2, data.totalItems);
		assertTrue(data.obtainedIds.isEmpty());
		assertTrue(data.rankTracked);
		assertEquals("Kills: ", data.statLabel);
		assertEquals(1483, data.statValue);
		assertNull(builder.buildUnsyncedTooltipData("Zulrah", "zulrah", 1, "Kills: ", 1, catalog));
		assertNull(builder.buildUnsyncedTooltipData("Vorkath", "vorkath", 1, "Kills: ", 1, null));
	}

	@Test
	public void testBuildUnsyncedItemDataIsUntrackedAndDimmed()
	{
		ClogResult catalog = clogWith(new HashMap<>(), new HashMap<>());

		TooltipData data = builder.buildUnsyncedItemData("3rd Age",
			PanelData.THIRD_AGE_ITEMS, catalog);

		assertNotNull(data);
		assertEquals(-1, data.obtainedCount);
		assertEquals(PanelData.THIRD_AGE_ITEMS.length, data.totalItems);
		assertFalse(data.rankTracked);
		assertNull(data.statLabel);
	}
}
