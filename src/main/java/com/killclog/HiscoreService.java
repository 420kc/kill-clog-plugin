package com.killclog;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Parallelized hiscore lookup with account type detection.
 * Fires the main account-type endpoints simultaneously, then optionally refines
 * regular 1-defence and skiller accounts through their specialty hiscore table.
 */
@Slf4j
@Singleton
public class HiscoreService
{
	private static final String BASE_URL = "https://secure.runescape.com/m=";
	private static final String SUFFIX = "/index_lite.ws?player=";

	// Skill names in hiscore CSV order (lines 1-24).
	private static final String[] SKILL_NAMES = {
		"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
		"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting",
		"smithing", "mining", "herblore", "agility", "thieving", "slayer",
		"farming", "runecraft", "hunter", "construction", "sailing"
	};

	// Activity names in hiscore CSV order.
	// Includes deprecated entries (Grid Points, Deadman Points, BH Legacy).
	// Retired activity rows still occupy CSV lines, so indices must stay aligned.
	private static final String[] ACTIVITY_NAMES = {
		"Grid Points",
		"League Points",
		"Deadman Points",
		"Bounty Hunter - Hunter", "Bounty Hunter - Rogue",
		"Bounty Hunter (Legacy) - Hunter", "Bounty Hunter (Legacy) - Rogue",
		"Clue Scrolls (all)", "Clue Scrolls (beginner)", "Clue Scrolls (easy)",
		"Clue Scrolls (medium)", "Clue Scrolls (hard)", "Clue Scrolls (elite)",
		"Clue Scrolls (master)", "LMS - Rank", "PvP Arena - Rank",
		"Soul Wars Zeal", "Rifts closed", "Colosseum Glory", "Collections Logged"
	};

	// Hiscore CSV layout: line 0 = Overall, then skills, then activities, then bosses.
	// Derived from array lengths so they can never go out of sync.
	private static final int ACTIVITY_START_INDEX = 1 + SKILL_NAMES.length;
	private static final int BOSS_START_INDEX = ACTIVITY_START_INDEX + ACTIVITY_NAMES.length;

	// Boss names in Jagex hiscore CSV row order. This can diverge from the
	// panel's vanilla display order (Maggot King parses after Mimic, renders
	// before it) - name-keyed lookups bridge the two lists.
	// Boss update playbook:
	//   1. Add the CSV name here in Jagex hiscore order
	//   2. Add a temporary bossNamesForLineCount guard if Jagex ships the row
	//      before RuneLite ships the enum
	//   3. Add an optional enum bridge in PanelData if the official enum can
	//      appear before Kill Clog's next release
	//   4. Once RuneLite exposes the enum, promote it from optional to the base
	//      BOSSES list in PanelData and move the CSV name into BOSS_NAMES
	//   5. If HiscoreSkill.getName() != CSV name, add to NAME_OVERRIDES
	//   6. If collection-log provider category keys differ, add to BOSS_CATEGORY_OVERRIDES in ClogService
	private static final String[] BOSS_NAMES = {
		"Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor",
		"Artio", "Barrows Chests", "Brutus", "Bryophyta", "Callisto",
		"Cal'varion", "Cerberus", "Chambers of Xeric",
		"Chambers of Xeric: Challenge Mode", "Chaos Elemental", "Chaos Fanatic",
		"Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist",
		"Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme",
		"Deranged Archaeologist", "Doom of Mokhaiotl", "Duke Sucellus",
		"General Graardor", "Giant Mole", "Grotesque Guardians", "Hespori",
		"Kalphite Queen", "King Black Dragon", "Kraken", "Kree'Arra",
		"K'ril Tsutsaroth", "Lunar Chests", "Mimic", "Maggot King", "Nex",
		"Nightmare", "Phosani's Nightmare", "Obor",
		"Phantom Muspah", "Sarachnis", "Scorpia", "Scurrius",
		"Shellbane Gryphon", "Skotizo", "Sol Heredit", "Spindel", "Tempoross",
		"The Gauntlet", "The Corrupted Gauntlet", "The Hueycoatl",
		"The Leviathan", "The Royal Titans", "The Whisperer",
		"Theatre of Blood", "Theatre of Blood: Hard Mode",
		"Thermonuclear Smoke Devil", "Tombs of Amascut",
		"Tombs of Amascut: Expert Mode", "TzKal-Zuk", "TzTok-Jad",
		"Vardorvis", "Venenatis", "Vet'ion", "Vorkath", "Wintertodt",
		"Yama", "Zalcano", "Zulrah"
	};

