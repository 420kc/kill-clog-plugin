package com.killclog;

import java.util.Arrays;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Pins the bundled Skill Clog taxonomy to the exact table the plugin shipped
 * with in 2.3.0, when it lived in SkillClogSection itself. Golden rows are
 * verbatim; any drift in the TSV or its loader fails here.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class SkillClogCatalogTest
{
	@Test
	public void taxonomyMatchesGoldenTable()
	{
		assertEquals(Arrays.asList(
			"items|Weapon Unlocks|4151,13265,13274,13275,13276,11824,22324,26219,25859,24417,22486,34027,34717,22966,29580,28319,28321,28323,28325,13576,21003,13652,29574,11810,11812,11814,11816,26370,27684,27681,27687",
			"category|Barbarian Assault|barbarian_assault"),
			TestViews.specSignature(Skill.ATTACK));
		assertEquals(Arrays.asList(
			"items|Equipment Unlocks|24422,19529,29799,31109,33634",
			"category|Pest Control|pest_control"),
			TestViews.specSignature(Skill.HITPOINTS));
		assertEquals(Arrays.asList(
			"items|Equipment Unlocks|10551,4716,4720,4722,4724,4728,4730,4745,4749,4751,4753,4757,4759,29028,29022,29025,11832,11834,11836,24419,24420,24421,22326,22327,22328,30750,30753,30756,26376,26378,26380,34719",
			"items|Defenders|8844,8845,8846,8847,8848,8849,8850,12954,22477"),
			TestViews.specSignature(Skill.DEFENCE));
		assertEquals(Arrays.asList(
			"items|Weapon Unlocks|4153,21646,21742,29000,13576,6528,4718,4747,28997,21003,33631,22486,34027,34717"),
			TestViews.specSignature(Skill.STRENGTH));
		assertEquals(Arrays.asList(
			"items|Pets|13321,23760",
			"category|Camdozaal|camdozaal",
			"items|Mining Guild|21343,21345,21392",
			"category|Motherlode Mine|motherlode_mine",
			"category|Shooting Stars|shooting_stars",
			"category|Volcanic Mine|volcanic_mine",
			"category|Zalcano|zalcano"),
			TestViews.specSignature(Skill.MINING));
		assertEquals(Arrays.asList(
			"items|Pets|20659",
			"category|Brimhaven Agility Arena|brimhaven_agility_arena",
			"category|Colossal Wyrm Agility Course|colossal_wyrm_agility",
			"category|Hallowed Sepulchre|hallowed_sepulchre",
			"category|Monkey Backpacks|monkey_backpacks",
			"category|Rooftop Agility|rooftop_agility",
			"items|Underwater|21649"),
			TestViews.specSignature(Skill.AGILITY));
		assertEquals(Arrays.asList(
			"category|Giants' Foundry|giants_foundry"),
			TestViews.specSignature(Skill.SMITHING));
		assertEquals(Arrays.asList(
			"category|Mastering Mixology|mastering_mixology"),
			TestViews.specSignature(Skill.HERBLORE));
		assertEquals(Arrays.asList(
			"items|Pets|13320,25602",
			"category|Aerial Fishing|aerial_fishing",
			"items|Big Fish|7991,7993,7989",
			"items|Deep Sea Trawling|31408,31412,31416,31420,31424,31428",
			"category|Fishing Trawler|fishing_trawler",
			"items|Lantern Harpooning|31572",
			"category|Tempoross|tempoross"),
			TestViews.specSignature(Skill.FISHING));
		assertEquals(Arrays.asList(
			"items|Equipment Unlocks|4734,21918,11785,26372,21012,31572,12922,34681,34669,23956,25859,20997,29580,27614,29000,4732,4736,4738,29010,29004,29007,11826,11828,11830,27226,27229,27232",
			"category|Chompy Bird Hunting|chompy_bird_hunting"),
			TestViews.specSignature(Skill.RANGED));
		assertEquals(Arrays.asList(
			"items|Pets|20663",
			"items|Pickpocketing|23959,24777",
			"items|Pyramid Plunder|26945",
			"category|Rogues' Den|rogues_den",
			"items|Underwater|21649"),
			TestViews.specSignature(Skill.THIEVING));
		assertEquals(Arrays.asList(
			"category|Gnome Restaurant|gnome_restaurant",
			"category|Trouble Brewing|trouble_brewing"),
			TestViews.specSignature(Skill.COOKING));
		assertEquals(Arrays.asList(
			"items|Prayer Unlocks|21047,21034,21079,30626,30627,34679",
			"category|Shades of Mort'ton|shades_of_mortton"),
			TestViews.specSignature(Skill.PRAYER));
		assertEquals(Arrays.asList(
			"items|Rarities|34024,19707"),
			TestViews.specSignature(Skill.CRAFTING));
		assertEquals(Arrays.asList(
			"items|Pets|20693",
			"category|Shades of Mort'ton|shades_of_mortton",
			"category|Wintertodt|wintertodt"),
			TestViews.specSignature(Skill.FIREMAKING));
		assertEquals(Arrays.asList(
			"items|Equipment Unlocks|28583,11905,12932,22481,31115,27277,34727,27627,28268,28270,28272,28274,21043,24422,24511,24514,24517,30070,29580,30759,34404,34410,34416,34422,4708,4712,4714,29019,29013,29016,26241,26243,26245,21018,21021,21024",
			"items|Spell Upgrades|34671,34673,34675,34677",
			"category|Magic Training Arena|magic_training_arena"),
			TestViews.specSignature(Skill.MAGIC));
		assertEquals(Arrays.asList(
			"category|Vale Totems|vale_totems"),
			TestViews.specSignature(Skill.FLETCHING));
		assertEquals(Arrays.asList(
			"items|Pets|13322",
			"items|Evil Chicken Outfit|20439,20436,20442,20433",
			"category|Forestry|forestry"),
			TestViews.specSignature(Skill.WOODCUTTING));
		assertEquals(Arrays.asList(
			"items|Pets|20665,26901",
			"category|Guardians of the Rift|" + PanelData.GOTR_CATEGORY),
			TestViews.specSignature(Skill.RUNECRAFT));
		assertEquals(Arrays.asList(
			"category||" + PanelData.SLAYER_CATEGORY),
			TestViews.specSignature(Skill.SLAYER));
		assertEquals(Arrays.asList(
			"items|Pets|20661",
			"category|Hespori|hespori",
			"category|Tithe Farm|tithe_farm"),
			TestViews.specSignature(Skill.FARMING));
		assertEquals(Arrays.asList(
			"category|Mahogany Homes|mahogany_homes"),
			TestViews.specSignature(Skill.CONSTRUCTION));
		assertEquals(Arrays.asList(
			"items|Pets|13324,21509,28962,34040,13071",
			"category|Aerial Fishing|aerial_fishing",
			"category|Chompy Bird Hunting|chompy_bird_hunting",
			"items|Crystal Impling|23943",
			"category|Hunter Guild|hunter_guild"),
			TestViews.specSignature(Skill.HUNTER));
		assertEquals(Arrays.asList(
			"items|Pets|31283",
			"category|Barracuda Trials|barracuda_trials",
			"category|Boat Paints|boat_paints",
			"items|Deep Sea Trawling|31408,31412,31416,31420,31424,31428",
			"category|Lost Schematics|lost_schematics",
			"category|Ocean Encounters|ocean_encounters",
			"category|Sailing Miscellaneous|sailing_miscellaneous",
			"category|Sea Treasures|sea_treasures"),
			TestViews.specSignature(Skill.SAILING));

		int curated = 0;
		for (Skill skill : Skill.values())
		{
			if (!TestViews.specSignature(skill).isEmpty())
			{
				curated++;
			}
		}
		assertEquals(24, curated);
	}
}
