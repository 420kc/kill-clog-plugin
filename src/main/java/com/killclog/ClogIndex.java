package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.game.ItemManager;

/**
 * The collection log catalog parsed from the running game build's own enums.
 *
 * <p>Parsed on the client thread; published whole as an immutable-by-convention
 * snapshot through a volatile field, the same contract as {@link CaCatalog}.
 * Consumers on other threads (chat command executor, EDT) read one complete
 * parse or none; the maps are never mutated after publication.
 */
@Slf4j
final class ClogIndex
{
	private static final int ENUM_CLOG_TABS = 2102;
	private static final int ENUM_CLOG_DUPE_REMAP = 3721;
	private static final int PARAM_TAB_NAME = 682;
	private static final int PARAM_SUBTAB_ENUM = 683;
	private static final int PARAM_CATEGORY_NAME = 689;
	private static final int PARAM_CATEGORY_ITEMS = 690;
	private static final Map<Integer, Integer> FALLBACK_ITEM_REMAPS = fallbackItemRemaps();
	private static final ClogItemCanonicalizer FALLBACK_CANONICALIZER =
		new ClogItemCanonicalizer(Collections.emptyMap(), FALLBACK_ITEM_REMAPS);

	/** One parse product; replaced whole, never mutated. */
	private static final class Snapshot
	{
		final Map<String, List<Integer>> categoryItems;
		final Map<String, List<String>> tabCategoryKeys;
		final Map<Integer, List<String>> itemCategoryKeys;
		final Map<String, List<Integer>> itemNameIds;
		final Map<Integer, String> itemNames;
		final ClogItemCanonicalizer canonicalizer;

		Snapshot(Map<String, List<Integer>> categoryItems,
			Map<String, List<String>> tabCategoryKeys,
			Map<Integer, List<String>> itemCategoryKeys,
			Map<String, List<Integer>> itemNameIds,
			Map<Integer, String> itemNames,
			ClogItemCanonicalizer canonicalizer)
		{
			this.categoryItems = categoryItems;
			this.tabCategoryKeys = tabCategoryKeys;
			this.itemCategoryKeys = itemCategoryKeys;
			this.itemNameIds = itemNameIds;
			this.itemNames = itemNames;
			this.canonicalizer = canonicalizer;
		}
	}

	private volatile Snapshot snapshot;

