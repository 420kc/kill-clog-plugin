package com.killclog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Skill;

/**
 * A presentation-only view of collection-log slots associated with a skill.
 * The view never writes synthetic categories into {@link ClogResult}, so these
 * repeated slots cannot affect the plugin's canonical collection-log totals.
 */
final class SkillClogSection
{
	// Narrow sections are fixed by item id so they can find obtained items in
	// any provider category. Their declared order is their presentation order.
	private static final int[] MINING_PETS = {13321};
	private static final int[] MINING_GUILD_ITEMS = {21343, 21345, 21392};
	private static final int[] HITPOINTS_EQUIPMENT_ITEMS = {
		24422, 19529, 29799, 31109, 33634
	};
	// Direct Collection Log weapon entries in Strength level-up order.
	private static final int[] STRENGTH_WEAPON_ITEMS = {
		4153, 21646, 21742, 29000,
		13576, 6528,
		4718, 4747,
		28997, 21003,
		33631, 22486
	};
	private static final int[] AGILITY_PETS = {20659};
	private static final int[] UNDERWATER_ITEMS = {21649};
	private static final int[] FISHING_PETS = {13320};
	private static final int[] BIG_FISH_ITEMS = {7991, 7993, 7989};
	private static final int[] DEEP_SEA_TRAWLING_ITEMS = {
		31408, 31412, 31416, 31420, 31424, 31428
	};
	private static final int[] LANTERN_HARPOONING_ITEMS = {31572};
	private static final int[] THIEVING_PETS = {20663};
	private static final int[] PICKPOCKETING_ITEMS = {23959, 24777};
	private static final int[] PYRAMID_PLUNDER_ITEMS = {26945};
	private static final int[] CRAFTING_RARITIES = {34024, 19707};
	private static final int[] WOODCUTTING_PETS = {13322};
	private static final int[] EVIL_CHICKEN_ITEMS = {20439, 20436, 20442, 20433};
	private static final int[] RUNECRAFT_PETS = {20665};
	private static final int[] FARMING_PETS = {20661};
	private static final int[] HUNTER_PETS = {13324, 21509, 34040};
	private static final int[] CRYSTAL_IMPLING_ITEMS = {23943};
	private static final int[] SAILING_PETS = {31283};
	private static final int LEGACY_FLAMTAER_BAG = 12854;
	private static final int FLAMTAER_BAG = 25630;
	private static final int COAL_BAG = 25627;
	private static final int GEM_BAG = 25628;
	private static final int PLANK_SACK = 25629;
	private static final int ALCHEMISTS_AMULET = 29992;
	private static final Map<Skill, List<Spec>> SPECS = buildSpecs();

	@Nullable
	private final String heading;
	@Nullable
	private final String category;
	private final List<Integer> itemIds;
	private final Map<Integer, String> itemNames;
	private final PlayerItems primary;
	private final PlayerItems compared;

	private SkillClogSection(@Nullable String heading, @Nullable String category,
		List<Integer> itemIds,
		Map<Integer, String> itemNames, PlayerItems primary, PlayerItems compared)
	{
		this.heading = heading;
		this.category = category;
		this.itemIds = Collections.unmodifiableList(itemIds);
		this.itemNames = Collections.unmodifiableMap(itemNames);
		this.primary = primary;
		this.compared = compared;
	}

	static List<SkillClogSection> forSkill(Skill skill,
		@Nullable ClogResult primary, @Nullable ClogResult compared,
		@Nullable ClogResult catalog)
	{
		List<Spec> specs = SPECS.get(skill);
		if (specs == null)
		{
			return Collections.emptyList();
		}

		List<SkillClogSection> sections = new ArrayList<>();
		for (Spec spec : specs)
		{
			SkillClogSection section = fromSpec(spec, primary, compared, catalog);
			if (section != null)
			{
				sections.add(section);
			}
		}
		return Collections.unmodifiableList(sections);
	}

