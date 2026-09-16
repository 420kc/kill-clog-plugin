package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemVariationMapping;

/** Maps observed item forms onto unambiguous slots in the live Collection Log catalog. */
final class ClogItemCanonicalizer
{
	private final Map<Integer, Integer> declaredAliases;
	private final Map<Integer, Integer> catalogVariants;

	ClogItemCanonicalizer(Map<String, List<Integer>> categoryItems,
		Map<Integer, Integer> declaredAliases)
	{
		this.declaredAliases = Collections.unmodifiableMap(new HashMap<>(declaredAliases));
		catalogVariants = buildCatalogVariants(categoryItems, declaredAliases);
	}

	int canonicalItemId(int itemId)
	{
		int declared = declaredAliases.getOrDefault(itemId, itemId);
		return catalogVariants.getOrDefault(declared, declared);
	}

	List<Integer> canonicalizeItemIds(List<Integer> itemIds)
	{
		LinkedHashSet<Integer> canonical = new LinkedHashSet<>();
		if (itemIds != null)
		{
			for (int itemId : itemIds)
			{
				canonical.add(canonicalItemId(itemId));
			}
		}
		return new ArrayList<>(canonical);
	}

	List<ClogResult.ClogItem> canonicalizeItems(List<ClogResult.ClogItem> items)
	{
		Map<Integer, ClogResult.ClogItem> canonical = new LinkedHashMap<>();
		if (items != null)
		{
			for (ClogResult.ClogItem item : items)
			{
				int itemId = canonicalItemId(item.getId());
				ClogResult.ClogItem prior = canonical.get(itemId);
				canonical.put(itemId, prior == null
					? copyItem(item, itemId) : mergeItems(prior, item, itemId));
			}
		}
		return new ArrayList<>(canonical.values());
	}

	int declaredAliasCount()
	{
		return declaredAliases.size();
	}

	int catalogVariantCount()
	{
		return catalogVariants.size();
	}

	private static Map<Integer, Integer> buildCatalogVariants(
		Map<String, List<Integer>> categoryItems, Map<Integer, Integer> declaredAliases)
	{
		Set<Integer> catalogIds = new HashSet<>();
		for (List<Integer> items : categoryItems.values())
		{
			catalogIds.addAll(items);
		}

		Map<Integer, Set<Integer>> candidates = new HashMap<>();
		for (int itemId : catalogIds)
		{
			addCandidates(candidates, itemId, itemId);
		}
		for (Map.Entry<Integer, Integer> alias : declaredAliases.entrySet())
		{
			if (catalogIds.contains(alias.getValue()))
			{
				addCandidates(candidates, alias.getKey(), alias.getValue());
			}
		}

		Map<Integer, Integer> variants = new HashMap<>();
		for (Map.Entry<Integer, Set<Integer>> candidate : candidates.entrySet())
		{
			if (!catalogIds.contains(candidate.getKey())
				&& !declaredAliases.containsKey(candidate.getKey())
				&& candidate.getValue().size() == 1)
			{
				variants.put(candidate.getKey(), candidate.getValue().iterator().next());
			}
		}
		return Collections.unmodifiableMap(variants);
	}

	private static void addCandidates(Map<Integer, Set<Integer>> candidates,
		int sourceItemId, int canonicalItemId)
	{
		for (int variantId : ItemVariationMapping.getVariations(sourceItemId))
		{
			candidates.computeIfAbsent(variantId, ignored -> new HashSet<>())
				.add(canonicalItemId);
		}
	}

	private static ClogResult.ClogItem copyItem(ClogResult.ClogItem item, int itemId)
	{
		return new ClogResult.ClogItem(itemId, item.getCount(), item.getDate(),
			item.getObtainedAtKc(), item.getObtainedFrom());
	}

	private static ClogResult.ClogItem mergeItems(ClogResult.ClogItem prior,
		ClogResult.ClogItem current, int itemId)
	{
		return new ClogResult.ClogItem(itemId, Math.max(prior.getCount(), current.getCount()),
			current.getDate() != null ? current.getDate() : prior.getDate(),
			current.getObtainedAtKc() > 0 ? current.getObtainedAtKc() : prior.getObtainedAtKc(),
			current.getObtainedFrom() != null
				? current.getObtainedFrom() : prior.getObtainedFrom());
	}
}
