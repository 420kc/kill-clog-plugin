package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkillClogSectionTest
{
	private static final List<Integer> DEFENDER_IDS = Arrays.asList(
		8844, 8845, 8846, 8847, 8848, 8849, 8850, 12954);
	private static final List<Integer> HITPOINTS_EQUIPMENT_IDS = Arrays.asList(
		24422, 19529, 29799, 31109, 33634);
	private static final List<Integer> STRENGTH_WEAPON_IDS = Arrays.asList(
		4153, 21646, 21742, 29000,
		13576, 6528, 4718, 4747,
		28997, 21003, 33631, 22486);
	private static final List<Integer> CRAFTING_RARITY_IDS = Arrays.asList(
		34024, 19707);
	private static final List<Integer> PEST_CONTROL_IDS = Arrays.asList(
		8841, 8839, 8840, 8842, 11663, 11665, 11664, 11666, 13072, 13073);

	@Test
	public void slayerUsesTheCurrentCatalogWithoutOwningNewClogSlots()
	{
		List<Integer> currentSlayerCatalog = new ArrayList<>();
		for (int id = 1; id <= 90; id++)
		{
			currentSlayerCatalog.add(id);
		}
		currentSlayerCatalog.add(42);

		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put(PanelData.SLAYER_CATEGORY, currentSlayerCatalog);
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put(PanelData.SLAYER_CATEGORY, Arrays.asList(
			new ClogResult.ClogItem(42, 3, null),
			new ClogResult.ClogItem(42, 7, null),
			new ClogResult.ClogItem(90, 1, null)));
		ClogResult clog = new ClogResult("Tester", obtained, categories,
			Collections.singletonMap(42, "The answer"), null, null);
		int[] totalsBefore = ClogHelper.sumClogTotals(clog);

		List<SkillClogSection> sections = SkillClogSection.forSkill(
			Skill.SLAYER, clog, null, null);

		assertEquals(1, sections.size());
		SkillClogSection section = sections.get(0);
		assertNull(section.heading());
		assertFalse(section.hasHeading());
		assertEquals(90, section.itemIds().size());
		assertEquals(Integer.valueOf(1), section.itemIds().get(0));
		assertEquals(Integer.valueOf(90), section.itemIds().get(89));
		assertEquals(2, section.primary().obtainedCount());
		assertEquals(Integer.valueOf(7), section.primary().obtainedCounts().get(42));
		assertEquals("The answer", section.itemNames().get(42));
		assertEquals(91, currentSlayerCatalog.size());
		assertEquals(totalsBefore[0], ClogHelper.sumClogTotals(clog)[0]);
		assertEquals(totalsBefore[1], ClogHelper.sumClogTotals(clog)[1]);
	}

	@Test
	public void catalogOrderLeadsAndBothPlayersRemainIndependent()
	{
		ClogResult catalog = clog(Arrays.asList(3, 1, 2), Collections.emptyList());
		ClogResult blue = clog(Arrays.asList(1, 2, 4), Arrays.asList(
			new ClogResult.ClogItem(1, 2, null),
			new ClogResult.ClogItem(4, 5, null)));
		ClogResult red = clog(Arrays.asList(2, 3), Collections.singletonList(
			new ClogResult.ClogItem(3, 9, null)));

		SkillClogSection section = SkillClogSection.forSkill(
			Skill.SLAYER, blue, red, catalog).get(0);

		assertEquals(Arrays.asList(3, 1, 2, 4), section.itemIds());
		assertEquals(2, section.primary().obtainedCount());
		assertEquals(1, section.compared().obtainedCount());
		assertTrue(section.primary().obtainedIds().contains(4));
		assertFalse(section.compared().obtainedIds().contains(4));
	}

	@Test
	public void everyGridSkillHasMappedProgress()
	{
		ClogResult catalog = mappedCatalog();
		for (Skill skill : SkillGridOrder.skills())
		{
			assertFalse(skill.getName(), SkillClogSection.forSkill(
				skill, null, null, catalog).isEmpty());
		}
	}

	@Test
	public void everyDormantSkillShowsItsKnownCatalogDenominator()
	{
		ClogResult catalog = mappedCatalog();
		for (Skill skill : SkillGridOrder.skills())
		{
			List<SkillClogSection> sections = SkillClogSection.forSkill(
				skill, null, null, catalog);
			SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
				sections, false);
			SkillTooltip tooltip = new SkillTooltip();
			tooltip.setData(skill, null, false, sections, null);

			assertTrue(skill.getName(), progress.total() > 0);
			assertEquals(skill.getName(), " (--/" + progress.total() + ")",
				tooltip.getTitleSuffix());
			for (SkillClogSection section : sections)
			{
				assertEquals(skill.getName(), "--/" + section.itemIds().size(),
					SkillClogSectionRenderer.progressText(
						section.primary(), section.itemIds().size()));
				assertTrue(skill.getName(), section.primary().obtainedIds().isEmpty());
			}
		}
	}

	@Test
	public void everyPlannedSkillUsesTheApprovedSectionOrder()
	{
		ClogResult catalog = mappedCatalog();

		assertHeadings(catalog, Skill.ATTACK, "Barbarian Assault");
		assertHeadings(catalog, Skill.HITPOINTS, "Equipment Unlocks|Pest Control");
		assertHeadings(catalog, Skill.DEFENCE, "Defenders");
		assertHeadings(catalog, Skill.STRENGTH, "Weapon Unlocks");
		assertEquals(HITPOINTS_EQUIPMENT_IDS, SkillClogSection.forSkill(
			Skill.HITPOINTS, null, null, catalog).get(0).itemIds());
		assertEquals(DEFENDER_IDS, SkillClogSection.forSkill(
			Skill.DEFENCE, null, null, catalog).get(0).itemIds());
		assertEquals(STRENGTH_WEAPON_IDS, SkillClogSection.forSkill(
			Skill.STRENGTH, null, null, catalog).get(0).itemIds());
		assertHeadings(catalog, Skill.MINING,
			"Pets|Camdozaal|Mining Guild|Motherlode Mine|Shooting Stars|Volcanic Mine|Zalcano");
		assertHeadings(catalog, Skill.AGILITY,
			"Pets|Brimhaven Agility Arena|Colossal Wyrm Agility Course|Hallowed Sepulchre"
				+ "|Monkey Backpacks|Rooftop Agility|Underwater");
		assertHeadings(catalog, Skill.SMITHING, "Giants' Foundry");
		assertHeadings(catalog, Skill.HERBLORE, "Mastering Mixology");
		assertHeadings(catalog, Skill.FISHING,
			"Pets|Aerial Fishing|Big Fish|Deep Sea Trawling|Fishing Trawler"
				+ "|Lantern Harpooning|Tempoross");
		assertHeadings(catalog, Skill.RANGED, "Chompy Bird Hunting");
		assertHeadings(catalog, Skill.THIEVING,
			"Pets|Pickpocketing|Pyramid Plunder|Rogues' Den|Underwater");
		assertEquals(Arrays.asList(23959, 24777), SkillClogSection.forSkill(
			Skill.THIEVING, null, null, catalog).get(1).itemIds());
		assertHeadings(catalog, Skill.COOKING, "Gnome Restaurant|Trouble Brewing");
		assertHeadings(catalog, Skill.PRAYER, "Shades of Mort'ton");
		assertHeadings(catalog, Skill.CRAFTING, "Rarities");
		assertEquals(CRAFTING_RARITY_IDS, SkillClogSection.forSkill(
			Skill.CRAFTING, null, null, catalog).get(0).itemIds());
		assertHeadings(catalog, Skill.FIREMAKING, "Shades of Mort'ton|Wintertodt");
		assertHeadings(catalog, Skill.MAGIC, "Magic Training Arena");
		assertHeadings(catalog, Skill.FLETCHING, "Vale Totems");
		assertHeadings(catalog, Skill.WOODCUTTING, "Pets|Evil Chicken Outfit|Forestry");
		assertHeadings(catalog, Skill.RUNECRAFT, "Pets|Guardians of the Rift");
		assertHeadings(catalog, Skill.SLAYER, "");
		assertHeadings(catalog, Skill.FARMING, "Pets|Hespori|Tithe Farm");
		assertHeadings(catalog, Skill.CONSTRUCTION, "Mahogany Homes");
		assertHeadings(catalog, Skill.HUNTER,
			"Pets|Aerial Fishing|Crystal Impling|Hunter Guild");
		assertHeadings(catalog, Skill.SAILING,
			"Pets|Barracuda Trials|Boat Paints|Deep Sea Trawling|Lost Schematics"
				+ "|Ocean Encounters|Sailing Miscellaneous|Sea Treasures");
	}

	@Test
	public void explicitItemsFindCountsAcrossProviderCategories()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("skilling_pets", Collections.singletonList(
			new ClogResult.ClogItem(13321, 2, null)));
		obtained.put("all_pets", Collections.singletonList(
			new ClogResult.ClogItem(13321, 7, null)));
		ClogResult player = new ClogResult("Tester", obtained,
			Collections.emptyMap(), Collections.singletonMap(13321, "Rock golem"),
			null, null);

		SkillClogSection pets = SkillClogSection.forSkill(
			Skill.MINING, player, null, null).get(0);

		assertEquals("Pets", pets.heading());
		assertEquals(Collections.singletonList(13321), pets.itemIds());
		assertEquals(1, pets.primary().obtainedCount());
		assertEquals(Integer.valueOf(7), pets.primary().obtainedCounts().get(13321));
		assertEquals("Rock golem", pets.itemNames().get(13321));
	}

	@Test
	public void hitpointsProgressUsesLoggedEquipmentUnlocks()
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("pest_control", PEST_CONTROL_IDS.stream()
			.map(id -> new ClogResult.ClogItem(id, 1, null))
			.collect(Collectors.toList()));
		obtained.put("equipment_sources", HITPOINTS_EQUIPMENT_IDS.subList(0, 4).stream()
			.map(id -> new ClogResult.ClogItem(id, 1, null))
			.collect(Collectors.toList()));
		ClogResult player = new ClogResult("420 kc", obtained,
			Collections.singletonMap("pest_control", PEST_CONTROL_IDS),
			Collections.emptyMap(), null, null);

		List<SkillClogSection> sections = SkillClogSection.forSkill(
			Skill.HITPOINTS, player, null, null);

		assertEquals(2, sections.size());
		assertEquals(4, sections.get(0).primary().obtainedCount());
		assertEquals(10, sections.get(1).primary().obtainedCount());
		SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
			sections, false);
		assertEquals(14, progress.obtained());
		assertEquals(15, progress.total());
	}

	@Test
	public void shadesCollapsesLegacyAndCurrentFlamtaerBagIds()
	{
		List<Integer> sharedItems = ids(200_000, 13);
		List<Integer> catalogItems = new ArrayList<>(sharedItems);
		catalogItems.add(25630);
		List<Integer> playerItems = new ArrayList<>(sharedItems);
		playerItems.add(12854);
		List<ClogResult.ClogItem> obtainedItems = new ArrayList<>();
		for (int itemId : sharedItems)
		{
			obtainedItems.add(new ClogResult.ClogItem(itemId, 1, null));
		}
		obtainedItems.add(new ClogResult.ClogItem(12854, 2, null));
		obtainedItems.add(new ClogResult.ClogItem(25630, 7, null));

		ClogResult catalog = new ClogResult("Catalog", Collections.emptyMap(),
			Collections.singletonMap("shades_of_mortton", catalogItems),
			Collections.emptyMap(), null, null);
		ClogResult player = new ClogResult("Tester",
			Collections.singletonMap("shades_of_mortton", obtainedItems),
			Collections.singletonMap("shades_of_mortton", playerItems),
			Collections.singletonMap(12854, "Flamtaer bag"), null, null);

		SkillClogSection prayer = SkillClogSection.forSkill(
			Skill.PRAYER, player, null, catalog).get(0);
		assertEquals(14, prayer.itemIds().size());
		assertTrue(prayer.itemIds().contains(25630));
		assertFalse(prayer.itemIds().contains(12854));
		assertEquals(14, prayer.primary().obtainedCount());
		assertEquals(Integer.valueOf(7), prayer.primary().obtainedCounts().get(25630));
		assertEquals("Flamtaer bag", prayer.itemNames().get(25630));
		List<SkillClogSection> firemaking = SkillClogSection.forSkill(
			Skill.FIREMAKING, player, null, catalog);
		assertEquals(14, uniqueItemCount(firemaking));
	}

	@Test
	public void runtimeVariantsCollapseToThePlayersCollectionLogSlots()
	{
		Map<String, List<Integer>> runtimeCategories = new HashMap<>();
		runtimeCategories.put("motherlode_mine", Arrays.asList(12019, 12020));
		runtimeCategories.put("volcanic_mine", Arrays.asList(29472, 29474, 29476, 29478));
		runtimeCategories.put("mastering_mixology",
			Arrays.asList(29974, 29978, 29982, 29986, 29990, 29996, 30002));
		runtimeCategories.put("shades_of_mortton", Collections.singletonList(12854));
		runtimeCategories.put("mahogany_homes", Collections.singletonList(24882));

		Map<String, List<Integer>> playerCategories = new HashMap<>();
		playerCategories.put("motherlode_mine", Arrays.asList(25627, 25628));
		playerCategories.put("volcanic_mine", Arrays.asList(12013, 12014, 12015, 12016));
		playerCategories.put("mastering_mixology",
			Arrays.asList(29974, 29978, 29982, 29986, 29992, 29996, 30002));
		playerCategories.put("shades_of_mortton", Collections.singletonList(25630));
		playerCategories.put("mahogany_homes", Collections.singletonList(25629));

		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : playerCategories.entrySet())
		{
			obtained.put(entry.getKey(), entry.getValue().stream()
				.map(id -> new ClogResult.ClogItem(id, 1, null))
				.collect(Collectors.toList()));
		}
		ClogResult catalog = new ClogResult("Catalog", Collections.emptyMap(),
			runtimeCategories, Collections.singletonMap(29990, "Alchemist's amulet"), null, null);
		ClogResult player = new ClogResult("420 kc", obtained,
			playerCategories, Collections.emptyMap(), null, null);

		assertSection(player, catalog, Skill.MINING, "Motherlode Mine", 2);
		assertSection(player, catalog, Skill.MINING, "Volcanic Mine", 4);
		SkillClogSection mixology = assertSection(
			player, catalog, Skill.HERBLORE, "Mastering Mixology", 7);
		assertEquals("Alchemist's amulet", mixology.itemNames().get(29992));
		assertSection(player, catalog, Skill.PRAYER, "Shades of Mort'ton", 1);
		assertSection(player, catalog, Skill.CONSTRUCTION, "Mahogany Homes", 1);
	}

	@Test
	public void representativeCatalogProducesApprovedUniqueTotals()
	{
		ClogResult catalog = mappedCatalog();

		assertTotal(catalog, Skill.ATTACK, 11);
		assertTotal(catalog, Skill.HITPOINTS, 15);
		assertTotal(catalog, Skill.DEFENCE, 8);
		assertTotal(catalog, Skill.STRENGTH, 12);
		assertTotal(catalog, Skill.MINING, 30);
		assertTotal(catalog, Skill.AGILITY, 48);
		assertTotal(catalog, Skill.SMITHING, 9);
		assertTotal(catalog, Skill.HERBLORE, 7);
		assertTotal(catalog, Skill.FISHING, 32);
		assertTotal(catalog, Skill.RANGED, 19);
		assertTotal(catalog, Skill.THIEVING, 10);
		assertTotal(catalog, Skill.COOKING, 34);
		assertTotal(catalog, Skill.PRAYER, 14);
		assertTotal(catalog, Skill.CRAFTING, 2);
		assertTotal(catalog, Skill.FIREMAKING, 24);
		assertTotal(catalog, Skill.MAGIC, 11);
		assertTotal(catalog, Skill.FLETCHING, 4);
		assertTotal(catalog, Skill.WOODCUTTING, 28);
		assertTotal(catalog, Skill.RUNECRAFT, 18);
		assertTotal(catalog, Skill.SLAYER, 95);
		assertTotal(catalog, Skill.FARMING, 12);
		assertTotal(catalog, Skill.CONSTRUCTION, 8);
		assertTotal(catalog, Skill.HUNTER, 19);
		assertTotal(catalog, Skill.SAILING, 79);
	}

	@Test
	public void everySkillTooltipMeasuresAndPaintsInSoloAndComparisonModes()
	{
		ClogResult catalog = mappedCatalog();
		for (Skill skill : SkillGridOrder.skills())
		{
			List<SkillClogSection> sections = SkillClogSection.forSkill(
				skill, null, null, catalog);
			SkillTooltip solo = new SkillTooltip();
			solo.setData(skill, null, false, sections, null);
			Dimension soloSize = solo.getPreferredSize();
			assertTrue(skill.getName() + " solo tooltip is too wide: " + soloSize,
				soloSize.width <= 300);
			assertTrue(skill.getName() + " solo tooltip is too tall: " + soloSize,
				soloSize.height <= 720);
			paint(solo);

			SkillTooltip leftSide = new SkillTooltip();
			leftSide.setData(skill, null, false, sections, null);
			SkillTooltip rightSide = new SkillTooltip();
			rightSide.setData(skill, null, false, sections, null);
			SideBySideTooltip comparison = new SideBySideTooltip(
				"Blue", leftSide, "Red", rightSide);
			Dimension compareSize = comparison.getPreferredSize();
			assertTrue(skill.getName() + " comparison tooltip is too wide: " + compareSize,
				compareSize.width <= soloSize.width * 2 + 50);
			assertTrue(skill.getName() + " comparison tooltip is too tall: " + compareSize,
				compareSize.height <= soloSize.height + 60);
			comparison.setSize(compareSize);
			comparison.doLayout();
			paint(comparison);
			assertMeasuredSectionHeightMatchesPaint(skill, sections);
		}
	}

	@Test
	public void regularSpritesGiveWayToCompactSpritesOnlyForDenseSkillLogs()
	{
		SkillClogSectionRenderer renderer = new SkillClogSectionRenderer(new SkillTooltip());
		renderer.setSections(SkillClogSection.forSkill(
			Skill.SLAYER, null, null, clog(ids(1, 29), Collections.emptyList())), null);
		assertFalse(renderer.usesCompactSprites());

		renderer.setSections(SkillClogSection.forSkill(
			Skill.SLAYER, null, null, clog(ids(1, 30), Collections.emptyList())), null);
		assertTrue(renderer.usesCompactSprites());
	}

	@Test
	public void combinedProgressDeduplicatesItemsSharedByActivities()
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("motherlode_mine", Arrays.asList(10, 11, 12, 13, 14, 15));
		categories.put("volcanic_mine", Arrays.asList(12, 13, 14, 15, 16, 17));
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("motherlode_mine", Arrays.asList(
			new ClogResult.ClogItem(10, 1, null),
			new ClogResult.ClogItem(12, 2, null)));
		obtained.put("volcanic_mine", Arrays.asList(
			new ClogResult.ClogItem(12, 2, null),
			new ClogResult.ClogItem(16, 1, null)));
		ClogResult player = new ClogResult("Tester", obtained, categories,
			Collections.emptyMap(), null, null);

		List<SkillClogSection> sections = SkillClogSection.forSkill(
			Skill.MINING, player, null, null);
		SkillClogSection.Progress progress = SkillClogSection.combinedProgress(
			sections, false);

		// Eight unique category items, plus the pet and three Mining Guild slots.
		assertEquals(12, progress.total());
		assertEquals(3, progress.obtained());
	}

	private static void assertHeadings(ClogResult catalog, Skill skill, String expected)
	{
		String actual = SkillClogSection.forSkill(skill, null, null, catalog).stream()
			.map(section -> section.heading() == null ? "" : section.heading())
			.collect(Collectors.joining("|"));
		assertEquals(skill.getName(), expected, actual);
	}

	private static void assertTotal(ClogResult catalog, Skill skill, int expected)
	{
		List<SkillClogSection> sections = SkillClogSection.forSkill(
			skill, null, null, catalog);
		assertEquals(skill.getName(), expected, uniqueItemCount(sections));
	}

	private static int uniqueItemCount(List<SkillClogSection> sections)
	{
		HashSet<Integer> itemIds = new HashSet<>();
		for (SkillClogSection section : sections)
		{
			itemIds.addAll(section.itemIds());
		}
		return itemIds.size();
	}

	private static void assertMeasuredSectionHeightMatchesPaint(Skill skill,
		List<SkillClogSection> sections)
	{
		SkillTooltip target = new SkillTooltip();
		SkillClogSectionRenderer solo = new SkillClogSectionRenderer(target);
		solo.setSections(sections, null);
		boolean runecraft = skill == Skill.RUNECRAFT;
		if (runecraft)
		{
			solo.setRiftsClosed(34, -1);
		}
		Dimension soloSize = solo.soloSize(100);
		BufferedImage image = new BufferedImage(
			Math.max(1, soloSize.width + TitleTooltip.getInset() * 2),
			Math.max(1, soloSize.height), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		assertEquals(skill.getName() + " solo measured height",
			soloSize.height, solo.paintSolo(graphics, image.getWidth(), 0, new ArrayList<>()));

		SkillClogSectionRenderer comparison = new SkillClogSectionRenderer(target);
		comparison.setSections(sections, null);
		if (runecraft)
		{
			comparison.setRiftsClosed(34, 1_234);
		}
		Dimension compareSize = comparison.compareSize(100);
		assertEquals(skill.getName() + " comparison measured height",
			compareSize.height,
			comparison.paintCompare(graphics, compareSize.width + TitleTooltip.getInset() * 2,
				0, new ArrayList<>()));
		graphics.dispose();
	}

	private static SkillClogSection assertSection(ClogResult player, ClogResult catalog,
		Skill skill, String heading, int expected)
	{
		SkillClogSection section = SkillClogSection.forSkill(
			skill, player, null, catalog).stream()
			.filter(candidate -> heading.equals(candidate.heading()))
			.findFirst()
			.orElseThrow(AssertionError::new);
		assertEquals(heading, expected, section.itemIds().size());
		assertEquals(heading, expected, section.primary().obtainedCount());
		return section;
	}

	private static ClogResult mappedCatalog()
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		List<Integer> angler = ids(100_000, 4);
		List<Integer> prospector = ids(101_000, 4);
		categories.put("aerial_fishing", concat(ids(102_000, 5), angler));
		categories.put("barbarian_assault", ids(137_000, 11));
		categories.put("barracuda_trials", ids(103_000, 9));
		categories.put("boat_paints", ids(104_000, 11));
		categories.put("brimhaven_agility_arena", ids(105_000, 9));
		categories.put("camdozaal", ids(106_000, 10));
		categories.put("chompy_bird_hunting", ids(107_000, 19));
		categories.put("colossal_wyrm_agility", ids(108_000, 8));
		categories.put("cyclopes", DEFENDER_IDS);
		categories.put("fishing_trawler", angler);
		categories.put("forestry", ids(109_000, 23));
		categories.put("giants_foundry", ids(110_000, 9));
		categories.put("gnome_restaurant", ids(111_000, 4));
		categories.put("guardians_of_the_rift", ids(112_000, 17));
		categories.put("hallowed_sepulchre", ids(113_000, 16));
		categories.put("hespori", ids(114_000, 4));
		categories.put("hunter_guild", ids(115_000, 6));
		categories.put("lost_schematics", ids(116_000, 12));
		categories.put("magic_training_arena", ids(117_000, 11));
		categories.put("mahogany_homes", ids(118_000, 8));
		categories.put("mastering_mixology", ids(119_000, 7));
		categories.put("monkey_backpacks", ids(120_000, 6));
		categories.put("motherlode_mine", concat(ids(121_000, 2), prospector));
		categories.put("ocean_encounters", ids(122_000, 11));
		categories.put("pest_control", PEST_CONTROL_IDS);
		categories.put("rogues_den", ids(123_000, 5));
		categories.put("rooftop_agility", ids(124_000, 7));
		categories.put("sailing_miscellaneous", ids(125_000, 12));
		categories.put("sea_treasures", ids(126_000, 17));
		categories.put("shades_of_mortton", ids(127_000, 14));
		categories.put("shooting_stars", ids(128_000, 2));
		categories.put("slayer", ids(129_000, 95));
		categories.put("tempoross", ids(130_000, 12));
		categories.put("tithe_farm", ids(131_000, 7));
		categories.put("trouble_brewing", ids(132_000, 30));
		categories.put("vale_totems", ids(133_000, 4));
		categories.put("volcanic_mine", concat(ids(134_000, 4), prospector));
		categories.put("wintertodt", ids(135_000, 10));
		categories.put("zalcano", ids(136_000, 4));
		return new ClogResult("Catalog", Collections.emptyMap(), categories,
			Collections.emptyMap(), null, null);
	}

	private static List<Integer> ids(int first, int count)
	{
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			ids.add(first + i);
		}
		return ids;
	}

	private static List<Integer> concat(List<Integer> first, List<Integer> second)
	{
		List<Integer> combined = new ArrayList<>(first);
		combined.addAll(second);
		return combined;
	}

	private static void paint(javax.swing.JToolTip tooltip)
	{
		Dimension size = tooltip.getPreferredSize();
		assertTrue(size.width > 0);
		assertTrue(size.height > 0);
		tooltip.setSize(size);
		BufferedImage image = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();
	}

	private static ClogResult clog(List<Integer> category,
		List<ClogResult.ClogItem> obtained)
	{
		return new ClogResult("Tester",
			Collections.singletonMap(PanelData.SLAYER_CATEGORY, obtained),
			Collections.singletonMap(PanelData.SLAYER_CATEGORY, category),
			Collections.emptyMap(), null, null);
	}
}
