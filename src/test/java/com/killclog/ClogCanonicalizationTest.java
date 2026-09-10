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
		item(13639), item(13353), item(13226));

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
	public void femaleFarmersOutfitIsOneSevenSlotTitheFarmLogEverywhere()
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

		List<ClogHelper.ClogGroupProgress> progress = ClogHelper.clogGroupProgress(
			result, tabs, index::canonicalItemId);
		assertEquals(1, progress.size());
		assertEquals(7, progress.get(0).obtained);
		assertEquals(7, progress.get(0).total);
		assertEquals(7, ClogHelper.sumClogTotals(result, index::canonicalItemId)[0]);
		assertEquals(7, ClogHelper.sumClogTotals(result, index::canonicalItemId)[1]);
	}

	private static ClogResult.ClogItem item(int id)
	{
		return new ClogResult.ClogItem(id, 1, null);
	}
}
