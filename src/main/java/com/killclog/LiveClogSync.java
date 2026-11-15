package com.killclog;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
 * from), so an item takes that NPC's latest kill however long the loot waited on its corpse: an awakened or a League's
 * Echo kill is its boss's, and an encounter's NPC named apart from its count message is pb-names.tsv's (Dusk's loot is
 * the Grotesque Guardians', a Royal Titan's the Royal Titans'). An NPC with no kill gives none unless a kill lands with
 * its loot (a plain smoke devil's Occult necklace, on Thermy's page too). A pet, a chest or a reward screen is credited
 * to nobody, and a Collection Log item comes only from its own content: it takes the latest kill of its page's
 * content, the page's own name or a count message naming it in whole words (a raid's modes, the Corrupted Gauntlet,
 * Phosani's Nightmare, Callisto on Callisto and Artio's page), else the count message it arrived with (the Lunar
 * Chest's).
 */
@Slf4j
final class LiveClogSync
{
	// A count message this many ticks or fewer before lands with its loot: a chest's reward, or loot credited to an NPC
	// named apart from its kill message that pb-names.tsv doesn't name. Never a wait.
	private static final int WITH_THE_KILL = 5;
	private static final Kill NO_KILL = new Kill(null, -1);

	private boolean needsFirstSyncWarned;
	// Each boss's latest kill this login by name, the latest of all, and each credited item's kill until it unlocks. The
	// plugin stopping clears them from the Swing thread while the client thread may be writing.
	private final Map<String, Kill> kills = new ConcurrentHashMap<>();
	@Nullable
	private volatile Kill last;
	private final Map<String, Kill> credits = new ConcurrentHashMap<>();

	@RequiredArgsConstructor
	private static final class Kill
	{
		@Nullable
		final ClogUnlockParser.KillContext context;
		final int tick;
	}

	void rememberKill(ClogUnlockParser.KillContext kill, int tick)
	{
		// Never read back: a stop may clear it in between, and the map takes no null.
		Kill remembered = new Kill(kill, tick);
		last = remembered;
		kills.put(name(kill.boss), remembered);
	}

	/**
	 * The game credited loot to an NPC: each item keeps that NPC's latest kill, or the kill the credit landed with when
	 * the two are named apart, until the item unlocks; none when neither (a plain monster).
	 */
	void credit(String npc, Collection<String> items, int tick)
	{
		// By its encounter's one name: a plain "Smoke devil" never takes the Thermonuclear smoke devil's kill.
		Kill kill = kills.get(name(npc));
		Kill latest = last;
		if (kill == null && latest != null && tick - latest.tick <= WITH_THE_KILL)
		{
			kill = latest;
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

	/**
	 * An item credited to nobody: the latest kill of its page's content (a count message naming the page in whole
	 * words, or named by it), else the count message it came with.
	 */
	@Nullable
	private ClogUnlockParser.KillContext uncreditedKill(@Nullable List<String> pages, ClogIndex clogIndex, int tick)
	{
		Kill cause = null;
		for (String page : pages != null ? pages : Collections.<String>emptyList())
		{
			String content = " " + name(clogIndex.pageName(page)) + " ";
			for (Map.Entry<String, Kill> kill : kills.entrySet())
			{
				String boss = " " + kill.getKey() + " ";
				if (content.trim().length() > 0 && (boss.contains(content) || content.contains(boss))
					&& (cause == null || kill.getValue().tick > cause.tick))
				{
					cause = kill.getValue();
				}
			}
		}
		Kill latest = last;
		if (cause == null && latest != null && tick - latest.tick <= WITH_THE_KILL)
		{
			cause = latest;
		}
		return cause != null ? cause.context : null;
	}

	/**
	 * The one name a boss's kill message, its page and the game's credit share: a variant's brackets dropped (an
	 * awakened or an Echo kill is its boss's), the name every lane's spelling reaches (PersonalBests.encounter: case, a
	 * leading "the" and pb-names.tsv's renames aside), then words alone ("Tombs of Amascut: Expert Mode" holds "tombs of
	 * amascut").
	 */
	private static String name(@Nullable String name)
	{
		return name == null ? ""
			: PersonalBests.encounter(name.replaceFirst(" \\([^()]*\\)$", "")).replaceAll("[^a-z0-9]+", " ").trim();
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
