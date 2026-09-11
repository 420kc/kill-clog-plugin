package com.killclog;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
	// JSON first: every row carries its name, so Jagex inserting, reordering,
	// or renaming hiscore rows cannot misalign the parse. The 2026-07-29
	// update blanked boss KCs for a full release cycle under positional
	// parsing; the CSV is the fallback transport only now.
	private static final String JSON_SUFFIX = "/index_lite.json?player=";
	private static final String CSV_SUFFIX = "/index_lite.ws?player=";

	// Jagex JSON spelling -> internal key, where the internal key predates the feed spelling.
	private static final Map<String, String> JSON_BOSS_NAME_OVERRIDES =
		Map.of("Calvar'ion", "Cal'varion");

	// Skill names in hiscore CSV order (lines 1-24).
	private static final String[] SKILL_NAMES = layoutNames("skill");

	// Activity names in hiscore CSV order.
	// Includes deprecated entries (Grid Points, Deadman Points, BH Legacy).
	// Retired activity rows still occupy CSV lines, so indices must stay aligned.
	private static final String[] ACTIVITY_NAMES = layoutNames("activity");

	// Hiscore CSV layout: line 0 = Overall, then skills, then activities, then bosses.
	// Derived from array lengths so they can never go out of sync.
	private static final int ACTIVITY_START_INDEX = 1 + SKILL_NAMES.length;
	private static final int BOSS_START_INDEX = ACTIVITY_START_INDEX + ACTIVITY_NAMES.length;

	// Boss names in Jagex hiscore CSV row order. This can diverge from the
	// panel's vanilla display order - name-keyed lookups bridge the two lists.
	// The Wyrmscraig update (2026-07-29) re-alphabetized Mimic/Maggot King when
	// inserting Mad Angel, so CSV growth can REORDER neighbors, not just shift
	// the tail: promote the whole list from a live response, never a guessed
	// single insert.
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
	private static final String[] BOSS_NAMES = layoutNames("boss");

	/**
	 * Rows of the bundled hiscore-layout.tsv with this kind, in feed order.
	 * The layout catalog is positional against the Jagex CSV; the parity test
	 * pins its exact contents.
	 */
	private static String[] layoutNames(String kind)
	{
		return CatalogTsv.values(HiscoreService.class, "hiscore-layout.tsv", kind)
			.toArray(new String[0]);
	}

	// The layout is positional against the Jagex CSV: a silently short list
	// would attribute every following row to the wrong name. Counts only ever
	// grow, so anything below the shipped floor means the catalog did not load
	// intact and lookups cannot be trusted.
	static
	{
		if (SKILL_NAMES.length < 24 || ACTIVITY_NAMES.length < 20 || BOSS_NAMES.length < 71)
		{
			log.error("hiscore layout catalog incomplete: {} skills, {} activities, {} bosses",
				SKILL_NAMES.length, ACTIVITY_NAMES.length, BOSS_NAMES.length);
		}
	}

	/** Skill names in CSV order. Used by the layout-parity test. */
	static String[] skillNames()
	{
		return SKILL_NAMES;
	}

	/** Activity names in CSV order. Used by the layout-parity test. */
	static String[] activityNames()
	{
		return ACTIVITY_NAMES;
	}

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
	private final Cache<String, HiscoreResult> rankTables = CacheBuilder.newBuilder()
		.maximumSize(256).expireAfterWrite(CACHE_TTL_MS, TimeUnit.MILLISECONDS).build();
	private final ConcurrentHashMap<String, CompletableFuture<HiscoreResult>> rankRequests = new ConcurrentHashMap<>();

	CompletableFuture<HiscoreResult> lookupRanks(String player, RankLeaderboard table)
	{
		String encoded = URLEncoder.encode(player.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
		String key = rankKey(table.endpoint, encoded);
		HiscoreResult cached = rankTables.getIfPresent(key);
		if (cached != null) return CompletableFuture.completedFuture(cached);
		return HttpUtil.singleFlightLookup(rankRequests, key,
			() -> fetchAsync(table.endpoint, encoded).thenApply(body -> rankTables.getIfPresent(key)))
			.completeOnTimeout(null, 12, TimeUnit.SECONDS).exceptionally(ex -> null);
	}

	private static String rankKey(String endpoint, String encodedPlayer)
	{
		return endpoint + ":" + encodedPlayer.toLowerCase(Locale.ROOT);
	}

	private String rememberRanks(String endpoint, String encodedPlayer, String body)
	{
		if (body != null)
		{
			try
			{
				HiscoreResult result = parseHiscoreBody(body, AccountType.REGULAR);
				if (result != null && result.getTotalLevel() > 0 && !result.isBossSectionShifted())
				{
					rankTables.put(rankKey(endpoint, encodedPlayer), result);
				}
			}
			catch (RuntimeException ignored)
			{
				// An unusable response must not poison either the lookup or rank cache.
			}
		}
		return body;
	}

	// Jagex republishes a player's hiscore row when they log out or hop
	// worlds, so a hop makes any cached self-row instantly outdated. The
	// plugin marks the local player on those transitions and the next
	// self-search bypasses the cache. The row lands server-side a few
	// seconds after the hop; a fetch inside the settle window keeps the
	// mark so the following search refetches too.
	private static final long HOP_SETTLE_MS = 10_000;

	private final ConcurrentHashMap<String, Long> dirtySince = new ConcurrentHashMap<>();

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public HiscoreService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Get a cached result immediately, or null if none exists.
	 * Used by the panel for stale-while-revalidate: show cached data
	 * instantly, then fire a background refresh.
	 */
	public HiscoreResult getCached(String playerName)
	{
		CachedResult cached = cache.get(playerName.toLowerCase(Locale.ROOT));
		return cached != null ? cached.result : null;
	}

	/**
	 * True if the cached result exists but is past TTL and should be refreshed.
	 */
	public boolean isStale(String playerName)
	{
		String key = playerName.toLowerCase(Locale.ROOT);
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
		dirtySince.put(playerName.toLowerCase(Locale.ROOT), now);
	}

	/**
	 * Look up a player across the account-type hiscore endpoints in parallel.
	 * Retries once on transient failure (Jagex 502/503/rate-limit).
	 */
	public CompletableFuture<HiscoreResult> lookup(String playerName, AccountType knownType)
	{
		return attemptLookup(playerName, knownType).thenApply(result ->
		{
			if (result != null) cacheResult(playerName, result);
			return result;
		});
	}

	private void cacheResult(String playerName, HiscoreResult result)
	{
		cacheResult(playerName, result, System.currentTimeMillis());
	}

	/* package */ void cacheResult(String playerName, HiscoreResult result, long now)
	{
		String key = playerName.toLowerCase(Locale.ROOT);
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
		if (knownType != null) return lookupKnown(encoded, knownType);

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

				// Missing tables have already used their own bounded retry. Keep the
				// XP cross-check so a frozen HCIM row cannot override fresher stats.
				String bestBody = pickBestBody(type, uimBody, hcimBody, ironBody, regBody);
				if (bestBody == null)
				{
					return CompletableFuture.completedFuture(null);
				}

				return parseAndRefine(encoded, bestBody, type);
			});
	}

	private CompletableFuture<HiscoreResult> lookupKnown(String encodedPlayer, AccountType type)
	{
		CompletableFuture<String> regular = fetchAsync("hiscore_oldschool", encodedPlayer);
		String endpoint;
		switch (type)
		{
			case ULTIMATE_IRONMAN: endpoint = "hiscore_oldschool_ultimate"; break;
			case HARDCORE_IRONMAN: endpoint = "hiscore_oldschool_hardcore_ironman"; break;
			case IRONMAN: endpoint = "hiscore_oldschool_ironman"; break;
			default:
				return regular.thenCompose(body -> body != null ? parseAndRefine(encodedPlayer, body, type)
					: CompletableFuture.completedFuture(null));
		}
		return regular.thenCombine(fetchAsync(endpoint, encodedPlayer), (base, ranked) ->
		{
			if (base == null && ranked == null) return null;
			// RuneLite supplies the current self type. Keep its selected ranks, but
			// do not let a stale specialty row replace fresher regular-table stats.
			String body = base != null && (ranked == null || extractTotalXp(base) >= extractTotalXp(ranked))
				? base : ranked;
			return parseHiscoreBody(body, type)
				.withRanks(ranked != null ? parseHiscoreBody(ranked, type) : null);
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
			// Account-type detection compares this value across the four
			// endpoint variants, and the transport is JSON-first now: read
			// Overall xp from whichever shape arrived.
			if (body.trim().startsWith("{"))
			{
				JsonObject root = gson.fromJson(body, JsonObject.class);
				for (JsonElement e : root.getAsJsonArray("skills"))
				{
					JsonObject s = e.getAsJsonObject();
					if ("Overall".equals(s.get("name").getAsString()))
					{
						return s.get("xp").getAsLong();
					}
				}
				return -1;
			}
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
		if (body != null && body.trim().startsWith("{"))
		{
			return parseHiscoreJson(body, type, hiscoreTable);
		}
		String[] lines = body.trim().split("\\r?\\n");
		String[] bossNames = bossNames();
		int expected = 1 + SKILL_NAMES.length + ACTIVITY_NAMES.length + bossNames.length;
		boolean bossSectionShifted = lines.length != expected;
		if (bossSectionShifted)
		{
			log.warn("Hiscore CSV line count changed: expected {} but got {} - boss rows parse "
				+ "best-effort and every boss surface carries the misalignment notice",
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

		// This CSV path only runs when the JSON endpoint is down, and a count
		// mismatch means rows at and below the change wear their neighbors'
		// numbers. The 2026-07-29 outage set the rule - visibly imperfect beats blank:
		// parse best-effort and keep the shifted flag set so every boss surface
		// shows the misalignment notice. Never silently wrong.
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
	/**
	 * Name-keyed parse of Jagex's JSON hiscores. Every row carries its name,
	 * so this path is structurally immune to the CSV failure class: row
	 * insertions, reorders (the 2026-07-29 Mimic/Maggot King swap), and
	 * renames cannot misalign anything. Unknown names are future bosses and
	 * simply wait for their display cell; the shifted flag is never set here.
	 */
	/* package */ HiscoreResult parseHiscoreJson(String body, AccountType type,
		HiscoreTable hiscoreTable)
	{
		Map<String, Integer> bossKills = new LinkedHashMap<>();
		Map<String, Integer> bossRanks = new LinkedHashMap<>();
		Map<String, Integer> activityScores = new LinkedHashMap<>();
		Map<String, Integer> activityRanks = new LinkedHashMap<>();
		Map<String, Integer> skillLevels = new LinkedHashMap<>();
		Map<String, Integer> skillRanks = new LinkedHashMap<>();
		Map<String, Long> skillXps = new LinkedHashMap<>();

		int totalLevel = -1;
		long totalXp = -1;
		int overallRank = -1;

		JsonObject root = gson.fromJson(body, JsonObject.class);
		for (JsonElement e : root.getAsJsonArray("skills"))
		{
			JsonObject s = e.getAsJsonObject();
			String name = s.get("name").getAsString();
			if ("Overall".equals(name))
			{
				overallRank = s.get("rank").getAsInt();
				totalLevel = s.get("level").getAsInt();
				totalXp = s.get("xp").getAsLong();
				continue;
			}
			// Internal skill keys are the CSV-era lowercase names.
			String key = name.toLowerCase(Locale.ROOT);
			skillRanks.put(key, s.get("rank").getAsInt());
			skillLevels.put(key, s.get("level").getAsInt());
			skillXps.put(key, s.get("xp").getAsLong());
		}

		Set<String> activityNames = new HashSet<>(Arrays.asList(ACTIVITY_NAMES));
		Set<String> knownBosses = new HashSet<>(Arrays.asList(BOSS_NAMES));
		for (JsonElement e : root.getAsJsonArray("activities"))
		{
			JsonObject a = e.getAsJsonObject();
			String name = a.get("name").getAsString();
			int rank = a.get("rank").getAsInt();
			int score = a.get("score").getAsInt();
			if (activityNames.contains(name))
			{
				activityScores.put(name, score);
				activityRanks.put(name, rank);
				continue;
			}
			String key = JSON_BOSS_NAME_OVERRIDES.getOrDefault(name, name);
			if (knownBosses.contains(key))
			{
				bossKills.put(key, score);
				bossRanks.put(key, rank);
			}
			else
			{
				// A boss added after this release. Its KC waits for the update
				// that gives it a cell; nothing else in the parse is disturbed.
				log.debug("Unknown hiscore row '{}' (score {}) - future boss, holding", name, score);
			}
		}

		int combatLevel = calcCmbLvlFromLevels(skillLevels);

		HiscoreResult result = new HiscoreResult(type, hiscoreTable, bossKills, bossRanks,
			activityScores, activityRanks, skillLevels, skillRanks, skillXps, totalLevel,
			totalXp, combatLevel, overallRank);
		result.setBossSectionShifted(false);
		return result;
	}

	/* package */ int calcCmbLvlFromLevels(Map<String, Integer> levels)
	{
		try
		{
			int attack = levels.get("attack");
			int defence = levels.get("defence");
			int strength = levels.get("strength");
			int hitpoints = levels.get("hitpoints");
			int ranged = levels.get("ranged");
			int prayer = levels.get("prayer");
			int magic = levels.get("magic");

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

	/* package */ int calcCmbLvl(String[] lines)
	{
		try
		{
			Map<String, Integer> levels = new LinkedHashMap<>();
			levels.put("attack", parseSkillLevel(lines, 1));
			levels.put("defence", parseSkillLevel(lines, 2));
			levels.put("strength", parseSkillLevel(lines, 3));
			levels.put("hitpoints", parseSkillLevel(lines, 4));
			levels.put("ranged", parseSkillLevel(lines, 5));
			levels.put("prayer", parseSkillLevel(lines, 6));
			levels.put("magic", parseSkillLevel(lines, 7));
			return calcCmbLvlFromLevels(levels);
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

	private enum FetchStatus
	{
		FOUND, NOT_FOUND, TRANSIENT, MALFORMED, REJECTED
	}

	private static final class TableResponse
	{
		final FetchStatus status;
		final String body;

		TableResponse(FetchStatus status, String body)
		{
			this.status = status;
			this.body = body;
		}

		boolean retryable()
		{
			return status == FetchStatus.TRANSIENT || status == FetchStatus.MALFORMED;
		}
	}

	private CompletableFuture<String> fetchAsync(String hiscoreKey, String encodedPlayer)
	{
		return fetchTable(hiscoreKey, encodedPlayer).thenCompose(first ->
		{
			if (!first.retryable()) return CompletableFuture.completedFuture(first);
			return CompletableFuture.supplyAsync(() -> null,
				CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))
				.thenCompose(ignored -> fetchTable(hiscoreKey, encodedPlayer));
		}).thenApply(result -> rememberRanks(hiscoreKey, encodedPlayer, result.body));
	}

	private CompletableFuture<TableResponse> fetchTable(String hiscoreKey, String encodedPlayer)
	{
		return HttpUtil.httpGet(httpClient, BASE_URL + hiscoreKey + JSON_SUFFIX + encodedPlayer)
			.thenCompose(response ->
			{
				TableResponse json = classify(response);
				if (!json.retryable()) return CompletableFuture.completedFuture(json);
				return HttpUtil.httpGet(httpClient, BASE_URL + hiscoreKey + CSV_SUFFIX + encodedPlayer)
					.thenApply(csvResponse ->
					{
						TableResponse csv = classify(csvResponse);
						// Only the primary endpoint can establish absence. A missing
						// fallback after an outage or bad JSON remains retryable.
						return csv.status == FetchStatus.FOUND ? csv : json;
					});
			});
	}

	private TableResponse classify(HttpUtil.HttpResult response)
	{
		if (response.code == 404) return new TableResponse(FetchStatus.NOT_FOUND, null);
		if (response.code == 200)
		{
			try
			{
				String body = response.body;
				boolean overallFound = false;
				if (body != null && body.trim().startsWith("{"))
				{
					JsonObject root = gson.fromJson(body, JsonObject.class);
					for (JsonElement skill : root.getAsJsonArray("skills"))
					{
						if ("Overall".equals(skill.getAsJsonObject().get("name").getAsString())) overallFound = true;
					}
				}
				else if (body != null)
				{
					String[] overall = body.trim().split("\\r?\\n", 2)[0].split(",");
					Integer.parseInt(overall[0]);
					Integer.parseInt(overall[1]);
					Long.parseLong(overall[2]);
					overallFound = true;
				}
				if (overallFound)
				{
					// Parse before accepting JSON: a leading brace alone is not a schema.
					// CSV keeps its existing best-effort parse and shifted-row warning.
					parseHiscoreBody(body, AccountType.REGULAR);
					return new TableResponse(FetchStatus.FOUND, body);
				}
			}
			catch (RuntimeException ignored)
			{
				// Malformed and schema-invalid bodies use the same bounded fallback.
			}
			return new TableResponse(FetchStatus.MALFORMED, null);
		}
		boolean transientFailure = response.code == -1 || response.code == 408
			|| response.code == 429 || response.code >= 500;
		return new TableResponse(transientFailure ? FetchStatus.TRANSIENT : FetchStatus.REJECTED, null);
	}
}
