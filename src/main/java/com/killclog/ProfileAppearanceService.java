package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Publishes the current player's bounded appearance recipe, never image data. */
@Slf4j
@Singleton
final class ProfileAppearanceService
{
	private static final String CONFIG_GROUP = "killclog-profile-appearance";
	private static final String DEVICE_SECRET_KEY = "deviceSecret";
	private static final String RECOVERY_TOKEN_KEY = "recoveryToken";
	private static final String RECOVERY_AT_KEY = "recoveryActivatesAt";
	static final long PUBLISH_RETRY_DELAY_MS = 2250L;

	enum Outcome
	{
		PUBLISHED,
		PROFILE_REQUIRED,
		DISABLED,
		RENDERING,
		RECOVERY_PENDING,
		BUSY,
		CANCELLED,
		UNKNOWN,
		FAILED
	}

	static final class PublishResult
	{
		final Outcome outcome;
		final String message;

		private PublishResult(Outcome outcome)
		{
			this(outcome, null);
		}

		private PublishResult(Outcome outcome, @Nullable String message)
		{
			this.outcome = outcome;
			this.message = message;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Function<String, String> readConfig;
	private final BiConsumer<String, String> writeConfig;
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private volatile RetryHold retryHold;

	private static final class RetryHold
	{
		final long account;
		final long until;
		final PublishResult result;

		RetryHold(long account, long until, PublishResult result)
		{
			this.account = account;
			this.until = until;
			this.result = result;
		}
	}
	private final okhttp3.OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	ProfileAppearanceService(Client client, ClientThread clientThread,
		ScheduledExecutorService executor,
		ConfigManager configManager, okhttp3.OkHttpClient httpClient, Gson gson)
	{
		this(client, clientThread, executor, httpClient, gson,
			key -> configManager.getConfiguration(CONFIG_GROUP, key, String.class),
			(key, value) ->
			{
				if (value == null) configManager.unsetConfiguration(CONFIG_GROUP, key);
				else configManager.setConfiguration(CONFIG_GROUP, key, value);
			});
	}

	ProfileAppearanceService(Client client, ClientThread clientThread,
		ScheduledExecutorService executor, okhttp3.OkHttpClient httpClient, Gson gson,
		Function<String, String> readConfig, BiConsumer<String, String> writeConfig)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.httpClient = httpClient;
		this.gson = gson;
		this.readConfig = readConfig;
		this.writeConfig = writeConfig;
	}

	CompletableFuture<PublishResult> publishCurrent(String expectedRsn, long accountHash,
		BooleanSupplier authorized)
	{
		if (!inFlight.compareAndSet(false, true)) return completed(Outcome.BUSY);
		PublishAttempt attempt = new PublishAttempt(expectedRsn, accountHash, authorized);
		CompletableFuture<PublishResult> result = new CompletableFuture<>();
		try
		{
			clientThread.invokeLater(() ->
			{
				try
				{
					captureAndPublish(attempt).whenComplete((value, error) ->
					{
						// Retain the slot across cancellation until the whole chain settles.
						inFlight.set(false);
						result.complete(error == null ? value : failed());
					});
				}
				catch (RuntimeException e)
				{
					inFlight.set(false);
					result.complete(failed());
				}
			});
		}
		catch (RuntimeException e)
		{
			inFlight.set(false);
			result.complete(failed());
		}
		return result;
	}

