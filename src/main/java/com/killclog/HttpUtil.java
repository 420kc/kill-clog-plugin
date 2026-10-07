package com.killclog;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
 * Shared HTTP plumbing for every provider and killclog.com request.
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
		// Even an immediate response must be registered before cleanup runs, and subscribers hear
		// only after it: a lookup started from their callback then starts a fresh flight.
		return flight.whenComplete((result, error) -> flights.remove(key, flight)).copy();
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
		private final long resultTtlMs;
		private final long notFoundTtlMs;

		Lane(CircuitBreaker breaker)
		{
			this(breaker, RESULT_TTL_MS, NOT_FOUND_TTL_MS);
		}

		/** A lane whose answers age differently from a provider lookup's. */
		Lane(CircuitBreaker breaker, long resultTtlMs, long notFoundTtlMs)
		{
			this.breaker = breaker;
			this.resultTtlMs = resultTtlMs;
			this.notFoundTtlMs = notFoundTtlMs;
		}

		@Nullable
		T fresh(String key)
		{
			T value = values.get(key);
			return value != null && System.currentTimeMillis() - fetched.getOrDefault(key, 0L) < resultTtlMs ? value : null;
		}

		/** True while the lane answers from what it holds; a previous success outlives its TTL. */
		boolean hold(String key)
		{
			return fresh(key) != null || resting(key);
		}

		/** True while a name is not asked about: a recent miss or failure, or the breaker open. */
		boolean resting(String key)
		{
			long now = System.currentTimeMillis();
			return now - notFound.getOrDefault(key, 0L) < notFoundTtlMs
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

		/** Not on this provider: skip the name for the lane's not-found time, keeping whatever it held. */
		T missing(String key)
		{
			notFound.put(key, System.currentTimeMillis());
			prune(notFound, notFoundTtlMs);
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
		return fitsWithin(body, MAX_BODY_BYTES) ? body.string() : null;
	}

	/**
	 * True when the whole body is at most {@code max} bytes. request() returning true means at
	 * least max+1 bytes exist (covers chunked responses with no content-length); false means the
	 * whole body is already buffered under the cap, ready to drain.
	 */
	private static boolean fitsWithin(ResponseBody body, long max) throws IOException
	{
		return body.contentLength() <= max && !body.source().request(max + 1);
	}

	/**
	 * A GET whose successful body is bytes, at most {@code maxBytes} of them; anything larger is dropped.
	 * With {@code ifNoneMatch}, an unchanged resource answers 304 with no body.
	 */
	static CompletableFuture<BytesResult> httpGetBytes(OkHttpClient client, String url, int maxBytes,
		@Nullable String ifNoneMatch)
	{
		Request.Builder request = request(url);
		if (ifNoneMatch != null)
		{
			request.header("If-None-Match", ifNoneMatch);
		}
		BytesResult failed = new BytesResult(-1, null, null);
		return enqueue(client, request.build(),
			(response, body) -> new BytesResult(response.code(),
				response.isSuccessful() && body != null && fitsWithin(body, maxBytes) ? body.bytes() : null,
				response.header("ETag")),
			response -> failed, failed);
	}

	/** HTTP status code (-1 on transport failure), the bytes of a successful response, and its ETag. */
	static final class BytesResult
	{
		final int code;
		@Nullable
		final byte[] bytes;
		@Nullable
		final String etag;

		BytesResult(int code, @Nullable byte[] bytes, @Nullable String etag)
		{
			this.code = code;
			this.bytes = bytes;
			this.etag = etag;
		}
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
		Request.Builder request = request(url);
		if (post)
		{
			request.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json));
		}
		if (bearerToken != null && bearerToken.matches("[a-f0-9]{64}"))
		{
			request.header("Authorization", "Bearer " + bearerToken);
		}
		return enqueue(client, request.build(),
			(response, body) -> new HttpResult(response.code(),
				(post || response.isSuccessful()) && body != null ? readBounded(body) : null,
				post ? retryAfterSeconds(response.header("Retry-After")) : 0),
			response -> new HttpResult(post ? response.code() : -1, null),
			new HttpResult(-1, null));
	}

	/** Makes a call's answer from its response; it may read the body, which is closed afterwards. */
	@FunctionalInterface
	private interface BodyReader<T>
	{
		T read(Response response, @Nullable ResponseBody body) throws IOException;
	}

	/**
	 * Every request goes out here, on OkHttp's dispatcher. {@code unreadable} answers when the body
	 * can't be read, and {@code unreachable} when the request never got a response.
	 */
	private static <T> CompletableFuture<T> enqueue(OkHttpClient client, Request request, BodyReader<T> reader,
		Function<Response, T> unreadable, T unreachable)
	{
		String url = request.url().toString();
		log.debug("HTTP {}: {}", request.method(), url);
		CompletableFuture<T> future = new CompletableFuture<>();
		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("HTTP request failed for {}: {}", url, e.getMessage());
				future.complete(unreachable);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					future.complete(reader.read(response, body));
				}
				catch (IOException e)
				{
					log.debug("Failed to read response for {}: {}", url, e.getMessage());
					future.complete(unreadable.apply(response));
				}
			}
		});
		return future;
	}

	/** Every request carries the plugin's User-Agent, and the staging token when it goes to staging. */
	private static Request.Builder request(String url)
	{
		Request.Builder request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT);
		KillClogEndpoint.addStagingHeader(request, url);
		return request;
	}

	private HttpUtil()
	{
	}
}
