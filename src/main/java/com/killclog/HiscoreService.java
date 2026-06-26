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

	// Boss names in hiscore CSV order. Must match Jagex's order.
	// Boss update playbook:
	//   1. Add name here in alphabetical order
	//   2. Wait for RuneLite to add HiscoreSkill enum (check latest.release jar)
	//   3. Add HiscoreSkill.BOSS_NAME to BOSSES[] in PanelData (alphabetical)
	//   4. If HiscoreSkill.getName() != CSV name, add to NAME_OVERRIDES
	//   5. If collection-log provider category keys differ, add to BOSS_CATEGORY_OVERRIDES in ClogService
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
		"K'ril Tsutsaroth", "Lunar Chests", "Mimic", "Nex",
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

	/** Number of boss entries in the hiscore CSV. Used by tests to detect drift. */
	static int bossCount()
	{
		return BOSS_NAMES.length;
	}

	/** Boss names in CSV order. Used by tests to validate PanelData consistency. */
	static String[] bossNames()
	{
		return BOSS_NAMES;
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
		CachedResult cached = cache.get(playerName.toLowerCase());
		return cached == null || cached.isStale();
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
				cache.put(playerName.toLowerCase(),
					new CachedResult(result, System.currentTimeMillis()));
				return CompletableFuture.completedFuture(result);
			}
			CompletableFuture<HiscoreResult> retry = new CompletableFuture<>();
			CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
				.execute(() -> attemptLookup(playerName, knownType)
					.whenComplete((r, ex) ->
					{
						if (r != null)
						{
							cache.put(playerName.toLowerCase(),
								new CachedResult(r, System.currentTimeMillis()));
						}
						retry.complete(r);
					}));
			// Safety net: complete with null if the retry hangs
			retry.completeOnTimeout(null, 12, TimeUnit.SECONDS);
			return retry;
		});
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
		int expected = 1 + SKILL_NAMES.length + ACTIVITY_NAMES.length + BOSS_NAMES.length;
		if (lines.length != expected)
		{
			log.warn("Hiscore CSV line count changed: expected {} but got {} - boss data may have shifted",
				expected, lines.length);
		}
		Map<String, Integer> bossKills = new LinkedHashMap<>();
		Map<String, Integer> bossRanks = new LinkedHashMap<>();
		Map<String, Integer> activityScores = new LinkedHashMap<>();
		Map<String, Integer> activityRanks = new LinkedHashMap<>();

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

		Map<String, Integer> skillLevels = new LinkedHashMap<>();
		for (int i = 0; i < SKILL_NAMES.length; i++)
		{
			int lineIdx = 1 + i;
			if (lineIdx >= lines.length)
			{
				break;
			}
			try
			{
				skillLevels.put(SKILL_NAMES[i], parseSkillLevel(lines, lineIdx));
			}
			catch (Exception e)
			{
				skillLevels.put(SKILL_NAMES[i], -1);
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

		for (int i = 0; i < BOSS_NAMES.length; i++)
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
				bossKills.put(BOSS_NAMES[i], kc);
				bossRanks.put(BOSS_NAMES[i], rank);
			}
			catch (Exception e)
			{
				bossKills.put(BOSS_NAMES[i], -1);
				bossRanks.put(BOSS_NAMES[i], -1);
			}
		}

		return new HiscoreResult(type, hiscoreTable, bossKills, bossRanks, activityScores,
			activityRanks, skillLevels, totalLevel, totalXp, combatLevel, overallRank);
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
