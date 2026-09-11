package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

@Slf4j
final class ManualClogSync
{
	private static final int CLOG_ITEM_SCRIPT = 4100;
	private static final int FIRST_CAPTURE_TIMEOUT_TICKS = 100;

	private final BulkCaptureState bulk = new BulkCaptureState();
	private String openingPlayer;
	private int openingTick;

	void reset()
	{
		bulk.reset();
		openingPlayer = null;
	}

	void onGameTick(Client client, ClogIndex clogIndex, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier, ClogButtonOverlay clogButtonOverlay,
		Runnable firstSyncComplete, Consumer<String> panelRefresh)
	{
		int tickCount = client.getTickCount();
		if ((openingPlayer != null || bulk.isActive()) && isHostLog(client))
		{
			reset();
			return;
		}
		if (openingPlayer != null && tickCount > openingTick)
		{
			requestOpeningCapture(client, clogIndex, localClogCache, chatNotifier);
		}
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

	void captureScriptArguments(Client client, int scriptId, Object[] args, int tickCount)
	{
		if (scriptId != CLOG_ITEM_SCRIPT)
		{
			return;
		}
		if (isHostLog(client))
		{
			reset();
			return;
		}
		if (bulk.isActive())
		{
			bulk.captureScriptArguments(args, tickCount);
		}
	}

	void onCollectionLogOpened(Client client, LocalClogCache localClogCache)
	{
		if (isHostLog(client))
		{
			reset();
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null || bulk.isActive()
			|| localClogCache.hasCompletedFirstPartySetupFor(local.getName()))
		{
			return;
		}

		// WidgetLoaded precedes interface initialization. Request the full walk
		// on the next tick, once the interface is available.
		if (local.getName().equals(openingPlayer))
		{
			return;
		}
		openingPlayer = local.getName();
		openingTick = client.getTickCount();
	}

	private void requestOpeningCapture(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		String name = openingPlayer;
		openingPlayer = null;
		Player local = client.getLocalPlayer();
		Widget root = client.getWidget(KillClogPlugin.CLOG_INTERFACE, 0);
		if (bulk.isActive() || local == null || !name.equals(local.getName())
			|| root == null || root.isHidden() || localClogCache.hasCompletedFirstPartySetupFor(name))
		{
			return;
		}
		Widget search = client.getWidget(KillClogPlugin.CLOG_INTERFACE, 71);
		if (search == null || !search.isSelfHidden())
		{
			// Never toggle an already-open Search back or accept a partial walk.
			sendSearchHelp(chatNotifier);
			return;
		}
		beginFirstCapture(client, clogIndex, localClogCache, chatNotifier);
		if (!bulk.isActive())
		{
			return;
		}
		try
		{
			// This specific Collection Log sync use was approved by Riktenx:
			// https://github.com/runelite/plugin-hub/pull/12380#issuecomment-4773784134
			// Arm before requesting data; the native callback must not restart it.
			client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE,
				MenuAction.CC_OP, 1, -1, "Search", null);
			// Restore the normal view, as in RuneProfile's approved retrieval flow.
			client.runScript(2240);
		}
		catch (RuntimeException ex)
		{
			reset();
			log.warn("Could not request automatic Collection Log setup", ex);
			sendSearchHelp(chatNotifier);
		}
	}

	private static boolean isHostLog(Client client)
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0;
	}

	private static void sendSearchHelp(KillClogChatNotifier chatNotifier)
	{
		chatNotifier.send(ChatNotice.SYNC_HELP,
			"First Time Setup: Right-click the top of the Collection Log and choose Search.");
	}

	boolean onSyncClicked(Client client, ClogIndex clogIndex,
		VisibleClogCategoryReader visibleClogCategoryReader,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh)
	{
		if (isHostLog(client))
		{
			return false;
		}
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
			sendSearchHelp(chatNotifier);
			return false;
		}

		return captureVisibleCategory(client, visibleClogCategoryReader,
			localClogCache, chatNotifier, panelRefresh);
	}

	/**
	 * Search starts first-time capture before script 4100 streams the obtained
	 * entries. Automatic and manual requests share the same completeness checks.
	 */
	void onCollectionLogSearch(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		beginFirstCapture(client, clogIndex, localClogCache, chatNotifier);
	}

	private void beginFirstCapture(Client client, ClogIndex clogIndex,
		LocalClogCache localClogCache, KillClogChatNotifier chatNotifier)
	{
		if (isHostLog(client))
		{
			reset();
			return;
		}
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

		// A public provider cache or a live unlock alone does not complete setup.
		if (localClogCache.hasCompletedFirstPartySetupFor(local.getName()))
		{
			return;
		}
		// A manual request or another plugin's Search supersedes the queued open.
		openingPlayer = null;

		bulk.arm(client.getTickCount(),
			client.getVarpValue(ClogVarps.OBTAINED),
			client.getVarpValue(ClogVarps.TOTAL));
		// Leave room for unsettled counters and delayed item scripts, including
		// empty logs, which produce no item events at all.
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

		int mappedCount = 0;
		for (ClogResult.ClogItem item : bulk.obtained)
		{
			List<String> cats = clogIndex.categoryKeysForItem(item.getId());
			if (cats != null && !cats.isEmpty())
			{
				mappedCount++;
				for (String cat : cats)
				{
					obtainedByCategory.get(cat).add(item);
				}
			}
		}

		Map<String, List<Integer>> categoryItemsCopy = clogIndex.copyCategoryItems();

		// Guard against partial captures (e.g., player closed clog mid-sync).
		if (reportedCount < 0 || (reportedCount == 0 && !bulk.obtained.isEmpty())
			|| mappedCount < reportedCount || mappedCount != bulk.obtained.size())
		{
			log.warn("Bulk capture incomplete: {} mapped, {} streamed, {} reported; discarding",
				mappedCount, bulk.obtained.size(), reportedCount);
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

		// Prefer Jagex's logical-slot count; distinct item forms can share a slot.
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

	private boolean captureVisibleCategory(Client client,
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
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Open a Collection Log category to refresh its items.");
			return false;
		}

		String name = local.getName();
		List<Integer> categoryItems = category.allItemIds();
		List<ClogResult.ClogItem> obtained = category.obtained();
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
