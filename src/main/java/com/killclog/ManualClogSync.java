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
	private static final int FIRST_CAPTURE_TIMEOUT_TICKS = 100;

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
		bulk.deferEmptyFinalizationIfItemsReported(
			client.getVarpValue(ClogVarps.OBTAINED));
		if (bulk.readyToFinalize(tickCount))
		{
			finalizeBulkCapture(client, clogIndex, localClogCache,
				chatNotifier, clogButtonOverlay, firstSyncComplete, panelRefresh);
		}
		else if (bulk.timedOut(tickCount, FIRST_CAPTURE_TIMEOUT_TICKS))
		{
			reset();
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Setup timed out. Open the Collection Log and choose Search again.");
		}
	}

	void captureScriptArguments(int scriptId, Object[] args, int tickCount)
	{
		if (scriptId == CLOG_ITEM_SCRIPT && bulk.isActive())
		{
			bulk.captureScriptArguments(args, tickCount);
		}
	}

	void onCollectionLogOpened(Client client, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null || bulk.isActive()
			|| localClogCache.hasCompletedFirstPartySetupFor(local.getName()))
		{
			return;
		}

		chatNotifier.send(ChatNotice.SYNC_HELP,
			"First Time Setup: Right-click the top of the Collection Log and choose Search.");
	}

	boolean onSyncClicked(Client client, ClogIndex clogIndex,
		VisibleClogCategoryReader visibleClogCategoryReader,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return false;
		}

		if (!localClogCache.hasCompletedFirstPartySetupFor(local.getName()))
		{
			// A visible page can run the same item script as a full Search walk.
			// Do not arm setup here: doing so could mistake one page for the
			// player's complete log. Search is the explicit full-catalog signal.
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"First Time Setup: Right-click the top of the Collection Log and choose Search.");
			return false;
		}

		return captureVisibleCategory(client, clogIndex, visibleClogCategoryReader,
			localClogCache, chatNotifier, panelRefresh);
	}

	/**
	 * The game's Collection Log Search menu action is the real start of a
	 * first-time capture. This runs before script 4100 streams the obtained
	 * entries, so no separate chalice click (or Back action) is required.
	 */
	void onCollectionLogSearch(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		beginFirstCapture(client, clogIndex, localClogCache, chatNotifier);
	}

	private void beginFirstCapture(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		if (bulk.isActive())
		{
			return;
		}

		if (!clogIndex.isParsed())
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Collection Log is still loading - choose Search again in a moment.");
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		// Search is needed until a full first-party walk has completed. A public
		// provider cache or a live unlock alone does not complete local setup.
		if (localClogCache.hasCompletedFirstPartySetupFor(local.getName()))
		{
			return;
		}

		bulk.arm(client.getTickCount(),
			client.getVarpValue(ClogVarps.OBTAINED),
			client.getVarpValue(ClogVarps.TOTAL));
		// A genuinely empty account produces no item scripts. Search itself is
		// the completion signal; the total varp is also zero on a fresh account.
		// Any item script moves this deadline to three ticks after that stream.
		bulk.scheduleEmptySearchFinalization(client.getTickCount());

		chatNotifier.send(ChatNotice.SYNC_HELP,
			"Kill Clog is reading your Collection Log for First Time Setup...");

		log.debug("Armed first-time clog capture from Collection Log Search "
			+ "(game reports {} obtained)", bulk.clogCount);
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
		if (!localClogCache.setActivePlayer(name))
		{
			reset();
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Local account identity is still settling - reopen the collection log in a moment.");
			return;
		}

		// The collection-log counters can settle after the Search menu click.
		// Re-read them before accepting the stream so an early zero cannot make
		// a partial walk look like a valid empty or one-item first capture.
		int reportedCount = Math.max(bulk.clogCount,
			client.getVarpValue(ClogVarps.OBTAINED));
		int reportedTotal = Math.max(bulk.clogTotal,
			client.getVarpValue(ClogVarps.TOTAL));

		// Group obtained items by category.
		Map<String, List<ClogResult.ClogItem>> obtainedByCategory = new HashMap<>();

		// Include empty categories so cacheResult stores the full catalog.
		for (String cat : clogIndex.categoryKeys())
		{
			obtainedByCategory.put(cat, new ArrayList<>());
		}

		for (ClogResult.ClogItem item : clogIndex.canonicalizeItems(bulk.obtained))
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
		if (reportedCount > 0 && bulk.obtained.size() * 2 < reportedCount)
		{
			log.warn("Bulk capture incomplete: got {} of {} items, discarding",
				bulk.obtained.size(), reportedCount);
			reset();
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Sync interrupted - open the collection log and try again.");
			return;
		}

		ClogResult result = new ClogResult(name, obtainedByCategory, categoryItemsCopy,
			new HashMap<>(), null, null);
		if (reportedCount > 0)
		{
			result.setUniqueObtained(reportedCount);
		}
		if (reportedTotal > 0)
		{
			result.setUniqueTotal(reportedTotal);
		}
		localClogCache.cacheFirstPartyResult(result);
		firstSyncComplete.run();

		// Prefer the Jagex de-duped count (varp 2943) over the raw streamed count.
		// Some items appear in multiple clog categories, so bulk.obtained.size() can
		// exceed the player's actual unique obtained count.
		int displayCount = reportedCount > 0 ? reportedCount : bulk.obtained.size();
		log.debug("Bulk clog capture complete: {} items across {} categories for '{}' ({} streamed)",
			displayCount, clogIndex.categoryCount(), name, bulk.obtained.size());

		// Setup guidance and confirmation are always visible, even when routine
		// sync-result chat messages are disabled in config.
		chatNotifier.send(ChatNotice.SYNC_HELP,
			"First Time Setup complete - " + displayCount + " items saved to Kill Clog.");

		reset();

		clogButtonOverlay.flashGreen();
		SwingUtilities.invokeLater(() -> panelRefresh.accept(name));
	}

	private boolean captureVisibleCategory(Client client, ClogIndex clogIndex,
		VisibleClogCategoryReader visibleClogCategoryReader,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return false;
		}

		VisibleClogCategory category = visibleClogCategoryReader.read(client).orElse(null);
		if (category == null)
		{
			return false;
		}

		String name = local.getName();
		List<Integer> categoryItems = clogIndex.canonicalizeItemIds(category.allItemIds());
		List<ClogResult.ClogItem> obtained = clogIndex.canonicalizeItems(category.obtained());
		localClogCache.mergeCategory(name, category.key(), categoryItems, obtained);

		// Re-read global clog totals from live varps (catches game updates + new items).
		int liveObtained = client.getVarpValue(ClogVarps.OBTAINED);
		int liveTotal = client.getVarpValue(ClogVarps.TOTAL);
		if (liveObtained > 0 || liveTotal > 0)
		{
			localClogCache.updateTotals(name, liveObtained, liveTotal);
		}

		chatNotifier.send(ChatNotice.SYNC_RESULT,
			"Captured " + category.name() + " - " + obtained.size()
				+ "/" + categoryItems.size() + " obtained");
		SwingUtilities.invokeLater(() -> panelRefresh.accept(name));
		return true;
	}
}
