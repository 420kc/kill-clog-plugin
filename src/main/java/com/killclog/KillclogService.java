package com.killclog;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.Nullable;
import net.runelite.api.clan.ClanTitle;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Reads back what players published through their own Kill Clog sync, in two requests to killclog.com.
 *
 * The sync index is the list of synced players, fetched once per interval. A name that never synced is answered from
 * it with no request of its own.
 *
 * The proof view ({@link #lookupClog}) is one GET of {@code /api/player/{rsn}/proof-view} for a player: their own
 * log, personal bests and game counts, never a provider's data. Its bests are kept by panel boss name, so a looked-up
 * player's boss cards can show them. No other source serves personal bests.
 *
 * Answers are kept the way {@link RuneProfileService}'s are: a success, a not-found and a failure each for their own
 * time, one request per name at once, and a last good answer served through a failed one.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class KillclogService
{
	private static final String PROOF_SUFFIX = "/proof-view";

	/** A page's items: the game's own when the client has read the log, else the provider catalog. */
	@Nullable
	private List<Integer> pageItems(String key)
	{
		ClogIndex index = clogIndex;
		List<Integer> ids = index != null ? index.categoryItems().get(key) : null;
		return ids != null || clogService == null ? ids : clogService.getCategoryCatalogIds(key);
	}

	/** Every page a lookup is measured against: the game's own once read, else the provider catalog's. */
	private Set<String> pageKeys()
	{
		ClogIndex index = clogIndex;
		if (index != null && index.isParsed())
		{
			return new java.util.HashSet<>(index.categoryKeys());
		}
		return clogService != null ? clogService.getCatalogCategoryKeys() : Collections.emptySet();
	}

	private static final long INDEX_TTL_MS = 10 * 60 * 1000;      // 10 min: the list of synced players
	// A name the list doesn't hold may have synced since it was fetched: a lookup that misses asks again once the
	// list is a minute old, so a player's first sync shows within a minute, not ten.
	private static final long MISS_RECHECK_MS = 60 * 1000;

	private final CircuitBreaker breaker = new CircuitBreaker("killclog.com");

	// Plausibility ceiling for the game's unique counters: the real log sits
	// around 1.7k slots, so anything past this is a corrupt or hostile value
	// and must not be allowed to decide a coverage race.
	private static final int MAX_PLAUSIBLE_UNIQUE = 50_000;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ClogService clogService;
	// The game's own pages once the client has read the log; the provider catalog stands in before then.
	@Inject
	private ClogIndex clogIndex;
	// The game's task counts, which a sync's Combat Achievements count against as RuneProfile's do.
	@Inject
	private CaCatalog caCatalog;

	// Membership index: normalized names of players who have synced.
	// Null until the first successful fetch; a null index degrades to "skip the
	// killclog leg entirely" so providers keep serving when killclog.com is down.
	@Nullable private volatile Set<String> syncIndex;
	private volatile long indexFetchedAt = 0;
	private volatile long indexFailedAt = 0;
	private final Map<String, CompletableFuture<Set<String>>> indexInFlight = new ConcurrentHashMap<>();

	// Proof-view cache, keyed by lowercase rsn. Same TTL trio as the providers.
	private final HttpUtil.Lane<ClogResult> clogs = new HttpUtil.Lane<>(breaker);

	// Personal bests per player, filled by every successful proof-view fetch whichever source wins the log. Keys are
	// panel boss names, as the sync sends them (gatherPersonalBests keys by HiscoreSkill name), so a lookup needs no
	// translation.
	private final Map<String, Map<String, Double>> pbCache = new ConcurrentHashMap<>();

	/** A player's PB-cache and sync-index key: the shared name key (RsnInputPolicy.key), every spelling Jagex accepts. */
	private static String pbKey(String playerName)
	{
		return RsnInputPolicy.key(playerName);
	}

	/** Cache key for one player in one mode, also accepted by {@link #pbText}; main keeps the plain name. */
	static String modeKey(String mode, String playerName)
	{
		return GameMode.MAIN.equals(mode) ? pbKey(playerName) : mode + "/" + pbKey(playerName);
	}

	/** Clear transient failure cooldowns immediately (called on login); TTLs auto-expire otherwise. */
	public void clearFailures()
	{
		clogs.failed.clear();
		indexFailedAt = 0;
		breaker.reset();
	}

	/**
	 * A player's own synced log. Null when the player has never synced (the index answers, with no request), when
	 * killclog.com can't be reached with nothing cached, or on opt-out. LookupFanout combines this with the providers:
	 * the fullest result leads, and ties go to the player's own sync.
	 */
	public CompletableFuture<ClogResult> lookupClog(String playerName)
	{
		return lookupClog(playerName, GameMode.MAIN);
	}

	/** One mode's synced log: main, or a League from that League's own proof view. */
	CompletableFuture<ClogResult> lookupClog(String playerName, String mode)
	{
		String key = modeKey(mode, playerName);
		// The canonical catalog supplies category denominators; until it
		// warms (a brief startup window), a fresh synced result would
		// render dishonest totals, so the leg waits it out. Providers keep
		// serving, and nothing is recorded against the breaker.
		if (clogs.hold(key) || clogService != null && !clogService.hasCatalog())
		{
			return CompletableFuture.completedFuture(clogs.values.get(key));
		}

		return ensureIndex().thenCompose(index -> index == null || index.contains(pbKey(playerName))
			? CompletableFuture.completedFuture(index) : ensureIndex(MISS_RECHECK_MS)).thenCompose(index ->
		{
			// Unknown membership (index never fetched, server unreachable):
			// keep whatever is cached rather than guessing either way.
			if (index == null)
			{
				return CompletableFuture.completedFuture(clogs.values.get(key));
			}
			// A name the index lacks never synced, or synced and withdrew (an opt-out, an unbind). Its cached log and
			// bests go now, so a withdrawal holds within one index refresh, never for a cache's lifetime.
			if (!index.contains(pbKey(playerName)))
			{
				evictSynced(key);
				return CompletableFuture.completedFuture(null);
			}
			return HttpUtil.singleFlightLookup(clogs.inFlight, key, () -> startProofViewLookup(playerName, key, mode));
		});
	}

	/** Drop everything cached of a player's sync: log, freshness, bests. */
	private void evictSynced(String key)
	{
		clogs.values.remove(key);
		clogs.fetched.remove(key);
		pbCache.remove(key);
	}

	/**
	 * A looked-up synced player's personal best as text, or null when none is cached. A plain cache read, safe from the
	 * Swing thread as a card builds: the map fills in the same fetch that brought the log.
	 */
	@Nullable
	public String pbText(@Nullable String playerName, String panelBossName)
	{
		if (playerName == null)
		{
			return null;
		}
		// A League's key (modeKey) is its id, then a slash, then the name; only the name is a player's to fold.
		int slash = playerName.indexOf('/');
		Map<String, Double> pbs = pbCache.get(slash < 0 ? pbKey(playerName)
			: playerName.substring(0, slash + 1) + pbKey(playerName.substring(slash + 1)));
		if (pbs == null)
		{
			return null;
		}
		Double best = pbs.get(panelBossName);
		return best != null && best > 0 ? PersonalBests.formatSeconds(best)
			: PersonalBests.labeled(PersonalBests.variantSeconds(pbs, panelBossName), panelBossName);
	}

	private CompletableFuture<Set<String>> ensureIndex()
	{
		return ensureIndex(INDEX_TTL_MS);
	}

	/** The sync list, fetched again when the one held is older than {@code maxAgeMs}. */
	private CompletableFuture<Set<String>> ensureIndex(long maxAgeMs)
	{
		long now = System.currentTimeMillis();
		Set<String> cached = syncIndex;
		if (cached != null && now - indexFetchedAt < maxAgeMs)
		{
			return CompletableFuture.completedFuture(cached);
		}
		if (indexFailedAt > 0 && now - indexFailedAt < HttpUtil.Lane.FAILURE_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}
		return HttpUtil.singleFlightLookup(indexInFlight, "index", this::startIndexFetch);
	}

	private CompletableFuture<Set<String>> startIndexFetch()
	{
		return HttpUtil.httpGet(httpClient,
			KillClogEndpoint.apiBaseUrl() + "/player/sync-index").thenApply(resp ->
		{
			Set<String> parsed = resp.code == 200 && resp.body != null ? parseSyncIndex(resp.body) : null;
			if (parsed == null)
			{
				indexFailedAt = System.currentTimeMillis();
				breaker.failure();
				return syncIndex;
			}
			syncIndex = parsed;
			indexFetchedAt = System.currentTimeMillis();
			indexFailedAt = 0;
			breaker.success();
			return parsed;
		});
	}

	// The League the server takes syncs for and the RuneLite profile type holding its PBs,
	// announced together with the index and replaced together. The id names a folder and a
	// path segment, so anything but a plain slug is ignored.
	private volatile String[] activeLeague = new String[2];
	private static final java.util.regex.Pattern LEAGUE_ID = java.util.regex.Pattern.compile("[a-z0-9]+(-[a-z0-9]+){0,5}");
	@Nullable
	String activeLeague()
	{
		return activeLeague[0];
	}

	/** This League's RuneLite profile type, or null while RuneLite files its PBs under another League. */
	@Nullable
	String leagueProfileType(String league)
	{
		String[] active = activeLeague;
		return league.equals(active[0]) ? active[1] : null;
	}

	/** Whether this player syncs to Kill Clog, by the sync list held now; false before the first one arrives. */
	boolean isSynced(@Nullable String playerName)
	{
		Set<String> index = syncIndex;
		return playerName != null && index != null && index.contains(pbKey(playerName));
	}

	/** Login: learn the active League even when no lookup needs the index. */
	void refreshIndex()
	{
		ensureIndex();
	}

	@Nullable
	Set<String> parseSyncIndex(String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonArray list = Json.array(root, "names");
			if (list == null)
			{
				return null;
			}
			Set<String> names = ConcurrentHashMap.newKeySet();
			for (JsonElement el : list)
			{
				JsonPrimitive name = Json.primitive(el);
				if (name != null && name.isString())
				{
					names.add(RsnInputPolicy.key(name.getAsString()));
				}
			}
			String id = Objects.toString(Json.stringField(root, "active_league"), "");
			activeLeague = id.length() <= 40 && LEAGUE_ID.matcher(id).matches() && !GameMode.MAIN.equals(id)
				? new String[]{id, Json.stringField(root, "active_league_profile")} : new String[2];
			return names;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse killclog sync index: {}", e.getMessage());
			return null;
		}
	}

	private CompletableFuture<ClogResult> startProofViewLookup(String playerName, String key, String mode)
	{
		String url = KillClogEndpoint.apiBaseUrl() + "/player/" + HttpUtil.pathSegment(playerName) + PROOF_SUFFIX
			+ (GameMode.MAIN.equals(mode) ? "" : "/" + mode);
		return HttpUtil.httpGet(httpClient, url).thenApply(resp -> onProofViewResponse(resp.code, resp.body, playerName, key));
	}

	/**
	 * What a proof-view answer does to the cache; package-private so tests can drive it without HTTP.
	 *
	 * 404 (never synced, or purged) and 451 (opted out) mean the player isn't there: everything cached of theirs goes
	 * at once, since a withdrawal must not be served for a cache's lifetime. A passing failure keeps what is held, so a
	 * server blip never blanks a good result. One answer at a time, so a player's bests and log are stored and trimmed
	 * together.
	 */
	@Nullable
	synchronized ClogResult onProofViewResponse(int code, @Nullable String body, String playerName, String key)
	{
		if (code == 404 || code == 451)
		{
			evictSynced(key);
			clogs.missing(key);
			return null;
		}
		ClogResult result = code == 200 && body != null ? parseProofView(playerName, body, key) : null;
		if (result == null)
		{
			return clogs.fail(key);
		}
		breaker.success();
		clogs.ok(key, result);
		// A player's PBs live exactly as long as the clog result they came with.
		pbCache.keySet().retainAll(clogs.values.keySet());
		return result;
	}

	/**
	 * Parse a proof-view response into a {@link ClogResult}. Response shape:
	 * <pre>{@code
	 * { exists, rsn, updated_at, last_changed, unique_obtained, unique_total,
	 *   pbs: { "Boss Name": seconds },
	 *   combat_achievements: [{ name, completed, total }],
	 *   clog: { items_by_category: { cat: [{ item_id, quantity, last_seen_at }] },
	 *           total_obtained, capitalized_name } }
	 * }</pre>
	 * Category keys round-trip the plugin's own canon (they were pushed from
	 * it), so no category normalization is applied. Only acquisition dates are
	 * read; server-observed sync timestamps remain separate.
	 */
	@Nullable
	ClogResult parseProofView(String playerName, String json, String key)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonObject clog = Json.object(root, "clog");
			JsonObject byCategory = Json.object(clog, "items_by_category");
			if (byCategory == null)
			{
				return null;
			}

			Map<String, List<ClogResult.ClogItem>> obtainedItems = new HashMap<>();
			Map<String, List<Integer>> categoryItems = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : byCategory.entrySet())
			{
				List<ClogResult.ClogItem> obtained = new ArrayList<>();
				for (JsonObject item : Json.objects(entry.getValue()))
				{
					int id = Json.intField(item, "item_id");
					int qty = Json.intField(item, "quantity");
					if (id <= 0 || qty < 1)
					{
						// A missing or invalid id must not fabricate item 0,
						// and a missing or nonpositive quantity must not
						// fabricate an obtained item into the coverage race.
						continue;
					}
					obtained.add(new ClogResult.ClogItem(id, qty,
						ClogDates.local(Json.stringField(item, "obtained_at"))));
				}
				if (obtained.isEmpty())
				{
					continue;
				}
				obtainedItems.put(entry.getKey(), obtained);
				// The category's full item list comes from the plugin's own
				// canonical catalog, never from the obtained ids: obtained-only
				// "catalogs" would render every category as complete and hide
				// what's missing. Categories the catalog doesn't know yet are
				// simply left without a denominator.
				List<Integer> catalogIds = pageItems(entry.getKey());
				if (catalogIds != null)
				{
					categoryItems.put(entry.getKey(), catalogIds);
				}
			}
			// An empty log is an answer, not a fault: the server serves a valid empty shape for a player whose sync
			// carried bests or counts but no items yet. It goes on to give every page its 0 of N and still keeps the
			// bests below. Returning null would count a healthy answer against the breaker.

			// Never-logged categories are absent from a sync's payload,
			// but absence is data: the player owns none of that catalog. Seed
			// them with catalog denominators so a lookup the sync wins
			// renders every boss as 0-of-N with a dimmed grid instead of the
			// generic unsynced shape.
			if (clogService != null || clogIndex != null)
			{
				for (String catalogKey : pageKeys())
				{
					if (!categoryItems.containsKey(catalogKey))
					{
						List<Integer> ids = pageItems(catalogKey);
						if (ids != null)
						{
							categoryItems.put(catalogKey, ids);
						}
					}
				}
			}

			String displayName = Json.stringField(root, "rsn");
			if (displayName == null)
			{
				displayName = Json.stringField(clog, "capitalized_name");
			}
			ClogResult result = new ClogResult(
				displayName != null ? displayName : playerName,
				obtainedItems,
				categoryItems,
				null,   // item names resolve client-side through ItemManager
				ClogDates.local(Json.stringField(root, "last_changed")),
				AccountType.fromProviderValue(Json.stringField(root, "account_type"))
			);
			// The obtained count decides which log leads, so it must be believable: a total under the ceiling and a
			// count within it. A count without a credible total is dropped, and coverage falls back to the distinct items
			// read; a believable total alone still serves as the denominator.
			int uniqueObtained = Json.intField(root, "unique_obtained");
			int uniqueTotal = Json.intField(root, "unique_total");
			boolean totalPlausible = uniqueTotal > 0 && uniqueTotal <= MAX_PLAUSIBLE_UNIQUE;
			if (totalPlausible)
			{
				result.setUniqueTotal(uniqueTotal);
				if (uniqueObtained > 0 && uniqueObtained <= uniqueTotal)
				{
					result.setUniqueObtained(uniqueObtained);
				}
			}
			if (root.has(HighGambles.FIELD))
			{
				result.setHighGambles(HighGambles.plausible(Json.intField(root, HighGambles.FIELD)));
			}
			JsonObject counts = Json.object(root, PageCounts.FIELD);
			// Only the lines Kill Clog reads, so a reply can't fill a log with names it never shows; a value that isn't
			// a count is skipped, never the whole lookup.
			for (PageCounts.Line line : PageCounts.LINES)
			{
				double number = Json.number(Json.get(counts, line.label));
				int count = HighGambles.plausible(number > Integer.MAX_VALUE ? -1 : (int) number);
				if (count >= 0)
				{
					result.getPageCounts().put(line.label, count);
				}
			}
			// This footer describes collection-log provenance, not merely account
			// presence on killclog.com. PB-only proof views intentionally parse
			// and cache as healthy empty results, but do not earn a clog source.
			if (!obtainedItems.isEmpty())
			{
				result = result.withSources(false, false, true);
			}
			// The clan the player's own sync named, or null for none: once present, the Player Summary reads it
			// instead of RuneProfile's. Absent until their sync has said.
			result.setSyncedClanKnown(root.has("clan"));
			JsonObject clan = Json.object(root, "clan");
			if (Json.stringField(clan, "name") != null)
			{
				result.setSyncedClan(Json.stringField(clan, "name"));
				result.setSyncedClanTitle(Json.primitive(clan.get("title_id")) != null
					? new ClanTitle(Json.intField(clan, "title_id"), Json.stringField(clan, "title")) : null);
			}
			// The Combat Achievements their sync sent: the Combat Summary shows the fuller of them and RuneProfile's.
			result.setSyncedCa(syncedCa(root));

			pbCache.put(key, parsePbs(root));
			return result;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse killclog proof view: {}", e.getMessage());
			return null;
		}
	}

	/** A sync's tiers, or null for none or a garbled reading, which never costs the log. */
	@Nullable
	private CombatAchievementResult syncedCa(JsonObject root)
	{
		JsonArray rows = Json.array(root, "combat_achievements");
		CaCatalog catalog = caCatalog;
		try
		{
			return rows != null ? CombatAchievementResult.fromRows(rows, catalog != null ? catalog.totals() : null) : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static Map<String, Double> parsePbs(JsonObject root)
	{
		if (Json.object(root, "pbs") == null)
		{
			return Collections.emptyMap();
		}
		Map<String, Double> pbs = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : Json.entries(root.get("pbs")))
		{
			putTime(pbs, entry.getKey(), entry.getValue());
		}
		// A group encounter's times come only by team size, under the game's store keys beside the names above.
		for (Map.Entry<String, JsonElement> entry : Json.entries(root.get("pbs_detailed")))
		{
			putTime(pbs, entry.getKey(), Json.get(Json.object(entry.getValue()), "seconds"));
		}
		return pbs;
	}

	private static void putTime(Map<String, Double> pbs, String name, @Nullable JsonElement value)
	{
		double seconds = Json.number(value);
		if (seconds > 0)
		{
			pbs.put(name, seconds);
		}
	}
}
