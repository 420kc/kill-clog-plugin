package com.killclog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnsyncedClogCatalogTest
{
	@Test
	public void testCatalogCopyIsCoherentAndCachedPerGeneration()
	{
		// The catalog must serve categories and item names from one parse:
		// a reparse between two reads once paired pieces of two generations.
		ClogIndex index = new ClogIndex();
		index.publishForTest(
			Collections.singletonMap("vorkath", java.util.Arrays.asList(1, 2)),
			twoNames());

		UnsyncedClogCatalog catalog = new UnsyncedClogCatalog(null);
		catalog.setClogIndex(index);

		ClogResult first = catalog.result();
		assertNotNull(first);
		assertTrue(first.getCategoryItems().containsKey("vorkath"));
		assertEquals(2, first.getCategoryItems().get("vorkath").size());
		assertTrue(first.getObtainedItems().get("vorkath").isEmpty());

		// Same generation serves the cached build.
		assertSame(first, catalog.result());

		// A reparse moves the generation; the catalog rebuilds coherently.
		index.publishForTest(
			Collections.singletonMap("zulrah", Collections.singletonList(3)),
			Collections.singletonMap(3, "Tanzanite fang"));
		ClogResult rebuilt = catalog.result();
		assertNotNull(rebuilt);
		assertNotSame(first, rebuilt);
		assertTrue(rebuilt.getCategoryItems().containsKey("zulrah"));
		assertFalse(rebuilt.getCategoryItems().containsKey("vorkath"));
	}

	@Test
	public void testAtomicCopyCarriesBothHalves()
	{
		ClogIndex index = new ClogIndex();
		Map<String, List<Integer>> cats = new HashMap<>();
		cats.put("vorkath", java.util.Arrays.asList(1, 2));
		index.publishForTest(cats, twoNames());

		ClogIndex.CatalogCopy copy = index.copyCatalog();
		assertNotNull(copy);
		assertEquals(2, copy.categoryItems.get("vorkath").size());
		assertEquals("Dragonbone necklace", copy.itemNames.get(1));
		assertSame(copy.generation, index.generation());

		index.clear();
		assertNull(index.generation());
		assertNull(index.copyCatalog());
	}

	private static Map<Integer, String> twoNames()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(1, "Dragonbone necklace");
		names.put(2, "Skeletal visage");
		return names;
	}
}
