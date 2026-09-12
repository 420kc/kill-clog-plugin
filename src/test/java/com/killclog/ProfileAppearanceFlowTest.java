package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.client.callback.ClientThread;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProfileAppearanceFlowTest
{
	private static final String SECRET = "a".repeat(64);
	private static final String TOKEN = "b".repeat(64);
	private static final String READY = "{\"published\":true,\"render_status\":\"ready\"}";

	@Test
	public void registrationThenPublishUsesNewCredential() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			if (calls.incrementAndGet() == 1)
			{
				assertTrue(chain.request().url().encodedPath().endsWith("/device"));
				return response(chain, 201, "{\"device_secret\":\"" + SECRET + "\"}");
			}
			assertEquals("Bearer " + SECRET, chain.request().header("Authorization"));
			return response(chain, 200, READY);
		}))
		{
			assertEquals(ProfileAppearanceService.Outcome.PUBLISHED, h.publish().get(5, TimeUnit.SECONDS).outcome);
			assertEquals(2, calls.get());
			assertEquals(SECRET, h.config.get(h.key("deviceSecret")));
		}
	}

	@Test
	public void disablingDuringRegistrationSavesIssuedSecretButDoesNotPublish() throws Exception
	{
		cancelRegistration(false);
	}

	@Test
	public void accountSwitchDuringRegistrationDoesNotPublish() throws Exception
	{
		cancelRegistration(true);
	}

	private void cancelRegistration(boolean switchAccount) throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			entered.countDown();
			await(release);
			return response(chain, 201, "{\"device_secret\":\"" + SECRET + "\"}");
		}))
		{
			CompletableFuture<ProfileAppearanceService.PublishResult> first = h.publish();
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			if (switchAccount) h.account.set(2); else h.authorized.set(false);
			assertEquals(ProfileAppearanceService.Outcome.BUSY, h.publish().get(3, TimeUnit.SECONDS).outcome);
			release.countDown();
			assertEquals(ProfileAppearanceService.Outcome.CANCELLED, first.get(3, TimeUnit.SECONDS).outcome);
			assertEquals(1, calls.get());
			assertEquals(SECRET, h.config.get(h.key("deviceSecret")));
			assertNull(h.config.get(ProfileAppearanceService.scopedKey(2, "deviceSecret")));
		}
		finally
		{
			release.countDown();
		}
	}

	@Test
	public void logoutBeforeDispatchMakesNoRequest() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			h.loggedIn.set(false);
			assertEquals(ProfileAppearanceService.Outcome.CANCELLED, h.publish().get(3, TimeUnit.SECONDS).outcome);
		}
	}

	@Test
	public void acceptedRenderIsNeutralAndRepeatClicksDoNotRepublish() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 202, "{\"accepted\":true,\"published\":false,\"render_status\":\"pending_renderer\"}");
		}))
		{
			h.secret();
			assertEquals(ProfileAppearanceService.Outcome.RENDERING, h.publish().get(3, TimeUnit.SECONDS).outcome);
			assertEquals(ProfileAppearanceService.Outcome.RENDERING, h.publish().get(3, TimeUnit.SECONDS).outcome);
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void recoveryWaitRetainsDateAndDoesNotClaimSuccess() throws Exception
	{
		try (Harness h = new Harness(chain -> response(chain, 409, "{\"error\":\"appearance_recovery_wait\"}")))
		{
			h.config.put(h.key("recoveryToken"), TOKEN);
			h.config.put(h.key("recoveryActivatesAt"), "2030-09-20T12:00:00Z");
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.RECOVERY_PENDING, result.outcome);
			assertTrue(result.message.contains("Sep 20"));
			assertEquals("2030-09-20T12:00:00Z", h.config.get(h.key("recoveryActivatesAt")));
		}
	}

	@Test
	public void freshRecoverySavesTokenAndShowsWait() throws Exception
	{
		try (Harness h = new Harness(chain -> response(chain, 202,
			"{\"recovery_token\":\"" + TOKEN + "\",\"activates_at\":\"2030-09-20T12:00:00Z\"}")))
		{
			assertEquals(ProfileAppearanceService.Outcome.RECOVERY_PENDING, h.publish().get(3, TimeUnit.SECONDS).outcome);
			assertEquals(TOKEN, h.config.get(h.key("recoveryToken")));
		}
	}

	@Test
	public void serverRetryAfterPreventsRepeatRequests() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 429, "{\"error\":\"rate_limited\",\"request_id\":\"0123456789abcdef\"}")
				.newBuilder().header("Retry-After", "120").build();
		}))
		{
			h.secret();
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.FAILED, result.outcome);
			assertTrue(result.message.contains("Retry after"));
			assertTrue(result.message.contains("0123456789abcdef"));
			h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void lostPublishResponseIsUnknownAndDoesNotAutomaticallyRetry() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			throw new java.io.IOException("test connection loss");
		}))
		{
			h.secret();
			assertEquals(ProfileAppearanceService.Outcome.UNKNOWN, h.publish().get(3, TimeUnit.SECONDS).outcome);
			h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void unsupportedTransformFailsLocally() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			h.transform.set(123);
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.FAILED, result.outcome);
			assertTrue(result.message.contains("normal player form"));
		}
	}

	@Test
	public void malformedResponseCannotReportSuccessOrLeakBody() throws Exception
	{
		try (Harness h = new Harness(chain -> response(chain, 200, "<html>private upstream detail</html>")))
		{
			h.secret();
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.FAILED, result.outcome);
			assertFalse(result.message.contains("private upstream detail"));
		}
	}

	@Test
	public void cancelledRecoveryRetryDoesNotSendPublish() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		AtomicBoolean allowed = new AtomicBoolean(true);
		try (Harness h = new Harness(chain ->
		{
			if (calls.incrementAndGet() == 1)
			{
				return response(chain, 409, "{\"error\":\"appearance_recovery_pending\"}");
			}
			assertTrue(chain.request().url().encodedPath().endsWith("/device/cancel"));
			allowed.set(false);
			return response(chain, 200, "{\"recovery_cancelled\":true}");
		}))
		{
			h.secret();
			assertEquals(ProfileAppearanceService.Outcome.CANCELLED,
				h.service.publishCurrent("Test player", 1, allowed::get).get(5, TimeUnit.SECONDS).outcome);
			assertEquals(2, calls.get());
		}
	}

	@Test
	public void claimedRecoveryPublishesOnceAndClearsPendingToken() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			if (calls.incrementAndGet() == 1)
			{
				assertTrue(chain.request().url().encodedPath().endsWith("/device/claim"));
				return response(chain, 200, "{\"device_secret\":\"" + SECRET + "\"}");
			}
			return response(chain, 200, READY);
		}))
		{
			h.config.put(h.key("recoveryToken"), TOKEN);
			assertEquals(ProfileAppearanceService.Outcome.PUBLISHED, h.publish().get(3, TimeUnit.SECONDS).outcome);
			assertNull(h.config.get(h.key("recoveryToken")));
			assertEquals(2, calls.get());
		}
	}

	@Test
	public void rendererFailureUsesSpecificReasonAndBacksOff() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 503, "{\"accepted\":true,\"error\":\"appearance_render_failed\"}");
		}))
		{
			h.secret();
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.FAILED, result.outcome);
			assertTrue(result.message.contains("render"));
			h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void retryAfterAcceptsSecondsAndHttpDateButRejectsMalformed()
	{
		assertEquals(120, HttpUtil.retryAfterSeconds("120"));
		String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
			java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120));
		int delay = HttpUtil.retryAfterSeconds(date);
		assertTrue(delay >= 119 && delay <= 120);
		assertEquals(0, HttpUtil.retryAfterSeconds("<html>"));
		assertEquals(0, HttpUtil.retryAfterSeconds(null));
	}

	private static void await(CountDownLatch latch) throws java.io.IOException
	{
		try
		{
			if (!latch.await(3, TimeUnit.SECONDS)) throw new java.io.IOException("Test timed out");
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt(); throw new java.io.IOException(e);
		}
	}

	private static Response response(Interceptor.Chain chain, int status, String json)
	{
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
			.code(status).message("test").body(ResponseBody.create(MediaType.parse("application/json"), json)).build();
	}

	private static final class Harness implements AutoCloseable
	{
		private final AtomicBoolean authorized = new AtomicBoolean(true);
		private final AtomicBoolean loggedIn = new AtomicBoolean(true);
		private final AtomicLong account = new AtomicLong(1);
		private final AtomicInteger transform = new AtomicInteger(-1);
		private final Map<String, String> config = new ConcurrentHashMap<>();
		private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		private final OkHttpClient http;
		private final ProfileAppearanceService service;

		private Harness(Interceptor interceptor)
		{
			PlayerComposition composition = proxy(PlayerComposition.class, (name) ->
			{
				switch (name)
				{
					case "getTransformedNpcId": return transform.get();
					case "getGender": return 0;
					case "getEquipmentIds": return new int[12];
					case "getColors": return new int[5];
					default: return null;
				}
			});
			Player player = proxy(Player.class, name ->
				"getName".equals(name) ? "Test player" : "getPlayerComposition".equals(name) ? composition
					: "getIdlePoseAnimation".equals(name) ? 808 : null);
			Client client = proxy(Client.class, name ->
			{
				switch (name)
				{
					case "getLocalPlayer": return player;
					case "getAccountHash": return account.get();
					case "getRevision": return 237;
					case "getGameState": return loggedIn.get() ? GameState.LOGGED_IN : GameState.LOGIN_SCREEN;
					default: return null;
				}
			});
			ClientThread clientThread = new ClientThread()
			{
				@Override
				public void invokeLater(Runnable action)
				{
					action.run();
				}
			};
			http = new OkHttpClient.Builder().addInterceptor(interceptor).build();
			service = new ProfileAppearanceService(client, clientThread, executor, http, new Gson(), config::get,
				(key, value) ->
				{
					if (value == null) config.remove(key); else config.put(key, value);
				});
		}

		private String key(String name)
		{
			return ProfileAppearanceService.scopedKey(1, name);
		}
		private void secret()
		{
			config.put(key("deviceSecret"), SECRET);
		}
		private CompletableFuture<ProfileAppearanceService.PublishResult> publish()
		{
			return service.publishCurrent("Test player", 1, authorized::get);
		}

		@Override public void close()
		{
			executor.shutdownNow();
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}

	private static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> value)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> value.apply(method.getName())));
	}
}
