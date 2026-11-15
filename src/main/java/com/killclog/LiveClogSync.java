package com.killclog;

import java.util.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

/**
 * Live unlocks between walks: a "New item added" line, or the clan broadcast, adds that one item to your own log,
 * dated, with the kill that caused it; a name that matches two missing items is remembered for the next walk to
 * settle; totals only rise. Client thread.
 *
 * The kill is the game's word where it gives one, never a clock (Dylan, 2026-11-15). The server credits each loot to
 * the NPC that gave it as you receive it (RuneLite's ServerNpcLoot, the only source its own Loot Tracker reads NPC loot
 * from), so an item takes that NPC's latest kill however long the loot waited on its corpse, and none when the NPC has
 * no kill (a plain smoke devil's Occult necklace, on Thermy's page too). A pet, a chest or a reward screen is credited to
 * nobody, and a Collection Log item comes only from its own content: it takes the latest kill of the boss whose page
 * holds it, else the count message it arrived with (the Barrows chest's, whose name isn't its page's).
 */
@Slf4j
final class LiveClogSync
{
	// A count message this many ticks or fewer before lands with its loot: a chest's reward, or loot credited to an NPC
	// named apart from its kill message (Grotesque Guardians' Dusk). Never a wait.
	private static final int WITH_THE_KILL = 5;
	private static final Kill NO_KILL = new Kill(null, -1);

	private boolean needsFirstSyncWarned;
	// Each boss's latest kill this login by name, the latest of all, and each credited item's kill until it unlocks.
	private final Map<String, Kill> kills = new HashMap<>();
	@Nullable
	private Kill last;
	private final Map<String, Kill> credits = new HashMap<>();

	@RequiredArgsConstructor
	private static final class Kill
	{
		@Nullable
		final ClogUnlockParser.KillContext context;
		final int tick;
	}

	void rememberKill(ClogUnlockParser.KillContext kill, int tick)
	{
		last = new Kill(kill, tick);
		kills.put(name(kill.boss), last);
	}

	/**
	 * The game credited loot to an NPC: each item keeps that NPC's latest kill, or the kill the credit landed with when
	 * the two are named apart, until the item unlocks; none when neither (a plain monster).
	 */
	void credit(String npc, Collection<String> items, int tick)
	{
		Kill kill = kills.get(name(npc));
		if (kill == null && last != null && tick - last.tick <= WITH_THE_KILL)
		{
			kill = last;
		}
		for (String item : items)
		{
			credits.put(ClogUnlockParser.normalizeItemName(item), kill != null ? kill : NO_KILL);
		}
	}

	void handleUnlock(String itemName, int broadcastObtained, int broadcastTotal,
		Client client, ItemManager itemManager,
		ClogIndex clogIndex, LocalClogCache localClogCache,
		KillClogChatNotifier chatNotifier,
		Consumer<String> panelRefresh, boolean countersSettled)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		if (!clogIndex.ensureParsed(client, itemManager))
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Kill Clog could not read your Collection Log. Open it to update it.");
			return;
		}

		String playerName = local.getName();
		if (!localClogCache.serving(playerName))
		{
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Kill Clog is still checking this account. Open your Collection Log to update it.");
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

		// The kill the game credited the item to, else its own page's (the class note).
		Kill credited = credits.remove(ClogUnlockParser.normalizeItemName(itemName));
		boolean changed = false;
		for (int itemId : missingItemIds)
		{
			List<String> pages = clogIndex.categoryKeysForItem(itemId);
			ClogUnlockParser.KillContext kill = credited != null ? credited.context
				: uncreditedKill(pages, clogIndex, client.getTickCount());
			changed |= localClogCache.mergeObtainedItem(playerName, itemId, pages, clogIndex.categoryItems(),
				kill != null ? kill.kc : 0, kill != null ? kill.boss : null);
		}

		// Game counters own the scalar; unlock messages only add item history.
		// A lagging varp must not undo a newer broadcast count, and right after a
		// hop the varps may still hold the last world's. Full log refresh remains
		// the downward authority.
		int liveObtained = Math.max(countersSettled ? client.getVarpValue(VarPlayerID.COLLECTION_COUNT) : 0, broadcastObtained);
		int liveTotal = Math.max(countersSettled ? client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX) : 0, broadcastTotal);
		localClogCache.updateTotalsUpward(playerName, liveObtained, liveTotal);

		if (changed)
		{
			resetFirstSyncWarning();
			chatNotifier.send(ChatNotice.NEW_DROP,
				"Added " + itemName + " to Kill Clog");
			SwingUtilities.invokeLater(() -> panelRefresh.accept(playerName));
		}
	}

	void resetFirstSyncWarning()
	{
		needsFirstSyncWarned = false;
	}

	/**
	 * The account that just ended, or the plugin stopping (RuneLite keeps the instance for a re-enable): its kills, its
	 * credits and its first-sync warning go, so the next account's unlocks never take them.
	 */
	void reset()
	{
		kills.clear();
		credits.clear();
		last = null;
		resetFirstSyncWarning();
	}

	/** An item credited to nobody: the latest kill of a boss whose page holds it, else the count message it came with. */
	@Nullable
	private ClogUnlockParser.KillContext uncreditedKill(@Nullable List<String> pages, ClogIndex clogIndex, int tick)
	{
		Kill cause = null;
		for (String page : pages != null ? pages : Collections.<String>emptyList())
		{
			Kill kill = kills.get(name(clogIndex.pageName(page)));
			cause = kill != null && (cause == null || kill.tick > cause.tick) ? kill : cause;
		}
		if (cause == null && last != null && tick - last.tick <= WITH_THE_KILL)
		{
			cause = last;
		}
		return cause != null ? cause.context : null;
	}

	/** A boss's name as its kill message, its page and the game's credit write it, case and a leading "the" aside. */
	private static String name(@Nullable String name)
	{
		String key = name == null ? "" : name.replace((char) 160, ' ').trim().toLowerCase(Locale.ROOT);
		return key.startsWith("the ") ? key.substring(4) : key;
	}

	private void warnFirstSync(KillClogChatNotifier chatNotifier)
	{
		if (!needsFirstSyncWarned)
		{
			needsFirstSyncWarned = true;
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Open your Collection Log to set up Kill Clog automatically.");
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
				"Could not match " + itemName + ". Open your Collection Log to update it.");
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
			localClogCache.rememberPendingUnlock(playerName, missingItemIds);
			chatNotifier.send(ChatNotice.SYNC_HELP,
				"Could not match " + itemName + " exactly. Open your Collection Log to update it.");
			log.debug("Live clog sync found ambiguous item '{}' ({})", itemName, missingItemIds);
			return null;
		}
		return missingItemIds;
	}
}
