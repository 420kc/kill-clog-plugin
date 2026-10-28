package com.killclog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.runelite.client.game.ItemVariationMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClogItemCanonicalizerTest
{
	// The abyssal whip: one catalog slot whose other forms RuneLite's variation map knows.
	private static final int WHIP = 4151;

	@Test
	public void aCatalogItemIsItsOwnSlotAndAnotherFormFindsIt()
	{
		ClogItemCanonicalizer canon = new ClogItemCanonicalizer(Map.of("whip", List.of(WHIP)), Map.of());
		assertEquals(WHIP, canon.canonicalItemId(WHIP));
		assertEquals(WHIP, canon.canonicalItemId(otherForm(WHIP)));
		assertEquals("an item outside the catalog stays itself", 995, canon.canonicalItemId(995));
	}

	@Test
	public void aDeclaredAliasWinsAndAFormInTheCatalogKeepsItsOwnSlot()
	{
		int variant = otherForm(WHIP);
		ClogItemCanonicalizer aliased = new ClogItemCanonicalizer(Map.of("whip", List.of(WHIP)), Map.of(123456, WHIP));
		assertEquals(WHIP, aliased.canonicalItemId(123456));
		assertEquals(1, aliased.declaredAliasCount());
		ClogItemCanonicalizer both = new ClogItemCanonicalizer(Map.of("whip", List.of(WHIP, variant)), Map.of());
		assertEquals("a form the catalog lists is a slot of its own", variant, both.canonicalItemId(variant));
	}

	@Test
	public void twoFormsOfOneSlotMergeKeepingTheHigherCountAndTheDate()
	{
		int variant = otherForm(WHIP);
		ClogItemCanonicalizer canon = new ClogItemCanonicalizer(Map.of("whip", List.of(WHIP)), Map.of());
		List<ClogResult.ClogItem> merged = canon.canonicalizeItems(List.of(
			new ClogResult.ClogItem(WHIP, 1, "2026-10-01 12:00:00", 0, null),
			new ClogResult.ClogItem(variant, 3, null, 0, null)));
		assertEquals(1, merged.size());
		assertEquals(WHIP, merged.get(0).getId());
		assertEquals(3, merged.get(0).getCount());
		assertEquals("2026-10-01 12:00:00", merged.get(0).getDate());
		assertTrue(canon.canonicalizeItems(null).isEmpty());
	}

	private static int otherForm(int itemId)
	{
		Collection<Integer> forms = ItemVariationMapping.getVariations(itemId);
		return forms.stream().filter(id -> id != itemId).findFirst()
			.orElseThrow(() -> new AssertionError("RuneLite's variation map has no other form of " + itemId));
	}
}
