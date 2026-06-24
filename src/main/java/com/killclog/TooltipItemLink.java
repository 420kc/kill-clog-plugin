package com.killclog;

import java.awt.FontMetrics;
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
	private static final String ELLIPSIS = "...";

	private TooltipItemLink()
	{
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

	static String fitRight(FontMetrics fm, String text, int maxWidth)
	{
		if (text == null || text.isEmpty() || fm.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		int ellipsisWidth = fm.stringWidth(ELLIPSIS);
		if (ellipsisWidth >= maxWidth)
		{
			return "";
		}
		StringBuilder out = new StringBuilder(text);
		while (out.length() > 0 && fm.stringWidth(out.toString()) + ellipsisWidth > maxWidth)
		{
			out.deleteCharAt(out.length() - 1);
		}
		return out + ELLIPSIS;
	}

	private static String encodePageName(String pageName)
	{
		if (pageName == null)
		{
			return "";
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
