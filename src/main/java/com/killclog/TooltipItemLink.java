package com.killclog;

import java.util.HashMap;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.runelite.client.util.LinkBrowser;

/** Wiki-link and display helpers for tooltip affordances. */
final class TooltipItemLink
{
	private static final String WIKI_ITEM_LOOKUP =
		"https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=";
	private static final String WIKI_PAGE =
		"https://oldschool.runescape.wiki/w/";
	private static final String FALLBACK_NAME_PREFIX = "Item ";

	// The chompy bird hats, from item 2978 up: the kills each takes and its rank (chompy-hats.tsv).
	private static final Map<Integer, String> CHOMPY_HATS = new HashMap<>();

	static
	{
		for (String[] row : CatalogTsv.rows(TooltipItemLink.class, "chompy-hats.tsv", 2))
		{
			CHOMPY_HATS.put(Integer.parseInt(row[0]), row[1]);
		}
	}

	static String displayName(int itemId, String name)
	{
		return CHOMPY_HATS.getOrDefault(itemId, name);
	}

	private TooltipItemLink()
	{
	}

	// A player's or a clan's page elsewhere (Dylan, 2026-10-17): the addresses killclog.com's badges open.
	static String templeUrl(String rsn)
	{
		return "https://templeosrs.com/player/overview.php?player=" + URLEncoder.encode(rsn, StandardCharsets.UTF_8);
	}

	static String runeProfileUrl(String rsn)
	{
		return "https://runeprofile.com/" + HttpUtil.pathSegment(rsn);
	}

	static String runeProfileClanUrl(String clan)
	{
		return "https://runeprofile.com/clan/" + HttpUtil.pathSegment(clan);
	}

	static String killclogUrl(String rsn)
	{
		return "https://killclog.com/p/" + HttpUtil.pathSegment(rsn.trim().replaceAll("\\s+", "-"));
	}

	static String wikiUrl(int itemId)
	{
		return WIKI_ITEM_LOOKUP + itemId;
	}

	static void openWiki(int itemId)
	{
		if (itemId > 0)
		{
			LinkBrowser.browse(wikiUrl(itemId));
		}
	}

	static String wikiPageUrl(String pageName)
	{
		return WIKI_PAGE + encodePageName(pageName);
	}

	static void openWikiPage(String pageName)
	{
		if (pageName != null && !pageName.trim().isEmpty())
		{
			LinkBrowser.browse(wikiPageUrl(pageName));
		}
	}

	static String itemName(Map<Integer, String> itemNames, int itemId)
	{
		String hatName = displayName(itemId, null);
		if (hatName != null) return hatName;
		if (itemNames == null)
		{
			return fallbackName(itemId);
		}
		String name = itemNames.get(itemId);
		if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name))
		{
			return fallbackName(itemId);
		}
		return name;
	}

	/** A page name, and after a '#' the section on it, as the wiki's URLs write them. */
	private static String encodePageName(String pageName)
	{
		if (pageName == null)
		{
			return "";
		}
		int section = pageName.indexOf('#');
		if (section >= 0)
		{
			return encodePageName(pageName.substring(0, section)) + "#" + encodePageName(pageName.substring(section + 1));
		}
		String normalized = pageName.trim().replace(' ', '_');
		String[] parts = normalized.split("/", -1);
		for (int i = 0; i < parts.length; i++)
		{
			parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8)
				.replace("+", "_");
		}
		return String.join("/", parts);
	}

	private static String fallbackName(int itemId)
	{
		return FALLBACK_NAME_PREFIX + itemId;
	}
}
