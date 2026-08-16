package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
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
	static final String CLIENT_VERSION = "2.1.0";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LocalClogCache localClogCache;

	/** Outcome of a sync attempt, surfaced for chat feedback. */
	static final class SyncResult
	{
		final boolean ok;
		final boolean dryRun;
		final int code;
		final String message;
		/** Server says another sync holds this player's lock: retry, don't fail. */
		final boolean retryAdvised;
		final int retryAfterSeconds;

		SyncResult(boolean ok, boolean dryRun, int code, String message)
		{
			this(ok, dryRun, code, message, false, 0);
		}

		SyncResult(boolean ok, boolean dryRun, int code, String message,
			boolean retryAdvised, int retryAfterSeconds)
		{
			this.ok = ok;
			this.dryRun = dryRun;
			this.code = code;
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
	CompletableFuture<SyncResult> syncCollectionLog(String rsn, long accountHash,
		@Nullable AccountType accountType, Map<String, Double> personalBests,
		Map<String, DetailedPb> detailedPersonalBests)
	{
		if (rsn == null || rsn.isBlank())
		{
			return CompletableFuture.completedFuture(
				new SyncResult(false, false, -1, "No player to sync."));
		}

		// Rename continuity, local half: if this account's data lives under a
		// previous name's file, it follows the player BEFORE the local-store
		// check below - otherwise a renamed player's sync dies right here and
		// the server's own migration never even sees a packet.
		if (!localClogCache.followNameChangeForSync(rsn, accountHash))
		{
			// The disk half of a migration or adoption did not land - the
			// local store's provenance is unresolved and its bytes must not
			// become a payload. The next login (or sync) re-decides.
			return CompletableFuture.completedFuture(new SyncResult(false, false, -1,
				"Local name ownership is still settling - sync skipped this round."));
		}

		// The player's own accumulated local store is the payload: months of
		// their in-client captures and live unlocks, published as their own
		// truth - requiring a full re-walk to seed the sync would be needless
		// friction. Only the logged-in account can
		// ever push, and only its own file - filtered to items this client
		// observed first-hand, so provider-cached data (pre-login lookups,
		// cross-character searches) can never launder into first-party proof.
		ClogResult clog = localClogCache.toFirstPartySyncResult(rsn);
		if (clog == null || clog.getObtainedItems().isEmpty())
		{
			return CompletableFuture.completedFuture(
				new SyncResult(false, false, -1, "No local collection log to sync yet."));
		}

		String body = gson.toJson(buildBody(accountHash, accountType, clog, personalBests, detailedPersonalBests));
		final int observedCount = countUniqueItems(clog);
		// URLEncoder form-encodes spaces as '+', which the server preserves and
		// rejects; path segments need %20.
		String url = KillClogEndpoint.apiBaseUrl() + "/player/"
			+ URLEncoder.encode(rsn, StandardCharsets.UTF_8).replace("+", "%20") + "/sync";

		log.debug("Syncing collection log for '{}' to {} ({} items)",
			rsn, url, observedCount);
		final int pbCount = personalBests != null ? personalBests.size() : 0;
		return HttpUtil.httpPostJson(httpClient, url, body).thenApply(r ->
		{
			if (r.code >= 200 && r.code < 300)
			{
				boolean dryRun = responseSaysDryRun(r.body);
				return new SyncResult(true, dryRun, r.code,
					"Done! ("
					+ observedCount + (observedCount == 1 ? " item" : " items")
					+ (pbCount > 0 ? ", " + pbCount + (pbCount == 1 ? " pb" : " pbs") : "")
					+ (dryRun ? ", server dry run" : "") + ")");
			}
			// 409 sync_in_flight is contention, not failure: another client of
			// this account holds the per-player lock for a moment. Advise a
			// short retry instead of booking a failure.
			if (r.code == 409 && r.body != null && r.body.contains("sync_in_flight"))
			{
				return new SyncResult(false, false, r.code,
					"Another sync for this account is in flight - retrying shortly.",
					true, parseRetryAfterSeconds(r.body));
			}
			// The server's identity arbitration answers (2026-08-16 wire
			// contract): each gets plain words instead of a bare HTTP code.
			if (r.code == 409 && r.body != null && r.body.contains("name_active_with_another_account"))
			{
				return new SyncResult(false, false, r.code,
					"This name's previous owner played recently - killclog.com will "
					+ "accept your log after their continuity window passes.");
			}
			if (r.code == 409 && r.body != null && r.body.contains("account_hash_mismatch"))
			{
				return new SyncResult(false, false, r.code,
					"This name is registered to a different account on killclog.com.");
			}
			if (r.code == 451)
			{
				return new SyncResult(false, false, r.code,
					"This account has opted out of killclog.com syncing.");
			}
			log.debug("killclog sync failed for '{}': HTTP {}", rsn, r.code);
			return new SyncResult(false, false, r.code,
				"killclog.com sync failed (HTTP " + r.code + ").");
		});
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

	private static int countUniqueItems(ClogResult clog)
	{
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (List<ClogResult.ClogItem> items : clog.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items)
			{
				ids.add(item.getId());
			}
		}
		return ids.size();
	}

	/**
	 * Request body per the ingest contract:
	 * {@code { account_hash, account_type?, clog: [{item_id, quantity, categories[]}], client_version }}.
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
		final List<String> categories = new ArrayList<>();

		ItemEntry(int id, int quantity)
		{
			this.id = id;
			this.quantity = Math.max(quantity, 1);
		}
	}
}
