package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClogPagesTest
{
	@Test
	public void aPageOpensABossPopupOnlyWhenThePageIsThatBosss()
	{
		assertEquals(HiscoreSkill.ZULRAH, ClogPages.pageBoss("zulrah", "Zulrah"));
		// A page in its own name: Moons of Peril is the Lunar Chests.
		assertEquals(HiscoreSkill.LUNAR_CHESTS, ClogPages.pageBoss("moons_of_peril", "Moons of Peril"));
		// A page shared by a boss's modes opens the one it is named for.
		assertEquals(HiscoreSkill.CHAMBERS_OF_XERIC, ClogPages.pageBoss("chambers_of_xeric", "Chambers of Xeric"));
		assertEquals(HiscoreSkill.NIGHTMARE, ClogPages.pageBoss("the_nightmare", "The Nightmare"));
		// Pages shared by bosses, and pages no boss holds, have their own popups.
		assertNull(ClogPages.pageBoss("dagannoth_kings", "Dagannoth Kings"));
		assertNull(ClogPages.pageBoss("callisto_and_artio", "Callisto and Artio"));
		assertNull(ClogPages.pageBoss("barbarian_assault", "Barbarian Assault"));
	}

	@Test
	public void everyPanelBossHasAPageInTheGamesLog()
	{
		JsonObject seed = new Gson().fromJson(new InputStreamReader(
			ClogPagesTest.class.getResourceAsStream("clog-structure-seed.json"), StandardCharsets.UTF_8), JsonObject.class);
		Set<String> pages = new HashSet<>();
		for (JsonElement tab : seed.getAsJsonArray("tabs"))
		{
			for (JsonElement page : tab.getAsJsonObject().getAsJsonArray("pages"))
			{
				pages.add(page.getAsJsonObject().get("key").getAsString());
			}
		}
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			if (boss == HiscoreSkill.MIMIC)
			{
				// Its one slot, the 3rd age ring, sits on the clue pages; the plugin keeps a page of its own for it.
				continue;
			}
			String key = ClogService.bossToCategory(PanelData.NAME_OVERRIDES.getOrDefault(boss.getName(), boss.getName()));
			assertTrue(boss.getName() + " has no page " + key, pages.contains(key));
		}
	}
}
