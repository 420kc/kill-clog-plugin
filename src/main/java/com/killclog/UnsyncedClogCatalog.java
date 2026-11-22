package com.killclog;

import java.util.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.*;

/**
 * The empty log shown for a player with none synced, shared by every such card. It is built from the game's own catalog
 * once read, so those cards use the same item ids and names as a synced log's; the site's page list stands in before
 * then.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class UnsyncedClogCatalog
{
	private static final String CATALOG_NAME = "Collection Log";

	private final ClogService clogService;
	private Consumer<ClogResult> resolver = UnsyncedClogCatalog::noop;
	@Nullable private ClogIndex clogIndex;
	@Nullable private ClogResult indexCatalog;
	@Nullable private ClogResult fallbackCatalog;
	@Nullable private Object builtFrom;

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

		// generation() names one reading. When it moves, copyCatalog() takes pages and item names from a single
		// reading, which may be newer than the one just named; the copy is kept under the reading it really came from,
		// so the two always match.
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
