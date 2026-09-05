package com.killclog;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Handler for the !kclog and !missing chat commands. Both replace the user's chat line with
 * collection log progress for the requested boss or clue tier, plus inline sprite icons.
 * !kclog renders obtained items, !missing renders the inverse (still-unobtained items).
 * Reuses ClogService so the commands share the panel's cache.
 *
 * Registered async via ChatCommandManager so the lookup I/O runs off-thread. Chat-icon
 * registration and the message rewrite both jump to the client thread.
 *
 * Icons are stored directly in client.getModIcons() (the same approach RuneLite's
 * first-party ChatCommandsPlugin uses for !pets). ChatIconManager is bypassed because
 * its registerChatIcon rejects AsyncBufferedImage and its index is only valid one tick
 * after registration. Neither works well with on-demand item-cache sprites.
 */
@Slf4j
@Singleton
class KillClogChatCommand
{
	static final String COMMAND = "!kclog";
	static final String COMMAND_MISSING = "!missing";
	static final String COMMAND_THIRD_AGE = "!3a";
	static final String COMMAND_GILDED = "!gilded";
	static final String COMMAND_KC = "!kc";
	static final String COMMAND_LOG_COMPATIBILITY = "!log";

	// The channels ChatCommandManager itself dispatches on; the !kc item path
	// reads raw events, so it mirrors the same set.
	private static final Set<ChatMessageType> PLAYER_CHAT_TYPES = EnumSet.of(
		ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
		ChatMessageType.FRIENDSCHAT, ChatMessageType.PRIVATECHAT,
		ChatMessageType.MODPRIVATECHAT, ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.CLAN_GIM_CHAT);

	private static final int ICON_W = 18;
	private static final int ICON_H = 16;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClogService clogService;
	@Inject private HiscoreService hiscoreService;
	@Inject private ItemManager itemManager;

	// itemId -> absolute index into client.getModIcons(). Persists for the plugin's lifetime;
	// cleared in clear() on shutdown so a disable/enable cycle re-registers cleanly.
	private final Map<Integer, Integer> itemIconIdx = new HashMap<>();

	// Plugin-owned (not injected): set at startup like the panel's reference.
	// Backs the new-content catalog fallback in dispatch.
	@Nullable private ClogIndex clogIndex;

