package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The game's Collection Log structure as this client reads it: the tabs in order, then each page's name,
 * key and items in the game's order. killclog.com counts every page and tab from it, and follows the
 * game through it: the server names the structure it holds by a hash in each sync reply, and a client
 * whose own structure hashes differently sends it with its next sync.
 */
final class ClogStructure
{
	private ClogStructure()
	{
	}

	/** {"tabs":[{name, pages:[{name, key, items}]}]}, or null before the client has read the log. */
	@Nullable
	static JsonObject of(ClogIndex index)
	{
		Map<String, List<String>> tabPages = index.tabPages();
		Map<String, List<Integer>> items = index.categoryItems();
		if (tabPages.isEmpty())
		{
			return null;
		}
		JsonArray tabs = new JsonArray();
		for (Map.Entry<String, List<String>> tab : tabPages.entrySet())
		{
			JsonArray pages = new JsonArray();
			for (String key : tab.getValue())
			{
				String name = index.pageName(key);
				List<Integer> ids = items.get(key);
				if (name == null || ids == null || ids.isEmpty())
				{
					return null;
				}
				JsonObject page = new JsonObject();
				page.addProperty("name", name);
				page.addProperty("key", key);
				JsonArray pageItems = new JsonArray();
				ids.forEach(pageItems::add);
				page.add("items", pageItems);
				pages.add(page);
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("name", tab.getKey());
			entry.add("pages", pages);
			tabs.add(entry);
		}
		JsonObject structure = new JsonObject();
		structure.add("tabs", tabs);
		return structure;
	}

	/** The server's name for a structure: the first 16 hex digits of the SHA-256 of its compact tabs JSON. */
	static String hash(Gson gson, JsonObject structure)
	{
		String json = gson.newBuilder().disableHtmlEscaping().create().toJson(structure.get("tabs"));
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
