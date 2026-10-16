package com.killclog;

import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpUtilReadFailureTest
{
	@Test
	public void aBodyThatFailsToReadStillAnswersTheRequest() throws Exception
	{
		// Any failure while reading, not only an IOException, must complete the call: an
		// unanswered future would hold its single-flight entry for the rest of the session.
		ResponseBody broken = new ResponseBody()
		{
			@Override
			public MediaType contentType()
			{
				return MediaType.parse("application/json");
			}

			@Override
			public long contentLength()
			{
				return -1;
			}

			@Override
			public BufferedSource source()
			{
				throw new IllegalStateException("fixture: unreadable body");
			}
		};
		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain -> new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(200).message("fixture").body(broken).build())
			.build();
		HttpUtil.HttpResult result = HttpUtil.httpGet(client, "https://example.test/answer")
			.get(3, TimeUnit.SECONDS);
		assertEquals(-1, result.code);
		assertNull(result.body);
	}
}
