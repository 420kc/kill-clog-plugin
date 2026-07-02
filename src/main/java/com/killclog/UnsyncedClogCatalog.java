package com.killclog;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Shared empty collection-log catalog for players without synced clog data.
 * Prefers the parsed in-game clog index so unsynced popups use the same item
 * ids and item names as local/synced tooltip data, with provider categories as
 * a startup fallback.
 */
final class UnsyncedClogCatalog
{
	private static final String CATALOG_NAME = "Collection Log";

	private final ClogService clogService;
	private Consumer<ClogResult> resolver = UnsyncedClogCatalog::noop;
	@Nullable private ClogIndex clogIndex;
	@Nullable private ClogResult indexCatalog;
	@Nullable private ClogResult fallbackCatalog;
	private int indexCategoryCount = -1;

	UnsyncedClogCatalog(ClogService clogService)
	{
		this.clogService = clogService;
	}

	void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		this.clogIndex = clogIndex;
		this.indexCatalog = null;
		this.indexCategoryCount = -1;
	}

	void setResolver(@Nullable Consumer<ClogResult> resolver)
	{
		this.resolver = resolver != null ? resolver : UnsyncedClogCatalog::noop;
	}

	@Nullable
	ClogResult result()
	{
		ClogResult catalog = indexCatalog();
		if (catalog == null)
		{
			catalog = fallbackCatalog();
		}
		if (catalog != null)
		{
			resolver.accept(catalog);
		}
		return catalog;
	}

	@Nullable
	private ClogResult indexCatalog()
	{
		ClogIndex index = clogIndex;
		if (index == null || !index.isParsed())
		{
			return null;
		}

		int categoryCount = index.categoryCount();
		if (indexCatalog == null || indexCategoryCount != categoryCount)
		{
			Map<String, List<Integer>> categories = index.copyCategoryItems();
			indexCatalog = new ClogResult(
				CATALOG_NAME,
				emptyObtained(categories),
				categories,
				index.copyItemNames(),
				null,
				null);
			indexCategoryCount = categoryCount;
		}
		return indexCatalog;
	}

	@Nullable
	private ClogResult fallbackCatalog()
	{
		if (fallbackCatalog == null)
		{
			fallbackCatalog = clogService.getCatalogResult(CATALOG_NAME);
		}
		return fallbackCatalog;
	}

	private static Map<String, List<ClogResult.ClogItem>> emptyObtained(
		Map<String, List<Integer>> categories)
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		for (String category : categories.keySet())
		{
			obtained.put(category, Collections.emptyList());
		}
		return obtained;
	}

	private static void noop(ClogResult result)
	{
	}
}
