package com.killclog;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.*;
import lombok.*;
import net.runelite.api.clan.ClanTitle;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

/**
 * Fetches player data from RuneProfile, in two requests.
 *
 * The account summary ({@link #lookup}) is one GET of {@code /accounts/{rsn}}: another player's Combat Achievements
 * and their account type. The local player is asked for too, only so Sources can list RuneProfile.
 *
 * The Collection Log ({@link #lookupClog}) is one GET of {@code /collection-log}, read into a {@link ClogResult}.
 * LookupSession and ComparisonController combine it with TempleOSRS's and keep the fuller log.
 *
 * Each keeps its answers apart, so a 404 on one never stops the other being asked: a success, a not-synced (404) and
 * a failure each for their own time, and one request per name at once. A 404 means the player hasn't synced to
 * RuneProfile and is held longer than a failure, since it rarely changes quickly. A last good answer is served while
 * cached, so a later 404 can't blank it mid-session.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RuneProfileService
{
	private static final String BASE_URL = "https://api.runeprofile.com/v1/accounts/";
	private static final String CLOG_SUFFIX = "/collection-log";

	// Shared by the summary's requests and the log's. One success clears it.
	private final CircuitBreaker breaker = new CircuitBreaker("RuneProfile");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalCaCache localCaCache;

	// The game's live task counts. Read from HTTP threads; the catalog publishes
	// complete immutable snapshots through a volatile field.
	@Inject
	private CaCatalog caCatalog;

	// The summary (Combat Achievements and account details) and the log: cached apart, for the same times.
	private final HttpUtil.Lane<RuneProfileSummary> summaries = new HttpUtil.Lane<>(breaker);
	private final HttpUtil.Lane<ClogResult> clogs = new HttpUtil.Lane<>(breaker);


	/** Clear transient failure cooldowns immediately (called on login); TTLs auto-expire otherwise. */
	public void clearFailures()
	{
		summaries.failed.clear();
		clogs.failed.clear();
		breaker.reset();
	}

	/** The Combat Achievements already cached for this player, for showing without a request; null if none is held. */
	public CombatAchievementResult getCached(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		RuneProfileSummary cached = summaries.values.get(RsnInputPolicy.key(playerName));
		return cached != null ? rebased(cached.combatAchievements) : null;
	}

	/**
	 * A cached result is put right against the catalog as it is read: one worked out before the catalog was read (or
	 * across a release) must not serve its old tier until it ages out.
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

	/** RuneProfile's account type from its last answer for this name, or null without one. */
	@Nullable
	public AccountType getCachedAccountType(String playerName)
	{
		// The active player's summary is fetched for Sources only; their own
		// client stays the authority on account type.
		if (playerName == null || localCaCache != null && localCaCache.isActivePlayer(playerName))
		{
			return null;
		}
		RuneProfileSummary held = held(playerName);
		return held != null ? held.accountType : null;
	}

	/** A player's clan as RuneProfile last saw it; null without one. */
	@Nullable
	String clan(String playerName)
	{
		RuneProfileSummary held = held(playerName);
		return held != null ? held.clan : null;
	}

	/** The player's title in that clan, its id the game's clan-chat icon; null without one. */
	@Nullable
	ClanTitle clanTitle(String playerName)
	{
		RuneProfileSummary held = held(playerName);
		return held != null ? held.clanTitle : null;
	}

	/** True once RuneProfile has answered with a profile for this name. */
	boolean hasProfile(@Nullable String playerName)
	{
		return playerName != null && held(playerName) != null;
	}

	/**
	 * The profile RuneProfile last answered with for this name, whatever its age: a card shows what its own lookup
	 * brought, and the next lookup asks again once it's stale, so a card held open never loses its clan. Matched as
	 * Jagex names, because the panel asks with the spelling a site gave while the cache is keyed by what was typed.
	 */
	@Nullable
	private RuneProfileSummary held(String playerName)
	{
		String wanted = RsnInputPolicy.key(playerName);
		for (Map.Entry<String, RuneProfileSummary> entry : summaries.values.entrySet())
		{
			if (Text.toJagexName(entry.getKey()).equals(wanted))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * A player's Combat Achievements. Null when RuneProfile has none (never synced, or it didn't answer and nothing is
	 * cached); a card then shows the tiers the player's Kill Clog sync carries, or none.
	 */
	public CompletableFuture<CombatAchievementResult> lookup(String playerName)
	{
		CompletableFuture<RuneProfileSummary> summary = lookupSummary(playerName);
		// The player logged in: their Combat Achievements are the game's, as kept here from this session or their last,
		// and stand even when empty, so RuneProfile's never replace them. The summary is still asked for, so Sources
		// can say whether they have a RuneProfile. Only where this PC has never read the game for them do RuneProfile's
		// stand in, and only until it has: an answer that lands after the game was read gives the game's.
		CombatAchievementResult own = own(playerName);
		if (own != null)
		{
			summary.exceptionally(ex -> null);
			return CompletableFuture.completedFuture(own);
		}
		return summary.thenApply(held ->
		{
			CombatAchievementResult read = own(playerName);
			return read != null ? read : held != null ? rebased(held.combatAchievements) : null;
		});
	}

	/** The logged-in player's own tiers as this client holds them; null for anyone else, or with none held. */
	@Nullable
	private CombatAchievementResult own(String playerName)
	{
		return localCaCache != null ? localCaCache.getCached(playerName) : null;
	}

	private CompletableFuture<RuneProfileSummary> lookupSummary(String playerName)
	{
		String key = RsnInputPolicy.key(playerName);
		return summaries.lookup(key, () -> startSummaryLookup(playerName, key));
	}

	private CompletableFuture<RuneProfileSummary> startSummaryLookup(String playerName, String key)
	{
		return HttpUtil.httpGet(httpClient, BASE_URL + HttpUtil.pathSegment(playerName)).thenApply(resp ->
		{
			if (resp.code == 404)
			{
				// No profile now: what it held goes, so no card shows a clan or account from one that's gone.
				forgetSpellings(key);
				return summaries.missing(key);
			}
			RuneProfileSummary result = resp.code == 200 && resp.body != null ? parseAccountSummary(resp.body) : null;
			if (result == null)
			{
				return summaries.fail(key);
			}
			breaker.reset();
			// The new answer is the only one held for the name, so no older spelling's answer can stand for it. Under
			// the lane's lock, as ok() stores: two spellings answering at once can't both clear and then both stay.
			synchronized (summaries)
			{
				forgetSpellings(key);
				return summaries.ok(key, result);
			}
		});
	}

	/** Drop every spelling of a name, as held() matches them all (a_q_p_weed held, a q p weed asked). */
	private void forgetSpellings(String key)
	{
		String name = Text.toJagexName(key);
		synchronized (summaries)
		{
			summaries.values.keySet().removeIf(held -> Text.toJagexName(held).equals(name));
		}
	}

	/**
	 * Reads RuneProfile's Combat Achievement rows, from {@code /combat-achievements} ({@code data}) or from the account
	 * summary ({@code combatAchievements}).
	 */
	CombatAchievementResult parseCombatAchievements(String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonArray data = Json.array(root, "data");
			data = data != null ? data : Json.array(root, "combatAchievements");
			CaCatalog catalog = caCatalog;
			return data != null ? CombatAchievementResult.fromRows(data, catalog != null ? catalog.totals() : null) : null;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse combat achievements: {}", e.getMessage());
			return null;
		}
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
			String clan = RuneProfileAccountSummaryParser.parseClan(root);
			if (accountType == null && ca == null && clan == null)
			{
				return null;
			}
			return new RuneProfileSummary(accountType, ca, clan,
				clan != null ? RuneProfileAccountSummaryParser.parseClanTitle(root) : null);
		}
		catch (Exception e)
		{
			log.debug("Failed to parse RuneProfile account summary: {}", e.getMessage());
			return null;
		}
	}

	// Collection log provider lane.

	/**
	 * A player's Collection Log from RuneProfile. LookupSession and ComparisonController combine it with TempleOSRS's
	 * and keep the fuller log.
	 *
	 * Cached as the summary is: a success, a 404 and a failure each for their own time, one request per name at once.
	 * The two caches are apart, so a 404 for the summary never stops the log being asked (though a player not on
	 * RuneProfile gets a 404 from both).
	 */
	public CompletableFuture<ClogResult> lookupClog(String playerName)
	{
		String key = RsnInputPolicy.key(playerName);
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
	 * Reads RuneProfile's collection-log answer into a {@link ClogResult}. Its shape, from RuneProfile's OpenAPI spec:
	 *
	 * <pre>{@code
	 * { obtained, total, tabs: [{ name, pages: [{ name, items: [{ id, name, quantity }] }] }] }
	 * }</pre>
	 *
	 * An item with {@code quantity > 0} is obtained. A page's name becomes the plugin's own name for it, the names
	 * TempleOSRS's pages and the boss grid go by.
	 */
	ClogParseOutcome parseCollectionLogOutcome(String playerName, String json,
		@Nullable AccountType providerAccountType)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonArray tabs = Json.array(root, "tabs");
			if (tabs == null || !Json.present(root, "obtained") || !Json.present(root, "total"))
			{
				return ClogParseOutcome.invalid();
			}

			int rootObtained = Json.strictInt(root, "obtained");
			int rootTotal = Json.strictInt(root, "total");
			Map<String, List<ClogResult.ClogItem>> obtainedItems = new HashMap<>();
			Map<String, List<Integer>> categoryItems = new HashMap<>();
			Map<Integer, String> itemNames = new HashMap<>();
			int parsedItemCount = 0;

			for (JsonObject tab : Json.objects(tabs))
			{
				for (JsonObject page : Json.objects(tab.get("pages")))
				{
					if (!Json.present(page, "name"))
					{
						continue;
					}
					String pageName = page.get("name").getAsString();
					JsonArray items = Json.array(page, "items");
					if (items == null)
					{
						continue;
					}

					String categoryKey = normalizePageKey(pageName);
					List<ClogResult.ClogItem> obtained = new ArrayList<>();
					List<Integer> allIds = new ArrayList<>();

					for (JsonObject item : Json.objects(items))
					{
						if (!Json.present(item, "id") || !Json.present(item, "quantity"))
						{
							continue;
						}
						parsedItemCount++;
						int id = Json.strictInt(item, "id");
						int qty = Json.strictInt(item, "quantity");
						String name = Json.present(item, "name") ? item.get("name").getAsString() : null;

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

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class ClogParseOutcome
	{
		private final ClogParseState state;
		@Nullable final ClogResult result;

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

	/** A RuneProfile page name as the plugin names pages, the names TempleOSRS's pages and the boss grid go by. */
	static String normalizePageKey(String pageName)
	{
		return ClogService.bossToCategory(pageName);
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static final class RuneProfileSummary
	{
		final AccountType accountType;
		final CombatAchievementResult combatAchievements;
		final String clan;
		final ClanTitle clanTitle;

	}
}
