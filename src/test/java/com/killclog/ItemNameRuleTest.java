package com.killclog;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Every card names an item by one rule: the log's name, or else the name of one of the item's alias forms. */
public class ItemNameRuleTest
{
	@Test
	public void anItemTheLogHoldsOnlyByAnAliasIsNamedByItsFirstAliasInTheirOrder()
	{
		// 25627 is counted for 12019, 24480 and 764, which stand in for its name in that order.
		ClogResult aliasesOnly = names(Map.of(764, "third", 24480, "second", 12019, "first"));
		assertEquals("first", ClogIndex.itemNameOrAlias(null, aliasesOnly, 25627));
		assertEquals("second", ClogIndex.itemNameOrAlias(null, names(Map.of(764, "third", 24480, "second")), 25627));

		// The item's own name always wins, and an item with neither has none.
		assertEquals("own", ClogIndex.itemNameOrAlias(null, names(Map.of(25627, "own", 12019, "first")), 25627));
		assertNull(ClogIndex.itemNameOrAlias(null, names(Map.of()), 25627));
	}

	private static ClogResult names(Map<Integer, String> itemNames)
	{
		return new ClogResult("Tester", Map.of(), Map.of(), itemNames, null, null);
	}
}
