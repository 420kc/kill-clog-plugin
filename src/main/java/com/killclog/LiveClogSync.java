package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.game.ItemManager;

@Slf4j
final class LiveClogSync
{
	// A kill message within this many ticks of the unlock message is treated
	// as its cause; beyond it the unlock stays provenance-less rather than
	// guessing (skilling clogs, chest opens, and pet drops have no kc).
	private static final int KILL_CONTEXT_MAX_TICKS = 5;

	private boolean needsFirstSyncWarned;
	private ClogUnlockParser.KillContext lastKill;
	private int lastKillTick = -1;

	void rememberKill(ClogUnlockParser.KillContext kill, int tick)
	{
		lastKill = kill;
		lastKillTick = tick;
	}

	void handleUnlock(String itemName, int broadcastObtained, int broadcastTotal,
		Client client, ItemManager itemManager,
		ClogIndex clogIndex, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier, ClogButtonOverlay clogButtonOverlay,
		Consumer<String> panelRefresh)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		if (!clogIndex.ensureParsed(client, itemManager))
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Live sync could not read collection-log data. Click the chalice to sync.");
			return;
		}

		String playerName = local.getName();
		if (!localClogCache.setActivePlayer(playerName))
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Live sync is waiting for local account identity. Click the chalice to sync.");
			return;
		}
		if (!localClogCache.hasDataFor(playerName))
		{
			warnFirstSync(chatNotifier);
			return;
		}

		List<Integer> missingItemIds = missingItemIds(itemName, playerName, clogIndex, localClogCache, chatNotifier);
		if (missingItemIds == null)
		{
			return;
		}

		// Attach the causing kill when one landed just before the unlock.
		ClogUnlockParser.KillContext kill = null;
		if (lastKill != null && lastKillTick >= 0
			&& client.getTickCount() - lastKillTick <= KILL_CONTEXT_MAX_TICKS)
		{
			kill = lastKill;
		}

		boolean changed = false;
		for (int itemId : missingItemIds)
		{
			changed |= localClogCache.mergeObtainedItem(playerName, itemId,
				clogIndex.categoryKeysForItem(itemId), clogIndex.categoryItems(),
				kill != null ? kill.kc : 0, kill != null ? kill.boss : null);
		}

		// Upward-only here: the varp can lag the unlock message by a tick, and
		// a stale read would revert the unique bump the merge just made.
		// Chalice sync remains the downward authority. A clan broadcast
		// carries its own fresh counts; take whichever signal is highest.
		int liveObtained = Math.max(client.getVarpValue(ClogVarps.OBTAINED), broadcastObtained);
		int liveTotal = Math.max(client.getVarpValue(ClogVarps.TOTAL), broadcastTotal);
		if (liveObtained > 0 || liveTotal > 0)
		{
			localClogCache.updateTotalsUpward(playerName, liveObtained, liveTotal);
		}

		if (changed)
		{
			resetFirstSyncWarning();
			clogButtonOverlay.flashGreen();
			chatNotifier.send(ChatNotice.NEW_DROP,
				"Added " + itemName + " to Kill Clog");
			SwingUtilities.invokeLater(() -> panelRefresh.accept(playerName));
		}
	}

	void resetFirstSyncWarning()
	{
		needsFirstSyncWarned = false;
	}

	private void warnFirstSync(KillClogChatNotifier chatNotifier)
	{
		if (!needsFirstSyncWarned)
		{
			needsFirstSyncWarned = true;
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Open the Collection Log, right-click the top, and choose Search to set up Kill Clog.");
		}
	}

	private List<Integer> missingItemIds(String itemName, String playerName,
		ClogIndex clogIndex, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier)
	{
		String itemKey = ClogUnlockParser.normalizeItemName(itemName);
		List<Integer> itemIds = clogIndex.itemIdsForName(itemKey);
		if (itemIds == null || itemIds.isEmpty())
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Could not match " + itemName + ". Click the chalice to sync.");
			log.debug("Live clog sync could not match item '{}'", itemName);
			return null;
		}

		List<Integer> missingItemIds = new ArrayList<>();
		for (int itemId : itemIds)
		{
			if (!localClogCache.hasObtainedItem(playerName, itemId, clogIndex.categoryKeysForItem(itemId)))
			{
				missingItemIds.add(itemId);
			}
		}
		if (missingItemIds.size() > 1)
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Could not match " + itemName + " exactly. Click the chalice to sync.");
			log.debug("Live clog sync found ambiguous item '{}' ({})", itemName, missingItemIds);
			return null;
		}
		return missingItemIds;
	}
}
