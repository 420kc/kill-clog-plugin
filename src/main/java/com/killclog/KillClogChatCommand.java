package com.killclog;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

/**
 * Handler for the !kclog [boss] chat command. Replaces the user's chat line with their KC
 * and collection log progress for the requested boss. Reuses HiscoreService and ClogService
 * so the command shares the same caches and TTLs as the panel lookup.
 *
 * Registered async via ChatCommandManager so the I/O runs off-thread; the chat replacement
 * is applied on the client thread via MessageNode.setValue + BUILD_CHATBOX.
 */
@Slf4j
@Singleton
class KillClogChatCommand
{
	static final String COMMAND = "!kclog";

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClogService clogService;
	@Inject private ItemManager itemManager;
	@Inject private ChatIconManager chatIconManager;

	// itemId -> registered chat icon id. Each item only needs registering once per session.
	private final Map<Integer, Integer> loadedIcons = new HashMap<>();

	private static final Map<String, String> ALIASES = buildAliases();

	private static Map<String, String> buildAliases()
	{
		Map<String, String> m = new HashMap<>();
		// Canonical names first — every boss the panel knows about, normalized self-mapping.
		String[] canon = {
			"Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio",
			"Barrows Chests", "Brutus", "Bryophyta", "Callisto", "Cal'varion",
			"Cerberus", "Chambers of Xeric", "Chambers of Xeric: Challenge Mode",
			"Chaos Elemental", "Chaos Fanatic", "Commander Zilyana", "Corporeal Beast",
			"Crazy Archaeologist", "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme",
			"Deranged Archaeologist", "Doom of Mokhaiotl", "Duke Sucellus",
			"General Graardor", "Giant Mole", "Grotesque Guardians", "Hespori",
			"Kalphite Queen", "King Black Dragon", "Kraken", "Kree'Arra",
			"K'ril Tsutsaroth", "Lunar Chests", "Mimic", "Nex", "Nightmare",
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
		m.put("nightmare", "Nightmare");
		return m;
	}

	private static String normalize(String s)
	{
		return s.toLowerCase().replace("'", "").replace(":", "")
			.replaceAll("\\s+", " ").trim();
	}

	/**
	 * Async handler. Runs on a background thread per ChatCommandManager.registerCommandAsync.
	 * Blocking I/O (ClogService future) is fine here; UI work jumps to clientThread.
	 */
	void handle(ChatMessage chatMessage, String message)
	{
		String[] parts = message.split("\\s+", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty())
		{
			replaceText(chatMessage, "usage !kclog <boss>");
			return;
		}

		String query = normalize(parts[1]);
		String resolved = ALIASES.get(query);
		if (resolved == null)
		{
			// Loose substring fallback so partial typing still works ("abyssal" → "Abyssal Sire").
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

		String rsn = Text.sanitize(chatMessage.getName());
		final String boss = resolved;
		ClogResult cl;
		try
		{
			cl = clogService.lookup(rsn).get();
		}
		catch (Exception e)
		{
			log.warn("!kclog lookup failed for {}", rsn, e);
			replaceText(chatMessage, boss + ": lookup failed");
			return;
		}

		if (cl == null)
		{
			replaceText(chatMessage, boss + ": no clog data");
			return;
		}

		String categoryKey = ClogService.bossToCategory(boss);
		final List<ClogResult.ClogItem> obtainedList = cl.getObtainedItems().getOrDefault(categoryKey, Collections.emptyList());
		final List<Integer> totalList = cl.getCategoryItems().getOrDefault(categoryKey, Collections.emptyList());
		final int obtained = obtainedList.size();
		final int total = totalList.size();

		if (total == 0)
		{
			replaceText(chatMessage, boss + ": no clog items found");
			return;
		}

		// Icon registration + chat replacement both need the client thread.
		// AsyncBufferedImage loads the sprite from cache off-thread; if it isn't ready yet
		// we register an onLoaded callback that fires when the worker finishes, then renders.
		clientThread.invoke(() -> renderWithIcons(chatMessage, boss, obtained, total, obtainedList));
	}

	/**
	 * Render the chat replacement, waiting for any unloaded item sprites via AsyncBufferedImage.onLoaded.
	 * Runs on the client thread.
	 */
	private void renderWithIcons(ChatMessage chatMessage, String boss, int obtained, int total,
		List<ClogResult.ClogItem> obtainedList)
	{
		AtomicInteger pending = new AtomicInteger(0);
		Runnable render = () ->
		{
			if (pending.get() > 0) return;
			StringBuilder sb = new StringBuilder();
			sb.append(boss).append(": ").append(obtained).append("/").append(total);
			if (!obtainedList.isEmpty())
			{
				sb.append(" ");
				for (ClogResult.ClogItem item : obtainedList)
				{
					Integer iconId = loadedIcons.get(item.getId());
					if (iconId != null)
					{
						sb.append("<img=").append(iconId).append(">");
					}
				}
			}
			chatMessage.getMessageNode().setValue(sb.toString());
			client.runScript(ScriptID.BUILD_CHATBOX);
		};

		for (ClogResult.ClogItem item : obtainedList)
		{
			final int itemId = item.getId();
			if (loadedIcons.containsKey(itemId)) continue;

			AsyncBufferedImage img;
			try
			{
				img = (AsyncBufferedImage) itemManager.getImage(itemId, 1, false);
			}
			catch (Exception e)
			{
				continue;
			}
			if (img == null) continue;

			if (img.getWidth() > 0 && img.getHeight() > 0)
			{
				registerSafe(itemId, img);
			}
			else
			{
				pending.incrementAndGet();
				img.onLoaded(() -> clientThread.invoke(() ->
				{
					registerSafe(itemId, img);
					pending.decrementAndGet();
					render.run();
				}));
			}
		}

		render.run();
	}

	private void registerSafe(int itemId, AsyncBufferedImage img)
	{
		try
		{
			loadedIcons.put(itemId, chatIconManager.registerChatIcon(img));
		}
		catch (IllegalArgumentException ignored)
		{
		}
	}

	private void replaceText(ChatMessage chatMessage, String text)
	{
		clientThread.invoke(() ->
		{
			chatMessage.getMessageNode().setValue(text);
			client.runScript(ScriptID.BUILD_CHATBOX);
		});
	}
}