	// Pending boss slot, Wyrmscraig launch. Jagex ships the CSV row before
	// RuneLite ships the HiscoreSkill enum, so the row is guarded here first
	// and promoted into BOSS_NAMES once the enum lands (playbook steps 2 and 4).
	//
	// ARMING THIS IS A ONE-LINE CHANGE: set MAD_ANGEL_FOLLOWS to the CSV name
	// the new row lands immediately after, read off a real hiscore response on
	// release day. While it stays null the guard is inert and a longer CSV
	// still fails the boss section closed - a wrong insertion point would ship
	// silently shifted KCs for every boss below it, which is the exact failure
	// b69e9d99 hardened against. Guessing is worse than blank.
	private static final String MAD_ANGEL = "The Mad Angel";
	private static final String MAD_ANGEL_FOLLOWS = null;
	private static final String[] BOSS_NAMES_WITH_MAD_ANGEL = MAD_ANGEL_FOLLOWS == null
		? null
		: insertBossAfter(BOSS_NAMES, MAD_ANGEL_FOLLOWS, MAD_ANGEL);

	/** Number of boss entries in the hiscore CSV. Used by tests to detect drift. */
	static int bossCount()
	{
		return bossNames().length;
	}

	/** Boss names in CSV order. Used by tests to validate PanelData consistency. */
	static String[] bossNames()
	{
		return BOSS_NAMES;
	}

	/**
	 * Boss names matching the CSV length being parsed. When Jagex adds a boss
	 * before RuneLite exposes its enum, an armed pending slot keeps every boss
	 * below the insertion point aligned instead of failing the section closed.
	 * Returns the plain list while the slot is unarmed, so the caller's
	 * count check still rejects an unexplained length.
	 */
	static String[] bossNamesForLineCount(int lineCount)
	{
		return bossNamesFor(lineCount, BOSS_NAMES_WITH_MAD_ANGEL);
	}

	/**
	 * Selection logic behind {@link #bossNamesForLineCount}, split out so tests
	 * can exercise the armed path with a simulated pending list while the real
	 * slot is still parked. Release day arms the real one; this is what proves
	 * the mechanism works before then.
	 */
	static String[] bossNamesFor(int lineCount, String[] pending)
	{
		if (pending != null && lineCount == BOSS_START_INDEX + pending.length)
		{
			return pending;
		}
		return BOSS_NAMES;
	}

	/** True once the pending boss slot carries a confirmed CSV position. */
	static boolean pendingBossArmed()
	{
		return BOSS_NAMES_WITH_MAD_ANGEL != null;
	}

	static String[] insertBossAfter(String[] names, String afterName, String bossName)
	{
		String[] next = new String[names.length + 1];
		int out = 0;
		boolean inserted = false;
		for (String name : names)
		{
			next[out++] = name;
			if (!inserted && afterName.equals(name))
			{
				next[out++] = bossName;
				inserted = true;
			}
		}
		if (!inserted)
		{
			next[next.length - 1] = bossName;
		}
		return next;
	}

	// Stale-while-revalidate cache.
	private static final long CACHE_TTL_MS = 2 * 60 * 1000; // 2 minutes

	private static class CachedResult
	{
		final HiscoreResult result;
		final long timestamp;

		CachedResult(HiscoreResult result, long timestamp)
		{
			this.result = result;
			this.timestamp = timestamp;
		}

		boolean isStale()
		{
			return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
		}
	}

	private final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();

