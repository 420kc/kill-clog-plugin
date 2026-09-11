package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClogCanonicalizationTest
{
	private static final List<Integer> TITHE_CATALOG = Arrays.asList(
		13646, 13647, 13642, 13643, 13640, 13641, 13644, 13645,
		13639, 13353, 13226);
	private static final List<ClogResult.ClogItem> FEMALE_TITHE_OBTAINED = Arrays.asList(
		item(13647), item(13643), item(13641), item(13645),
		item(24482), item(13353), item(24478));

	@Test
	public void missingRuntimeTabLabelsFallBackToTheFiveJagexTabs()
	{
		assertEquals("Bosses", ClogIndex.resolveTabName(null, 0));
		assertEquals("Raids", ClogIndex.resolveTabName("", 1));
		assertEquals("Clues", ClogIndex.resolveTabName("null", 2));
		assertEquals("Minigames", ClogIndex.resolveTabName(null, 3));
		assertEquals("Other", ClogIndex.resolveTabName(null, 4));
		assertEquals("Custom", ClogIndex.resolveTabName("Custom", 0));
	}

	@Test
	public void onlyDeclaredAliasesCollapse()
	{
		ClogIndex index = new ClogIndex();
		index.publishForTest(Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.singletonMap(90_001, 90_000));
		assertEquals(90_000, index.canonicalItemId(90_001));
		assertEquals(10420, index.canonicalItemId(10420));
		assertEquals(10422, index.canonicalItemId(10422));
	}

	@Test
	public void openContainersAndFemaleOutfitAreOneSevenSlotTitheFarmLogEverywhere()
	{
		Map<String, List<Integer>> categories = Collections.singletonMap(
			"tithe_farm", TITHE_CATALOG);
		Map<String, List<ClogResult.ClogItem>> obtained = Collections.singletonMap(
			"tithe_farm", FEMALE_TITHE_OBTAINED);
		ClogResult result = new ClogResult("Based Batt", obtained, categories,
			Collections.emptyMap(), null, null);

		Map<String, List<String>> tabs = new LinkedHashMap<>();
		tabs.put("Minigames", Collections.singletonList("tithe_farm"));
		ClogIndex index = new ClogIndex();
		index.publishForTest(categories, Collections.emptyMap(), tabs, Collections.emptyMap());

		SkillClogSection tithe = SkillClogSection.forSkill(
			Skill.FARMING, result, null, result, index).stream()
			.filter(section -> "Tithe Farm".equals(section.heading()))
			.findFirst()
			.orElseThrow(AssertionError::new);
		assertEquals(7, tithe.itemIds().size());
		assertEquals(7, tithe.primary().obtainedCount());
		assertTrue(tithe.primary().obtainedIds().containsAll(
			Arrays.asList(13646, 13642, 13640, 13644)));

		TooltipDataBuilder builder = new TooltipDataBuilder(null);
		builder.setClogIndex(index);
		TooltipData tooltip = builder.buildTooltipData(
			"Tithe Farm", "tithe_farm", -1, result);
		assertEquals(7, tooltip.totalItems);
		assertEquals(7, tooltip.obtainedCount);

		assertEquals(7, ClogHelper.sumClogTotals(result, index::canonicalItemId)[0]);
		assertEquals(7, ClogHelper.sumClogTotals(result, index::canonicalItemId)[1]);
	}

	@Test
	public void runeLiteVariantsResolveToTheirSingleCollectionLogSlot()
	{
		Map<String, List<Integer>> categories = Collections.singletonMap("variants",
			Arrays.asList(8844, 13226, 13639, 20661, 25539, 25582, 26376,
				26850, 27226, 28140, 28583, 29996));
		ClogIndex index = new ClogIndex();
		index.publishForTest(categories, Collections.emptyMap());

		int[][] pairs = {
			{20449, 8844}, {24478, 13226}, {24482, 13639}, {24555, 20661},
			{25541, 25539}, {25584, 25582}, {26382, 26376}, {26858, 26850},
			{27235, 27226}, {28142, 28140}, {28585, 28583}, {29998, 29996}
		};
		for (int[] pair : pairs)
		{
			assertEquals(pair[1], index.canonicalItemId(pair[0]));
			assertEquals(Collections.singletonList("variants"),
				index.categoryKeysForItem(pair[0]));
		}
	}

	@Test
	public void exactCatalogSlotsAreNeverCollapsedByAReportedVariantFamily()
	{
		Map<String, List<Integer>> categories = Collections.singletonMap("variants",
			Arrays.asList(28140, 28142));
		ClogIndex index = new ClogIndex();
		index.publishForTest(categories, Collections.emptyMap());

		assertEquals(28140, index.canonicalItemId(28140));
		assertEquals(28142, index.canonicalItemId(28142));
	}

	@Test
	public void capturedVariantsAreCanonicalizedAndDeduplicatedBeforeStorage()
	{
		Map<String, List<Integer>> categories = Collections.singletonMap("variants",
			Collections.singletonList(13226));
		ClogIndex index = new ClogIndex();
		index.publishForTest(categories, Collections.emptyMap());

		List<ClogResult.ClogItem> normalized = index.canonicalizeItems(Arrays.asList(
			new ClogResult.ClogItem(24478, 1, "2026-01-01"),
			new ClogResult.ClogItem(13226, 3, null)));
		assertEquals(1, normalized.size());
		assertEquals(13226, normalized.get(0).getId());
		assertEquals(3, normalized.get(0).getCount());
		assertEquals("2026-01-01", normalized.get(0).getDate());
	}

	private static ClogResult.ClogItem item(int id)
	{
		return new ClogResult.ClogItem(id, 1, null);
	}
}
