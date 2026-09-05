package com.killclog;

import java.util.ArrayList;
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
		// The curated taxonomy ships as a bundled catalog; the parity test pins
		// its exact contents. Rows are presentation order per skill: pets stay
		// first, activity sections follow alphabetically, and full activities
		// read their live category catalogs instead of copying slots.
		Map<Skill, List<Spec>> specs = new LinkedHashMap<>();
		for (String[] row : CatalogTsv.rows(SkillClogSection.class, "skill-clog-sections.tsv", 4))
		{
			String heading = row[2].isEmpty() ? null : row[2];
			Spec spec;
			if ("category".equals(row[1]))
			{
				spec = new Spec(heading, row[3], Collections.emptyList());
			}
			else
			{
				List<Integer> itemIds = new ArrayList<>();
				for (String itemId : row[3].split(","))
				{
					itemIds.add(Integer.parseInt(itemId.trim()));
				}
				spec = new Spec(heading, null, itemIds);
			}
			Skill skill;
			try
			{
				skill = Skill.valueOf(row[0]);
			}
			catch (IllegalArgumentException e)
			{
				// Skip: skill not present in this client build.
				continue;
			}
			specs.computeIfAbsent(skill, s -> new ArrayList<>()).add(spec);
		}
		for (Map.Entry<Skill, List<Spec>> entry : specs.entrySet())
		{
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
		return Collections.unmodifiableMap(specs);
	}

	/** Read-only projection for catalog-parity tests: kind|heading|category-or-ids. */
	/* package */ static List<String> specSignature(Skill skill)
	{
		List<Spec> specs = SPECS.get(skill);
		if (specs == null)
		{
			return Collections.emptyList();
		}
		List<String> signature = new ArrayList<>(specs.size());
		for (Spec spec : specs)
		{
			StringBuilder line = new StringBuilder(spec.category != null ? "category" : "items");
			line.append('|').append(spec.heading != null ? spec.heading : "").append('|');
			if (spec.category != null)
			{
				line.append(spec.category);
			}
			else
			{
				for (int i = 0; i < spec.itemIds.size(); i++)
				{
					if (i > 0)
					{
						line.append(',');
					}
					line.append(spec.itemIds.get(i));
				}
			}
			signature.add(line.toString());
		}
		return signature;
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