	@Nullable
	private static SkillClogSection fromSpec(Spec spec,
		@Nullable ClogResult primary, @Nullable ClogResult compared,
		@Nullable ClogResult catalog)
	{
		LinkedHashSet<Integer> distinctIds = new LinkedHashSet<>();
		if (spec.category != null)
		{
			addCategoryItems(distinctIds, catalog, spec.category);
			addCategoryItems(distinctIds, primary, spec.category);
			addCategoryItems(distinctIds, compared, spec.category);
		}
		else
		{
			distinctIds.addAll(spec.itemIds);
		}
		if (distinctIds.isEmpty())
		{
			return null;
		}

		List<Integer> itemIds = new ArrayList<>(distinctIds);
		Map<Integer, String> itemNames = new LinkedHashMap<>();
		addItemNames(itemNames, itemIds, catalog);
		addItemNames(itemNames, itemIds, compared);
		addItemNames(itemNames, itemIds, primary);
		return new SkillClogSection(spec.heading, spec.category, itemIds, itemNames,
			PlayerItems.from(primary, spec, distinctIds),
			PlayerItems.from(compared, spec, distinctIds));
	}

	private static void addCategoryItems(Set<Integer> destination,
		@Nullable ClogResult result, String category)
	{
		if (result == null)
		{
			return;
		}
		List<Integer> items = result.getCategoryItems().get(category);
		if (items != null)
		{
			for (int itemId : items)
			{
				destination.add(canonicalItemId(itemId));
			}
		}
	}

	private static void addItemNames(Map<Integer, String> destination,
		List<Integer> itemIds, @Nullable ClogResult result)
	{
		if (result == null)
		{
			return;
		}
		for (int itemId : itemIds)
		{
			String name = result.getItemName(itemId);
			if (name == null)
			{
				name = aliasName(result, itemId);
			}
			if (name != null)
			{
				destination.put(itemId, name);
			}
		}
	}

	private static int canonicalItemId(int itemId)
	{
		switch (itemId)
		{
			case 764:
			case 12019:
			case 24480:
				return COAL_BAG;
			case 766:
			case 12020:
			case 24481:
				return GEM_BAG;
			case 29472:
				return 12013;
			case 29474:
				return 12014;
			case 29476:
				return 12015;
			case 29478:
				return 12016;
			case LEGACY_FLAMTAER_BAG:
				return FLAMTAER_BAG;
			case 24882:
				return PLANK_SACK;
			case 29988:
			case 29990:
				return ALCHEMISTS_AMULET;
			default:
				return itemId;
		}
	}

	@Nullable
	private static String aliasName(ClogResult result, int canonicalItemId)
	{
		switch (canonicalItemId)
		{
			case COAL_BAG:
				return firstName(result, 12019, 24480, 764);
			case GEM_BAG:
				return firstName(result, 12020, 24481, 766);
			case 12013:
				return result.getItemName(29472);
			case 12014:
				return result.getItemName(29474);
			case 12015:
				return result.getItemName(29476);
			case 12016:
				return result.getItemName(29478);
			case FLAMTAER_BAG:
				return result.getItemName(LEGACY_FLAMTAER_BAG);
			case PLANK_SACK:
				return result.getItemName(24882);
			case ALCHEMISTS_AMULET:
				return firstName(result, 29990, 29988);
			default:
				return null;
		}
	}

	private static String firstName(ClogResult result, int... itemIds)
	{
		for (int itemId : itemIds)
		{
			String name = result.getItemName(itemId);
			if (name != null)
			{
				return name;
			}
		}
		return null;
	}

	static Progress combinedProgress(List<SkillClogSection> sections, boolean comparisonSide)
	{
		Set<Integer> allItems = new HashSet<>();
		Set<Integer> obtained = new HashSet<>();
		boolean synced = false;
		for (SkillClogSection section : sections)
		{
			allItems.addAll(section.itemIds);
			PlayerItems playerItems = comparisonSide ? section.compared : section.primary;
			synced |= playerItems.synced;
			obtained.addAll(playerItems.obtainedIds);
		}
		return new Progress(synced ? obtained.size() : -1, allItems.size());
	}

	@Nullable
	String heading()
	{
		return heading;
	}

	boolean hasHeading()
	{
		return heading != null && !heading.isEmpty();
	}

	boolean isCategory(String expectedCategory)
	{
		return expectedCategory.equals(category);
	}

	List<Integer> itemIds()
	{
		return itemIds;
	}

	Map<Integer, String> itemNames()
	{
		return itemNames;
	}

	PlayerItems primary()
	{
		return primary;
	}

	PlayerItems compared()
	{
		return compared;
	}

