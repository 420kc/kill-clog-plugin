package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import javax.swing.SwingUtilities;

/**
 * Names for the items a log shows that the item cache hasn't named (untradeables), read from the game in batches of 48
 * a tick, one run per log at a time; the cards repaint once when they're in.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class TooltipItemNameResolver
{
	private static final int BATCH_SIZE = 48;

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final Runnable onComplete;
	private final Set<ClogResult> inFlight = Collections.synchronizedSet(
		Collections.newSetFromMap(new IdentityHashMap<>()));

	void resolve(ClogResult result)
	{
		Set<Integer> allIds = new HashSet<>();
		for (List<ClogResult.ClogItem> items : result.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items)
			{
				allIds.add(item.getId());
			}
		}
		for (List<Integer> ids : result.getCategoryItems().values())
		{
			allIds.addAll(ids);
		}

		List<Integer> missing = new ArrayList<>();
		for (int id : allIds)
		{
			if (!result.isItemResolved(id))
			{
				missing.add(id);
			}
		}
		if (missing.isEmpty() || !inFlight.add(result))
		{
			return;
		}

		log.debug("Resolving {} untradeable item names via game cache", missing.size());
		resolveBatch(result, missing, 0);
	}

	private void resolveBatch(ClogResult result, List<Integer> missing, int start)
	{
		clientThread.invokeLater(() ->
		{
			int end = Math.min(start + BATCH_SIZE, missing.size());
			for (int i = start; i < end; i++)
			{
				try
				{
					int id = missing.get(i);
					String name = itemManager.getItemComposition(id).getName();
					if (name != null && !name.isEmpty() && !name.equals("null") && !name.equals("Null"))
					{
						result.markItemResolved(id, name);
					}
				}
				catch (Exception e)
				{
					// Item not in cache - skip.
				}
			}
			if (end < missing.size())
			{
				resolveBatch(result, missing, end);
				return;
			}
			inFlight.remove(result);
			SwingUtilities.invokeLater(onComplete);
		});
	}
}
