package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Fetches player data from RuneProfile. Two lanes:
 * <ol>
 *   <li><strong>Account summary</strong> ({@link #lookup}) -- public CA source for other
 *       players, plus account-type metadata. One GET per player against {@code /accounts/{rsn}}.</li>
 *   <li><strong>Collection log provider</strong> ({@link #lookupClog}) -- one GET against
 *       {@code /collection-log}, parsed into a {@link ClogResult}. LookupSession and
 *       ComparisonController combine this with TempleOSRS and keep the freshest result.</li>
 * </ol>
 *
 * <p>Both lanes share the same freshness model: success TTL, not-synced (404) TTL, transient
 * failure cooldown, and in-flight dedup. Caches are independent per lane so a CA 404 does not
 * suppress a clog request (or vice versa).
 *
 * <p>A 404 on either lane means the player has not synced to RuneProfile; held longer than a
 * transient failure since it rarely flips quickly. A previous success is still returned while
 * cached, so a provider-side 404 cannot blank known-good data mid-session.
 */
@Slf4j
@Singleton
public class RuneProfileService
{
	private static final String BASE_URL = "https://api.runeprofile.com/v1/accounts/";
	private static final String CLOG_SUFFIX = "/collection-log";

	private static final long RESULT_TTL_MS = 5 * 60 * 1000;       // 5 min -- fresh success (CA + clog)
	private static final long NOT_FOUND_TTL_MS = 60 * 60 * 1000;  // 1 hour -- not synced
	private static final long FAILURE_TTL_MS = 3 * 60 * 1000;     // 3 min -- transient failure

	// Provider-level circuit breaker shared across CA and clog lanes.
	// Trips after BREAKER_THRESHOLD consecutive failures within BREAKER_WINDOW_MS.
	// Once tripped, all requests short-circuit for BREAKER_COOLDOWN_MS (escalating on
	// repeated trips up to BREAKER_MAX_COOLDOWN_MS). A single success resets the
	// breaker and the cooldown tier.
	private static final int BREAKER_THRESHOLD = 5;
	private static final long BREAKER_WINDOW_MS = 60 * 1000;          // 5 in 60s trips it
	private static final long BREAKER_COOLDOWN_MS = 60 * 1000;        // 1 min initial cooldown
	private static final long BREAKER_MAX_COOLDOWN_MS = 30 * 60 * 1000; // 30 min ceiling

	private final long[] recentFailures = new long[BREAKER_THRESHOLD]; // ring buffer of failure timestamps
	private int failureIndex = 0;
	private volatile long breakerTrippedAt = 0;
	private volatile long breakerCooldownMs = BREAKER_COOLDOWN_MS;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalCaCache localCaCache;

	// Plugin-owned live catalog, set at startup. Read from HTTP threads; the
	// catalog publishes complete immutable snapshots through a volatile field.
	@Nullable private volatile CaCatalog caCatalog;

	// Summary cache. Supplies CA tiers plus account metadata from one RuneProfile request.
	private final Map<String, RuneProfileSummary> summaryCache = new ConcurrentHashMap<>();
	private final Map<String, Long> summaryFetchTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> summaryNotFoundTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> summaryFailures = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<RuneProfileSummary>> summaryInFlight = new ConcurrentHashMap<>();

	// Clog cache. Independent of CA, same TTLs.
	private final Map<String, ClogResult> clogCache = new ConcurrentHashMap<>();
	private final Map<String, Long> clogFetchTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> clogNotFoundTimes = new ConcurrentHashMap<>();
	private final Map<String, Long> clogFailures = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<ClogResult>> clogInFlight = new ConcurrentHashMap<>();

	@Inject
	public RuneProfileService(OkHttpClient httpClient, Gson gson, LocalCaCache localCaCache)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.localCaCache = localCaCache;
	}

	public void setCaCatalog(@Nullable CaCatalog caCatalog)
	{
		this.caCatalog = caCatalog;
	}

	/** Clear transient failure cooldowns immediately (called on login); TTLs auto-expire otherwise. */
	public void clearFailures()
	{
		summaryFailures.clear();
		clogFailures.clear();
		resetBreaker();
	}

	/**
	 * Record a transient failure in the circuit breaker ring buffer. When
	 * {@link #BREAKER_THRESHOLD} failures land within {@link #BREAKER_WINDOW_MS},
	 * the breaker trips and all subsequent requests short-circuit until cooldown
	 * expires. Cooldown doubles on each consecutive trip (capped at 30 min).
	 */
	private synchronized void recordBreakerFailure()
	{
		long now = System.currentTimeMillis();
		recentFailures[failureIndex] = now;
		failureIndex = (failureIndex + 1) % BREAKER_THRESHOLD;

		// Check if the oldest failure in the ring is within the window
		long oldest = recentFailures[failureIndex]; // next slot = oldest entry
		if (oldest > 0 && now - oldest <= BREAKER_WINDOW_MS)
		{
			breakerTrippedAt = now;
			// Escalate cooldown on repeated trips (1m -> 2m -> 4m -> ... -> 30m)
			breakerCooldownMs = Math.min(breakerCooldownMs * 2, BREAKER_MAX_COOLDOWN_MS);
			log.warn("RuneProfile circuit breaker tripped ({} failures in {}s), cooldown {}s",
				BREAKER_THRESHOLD,
				BREAKER_WINDOW_MS / 1000,
				breakerCooldownMs / 1000);
		}
	}

	/** Record a success -- resets the breaker and cooldown escalation tier. */
	private synchronized void recordBreakerSuccess()
	{
		resetBreaker();
	}

	/** Hard reset: clear failure ring, un-trip, reset cooldown to base tier. */
	private synchronized void resetBreaker()
	{
		Arrays.fill(recentFailures, 0);
		failureIndex = 0;
		breakerTrippedAt = 0;
		breakerCooldownMs = BREAKER_COOLDOWN_MS;
	}

	/** True when the breaker is tripped and the cooldown hasn't elapsed yet. */
	private boolean isBreakerOpen()
	{
		long tripped = breakerTrippedAt;
		return tripped > 0 && System.currentTimeMillis() - tripped < breakerCooldownMs;
	}

	/** Cached CA result for SWR reveal without an API call, or null if none is held. */
	public CombatAchievementResult getCached(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		RuneProfileSummary cached = summaryCache.get(playerName.toLowerCase());
		return cached != null ? rebased(cached.combatAchievements) : null;
	}

	/**
	 * Cached results heal against the current catalog on read: a verdict
	 * computed before a capture (or across a threshold change) must not serve
	 * its old tier for the cache TTL.
	 */
	@Nullable
	private CombatAchievementResult rebased(@Nullable CombatAchievementResult result)
	{
		CaCatalog catalog = caCatalog;
		if (result == null || catalog == null)
		{
			return result;
		}
		return result.rebasedOn(catalog.totals());
	}

	/** Cached RuneProfile account type, or null when the summary is absent or stale. */
	@Nullable
	public AccountType getCachedAccountType(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		RuneProfileSummary cached = freshSummary(playerName.toLowerCase());
		return cached != null ? cached.accountType : null;
	}

	/**
	 * Look up CA tier data for a player. Resolves to null when the player has no CA data
	 * (not synced, or RuneProfile is unavailable with no cached result) so the caller can
	 * simply omit the CA surface.
	 */
	public CompletableFuture<CombatAchievementResult> lookup(String playerName)
	{
		// Active player: CA is read straight from the game and held locally.
		// It is authoritative even when empty, so never fall through to RuneProfile.
		if (localCaCache != null && localCaCache.isActivePlayer(playerName))
		{
			CombatAchievementResult local = localCaCache.hasDataFor(playerName)
				? localCaCache.getCached(playerName) : null;
			return CompletableFuture.completedFuture(local);
		}

		return lookupSummary(playerName).thenApply(summary ->
			summary != null ? rebased(summary.combatAchievements) : null);
	}

	private CompletableFuture<RuneProfileSummary> lookupSummary(String playerName)
	{
		String key = playerName.toLowerCase();
		long now = System.currentTimeMillis();

		RuneProfileSummary cached = summaryCache.get(key);
		Long fetched = summaryFetchTimes.get(key);
		if (cached != null && fetched != null && now - fetched < RESULT_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		Long notFoundAt = summaryNotFoundTimes.get(key);
		if (notFoundAt != null && now - notFoundAt < NOT_FOUND_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		Long failedAt = summaryFailures.get(key);
		if (failedAt != null && now - failedAt < FAILURE_TTL_MS)
		{
			return CompletableFuture.completedFuture(cached);
		}

		// If RuneProfile is down, do not pile on requests.
		if (isBreakerOpen())
		{
			return CompletableFuture.completedFuture(cached);
		}

		return summaryInFlight.computeIfAbsent(key, ignored -> startSummaryLookup(playerName, key));
	}

	private CompletableFuture<RuneProfileSummary> startSummaryLookup(String playerName, String key)
	{
		// RSN sits in the path, so spaces must be %20 (URLEncoder yields '+', valid only in a query).
		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8).replace("+", "%20");
		String url = BASE_URL + encoded;

		return httpGet(url).thenApply(resp ->
		{
			try
			{
				if (resp.code == 404)
				{
					summaryNotFoundTimes.put(key, System.currentTimeMillis());
					return summaryCache.get(key);
				}
				if (resp.code != 200 || resp.body == null)
				{
					summaryFailures.put(key, System.currentTimeMillis());
					recordBreakerFailure();
					return summaryCache.get(key);
				}
				RuneProfileSummary result = parseAccountSummary(resp.body);
				if (result == null)
				{
					summaryFailures.put(key, System.currentTimeMillis());
					recordBreakerFailure();
					return summaryCache.get(key);
				}
				summaryCache.put(key, result);
				summaryFetchTimes.put(key, System.currentTimeMillis());
				recordBreakerSuccess();
				return result;
			}
			finally
			{
				summaryInFlight.remove(key);
			}
		});
	}

	/**
	 * Parse RuneProfile CA rows from either {@code /combat-achievements} ({@code data}) or
	 * the account summary response ({@code combatAchievements}).
	 */
	CombatAchievementResult parseCombatAchievements(String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null)
			{
				return null;
			}
			JsonArray data = null;
			if (root.has("data") && root.get("data").isJsonArray())
			{
				data = root.getAsJsonArray("data");
			}
			else if (root.has("combatAchievements") && root.get("combatAchievements").isJsonArray())
			{
				data = root.getAsJsonArray("combatAchievements");
			}

			if (data == null)
			{
				return null;
			}
			return parseCombatAchievementRows(data);
		}
		catch (Exception e)
		{
			log.debug("Failed to parse combat achievements: {}", e.getMessage());
			return null;
		}
	}

	private CombatAchievementResult parseCombatAchievementRows(JsonArray data)
	{
		Map<CombatAchievementTier, Integer> completed = new EnumMap<>(CombatAchievementTier.class);
		Map<CombatAchievementTier, Integer> total = new EnumMap<>(CombatAchievementTier.class);

		for (JsonElement element : data)
		{
			if (!element.isJsonObject())
			{
				continue;
			}
			JsonObject row = element.getAsJsonObject();
			if (!row.has("name"))
			{
				continue;
			}
			CombatAchievementTier tier = CombatAchievementTier.fromName(row.get("name").getAsString());
			if (tier == null)
			{
				continue;
			}
			completed.put(tier, intField(row, "completed"));
			total.put(tier, intField(row, "total"));
		}

		if (completed.isEmpty())
		{
			return null;
		}
		CaCatalog catalog = caCatalog;
		return CombatAchievementResult.of(completed, total,
			catalog != null ? catalog.totals() : null);
	}

	@Nullable
	RuneProfileSummary parseAccountSummary(String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null)
			{
				return null;
			}

			AccountType accountType = parseAccountType(root);
			CombatAchievementResult ca = parseCombatAchievements(json);
			if (accountType == null && ca == null)
			{
				return null;
			}
			return new RuneProfileSummary(accountType, ca);
		}
		catch (Exception e)
		{
			log.debug("Failed to parse RuneProfile account summary: {}", e.getMessage());
			return null;
		}
	}

	@Nullable
	private AccountType parseAccountType(JsonObject root)
	{
		return RuneProfileAccountSummaryParser.parseAccountType(root);
	}

	private static int intField(JsonObject obj, String field)
	{
		return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsInt() : 0;
	}

	// Collection log provider lane.

	/**
	 * Look up collection log data from RuneProfile. LookupSession and ComparisonController
	 * combine this with TempleOSRS and keep the freshest result.
	 *
	 * <p>Caching mirrors the CA lookup: success TTL, 404 TTL, failure cooldown,
	 * in-flight dedup. Caches are independent of the CA caches so a CA 404 does
	 * not suppress a clog lookup (though in practice both will 404 together for
	 * players not on RuneProfile).
	 */
	public CompletableFuture<ClogResult> lookupClog(String playerName)
	{
		String key = playerName.toLowerCase();
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

		// Shared with CA lane; if RuneProfile is down, do not pile on.
		if (isBreakerOpen())
		{
			return CompletableFuture.completedFuture(cached);
		}

		return clogInFlight.computeIfAbsent(key, ignored -> startClogLookup(playerName, key));
	}

	private CompletableFuture<ClogResult> startClogLookup(String playerName, String key)
	{
		lookupSummary(playerName).exceptionally(ex -> null);

		String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8).replace("+", "%20");
		String url = BASE_URL + encoded + CLOG_SUFFIX;

		return httpGet(url).thenApply(resp ->
		{
			try
			{
				if (resp.code == 404)
				{
					clogNotFoundTimes.put(key, System.currentTimeMillis());
					return clogCache.get(key);
				}
				if (resp.code != 200 || resp.body == null)
				{
					clogFailures.put(key, System.currentTimeMillis());
					recordBreakerFailure();
					return clogCache.get(key);
				}
				RuneProfileSummary summary = freshSummary(key);
				AccountType accountType = summary != null ? summary.accountType : null;
				ClogParseOutcome parsed = parseCollectionLogOutcome(playerName, resp.body, accountType);
				if (parsed.state == ClogParseState.NOT_SYNCED)
				{
					// RuneProfile can return a complete all-zero catalog instead of 404
					// when no player snapshot exists. This is a healthy negative result,
					// and it must replace any stale success rather than revive it.
					clogCache.remove(key);
					clogFetchTimes.remove(key);
					clogFailures.remove(key);
					clogNotFoundTimes.put(key, System.currentTimeMillis());
					recordBreakerSuccess();
					return null;
				}
				ClogResult result = parsed.result;
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
			finally
			{
				clogInFlight.remove(key);
			}
		});
	}

	@Nullable
	private RuneProfileSummary freshSummary(String key)
	{
		RuneProfileSummary summary = summaryCache.get(key);
		Long fetched = summaryFetchTimes.get(key);
		if (summary == null || fetched == null)
		{
			return null;
		}
		return System.currentTimeMillis() - fetched < RESULT_TTL_MS ? summary : null;
	}

	/**
	 * Parse the RuneProfile collection-log response into a {@link ClogResult}.
	 * The collection-log endpoint response shape (confirmed from RuneProfile OpenAPI spec):
	 * <pre>{@code
	 * { obtained, total, tabs: [{ name, pages: [{ name, items: [{ id, name, quantity }] }] }] }
	 * }</pre>
	 * Items with {@code quantity > 0} are obtained. The page name is normalized through
	 * the category canon shared with TempleOSRS and the boss grid.
	 */
	@Nullable
	ClogResult parseCollectionLog(String playerName, String json)
	{
		return parseCollectionLog(playerName, json, null);
	}

	@Nullable
	ClogResult parseCollectionLog(String playerName, String json, @Nullable AccountType providerAccountType)
	{
		return parseCollectionLogOutcome(playerName, json, providerAccountType).result;
	}

	private ClogParseOutcome parseCollectionLogOutcome(String playerName, String json,
		@Nullable AccountType providerAccountType)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			if (root == null || !root.has("tabs")
				|| !root.has("obtained") || root.get("obtained").isJsonNull()
				|| !root.has("total") || root.get("total").isJsonNull())
			{
				return ClogParseOutcome.invalid();
			}

			int rootObtained = intField(root, "obtained");
			int rootTotal = intField(root, "total");

			if (!root.get("tabs").isJsonArray())
			{
				return ClogParseOutcome.invalid();
			}
			JsonArray tabs = root.getAsJsonArray("tabs");
			Map<String, List<ClogResult.ClogItem>> obtainedItems = new HashMap<>();
			Map<String, List<Integer>> categoryItems = new HashMap<>();
			Map<Integer, String> itemNames = new HashMap<>();
			int parsedItemCount = 0;

			for (JsonElement tabEl : tabs)
			{
				if (!tabEl.isJsonObject())
				{
					continue;
				}
				JsonObject tab = tabEl.getAsJsonObject();
				if (!tab.has("pages") || !tab.get("pages").isJsonArray())
				{
					continue;
				}

				JsonArray pages = tab.getAsJsonArray("pages");
				for (JsonElement pageEl : pages)
				{
					if (!pageEl.isJsonObject())
					{
						continue;
					}
					JsonObject page = pageEl.getAsJsonObject();
					if (!page.has("name") || page.get("name").isJsonNull())
					{
						continue;
					}
					String pageName = page.get("name").getAsString();
					if (!page.has("items") || !page.get("items").isJsonArray())
					{
						continue;
					}

					String categoryKey = normalizePageKey(pageName);
					JsonArray items = page.getAsJsonArray("items");

					List<ClogResult.ClogItem> obtained = new ArrayList<>();
					List<Integer> allIds = new ArrayList<>();

					for (JsonElement itemEl : items)
					{
						if (!itemEl.isJsonObject())
						{
							continue;
						}
						JsonObject item = itemEl.getAsJsonObject();
						if (!item.has("id") || item.get("id").isJsonNull()
							|| !item.has("quantity") || item.get("quantity").isJsonNull())
						{
							continue;
						}
						parsedItemCount++;
						int id = intField(item, "id");
						int qty = intField(item, "quantity");
						String name = item.has("name") && !item.get("name").isJsonNull()
							? item.get("name").getAsString() : null;

						allIds.add(id);
						if (name != null)
						{
							itemNames.put(id, name);
						}
						if (qty > 0)
						{
							obtained.add(new ClogResult.ClogItem(id, qty, null));
						}
					}

					categoryItems.put(categoryKey, allIds);
					if (!obtained.isEmpty())
					{
						obtainedItems.put(categoryKey, obtained);
					}
				}
			}

			if (categoryItems.isEmpty() || parsedItemCount == 0)
			{
				return ClogParseOutcome.invalid();
			}

			if (obtainedItems.isEmpty())
			{
				return rootObtained == 0
					? ClogParseOutcome.notSynced()
					: ClogParseOutcome.invalid();
			}

			ClogResult result = new ClogResult(
				playerName,
				obtainedItems,
				categoryItems,
				itemNames,
				null,   // no lastChanged from RuneProfile clog endpoint
				providerAccountType
			).withSources(false, true, false);
			result.setUniqueObtained(rootObtained);
			result.setUniqueTotal(rootTotal);
			return ClogParseOutcome.data(result);
		}
		catch (Exception e)
		{
			log.debug("Failed to parse RuneProfile collection log: {}", e.getMessage());
			return ClogParseOutcome.invalid();
		}
	}

	private enum ClogParseState
	{
		DATA,
		NOT_SYNCED,
		INVALID
	}

	private static final class ClogParseOutcome
	{
		private final ClogParseState state;
		@Nullable private final ClogResult result;

		private ClogParseOutcome(ClogParseState state, @Nullable ClogResult result)
		{
			this.state = state;
			this.result = result;
		}

		private static ClogParseOutcome data(ClogResult result)
		{
			return new ClogParseOutcome(ClogParseState.DATA, result);
		}

		private static ClogParseOutcome notSynced()
		{
			return new ClogParseOutcome(ClogParseState.NOT_SYNCED, null);
		}

		private static ClogParseOutcome invalid()
		{
			return new ClogParseOutcome(ClogParseState.INVALID, null);
		}
	}

	/**
	 * Normalize a RuneProfile collection-log page name through the same category
	 * canon used by TempleOSRS and the boss grid.
	 */
	static String normalizePageKey(String pageName)
	{
		return ClogService.bossToCategory(pageName);
	}

	static final class RuneProfileSummary
	{
		final AccountType accountType;
		final CombatAchievementResult combatAchievements;

		RuneProfileSummary(AccountType accountType, CombatAchievementResult combatAchievements)
		{
			this.accountType = accountType;
			this.combatAchievements = combatAchievements;
		}
	}

	private CompletableFuture<HttpUtil.HttpResult> httpGet(String url)
	{
		return HttpUtil.httpGet(httpClient, url);
	}
}
