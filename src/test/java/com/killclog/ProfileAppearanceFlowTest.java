package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
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
	public void staleOriginalWaitsForFreshEquipmentBeforeRegistrationOrPublish() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		try (Harness h = new Harness(chain ->
		{
			calls.incrementAndGet();
			return response(chain, 200, READY);
		}))
		{
			h.equipment[3] = 22325 + PlayerComposition.ITEM_OFFSET;
			h.worn[3] = new Item(4151, 1);
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, result.outcome);
			assertEquals("Change equipment, then retry", KillClogPlugin.characterPublishTerminalStatus(result.outcome));
			assertTrue(result.message.startsWith("Equip or unequip an item"));
			assertEquals(0, calls.get());
			h.secret();
			h.equipment[3] = 4151 + PlayerComposition.ITEM_OFFSET;
			h.service.captureOriginalAppearance(h.player);
			assertEquals(ProfileAppearanceService.Outcome.PUBLISHED, h.publish().get(3, TimeUnit.SECONDS).outcome);
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void publishesOriginalRecipeBeforeCosmeticEventHandlers() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			okio.Buffer body = new okio.Buffer();
			chain.request().body().writeTo(body);
			JsonObject json = new Gson().fromJson(body.readUtf8(), JsonObject.class);
			assertEquals(4151 + PlayerComposition.ITEM_OFFSET, json.getAsJsonArray("equipment").get(3).getAsInt());
			assertEquals(300, json.getAsJsonArray("equipment").get(6).getAsInt());
			assertEquals(2, json.getAsJsonArray("colors").get(0).getAsInt());
			assertEquals(808, json.get("idle_pose_animation").getAsInt());
			return response(chain, 200, READY);
		}))
		{
			h.secret();
			h.equipment[3] = 4151 + PlayerComposition.ITEM_OFFSET;
			h.equipment[6] = 300;
			h.colors[0] = 2;
			h.worn[3] = new Item(4151, 1);
			KillClogPlugin plugin = new KillClogPlugin();
			java.lang.reflect.Field field = KillClogPlugin.class.getDeclaredField("profileAppearanceService");
			field.setAccessible(true);
			field.set(plugin, h.service);
			EventBus bus = new EventBus();
			bus.register(new CosmeticSubscriber(h));
			bus.register(plugin);
			// Initial appearance arrives during loading, before normal logged-in UI.
			h.loading = true;
			bus.post(new PlayerChanged(h.player));
			h.loading = false;
			assertEquals(22325 + PlayerComposition.ITEM_OFFSET, h.equipment[3]);
			assertEquals(ProfileAppearanceService.Outcome.PUBLISHED, h.publish().get(3, TimeUnit.SECONDS).outcome);
		}
	}

	private static final class CosmeticSubscriber
	{
		private final Harness harness;
		private CosmeticSubscriber(Harness harness)
		{
			this.harness = harness;
		}
		@Subscribe(priority = 1)
		public void onPlayerChanged(PlayerChanged event)
		{
			harness.equipment[3] = 22325 + PlayerComposition.ITEM_OFFSET;
			harness.equipment[6] = 0;
			harness.colors[0] = 6;
			harness.idle.set(5318);
		}
	}

	@Test
	public void missingOrForeignOriginalNeverFallsBackToVisibleComposition() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			h.service.clearOriginalAppearance();
			assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, h.publish().get().outcome);
			h.account.set(2);
			h.service.captureOriginalAppearance(h.player);
			h.account.set(1);
			assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, h.publish().get().outcome);
			h.service.captureOriginalAppearance(h.player);
			h.player = proxy(Player.class, name -> "getName".equals(name) ? "Test player" : null);
			assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, h.publish().get().outcome);
		}
	}

	@Test
	public void invalidFreshAppearanceClearsPreviousSnapshot() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			h.transform.set(123);
			h.service.captureOriginalAppearance(h.player);
			h.transform.set(-1);
			assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, h.publish().get().outcome);
		}
	}

	@Test
	public void hopAndConnectionLossRequireNewOriginalAppearance() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			KillClogPlugin plugin = new KillClogPlugin();
			java.lang.reflect.Field field = KillClogPlugin.class.getDeclaredField("profileAppearanceService");
			field.setAccessible(true);
			field.set(plugin, h.service);
			for (GameState state : new GameState[]{GameState.HOPPING, GameState.CONNECTION_LOST})
			{
				h.service.captureOriginalAppearance(h.player);
				GameStateChanged event = new GameStateChanged();
				event.setGameState(state);
				plugin.onGameStateChanged(event);
				assertEquals(ProfileAppearanceService.Outcome.APPEARANCE_PENDING, h.publish().get().outcome);
			}
		}
	}

	@Test
	public void missingEquipmentContainerMakesNoRequest() throws Exception
	{
		try (Harness h = new Harness(chain ->
		{
			throw new AssertionError("Unexpected HTTP request");
		}))
		{
			h.equipmentReady = false;
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.FAILED, result.outcome);
			assertTrue(result.message.startsWith("Your equipment is not ready."));
		}
	}

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

	@Test
	public void missingRecoveryTokenDoesNotPromiseTimedRetry() throws Exception
	{
		try (Harness h = new Harness(chain -> response(chain, 409,
			"{\"error\":\"appearance_recovery_pending\",\"activates_at\":\"2030-09-20T12:00:00Z\"}")))
		{
			ProfileAppearanceService.PublishResult result = h.publish().get(3, TimeUnit.SECONDS);
			assertEquals(ProfileAppearanceService.Outcome.RECOVERY_PENDING, result.outcome);
			assertTrue(result.message.contains("started elsewhere"));
			assertFalse(result.message.contains("Sep 20"));
		}
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
		private final int[] equipment = new int[12];
		private final int[] colors = new int[5];
		private final AtomicInteger idle = new AtomicInteger(808);
		private Player player;
		private final Item[] worn = new Item[14];
		private boolean equipmentReady = true;
		private boolean loading;
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
					case "getEquipmentIds": return equipment;
					case "getColors": return colors;
					default: return null;
				}
			});
			player = proxy(Player.class, name ->
				"getName".equals(name) ? "Test player" : "getPlayerComposition".equals(name) ? composition
					: "getIdlePoseAnimation".equals(name) ? idle.get() : null);
			Client client = proxy(Client.class, name ->
			{
				switch (name)
				{
					case "getItemContainer": return equipmentReady
						? proxy(ItemContainer.class, method -> "getItems".equals(method) ? worn : null) : null;
					case "getLocalPlayer": return player;
					case "getAccountHash": return account.get();
					case "getRevision": return 237;
					case "getGameState": return loading ? GameState.LOADING
						: loggedIn.get() ? GameState.LOGGED_IN : GameState.LOGIN_SCREEN;
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
			service.captureOriginalAppearance(player);
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