	boolean ensureParsed(Client client, ItemManager itemManager)
	{
		if (snapshot != null)
		{
			return true;
		}

		try
		{
			Map<String, List<Integer>> nextCategoryItems = new HashMap<>();
			Map<String, List<String>> nextTabCategoryKeys = new LinkedHashMap<>();
			Map<Integer, List<String>> nextItemCategoryKeys = new HashMap<>();
			Map<String, List<Integer>> nextItemNameIds = new HashMap<>();
			Map<Integer, String> nextItemNames = new HashMap<>();
			Map<Integer, Integer> nextCanonicalItemIds = readCanonicalItemIds(client);

			EnumComposition tabs = client.getEnum(ENUM_CLOG_TABS);
			int tabPosition = 0;
			for (int tabKey : tabs.getKeys())
			{
				int tabStructId = tabs.getIntValue(tabKey);
				StructComposition tabStruct = client.getStructComposition(tabStructId);
				String tabName = resolveTabName(
					tabStruct.getStringValue(PARAM_TAB_NAME), tabPosition++);
				int subtabEnumId = tabStruct.getIntValue(PARAM_SUBTAB_ENUM);

				EnumComposition subtabs = client.getEnum(subtabEnumId);
				for (int subKey : subtabs.getKeys())
				{
					int catStructId = subtabs.getIntValue(subKey);
					StructComposition catStruct = client.getStructComposition(catStructId);

					String name = catStruct.getStringValue(PARAM_CATEGORY_NAME);
					int itemsEnumId = catStruct.getIntValue(PARAM_CATEGORY_ITEMS);
					if (name == null || itemsEnumId <= 0)
					{
						continue;
					}

					String categoryKey = ClogService.bossToCategory(name);
					if (tabName != null)
					{
						nextTabCategoryKeys.computeIfAbsent(tabName, k -> new ArrayList<>())
							.add(categoryKey);
					}
					EnumComposition itemsEnum = client.getEnum(itemsEnumId);
					LinkedHashSet<Integer> itemIds = new LinkedHashSet<>();
					for (int itemKey : itemsEnum.getKeys())
					{
						int rawItemId = itemsEnum.getIntValue(itemKey);
						int itemId = canonicalItemId(rawItemId, nextCanonicalItemIds);
						itemIds.add(itemId);
						List<String> itemCategories = nextItemCategoryKeys.computeIfAbsent(
							itemId, k -> new ArrayList<>());
						if (!itemCategories.contains(categoryKey))
						{
							itemCategories.add(categoryKey);
						}
						indexItemName(nextItemNameIds, nextItemNames, itemManager,
							rawItemId, itemId);
					}
					nextCategoryItems.put(categoryKey, new ArrayList<>(itemIds));
				}
			}

			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, "mimic", new int[]{PanelData.THIRD_AGE_RING_ITEM_ID});
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, PanelData.CLOG_THIRD_AGE, PanelData.THIRD_AGE_ITEMS);
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, PanelData.CLOG_GILDED, PanelData.GILDED_ITEMS);

			ClogItemCanonicalizer canonicalizer = new ClogItemCanonicalizer(
				nextCategoryItems, nextCanonicalItemIds);
			Snapshot parsed = new Snapshot(nextCategoryItems, nextTabCategoryKeys,
				nextItemCategoryKeys,
				nextItemNameIds, nextItemNames, canonicalizer);
			snapshot = parsed;
			log.debug("Parsed clog enums: {} tabs, {} categories, {} items, {} names, "
				+ "{} remaps, {} variants",
				parsed.tabCategoryKeys.size(), parsed.categoryItems.size(),
				parsed.itemCategoryKeys.size(), parsed.itemNameIds.size(),
				canonicalizer.declaredAliasCount(), canonicalizer.catalogVariantCount());
			return true;
		}
		catch (Exception e)
		{
			log.warn("Failed to parse clog enums", e);
			snapshot = null;
			return false;
		}
	}

	void clear()
	{
		snapshot = null;
	}

	boolean isParsed()
	{
		return snapshot != null;
	}

	List<Integer> itemIdsForName(String itemKey)
	{
		Snapshot s = snapshot;
		return s != null ? s.itemNameIds.get(itemKey) : null;
	}

	List<String> categoryKeysForItem(int itemId)
	{
		Snapshot s = snapshot;
		return s != null ? s.itemCategoryKeys.get(canonicalItemId(itemId)) : null;
	}

	int canonicalItemId(int itemId)
	{
		Snapshot s = snapshot;
		return s != null
			? s.canonicalizer.canonicalItemId(itemId) : fallbackCanonicalItemId(itemId);
	}

	List<Integer> canonicalizeItemIds(List<Integer> itemIds)
	{
		Snapshot s = snapshot;
		return s != null ? s.canonicalizer.canonicalizeItemIds(itemIds)
			: FALLBACK_CANONICALIZER.canonicalizeItemIds(itemIds);
	}

	List<ClogResult.ClogItem> canonicalizeItems(List<ClogResult.ClogItem> items)
	{
		Snapshot s = snapshot;
		return s != null ? s.canonicalizer.canonicalizeItems(items)
			: FALLBACK_CANONICALIZER.canonicalizeItems(items);
	}

	static int fallbackCanonicalItemId(int itemId)
	{
		return canonicalItemId(itemId, FALLBACK_ITEM_REMAPS);
	}

	@Nullable
	String itemName(ClogResult result, int itemId)
	{
		String direct = result.getItemName(itemId);
		if (direct != null)
		{
			return direct;
		}
		Snapshot s = snapshot;
		if (s != null)
		{
			return s.itemNames.get(itemId);
		}
		return null;
	}

	Set<String> categoryKeys()
	{
		Snapshot s = snapshot;
		return s != null ? s.categoryItems.keySet() : Collections.emptySet();
	}

	Map<String, List<Integer>> categoryItems()
	{
		Snapshot s = snapshot;
		return s != null ? s.categoryItems : Collections.emptyMap();
	}

	Map<String, List<String>> tabCategoryKeys()
	{
		Snapshot s = snapshot;
		return s != null ? s.tabCategoryKeys : Collections.emptyMap();
	}

	Map<String, List<Integer>> copyCategoryItems()
	{
		Map<String, List<Integer>> copy = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : categoryItems().entrySet())
		{
			copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return copy;
	}

	/**
	 * Identity of the current parse, for callers caching structures derived
	 * from it; null when unparsed. Reference-compare only.
	 */
	@Nullable
	Object generation()
	{
		return snapshot;
	}

	/**
	 * One coherent copy of the whole catalog: category items and item names
	 * captured from the same parse, so a concurrent clear or reparse can
	 * never pair pieces of two generations. Null when unparsed.
	 */
	@Nullable
	CatalogCopy copyCatalog()
	{
		Snapshot s = snapshot;
		if (s == null)
		{
			return null;
		}
		Map<String, List<Integer>> categories = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : s.categoryItems.entrySet())
		{
			categories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return new CatalogCopy(s, categories, new HashMap<>(s.itemNames));
	}

	/** Test seam: publish a parse with the given categories and item names. */
	/* package */ void publishForTest(Map<String, List<Integer>> categoryItems,
		Map<Integer, String> itemNames)
	{
		Map<Integer, Integer> remaps = new HashMap<>(FALLBACK_ITEM_REMAPS);
		snapshot = new Snapshot(new HashMap<>(categoryItems), new LinkedHashMap<>(),
			itemCategoryKeys(categoryItems),
			new HashMap<>(), new HashMap<>(itemNames),
			new ClogItemCanonicalizer(categoryItems, remaps));
	}

	/** Test seam for a catalog carrying the top-level Jagex tab taxonomy. */
	/* package */ void publishForTest(Map<String, List<Integer>> categoryItems,
		Map<Integer, String> itemNames, Map<String, List<String>> tabCategoryKeys)
	{
		Map<Integer, Integer> remaps = new HashMap<>(FALLBACK_ITEM_REMAPS);
		snapshot = new Snapshot(new HashMap<>(categoryItems), new LinkedHashMap<>(tabCategoryKeys),
			itemCategoryKeys(categoryItems), new HashMap<>(), new HashMap<>(itemNames),
			new ClogItemCanonicalizer(categoryItems, remaps));
	}

	/** Test seam for runtime-provided duplicate item remaps. */
	/* package */ void publishForTest(Map<String, List<Integer>> categoryItems,
		Map<Integer, String> itemNames, Map<String, List<String>> tabCategoryKeys,
		Map<Integer, Integer> canonicalItemIds)
	{
		Map<Integer, Integer> remaps = new HashMap<>(FALLBACK_ITEM_REMAPS);
		remaps.putAll(canonicalItemIds);
		snapshot = new Snapshot(new HashMap<>(categoryItems), new LinkedHashMap<>(tabCategoryKeys),
			itemCategoryKeys(categoryItems), new HashMap<>(), new HashMap<>(itemNames),
			new ClogItemCanonicalizer(categoryItems, remaps));
	}

	private static Map<Integer, List<String>> itemCategoryKeys(
		Map<String, List<Integer>> categoryItems)
	{
		Map<Integer, List<String>> itemCategories = new HashMap<>();
		for (Map.Entry<String, List<Integer>> category : categoryItems.entrySet())
		{
			for (int itemId : category.getValue())
			{
				itemCategories.computeIfAbsent(itemId, ignored -> new ArrayList<>())
					.add(category.getKey());
			}
		}
		return itemCategories;
	}

	/** A coherent catalog copy plus the identity of the parse it came from. */
	static final class CatalogCopy
	{
		final Object generation;
		final Map<String, List<Integer>> categoryItems;
		final Map<Integer, String> itemNames;

		private CatalogCopy(Object generation, Map<String, List<Integer>> categoryItems,
			Map<Integer, String> itemNames)
		{
			this.generation = generation;
			this.categoryItems = categoryItems;
			this.itemNames = itemNames;
		}
	}

	int categoryCount()
	{
		Snapshot s = snapshot;
		return s != null ? s.categoryItems.size() : 0;
	}

	private void injectSynthetic(Map<String, List<Integer>> categoryItems,
		Map<Integer, List<String>> itemCategoryKeys, Map<String, List<Integer>> itemNameIds,
		Map<Integer, String> itemNames,
		ItemManager itemManager, String key, int[] itemIds)
	{
		List<Integer> ids = new ArrayList<>(itemIds.length);
		for (int id : itemIds)
		{
			ids.add(id);
			itemCategoryKeys.computeIfAbsent(id, k -> new ArrayList<>()).add(key);
			indexItemName(itemNameIds, itemNames, itemManager, id, id);
		}
		categoryItems.put(key, ids);
	}

	private void indexItemName(Map<String, List<Integer>> itemNameIds,
		Map<Integer, String> itemNames, ItemManager itemManager, int sourceItemId,
		int canonicalItemId)
	{
		String itemName = itemManager.getItemComposition(sourceItemId).getName();
		String itemKey = ClogUnlockParser.normalizeItemName(itemName);
		if (!itemKey.isEmpty() && !"null".equals(itemKey))
		{
			String canonicalName = itemManager.getItemComposition(canonicalItemId).getName();
			itemNames.putIfAbsent(canonicalItemId,
				canonicalName != null && !"null".equalsIgnoreCase(canonicalName)
					? canonicalName : itemName);
			List<Integer> ids = itemNameIds.computeIfAbsent(itemKey, k -> new ArrayList<>());
			if (!ids.contains(canonicalItemId))
			{
				ids.add(canonicalItemId);
			}
		}
	}

	private static Map<Integer, Integer> readCanonicalItemIds(Client client)
	{
		Map<Integer, Integer> remaps = new HashMap<>(FALLBACK_ITEM_REMAPS);
		try
		{
			EnumComposition runtime = client.getEnum(ENUM_CLOG_DUPE_REMAP);
			int[] sourceIds = runtime.getKeys();
			int[] canonicalIds = runtime.getIntVals();
			for (int i = 0; i < sourceIds.length && i < canonicalIds.length; i++)
			{
				remaps.put(sourceIds[i], canonicalIds[i]);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to read clog item remaps; using known aliases", e);
		}
		return remaps;
	}

	private static int canonicalItemId(int itemId, Map<Integer, Integer> remaps)
	{
		return remaps.getOrDefault(itemId, itemId);
	}

	static String resolveTabName(@Nullable String parsedName, int position)
	{
		if (parsedName != null && !parsedName.isBlank()
			&& !"null".equalsIgnoreCase(parsedName))
		{
			return parsedName;
		}
		return position >= 0 && position < ClogHelper.CLOG_GROUP_NAMES.length
			? ClogHelper.CLOG_GROUP_NAMES[position] : null;
	}

	private static Map<Integer, Integer> fallbackItemRemaps()
	{
		Map<Integer, Integer> remaps = new HashMap<>();
		remaps.put(764, 25627);
		remaps.put(12019, 25627);
		remaps.put(24480, 25627);
		remaps.put(766, 25628);
		remaps.put(12020, 25628);
		remaps.put(24481, 25628);
		remaps.put(29472, 12013);
		remaps.put(29474, 12014);
		remaps.put(29476, 12015);
		remaps.put(29478, 12016);
		remaps.put(12854, 25630);
		remaps.put(24882, 25629);
		remaps.put(29988, 29992);
		remaps.put(29990, 29992);
		remaps.put(13641, 13640);
		remaps.put(13643, 13642);
		remaps.put(13645, 13644);
		remaps.put(13647, 13646);
		return Collections.unmodifiableMap(remaps);
	}
}
