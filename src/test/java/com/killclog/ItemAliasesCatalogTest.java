package com.killclog;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * item-aliases.tsv: every id the game or a provider files under another, the id the log counts it as, and the order
 * other ids lend their names when the counted item has none (as SkillClogSection.aliasName had them written out).
 */
public class ItemAliasesCatalogTest
{
	@Test
	public void everyAliasCountsAsItsItem()
	{
		Map<Integer, Integer> golden = new LinkedHashMap<>();
		int[][] pairs = {{764, 25627}, {12019, 25627}, {24480, 25627}, {766, 25628}, {12020, 25628},
			{24481, 25628}, {29472, 12013}, {29474, 12014}, {29476, 12015}, {29478, 12016}, {12854, 25630},
			{24882, 25629}, {29988, 29992}, {29990, 29992}, {13641, 13640}, {13643, 13642}, {13645, 13644},
			{13647, 13646}};
		for (int[] pair : pairs)
		{
			golden.put(pair[0], pair[1]);
		}
		Map<Integer, Integer> catalog = new LinkedHashMap<>();
		for (String[] row : CatalogTsv.rows(ClogIndex.class, "item-aliases.tsv", 3))
		{
			catalog.put(Integer.parseInt(row[0]), Integer.parseInt(row[1]));
		}
		assertEquals(golden, catalog);
		golden.forEach((alias, item) -> assertEquals(alias + "", (int) item, ClogIndex.fallbackCanonicalItemId(alias)));
		assertEquals(4151, ClogIndex.fallbackCanonicalItemId(4151));
	}

	@Test
	public void namesStandInInTheOrderTheyAlwaysHave()
	{
		assertEquals(Arrays.asList(12019, 24480, 764), ClogIndex.nameAliases(25627));
		assertEquals(Arrays.asList(12020, 24481, 766), ClogIndex.nameAliases(25628));
		assertEquals(Arrays.asList(29472), ClogIndex.nameAliases(12013));
		assertEquals(Arrays.asList(29474), ClogIndex.nameAliases(12014));
		assertEquals(Arrays.asList(29476), ClogIndex.nameAliases(12015));
		assertEquals(Arrays.asList(29478), ClogIndex.nameAliases(12016));
		assertEquals(Arrays.asList(12854), ClogIndex.nameAliases(25630));
		assertEquals(Arrays.asList(24882), ClogIndex.nameAliases(25629));
		assertEquals(Arrays.asList(29990, 29988), ClogIndex.nameAliases(29992));
		// The Abyssal Sire's unsired forms count as one item but never lend a name.
		List<Integer> none = Collections.emptyList();
		assertEquals(none, ClogIndex.nameAliases(13640));
		assertEquals(none, ClogIndex.nameAliases(4151));
	}
}