	private static Map<Skill, List<Spec>> buildSpecs()
	{
		// Pets stay first. Activity sections follow alphabetically. Full
		// activities read their live category catalogs instead of copying slots.
		Map<Skill, List<Spec>> specs = new LinkedHashMap<>();
		specs.put(Skill.ATTACK, specs(
			category("Barbarian Assault", "barbarian_assault")));
		specs.put(Skill.HITPOINTS, specs(
			items("Equipment Unlocks", HITPOINTS_EQUIPMENT_ITEMS),
			category("Pest Control", "pest_control")));
		specs.put(Skill.DEFENCE, specs(
			category("Defenders", "cyclopes")));
		specs.put(Skill.STRENGTH, specs(
			items("Weapon Unlocks", STRENGTH_WEAPON_ITEMS)));
		specs.put(Skill.MINING, specs(
			items("Pets", MINING_PETS),
			category("Camdozaal", "camdozaal"),
			items("Mining Guild", MINING_GUILD_ITEMS),
			category("Motherlode Mine", "motherlode_mine"),
			category("Shooting Stars", "shooting_stars"),
			category("Volcanic Mine", "volcanic_mine"),
			category("Zalcano", "zalcano")));
		specs.put(Skill.AGILITY, specs(
			items("Pets", AGILITY_PETS),
			category("Brimhaven Agility Arena", "brimhaven_agility_arena"),
			category("Colossal Wyrm Agility Course", "colossal_wyrm_agility"),
			category("Hallowed Sepulchre", "hallowed_sepulchre"),
			category("Monkey Backpacks", "monkey_backpacks"),
			category("Rooftop Agility", "rooftop_agility"),
			items("Underwater", UNDERWATER_ITEMS)));
		specs.put(Skill.SMITHING, specs(
			category("Giants' Foundry", "giants_foundry")));
		specs.put(Skill.HERBLORE, specs(
			category("Mastering Mixology", "mastering_mixology")));
		specs.put(Skill.FISHING, specs(
			items("Pets", FISHING_PETS),
			category("Aerial Fishing", "aerial_fishing"),
			items("Big Fish", BIG_FISH_ITEMS),
			items("Deep Sea Trawling", DEEP_SEA_TRAWLING_ITEMS),
			category("Fishing Trawler", "fishing_trawler"),
			items("Lantern Harpooning", LANTERN_HARPOONING_ITEMS),
			category("Tempoross", "tempoross")));
		specs.put(Skill.RANGED, specs(
			category("Chompy Bird Hunting", "chompy_bird_hunting")));
		specs.put(Skill.THIEVING, specs(
			items("Pets", THIEVING_PETS),
			items("Pickpocketing", PICKPOCKETING_ITEMS),
			items("Pyramid Plunder", PYRAMID_PLUNDER_ITEMS),
			category("Rogues' Den", "rogues_den"),
			items("Underwater", UNDERWATER_ITEMS)));
		specs.put(Skill.COOKING, specs(
			category("Gnome Restaurant", "gnome_restaurant"),
			category("Trouble Brewing", "trouble_brewing")));
		specs.put(Skill.PRAYER, specs(
			category("Shades of Mort'ton", "shades_of_mortton")));
		specs.put(Skill.CRAFTING, specs(
			items("Rarities", CRAFTING_RARITIES)));
		specs.put(Skill.FIREMAKING, specs(
			category("Shades of Mort'ton", "shades_of_mortton"),
			category("Wintertodt", "wintertodt")));
		specs.put(Skill.MAGIC, specs(
			category("Magic Training Arena", "magic_training_arena")));
		specs.put(Skill.FLETCHING, specs(
			category("Vale Totems", "vale_totems")));
		specs.put(Skill.WOODCUTTING, specs(
			items("Pets", WOODCUTTING_PETS),
			items("Evil Chicken Outfit", EVIL_CHICKEN_ITEMS),
			category("Forestry", "forestry")));
		specs.put(Skill.RUNECRAFT, specs(
			items("Pets", RUNECRAFT_PETS),
			category("Guardians of the Rift", PanelData.GOTR_CATEGORY)));
		specs.put(Skill.SLAYER, specs(
			category(null, PanelData.SLAYER_CATEGORY)));
		specs.put(Skill.FARMING, specs(
			items("Pets", FARMING_PETS),
			category("Hespori", "hespori"),
			category("Tithe Farm", "tithe_farm")));
		specs.put(Skill.CONSTRUCTION, specs(
			category("Mahogany Homes", "mahogany_homes")));
		specs.put(Skill.HUNTER, specs(
			items("Pets", HUNTER_PETS),
			category("Aerial Fishing", "aerial_fishing"),
			items("Crystal Impling", CRYSTAL_IMPLING_ITEMS),
			category("Hunter Guild", "hunter_guild")));
		specs.put(Skill.SAILING, specs(
			items("Pets", SAILING_PETS),
			category("Barracuda Trials", "barracuda_trials"),
			category("Boat Paints", "boat_paints"),
			items("Deep Sea Trawling", DEEP_SEA_TRAWLING_ITEMS),
			category("Lost Schematics", "lost_schematics"),
			category("Ocean Encounters", "ocean_encounters"),
			category("Sailing Miscellaneous", "sailing_miscellaneous"),
			category("Sea Treasures", "sea_treasures")));
		return Collections.unmodifiableMap(specs);
	}