	// Jagex republishes a player's hiscore row when they log out or hop
	// worlds, so a hop makes any cached self-row instantly outdated. The
	// plugin marks the local player on those transitions and the next
	// self-search bypasses the cache. The row lands server-side a few
	// seconds after the hop; a fetch inside the settle window keeps the
	// mark so the following search refetches too.
	private static final long HOP_SETTLE_MS = 10_000;

	private final ConcurrentHashMap<String, Long> dirtySince = new ConcurrentHashMap<>();

	private final OkHttpClient httpClient;

	@Inject
	public HiscoreService(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	/**
	 * Get a cached result immediately, or null if none exists.
	 * Used by the panel for stale-while-revalidate: show cached data
	 * instantly, then fire a background refresh.
	 */
	public HiscoreResult getCached(String playerName)
	{
		CachedResult cached = cache.get(playerName.toLowerCase());
		return cached != null ? cached.result : null;
	}

	/**
	 * True if the cached result exists but is past TTL and should be refreshed.
	 */
	public boolean isStale(String playerName)
	{
		String key = playerName.toLowerCase();
		CachedResult cached = cache.get(key);
		return cached == null || cached.isStale() || dirtySince.containsKey(key);
	}

	/**
	 * Mark a player's cached row outdated ahead of its TTL. The next lookup
	 * bypasses the cache regardless of entry age.
	 */
	public void markDirty(String playerName)
	{
		markDirty(playerName, System.currentTimeMillis());
	}

	/* package */ void markDirty(String playerName, long now)
	{
		dirtySince.put(playerName.toLowerCase(), now);
	}

	/**
	 * Look up a player across the account-type hiscore endpoints in parallel.
	 * Retries once on transient failure (Jagex 502/503/rate-limit).
	 */
	public CompletableFuture<HiscoreResult> lookup(String playerName, AccountType knownType)
	{
		return attemptLookup(playerName, knownType).thenCompose(result ->
		{
			if (result != null)
			{
				cacheResult(playerName, result);
				return CompletableFuture.completedFuture(result);
			}
			CompletableFuture<HiscoreResult> retry = new CompletableFuture<>();
			CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
				.execute(() -> attemptLookup(playerName, knownType)
					.whenComplete((r, ex) ->
					{
						if (r != null)
						{
							cacheResult(playerName, r);
						}
						retry.complete(r);
					}));
			// Safety net: complete with null if the retry hangs
			retry.completeOnTimeout(null, 12, TimeUnit.SECONDS);
			return retry;
		});
	}

	private void cacheResult(String playerName, HiscoreResult result)
	{
		cacheResult(playerName, result, System.currentTimeMillis());
	}

	/* package */ void cacheResult(String playerName, HiscoreResult result, long now)
	{
		String key = playerName.toLowerCase();
		cache.put(key, new CachedResult(result, now));
		Long markedAt = dirtySince.get(key);
		if (markedAt != null)
		{
			clearMarkIfSettled(key, markedAt, now);
		}
	}

	// A hop can re-mark between a fetch reading the mark and clearing it; the
	// conditional remove only clears the mark the fetch actually observed, so
	// the newer hop's mark survives and the next self-search still refetches.
	/* package */ void clearMarkIfSettled(String key, long observedMark, long now)
	{
		if (now - observedMark >= HOP_SETTLE_MS)
		{
			dirtySince.remove(key, observedMark);
		}
	}

	private CompletableFuture<HiscoreResult> attemptLookup(String playerName,
		AccountType knownType)
	{
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);

		CompletableFuture<String> uimFuture = fetchAsync("hiscore_oldschool_ultimate", encoded);
		CompletableFuture<String> hcimFuture = fetchAsync("hiscore_oldschool_hardcore_ironman", encoded);
		CompletableFuture<String> ironFuture = fetchAsync("hiscore_oldschool_ironman", encoded);
		CompletableFuture<String> regFuture = fetchAsync("hiscore_oldschool", encoded);

		return CompletableFuture.allOf(uimFuture, hcimFuture, ironFuture, regFuture)
			.thenCompose(v ->
			{
				String uimBody = uimFuture.join();
				String hcimBody = hcimFuture.join();
				String ironBody = ironFuture.join();
				String regBody = regFuture.join();

				AccountType type = knownType != null
					? knownType
					: detectAccountType(uimBody, hcimBody, ironBody, regBody);

				// HCIM detected without knownType. Dead HCIMs have frozen XP on the
				// HCIM table forever. If reg or iron failed, retry those two specifically
				// before accepting HCIM. UIM is irrelevant (mutually exclusive).
				if (knownType == null && type == AccountType.HARDCORE_IRONMAN
					&& (regBody == null || ironBody == null))
				{
					log.debug("HCIM detected but reg/iron missing - retrying to confirm");
					CompletableFuture<String> retryReg = regBody != null
						? CompletableFuture.completedFuture(regBody)
						: fetchAsync("hiscore_oldschool", encoded);
					CompletableFuture<String> retryIron = ironBody != null
						? CompletableFuture.completedFuture(ironBody)
						: fetchAsync("hiscore_oldschool_ironman", encoded);

					final String fUim = uimBody;
					final String fHcim = hcimBody;
					return CompletableFuture.allOf(retryReg, retryIron).thenCompose(v2 ->
					{
						String confirmedReg = retryReg.join();
						String confirmedIron = retryIron.join();
						AccountType confirmedType = detectAccountType(
							fUim, fHcim, confirmedIron, confirmedReg);
						String body = pickBestBody(confirmedType,
							fUim, fHcim, confirmedIron, confirmedReg);
						return body != null ? parseAndRefine(encoded, body, confirmedType)
							: CompletableFuture.completedFuture(null);
					});
				}

				String bestBody = pickBestBody(type, uimBody, hcimBody, ironBody, regBody);
				if (bestBody == null)
				{
					return CompletableFuture.completedFuture(null);
				}

				return parseAndRefine(encoded, bestBody, type);
			});
	}

