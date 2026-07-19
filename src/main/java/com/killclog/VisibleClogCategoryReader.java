package com.killclog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class VisibleClogCategoryReader
{
	private static final int CLOG_HEADER_CHILD = 20;
	private static final int CLOG_ITEMS_CHILD = 37;
	private static final Pattern OBTAINED_PATTERN = Pattern.compile("(\\d+)/(\\d+)");

	Optional<VisibleClogCategory> read(Client client)
	{
		// 621:20 getDynamicChildren(): [0]=category name, [1]="Obtained: x/y", [2]="Boss kills: n"
		Widget header = client.getWidget(KillClogPlugin.CLOG_INTERFACE, CLOG_HEADER_CHILD);
		if (header == null)
		{
			return Optional.empty();
		}
		Widget[] headerKids = header.getDynamicChildren();
		if (headerKids == null || headerKids.length < 2)
		{
			return Optional.empty();
		}

		String headerText = (headerKids[0] != null) ? headerKids[0].getText() : null;
		if (headerText == null || headerText.isEmpty())
		{
			return Optional.empty();
		}

		String categoryName = Text.removeTags(headerText);
		String categoryKey = ClogService.bossToCategory(categoryName);
		int obtainedCount = parseObtainedCount(headerKids);

		Widget items = client.getWidget(KillClogPlugin.CLOG_INTERFACE, CLOG_ITEMS_CHILD);
		if (items == null)
		{
			return Optional.empty();
		}

		Widget[] children = items.getChildren();
		if (children == null || children.length == 0)
		{
			return Optional.empty();
		}

		ArrayList<Integer> allItemIds = new ArrayList<>();
		ArrayList<ClogResult.ClogItem> obtained = new ArrayList<>();
		for (Widget child : children)
		{
			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}
			allItemIds.add(itemId);
			if (child.getOpacity() == 0)
			{
				int qty = child.getItemQuantity();
				obtained.add(new ClogResult.ClogItem(itemId, Math.max(qty, 1), null));
			}
		}

		if (allItemIds.isEmpty())
		{
			return Optional.empty();
		}

		inferHiddenUntradeables(obtainedCount, allItemIds, obtained);
		return Optional.of(new VisibleClogCategory(categoryKey, categoryName, allItemIds, obtained));
	}

	private static int parseObtainedCount(Widget[] headerKids)
	{
		if (headerKids.length < 2 || headerKids[1] == null)
		{
			return -1;
		}

		String obtainedText = Text.removeTags(headerKids[1].getText());
		if (obtainedText == null)
		{
			return -1;
		}

		Matcher matcher = OBTAINED_PATTERN.matcher(obtainedText);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
	}

	private static void inferHiddenUntradeables(int obtainedCount,
		ArrayList<Integer> allItemIds, ArrayList<ClogResult.ClogItem> obtained)
	{
		// If the header count exceeds visible obtained sprites, the missing ones are
		// untradeables that still exist in the category widget but render hidden.
		if (obtainedCount <= obtained.size() || obtainedCount > allItemIds.size())
		{
			return;
		}

		Set<Integer> obtainedIds = new HashSet<>();
		for (ClogResult.ClogItem item : obtained)
		{
			obtainedIds.add(item.getId());
		}

		int missing = obtainedCount - obtained.size();
		for (int itemId : allItemIds)
		{
			if (!obtainedIds.contains(itemId))
			{
				obtained.add(new ClogResult.ClogItem(itemId, 1, null));
				obtainedIds.add(itemId);
				missing--;
				if (missing <= 0)
				{
					break;
				}
			}
		}
	}
}
