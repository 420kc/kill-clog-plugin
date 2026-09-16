package com.killclog;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogCatalogFlightTest
{
	@Test
	public void immediateFailureCanRetryAndCacheSuccess() throws Exception
	{
		for (String name : List.of("fetchCategories", "fetchItemNames"))
		{
			for (boolean malformed : List.of(false, true))
			{
				CatalogHttp http = new CatalogHttp();
				http.body = malformed ? "not json" : null;
				ClogService service = service(http);
				assertNull(fetch(service, name).join());
				http.body = successBody(name);
				assertFalse(fetch(service, name).join().isEmpty());
				assertEquals(2, http.starts);
				assertFalse(fetch(service, name).join().isEmpty());
				assertEquals("successful catalog stays cached", 2, http.starts);
			}
		}
	}

	@Test
	public void delayedFailureIsSharedAndNextRequestCanSucceed() throws Exception
	{
		for (String name : List.of("fetchCategories", "fetchItemNames"))
		{
			CatalogHttp http = new CatalogHttp();
			http.delayed = true;
			ClogService service = service(http);
			CompletableFuture<Map<?, ?>> first = fetch(service, name);
			CompletableFuture<Map<?, ?>> second = fetch(service, name);
			assertEquals(1, http.starts);
			assertFalse(first.isDone());
			CompletableFuture.runAsync(http.pending).join();
			assertNull(first.join());
			assertNull(second.join());
			http.body = successBody(name);
			CompletableFuture<Map<?, ?>> retry = fetch(service, name);
			assertNotNull(retry);
			assertEquals(2, http.starts);
			CompletableFuture.runAsync(http.pending).join();
			assertFalse(retry.join().isEmpty());
			assertFalse(fetch(service, name).join().isEmpty());
			assertEquals(2, http.starts);
		}
	}

	@Test
	public void immediateSuccessReturnsStableFuture() throws Exception
	{
		for (String name : List.of("fetchCategories", "fetchItemNames"))
		{
			CatalogHttp http = new CatalogHttp();
			http.body = successBody(name);
			ClogService service = service(http);
			assertFalse(fetch(service, name).join().isEmpty());
			assertFalse(fetch(service, name).join().isEmpty());
			assertEquals(1, http.starts);
		}
	}

	private static String successBody(String name)
	{
		return name.equals("fetchCategories") ? "{\"bosses\":{\"zulrah\":[1]}}"
			: "[{\"id\":1,\"name\":\"Item\"}]";
	}

	private static ClogService service(OkHttpClient http)
	{
		return new ClogService(http, new Gson(),
			new LocalClogCache(new Gson(), new NoopScheduledExecutorService()));
	}

	@SuppressWarnings("unchecked")
	private static CompletableFuture<Map<?, ?>> fetch(ClogService service, String name) throws Exception
	{
		Method method = ClogService.class.getDeclaredMethod(name);
		method.setAccessible(true);
		return (CompletableFuture<Map<?, ?>>) method.invoke(service);
	}

	private static final class CatalogHttp extends OkHttpClient
	{
		private int starts;
		private boolean delayed;
		private String body;
		private Runnable pending;

		@Override
		public Call newCall(Request request)
		{
			starts++;
			return new Call()
			{
				@Override
				public Request request()
				{
					return request;
				}
				@Override
				public Response execute()
				{
					throw new UnsupportedOperationException();
				}
				@Override
				public void cancel()
				{
				}
				@Override
				public boolean isExecuted()
				{
					return true;
				}
				@Override
				public boolean isCanceled()
				{
					return false;
				}
				@Override
				public Timeout timeout()
				{
					return Timeout.NONE;
				}
				@Override
				public Call clone()
				{
					throw new UnsupportedOperationException();
				}

				@Override
				public void enqueue(Callback callback)
				{
					String responseBody = body;
					pending = () ->
					{
						if (responseBody == null)
						{
							callback.onFailure(this, new IOException("fixture transport failure"));
							return;
						}
						try
						{
							callback.onResponse(this, new Response.Builder().request(request)
								.protocol(Protocol.HTTP_1_1).code(200).message("OK")
								.body(ResponseBody.create(MediaType.parse("application/json"), responseBody)).build());
						}
						catch (IOException e)
						{
							throw new AssertionError(e);
						}
					};
					if (!delayed) pending.run();
				}
			};
		}
	}
}
