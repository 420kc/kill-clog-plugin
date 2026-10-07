package com.killclog;

import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Pins the bundled clog category overrides to the exact table the plugin
 * shipped with in 2.3.0, when it lived in ClogService itself.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class ClogCategoryOverridesCatalogTest
{
	@Test
	public void overridesMatchGoldenTable()
	{
		Map<String, String> overrides = ClogService.bossCategoryOverrides();
		assertEquals(24, overrides.size());
		assertEquals("callisto_and_artio", overrides.get("Artio"));
		assertEquals("callisto_and_artio", overrides.get("Callisto"));
		assertEquals("vetion_and_calvarion", overrides.get("Cal'varion"));
		assertEquals("vetion_and_calvarion", overrides.get("Vet'ion"));
		assertEquals("venenatis_and_spindel", overrides.get("Venenatis"));
		assertEquals("venenatis_and_spindel", overrides.get("Spindel"));
		assertEquals("dagannoth_kings", overrides.get("Dagannoth Prime"));
		assertEquals("dagannoth_kings", overrides.get("Dagannoth Rex"));
		assertEquals("dagannoth_kings", overrides.get("Dagannoth Supreme"));
		assertEquals("kree_arra", overrides.get("Kree'Arra"));
		assertEquals("kril_tsutsaroth", overrides.get("K'ril Tsutsaroth"));
		assertEquals(PanelData.COX_CATEGORY, overrides.get(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE.getName()));
		assertEquals(PanelData.TOB_CATEGORY, overrides.get(HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE.getName()));
		assertEquals(PanelData.TOA_CATEGORY, overrides.get(HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT.getName()));
		assertEquals("the_fight_caves", overrides.get("TzTok-Jad"));
		assertEquals("the_inferno", overrides.get("TzKal-Zuk"));
		assertEquals("fortis_colosseum", overrides.get("Sol Heredit"));
		assertEquals("the_mad_angel", overrides.get("Mad Angel"));
		assertEquals("the_nightmare", overrides.get("Nightmare"));
		assertEquals("the_nightmare", overrides.get("Phosani's Nightmare"));
		assertEquals("the_gauntlet", overrides.get("The Corrupted Gauntlet"));
		assertEquals("hueycoatl", overrides.get("The Hueycoatl"));
		assertEquals("royal_titans", overrides.get("The Royal Titans"));
		assertEquals("moons_of_peril", overrides.get("Lunar Chests"));

		// The map is case-insensitive by contract.
		assertEquals("the_mad_angel", overrides.get("MAD ANGEL"));
	}
}
