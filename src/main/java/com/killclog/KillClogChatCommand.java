package com.killclog;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.inject.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.*;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.*;

/**
 * The !kclog and !missing chat commands: each replaces the player's chat line with a Collection Log page's progress and
 * its items as icons, !kclog the items obtained and !missing the ones still to get. The log is chosen as the panel
 * chooses it, from the same caches.
 *
 * Registered with ChatCommandManager as async, so the requests run off the client thread; the icons and the rewritten
 * line go back to it.
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
	@Inject private RuneProfileService runeProfileService;
	@Inject private KillclogService killclogService;
	@Inject private LocalClogCache localClogCache;
	@Inject private HiscoreService hiscoreService;
	@Inject private ItemManager itemManager;
	@Inject private ChatIconManager chatIconManager;

	// itemId -> RuneLite's chat icon id, reserved once and kept for the life of the client (RuneLite has no way to
	// hand one back), as RuneLite's emoji plugin keeps its own.
	private final Map<Integer, Integer> itemIconId = new HashMap<>();

	// Backs the new-content catalog fallback in dispatch.
	@Inject private ClogIndex clogIndex;


	// On a League world, replies read that League (null: the main game) and the player's own log for it.
	static final String LEAGUE_PREFIX = "[Leagues] ";
	@Nullable private volatile String league;
	@Nullable private volatile LocalClogCache leagueLog;

	void readLeague(@Nullable String league, @Nullable LocalClogCache leagueLog)
	{
		this.leagueLog = leagueLog;
		this.league = league;
	}

	/** Every reply on a League world names the League, so it never reads as main-game data. */
	private String inGame(String text)
	{
		return league != null ? LEAGUE_PREFIX + text : text;
	}

	private static final Map<String, String> ALIASES = buildAliases();
	private static final Map<String, ClogTarget> CLUE_ALIASES = buildClueAliases();
	private static final Map<String, String> PAGE_ALIASES = buildPageAliases();

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class ClogTarget
	{
		final String label;
		final String categoryKey;
		@Nullable final String boss;

		private ClogTarget(String label, String categoryKey)
		{
			this(label, categoryKey, null);
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
		// The hiscores' own names first: every boss they list, the same ones the panel shows.
		for (String canonical : HiscoreService.bossNames())
		{
			m.put(normalize(canonical), canonical);
		}

		// Community shorthand rides the bundled catalog; the parity test pins
		// its complete contents against the shipped table.
		CatalogTsv.map(KillClogChatCommand.class, "chat-boss-aliases.tsv").forEach((alias, boss) -> m.put(normalize(alias), boss));
		return m;
	}

	private static Map<String, ClogTarget> buildClueAliases()
	{
		Map<String, ClogTarget> m = new HashMap<>();
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-clue-aliases.tsv", 2))
		{
			HiscoreSkill tier;
			try
			{
				tier = HiscoreSkill.valueOf("CLUE_SCROLL_" + row[1]);
			}
			catch (IllegalArgumentException e)
			{
				continue;
			}
			String categoryKey = PanelData.CLUE_CATEGORIES.get(tier);
			m.put(normalize(row[0]), new ClogTarget(titleCase(categoryKey), categoryKey));
		}
		return m;
	}

	private static Map<String, String> buildPageAliases()
	{
		Map<String, String> aliases = new HashMap<>();
		CatalogTsv.map(KillClogChatCommand.class, "chat-page-aliases.tsv").forEach((alias, page) -> aliases.put(normalize(alias), page));
		return Collections.unmodifiableMap(aliases);
	}

	/** Exact names and explicit shorthand only: "events" must never contain-match "ven". */
	@Nullable
	static ClogTarget resolvePage(String query, Set<String> categoryKeys)
	{
		String key = normalize(query);
		ClogTarget clue = CLUE_ALIASES.get(key);
		if (clue != null)
		{
			return clue;
		}
		String boss = ALIASES.get(key);
		if (boss != null)
		{
			return new ClogTarget(boss, ClogService.bossToCategory(boss), boss);
		}
		String page = PAGE_ALIASES.get(key);
		if (page != null)
		{
			return new ClogTarget(titleCase(page), page);
		}
		for (String category : categoryKeys)
		{
			if (normalize(category).equals(key))
			{
				return new ClogTarget(titleCase(category), category);
			}
		}
		return null;
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

	/* package */ static String normalize(String s)
	{
		return s.toLowerCase(Locale.ROOT).replace("'", "").replace("\u2019", "").replace(":", "")
			.replaceAll("[^a-z0-9]+", " ").trim();
	}

	/** Whether the text opens with this word and a space after it, in any case. */
	/* package */ static boolean startsWithWord(String text, String word)
	{
		return text.regionMatches(true, 0, word + " ", 0, word.length() + 1);
	}

	/* package */ static boolean isCompatibleLogCommand(ChatMessageType type, String message)
	{
		return PLAYER_CHAT_TYPES.contains(type) && message != null
			&& (message.equalsIgnoreCase(COMMAND_LOG_COMPATIBILITY) || startsWithWord(message, COMMAND_LOG_COMPATIBILITY));
	}

	/** The commands the chat command manager runs for us, each with its handler. */
	Map<String, BiConsumer<ChatMessage, String>> commands()
	{
		Map<String, BiConsumer<ChatMessage, String>> commands = new LinkedHashMap<>();
		commands.put(COMMAND, this::handle);
		commands.put(COMMAND_MISSING, this::handleMissing);
		commands.put(COMMAND_THIRD_AGE, this::handleThirdAge);
		commands.put(COMMAND_GILDED, this::handleGilded);
		return commands;
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
		if (startsWithWord(query, "missing"))
		{
			return COMMAND_MISSING + " " + query.substring("missing ".length()).trim();
		}
		return COMMAND + " " + query;
	}

	/**
	 * The player a command speaks for: whoever sent the line. In an outgoing private message chatMessage.getName() is
	 * the recipient, not the sender, so there it is the local player.
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
	 * Runs on the executor RuneLite shares with every plugin (ChatCommandManager.registerCommandAsync), so it only
	 * asks: the reply is finished when the answers arrive, and UI work jumps to clientThread.
	 */
	void handle(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, message, false);
	}

	/** !missing: the items still to get. */
	void handleMissing(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, message, true);
	}

	/**
	 * !log, RuneProfile's command, answered as !kclog is. The command itself is never registered, since it is
	 * RuneProfile's.
	 */
	void handleLogCompatibility(ChatMessage chatMessage, String message)
	{
		String delegated = toKillClogCommand(message);
		if (delegated == null)
		{
			replaceText(chatMessage, "usage " + COMMAND_LOG_COMPATIBILITY + " <collection-log page>");
			return;
		}

		dispatch(chatMessage, delegated, startsWithWord(delegated, COMMAND_MISSING));
	}

	/**
	 * !kc with an item's name: the kill count an obtained item arrived on, as "Elder venator fang received on 421 kc".
	 *
	 * Never registered with ChatCommandManager: RuneLite's Chat Commands owns "!kc" (the manager keeps one handler per
	 * command), and its arguments are boss names. Item names are ours, so KillClogPlugin sends the raw chat line here;
	 * a boss's name, an unknown item and an item with no kill kept are all left alone, and Chat Commands' boss lookup
	 * keeps working.
	 *
	 * Runs on the client thread (it comes from a chat event, not the command manager's executor), so item definitions
	 * can be read here.
	 */
	void handleKcItem(ChatMessage chatMessage, ClogIndex clogIndex, LocalClogCache localClogCache)
	{
		if (!PLAYER_CHAT_TYPES.contains(chatMessage.getType()))
		{
			return;
		}
		String message = chatMessage.getMessage();
		if (!startsWithWord(message, COMMAND_KC))
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
		return itemName + " received on " + TitleTooltip.grouped(kc) + " kc";
	}

	/** !3a: the 3rd age items received. */
	void handleThirdAge(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, COMMAND + " third age", false);
	}

	/** !gilded: the gilded items received. */
	void handleGilded(ChatMessage chatMessage, String message)
	{
		dispatch(chatMessage, COMMAND + " gilded", false);
	}

	private void dispatch(ChatMessage chatMessage, String message, boolean missingMode)
	{
		String[] parts = message.split("\\s+", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty())
		{
			replaceText(chatMessage, "usage " + (missingMode ? COMMAND_MISSING : COMMAND)
				+ " <collection-log page>, e.g. pets, mixology, medium clues");
			return;
		}

		String query = normalize(parts[1]);
		ClogIndex index = clogIndex;
		Set<String> categoryKeys = index != null ? index.categoryKeys() : Collections.emptySet();
		ClogTarget target = resolvePage(query, categoryKeys);
		if (target == null && !categoryKeys.isEmpty())
		{
			replaceText(chatMessage, "collection log page not recognized; use its full name or a listed alias");
			return;
		}

		String rsn = resolveTargetRsn(chatMessage);
		ClogTarget known = target;
		String label = known != null ? known.label : "Collection Log";
		lookup(rsn).whenComplete((cl, error) ->
		{
			if (error != null)
			{
				lookupFailed(chatMessage, label, rsn, error);
			}
			else if (cl == null)
			{
				replaceText(chatMessage, label + ": no clog data");
			}
			else
			{
				try
				{
					reply(chatMessage, query, known, index, rsn, cl, missingMode);
				}
				catch (RuntimeException e)
				{
					lookupFailed(chatMessage, label, rsn, e);
				}
			}
		});
	}

	/** The reply once the log has arrived: the page, then the boss's kill count when the page has one. */
	private void reply(ChatMessage chatMessage, String query, @Nullable ClogTarget target, @Nullable ClogIndex index,
		String rsn, ClogResult cl, boolean missingMode)
	{
		if (target == null)
		{
			target = resolvePage(query, cl.getCategoryItems().keySet());
		}
		if (target == null)
		{
			replaceText(chatMessage, "collection log page not recognized; use its full name or a listed alias");
			return;
		}
		final String label = target.label;
		final String categoryKey = target.categoryKey;
		PageItems page = pageItems(cl, categoryKey, index != null ? index.categoryItems() : null);
		final List<Integer> totalList = page.total;

		if (totalList.isEmpty())
		{
			replaceText(chatMessage, label + ": no clog items found");
			return;
		}
		if (!page.available)
		{
			replaceText(chatMessage, label + ": page not synced");
			return;
		}

		CompletableFuture<Integer> kc = target.boss != null ? bossKc(rsn, target.boss)
			: CompletableFuture.completedFuture(-1);
		kc.thenAccept(bossKc -> respond(chatMessage, label, page, missingMode, bossKc)).exceptionally(e ->
		{
			lookupFailed(chatMessage, label, rsn, e);
			return null;
		});
	}

	/** A lookup that broke: logged with its cause, and the chat line says so. */
	private void lookupFailed(ChatMessage chatMessage, String label, String rsn, Throwable error)
	{
		log.warn("clog lookup failed for {}", rsn, error);
		replaceText(chatMessage, label + ": lookup failed");
	}

	private void respond(ChatMessage chatMessage, String label, PageItems page, boolean missingMode, int bossKc)
	{
		final List<Integer> totalList = page.total;
		final List<Integer> renderIds;
		final Map<Integer, Integer> renderQuantities;
		final String header;
		if (missingMode)
		{
			List<Integer> missing = new ArrayList<>();
			for (Integer id : totalList)
			{
				if (!page.quantities.containsKey(id))
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
			renderIds = new ArrayList<>(page.quantities.keySet());
			renderQuantities = page.quantities;
			header = buildCommandHeader(label, bossKc, renderIds.size(), totalList.size(), false);
		}

		// Icon registration + chat replacement both need the client thread.
		clientThread.invoke(() -> render(chatMessage, header, renderIds, renderQuantities));
	}

	static final class PageItems
	{
		final List<Integer> total;
		final Map<Integer, Integer> quantities = new LinkedHashMap<>();
		final boolean available;

		private PageItems(List<Integer> total, boolean available)
		{
			this.total = new ArrayList<>(new LinkedHashSet<>(total));
			this.available = available;
		}
	}

	/** A page's items from one log: the slots the page has, each once, and how many of each the player holds. */
	static PageItems pageItems(ClogResult cl, String category, @Nullable Map<String, List<Integer>> catalog)
	{
		List<Integer> total = totalsWithCatalogFallback(
			cl.getCategoryItems().getOrDefault(category, Collections.emptyList()), catalog, category);
		boolean bucket = PanelData.CLOG_THIRD_AGE.equals(category) || PanelData.CLOG_GILDED.equals(category);
		if (total.isEmpty() && bucket)
		{
			total = new ArrayList<>();
			for (int id : PanelData.CLOG_THIRD_AGE.equals(category) ? PanelData.THIRD_AGE_ITEMS : PanelData.GILDED_ITEMS)
			{
				total.add(id);
			}
		}
		PageItems page = new PageItems(total, bucket || cl.getCategoryItems().containsKey(category)
			|| cl.getObtainedItems().containsKey(category));
		Map<Integer, Integer> counts = new HashMap<>();
		// Only synthetic rare-item groups span pages. Pets read all_pets directly.
		Iterable<List<ClogResult.ClogItem>> lists = bucket && !cl.getObtainedItems().containsKey(category)
			? cl.getObtainedItems().values()
			: Collections.singletonList(cl.getObtainedItems().getOrDefault(category, Collections.emptyList()));
		for (List<ClogResult.ClogItem> items : lists)
		{
			for (ClogResult.ClogItem item : items)
			{
				if (item.getCount() > 0)
				{
					counts.merge(item.getId(), item.getCount(), Math::max);
				}
			}
		}
		for (Integer id : page.total)
		{
			if (counts.containsKey(id))
			{
				page.quantities.put(id, counts.get(id));
			}
		}
		return page;
	}

	/** The player's log from the providers (or this League's), laid on the game's pages; a failure completes it so. */
	private CompletableFuture<ClogResult> lookup(String rsn)
	{
		CompletableFuture<ClogResult> asked;
		try
		{
			String league = this.league;
			LocalClogCache leagueLog = this.leagueLog;
			asked = league == null ? ClogProviderFanout.lookup(localClogCache.isActivePlayer(rsn),
				() -> clogService.lookup(rsn), () -> runeProfileService.lookupClog(rsn),
				() -> killclogService.lookupClog(rsn))
				: leagueLog != null && leagueLog.isActivePlayer(rsn) ? clogService.lookupLocal(leagueLog, rsn)
				: killclogService.lookupClog(rsn, league);
		}
		catch (RuntimeException e)
		{
			CompletableFuture<ClogResult> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
		return asked.thenApply(cl ->
		{
			ClogIndex index = clogIndex;
			return index != null && cl != null ? index.laid(cl) : cl;
		});
	}

	/** The boss's kill count for the reply's header, or -1 when the hiscores don't answer in the panel's time limit. */
	private CompletableFuture<Integer> bossKc(String rsn, String boss)
	{
		try
		{
			boolean main = league == null;
			HiscoreResult cached = hiscoreService.getCached(rsn);
			if (main && cached != null && !hiscoreService.isStale(rsn))
			{
				return CompletableFuture.completedFuture(cached.getKc(boss));
			}
			return (main ? hiscoreService.lookup(rsn, null)
				: hiscoreService.lookupTable(rsn, HiscoreService.LEAGUE_TABLE))
				.copy().orTimeout(LookupFanout.hiscoreTimeoutSeconds, TimeUnit.SECONDS)
				.handle((result, error) ->
				{
					if (error != null)
					{
						log.debug("hiscore lookup failed for chat command {}", rsn, error);
					}
					return error == null && result != null ? result.getKc(boss) : -1;
				});
		}
		catch (RuntimeException e)
		{
			log.debug("hiscore lookup failed for chat command {}", rsn, e);
			return CompletableFuture.completedFuture(-1);
		}
	}

	/**
	 * A page's full list of slots: the cached or the site's list when it has any, else the running game's catalog. A
	 * log walked before a page existed lacks it, and sites trail new pages the same way, but the running game always
	 * knows the page. What was obtained is untouched: it stays as captured.
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
		return headerStart(boss, bossKc) + count + "/" + total + (missingMode ? " missing" : "");
	}

	static String buildCompleteHeader(String boss, int bossKc)
	{
		return headerStart(boss, bossKc) + "complete";
	}

	/** "Zulrah: 1,234 kc, ", the kill count left out when the hiscores have none. */
	private static String headerStart(String boss, int bossKc)
	{
		return boss + ": " + (bossKc >= 0 ? ClogHelper.formatKc(bossKc) + " kc, " : "");
	}

	/**
	 * Write the response onto the player's MessageNode with each item's icon. Runs on the client thread.
	 *
	 * setRuneLiteFormatMessage + refreshChat is the after-the-fact write path (the chat line
	 * has already been drawn by the time the async lookup returns). A sprite that streams in
	 * late repaints the line itself.
	 */
	private void render(ChatMessage chatMessage, String header, List<Integer> itemIds,
		Map<Integer, Integer> itemQuantities)
	{
		// Before the client has its icons (a reset, until the login screen) the reply goes without them.
		chatMessage.getMessageNode().setRuneLiteFormatMessage(formatMessage(inGame(header), itemIds, itemQuantities,
			client.getModIcons() != null ? iconIndices(itemIds) : Collections.emptyMap()));
		client.refreshChat();
	}

	static String formatMessage(String header, List<Integer> itemIds,
		Map<Integer, Integer> itemQuantities, Map<Integer, Integer> iconIndices)
	{
		StringBuilder sb = new StringBuilder(header);
		for (Integer id : itemIds)
		{
			Integer idx = iconIndices.get(id);
			if (idx != null)
			{
				// Native chat wraps on spaces. Keep each sprite and its quantity one word.
				sb.append(' ').append(formatItemIcon(idx, itemQuantities.getOrDefault(id, 1)));
			}
		}
		return sb.toString();
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
	 * Each item's index in the chat's icons. An item not seen before reserves a chat icon from RuneLite's manager
	 * (usable at once, client thread only) that its sprite fills when it loads, as RuneLite's emoji plugin does.
	 */
	private Map<Integer, Integer> iconIndices(List<Integer> itemIds)
	{
		Map<Integer, Integer> indices = new HashMap<>();
		for (int itemId : itemIds)
		{
			int index = chatIconManager.chatIconIndex(itemIconId.computeIfAbsent(itemId, this::reserveIcon));
			if (index >= 0)
			{
				indices.put(itemId, index);
			}
		}
		return indices;
	}

	private int reserveIcon(int itemId)
	{
		int iconId = chatIconManager.reserveChatIcon();
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() ->
		{
			chatIconManager.updateChatIcon(iconId, ImageUtil.resizeImage(image, ICON_W, ICON_H));
			client.refreshChat();
		});
		return iconId;
	}

	private void replaceText(ChatMessage chatMessage, String text)
	{
		clientThread.invoke(() ->
		{
			chatMessage.getMessageNode().setRuneLiteFormatMessage(inGame(text));
			client.refreshChat();
		});
	}
}
