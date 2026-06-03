package com.killclog;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
					String text = response.isSuccessful() && body != null ? body.string() : null;
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

	private HttpUtil()
	{
	}
}
