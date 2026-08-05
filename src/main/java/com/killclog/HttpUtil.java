package com.killclog;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
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

	/** HTTP status code (-1 on transport failure) plus the body of a successful response. */
	static final class HttpResult
	{
		final int code;
		final String body;

		HttpResult(int code, String body)
		{
			this.code = code;
			this.body = body;
		}
	}

	static CompletableFuture<HttpResult> httpGet(OkHttpClient client, String url)
	{
		log.debug("HTTP GET: {}", url);
		CompletableFuture<HttpResult> future = new CompletableFuture<>();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("HTTP GET failed for {}: {}", url, e.getMessage());
				future.complete(new HttpResult(-1, null));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					String text = response.isSuccessful() && body != null ? readBounded(body) : null;
					future.complete(new HttpResult(response.code(), text));
				}
				catch (IOException e)
				{
					log.debug("Failed to read response for {}: {}", url, e.getMessage());
					future.complete(new HttpResult(-1, null));
				}
			}
		});

		return future;
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

	static CompletableFuture<HttpResult> httpPostJson(OkHttpClient client, String url, String json)
	{
		log.debug("HTTP POST: {}", url);
		CompletableFuture<HttpResult> future = new CompletableFuture<>();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json))
			.build();

		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("HTTP POST failed for {}: {}", url, e.getMessage());
				future.complete(new HttpResult(-1, null));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					String text = body != null ? readBounded(body) : null;
					future.complete(new HttpResult(response.code(), text));
				}
				catch (IOException e)
				{
					log.debug("Failed to read response for {}: {}", url, e.getMessage());
					future.complete(new HttpResult(response.code(), null));
				}
			}
		});

		return future;
	}

	private HttpUtil()
	{
	}
}
