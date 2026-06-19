package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.game.ItemManager;

@Slf4j
final class ClogIndex
{
	private static final int ENUM_CLOG_TABS = 2102;
	private static final int PARAM_SUBTAB_ENUM = 683;
	private static final int PARAM_CATEGORY_NAME = 689;
	private static final int PARAM_CATEGORY_ITEMS = 690;

	private Map<String, List<Integer>> categoryItems = Collections.emptyMap();
	private Map<Integer, List<String>> itemCategoryKeys = Collections.emptyMap();
	private Map<String, List<Integer>> itemNameIds = Collections.emptyMap();
	private boolean parsed;

	boolean ensureParsed(Client client, ItemManager itemManager)
	{
		if (parsed)
		{
			return true;
		}

		try
		{
			Map<String, List<Integer>> nextCategoryItems = new HashMap<>();
			Map<Integer, List<String>> nextItemCategoryKeys = new HashMap<>();
			Map<String, List<Integer>> nextItemNameIds = new HashMap<>();

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
						indexItemName(nextItemNameIds, itemManager, itemId);
					}
					nextCategoryItems.put(categoryKey, itemIds);
				}
			}

			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds,
				itemManager, "mimic", new int[]{PanelData.THIRD_AGE_RING_ITEM_ID});
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds,
				itemManager, PanelData.CLOG_THIRD_AGE, PanelData.THIRD_AGE_ITEMS);
			injectSynthetic(nextCategoryItems, nextItemCategoryKeys, nextItemNameIds,
				itemManager, PanelData.CLOG_GILDED, PanelData.GILDED_ITEMS);

			categoryItems = nextCategoryItems;
			itemCategoryKeys = nextItemCategoryKeys;
			itemNameIds = nextItemNameIds;
			parsed = true;
			log.debug("Parsed clog enums: {} categories, {} items, {} names",
				categoryItems.size(), itemCategoryKeys.size(), itemNameIds.size());
			return true;
		}
		catch (Exception e)
		{
			log.warn("Failed to parse clog enums", e);
			clear();
			return false;
		}
	}

	void clear()
	{
		categoryItems = Collections.emptyMap();
		itemCategoryKeys = Collections.emptyMap();
		itemNameIds = Collections.emptyMap();
		parsed = false;
	}

	boolean isParsed()
	{
		return parsed;
	}

	List<Integer> itemIdsForName(String itemKey)
	{
		return itemNameIds.get(itemKey);
	}

	List<String> categoryKeysForItem(int itemId)
	{
		return itemCategoryKeys.get(itemId);
	}

	Set<String> categoryKeys()
	{
		return categoryItems.keySet();
	}

	Map<String, List<Integer>> categoryItems()
	{
		return categoryItems;
	}

	Map<String, List<Integer>> copyCategoryItems()
	{
		Map<String, List<Integer>> copy = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : categoryItems.entrySet())
		{
			copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return copy;
	}

	int categoryCount()
	{
		return categoryItems.size();
	}

	private void injectSynthetic(Map<String, List<Integer>> categoryItems,
		Map<Integer, List<String>> itemCategoryKeys, Map<String, List<Integer>> itemNameIds,
		ItemManager itemManager, String key, int[] itemIds)
	{
		List<Integer> ids = new ArrayList<>(itemIds.length);
		for (int id : itemIds)
		{
			ids.add(id);
			itemCategoryKeys.computeIfAbsent(id, k -> new ArrayList<>()).add(key);
			indexItemName(itemNameIds, itemManager, id);
		}
		categoryItems.put(key, ids);
	}

	private void indexItemName(Map<String, List<Integer>> itemNameIds, ItemManager itemManager, int itemId)
	{
		String itemName = itemManager.getItemComposition(itemId).getName();
		String itemKey = ClogUnlockParser.normalizeItemName(itemName);
		if (!itemKey.isEmpty() && !"null".equals(itemKey))
		{
			List<Integer> ids = itemNameIds.computeIfAbsent(itemKey, k -> new ArrayList<>());
			if (!ids.contains(itemId))
			{
				ids.add(itemId);
			}
		}
	}
}
