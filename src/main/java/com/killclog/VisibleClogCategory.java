package com.killclog;

import java.util.List;

final class VisibleClogCategory
{
	private final String key;
	private final String name;
	private final List<Integer> allItemIds;
	private final List<ClogResult.ClogItem> obtained;
	private final String fastestTime;

	VisibleClogCategory(String key, String name, List<Integer> allItemIds,
		List<ClogResult.ClogItem> obtained, String fastestTime)
	{
		this.key = key;
		this.name = name;
		this.allItemIds = allItemIds;
		this.obtained = obtained;
		this.fastestTime = fastestTime;
	}

	String key()
	{
		return key;
	}

	String name()
	{
		return name;
	}

	List<Integer> allItemIds()
	{
		return allItemIds;
	}

	List<ClogResult.ClogItem> obtained()
	{
		return obtained;
	}

	/** The page's "Fastest ..." header time, or null when the page has none. */
	String fastestTime()
	{
		return fastestTime;
	}
}
