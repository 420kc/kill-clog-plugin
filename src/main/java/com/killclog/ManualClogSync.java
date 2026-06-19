package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

@Slf4j
final class ManualClogSync
{
	private static final int CLOG_ITEM_SCRIPT = 4100;
	private static final int MANUAL_BULK_TIMEOUT_TICKS = 100;

	private final BulkCaptureState bulk = new BulkCaptureState();

	void reset()
	{
		bulk.reset();
	}

	void onGameTick(Client client, ClogIndex clogIndex, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier, ClogButtonOverlay clogButtonOverlay,
		Runnable firstSyncComplete, Consumer<String> panelRefresh)
	{
		int tickCount = client.getTickCount();
		if (bulk.readyToFinalize(tickCount))
		{
			finalizeBulkCapture(client, clogIndex, localClogCache,
				chatNotifier, clogButtonOverlay, firstSyncComplete, panelRefresh);
		}
		else if (bulk.timedOut(tickCount, MANUAL_BULK_TIMEOUT_TICKS))
		{
			reset();
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Sync timed out. Click the chalice, then Search and Back.");
		}
	}

	void captureScriptArguments(int scriptId, Object[] args, int tickCount)
	{
		if (scriptId == CLOG_ITEM_SCRIPT && bulk.isActive())
		{
			bulk.captureScriptArguments(args, tickCount);
		}
	}

	void onSyncClicked(Client client, ClogIndex clogIndex,
		VisibleClogCategoryReader visibleClogCategoryReader,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		captureVisibleCategory(client, visibleClogCategoryReader,
			localClogCache, chatNotifier, panelRefresh);

		if (!localClogCache.hasDataFor(local.getName()))
		{
			beginManualBulkCapture(client, clogIndex, localClogCache, chatNotifier);
		}
	}

	private void beginManualBulkCapture(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		if (bulk.isActive())
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Sync is armed. Click Search, then Back.");
			return;
		}

		if (!clogIndex.isParsed())
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		// Skip if local cache already exists for this player.
		if (localClogCache.hasDataFor(local.getName()))
		{
			return;
		}

		bulk.arm(client.getTickCount(),
			client.getVarpValue(ClogVarps.OBTAINED),
			client.getVarpValue(ClogVarps.TOTAL));

		chatNotifier.send(ChatNotice.SYNC_HELP,
			"First sync armed. Click Search, then Back.");

		log.debug("Armed manual bulk clog capture (game reports {} obtained)", bulk.clogCount);
	}

	private void finalizeBulkCapture(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		ClogButtonOverlay clogButtonOverlay, Runnable firstSyncComplete,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			reset();
			return;
		}

		String name = local.getName();
		localClogCache.setActivePlayer(name);

		// Group obtained items by category.
		Map<String, List<ClogResult.ClogItem>> obtainedByCategory = new HashMap<>();

		// Include empty categories so cacheResult stores the full catalog.
		for (String cat : clogIndex.categoryKeys())
		{
			obtainedByCategory.put(cat, new ArrayList<>());
		}

		for (ClogResult.ClogItem item : bulk.obtained)
		{
			List<String> cats = clogIndex.categoryKeysForItem(item.getId());
			if (cats != null)
			{
				for (String cat : cats)
				{
					obtainedByCategory.get(cat).add(item);
				}
			}
		}

		Map<String, List<Integer>> categoryItemsCopy = clogIndex.copyCategoryItems();

		// Guard against partial captures (e.g., player closed clog mid-sync).
		if (bulk.clogCount > 0 && bulk.obtained.size() < bulk.clogCount / 2)
		{
			log.warn("Bulk capture incomplete: got {} of {} items, discarding",
				bulk.obtained.size(), bulk.clogCount);
			reset();
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Sync interrupted - open the collection log and try again.");
			return;
		}

		ClogResult result = new ClogResult(name, obtainedByCategory, categoryItemsCopy,
			new HashMap<>(), null, null);
		if (bulk.clogCount > 0)
		{
			result.setUniqueObtained(bulk.clogCount);
		}
		if (bulk.clogTotal > 0)
		{
			result.setUniqueTotal(bulk.clogTotal);
		}
		localClogCache.cacheResult(result);
		firstSyncComplete.run();

		// Prefer the Jagex de-duped count (varp 2943) over the raw streamed count.
		// Some items appear in multiple clog categories, so bulk.obtained.size() can
		// exceed the player's actual unique obtained count.
		int displayCount = bulk.clogCount > 0 ? bulk.clogCount : bulk.obtained.size();
		log.debug("Bulk clog capture complete: {} items across {} categories for '{}' ({} streamed)",
			displayCount, clogIndex.categoryCount(), name, bulk.obtained.size());

		chatNotifier.send(ChatNotice.SYNC_RESULT,
			displayCount + " items synced to Kill Clog");

		// Merge the buffered category before reset (captured when clog first opened).
		String bufKey = bulk.bufferedCategoryKey;
		String bufName = bulk.bufferedCategoryName;
		List<Integer> bufItems = bulk.bufferedCategoryItems;
		List<ClogResult.ClogItem> bufObtained = bulk.bufferedCategoryObtained;
		reset();

		if (bufKey != null && bufItems != null)
		{
			localClogCache.mergeCategory(name, bufKey, bufItems, bufObtained);
			int buffObt = bufObtained != null ? bufObtained.size() : 0;
			int buffTotal = bufItems.size();
			log.debug("Merged buffered category '{}': {}/{} obtained",
				bufKey, buffObt, buffTotal);

			String displayName = bufName != null ? bufName : bufKey;
			chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Updated " + displayName + " - " + buffObt + "/" + buffTotal + " items");
		}

		clogButtonOverlay.flashGreen();
		SwingUtilities.invokeLater(() -> panelRefresh.accept(name));
	}

	private void captureVisibleCategory(Client client,
		VisibleClogCategoryReader visibleClogCategoryReader,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		VisibleClogCategory category = visibleClogCategoryReader.read(client).orElse(null);
		if (category == null)
		{
			return;
		}

		String name = local.getName();
		if (localClogCache.hasDataFor(name))
		{
			localClogCache.mergeCategory(name, category.key(), category.allItemIds(), category.obtained());

			// Re-read global clog totals from live varps (catches game updates + new items).
			int liveObtained = client.getVarpValue(ClogVarps.OBTAINED);
			int liveTotal = client.getVarpValue(ClogVarps.TOTAL);
			if (liveObtained > 0 || liveTotal > 0)
			{
				localClogCache.updateTotals(name, liveObtained, liveTotal);
			}

			chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Captured " + category.name() + " - " + category.obtained().size()
					+ "/" + category.allItemIds().size() + " obtained");
			SwingUtilities.invokeLater(() -> panelRefresh.accept(name));
		}
		else
		{
			bulk.bufferedCategoryKey = category.key();
			bulk.bufferedCategoryName = category.name();
			bulk.bufferedCategoryItems = new ArrayList<>(category.allItemIds());
			bulk.bufferedCategoryObtained = new ArrayList<>(category.obtained());

			chatNotifier.send(ChatNotice.SYNC_RESULT,
				"Buffered " + category.name() + " - " + category.obtained().size()
					+ "/" + category.allItemIds().size() + " obtained (will merge after sync)");
		}
	}
}