	void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		this.clogIndex = clogIndex;
	}

	private static final Map<String, String> ALIASES = buildAliases();
	private static final Map<String, ClogTarget> CLUE_ALIASES = buildClueAliases();

	private static final class ClogTarget
	{
		private final String label;
		private final String categoryKey;

		private ClogTarget(String label, String categoryKey)
		{
			this.label = label;
			this.categoryKey = categoryKey;
		}
	}

	/** Read-only view for canon-parity tests. */
	/* package */ static Map<String, String> aliases()
	{
		return java.util.Collections.unmodifiableMap(ALIASES);
	}

	private static Map<String, String> buildAliases()
	{
		Map<String, String> m = new HashMap<>();
		// Canonical names first: every boss the hiscore feed carries, which is
		// the same census the panel renders.
		for (String canonical : HiscoreService.bossNames())
		{
			m.put(normalize(canonical), canonical);
		}

		// Community shorthand rides the bundled catalog; the parity test pins
		// its complete contents against the shipped table.
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-boss-aliases.tsv", 2))
		{
			m.put(normalize(row[0]), row[1]);
		}
		return m;
	}

	private static Map<String, ClogTarget> buildClueAliases()
	{
		Map<String, ClogTarget> m = new HashMap<>();
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-clue-aliases.tsv", 2))
		{
			HiscoreSkill tier = HiscoreSkill.valueOf("CLUE_SCROLL_" + row[1]);
			String categoryKey = PanelData.CLUE_CATEGORIES.get(tier);
			m.put(normalize(row[0]), new ClogTarget(titleCase(categoryKey), categoryKey));
		}
		return m;
	}

	/** beginner_treasure_trails -> Beginner Treasure Trails. */
	private static String titleCase(String categoryKey)
	{
		StringBuilder label = new StringBuilder(categoryKey.length());
		for (String word : categoryKey.split("_"))
		{
			if (label.length() > 0)
			{
				label.append(' ');
			}
			label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return label.toString();
	}

	/** Read-only views for catalog-parity tests. */
	/* package */ static String clueLabel(String query)
	{
		ClogTarget target = CLUE_ALIASES.get(normalize(query));
		return target != null ? target.label : null;
	}

	/* package */ static int clueAliasCount()
	{
		return CLUE_ALIASES.size();
	}

	/* package */ static String resolveClueCategory(String query)
	{
		ClogTarget target = CLUE_ALIASES.get(normalize(query));
		return target != null ? target.categoryKey : null;
	}

	/* package */ static String normalize(String s)
	{
		return s.toLowerCase().replace("'", "").replace(":", "")
			.replaceAll("\\s+", " ").trim();
	}

	/* package */ static boolean isCompatibleLogCommand(ChatMessageType type, String message)
	{
		return PLAYER_CHAT_TYPES.contains(type) && message != null
			&& (message.equalsIgnoreCase(COMMAND_LOG_COMPATIBILITY)
				|| message.regionMatches(true, 0, COMMAND_LOG_COMPATIBILITY + " ", 0,
					COMMAND_LOG_COMPATIBILITY.length() + 1));
	}

	/* package */ static String toKillClogCommand(String message)
	{
		String[] parts = message.split("\\s+", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty())
		{
			return null;
		}

		String query = parts[1].trim();
		if (query.equalsIgnoreCase("missing"))
		{
			return null;
		}
		if (query.regionMatches(true, 0, "missing ", 0, "missing ".length()))
		{
			return COMMAND_MISSING + " " + query.substring("missing ".length()).trim();
		}
		return COMMAND + " " + query;
	}

	/**
	 * Resolve the player whose clog the command targets. In PRIVATECHATOUT,
	 * chatMessage.getName() returns the recipient's name rather than the
	 * sender's, so !kclog/!missing in an outgoing PM would have looked up the
	 * wrong player. Self-lookup commands route to the local player when the
	 * chat type is outgoing PM; every other type continues to use the sender
	 * field, which is the local player for the channels where these commands
	 * fire (PUBLICCHAT, FRIENDSCHAT, CLAN_CHAT, CLAN_GUEST_CHAT, AUTOTYPER).
	 */
	private String resolveTargetRsn(ChatMessage chatMessage)
	{
		if (chatMessage.getType() == ChatMessageType.PRIVATECHATOUT)
		{
			Player local = client.getLocalPlayer();
			if (local != null && local.getName() != null)
			{
				return Text.sanitize(local.getName());
			}
		}
		return Text.sanitize(chatMessage.getName());
	}

	/**
	 * !kclog handler: renders obtained items.
	 * Async per ChatCommandManager.registerCommandAsync. Blocking I/O fine here, UI work jumps to clientThread.
	 */
	void handle(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, message, false);
	}

	/**
	 * !missing handler: renders unobtained items.
	 */
	void handleMissing(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, message, true);
	}

	/**
	 * RuneProfile-compatible fallback: delegates !log to the Kill Clog result
	 * path without registering the shared command string.
	 */
	void handleLogCompatibility(ChatMessage chatMessage, String message)
	{
		String delegated = toKillClogCommand(message);
		if (delegated == null)
		{
			replaceText(chatMessage, "usage " + COMMAND_LOG_COMPATIBILITY + " <collection-log page>");
			return;
		}

		boolean missingMode = delegated.regionMatches(true, 0, COMMAND_MISSING + " ", 0,
			COMMAND_MISSING.length() + 1);
		dispatch(chatMessage, delegated, missingMode);
	}

	/**
	 * !kc <item name>: reveals the kill count an obtained clog item arrived on,
	 * e.g. "Elder venator fang received on 421 kc".
	 *
	 * Never registered with ChatCommandManager: the built-in ChatCommandsPlugin
	 * owns the "!kc" trigger (the manager keeps one handler per command string),
	 * and boss names are its argument space. Item names are ours, so
	 * KillClogPlugin routes raw chat events here; boss arguments, unknown
	 * items, and items without captured provenance all fall through untouched
	 * and the vanilla boss lookup keeps working.
	 *
	 * Runs on the client thread (event subscriber path, not the command
	 * manager's executor), so item compositions are safe to read directly.
	 */
	void handleKcItem(ChatMessage chatMessage, ClogIndex clogIndex, LocalClogCache localClogCache)
	{
		if (!PLAYER_CHAT_TYPES.contains(chatMessage.getType()))
		{
			return;
		}
		String message = chatMessage.getMessage();
		if (!message.regionMatches(true, 0, COMMAND_KC + " ", 0, COMMAND_KC.length() + 1))
		{
			return;
		}
		String query = message.substring(COMMAND_KC.length() + 1).trim();
		if (query.isEmpty() || !clogIndex.ensureParsed(client, itemManager))
		{
			return;
		}
		List<Integer> itemIds = clogIndex.itemIdsForName(ClogUnlockParser.normalizeItemName(query));
		if (itemIds == null || itemIds.isEmpty())
		{
			return;
		}

		ClogResult.ClogItem item = localClogCache.provenancedItem(resolveTargetRsn(chatMessage), itemIds);
		if (item == null)
		{
			return;
		}
		String itemName = itemManager.getItemComposition(item.getId()).getName();
		replaceText(chatMessage, kcReceivedText(itemName, item.getObtainedAtKc()));
	}

	/** Provenance is exact by definition: full grouped digits, never the k/m shorthand. */
	/* package */ static String kcReceivedText(String itemName, int kc)
	{
		return itemName + " received on " + String.format(Locale.US, "%,d", kc) + " kc";
	}

	/**
	 * !3a handler: renders received 3rd age bucket items.
	 */
	void handleThirdAge(ChatMessage chatMessage, String message)
	{
		dispatchBucket(chatMessage, "3rd Age", PanelData.THIRD_AGE_ITEMS);
	}

	/**
	 * !gilded handler: renders received gilded bucket items.
	 */
	void handleGilded(ChatMessage chatMessage, String message)
	{
		dispatchBucket(chatMessage, "Gilded", PanelData.GILDED_ITEMS);
	}

	private void dispatch(ChatMessage chatMessage, String message, boolean missingMode)
	{
		String[] parts = message.split("\\s+", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty())
		{
			replaceText(chatMessage, "usage " + (missingMode ? COMMAND_MISSING : COMMAND)
				+ " <boss or clue tier>");
			return;
		}

		String query = normalize(parts[1]);
		ClogTarget clueTarget = CLUE_ALIASES.get(query);
		String resolvedBoss = null;
		if (clueTarget == null)
		{
			resolvedBoss = ALIASES.get(query);
			if (resolvedBoss == null)
			{
				// Loose substring fallback so partial typing still works ("abyssal" matches "Abyssal Sire").
				for (Map.Entry<String, String> e : ALIASES.entrySet())
				{
					String key = e.getKey();
					if (key.contains(query) || query.contains(key))
					{
						resolvedBoss = e.getValue();
						break;
					}
				}
			}
		}
		if (clueTarget == null && resolvedBoss == null)
		{
			replaceText(chatMessage, "collection log page not recognized");
			return;
		}

		String rsn = resolveTargetRsn(chatMessage);
		final String label = clueTarget != null ? clueTarget.label : resolvedBoss;
		final String categoryKey = clueTarget != null
			? clueTarget.categoryKey : ClogService.bossToCategory(resolvedBoss);
		ClogResult cl = lookup(chatMessage, rsn, label);
		if (cl == null)
		{
			return;
		}

		final List<ClogResult.ClogItem> obtainedList = cl.getObtainedItems()
			.getOrDefault(categoryKey, Collections.emptyList());
		ClogIndex index = clogIndex;
		final List<Integer> totalList = totalsWithCatalogFallback(
			cl.getCategoryItems().getOrDefault(categoryKey, Collections.emptyList()),
			index != null && index.isParsed() ? index.categoryItems() : null,
			categoryKey);

		if (totalList.isEmpty())
		{
			replaceText(chatMessage, label + ": no clog items found");
			return;
		}

		int bossKc = clueTarget == null ? lookupBossKc(rsn, resolvedBoss) : -1;
		final List<Integer> renderIds;
		final Map<Integer, Integer> renderQuantities;
		final String header;
		if (missingMode)
		{
			Set<Integer> obtainedIds = new HashSet<>();
			for (ClogResult.ClogItem item : obtainedList)
			{
				obtainedIds.add(item.getId());
			}
			List<Integer> missing = new ArrayList<>();
			for (Integer id : totalList)
			{
				if (!obtainedIds.contains(id))
				{
					missing.add(id);
				}
			}
			if (missing.isEmpty())
			{
				replaceText(chatMessage, buildCompleteHeader(label, bossKc));
				return;
			}
			renderIds = missing;
			renderQuantities = Collections.emptyMap();
			header = buildCommandHeader(label, bossKc, missing.size(), totalList.size(), true);
		}
		else
		{
			renderIds = new ArrayList<>(obtainedList.size());
			renderQuantities = new HashMap<>();
			for (ClogResult.ClogItem item : obtainedList)
			{
				renderIds.add(item.getId());
				if (item.getCount() > 1)
				{
					renderQuantities.put(item.getId(), item.getCount());
				}
			}
			header = buildCommandHeader(label, bossKc, obtainedList.size(), totalList.size(), false);
		}

		// Icon registration + chat replacement both need the client thread.
		clientThread.invoke(() -> render(chatMessage, header, renderIds, renderQuantities));
	}

	private void dispatchBucket(ChatMessage chatMessage, String label, int[] bucketItemIds)
	{
		String rsn = resolveTargetRsn(chatMessage);
		ClogResult cl = lookup(chatMessage, rsn, label);
		if (cl == null)
		{
			return;
		}

		Set<Integer> obtainedIds = allObtainedIds(cl);
		List<Integer> renderIds = new ArrayList<>();
		for (int itemId : bucketItemIds)
		{
			if (obtainedIds.contains(itemId))
			{
				renderIds.add(itemId);
			}
		}

		String header = label + ": " + renderIds.size() + "/" + bucketItemIds.length;
		clientThread.invoke(() -> render(chatMessage, header, renderIds, Collections.emptyMap()));
	}

	private ClogResult lookup(ChatMessage chatMessage, String rsn, String label)
	{
		ClogResult cl;
		try
		{
			cl = clogService.lookup(rsn).join();
		}
		catch (Exception e)
		{
			log.warn("clog lookup failed for {}", rsn, e);
			replaceText(chatMessage, label + ": lookup failed");
			return null;
		}

		if (cl == null)
		{
			replaceText(chatMessage, label + ": no clog data");
			return null;
		}
		return cl;
	}

	private int lookupBossKc(String rsn, String boss)
	{
		try
		{
			HiscoreResult cached = hiscoreService.getCached(rsn);
			if (cached != null && !hiscoreService.isStale(rsn))
			{
				return cached.getKc(boss);
			}

			HiscoreResult result = hiscoreService.lookup(rsn, null).join();
			return result != null ? result.getKc(boss) : -1;
		}
		catch (Exception e)
		{
			log.debug("hiscore lookup failed for chat command {}", rsn, e);
			return -1;
		}
	}

	/**
	 * Total-slot list for a category: the cached or provider list when it has
	 * entries, else the parsed in-game catalog's. A cache bulk-synced before a
	 * category existed simply lacks it, and providers lag new pages the same
	 * way, but the running game build always knows the page. Obtained counts
	 * are untouched: they stay whatever was captured.
	 */
	/* package */ static List<Integer> totalsWithCatalogFallback(List<Integer> cached,
		@Nullable Map<String, List<Integer>> catalog, String categoryKey)
	{
		if (!cached.isEmpty() || catalog == null)
		{
			return cached;
		}
		return catalog.getOrDefault(categoryKey, cached);
	}

	static String buildCommandHeader(String boss, int bossKc, int count, int total, boolean missingMode)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(boss).append(": ");
		appendKc(sb, bossKc);
		sb.append(count).append("/").append(total);
		if (missingMode)
		{
			sb.append(" missing");
		}
		return sb.toString();
	}

	static String buildCompleteHeader(String boss, int bossKc)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(boss).append(": ");
		appendKc(sb, bossKc);
		sb.append("complete");
		return sb.toString();
	}

	private static void appendKc(StringBuilder sb, int bossKc)
	{
		if (bossKc >= 0)
		{
			sb.append(ClogHelper.formatKc(bossKc)).append(" kc, ");
		}
	}

	private static Set<Integer> allObtainedIds(ClogResult cl)
	{
		Set<Integer> obtainedIds = new HashSet<>();
		for (List<ClogResult.ClogItem> items : cl.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items)
			{
				obtainedIds.add(item.getId());
			}
		}
		return obtainedIds;
	}

	/**
	 * Reserve a mod-icon slot for every itemId we haven't seen before, kick off the async
	 * sprite loads, then write the response onto the player's MessageNode. Runs on the
	 * client thread.
	 *
	 * setRuneLiteFormatMessage + refreshChat is the after-the-fact write path (the chat line
	 * has already been drawn by the time the async lookup returns). Each onLoaded callback
	 * also fires refreshChat so a sprite that streams in late repaints the line.
	 */
	private void render(ChatMessage chatMessage, String header, List<Integer> itemIds,
		Map<Integer, Integer> itemQuantities)
	{
		ensureIcons(itemIds);

		StringBuilder sb = new StringBuilder();
		sb.append(header);
		if (!itemIds.isEmpty())
		{
			sb.append(" ");
			for (Integer id : itemIds)
			{
				Integer idx = itemIconIdx.get(id);
				if (idx != null)
				{
					sb.append(formatItemIcon(idx, itemQuantities.getOrDefault(id, 1)));
				}
			}
		}
		chatMessage.getMessageNode().setRuneLiteFormatMessage(sb.toString());
		client.refreshChat();
	}

	/* package */ static String formatItemIcon(int iconIndex, int quantity)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<img=").append(iconIndex).append(">");
		if (quantity > 1)
		{
			sb.append("x").append(quantity);
		}
		return sb.toString();
	}

	/**
	 * Grow client.getModIcons() once with a slot per never-before-seen itemId, then schedule
	 * each sprite to write into its slot via AsyncBufferedImage.onLoaded.
	 *
	 * Pattern matches RuneLite ChatCommandsPlugin.loadPets. Bypasses ChatIconManager because
	 * (a) registerChatIcon rejects AsyncBufferedImage, (b) its index is populated one tick
	 * later via invokeLater, so chatIconIndex returns -1 in the same call. Direct modIcons
	 * manipulation keeps the index synchronously valid and accepts async images cleanly.
	 */
	private void ensureIcons(List<Integer> itemIds)
	{
		List<Integer> needsRegister = new ArrayList<>();
		for (Integer itemId : itemIds)
		{
			if (!itemIconIdx.containsKey(itemId))
			{
				needsRegister.add(itemId);
			}
		}
		if (needsRegister.isEmpty()) return;

		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null) return;
		IndexedSprite[] grown = Arrays.copyOf(modIcons, modIcons.length + needsRegister.size());
		final int base = modIcons.length;

		for (int i = 0; i < needsRegister.size(); i++)
		{
			final int itemId = needsRegister.get(i);
			final int slot = base + i;
			itemIconIdx.put(itemId, slot);

			final AsyncBufferedImage abi = itemManager.getImage(itemId);
			abi.onLoaded(() ->
			{
				BufferedImage resized = ImageUtil.resizeImage(abi, ICON_W, ICON_H);
				IndexedSprite sprite = ImageUtil.getImageIndexedSprite(resized, client);
				// Re-fetch modIcons inside the callback; Jagex may swap the array between
				// our setModIcons above and this callback firing.
				IndexedSprite[] current = client.getModIcons();
				if (current != null && slot < current.length)
				{
					current[slot] = sprite;
					client.refreshChat();
				}
			});
		}

		client.setModIcons(grown);
	}

	/**
	 * Drops cached mod-icon indices on plugin shutdown so a disable/enable cycle re-registers
	 * against whatever modIcons looks like next time. Called from KillClogPlugin.shutDown.
	 */
	void clear()
	{
		itemIconIdx.clear();
	}

	private void replaceText(ChatMessage chatMessage, String text)
	{
		clientThread.invoke(() ->
		{
			chatMessage.getMessageNode().setRuneLiteFormatMessage(text);
			client.refreshChat();
		});
	}
}
