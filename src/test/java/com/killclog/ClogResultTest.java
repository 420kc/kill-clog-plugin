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

		assertSame(rp, picked);
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

		assertSame(temple, picked);
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
