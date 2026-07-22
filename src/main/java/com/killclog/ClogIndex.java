package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	private static final int PARAM_SUBTAB_ENUM = 683;
	private static final int PARAM_CATEGORY_NAME = 689;
	private static final int PARAM_CATEGORY_ITEMS = 690;

	/** One parse product; replaced whole, never mutated. */
	private static final class Snapshot
	{
		final Map<String, List<Integer>> categoryItems;
		final Map<Integer, List<String>> itemCategoryKeys;
		final Map<String, List<Integer>> itemNameIds;
		final Map<Integer, String> itemNames;

		Snapshot(Map<String, List<Integer>> categoryItems,
			Map<Integer, List<String>> itemCategoryKeys,
			Map<String, List<Integer>> itemNameIds,
			Map<Integer, String> itemNames)
		{
			this.categoryItems = categoryItems;
			this.itemCategoryKeys = itemCategoryKeys;
			this.itemNameIds = itemNameIds;
			this.itemNames = itemNames;
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
			Map<Integer, List<String>> nextItemCategoryKeys = new HashMap<>();
			Map<String, List<Integer>> nextItemNameIds = new HashMap<>();
			Map<Integer, String> nextItemNames = new HashMap<>();

			EnumComposition tabs = client.getEnum(ENUM_CLOG_TABS);
			for (int tabKey : tabs.getKeys())
			{
				int tabStructId = tabs.getIntValue(tabKey);
				StructComposition tabStruct = client.getStructComposition(tabStructId);
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
					EnumComposition itemsEnum = client.getEnum(itemsEnumId);
					List<Integer> itemIds = new ArrayList<>();
					for (int itemKey : itemsEnum.getKeys())
					{
						int itemId = itemsEnum.getIntValue(itemKey);
						itemIds.add(itemId);
						nextItemCategoryKeys.computeIfAbsent(itemId, k -> new ArrayList<>())
							.add(categoryKey);
						indexItemName(nextItemNameIds, nextItemNames, itemManager, itemId);
					}
					nextCategoryItems.put(categoryKey, itemIds);
				}
			}

			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, "mimic", new int[]{PanelData.THIRD_AGE_RING_ITEM_ID});
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, PanelData.CLOG_THIRD_AGE, PanelData.THIRD_AGE_ITEMS);
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds, nextItemNames,
				itemManager, PanelData.CLOG_GILDED, PanelData.GILDED_ITEMS);

			Snapshot parsed = new Snapshot(nextCategoryItems, nextItemCategoryKeys,
				nextItemNameIds, nextItemNames);
			snapshot = parsed;
			log.debug("Parsed clog enums: {} categories, {} items, {} names",
				parsed.categoryItems.size(), parsed.itemCategoryKeys.size(), parsed.itemNameIds.size());
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
		return s != null ? s.itemCategoryKeys.get(itemId) : null;
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
		snapshot = new Snapshot(new HashMap<>(categoryItems), new HashMap<>(),
			new HashMap<>(), new HashMap<>(itemNames));
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
			indexItemName(itemNameIds, itemNames, itemManager, id);
		}
		categoryItems.put(key, ids);
	}

	private void indexItemName(Map<String, List<Integer>> itemNameIds,
		Map<Integer, String> itemNames, ItemManager itemManager, int itemId)
	{
		String itemName = itemManager.getItemComposition(itemId).getName();
		String itemKey = ClogUnlockParser.normalizeItemName(itemName);
		if (!itemKey.isEmpty() && !"null".equals(itemKey))
		{
			itemNames.put(itemId, itemName);
			List<Integer> ids = itemNameIds.computeIfAbsent(itemKey, k -> new ArrayList<>());
			if (!ids.contains(itemId))
			{
				ids.add(itemId);
			}
		}
	}
}
