package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

/**
 * Fetches player data from RuneProfile. Two lanes:
 * <ol>
 *   <li><strong>Account summary</strong> ({@link #lookup}) -- public CA source for other
 *       players, plus account-type metadata. One GET per player against {@code /accounts/{rsn}}.
 *       The local player is asked for too, only so Sources can list RuneProfile.</li>
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

	// Shared across the CA and clog lanes. A single success clears it.
	private final CircuitBreaker breaker = new CircuitBreaker("RuneProfile");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalCaCache localCaCache;

	// Plugin-owned live catalog, set at startup. Read from HTTP threads; the
	// catalog publishes complete immutable snapshots through a volatile field.
	@Nullable private volatile CaCatalog caCatalog;

	// Summary lane (CA tiers plus account metadata) and clog lane: independent, same TTLs.
	private final HttpUtil.Lane<RuneProfileSummary> summaries = new HttpUtil.Lane<>(breaker);
	private final HttpUtil.Lane<ClogResult> clogs = new HttpUtil.Lane<>(breaker);

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
		summaries.failed.clear();
		clogs.failed.clear();
		breaker.reset();
	}

	/** Cached CA result for SWR reveal without an API call, or null if none is held. */
	public CombatAchievementResult getCached(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		RuneProfileSummary cached = summaries.values.get(playerName.toLowerCase());
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
		// The active player's summary is fetched for Sources only; their own
		// client stays the authority on account type.
		if (playerName == null || localCaCache != null && localCaCache.isActivePlayer(playerName))
		{
			return null;
		}
		RuneProfileSummary cached = summaries.fresh(playerName.toLowerCase());
		return cached != null ? cached.accountType : null;
	}

	/**
	 * True once RuneProfile has answered with a profile for this name. Matched
	 * as Jagex names, because the panel asks with a provider's canonical
	 * spelling while the cache is keyed by what was typed.
	 */
	boolean hasProfile(@Nullable String playerName)
	{
		if (playerName == null)
		{
			return false;
		}
		String wanted = Text.toJagexName(playerName.toLowerCase());
		return summaries.values.keySet().stream().anyMatch(key -> Text.toJagexName(key).equals(wanted));
	}

	/**
	 * Look up CA tier data for a player. Resolves to null when the player has no CA data
	 * (not synced, or RuneProfile is unavailable with no cached result) so the caller can
	 * simply omit the CA surface.
	 */
	public CompletableFuture<CombatAchievementResult> lookup(String playerName)
	{
		// Active player: CA is read straight from the game and held locally.
		// It is authoritative even when empty, so RuneProfile never supplies it.
		// The summary is still requested so Sources can say whether this player
		// has a RuneProfile; nothing from it replaces local data.
		if (localCaCache != null && localCaCache.isActivePlayer(playerName))
		{
			lookupSummary(playerName).exceptionally(ex -> null);
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
		return summaries.lookup(key, () -> startSummaryLookup(playerName, key));
	}

	private CompletableFuture<RuneProfileSummary> startSummaryLookup(String playerName, String key)
	{
		return HttpUtil.httpGet(httpClient, BASE_URL + HttpUtil.pathSegment(playerName)).thenApply(resp ->
		{
			if (resp.code == 404)
			{
				return summaries.missing(key);
			}
			RuneProfileSummary result = resp.code == 200 && resp.body != null ? parseAccountSummary(resp.body) : null;
			if (result == null)
			{
				return summaries.fail(key);
			}
			breaker.reset();
			return summaries.ok(key, result);
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

			AccountType accountType = RuneProfileAccountSummaryParser.parseAccountType(root);
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
		return clogs.lookup(key, () -> startClogLookup(playerName, key));
	}

	private CompletableFuture<ClogResult> startClogLookup(String playerName, String key)
	{
		lookupSummary(playerName).exceptionally(ex -> null);

		return HttpUtil.httpGet(httpClient, BASE_URL + HttpUtil.pathSegment(playerName) + CLOG_SUFFIX).thenApply(resp ->
		{
			if (resp.code == 404)
			{
				return clogs.missing(key);
			}
			if (resp.code != 200 || resp.body == null)
			{
				return clogs.fail(key);
			}
			RuneProfileSummary summary = summaries.fresh(key);
			AccountType accountType = summary != null ? summary.accountType : null;
			ClogParseOutcome parsed = parseCollectionLogOutcome(playerName, resp.body, accountType);
			if (parsed.state == ClogParseState.NOT_SYNCED)
			{
				// RuneProfile can return a complete all-zero catalog instead of 404
				// when no player snapshot exists. This is a healthy negative result,
				// and it must replace any stale success rather than revive it.
				clogs.values.remove(key);
				clogs.fetched.remove(key);
				clogs.failed.remove(key);
				clogs.missing(key);
				breaker.reset();
				return null;
			}
			if (parsed.result == null)
			{
				return clogs.fail(key);
			}
			breaker.reset();
			return clogs.ok(key, parsed.result);
		});
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
	ClogParseOutcome parseCollectionLogOutcome(String playerName, String json,
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

	static final class ClogParseOutcome
	{
		private final ClogParseState state;
		@Nullable final ClogResult result;

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
}
