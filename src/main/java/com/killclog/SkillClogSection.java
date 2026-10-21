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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

/**
 * A presentation-only view of collection-log slots associated with a skill.
 * The view never writes synthetic categories into {@link ClogResult}, so these
 * repeated slots cannot affect the plugin's canonical collection-log totals.
 */
final class SkillClogSection
{
	private static final Map<Skill, List<Spec>> SPECS = buildSpecs();

	@Nullable
	private final String heading;
	@Nullable
	private final String category;
	private final List<Integer> itemIds;
	private final Map<Integer, String> itemNames;
	private final PlayerItems primary;

	private SkillClogSection(@Nullable String heading, @Nullable String category,
		List<Integer> itemIds,
		Map<Integer, String> itemNames, PlayerItems primary)
	{
		this.heading = heading;
		this.category = category;
		this.itemIds = Collections.unmodifiableList(itemIds);
		this.itemNames = Collections.unmodifiableMap(itemNames);
		this.primary = primary;
	}

	static List<SkillClogSection> forSkill(Skill skill,
		@Nullable ClogResult primary, @Nullable ClogResult compared,
		@Nullable ClogResult catalog)
	{
		return forSkill(skill, primary, compared, catalog, null);
	}

	static List<SkillClogSection> forSkill(Skill skill,
		@Nullable ClogResult primary, @Nullable ClogResult compared,
		@Nullable ClogResult catalog, @Nullable ClogIndex clogIndex)
	{
		List<Spec> specs = SPECS.get(skill);
		if (specs == null)
		{
			return Collections.emptyList();
		}

		List<SkillClogSection> sections = new ArrayList<>();
		for (Spec spec : specs)
		{
			SkillClogSection section = fromSpec(spec, primary, compared, catalog,
				clogIndex);
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
		@Nullable ClogResult catalog,
		@Nullable ClogIndex clogIndex)
	{
		LinkedHashSet<Integer> distinctIds = new LinkedHashSet<>();
		if (spec.category != null)
		{
			addCategoryItems(distinctIds, catalog, spec.category, clogIndex);
			addCategoryItems(distinctIds, primary, spec.category, clogIndex);
			addCategoryItems(distinctIds, compared, spec.category, clogIndex);
		}
		else
		{
			for (int itemId : spec.itemIds)
			{
				distinctIds.add(canonicalItemId(itemId, clogIndex));
			}
		}
		if (distinctIds.isEmpty())
		{
			return null;
		}

		List<Integer> itemIds = new ArrayList<>(distinctIds);
		Map<Integer, String> itemNames = new LinkedHashMap<>();
		addItemNames(itemNames, itemIds, catalog, clogIndex);
		addItemNames(itemNames, itemIds, compared, clogIndex);
		addItemNames(itemNames, itemIds, primary, clogIndex);
		return new SkillClogSection(spec.heading, spec.category, itemIds, itemNames,
			PlayerItems.from(primary, spec, distinctIds, clogIndex));
	}

	private static void addCategoryItems(Set<Integer> destination,
		@Nullable ClogResult result, String category, @Nullable ClogIndex clogIndex)
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
				destination.add(canonicalItemId(itemId, clogIndex));
			}
		}
	}

	private static void addItemNames(Map<Integer, String> destination,
		List<Integer> itemIds, @Nullable ClogResult result, @Nullable ClogIndex clogIndex)
	{
		if (result == null)
		{
			return;
		}
		for (int itemId : itemIds)
		{
			String name = ClogIndex.itemNameOrAlias(clogIndex, result, itemId);
			if (name != null)
			{
				destination.put(itemId, name);
			}
		}
	}

	private static int canonicalItemId(int itemId, @Nullable ClogIndex clogIndex)
	{
		return clogIndex != null
			? clogIndex.canonicalItemId(itemId) : ClogIndex.fallbackCanonicalItemId(itemId);
	}

	static Progress combinedProgress(List<SkillClogSection> sections)
	{
		Set<Integer> allItems = new HashSet<>();
		Set<Integer> obtained = new HashSet<>();
		boolean synced = false;
		for (SkillClogSection section : sections)
		{
			allItems.addAll(section.itemIds);
			PlayerItems playerItems = section.primary;
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
			Spec spec, Set<Integer> catalogIds, @Nullable ClogIndex clogIndex)
		{
			Set<Integer> obtainedIds = new HashSet<>();
			Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
			if (result != null)
			{
				if (spec.category != null)
				{
					addObtained(result.getObtainedItems().get(spec.category),
						catalogIds, obtainedIds, obtainedCounts, clogIndex);
				}
				else
				{
					for (List<ClogResult.ClogItem> categoryItems
						: result.getObtainedItems().values())
					{
						addObtained(categoryItems, catalogIds,
							obtainedIds, obtainedCounts, clogIndex);
					}
				}
			}
			return new PlayerItems(result != null, obtainedIds, obtainedCounts);
		}

		private static void addObtained(@Nullable List<ClogResult.ClogItem> items,
			Set<Integer> catalogIds, Set<Integer> obtainedIds,
			Map<Integer, Integer> obtainedCounts, @Nullable ClogIndex clogIndex)
		{
			if (items == null)
			{
				return;
			}
			for (ClogResult.ClogItem item : items)
			{
				int itemId = canonicalItemId(item.getId(), clogIndex);
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

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Progress
	{
		private final int obtained;
		private final int total;

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
