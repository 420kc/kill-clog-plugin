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
	@Nullable private Object builtFrom;

	UnsyncedClogCatalog(ClogService clogService)
	{
		this.clogService = clogService;
	}

	void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		this.clogIndex = clogIndex;
		this.indexCatalog = null;
		this.builtFrom = null;
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
		if (index == null || index.generation() == null)
		{
			return null;
		}

		// The generation token names one parse. When it moves, copyCatalog()
		// captures categories and item names atomically from a single parse
		// (possibly newer than the token just read; the cache keys on the
		// generation the copy actually came from, so it stays coherent).
		if (indexCatalog == null || builtFrom != index.generation())
		{
			ClogIndex.CatalogCopy copy = index.copyCatalog();
			if (copy == null)
			{
				return null;
			}
			indexCatalog = new ClogResult(
				CATALOG_NAME,
				emptyObtained(copy.categoryItems),
				copy.categoryItems,
				copy.itemNames,
				null,
				null);
			builtFrom = copy.generation;
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
