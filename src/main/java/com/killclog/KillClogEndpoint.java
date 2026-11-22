package com.killclog;

import javax.annotation.Nullable;
import okhttp3.Request;

/**
 * Where Kill Clog's own requests go: production unless told otherwise. A developer can choose the one allowed staging
 * address when the JVM starts, and any other address, localhost included, is refused.
 */
final class KillClogEndpoint
{
	static final String ENDPOINT_PROPERTY = "killclog.endpoint";
	static final String STAGING_TOKEN_PROPERTY = "killclog.stagingToken";
	static final String STAGING_HEADER = "X-Killclog-Staging-Token";

	static final String PRODUCTION_API = "https://killclog.com/api";
	static final String STAGING_API = "https://staging.killclog.com/plugin-api";

	static String apiBaseUrl()
	{
		String configured = System.getProperty(ENDPOINT_PROPERTY);
		configured = stripTrailingSlashes(configured);
		return STAGING_API.equals(configured) ? STAGING_API : PRODUCTION_API;
	}

	static boolean isStagingUrl(String url)
	{
		return url != null && (url.equals(STAGING_API) || url.startsWith(STAGING_API + "/"));
	}

	static void addStagingHeader(Request.Builder request, String url)
	{
		if (!isStagingUrl(url))
		{
			return;
		}
		String token = stagingToken();
		if (token != null)
		{
			request.header(STAGING_HEADER, token);
		}
	}

	@Nullable
	private static String stagingToken()
	{
		String token = System.getProperty(STAGING_TOKEN_PROPERTY);
		if (token == null)
		{
			return null;
		}
		token = token.trim();
		return token.matches("[A-Za-z0-9_-]{32,256}") ? token : null;
	}

	@Nullable
	private static String stripTrailingSlashes(@Nullable String value)
	{
		if (value == null)
		{
			return null;
		}
		return value.trim().replaceFirst("/+$", "");
	}

	private KillClogEndpoint()
	{
	}
}
