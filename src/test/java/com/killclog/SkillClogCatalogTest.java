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
			"items|Stab Weapons|11902,30957,22963,21009,1249,6523,13265,4726,11824,22966,22324,26219",
			"items|Slash Weapons|11037,29084,29574,27021,7158,6739,13652,6525,20727,4151,4207,11838,12004,26370,11810,11818,11820,11822,11812,34027,11814,27684,27687,27681,11816,29580,25859",
			"items|Crush Weapons|25641,20756,29889,22542,23528,27667,4755,24417,34717",
			"items|Equipment|22983"),
			TestViews.specSignature(Skill.ATTACK));
		assertEquals(Arrays.asList(
			"items|Equipment|19529,29799,31109,33634",
			"items|Pest Control|8839,8840,8842,8841,11663,11665,11664,13072,13073"),
			TestViews.specSignature(Skill.HITPOINTS));
		assertEquals(Arrays.asList(
			"items|Armour|10551,10555,10548,10547,10550,10549,11335,3140,22103,21298,21301,21304,11832,11834,23956,4716,4720,4722,4724,4728,4730,24268,4745,4749,4751,4753,4757,4759,22326,22327,22328,12927,30750,30765,30756,30753,26376,26378,26380",
			"items|Shields|22100,2366,11931,11932,11933,11928,11929,11930,6524,4207,21015,11286,34719",
			"items|Equipment|10553,4131,10552,21643,21739,11840,11836,31088",
			"items|Defenders|8844,8845,8846,8847,8848,8849,8850,12954,22477"),
			TestViews.specSignature(Skill.DEFENCE));
		assertEquals(Arrays.asList(
			"items|Slash Weapons|21646,4718,28319,28321,28325,28323,22486",
			"items|Crush Weapons|21742,4153,13576,6528,13276,13275,13274,4747,28997,21003,33631",
			"items|Armour|10589,10564,6809,21736,24419,24420,24421,29028,29022,29025,13231,31088"),
			TestViews.specSignature(Skill.STRENGTH));
		assertEquals(Arrays.asList(
			"items|Pets|13321,23760",
			"items|Tools|11920",
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
			"items|Light Weapons|22804,6522,20849,31572,28919,12922",
			"items|Standard Weapons|6724,22547,11235,4207,27670,29000,29580,25859,27614,20997",
			"items|Heavy Weapons|21918,19586,11785,21012,4734,19592,19601,19589,19610,34681,26372",
			"items|Armour|11826,11828,11830,4732,4736,4738,29010,29004,29007,27226,27229,27232,26235",
			"items|Equipment|22006,28947,13229,21000,31088"),
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
			"items|Armour|28933,28936,28939",
			"items|Equipment|31081,12829,12833,22960,12827,22111,25346,12823,12819,25985"),
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
			"items|Staves & Wands|20736,20730,6562,11998,11787,6908,6910,6914,6912,22552,6526,28583,30070,27673,4710,27627,24517,24511,24422,24514,28268,28270,28272,28274,28988,11791,11905,11908,29580,12932,21043,30759,22481,31115,27277,34727",
			"items|Robes|20595,20517,20520,4099,23047,4109,4101,23050,4111,4103,23053,4113,6918,6916,6924,6922,6920,4708,4712,4714,24288,24291,24294,21018,21021,21024,29019,29013,29016,26241,26243,26245",
			"items|Equipment|25474,30066,20716,25576,34404,34416,34422,34410,25476,6889,21637,12002,13227,31088",
			"items|Spell Upgrades|34671,34673,34675,34677"),
			TestViews.specSignature(Skill.MAGIC));
		assertEquals(Arrays.asList(
			"category|Vale Totems|vale_totems"),
			TestViews.specSignature(Skill.FLETCHING));
		assertEquals(Arrays.asList(
			"items|Pets|13322",
			"items|Tools|6739,23953",
			"items|Evil Chicken Outfit|20439,20436,20442,20433",
			"category|Forestry|forestry"),
			TestViews.specSignature(Skill.WOODCUTTING));
		assertEquals(Arrays.asList(
			"items|Pets|20665,26901",
			"category|Guardians of the Rift|" + PanelData.GOTR_CATEGORY),
			TestViews.specSignature(Skill.RUNECRAFT));
		assertEquals(Arrays.asList(
			"items|Superiors|20724,21270,20736,20730",
			"rest||" + PanelData.SLAYER_CATEGORY),
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
