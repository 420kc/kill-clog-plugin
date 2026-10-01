package com.killclog;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Shared HTTP plumbing used by ClogService and RuneProfileService.
 */
@Slf4j
final class HttpUtil
{
	static final String USER_AGENT =
		"kill-clog-RuneLite-Plugin (https://github.com/420kc/kill-clog-plugin)";

	// A hostile or corrupt response must not exhaust client memory before the
	// JSON parser gets a chance to reject it. No legitimate provider payload
	// approaches this.
	static final long MAX_BODY_BYTES = 8L * 1024 * 1024;

	/** Services own registration and cleanup; subscribers receive independent futures. */
	static <T> CompletableFuture<T> singleFlightLookup(Map<String, CompletableFuture<T>> flights,
		String key, Supplier<CompletableFuture<T>> start)
	{
		CompletableFuture<T> flight = flights.computeIfAbsent(key, ignored -> start.get());
		// Even an immediate response must be registered before cleanup runs.
		flight.whenComplete((result, error) -> flights.remove(key, flight));
		return flight.copy();
	}

	/** Per-name lookup caches hold at most this many names. */
	static final int CACHE_CAP = 256;

	/** Past the cap, stamps older than their TTL go: they only ever meant "skip this name for a while". */
	static void prune(Map<String, Long> stamps, long ttlMs)
	{
		if (stamps.size() > CACHE_CAP)
		{
			long now = System.currentTimeMillis();
			stamps.values().removeIf(at -> now - at >= ttlMs);
		}
	}

	/** Past the cap, the name fetched longest ago leaves both maps; it is fetched again when asked for. */
	static void evictStalest(Map<String, ?> values, Map<String, Long> fetched)
	{
		if (values.size() > CACHE_CAP)
		{
			fetched.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).ifPresent(oldest ->
			{
				values.remove(oldest);
				fetched.remove(oldest);
			});
		}
	}

	/** An RSN as a path segment: spaces must be %20 (URLEncoder yields '+', valid only in a query). */
	static String pathSegment(String rsn)
	{
		return URLEncoder.encode(rsn, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** One provider lane: success, not-found and failure stamps, in-flight dedup and a breaker. */
	static final class Lane<T>
	{
		static final long RESULT_TTL_MS = 5 * 60 * 1000;       // 5 min -- fresh success
		static final long NOT_FOUND_TTL_MS = 60 * 60 * 1000;  // 1 hour -- not synced
		static final long FAILURE_TTL_MS = 3 * 60 * 1000;     // 3 min -- transient failure

		final Map<String, T> values = new ConcurrentHashMap<>();
		final Map<String, Long> fetched = new ConcurrentHashMap<>();
		final Map<String, Long> notFound = new ConcurrentHashMap<>();
		final Map<String, Long> failed = new ConcurrentHashMap<>();
		final Map<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();
		final CircuitBreaker breaker;

		Lane(CircuitBreaker breaker)
		{
			this.breaker = breaker;
		}

		@Nullable
		T fresh(String key)
		{
			T value = values.get(key);
			return value != null && System.currentTimeMillis() - fetched.getOrDefault(key, 0L) < RESULT_TTL_MS ? value : null;
		}

		/** True while the lane answers from what it holds; a previous success outlives its TTL. */
		boolean hold(String key)
		{
			long now = System.currentTimeMillis();
			return fresh(key) != null || now - notFound.getOrDefault(key, 0L) < NOT_FOUND_TTL_MS
				|| now - failed.getOrDefault(key, 0L) < FAILURE_TTL_MS || breaker.isOpen();
		}

		CompletableFuture<T> lookup(String key, Supplier<CompletableFuture<T>> start)
		{
			return hold(key) ? CompletableFuture.completedFuture(values.get(key)) : singleFlightLookup(inFlight, key, start);
		}

		T fail(String key)
		{
			failed.put(key, System.currentTimeMillis());
			prune(failed, FAILURE_TTL_MS);
			breaker.failure();
			return values.get(key);
		}

		/** Not on this provider: skip the name for an hour, keeping whatever it held. */
		T missing(String key)
		{
			notFound.put(key, System.currentTimeMillis());
			prune(notFound, NOT_FOUND_TTL_MS);
			return values.get(key);
		}

		/** Results land on many threads; storing and trimming as one step keeps the cap. */
		synchronized T ok(String key, T value)
		{
			values.put(key, value);
			fetched.put(key, System.currentTimeMillis());
			evictStalest(values, fetched);
			return value;
		}
	}

	/** HTTP status code (-1 on transport failure) plus the body of a successful response. */
	static final class HttpResult
	{
		final int code;
		final String body;
		final int retryAfterSeconds;

		HttpResult(int code, String body)
		{
			this(code, body, 0);
		}

		HttpResult(int code, String body, int retryAfterSeconds)
		{
			this.code = code;
			this.body = body;
			this.retryAfterSeconds = retryAfterSeconds;
		}
	}

	static int retryAfterSeconds(@Nullable String value)
	{
		if (value == null) return 0;
		try
		{
			long seconds = value.trim().matches("[0-9]+") ? Long.parseLong(value.trim())
				: (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
					.toInstant().toEpochMilli() - System.currentTimeMillis() + 999) / 1000;
			return (int) Math.max(0, Math.min(seconds, 86_400));
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	static CompletableFuture<HttpResult> httpGet(OkHttpClient client, String url)
	{
		return send(client, url, null, null);
	}

	/** Read a response body capped at {@link #MAX_BODY_BYTES}; null when oversized. */
	@Nullable
	private static String readBounded(ResponseBody body) throws IOException
	{
		if (body.contentLength() > MAX_BODY_BYTES)
		{
			return null;
		}
		// request() returning true means at least cap+1 bytes exist (covers
		// chunked responses with no content-length); false means the whole
		// body is already buffered under the cap, and string() drains it.
		if (body.source().request(MAX_BODY_BYTES + 1))
		{
			return null;
		}
		return body.string();
	}

	static CompletableFuture<HttpResult> httpPostJson(OkHttpClient client, String url, String json,
		@Nullable String bearerToken)
	{
		return send(client, url, json, bearerToken);
	}

	/** GETs keep only a successful body; POSTs keep every body and the Retry-After advice. */
	private static CompletableFuture<HttpResult> send(OkHttpClient client, String url, @Nullable String json,
		@Nullable String bearerToken)
	{
		boolean post = json != null;
		log.debug("HTTP {}: {}", post ? "POST" : "GET", url);
		CompletableFuture<HttpResult> future = new CompletableFuture<>();

		Request.Builder request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT);
		if (post)
		{
			request.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json));
		}
		if (bearerToken != null && bearerToken.matches("[a-f0-9]{64}"))
		{
			request.header("Authorization", "Bearer " + bearerToken);
		}
		KillClogEndpoint.addStagingHeader(request, url);

		client.newCall(request.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("HTTP request failed for {}: {}", url, e.getMessage());
				future.complete(new HttpResult(-1, null));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					String text = (post || response.isSuccessful()) && body != null ? readBounded(body) : null;
					future.complete(new HttpResult(response.code(), text,
						post ? retryAfterSeconds(response.header("Retry-After")) : 0));
				}
				catch (IOException e)
				{
					log.debug("Failed to read response for {}: {}", url, e.getMessage());
					future.complete(new HttpResult(post ? response.code() : -1, null));
				}
			}
		});

		return future;
	}

	private HttpUtil()
	{
	}
}
