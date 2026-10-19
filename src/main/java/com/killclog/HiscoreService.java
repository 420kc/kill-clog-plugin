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
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Parallelized hiscore lookup with account type detection.
 * Fires the main account-type endpoints simultaneously, then optionally refines
 * regular 1-defence and skiller accounts through their specialty hiscore table.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HiscoreService
{
	private static final String BASE_URL = "https://secure.runescape.com/m=";
	// Every JSON row carries its name, so Jagex inserting, reordering, or
	// renaming hiscore rows cannot misalign the parse.
	private static final String JSON_SUFFIX = "/index_lite.json?player=";

	// The names Kill Clog reads from the hiscores, from hiscore-layout.tsv.
	// Rows are matched by name; the file's order is for reading only.
	private static final String[] SKILL_NAMES = catalogNames("skill");

	// Includes retired entries (Grid Points, Deadman Points, BH Legacy).
	private static final String[] ACTIVITY_NAMES = catalogNames("activity");

	// Boss names as the hiscores spell them. These can diverge from the
	// panel's vanilla display order and names - name-keyed lookups bridge the
	// two lists. A row whose name is not listed is held until a release lists it.
	// Boss update playbook:
	//   1. Add the boss's hiscore name to hiscore-layout.tsv
	//   2. Add an optional enum bridge in PanelData if the official enum can
	//      appear before Kill Clog's next release
	//   3. Once RuneLite exposes the enum, promote it from optional to the base
	//      BOSSES list in PanelData
	//   4. If HiscoreSkill.getName() != hiscore name, add to PanelData's NAME_OVERRIDES
	//   5. If collection-log provider category keys differ, add to BOSS_CATEGORY_OVERRIDES in ClogService
	private static final String[] BOSS_NAMES = catalogNames("boss");

	/** Rows of the bundled hiscore-layout.tsv with this kind. */
	private static String[] catalogNames(String kind)
	{
		return CatalogTsv.values(HiscoreService.class, "hiscore-layout.tsv", kind)
			.toArray(new String[0]);
	}

	// A short catalog silently drops every row it no longer names. Counts only
	// ever grow, so anything below the shipped floor means the catalog did not
	// load intact and lookups cannot be trusted.
	static
	{
		if (SKILL_NAMES.length < 24 || ACTIVITY_NAMES.length < 20 || BOSS_NAMES.length < 71)
		{
			log.error("hiscore catalog incomplete: {} skills, {} activities, {} bosses",
				SKILL_NAMES.length, ACTIVITY_NAMES.length, BOSS_NAMES.length);
		}
	}

	/** Every skill on the hiscores; the skills tooltip's "To max" counts them. */
	static String[] skillNames()
	{
		return SKILL_NAMES;
	}

	/** Number of bosses the hiscores carry. Used by tests to detect drift. */
	static int bossCount()
	{
		return bossNames().length;
	}

	/** Boss names as the hiscores spell them. */
	static String[] bossNames()
	{
		return BOSS_NAMES;
	}

	// Stale-while-revalidate cache.
	private static final long CACHE_TTL_MS = 2 * 60 * 1000; // 2 minutes

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static class CachedResult
	{
		final HiscoreResult result;
		final long timestamp;

		boolean isStale()
		{
			return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
		}
	}

	private final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();
	private final Cache<String, HiscoreResult> rankTables = CacheBuilder.newBuilder()
		.maximumSize(256).expireAfterWrite(CACHE_TTL_MS, TimeUnit.MILLISECONDS).build();
	private final ConcurrentHashMap<String, CompletableFuture<HiscoreResult>> rankRequests = new ConcurrentHashMap<>();
	/** Boards Jagex answered "not found" on: the player has no row there to show. */
	// While Jagex is failing, lookups wait it out instead of adding to its load (as every other provider's do).
	private final CircuitBreaker breaker = new CircuitBreaker("Jagex hiscores");
	private final Cache<String, Boolean> absentTables = CacheBuilder.newBuilder()
		.maximumSize(256).expireAfterWrite(CACHE_TTL_MS, TimeUnit.MILLISECONDS).build();

	static final String LEAGUE_TABLE = "hiscore_oldschool_seasonal";
	// killclog.com says the same, word for word.
	static final String DOWN_MESSAGE = "Jagex hiscores are down. Try again soon.";

	/** Jagex's hiscores answered nothing for a lookup, not even "not found": down, never "no such player". */
	static final class HiscoresDown extends RuntimeException
	{
		HiscoresDown()
		{
			super("Jagex hiscores are down", null, false, false);
		}
	}

	/** What a failed lookup tells the player: the hiscores are down (unanswered past the deadline too), or it failed. */
	static String failureText(@Nullable Throwable error)
	{
		for (Throwable cause = error; cause != null; cause = cause.getCause())
		{
			if (cause instanceof HiscoresDown || cause instanceof java.util.concurrent.TimeoutException)
			{
				return DOWN_MESSAGE;
			}
		}
		return "Lookup failed";
	}

	/** A board with no row for the player: "not found" only on that board's own 404, else Jagex is down. */
	private static <T> CompletableFuture<T> missing(boolean notFound)
	{
		return notFound ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(new HiscoresDown());
	}

	/** The player's League row, null when Jagex says they have none, failing when Jagex says nothing. */
	CompletableFuture<HiscoreResult> lookupLeague(String player)
	{
		// Never a cached "not found": only this lookup's own answer can call the player missing.
		return tableFlight(player, LEAGUE_TABLE);
	}

	CompletableFuture<HiscoreResult> lookupRanks(String player, RankLeaderboard table)
	{
		return lookupTable(player, table.endpoint);
	}

	/** True once Jagex has said this player has no row on this leaderboard. */
	boolean notOnBoard(String player, RankLeaderboard table)
	{
		return notOnBoard(player, table.endpoint);
	}

	private boolean notOnBoard(String player, String endpoint)
	{
		return absentTables.getIfPresent(rankKey(endpoint,
			URLEncoder.encode(player.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8))) != null;
	}

	/** One leaderboard's full row for a player; null when absent or unreachable. */
	CompletableFuture<HiscoreResult> lookupTable(String player, String endpoint)
	{
		if (notOnBoard(player, endpoint)) return CompletableFuture.completedFuture(null);
		return tableFlight(player, endpoint).completeOnTimeout(null, 12, TimeUnit.SECONDS).exceptionally(ex -> null);
	}

	/** One shared fetch of a board's row: null on the board's own 404, failing when Jagex says nothing. */
	private CompletableFuture<HiscoreResult> tableFlight(String player, String endpoint)
	{
		String encoded = URLEncoder.encode(player.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
		String key = rankKey(endpoint, encoded);
		HiscoreResult cached = rankTables.getIfPresent(key);
		if (cached != null) return CompletableFuture.completedFuture(cached);
		Set<String> absent = ConcurrentHashMap.newKeySet();
		return HttpUtil.singleFlightLookup(rankRequests, key, () -> fetchAsync(endpoint, encoded, absent)
			.handle((body, error) -> rankTables.getIfPresent(key))
			.thenCompose(row -> row != null ? CompletableFuture.completedFuture(row) : missing(!absent.isEmpty())));
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
				if (result != null && result.getTotalLevel() > 0)
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

	/**
	 * Get a cached result immediately, or null if none exists.
	 * Used by the panel for stale-while-revalidate: show cached data
	 * instantly, then fire a background refresh.
	 */
	public HiscoreResult getCached(String playerName)
	{
		CachedResult cached = cache.get(RsnInputPolicy.key(playerName));
		return cached != null ? cached.result : null;
	}

	/**
	 * True if the cached result exists but is past TTL and should be refreshed.
	 */
	public boolean isStale(String playerName)
	{
		String key = RsnInputPolicy.key(playerName);
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
		dirtySince.put(RsnInputPolicy.key(playerName), now);
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

	/* package */ synchronized void cacheResult(String playerName, HiscoreResult result, long now)
	{
		String key = RsnInputPolicy.key(playerName);
		cache.put(key, new CachedResult(result, now));
		if (cache.size() > HttpUtil.CACHE_CAP)
		{
			// The name fetched longest ago goes; it is fetched again when asked for.
			cache.entrySet().stream().min(java.util.Comparator.comparingLong(e -> e.getValue().timestamp))
				.ifPresent(oldest -> cache.remove(oldest.getKey(), oldest.getValue()));
		}
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
		// The boards that said "no such player" to this lookup: an earlier or overlapping one's 404 never decides it.
		Set<String> absent = ConcurrentHashMap.newKeySet();
		if (knownType != null) return lookupKnown(encoded, knownType, absent);

		CompletableFuture<String> uimFuture = fetchAsync("hiscore_oldschool_ultimate", encoded, absent);
		CompletableFuture<String> hcimFuture = fetchAsync("hiscore_oldschool_hardcore_ironman", encoded, absent);
		CompletableFuture<String> ironFuture = fetchAsync("hiscore_oldschool_ironman", encoded, absent);
		CompletableFuture<String> regFuture = fetchAsync("hiscore_oldschool", encoded, absent);

		return CompletableFuture.allOf(uimFuture, hcimFuture, ironFuture, regFuture)
			.thenCompose(v ->
			{
				String uimBody = uimFuture.join();
				String hcimBody = hcimFuture.join();
				String ironBody = ironFuture.join();
				String regBody = regFuture.join();

				AccountType type = detectAccountType(uimBody, hcimBody, ironBody, regBody);
				// This lookup's own regular and Ironman answers say whether it's an Ironman now;
				// an Ironman row ahead of the regular one is a mismatched read and says nothing.
				long regXp = extractTotalXp(regBody);
				long ironXp = extractTotalXp(ironBody);
				Boolean ironmanNow = regXp > 0 && ironXp > 0 && ironXp <= regXp
					? Boolean.valueOf(ironXp == regXp) : null;
				// A Hardcore row behind the Ironman one stopped at a death, not when the account left iron.
				long hardcoreXp = extractTotalXp(hcimBody);
				Boolean diedAsHardcore = hardcoreXp > 0 && ironXp > 0 && hardcoreXp <= ironXp
					? Boolean.valueOf(hardcoreXp < ironXp) : null;

				// Missing tables have already used their own bounded retry. Keep the
				// XP cross-check so a frozen HCIM row cannot override fresher stats.
				String bestBody = pickBestBody(type, uimBody, hcimBody, ironBody, regBody);
				if (bestBody == null)
				{
					return missing(absent.contains("hiscore_oldschool"));
				}

				return parseAndRefine(encoded, bestBody, type)
					.thenApply(result -> proven(result, ironmanNow, diedAsHardcore));
			});
	}

	@Nullable
	private static HiscoreResult proven(@Nullable HiscoreResult result, @Nullable Boolean ironmanNow,
		@Nullable Boolean diedAsHardcore)
	{
		if (result != null)
		{
			result.setIronmanNow(ironmanNow);
			result.setDiedAsHardcore(diedAsHardcore);
		}
		return result;
	}

	private CompletableFuture<HiscoreResult> lookupKnown(String encodedPlayer, AccountType type, Set<String> absent)
	{
		CompletableFuture<String> regular = fetchAsync("hiscore_oldschool", encodedPlayer, absent);
		String endpoint;
		switch (type)
		{
			case ULTIMATE_IRONMAN: endpoint = "hiscore_oldschool_ultimate"; break;
			case HARDCORE_IRONMAN: endpoint = "hiscore_oldschool_hardcore_ironman"; break;
			case IRONMAN: endpoint = "hiscore_oldschool_ironman"; break;
			default:
				// RuneLite's own type: not a solo Ironman now (a group ironman reads its regular row).
				return regular.thenCompose(body -> body != null ? parseAndRefine(encodedPlayer, body, type)
					: HiscoreService.<HiscoreResult>missing(absent.contains("hiscore_oldschool")))
					.thenApply(result -> proven(result, false, null));
		}
		return regular.thenCombine(fetchAsync(endpoint, encodedPlayer, absent), (base, ranked) ->
		{
			if (base == null && ranked == null)
			{
				if (absent.contains("hiscore_oldschool")) return null;
				throw new HiscoresDown();
			}
			// RuneLite supplies the current self type. Keep its selected ranks, but
			// do not let a stale specialty row replace fresher regular-table stats.
			String body = base != null && (ranked == null || extractTotalXp(base) >= extractTotalXp(ranked))
				? base : ranked;
			return proven(parseHiscoreBody(body, type)
				.withRanks(ranked != null ? parseHiscoreBody(ranked, type) : null), true, null);
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

		return fetchAsync(table.hiscoreKey(), encodedPlayer, null)
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
			// endpoint variants.
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

	private static final String[] NON_DEFENCE_COMBAT = {"attack", "strength", "ranged", "prayer", "magic"};

	private boolean isSkiller(HiscoreResult result)
	{
		return result.getSkillLevel("defence") == 1
			&& Arrays.stream(NON_DEFENCE_COMBAT).allMatch(skill -> result.getSkillLevel(skill) == 1);
	}

	private boolean isOneDefence(HiscoreResult result)
	{
		return result.getSkillLevel("defence") == 1
			&& Arrays.stream(NON_DEFENCE_COMBAT).anyMatch(skill -> result.getSkillLevel(skill) > 1);
	}

	/* package */ HiscoreResult parseHiscoreBody(String body, AccountType type)
	{
		return parseHiscoreBody(body, type, HiscoreTable.STANDARD);
	}

	/**
	 * Name-keyed parse of Jagex's JSON hiscores. Every row carries its name, so
	 * row insertions, reorders (the 2026-07-29 Mimic/Maggot King swap), and
	 * renames cannot misalign anything. A row this release doesn't name (a boss
	 * added since) or can't read is skipped; the rest of the answer stands.
	 */
	/* package */ HiscoreResult parseHiscoreBody(String body, AccountType type,
		HiscoreTable hiscoreTable)
	{
		Board board = new Board();
		JsonObject root = gson.fromJson(body, JsonObject.class);
		for (JsonElement e : root.getAsJsonArray("skills"))
		{
			JsonObject s = e.getAsJsonObject();
			String name = s.get("name").getAsString();
			if ("Overall".equals(name))
			{
				board.overallRank = s.get("rank").getAsInt();
				board.totalLevel = s.get("level").getAsInt();
				board.totalXp = s.get("xp").getAsLong();
				continue;
			}
			// Internal skill keys are the lowercase names.
			board.skill(name.toLowerCase(Locale.ROOT), s.get("rank").getAsInt(), s.get("level").getAsInt(),
				s.get("xp").getAsLong());
		}

		Set<String> activityNames = new HashSet<>(Arrays.asList(ACTIVITY_NAMES));
		Set<String> knownBosses = new HashSet<>(Arrays.asList(BOSS_NAMES));
		for (JsonElement e : root.getAsJsonArray("activities"))
		{
			try
			{
				JsonObject a = e.getAsJsonObject();
				String name = a.get("name").getAsString();
				boolean activity = activityNames.contains(name);
				String key = activity ? name : PanelData.hiscoreName(name);
				if (!activity && !knownBosses.contains(key))
				{
					if (!PanelData.isTfaRow(name))
					{
						// A boss added after this release. Its KC waits for the update
						// that gives it a cell; nothing else in the parse is disturbed.
						log.debug("Unknown hiscore row '{}' - future boss, holding", name);
						continue;
					}
					// The Fractured Archive before this release's catalog lists it: kept under the raid's own name,
					// so its Combat Summary row shows the day Jagex adds it, whatever Jagex spells it.
					key = PanelData.TFA_HISCORE;
				}
				int rank = a.get("rank").getAsInt();
				int score = a.get("score").getAsInt();
				(activity ? board.activityScores : board.bossKills).put(key, score);
				(activity ? board.activityRanks : board.bossRanks).put(key, rank);
			}
			catch (RuntimeException ignored)
			{
				// A row without its name, rank or score says nothing; it never costs the rows around it.
			}
		}

		return board.result(type, hiscoreTable, calcCmbLvlExact(board.skillLevels));
	}

	/** One read of a hiscores answer as it fills in; result() makes it what lookups use. */
	private static final class Board
	{
		final Map<String, Integer> bossKills = new LinkedHashMap<>();
		final Map<String, Integer> bossRanks = new LinkedHashMap<>();
		final Map<String, Integer> activityScores = new LinkedHashMap<>();
		final Map<String, Integer> activityRanks = new LinkedHashMap<>();
		final Map<String, Integer> skillLevels = new LinkedHashMap<>();
		final Map<String, Integer> skillRanks = new LinkedHashMap<>();
		final Map<String, Long> skillXps = new LinkedHashMap<>();
		int totalLevel = -1;
		long totalXp = -1;
		int overallRank = -1;

		void skill(String key, int rank, int level, long xp)
		{
			skillRanks.put(key, rank);
			skillLevels.put(key, level);
			skillXps.put(key, xp);
		}

		HiscoreResult result(AccountType type, HiscoreTable table, double combatLevel)
		{
			return new HiscoreResult(type, table, bossKills, bossRanks, activityScores,
				activityRanks, skillLevels, skillRanks, skillXps, totalLevel, totalXp, combatLevel, overallRank);
		}
	}

	/** The unrounded combat level, the value vanilla's hiscore calls Exact Combat Level. */
	/* package */ double calcCmbLvlExact(Map<String, Integer> levels)
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

			return base + Math.max(melee, Math.max(range, mage));
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private enum FetchStatus
	{
		FOUND, NOT_FOUND, TRANSIENT, MALFORMED, REJECTED
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class TableResponse
	{
		final FetchStatus status;
		final String body;

		boolean retryable()
		{
			return status == FetchStatus.TRANSIENT || status == FetchStatus.MALFORMED;
		}
	}

	/** How long a failed board waits before its one retry; tests shorten it. */
	static long retryDelayMs = 500;

	private CompletableFuture<String> fetchAsync(String hiscoreKey, String encodedPlayer, @Nullable Set<String> absent)
	{
		return fetchTable(hiscoreKey, encodedPlayer).thenCompose(first ->
		{
			if (!first.retryable()) return CompletableFuture.completedFuture(first);
			return CompletableFuture.supplyAsync(() -> null,
				CompletableFuture.delayedExecutor(retryDelayMs, TimeUnit.MILLISECONDS))
				.thenCompose(ignored -> fetchTable(hiscoreKey, encodedPlayer));
		}).thenApply(result ->
		{
			// Any answer but a 404 retires an earlier one: the player may have just been renamed or ranked.
			String key = rankKey(hiscoreKey, encodedPlayer);
			if (result.status == FetchStatus.NOT_FOUND)
			{
				absentTables.put(key, true);
				if (absent != null)
				{
					absent.add(hiscoreKey);
				}
			}
			else
			{
				absentTables.invalidate(key);
			}
			return rememberRanks(hiscoreKey, encodedPlayer, result.body);
		});
	}

	private CompletableFuture<TableResponse> fetchTable(String hiscoreKey, String encodedPlayer)
	{
		if (breaker.isOpen()) return CompletableFuture.completedFuture(new TableResponse(FetchStatus.REJECTED, null));
		return HttpUtil.httpGet(httpClient, BASE_URL + hiscoreKey + JSON_SUFFIX + encodedPlayer)
			.thenApply(response ->
			{
				TableResponse table = classify(response);
				// An outage, a rate limit and a body that isn't the hiscores all count against Jagex.
				if (table.retryable()) breaker.failure();
				else if (table.status != FetchStatus.REJECTED) breaker.success();
				return table;
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
				JsonObject root = gson.fromJson(body, JsonObject.class);
				for (JsonElement skill : root.getAsJsonArray("skills"))
				{
					if ("Overall".equals(skill.getAsJsonObject().get("name").getAsString()))
					{
						// Parse before accepting: a body that only looks like the hiscores is not a schema.
						parseHiscoreBody(body, AccountType.REGULAR);
						return new TableResponse(FetchStatus.FOUND, body);
					}
				}
			}
			catch (RuntimeException ignored)
			{
				// Malformed and schema-invalid bodies are retried once, then the board is unavailable.
			}
			return new TableResponse(FetchStatus.MALFORMED, null);
		}
		boolean transientFailure = response.code == -1 || response.code == 408
			|| response.code == 429 || response.code >= 500;
		return new TableResponse(transientFailure ? FetchStatus.TRANSIENT : FetchStatus.REJECTED, null);
	}
}
