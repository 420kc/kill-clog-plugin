package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Pushes the active player's locally captured collection log to the
 * killclog.com first-party ingest endpoint
 * ({@code POST https://killclog.com/api/player/<rsn>/sync}).
 *
 * <p>Gated behind a default-off config toggle
 * ({@link KillClogConfig#killclogSync()}); nothing is pushed until the
 * player opts in.
 *
 * <p>Scope: only the logged-in account can push, and only its own local
 * store - the log this client has accumulated across its own captures and
 * live unlocks. Publishing your own account's truth is what the opt-in
 * means; other players' data has no path here.
 */
@Slf4j
@Singleton
class SyncService
{
	// Attributed on the server per client build.
	static final String CLIENT_VERSION = "2.6.1";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalClogCache localClogCache;
	// The game's Collection Log as this client reads it, shared when killclog.com holds another.
	@Setter
	@Nullable
	private ClogIndex clogIndex;
	// The structure killclog.com holds, as its last sync reply named it; null until one has.
	private volatile String serverStructureHash;
	// Every loading screen schedules a push: one identical to the last that killclog.com took, within a day, stays home.
	static final long RESEND_UNCHANGED_MS = 24 * 60 * 60 * 1000L;
	private volatile String acceptedPush;
	private volatile long acceptedAt;
	private volatile SyncResult acceptedResult;
	/** What a push killclog.com already holds answers: nothing went, so nothing is said. */
	static final SyncResult UNCHANGED = new SyncResult(true, false, "");

	/** Outcome of a sync attempt, surfaced for chat feedback. */
	static final class SyncResult
	{
		final boolean ok;
		final boolean dryRun;
		final String message;
		/** Server says another sync holds this player's lock: retry, don't fail. */
		final boolean retryAdvised;
		final int retryAfterSeconds;

		SyncResult(boolean ok, boolean dryRun, String message)
		{
			this(ok, dryRun, message, false, 0);
		}

		SyncResult(boolean ok, boolean dryRun, String message,
			boolean retryAdvised, int retryAfterSeconds)
		{
			this.ok = ok;
			this.dryRun = dryRun;
			this.message = message;
			this.retryAdvised = retryAdvised;
			this.retryAfterSeconds = retryAfterSeconds;
		}
	}

	@Inject
	SyncService(OkHttpClient httpClient, Gson gson, LocalClogCache localClogCache)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.localClogCache = localClogCache;
	}

	/**
	 * Build the ingest body from this client's in-game observations and POST
	 * it. Completes with a failed-shape result rather than throwing.
	 *
	 * @param accountType the locally detected account type (client varbits),
	 *                    never the provider-derived one
	 */
	/**
	 * {@code cache} is the mode's own store and {@code cacheEpoch} its gather-time session; {@code always} sends even
	 * what killclog.com already holds (a click, or a character publish that needs the profile). {@code sending} runs
	 * just before a push goes.
	 */
	CompletableFuture<SyncResult> syncCollectionLog(String rsn, long accountHash,
		@Nullable AccountType accountType, Map<String, Double> personalBests,
		Map<String, DetailedPb> detailedPersonalBests, long cacheEpoch,
		KillclogSyncGate syncGate, int generation, LocalClogCache cache, String mode, boolean always, Runnable sending)
	{
		if (rsn == null || rsn.isBlank())
		{
			return CompletableFuture.completedFuture(
				new SyncResult(false, false, "No player to sync."));
		}

		// Only this session's account, its own log serving, can be the payload.
		// The caller's gather-time epoch rides in so a logout since then fences
		// the whole pre-flight inside the cache monitor.
		if (!cache.servesAccount(rsn, accountHash, cacheEpoch))
		{
			return CompletableFuture.completedFuture(new SyncResult(false, false,
				"Still checking this account. Try again in a moment."));
		}

		// The player's own accumulated local store is the payload: months of
		// their in-client captures and live unlocks, published as their own
		// truth - requiring a full re-walk to seed the sync would be needless
		// friction. Only the logged-in account can
		// ever push, and only its own file - filtered to items this client
		// observed first-hand, so provider-cached data (pre-login lookups,
		// cross-character searches) can never launder into first-party proof.
		ClogResult clog = cache.toFirstPartySyncResult(rsn);
		if (clog == null || clog.getObtainedItems().isEmpty())
		{
			return CompletableFuture.completedFuture(
				new SyncResult(false, false, "No local collection log to sync yet."));
		}

		JsonObject root = buildBody(accountHash, accountType, clog, personalBests, detailedPersonalBests);
		JsonObject structure = GameMode.MAIN.equals(mode) ? structureToShare() : null;
		if (structure != null)
		{
			root.add("clog_structure", structure);
		}
		String body = gson.toJson(root);
		final int observedCount = ClogResult.coverageCount(clog);
		String url = KillClogEndpoint.apiBaseUrl() + "/player/" + HttpUtil.pathSegment(rsn) + "/sync/" + mode;
		String push = url + body;
		if (!always && acceptedResult != null && push.equals(acceptedPush)
			&& System.currentTimeMillis() - acceptedAt < RESEND_UNCHANGED_MS)
		{
			return CompletableFuture.completedFuture(UNCHANGED);
		}
		sending.run();

		log.debug("Syncing collection log for '{}' to {} ({} items)",
			rsn, url, observedCount);
		final int pbCount = personalBests != null ? personalBests.size() : 0;
		CompletableFuture<HttpUtil.HttpResult> request = cache.commitIfSessionCurrent(
			cacheEpoch, () -> syncGate.commitIfCurrent(generation,
				() -> HttpUtil.httpPostJson(httpClient, url, body, null)));
		if (request == null)
		{
			return CompletableFuture.completedFuture(
				new SyncResult(false, false, "Sync session ended before send."));
		}
		return request.thenApply(r ->
		{
			SyncResult result = outcome(r, rsn, observedCount, pbCount);
			if (result.ok && !result.dryRun)
			{
				acceptedResult = result;
				acceptedAt = System.currentTimeMillis();
				acceptedPush = push;
			}
			return result;
		});
	}

	/** The game's structure when killclog.com named a different one in its last reply, else null. */
	@Nullable
	JsonObject structureToShare()
	{
		ClogIndex index = clogIndex;
		String held = serverStructureHash;
		JsonObject structure = index != null && held != null ? ClogStructure.of(index) : null;
		return structure != null && !held.equals(ClogStructure.hash(gson, structure)) ? structure : null;
	}

	/** What one round trip means for the player, in plain words. */
	SyncResult outcome(HttpUtil.HttpResult r, String rsn, int observedCount, int pbCount)
	{
		if (r.code >= 200 && r.code < 300)
		{
			rememberStructureHash(r.body);
			boolean dryRun = responseSaysDryRun(r.body);
			return new SyncResult(true, dryRun,
				"Collection log synced! ("
				+ observedCount + (observedCount == 1 ? " item" : " items")
				+ (pbCount > 0 ? ", " + pbCount + (pbCount == 1 ? " pb" : " pbs") : "")
				+ (dryRun ? ", not saved" : "") + ").");
		}
		// 409 sync_in_flight is contention, not failure: another client of
		// this account holds the per-player lock for a moment. Advise a
		// short retry instead of booking a failure.
		if (r.code == 409 && r.body != null && r.body.contains("sync_in_flight"))
		{
			return new SyncResult(false, false,
				"Another sync for this account is in flight - retrying shortly.",
				true, parseRetryAfterSeconds(r.body));
		}
		// The server's identity arbitration answers (2026-08-16 wire
		// contract): each gets plain words instead of a bare HTTP code.
		if (r.code == 409 && r.body != null && r.body.contains("name_active_with_another_account"))
		{
			return new SyncResult(false, false,
				"This name's previous owner played recently - Kill Clog will "
				+ "accept your log after their continuity window passes.");
		}
		if (r.code == 409 && r.body != null && r.body.contains("account_hash_mismatch"))
		{
			return new SyncResult(false, false,
				"This name is registered to a different account on Kill Clog.");
		}
		// The server no longer takes this version's syncs (a League cutover): only an update helps.
		if (r.code == 426)
		{
			return new SyncResult(false, false, FirstPartyFeedback.UPDATE_REQUIRED);
		}
		if (r.code == 451)
		{
			return new SyncResult(false, false,
				"This account has opted out of Kill Clog sync.");
		}
		log.debug("killclog sync failed for '{}': HTTP {}", rsn, r.code);
		// A restarting server answers 502 to 504 for a few seconds: worth one
		// quiet retry before anyone is told. The delay is spread out so the
		// clients it turned away do not all come back together.
		boolean restarting = r.code >= 502 && r.code <= 504;
		return new SyncResult(false, false,
			"Collection log sync failed. Try again later.",
			restarting, restarting ? 15 + ThreadLocalRandom.current().nextInt(16) : 0);
	}

	private int parseRetryAfterSeconds(String body)
	{
		try
		{
			JsonObject obj = gson.fromJson(body, JsonObject.class);
			if (obj != null && obj.has("retry_after_seconds"))
			{
				int seconds = obj.get("retry_after_seconds").getAsInt();
				// Bounded: the server advises, the client decides.
				return Math.max(1, Math.min(seconds, 30));
			}
		}
		catch (RuntimeException e)
		{
			// fall through to the default
		}
		return 2;
	}

	private void rememberStructureHash(String body)
	{
		try
		{
			JsonObject obj = body != null ? gson.fromJson(body, JsonObject.class) : null;
			if (obj != null && obj.has("clog_structure_hash") && obj.get("clog_structure_hash").isJsonPrimitive())
			{
				serverStructureHash = obj.get("clog_structure_hash").getAsString();
			}
		}
		catch (RuntimeException e)
		{
			// a reply without it changes nothing
		}
	}

	private boolean responseSaysDryRun(String body)
	{
		if (body == null || body.isEmpty())
		{
			return false;
		}
		try
		{
			JsonObject obj = gson.fromJson(body, JsonObject.class);
			return obj != null && obj.has("dry_run") && obj.get("dry_run").getAsBoolean();
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	/**
	 * Request body per the ingest contract:
	 * {@code { account_hash, account_type?, clog: [{item_id, quantity, categories[], obtained_at?}], client_version }}.
	 * Items in multiple categories merge to one entry; sorted by item id so
	 * identical stores produce identical payloads.
	 */
	private JsonObject buildBody(long accountHash, @Nullable AccountType accountType,
		ClogResult clog, Map<String, Double> personalBests,
		Map<String, DetailedPb> detailedPersonalBests)
	{
		JsonObject root = new JsonObject();
		root.addProperty("account_hash", Long.toString(accountHash));
		if (accountType != null)
		{
			root.addProperty("account_type", accountType.name().toLowerCase(Locale.ROOT));
		}
		// The game's own unique counters (varp-sourced) ride along so the
		// site can show the true "obtained out of how many" fraction instead
		// of approximating the denominator from catalogs.
		if (clog.getUniqueObtained() > 0)
		{
			root.addProperty("unique_obtained", clog.getUniqueObtained());
		}
		if (clog.getUniqueTotal() > 0)
		{
			root.addProperty("unique_total", clog.getUniqueTotal());
		}
		// PBs are the sync's defining cargo: RuneLite records them locally per
		// profile and no public provider serves them. Raw seconds; the site
		// owns formatting.
		if (personalBests != null && !personalBests.isEmpty())
		{
			JsonObject pbs = new JsonObject();
			for (Map.Entry<String, Double> entry : new TreeMap<>(personalBests).entrySet())
			{
				pbs.addProperty(entry.getKey(), entry.getValue());
			}
			root.add("pbs", pbs);
		}

		// Ladder cargo: the same bests keyed by vanilla's variant key with
		// team sizes SPLIT ("chambers of xeric solo" vs "... 5 players").
		// The collapsed map above stays for display compatibility; ladders
		// need the split because solo and team runs rank separately. Each
		// entry carries where this client observed it: "store" from
		// RuneLite's own pb store, "advlog" from the Counters scroll harvest.
		if (detailedPersonalBests != null && !detailedPersonalBests.isEmpty())
		{
			JsonObject detailed = new JsonObject();
			for (Map.Entry<String, DetailedPb> entry : new TreeMap<>(detailedPersonalBests).entrySet())
			{
				JsonObject record = new JsonObject();
				record.addProperty("seconds", entry.getValue().seconds);
				record.addProperty("source", entry.getValue().source);
				detailed.add(entry.getKey(), record);
			}
			root.add("pbs_detailed", detailed);
		}

		Map<Integer, ItemEntry> byId = new TreeMap<>();
		for (Map.Entry<String, List<ClogResult.ClogItem>> categoryEntry
			: clog.getObtainedItems().entrySet())
		{
			String category = categoryEntry.getKey();
			for (ClogResult.ClogItem item : categoryEntry.getValue())
			{
				ItemEntry entry = byId.computeIfAbsent(item.getId(),
					id -> new ItemEntry(id, item.getCount()));
				entry.quantity = Math.max(entry.quantity, item.getCount());
				String date = ClogDates.iso(item.getDate());
				if (date != null && (entry.obtainedAt == null || date.compareTo(entry.obtainedAt) < 0))
				{
					entry.obtainedAt = date;
				}
				if (!entry.categories.contains(category))
				{
					entry.categories.add(category);
				}
			}
		}

		JsonArray items = new JsonArray();
		for (ItemEntry entry : byId.values())
		{
			JsonObject obj = new JsonObject();
			obj.addProperty("item_id", entry.id);
			obj.addProperty("quantity", Math.max(entry.quantity, 1));
			if (entry.obtainedAt != null) obj.addProperty("obtained_at", entry.obtainedAt);
			JsonArray cats = new JsonArray();
			for (String category : new TreeSet<>(entry.categories))
			{
				cats.add(category);
			}
			obj.add("categories", cats);
			items.add(obj);
		}
		root.add("clog", items);
		root.addProperty("client_version", CLIENT_VERSION);
		return root;
	}

	/** One variant-keyed pb with the lane this client observed it through. */
	static final class DetailedPb
	{
		final double seconds;
		final String source;

		DetailedPb(double seconds, String source)
		{
			this.seconds = seconds;
			this.source = source;
		}
	}

	private static final class ItemEntry
	{
		final int id;
		int quantity;
		String obtainedAt;
		final List<String> categories = new ArrayList<>();

		ItemEntry(int id, int quantity)
		{
			this.id = id;
			this.quantity = Math.max(quantity, 1);
		}
	}
}
