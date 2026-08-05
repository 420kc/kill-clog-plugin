package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogResultTest
{
	@Test
	public void testRuneProfileWinnerKeepsFallbackAccountMetadata()
	{
		ClogResult temple = result(
			"TempleName",
			1,
			10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-05-31 12:00:00",
			AccountType.GROUP_IRONMAN);

		Map<String, List<ClogResult.ClogItem>> rpItems = new HashMap<>();
		rpItems.put("boss", Arrays.asList(
			new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, null),
			new ClogResult.ClogItem(3, 1, null),
			new ClogResult.ClogItem(4, 1, null),
			new ClogResult.ClogItem(5, 1, null),
			new ClogResult.ClogItem(6, 1, null),
			new ClogResult.ClogItem(7, 1, null)));
		ClogResult rp = result("RuneProfileName", 7, 10, rpItems, null, null);
		rp.markItemResolved(7);

		ClogResult picked = ClogResult.pickFreshest(temple, rp);

		assertEquals("RuneProfileName", picked.getPlayerName());
		assertEquals(7, picked.getUniqueObtained());
		assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7),
			ids(picked.getObtainedItems().get("boss")));
		assertNull(picked.getLastChanged());
		assertEquals(AccountType.GROUP_IRONMAN, picked.getProviderAccountType());
		assertTrue(picked.isItemResolved(7));
	}

	@Test
	public void testRuneProfileWinnerKeepsOwnProviderAccountMetadata()
	{
		ClogResult temple = result(
			"TempleName",
			1,
			10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-05-31 12:00:00",
			AccountType.GROUP_IRONMAN);
		ClogResult rp = result(
			"RuneProfileName",
			7,
			10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null,
			AccountType.HARDCORE_GROUP_IRONMAN);

		ClogResult picked = ClogResult.pickFreshest(temple, rp);

		assertEquals("RuneProfileName", picked.getPlayerName());
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN, picked.getProviderAccountType());
	}

	@Test
	public void testTempleWinsNearTie()
	{
		ClogResult temple = result(
			"TempleName",
			5,
			10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-05-31 12:00:00",
			AccountType.IRONMAN);
		ClogResult rp = result(
			"RuneProfileName",
			9,
			10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null,
			null);

		ClogResult picked = ClogResult.pickFreshest(temple, rp);

		assertEquals("TempleName", picked.getPlayerName());
		assertEquals("2026-05-31 12:00:00", picked.getLastChanged());
	}

	// pickFullest: the first-party combine. Fullest leads, ties prefer the
	// player's own sync -- and a partial sync must never shrink the profile
	// below what providers prove.

	@Test
	public void testPartialSyncNeverShrinksBelowProviders()
	{
		// The killclog side carries a HIGHER varp counter (the account's true
		// unique count rides even a six-item partial payload) but fewer
		// actual items - the coverage race must count items, not varps.
		ClogResult provider = result("Provider", 1189, 1561,
			Collections.singletonMap("boss", Arrays.asList(
				new ClogResult.ClogItem(1, 1, null),
				new ClogResult.ClogItem(2, 1, null),
				new ClogResult.ClogItem(3, 1, null),
				new ClogResult.ClogItem(4, 1, null),
				new ClogResult.ClogItem(5, 1, null))),
			"2026-08-03 12:00:00", AccountType.REGULAR);
		ClogResult killclog = result("420 kc", 1500, 1561,
			Collections.singletonMap("abyssal_sire", Arrays.asList(
				new ClogResult.ClogItem(6, 1, null),
				new ClogResult.ClogItem(7, 1, null))),
			null, null);

		ClogResult picked = ClogResult.pickFullest(provider, killclog);

		assertEquals("Provider", picked.getPlayerName());
		assertEquals(1189, picked.getUniqueObtained());
		assertTrue("killclog returned data, provenance records it", picked.isFromKillclog());
	}

	@Test
	public void testFullSyncBeatsStaleProviderAndKeepsFallbackAccountType()
	{
		ClogResult provider = result("Provider", 1100, 1561,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-08-01 12:00:00", AccountType.IRONMAN);
		ClogResult killclog = result("420 kc", 1189, 1561,
			Collections.singletonMap("boss", Arrays.asList(
				new ClogResult.ClogItem(2, 1, null),
				new ClogResult.ClogItem(3, 1, null))),
			"2026-08-03 12:00:00", null);

		ClogResult picked = ClogResult.pickFullest(provider, killclog);

		assertEquals("420 kc", picked.getPlayerName());
		assertTrue(picked.isFromKillclog());
		assertEquals("winner without a type borrows the provider's",
			AccountType.IRONMAN, picked.getProviderAccountType());
	}

	@Test
	public void testTiePrefersFirstParty()
	{
		ClogResult provider = result("Provider", 1189, 1561,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-08-03 12:00:00", null);
		ClogResult killclog = result("420 kc", 1189, 1561,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null, null);

		ClogResult picked = ClogResult.pickFullest(provider, killclog);

		assertEquals("420 kc", picked.getPlayerName());
		assertTrue(picked.isFromKillclog());
	}

	@Test
	public void testFullestCombineNullLegs()
	{
		ClogResult provider = result("Provider", 5, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			null, null);
		ClogResult killclog = result("420 kc", 5, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null, null);

		ClogResult providerOnly = ClogResult.pickFullest(provider, null);
		assertEquals("Provider", providerOnly.getPlayerName());
		assertFalse(providerOnly.isFromKillclog());
		ClogResult kcOnly = ClogResult.pickFullest(null, killclog);
		assertEquals("420 kc", kcOnly.getPlayerName());
		assertTrue(kcOnly.isFromKillclog());
		assertNull(ClogResult.pickFullest(null, null));
	}

	@Test
	public void testDuplicateHeavyPartialCannotOutcountDistinctItems()
	{
		// Three distinct items fanned across three categories each (nine
		// entries) vs five distinct items. Neither carries game counters, so
		// coverage falls back to counting -- which must count DISTINCT ids.
		Map<String, List<ClogResult.ClogItem>> kcItems = new HashMap<>();
		for (String cat : Arrays.asList("a", "b", "c"))
		{
			kcItems.put(cat, Arrays.asList(
				new ClogResult.ClogItem(1, 1, null),
				new ClogResult.ClogItem(2, 1, null),
				new ClogResult.ClogItem(3, 1, null)));
		}
		ClogResult killclog = new ClogResult("420 kc", kcItems,
			new HashMap<>(), new HashMap<>(), null, null);

		Map<String, List<ClogResult.ClogItem>> providerItems = new HashMap<>();
		providerItems.put("boss", Arrays.asList(
			new ClogResult.ClogItem(10, 1, null),
			new ClogResult.ClogItem(11, 1, null),
			new ClogResult.ClogItem(12, 1, null),
			new ClogResult.ClogItem(13, 1, null),
			new ClogResult.ClogItem(14, 1, null)));
		ClogResult provider = new ClogResult("Provider", providerItems,
			new HashMap<>(), new HashMap<>(), null, null);

		ClogResult picked = ClogResult.pickFullest(provider, killclog);
		assertEquals("5 distinct beats 3 distinct, despite 9 entries",
			"Provider", picked.getPlayerName());
	}

	@Test
	public void testCombinesNeverMutateCachedInstances()
	{
		// Cached provider objects are shared by overlapping lookups whose
		// legs can resolve differently, so combines must return copies and
		// leave the cached instance untouched.
		ClogResult temple = result("TempleName", 9, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-08-03 12:00:00", null);
		ClogResult killclog = result("420 kc", 3, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null, null);

		// Combine A: killclog participates (equal coverage, first-party wins).
		ClogResult a = ClogResult.pickFullest(ClogResult.pickFreshest(temple, null), killclog);
		assertNotSame(temple, a);
		assertTrue(a.isFromKillclog());
		assertTrue(a.isFromTemple());

		// Combine B: provider-only race reusing the same cached instance.
		ClogResult b = ClogResult.pickFreshest(temple, null);
		assertNotSame(temple, b);
		assertFalse(b.isFromKillclog());

		// Combine C: killclog-only combine on the same cached instance.
		ClogResult c = ClogResult.pickFullest(null, temple);
		assertNotSame(temple, c);
		assertFalse(c.isFromTemple());
		assertTrue(c.isFromKillclog());

		// The cached instance itself never wore any combine's flags, and
		// each combine's copy kept its own answer.
		assertFalse(temple.isFromTemple());
		assertFalse(temple.isFromRuneProfile());
		assertFalse(temple.isFromKillclog());
		assertTrue(a.isFromKillclog());
		assertFalse(b.isFromKillclog());

		// Shared name-resolution cache is the one deliberate cross-copy seam:
		// copies of the same cached source share one concurrent name map.
		b.markItemResolved(99, "Shared Name");
		assertEquals("Shared Name", c.getItemName(99));
	}

	@Test
	public void testFullestCombineCarriesProviderProvenance()
	{
		ClogResult temple = result("TempleName", 3, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(1, 1, null))),
			"2026-08-03 12:00:00", null);
		ClogResult providerWinner = ClogResult.pickFreshest(temple, null);
		ClogResult killclog = result("420 kc", 9, 10,
			Collections.singletonMap("boss", Arrays.asList(new ClogResult.ClogItem(2, 1, null))),
			null, null);

		ClogResult picked = ClogResult.pickFullest(providerWinner, killclog);

		assertEquals("420 kc", picked.getPlayerName());
		assertTrue(picked.isFromKillclog());
		assertTrue("temple fed the race, provenance survives the combine", picked.isFromTemple());
		assertFalse(picked.isFromRuneProfile());
	}

	private static ClogResult result(
		String name,
		int obtainedCount,
		int totalCount,
		Map<String, List<ClogResult.ClogItem>> obtained,
		String lastChanged,
		AccountType accountType)
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("boss", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
		ClogResult result = new ClogResult(name, obtained, categories, new HashMap<>(), lastChanged, accountType);
		result.setUniqueObtained(obtainedCount);
		result.setUniqueTotal(totalCount);
		return result;
	}

	private static List<Integer> ids(List<ClogResult.ClogItem> items)
	{
		return Arrays.asList(
			items.get(0).getId(),
			items.get(1).getId(),
			items.get(2).getId(),
			items.get(3).getId(),
			items.get(4).getId(),
			items.get(5).getId(),
			items.get(6).getId());
	}
}
