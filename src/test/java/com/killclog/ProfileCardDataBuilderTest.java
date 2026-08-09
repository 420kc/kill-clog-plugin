package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileCardDataBuilderTest
{
	@Test
	public void allowsOnlyTheLoggedInPlayersCard()
	{
		assertTrue(ProfileCardDataBuilder.isSelfPlayer("420 kc", "420 KC"));
		assertTrue(ProfileCardDataBuilder.isSelfPlayer(" 420 kc ", "420 kc"));
		assertFalse(ProfileCardDataBuilder.isSelfPlayer("Other player", "420 kc"));
		assertFalse(ProfileCardDataBuilder.isSelfPlayer("420 kc", null));
	}

	@Test
	public void makesReadablePlayerProfilePath()
	{
		assertEquals("killclog.com/p/420-kc", ProfileCardDataBuilder.profileUrl("420 kc"));
		assertEquals("log-chaser", ProfileCardShare.sanitize(" Log Chaser! "));
	}

	@Test
	public void localCardNeedsSelfAndLoadedDataButNotWebPublication()
	{
		assertTrue(ProfileCardDataBuilder.canBuild("420 kc", "420 KC", true, true));
		assertFalse(ProfileCardDataBuilder.canBuild("Other player", "420 kc", true, true));
		assertFalse(ProfileCardDataBuilder.canBuild("420 kc", "420 kc", false, true));
		assertFalse(ProfileCardDataBuilder.canBuild("420 kc", "420 kc", true, false));
	}

	@Test
	public void rareShelfKeepsSpecialsThirdAgeAndMegaRaresVisible()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("mixed", Arrays.asList(
			new ClogResult.ClogItem(PanelData.STALE_BAGUETTE_ITEM_ID, 1, null),
			new ClogResult.ClogItem(PanelData.HELMET_OF_THE_MOON_ITEM_ID, 1, null),
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEMS[0], 1, null),
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEMS[1], 1, null),
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEMS[2], 1, null),
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEMS[3], 1, null),
			new ClogResult.ClogItem(PanelData.TWISTED_BOW_ITEM_ID, 1, null),
			new ClogResult.ClogItem(PanelData.SCYTHE_ITEM_ID, 1, null),
			new ClogResult.ClogItem(PanelData.SHADOW_ITEM_ID, 1, null)));
		ClogResult clog = new ClogResult("420 kc", obtained,
			Collections.emptyMap(), Collections.emptyMap(), null, null);

		List<ClogResult.ClogItem> rare = ProfileCardDataBuilder.rareTrophies(clog);

		assertEquals(6, rare.size());
		assertEquals(PanelData.STALE_BAGUETTE_ITEM_ID, rare.get(0).getId());
		assertEquals(PanelData.HELMET_OF_THE_MOON_ITEM_ID, rare.get(1).getId());
		assertTrue(rare.stream().anyMatch(item -> item.getId() == PanelData.THIRD_AGE_ITEMS[0]));
		assertTrue(rare.stream().anyMatch(item -> item.getId() == PanelData.TWISTED_BOW_ITEM_ID));
		assertTrue(rare.stream().anyMatch(item -> item.getId() == PanelData.SCYTHE_ITEM_ID));
		assertTrue(rare.stream().anyMatch(item -> item.getId() == PanelData.SHADOW_ITEM_ID));
	}
}