	private CompletableFuture<HiscoreResult> parseAndRefine(String encodedPlayer,
		String body, AccountType type)
	{
		HiscoreResult result = parseHiscoreBody(body, type);
		HiscoreTable table = detectSpecialHiscoreTable(result);
		if (!table.isSpecial())
		{
			return CompletableFuture.completedFuture(result);
		}

		return fetchAsync(table.hiscoreKey(), encodedPlayer)
			.thenApply(refinedBody ->
			{
				if (refinedBody == null)
				{
					return result;
				}

				HiscoreResult refined = parseHiscoreBody(refinedBody, type, table);
				return refined.getTotalXp() > 0 ? refined : result;
			})
			.exceptionally(ex ->
			{
				log.debug("Special hiscore lookup failed for {}: {}",
					table, ex.getMessage());
				return result;
			});
	}

	private AccountType detectAccountType(String uimBody, String hcimBody, String ironBody, String regBody)
	{
		long regXp = extractTotalXp(regBody);
		long uimXp = extractTotalXp(uimBody);
		long hcimXp = extractTotalXp(hcimBody);
		long ironXp = extractTotalXp(ironBody);

		if (uimBody != null && uimXp == regXp && regXp > 0)
		{
			return AccountType.ULTIMATE_IRONMAN;
		}
		if (hcimBody != null && hcimXp == regXp && regXp > 0)
		{
			return AccountType.HARDCORE_IRONMAN;
		}
		if (ironBody != null && ironXp == regXp && regXp > 0)
		{
			return AccountType.IRONMAN;
		}

		// Fallback: regular endpoint failed, so cross-check specialty endpoints.
		// to avoid false positives from dead HCIMs/UIMs with frozen XP
		if (regXp <= 0)
		{
			if (uimBody != null && uimXp > 0 && uimXp == ironXp) return AccountType.ULTIMATE_IRONMAN;
			if (hcimBody != null && hcimXp > 0 && hcimXp == ironXp) return AccountType.HARDCORE_IRONMAN;
			if (ironBody != null && ironXp > 0) return AccountType.IRONMAN;
		}

		return AccountType.REGULAR;
	}