	private CompletableFuture<PublishResult> captureAndPublish(PublishAttempt attempt)
	{
		if (!attempt.active()) return completed(Outcome.CANCELLED);
		RetryHold hold = retryHold;
		if (hold != null && hold.account == attempt.accountHash && System.currentTimeMillis() < hold.until)
		{
			return CompletableFuture.completedFuture(hold.result);
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Your character is not ready. Wait until it is visible, then retry."));
		}
		PlayerComposition composition = local.getPlayerComposition();
		if (composition != null && composition.getTransformedNpcId() != -1)
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Return to your normal player form and retry."));
		}
		ProfileAppearanceManifest manifest = ProfileAppearanceManifest.capture(
			composition, client.getRevision(), SyncService.CLIENT_VERSION,
			visibleFollowerNpcId(client.getFollower()), local.getIdlePoseAnimation());
		if (manifest == null)
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Your character is not ready. Wait until it is visible, then retry."));
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null || worn.getItems() == null)
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Your equipment is not ready. Wait a moment, then retry."));
		}
		if (!manifest.matchesEquipment(worn.getItems()))
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Turn off cosmetic equipment overrides, then publish again."));
		}
		String secret = accountConfig(attempt.accountHash, DEVICE_SECRET_KEY);
		String recoveryToken = accountConfig(attempt.accountHash, RECOVERY_TOKEN_KEY);
		attempt.recoveryAt = accountConfig(attempt.accountHash, RECOVERY_AT_KEY);
		String manifestJson = gson.toJson(manifest);
		return secret != null
			? publishWithSecret(attempt, manifestJson, secret, true)
			: ensureCredential(attempt, manifestJson, recoveryToken);
	}

	/** Each HTTP dispatch rechecks consent and identity on the client thread. */
	private final class PublishAttempt
	{
		private final String rsn;
		private final long accountHash;
		private final BooleanSupplier authorized;
		private String recoveryAt;

		private PublishAttempt(String rsn, long accountHash, BooleanSupplier authorized)
		{
			this.rsn = rsn;
			this.accountHash = accountHash;
			this.authorized = authorized;
		}

		private boolean active()
		{
			return authorized.getAsBoolean() && isStillSelf(rsn, accountHash);
		}

		private CompletableFuture<HttpUtil.HttpResult> post(String suffix, String body, String secret)
		{
			CompletableFuture<HttpUtil.HttpResult> result = new CompletableFuture<>();
			try
			{
				clientThread.invokeLater(() ->
				{
					try
					{
						if (!active())
						{
							result.complete(new HttpUtil.HttpResult(-2, null));
							return;
						}
						HttpUtil.httpPostJson(httpClient, endpoint(rsn, suffix), body, secret)
							.whenComplete((response, error) ->
							{
								if (error != null) result.completeExceptionally(error);
								else result.complete(response);
							});
					}
					catch (RuntimeException e)
					{
						result.completeExceptionally(e);
					}
				});
			}
			catch (RuntimeException e)
			{
				result.completeExceptionally(e);
			}
			return result;
		}
	}

	static int visibleFollowerNpcId(@Nullable NPC follower)
	{
		if (follower == null)
		{
			return -1;
		}
		NPCComposition transformed = follower.getTransformedComposition();
		NPCComposition visible = transformed != null ? transformed : follower.getComposition();
		String[] actions = visible != null ? visible.getActions() : null;
		if (visible == null || !visible.isFollower() || actions == null
			|| !Arrays.asList(actions).contains("Pick-up"))
		{
			return -1;
		}
		return transformed != null ? transformed.getId() : follower.getId();
	}

	private CompletableFuture<PublishResult> ensureCredential(PublishAttempt attempt,
		String manifestJson, @Nullable String recoveryToken)
	{
		if (validSecret(recoveryToken))
		{
			return claimRecovery(attempt, manifestJson, recoveryToken);
		}
		return requestDevice(attempt, manifestJson);
	}

	private CompletableFuture<PublishResult> requestDevice(PublishAttempt attempt,
		String manifestJson)
	{
		JsonObject body = new JsonObject();
		body.addProperty("account_hash", Long.toString(attempt.accountHash));
		return attempt.post("appearance/device", gson.toJson(body), null).thenCompose(response ->
		{
			if (response.code == -2) return completed(Outcome.CANCELLED);
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.DISABLED,
					"Character publishing is temporarily unavailable. Try again later."));
			}
			if (response.code == 201)
			{
				String secret = stringValue(json, "device_secret");
				if (!validSecret(secret))
				{
					return completed(Outcome.FAILED);
				}
				return saveDeviceSecret(attempt.accountHash, secret)
					.thenCompose(saved -> saved
						? publishWithSecret(attempt, manifestJson, secret, false)
						: completed(Outcome.FAILED));
			}
			if (response.code == 202)
			{
				String token = stringValue(json, "recovery_token");
				String activatesAt = stringValue(json, "activates_at");
				if (!validSecret(token))
				{
					return completed(Outcome.FAILED);
				}
				return saveRecovery(attempt.accountHash, token, activatesAt)
					.thenApply(saved -> saved
						? recoveryPending(activatesAt) : failed());
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_pending"))
			{
				// This installation has no token for the already-pending request.
				return CompletableFuture.completedFuture(new PublishResult(Outcome.RECOVERY_PENDING,
					"Publishing recovery was started elsewhere. Finish it from that installation, or contact Kill Clog support."));
			}
			if (isProfileRequired(response.code, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.PROFILE_REQUIRED,
					"Sync your Collection Log to killclog.com, then retry character publishing."));
			}
			return failedResponse(attempt, "registration", response, json);
		});
	}

	private CompletableFuture<PublishResult> claimRecovery(PublishAttempt attempt,
		String manifestJson, String recoveryToken)
	{
		JsonObject body = new JsonObject();
		body.addProperty("account_hash", Long.toString(attempt.accountHash));
		body.addProperty("recovery_token", recoveryToken);
		return attempt.post("appearance/device/claim", gson.toJson(body), null).thenCompose(response ->
		{
			if (response.code == -2) return completed(Outcome.CANCELLED);
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.DISABLED,
					"Character publishing is temporarily unavailable. Try again later."));
			}
			if (response.code >= 200 && response.code < 300)
			{
				String secret = stringValue(json, "device_secret");
				if (!validSecret(secret))
				{
					return completed(Outcome.FAILED);
				}
				return saveDeviceSecret(attempt.accountHash, secret)
					.thenCompose(saved -> saved
						? publishWithSecret(attempt, manifestJson, secret, false)
						: completed(Outcome.FAILED));
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_wait"))
			{
				return CompletableFuture.completedFuture(recoveryPending(stringValue(json, "activates_at") != null
					? stringValue(json, "activates_at") : attempt.recoveryAt));
			}
			if (isProfileRequired(response.code, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.PROFILE_REQUIRED,
					"Sync your Collection Log to killclog.com, then retry character publishing."));
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_missing"))
			{
				return clearRecovery(attempt.accountHash)
					.thenCompose(cleared -> cleared
						? requestDevice(attempt, manifestJson)
						: completed(Outcome.FAILED));
			}
			return failedResponse(attempt, "recovery", response, json);
		});
	}

	private CompletableFuture<PublishResult> publishWithSecret(PublishAttempt attempt,
		String manifestJson, String secret, boolean recoverInvalidSecret)
	{
		return attempt.post("appearance/publish", manifestJson, secret).thenCompose(response ->
		{
			if (response.code == -2) return completed(Outcome.CANCELLED);
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.DISABLED,
					"Character publishing is temporarily unavailable. Try again later."));
			}
			if (isRenderedPublishResponse(response.code, json))
			{
				return completed(Outcome.PUBLISHED);
			}
			if (isAcceptedPendingPublishResponse(response.code, json))
			{
				return holdRetry(attempt, new PublishResult(Outcome.RENDERING,
					"Your character was accepted and is still rendering. Check your profile shortly."), 15);
			}
			if (response.code == 503 && hasError(json, "appearance_render_failed"))
			{
				return failedResponse(attempt, "publish", response, json);
			}
			if (response.code == 409 && hasError(json, "appearance_superseded"))
			{
				return failedResponse(attempt, "publish", response, json);
			}
			if (response.code == 401 && recoverInvalidSecret)
			{
				return clearDeviceSecret(attempt.accountHash)
					.thenCompose(cleared -> cleared
						? requestDevice(attempt, manifestJson)
						: completed(Outcome.FAILED));
			}
			if (response.code == 429)
			{
				return failedResponse(attempt, "publish", response, json);
			}
			if (shouldCancelPendingRecovery(response.code, json, recoverInvalidSecret))
			{
				return cancelRecoveryAndPublish(attempt, manifestJson, secret);
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_pending"))
			{
				return CompletableFuture.completedFuture(recoveryPending(stringValue(json, "activates_at") != null
					? stringValue(json, "activates_at") : attempt.recoveryAt));
			}
			if (isProfileRequired(response.code, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.PROFILE_REQUIRED,
					"Sync your Collection Log to killclog.com, then retry character publishing."));
			}
			return failedResponse(attempt, "publish", response, json);
		});
	}

	private CompletableFuture<PublishResult> failedResponse(PublishAttempt attempt, String operation,
		HttpUtil.HttpResult response, @Nullable JsonObject json)
	{
		ProfileAppearanceFailure failure = ProfileAppearanceFailure.fromResponse(response.code, json);
		log.warn("Character {} failed: status={} reason={} reference={}",
			operation, response.code, failure.reason, failure.reference);
		if (response.code < 0 && "publish".equals(operation))
		{
			return holdRetry(attempt, new PublishResult(Outcome.UNKNOWN,
				"The connection was interrupted. Your character may have updated. Check your profile before retrying."), 10);
		}
		int delay = response.retryAfterSeconds;
		if (delay == 0 && (response.code == 429 || response.code == 503)) delay = 60;
		PublishResult result = new PublishResult(Outcome.FAILED, failure.message
			+ (delay > 0 ? " Retry after " + displayTime(java.time.Instant.now().plusSeconds(delay).toString()) + "." : ""));
		return holdRetry(attempt, result, delay);
	}

	private CompletableFuture<PublishResult> holdRetry(PublishAttempt attempt, PublishResult result, int seconds)
	{
		retryHold = new RetryHold(attempt.accountHash, System.currentTimeMillis() + seconds * 1000L, result);
		return CompletableFuture.completedFuture(result);
	}

	private static PublishResult recoveryPending(@Nullable String activatesAt)
	{
		String time = displayTime(activatesAt);
		return new PublishResult(Outcome.RECOVERY_PENDING,
			"Publishing access is being restored for this installation. "
				+ (time == null ? "Try again after the recovery wait." : "Try again after " + time + ".")
				+ " This does not affect your game login or Collection Log sync.");
	}

	@Nullable
	private static String displayTime(@Nullable String value)
	{
		try
		{
			return java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm z", java.util.Locale.ENGLISH)
				.withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(value));
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private CompletableFuture<PublishResult> cancelRecoveryAndPublish(PublishAttempt attempt,
		String manifestJson, String secret)
	{
		return attempt.post("appearance/device/cancel", "{}", secret).thenCompose(response ->
		{
			if (response.code == -2) return completed(Outcome.CANCELLED);
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return CompletableFuture.completedFuture(new PublishResult(Outcome.DISABLED,
					"Character publishing is temporarily unavailable. Try again later."));
			}
			boolean cancelled = response.code >= 200 && response.code < 300
				&& json != null && json.has("recovery_cancelled")
				&& json.get("recovery_cancelled").getAsBoolean();
			boolean alreadyClear = response.code == 409
				&& hasError(json, "appearance_recovery_missing");
			return cancelled || alreadyClear
				? retryPublishAfterCooldown(attempt, manifestJson, secret)
				: failedResponse(attempt, "cancel_recovery", response, json);
		});
	}

	private CompletableFuture<PublishResult> retryPublishAfterCooldown(PublishAttempt attempt,
		String manifestJson, String secret)
	{
		CompletableFuture<Boolean> delay = new CompletableFuture<>();
		try
		{
			executor.schedule(() -> delay.complete(true), PUBLISH_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			delay.complete(false);
		}
		return delay.thenCompose(ready -> ready
			? publishWithSecret(attempt, manifestJson, secret, false)
			: completed(Outcome.CANCELLED));
	}

	private CompletableFuture<Boolean> saveDeviceSecret(long accountHash, String secret)
	{
		return updateAccountConfig(() ->
		{
			writeConfig.accept(scopedKey(accountHash, DEVICE_SECRET_KEY), secret);
			writeConfig.accept(scopedKey(accountHash, RECOVERY_TOKEN_KEY), null);
			writeConfig.accept(scopedKey(accountHash, RECOVERY_AT_KEY), null);
		});
	}

	private CompletableFuture<Boolean> saveRecovery(long accountHash, String token,
		@Nullable String activatesAt)
	{
		return updateAccountConfig(() ->
		{
			writeConfig.accept(scopedKey(accountHash, RECOVERY_TOKEN_KEY), token);
			if (activatesAt != null)
			{
				writeConfig.accept(scopedKey(accountHash, RECOVERY_AT_KEY), activatesAt);
			}
		});
	}

	private CompletableFuture<Boolean> clearDeviceSecret(long accountHash)
	{
		return updateAccountConfig(() -> writeConfig.accept(scopedKey(accountHash, DEVICE_SECRET_KEY), null));
	}

	private CompletableFuture<Boolean> clearRecovery(long accountHash)
	{
		return updateAccountConfig(() ->
		{
			writeConfig.accept(scopedKey(accountHash, RECOVERY_TOKEN_KEY), null);
			writeConfig.accept(scopedKey(accountHash, RECOVERY_AT_KEY), null);
		});
	}

	private CompletableFuture<Boolean> updateAccountConfig(Runnable update)
	{
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				update.run();
				result.complete(true);
			}
			catch (RuntimeException e)
			{
				log.warn("Character credential storage failed");
				result.complete(false);
			}
		});
		return result;
	}

	private boolean isStillSelf(String expectedRsn, long accountHash)
	{
		Player local = client.getLocalPlayer();
		return client.getGameState() == GameState.LOGGED_IN && local != null
			&& samePlayer(expectedRsn, local.getName())
			&& client.getAccountHash() == accountHash;
	}

	private static boolean samePlayer(@Nullable String expectedRsn, @Nullable String localRsn)
	{
		return expectedRsn != null && localRsn != null
			&& expectedRsn.trim().equalsIgnoreCase(localRsn.trim());
	}

	@Nullable
	private String accountConfig(long accountHash, String key)
	{
		String storageKey = scopedKey(accountHash, key);
		String value = readConfig.apply(storageKey);
		if ((value == null || value.isBlank())
			&& KillClogEndpoint.STAGING_API.equals(KillClogEndpoint.apiBaseUrl()))
		{
			// Early staging builds predated endpoint-scoped credentials. Move that
			// one developer credential away from the production namespace.
			String legacyKey = legacyScopedKey(accountHash, key);
			value = readConfig.apply(legacyKey);
			if (value != null && !value.isBlank())
			{
				writeConfig.accept(storageKey, value);
				writeConfig.accept(legacyKey, null);
			}
		}
		if (value != null && !value.isBlank() && !RECOVERY_AT_KEY.equals(key) && !validSecret(value))
		{
			writeConfig.accept(storageKey, null);
			value = null;
		}
		return value != null && !value.isBlank() ? value : null;
	}

	static String scopedKey(long accountHash, String key)
	{
		String environment = KillClogEndpoint.STAGING_API.equals(KillClogEndpoint.apiBaseUrl())
			? "staging" : "production";
		return Long.toUnsignedString(accountHash, 16) + "." + environment + "." + key;
	}

	private static String legacyScopedKey(long accountHash, String key)
	{
		return Long.toUnsignedString(accountHash, 16) + "." + key;
	}

	private JsonObject parse(@Nullable String body)
	{
		if (body == null || body.isBlank())
		{
			return null;
		}
		try
		{
			return gson.fromJson(body, JsonObject.class);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static boolean isDryRun(HttpUtil.HttpResult response, @Nullable JsonObject json)
	{
		return response.code >= 200 && response.code < 300
			&& json != null && json.has("dry_run") && json.get("dry_run").getAsBoolean();
	}

	private static boolean hasError(@Nullable JsonObject json, String error)
	{
		return error.equals(stringValue(json, "error"));
	}

	static boolean shouldCancelPendingRecovery(int responseCode,
		@Nullable JsonObject json, boolean incumbentCredential)
	{
		return incumbentCredential && responseCode == 409
			&& hasError(json, "appearance_recovery_pending");
	}

	static boolean isProfileRequired(int responseCode, @Nullable JsonObject json)
	{
		return responseCode == 409
			&& (hasError(json, "no_bound_account")
				|| hasError(json, "binding_not_verified"));
	}

	static boolean isRenderedPublishResponse(int responseCode, @Nullable JsonObject json)
	{
		try
		{
			return responseCode == 200
				&& json != null && json.has("published")
				&& json.get("published").getAsBoolean()
				&& "ready".equals(stringValue(json, "render_status"));
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	static boolean isAcceptedPendingPublishResponse(int responseCode,
		@Nullable JsonObject json)
	{
		try
		{
			return responseCode == 202
				&& json != null && json.has("accepted")
				&& json.get("accepted").getAsBoolean()
				&& "pending_renderer".equals(stringValue(json, "render_status"));
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	@Nullable
	private static String stringValue(@Nullable JsonObject json, String key)
	{
		try
		{
			return json != null && json.has(key) && !json.get(key).isJsonNull()
				? json.get(key).getAsString() : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	static boolean validSecret(@Nullable String value)
	{
		return value != null && value.matches("[a-f0-9]{64}");
	}

	private static String endpoint(String rsn, String suffix)
	{
		return KillClogEndpoint.apiBaseUrl() + "/player/"
			+ URLEncoder.encode(rsn, StandardCharsets.UTF_8).replace("+", "%20")
			+ "/" + suffix;
	}

	private static CompletableFuture<PublishResult> completed(Outcome outcome)
	{
		return CompletableFuture.completedFuture(new PublishResult(outcome));
	}

	private static PublishResult failed()
	{
		return new PublishResult(Outcome.FAILED, "Could not update your character. Please retry.");
	}
}
