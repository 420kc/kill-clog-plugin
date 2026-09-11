package com.killclog;

import java.util.ArrayList;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class VisibleClogCategoryReader
{
	private static final int CLOG_HEADER_CHILD = 20;
	private static final int CLOG_ITEMS_CHILD = 37;

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
			if (child == null)
			{
				continue;
			}
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

		return Optional.of(new VisibleClogCategory(categoryKey, categoryName, allItemIds, obtained));
	}
}
