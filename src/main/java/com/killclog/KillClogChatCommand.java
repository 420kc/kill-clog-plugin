package com.killclog;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Handler for the !kclog [boss] and !missing [boss] chat commands. Both replace the user's
 * chat line with collection log progress for the requested boss, plus inline sprite icons.
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

	private static final Map<String, String> ALIASES = buildAliases();

	/** Read-only view for canon-parity tests. */
	/* package */ static Map<String, String> aliases()
	{
		return java.util.Collections.unmodifiableMap(ALIASES);
	}

	private static Map<String, String> buildAliases()
	{
		Map<String, String> m = new HashMap<>();
		// Canonical names first: every boss the panel knows about.
		String[] canon = {
			"Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio",
			"Barrows Chests", "Brutus", "Bryophyta", "Callisto", "Cal'varion",
			"Cerberus", "Chambers of Xeric", "Chambers of Xeric: Challenge Mode",
			"Chaos Elemental", "Chaos Fanatic", "Commander Zilyana", "Corporeal Beast",
			"Crazy Archaeologist", "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme",
			"Deranged Archaeologist", "Doom of Mokhaiotl", "Duke Sucellus",
			"General Graardor", "Giant Mole", "Grotesque Guardians", "Hespori",
			"Kalphite Queen", "King Black Dragon", "Kraken", "Kree'Arra",
			"K'ril Tsutsaroth", "Lunar Chests", "Maggot King", "Mimic", "Nex", "Nightmare",
			"Phosani's Nightmare", "Obor", "Phantom Muspah", "Sarachnis", "Scorpia",
			"Scurrius", "Shellbane Gryphon", "Skotizo", "Sol Heredit", "Spindel",
			"Tempoross", "The Gauntlet", "The Corrupted Gauntlet", "The Hueycoatl",
			"The Leviathan", "The Royal Titans", "The Whisperer", "Theatre of Blood",
			"Theatre of Blood: Hard Mode", "Thermonuclear Smoke Devil",
			"Tombs of Amascut", "Tombs of Amascut: Expert Mode", "TzKal-Zuk",
			"TzTok-Jad", "Vardorvis", "Venenatis", "Vet'ion", "Vorkath",
			"Wintertodt", "Yama", "Zalcano", "Zulrah",
		};
		for (String c : canon)
		{
			m.put(normalize(c), c);
		}

		// Common community shorthand. Order doesn't matter; these all resolve to canonical.
		m.put("vork", "Vorkath");
		m.put("bandos", "General Graardor");
		m.put("graardor", "General Graardor");
		m.put("sara", "Commander Zilyana");
		m.put("zilyana", "Commander Zilyana");
		m.put("zammy", "K'ril Tsutsaroth");
		m.put("kril", "K'ril Tsutsaroth");
		m.put("arma", "Kree'Arra");
		m.put("kree", "Kree'Arra");
		m.put("kreearra", "Kree'Arra");
		m.put("corp", "Corporeal Beast");
		m.put("kbd", "King Black Dragon");
		m.put("kq", "Kalphite Queen");
		m.put("mole", "Giant Mole");
		m.put("thermy", "Thermonuclear Smoke Devil");
		m.put("smoke devil", "Thermonuclear Smoke Devil");
		m.put("cerb", "Cerberus");
		m.put("vard", "Vardorvis");
		m.put("duke", "Duke Sucellus");
		m.put("whisp", "The Whisperer");
		m.put("whisperer", "The Whisperer");
		m.put("levi", "The Leviathan");
		m.put("leviathan", "The Leviathan");
		m.put("muspah", "Phantom Muspah");
		m.put("phosani", "Phosani's Nightmare");
		m.put("doom", "Doom of Mokhaiotl");
		m.put("mokhaiotl", "Doom of Mokhaiotl");
		m.put("amox", "Amoxliatl");
		m.put("arax", "Araxxor");
		m.put("huey", "The Hueycoatl");
		m.put("hueycoatl", "The Hueycoatl");
		m.put("titans", "The Royal Titans");
		m.put("royal titans", "The Royal Titans");
		m.put("gauntlet", "The Gauntlet");
		m.put("cg", "The Corrupted Gauntlet");
		m.put("corrupted gauntlet", "The Corrupted Gauntlet");
		m.put("jad", "TzTok-Jad");
		m.put("zuk", "TzKal-Zuk");
		m.put("inferno", "TzKal-Zuk");
		m.put("sire", "Abyssal Sire");
		m.put("hydra", "Alchemical Hydra");
		m.put("scur", "Scurrius");
		m.put("scurrius", "Scurrius");
		m.put("sara mage", "Sarachnis");
		m.put("sarachnis", "Sarachnis");
		m.put("cox", "Chambers of Xeric");
		m.put("raids", "Chambers of Xeric");
		m.put("cm", "Chambers of Xeric: Challenge Mode");
		m.put("toa", "Tombs of Amascut");
		m.put("tombs", "Tombs of Amascut");
		m.put("expert", "Tombs of Amascut: Expert Mode");
		m.put("toa expert", "Tombs of Amascut: Expert Mode");
		m.put("tob", "Theatre of Blood");
		m.put("hmt", "Theatre of Blood: Hard Mode");
		m.put("prime", "Dagannoth Prime");
		m.put("rex", "Dagannoth Rex");
		m.put("supreme", "Dagannoth Supreme");
		m.put("vetion", "Vet'ion");
		m.put("calvarion", "Cal'varion");
		m.put("chaos ele", "Chaos Elemental");
		m.put("chaos fan", "Chaos Fanatic");
		m.put("crazy arch", "Crazy Archaeologist");
		m.put("deranged arch", "Deranged Archaeologist");
		m.put("grotesque", "Grotesque Guardians");
		m.put("gg", "Grotesque Guardians");
		m.put("shellbane", "Shellbane Gryphon");
		m.put("sol", "Sol Heredit");
		m.put("colosseum", "Sol Heredit");
		m.put("barrows", "Barrows Chests");
		m.put("lunar", "Lunar Chests");
		m.put("moons", "Lunar Chests");
		m.put("maggot", "Maggot King");
		m.put("mk", "Maggot King");
		m.put("perilous", "Lunar Chests");
		m.put("perilous moons", "Lunar Chests");
		m.put("nightmare", "Nightmare");
		m.put("nm", "Nightmare");
		m.put("pnm", "Phosani's Nightmare");
		m.put("vorky", "Vorkath");
		m.put("bryo", "Bryophyta");
		m.put("hesp", "Hespori");
		m.put("wt", "Wintertodt");
		m.put("winter", "Wintertodt");
		m.put("zal", "Zalcano");
		m.put("tempo", "Tempoross");
		m.put("krak", "Kraken");
		m.put("abby", "Abyssal Sire");
		m.put("ele", "Chaos Elemental");
		m.put("fan", "Chaos Fanatic");
		m.put("ven", "Venenatis");
		m.put("venom", "Venenatis");
		m.put("vet", "Vet'ion");
		m.put("calv", "Cal'varion");
		m.put("calli", "Callisto");
		m.put("art", "Artio");
		m.put("spin", "Spindel");
		return m;
	}

	/* package */ static String normalize(String s)
	{
		return s.toLowerCase().replace("'", "").replace(":", "")
			.replaceAll("\\s+", " ").trim();
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
		replaceText(chatMessage,
			itemName + " received on " + ClogHelper.formatKc(item.getObtainedAtKc()) + " kc");
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
			replaceText(chatMessage, "usage " + (missingMode ? COMMAND_MISSING : COMMAND) + " <boss>");
			return;
		}

		String query = normalize(parts[1]);
		String resolved = ALIASES.get(query);
		if (resolved == null)
		{
			// Loose substring fallback so partial typing still works ("abyssal" matches "Abyssal Sire").
			for (Map.Entry<String, String> e : ALIASES.entrySet())
			{
				String key = e.getKey();
				if (key.contains(query) || query.contains(key))
				{
					resolved = e.getValue();
					break;
				}
			}
		}
		if (resolved == null)
		{
			replaceText(chatMessage, "boss not recognized");
			return;
		}

		String rsn = resolveTargetRsn(chatMessage);
		final String boss = resolved;
		ClogResult cl = lookup(chatMessage, rsn, boss);
		if (cl == null)
		{
			return;
		}

		String categoryKey = ClogService.bossToCategory(boss);
		final List<ClogResult.ClogItem> obtainedList = cl.getObtainedItems().getOrDefault(categoryKey, Collections.emptyList());
		final List<Integer> totalList = cl.getCategoryItems().getOrDefault(categoryKey, Collections.emptyList());

		if (totalList.isEmpty())
		{
			replaceText(chatMessage, boss + ": no clog items found");
			return;
		}

		int bossKc = lookupBossKc(rsn, boss);
		final List<Integer> renderIds;
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
				replaceText(chatMessage, buildCompleteHeader(boss, bossKc));
				return;
			}
			renderIds = missing;
			header = buildCommandHeader(boss, bossKc, missing.size(), totalList.size(), true);
		}
		else
		{
			renderIds = new ArrayList<>(obtainedList.size());
			for (ClogResult.ClogItem item : obtainedList)
			{
				renderIds.add(item.getId());
			}
			header = buildCommandHeader(boss, bossKc, obtainedList.size(), totalList.size(), false);
		}

		// Icon registration + chat replacement both need the client thread.
		clientThread.invoke(() -> render(chatMessage, header, renderIds));
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
		clientThread.invoke(() -> render(chatMessage, header, renderIds));
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
	private void render(ChatMessage chatMessage, String header, List<Integer> itemIds)
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
					sb.append("<img=").append(idx).append(">");
				}
			}
		}
		chatMessage.getMessageNode().setRuneLiteFormatMessage(sb.toString());
		client.refreshChat();
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