	private static List<Spec> specs(Spec... specs)
	{
		return Collections.unmodifiableList(Arrays.asList(specs));
	}

	private static Spec category(@Nullable String heading, String category)
	{
		return new Spec(heading, category, Collections.emptyList());
	}

	private static Spec items(String heading, int... itemIds)
	{
		List<Integer> ids = new ArrayList<>();
		for (int itemId : itemIds)
		{
			ids.add(itemId);
		}
		return new Spec(heading, null, ids);
	}

	static final class PlayerItems
	{
		private final boolean synced;
		private final Set<Integer> obtainedIds;
		private final Map<Integer, Integer> obtainedCounts;

		private PlayerItems(boolean synced, Set<Integer> obtainedIds,
			Map<Integer, Integer> obtainedCounts)
		{
			this.synced = synced;
			this.obtainedIds = Collections.unmodifiableSet(obtainedIds);
			this.obtainedCounts = Collections.unmodifiableMap(obtainedCounts);
		}

		private static PlayerItems from(@Nullable ClogResult result,
			Spec spec, Set<Integer> catalogIds)
		{
			Set<Integer> obtainedIds = new HashSet<>();
			Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
			if (result != null)
			{
				if (spec.category != null)
				{
					addObtained(result.getObtainedItems().get(spec.category),
						catalogIds, obtainedIds, obtainedCounts);
				}
				else
				{
					for (List<ClogResult.ClogItem> categoryItems
						: result.getObtainedItems().values())
					{
						addObtained(categoryItems, catalogIds,
							obtainedIds, obtainedCounts);
					}
				}
			}
			return new PlayerItems(result != null, obtainedIds, obtainedCounts);
		}

		private static void addObtained(@Nullable List<ClogResult.ClogItem> items,
			Set<Integer> catalogIds, Set<Integer> obtainedIds,
			Map<Integer, Integer> obtainedCounts)
		{
			if (items == null)
			{
				return;
			}
			for (ClogResult.ClogItem item : items)
			{
				int itemId = canonicalItemId(item.getId());
				if (catalogIds.contains(itemId))
				{
					obtainedIds.add(itemId);
					obtainedCounts.merge(itemId, item.getCount(), Integer::max);
				}
			}
		}

		boolean synced()
		{
			return synced;
		}

		Set<Integer> obtainedIds()
		{
			return obtainedIds;
		}

		Map<Integer, Integer> obtainedCounts()
		{
			return obtainedCounts;
		}

		int obtainedCount()
		{
			return synced ? obtainedIds.size() : -1;
		}
	}

	static final class Progress
	{
		private final int obtained;
		private final int total;

		private Progress(int obtained, int total)
		{
			this.obtained = obtained;
			this.total = total;
		}

		int obtained()
		{
			return obtained;
		}

		int total()
		{
			return total;
		}
	}

	private static final class Spec
	{
		@Nullable
		private final String heading;
		@Nullable
		private final String category;
		private final List<Integer> itemIds;

		private Spec(@Nullable String heading, @Nullable String category,
			List<Integer> itemIds)
		{
			this.heading = heading;
			this.category = category;
			this.itemIds = Collections.unmodifiableList(new ArrayList<>(itemIds));
		}
	}
}
