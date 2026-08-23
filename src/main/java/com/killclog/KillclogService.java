package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * First-party collection log provider: reads back what players published
 * through their own Kill Clog sync. Two requests, both against killclog.com:
 * <ol>
 *   <li><strong>Sync index</strong> -- the membership list of synced players,
 *       fetched once per interval. A lookup of a never-synced name is answered
 *       from this in-memory set and costs ZERO extra HTTP.</li>
 *   <li><strong>Proof view</strong> ({@link #lookupClog}) -- one GET against
 *       {@code /api/player/{rsn}/proof-view}, parsed into a {@link ClogResult}.
 *       The response is first-party cargo only (the player's own pushed log,
 *       personal bests, and game counters); provider data never rides it.</li>
 * </ol>
 *
 * <p>Freshness model mirrors {@link RuneProfileService}: success TTL, not-found
 * TTL, transient failure cooldown, in-flight dedup, and a circuit breaker.
 * A previous success is still returned while cached, so a server-side blip
 * cannot blank known-good data mid-session.
 *
 * <p>Personal bests are the read leg's defining cargo: no public provider
 * serves them. Each successful proof-view fetch caches the player's pb map
 * (keyed by panel boss name, exactly as the sync payload publishes them) so
 * boss tooltips can show PB lines for looked-up synced players.
 */
@Slf4j
@Singleton
public class KillclogService
{
	private static final String BASE_URL = "https://killclog.com/api/player/";
	private static final String INDEX_URL = BASE_URL + "sync-index";
	private static final String PROOF_SUFFIX = "/proof-view";

	private static final long RESULT_TTL_MS = 5 * 60 * 1000;       // 5 min -- fresh success
	private static final long NOT_FOUND_TTL_MS = 60 * 60 * 1000;  // 1 hour -- never synced / opted out
	private static final long FAILURE_TTL_MS = 3 * 60 * 1000;     // 3 min -- transient failure
	private static final long INDEX_TTL_MS = 10 * 60 * 1000;      // 10 min -- membership list

	// Circuit breaker, same shape as RuneProfileService: trips after
	// BREAKER_THRESHOLD failures within BREAKER_WINDOW_MS, short-circuits for
	// an escalating cooldown, resets on any success.
	private static final int BREAKER_THRESHOLD = 5;
	private static final long BREAKER_WINDOW_MS = 60 * 1000;
	private static final long BREAKER_COOLDOWN_MS = 60 * 1000;
	private static final long BREAKER_MAX_COOLDOWN_MS = 30 * 60 * 1000;

	private final long[] recentFailures = new long[BREAKER_THRESHOLD];
	private int failureIndex = 0;
	private volatile long breakerTrippedAt = 0;
	private volatile long breakerCooldownMs = BREAKER_COOLDOWN_MS;

	// Plausibility ceiling for the game's unique counters: the real log sits
	// around 1.7k slots, so anything past this is a corrupt or hostile value
	// and must not be allowed to decide a coverage race.
	private static final int MAX_PLAUSIBLE_UNIQUE = 50_000;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ClogService clogService;

	// Membership index: normalized names of players with first-party proof.
	// Null until the first successful fetch; a null index degrades to "skip the
	// killclog leg entirely" so providers keep serving when killclog.com is down.
	@Nullable private volatile Set<String> syncIndex;
	private volatile long indexFetchedAt = 0;
	private volatile long indexFailedAt = 0;
	private final Map<String, CompletableFuture<Set<String>>> indexInFlight = new ConcurrentHashMap<>();

	// Proof-view cache, keyed by lowercase rsn. Same TTL trio as the providers.
	private final Map<String, ClogResult> clogCache = new ConcurrentHashMap<>();
	private final Map<String, Long> clogFetchTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> clogNotFoundTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> clogFailures = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<ClogResult>> clogInFlight = new ConcurrentHashMap<>();

	// Personal bests per player, filled by every successful proof-view fetch
	// regardless of which provider wins the clog pick. Keys are panel boss
	// names -- the sync payload publishes them that way (gatherPersonalBests
	// keys by HiscoreSkill name), so lookups need no key translation.
	private final Map<String, Map<String, Double>> pbCache = new ConcurrentHashMap<>();

	/**
	 * The one pb-cache key normalization. Both sides previously used the
	 * default locale - consistent with each other, but wrong for locales
	 * that fold I/i differently (tr/az). Locale.ROOT keys everywhere now.
	 */
	private static String pbKey(String playerName)
	{
		return playerName.toLowerCase(java.util.Locale.ROOT);
	}

	@Inject
	public KillclogService(OkHttpClient httpClient, Gson gson, ClogService clogService)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.clogService = clogService;
	}

	/** Clear transient failure cooldowns immediately (called on login); TTLs auto-expire otherwise. */
	public void clearFailures()
	{
		clogFailures.clear();
		indexFailedAt = 0;
		resetBreaker();
	}

	private synchronized void recordBreakerFailure()
	{
		long now = System.currentTimeMillis();
		recentFailures[failureIndex] = now;
		failureIndex = (failureIndex + 1) % BREAKER_THRESHOLD;

		long oldest = recentFailures[failureIndex];
		if (oldest > 0 && now - oldest <= BREAKER_WINDOW_MS)
		{
			breakerTrippedAt = now;
			breakerCooldownMs = Math.min(breakerCooldownMs * 2, BREAKER_MAX_COOLDOWN_MS);
			log.warn("killclog.com circuit breaker tripped ({} failures in {}s), cooldown {}s",
				BREAKER_THRESHOLD, BREAKER_WINDOW_MS / 1000, breakerCooldownMs / 1000);
		}
	}

	private synchronized void recordBreakerSuccess()
	{
		if (breakerTrippedAt > 0 || breakerCooldownMs > BREAKER_COOLDOWN_MS)
		{
			resetBreaker();
		}
	}

	private synchronized void resetBreaker()
	{
		Arrays.fill(recentFailures, 0);
		failureIndex = 0;
		breakerTrippedAt = 0;
		breakerCooldownMs = BREAKER_COOLDOWN_MS;
	}

	private boolean isBreakerOpen()
	{
		long tripped = breakerTrippedAt;
		return tripped > 0 && System.currentTimeMillis() - tripped < breakerCooldownMs;
	}

	/**
	 * Look up first-party collection log data. Resolves to null when the player
	 * has never synced (index miss -- zero HTTP), when killclog.com is
	 * unreachable with nothing cached, or on opt-out. LookupFanout combines
	 * this with the providers; fullest result leads, ties prefer first-party.
	 */
	public CompletableFuture<ClogResult> lookupClog(String playerName)
	{
		String key = pbKey(playerName);
		long now = System.currentTimeMillis();

		ClogResult cached = clogCache.get(key);
		Long fetched = clogFetchTimes.get(key);
		if (cached != null && fetched != null && now - fetched < RESULT_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		Long notFoundAt = clogNotFoundTimes.get(key);
		if (notFoundAt != null && now - notFoundAt < NOT_FOUND_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		Long failedAt = clogFailures.get(key);
		if (failedAt != null && now - failedAt < FAILURE_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		if (isBreakerOpen())
		{
			return CompletableFuture.completedFuture(cached);
		}

		// The canonical catalog supplies category denominators; until it
		// warms (a brief startup window), a fresh first-party result would
		// render dishonest totals, so the leg waits it out. Providers keep
		// serving, and nothing is recorded against the breaker.
		if (clogService != null && !clogService.hasCatalog())
		{
			return CompletableFuture.completedFuture(cached);
		}

		return ensureIndex().thenCompose(index ->
		{
			// Unknown membership (index never fetched, server unreachable):
			// keep whatever is cached rather than guessing either way.
			if (index == null)
			{
				return CompletableFuture.completedFuture(clogCache.get(key));
			}
			// A real index miss is authoritative absence: never synced, or
			// synced-then-withdrawn (opt-out purge, unbind). Cached data and
			// pbs are evicted so withdrawal is honored within one index
			// refresh, never held for a cache TTL.
			if (!index.contains(key))
			{
				evictFirstParty(key);
				return CompletableFuture.completedFuture(null);
			}
			return clogInFlight.computeIfAbsent(key, ignored -> startProofViewLookup(playerName, key));
		});
	}

	/** Drop every first-party trace of a player: clog result, freshness, pbs. */
	private void evictFirstParty(String key)
	{
		clogCache.remove(key);
		clogFetchTimes.remove(key);
		pbCache.remove(key);
	}

	/**
	 * Formatted personal best for a looked-up synced player, or null when none
	 * is cached. Synchronous cache read -- safe from the EDT at tooltip-build
	 * time; the map fills during the same fetch that produced the clog result.
	 */
	@Nullable
	public String pbText(@Nullable String playerName, String panelBossName)
	{
		if (playerName == null)
		{
			return null;
		}
		Map<String, Double> pbs = pbCache.get(pbKey(playerName));
		if (pbs == null)
		{
			return null;
		}
		Double best = pbs.get(panelBossName);
		return best != null && best > 0 ? PersonalBests.formatSeconds(best) : null;
	}

	private CompletableFuture<Set<String>> ensureIndex()
	{
		long now = System.currentTimeMillis();
		Set<String> cached = syncIndex;
		if (cached != null && now - indexFetchedAt < INDEX_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}
		if (indexFailedAt > 0 && now - indexFailedAt < FAILURE_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}
		return indexInFlight.computeIfAbsent("index", ignored -> startIndexFetch());
	}

	private CompletableFuture<Set<String>> startIndexFetch()
	{
		return HttpUtil.httpGet(httpClient, INDEX_URL).thenApply(resp ->
		{
			try
			{
				if (resp.code != 200 || resp.body == null)
				{
					indexFailedAt = System.currentTimeMillis();
					recordBreakerFailure();
					return syncIndex;
				}
				Set<String> parsed = parseSyncIndex(resp.body);
				if (parsed == null)
				{
					indexFailedAt = System.currentTimeMillis();
					recordBreakerFailure();
					return syncIndex;
				}
				syncIndex = parsed;
				indexFetchedAt = System.currentTimeMillis();
				indexFailedAt = 0;
				recordBreakerSuccess();
				return parsed;
			}
			finally
			{
				indexInFlight.remove("index");
			}
		});
	}

	@Nullable
	Set<String> parseSyncIndex(String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null || !root.has("names") || !root.get("names").isJsonArray())
			{
				return null;
			}
			Set<String> names = ConcurrentHashMap.newKeySet();
			for (JsonElement el : root.getAsJsonArray("names"))
			{
				if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString())
				{
					names.add(el.getAsString().toLowerCase());
				}
			}
			return names;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse killclog sync index: {}", e.getMessage());
			return null;
		}
	}

	private CompletableFuture<ClogResult> startProofViewLookup(String playerName, String key)
	{
		// RSN sits in the path, so spaces must be %20 (URLEncoder yields '+', valid only in a query).
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8).replace("+", "%20");
		String url = BASE_URL + encoded + PROOF_SUFFIX;

		return HttpUtil.httpGet(httpClient, url).thenApply(resp ->
		{
			try
			{
				return onProofViewResponse(resp.code, resp.body, playerName, key);
			}
			finally
			{
				clogInFlight.remove(key);
			}
		});
	}

	/**
	 * Cache-state transition for a proof-view response; package-private so
	 * tests can drive the eviction paths without HTTP.
	 *
	 * <p>404 (never synced / purged) and 451 (opted out) are authoritative
	 * absence: every cached first-party trace is EVICTED, not merely aged --
	 * withdrawal must not be served for a cache TTL. Transient failures keep
	 * stale data, because a server blip should not blank known-good results.
	 */
	@Nullable
	ClogResult onProofViewResponse(int code, @Nullable String body, String playerName, String key)
	{
		if (code == 404 || code == 451)
		{
			evictFirstParty(key);
			clogNotFoundTimes.put(key, System.currentTimeMillis());
			return null;
		}
		if (code != 200 || body == null)
		{
			clogFailures.put(key, System.currentTimeMillis());
			recordBreakerFailure();
			return clogCache.get(key);
		}
		ClogResult result = parseProofView(playerName, body, key);
		if (result == null)
		{
			clogFailures.put(key, System.currentTimeMillis());
			recordBreakerFailure();
			return clogCache.get(key);
		}
		clogCache.put(key, result);
		clogFetchTimes.put(key, System.currentTimeMillis());
		recordBreakerSuccess();
		return result;
	}

	/**
	 * Parse a proof-view response into a {@link ClogResult}. Response shape:
	 * <pre>{@code
	 * { exists, rsn, updated_at, last_changed, unique_obtained, unique_total,
	 *   pbs: { "Boss Name": seconds },
	 *   clog: { items_by_category: { cat: [{ item_id, quantity, last_seen_at }] },
	 *           total_obtained, capitalized_name } }
	 * }</pre>
	 * Category keys round-trip the plugin's own canon (they were pushed from
	 * it), so no normalization is applied. Item dates are server-observed sync
	 * times, not true unlock dates, so they are dropped rather than shown as
	 * something they are not.
	 */
	@Nullable
	ClogResult parseProofView(String playerName, String json, String key)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null || !root.has("clog") || !root.get("clog").isJsonObject())
			{
				return null;
			}
			JsonObject clog = root.getAsJsonObject("clog");
			if (!clog.has("items_by_category") || !clog.get("items_by_category").isJsonObject())
			{
				return null;
			}

			Map<String, List<ClogResult.ClogItem>> obtainedItems = new HashMap<>();
			Map<String, List<Integer>> categoryItems = new HashMap<>();
			JsonObject byCategory = clog.getAsJsonObject("items_by_category");
			for (Map.Entry<String, JsonElement> entry : byCategory.entrySet())
			{
				if (!entry.getValue().isJsonArray())
				{
					continue;
				}
				List<ClogResult.ClogItem> obtained = new ArrayList<>();
				for (JsonElement itemEl : entry.getValue().getAsJsonArray())
				{
					if (!itemEl.isJsonObject())
					{
						continue;
					}
					JsonObject item = itemEl.getAsJsonObject();
					int id = intField(item, "item_id");
					int qty = intField(item, "quantity");
					if (id <= 0 || qty < 1)
					{
						// A missing or invalid id must not fabricate item 0,
						// and a missing or nonpositive quantity must not
						// fabricate an obtained item into the coverage race.
						continue;
					}
					obtained.add(new ClogResult.ClogItem(id, qty, null));
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
				List<Integer> catalogIds = clogService != null
					? clogService.getCategoryCatalogIds(entry.getKey()) : null;
				if (catalogIds != null)
				{
					categoryItems.put(entry.getKey(), catalogIds);
				}
			}
			// An empty log is DATA, not damage: the server serves a valid
			// empty shape for players whose sync carried pbs or counters but
			// no itemized clog yet. Malformed bodies were rejected above;
			// flowing through here seeds full catalog denominators (every
			// category 0-of-N) and still caches the pbs below. Returning
			// null would book a healthy response as a server fault and
			// charge the breaker.

			// Never-logged categories are absent from a first-party payload,
			// but absence is data: the player owns none of that catalog. Seed
			// them with catalog denominators so a first-party-won lookup
			// renders every boss as 0-of-N with a dimmed grid instead of the
			// generic unsynced shape.
			if (clogService != null)
			{
				for (String catalogKey : clogService.getCatalogCategoryKeys())
				{
					if (!categoryItems.containsKey(catalogKey))
					{
						List<Integer> ids = clogService.getCategoryCatalogIds(catalogKey);
						if (ids != null)
						{
							categoryItems.put(catalogKey, ids);
						}
					}
				}
			}

			String displayName = stringField(root, "rsn");
			if (displayName == null)
			{
				displayName = stringField(clog, "capitalized_name");
			}
			ClogResult result = new ClogResult(
				displayName != null ? displayName : playerName,
				obtainedItems,
				categoryItems,
				null,   // item names resolve client-side through ItemManager
				templeStyleTimestamp(stringField(root, "last_changed")),
				null    // account type is not served on proof-view
			);
			// The obtained counter decides coverage races, so it needs the
			// full plausibility chain: a valid total under the ceiling AND
			// obtained within it. Obtained without a credible total is
			// dropped and coverage falls back to counting distinct parsed
			// items; a lone plausible total still serves as a denominator.
			int uniqueObtained = intField(root, "unique_obtained");
			int uniqueTotal = intField(root, "unique_total");
			boolean totalPlausible = uniqueTotal > 0 && uniqueTotal <= MAX_PLAUSIBLE_UNIQUE;
			if (totalPlausible)
			{
				result.setUniqueTotal(uniqueTotal);
				if (uniqueObtained > 0 && uniqueObtained <= uniqueTotal)
				{
					result.setUniqueObtained(uniqueObtained);
				}
			}

			pbCache.put(key, parsePbs(root));
			return result;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse killclog proof view: {}", e.getMessage());
			return null;
		}
	}

	private static Map<String, Double> parsePbs(JsonObject root)
	{
		if (!root.has("pbs") || !root.get("pbs").isJsonObject())
		{
			return Collections.emptyMap();
		}
		Map<String, Double> pbs = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("pbs").entrySet())
		{
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
			{
				double seconds = value.getAsDouble();
				if (seconds > 0)
				{
					pbs.put(entry.getKey(), seconds);
				}
			}
		}
		return pbs;
	}

	/**
	 * The server publishes ISO timestamps; the panel's sync-line parser speaks
	 * Temple's {@code yyyy-MM-dd HH:mm:ss}. Convert so "Last synced" renders.
	 */
	@Nullable
	static String templeStyleTimestamp(@Nullable String iso)
	{
		if (iso == null)
		{
			return null;
		}
		String flattened = iso.replace('T', ' ');
		return flattened.length() >= 19 ? flattened.substring(0, 19) : flattened;
	}

	private static int intField(JsonObject obj, String field)
	{
		return obj.has(field) && !obj.get(field).isJsonNull() && obj.get(field).isJsonPrimitive()
			? obj.get(field).getAsInt() : 0;
	}

	@Nullable
	private static String stringField(JsonObject obj, String field)
	{
		return obj.has(field) && !obj.get(field).isJsonNull() && obj.get(field).isJsonPrimitive()
			? obj.get(field).getAsString() : null;
	}
}
