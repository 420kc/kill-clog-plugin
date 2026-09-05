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
			"category|Barbarian Assault|barbarian_assault"),
			SkillClogSection.specSignature(Skill.ATTACK));
		assertEquals(Arrays.asList(
			"items|Equipment Unlocks|24422,19529,29799,31109,33634",
			"category|Pest Control|pest_control"),
			SkillClogSection.specSignature(Skill.HITPOINTS));
		assertEquals(Arrays.asList(
			"category|Defenders|cyclopes"),
			SkillClogSection.specSignature(Skill.DEFENCE));
		assertEquals(Arrays.asList(
			"items|Weapon Unlocks|4153,21646,21742,29000,13576,6528,4718,4747,28997,21003,33631,22486"),
			SkillClogSection.specSignature(Skill.STRENGTH));
		assertEquals(Arrays.asList(
			"items|Pets|13321",
			"category|Camdozaal|camdozaal",
			"items|Mining Guild|21343,21345,21392",
			"category|Motherlode Mine|motherlode_mine",
			"category|Shooting Stars|shooting_stars",
			"category|Volcanic Mine|volcanic_mine",
			"category|Zalcano|zalcano"),
			SkillClogSection.specSignature(Skill.MINING));
		assertEquals(Arrays.asList(
			"items|Pets|20659",
			"category|Brimhaven Agility Arena|brimhaven_agility_arena",
			"category|Colossal Wyrm Agility Course|colossal_wyrm_agility",
			"category|Hallowed Sepulchre|hallowed_sepulchre",
			"category|Monkey Backpacks|monkey_backpacks",
			"category|Rooftop Agility|rooftop_agility",
			"items|Underwater|21649"),
			SkillClogSection.specSignature(Skill.AGILITY));
		assertEquals(Arrays.asList(
			"category|Giants' Foundry|giants_foundry"),
			SkillClogSection.specSignature(Skill.SMITHING));
		assertEquals(Arrays.asList(
			"category|Mastering Mixology|mastering_mixology"),
			SkillClogSection.specSignature(Skill.HERBLORE));
		assertEquals(Arrays.asList(
			"items|Pets|13320",
			"category|Aerial Fishing|aerial_fishing",
			"items|Big Fish|7991,7993,7989",
			"items|Deep Sea Trawling|31408,31412,31416,31420,31424,31428",
			"category|Fishing Trawler|fishing_trawler",
			"items|Lantern Harpooning|31572",
			"category|Tempoross|tempoross"),
			SkillClogSection.specSignature(Skill.FISHING));
		assertEquals(Arrays.asList(
			"category|Chompy Bird Hunting|chompy_bird_hunting"),
			SkillClogSection.specSignature(Skill.RANGED));
		assertEquals(Arrays.asList(
			"items|Pets|20663",
			"items|Pickpocketing|23959,24777",
			"items|Pyramid Plunder|26945",
			"category|Rogues' Den|rogues_den",
			"items|Underwater|21649"),
			SkillClogSection.specSignature(Skill.THIEVING));
		assertEquals(Arrays.asList(
			"category|Gnome Restaurant|gnome_restaurant",
			"category|Trouble Brewing|trouble_brewing"),
			SkillClogSection.specSignature(Skill.COOKING));
		assertEquals(Arrays.asList(
			"category|Shades of Mort'ton|shades_of_mortton"),
			SkillClogSection.specSignature(Skill.PRAYER));
		assertEquals(Arrays.asList(
			"items|Rarities|34024,19707"),
			SkillClogSection.specSignature(Skill.CRAFTING));
		assertEquals(Arrays.asList(
			"category|Shades of Mort'ton|shades_of_mortton",
			"category|Wintertodt|wintertodt"),
			SkillClogSection.specSignature(Skill.FIREMAKING));
		assertEquals(Arrays.asList(
			"category|Magic Training Arena|magic_training_arena"),
			SkillClogSection.specSignature(Skill.MAGIC));
		assertEquals(Arrays.asList(
			"category|Vale Totems|vale_totems"),
			SkillClogSection.specSignature(Skill.FLETCHING));
		assertEquals(Arrays.asList(
			"items|Pets|13322",
			"items|Evil Chicken Outfit|20439,20436,20442,20433",
			"category|Forestry|forestry"),
			SkillClogSection.specSignature(Skill.WOODCUTTING));
		assertEquals(Arrays.asList(
			"items|Pets|20665",
			"category|Guardians of the Rift|" + PanelData.GOTR_CATEGORY),
			SkillClogSection.specSignature(Skill.RUNECRAFT));
		assertEquals(Arrays.asList(
			"category||" + PanelData.SLAYER_CATEGORY),
			SkillClogSection.specSignature(Skill.SLAYER));
		assertEquals(Arrays.asList(
			"items|Pets|20661",
			"category|Hespori|hespori",
			"category|Tithe Farm|tithe_farm"),
			SkillClogSection.specSignature(Skill.FARMING));
		assertEquals(Arrays.asList(
			"category|Mahogany Homes|mahogany_homes"),
			SkillClogSection.specSignature(Skill.CONSTRUCTION));
		assertEquals(Arrays.asList(
			"items|Pets|13324,21509,34040",
			"category|Aerial Fishing|aerial_fishing",
			"items|Crystal Impling|23943",
			"category|Hunter Guild|hunter_guild"),
			SkillClogSection.specSignature(Skill.HUNTER));
		assertEquals(Arrays.asList(
			"items|Pets|31283",
			"category|Barracuda Trials|barracuda_trials",
			"category|Boat Paints|boat_paints",
			"items|Deep Sea Trawling|31408,31412,31416,31420,31424,31428",
			"category|Lost Schematics|lost_schematics",
			"category|Ocean Encounters|ocean_encounters",
			"category|Sailing Miscellaneous|sailing_miscellaneous",
			"category|Sea Treasures|sea_treasures"),
			SkillClogSection.specSignature(Skill.SAILING));

		int curated = 0;
		for (Skill skill : Skill.values())
		{
			if (!SkillClogSection.specSignature(skill).isEmpty())
			{
				curated++;
			}
		}
		assertEquals(24, curated);
	}
}