	/* package */ long extractTotalXp(String body)
	{
		if (body == null || body.isEmpty())
		{
			return -1;
		}
		try
		{
			String firstLine = body.trim().split("\\r?\\n")[0];
			String[] parts = firstLine.split(",");
			return parts.length >= 3 ? Long.parseLong(parts[2]) : -1;
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private String pickBestBody(AccountType type, String uim, String hcim, String iron, String reg)
	{
		switch (type)
		{
			case ULTIMATE_IRONMAN:
				return uim;
			case HARDCORE_IRONMAN:
				return hcim;
			case IRONMAN:
				return iron;
			default:
				return reg;
		}
	}

	/* package */ HiscoreTable detectSpecialHiscoreTable(HiscoreResult result)
	{
		if (result == null || result.getAccountType() != AccountType.REGULAR)
		{
			return HiscoreTable.STANDARD;
		}

		if (isSkiller(result))
		{
			return HiscoreTable.SKILLER;
		}
		if (isOneDefence(result))
		{
			return HiscoreTable.ONE_DEFENCE;
		}
		return HiscoreTable.STANDARD;
	}

	private boolean isSkiller(HiscoreResult result)
	{
		return levelIsOne(result, "attack")
			&& levelIsOne(result, "defence")
			&& levelIsOne(result, "strength")
			&& levelIsOne(result, "ranged")
			&& levelIsOne(result, "prayer")
			&& levelIsOne(result, "magic");
	}

	private boolean isOneDefence(HiscoreResult result)
	{
		return levelIsOne(result, "defence")
			&& (levelAboveOne(result, "attack")
				|| levelAboveOne(result, "strength")
				|| levelAboveOne(result, "ranged")
				|| levelAboveOne(result, "prayer")
				|| levelAboveOne(result, "magic"));
	}

	private boolean levelIsOne(HiscoreResult result, String skill)
	{
		return result.getSkillLevel(skill) == 1;
	}

	private boolean levelAboveOne(HiscoreResult result, String skill)
	{
		return result.getSkillLevel(skill) > 1;
	}

	/* package */ HiscoreResult parseHiscoreBody(String body, AccountType type)
	{
		return parseHiscoreBody(body, type, HiscoreTable.STANDARD);
	}

	/* package */ HiscoreResult parseHiscoreBody(String body, AccountType type,
		HiscoreTable hiscoreTable)
	{
		String[] lines = body.trim().split("\\r?\\n");
		String[] bossNames = bossNamesForLineCount(lines.length);
		int expected = 1 + SKILL_NAMES.length + ACTIVITY_NAMES.length + bossNames.length;
		boolean bossSectionShifted = lines.length != expected;
		if (bossSectionShifted)
		{
			log.warn("Hiscore CSV line count changed: expected {} but got {} - failing the boss section closed",
				expected, lines.length);
		}
		Map<String, Integer> bossKills = new LinkedHashMap<>();
		Map<String, Integer> bossRanks = new LinkedHashMap<>();
		Map<String, Integer> activityScores = new LinkedHashMap<>();
		Map<String, Integer> activityRanks = new LinkedHashMap<>();
		Map<String, Integer> skillLevels = new LinkedHashMap<>();
		Map<String, Integer> skillRanks = new LinkedHashMap<>();
		Map<String, Long> skillXps = new LinkedHashMap<>();

		int totalLevel = 0;
		long totalXp = 0;
		int overallRank = -1;
		try
		{
			String[] overall = lines[0].split(",");
			overallRank = Integer.parseInt(overall[0]);
			totalLevel = Integer.parseInt(overall[1]);
			totalXp = Long.parseLong(overall[2]);
		}
		catch (Exception ignored)
		{
		}

		int combatLevel = calcCmbLvl(lines);

		for (int i = 0; i < SKILL_NAMES.length; i++)
		{
			int lineIdx = 1 + i;
			if (lineIdx >= lines.length)
			{
				break;
			}
			try
			{
				String[] parts = lines[lineIdx].split(",");
				skillRanks.put(SKILL_NAMES[i], Integer.parseInt(parts[0]));
				skillLevels.put(SKILL_NAMES[i], Integer.parseInt(parts[1]));
				skillXps.put(SKILL_NAMES[i], Long.parseLong(parts[2]));
			}
			catch (Exception e)
			{
				skillLevels.put(SKILL_NAMES[i], -1);
				skillRanks.put(SKILL_NAMES[i], -1);
				skillXps.put(SKILL_NAMES[i], -1L);
			}
		}

		for (int i = 0; i < ACTIVITY_NAMES.length; i++)
		{
			int lineIdx = ACTIVITY_START_INDEX + i;
			if (lineIdx >= lines.length)
			{
				break;
			}
			try
			{
				String[] parts = lines[lineIdx].split(",");
				int rank = Integer.parseInt(parts[0]);
				int score = Integer.parseInt(parts[1]);
				activityScores.put(ACTIVITY_NAMES[i], score);
				activityRanks.put(ACTIVITY_NAMES[i], rank);
			}
			catch (Exception e)
			{
				activityScores.put(ACTIVITY_NAMES[i], -1);
				activityRanks.put(ACTIVITY_NAMES[i], -1);
			}
		}

		// Format growth historically lands in the boss rows (a new boss shifts
		// every row below its insertion point), so a count mismatch means the
		// positional tail cannot be trusted. Wrong KCs are worse than absent
		// ones: leave the boss maps empty and let every surface show the
		// shifted notice instead. The fixed prefix (overall, skills,
		// activities) stays live; a prefix-level change is a headline event
		// that ships its own same-day release.
		if (!bossSectionShifted)
		{
			for (int i = 0; i < bossNames.length; i++)
			{
				int lineIdx = BOSS_START_INDEX + i;
				if (lineIdx >= lines.length)
				{
					break;
				}
				try
				{
					String[] parts = lines[lineIdx].split(",");
					int rank = Integer.parseInt(parts[0]);
					int kc = Integer.parseInt(parts[1]);
					bossKills.put(bossNames[i], kc);
					bossRanks.put(bossNames[i], rank);
				}
				catch (Exception e)
				{
					bossKills.put(bossNames[i], -1);
					bossRanks.put(bossNames[i], -1);
				}
			}
		}

		HiscoreResult result = new HiscoreResult(type, hiscoreTable, bossKills, bossRanks,
			activityScores, activityRanks, skillLevels, skillRanks, skillXps, totalLevel,
			totalXp, combatLevel, overallRank);
		result.setBossSectionShifted(bossSectionShifted);
		return result;
	}

	/**
	 * Calculate combat level from hiscore CSV skill lines.
	 * Skills: 1=Attack, 2=Defence, 3=Strength, 4=Hitpoints, 5=Ranged, 6=Prayer, 7=Magic
	 */
	/* package */ int calcCmbLvl(String[] lines)
	{
		try
		{
			int attack = parseSkillLevel(lines, 1);
			int defence = parseSkillLevel(lines, 2);
			int strength = parseSkillLevel(lines, 3);
			int hitpoints = parseSkillLevel(lines, 4);
			int ranged = parseSkillLevel(lines, 5);
			int prayer = parseSkillLevel(lines, 6);
			int magic = parseSkillLevel(lines, 7);

			double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
			double melee = 0.325 * (attack + strength);
			double range = 0.325 * (Math.floor(ranged * 3.0 / 2.0));
			double mage = 0.325 * (Math.floor(magic * 3.0 / 2.0));

			return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private int parseSkillLevel(String[] lines, int lineIndex)
	{
		String[] parts = lines[lineIndex].split(",");
		return Integer.parseInt(parts[1]);
	}

	private CompletableFuture<String> fetchAsync(String hiscoreKey, String encodedPlayer)
	{
		return HttpUtil.httpGet(httpClient, BASE_URL + hiscoreKey + SUFFIX + encodedPlayer)
			.thenApply(r -> r.body);
	}
}
