package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
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
		PENDING,
		FAILED
	}

	static final class PublishResult
	{
		final Outcome outcome;

		private PublishResult(Outcome outcome)
		{
			this.outcome = outcome;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final ConfigManager configManager;
	private final okhttp3.OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	ProfileAppearanceService(Client client, ClientThread clientThread,
		ScheduledExecutorService executor,
		ConfigManager configManager, okhttp3.OkHttpClient httpClient, Gson gson)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.configManager = configManager;
		this.httpClient = httpClient;
		this.gson = gson;
	}

	CompletableFuture<PublishResult> publishCurrent(String expectedRsn, long accountHash)
	{
		CompletableFuture<PublishResult> result = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				Player local = client.getLocalPlayer();
				PlayerComposition composition = local != null ? local.getPlayerComposition() : null;
				NPC follower = client.getFollower();
				int followerNpcId = visibleFollowerNpcId(follower);
				if (!isStillSelf(expectedRsn, accountHash) || composition == null)
				{
					result.complete(failed());
					return;
				}
				ProfileAppearanceManifest manifest = ProfileAppearanceManifest.capture(
					composition, client.getRevision(), SyncService.CLIENT_VERSION, followerNpcId,
					local.getIdlePoseAnimation());
				if (manifest == null)
				{
					result.complete(failed());
					return;
				}

				String secret = accountConfig(accountHash, DEVICE_SECRET_KEY);
				String recoveryToken = accountConfig(accountHash, RECOVERY_TOKEN_KEY);
				String manifestJson = gson.toJson(manifest);
				CompletableFuture<PublishResult> request = secret != null
					? publishWithSecret(expectedRsn, accountHash, manifestJson, secret, true)
					: ensureCredential(expectedRsn, accountHash, manifestJson, recoveryToken);
				request.whenComplete((value, error) ->
				{
					if (error != null)
					{
						log.debug("profile appearance publish failed", error);
						result.complete(failed());
					}
					else
					{
						result.complete(value);
					}
				});
			}
			catch (RuntimeException e)
			{
				log.debug("profile appearance capture failed", e);
				result.complete(failed());
			}
		});
		return result;
	}

	static int visibleFollowerNpcId(@Nullable NPC follower)
	{
		if (follower == null)
		{
			return -1;
		}
		NPCComposition composition = follower.getComposition();
		if (!isPortableFollower(composition))
		{
			return -1;
		}
		NPCComposition visibleComposition = follower.getTransformedComposition();
		return visibleComposition != null ? visibleComposition.getId() : follower.getId();
	}

	private static boolean isPortableFollower(@Nullable NPCComposition composition)
	{
		if (composition == null || !composition.isFollower())
		{
			return false;
		}
		String[] actions = composition.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action != null && "Pick-up".equalsIgnoreCase(action.trim()))
			{
				return true;
			}
		}
		return false;
	}

	private CompletableFuture<PublishResult> ensureCredential(String rsn, long accountHash,
		String manifestJson, @Nullable String recoveryToken)
	{
		if (validSecret(recoveryToken))
		{
			return claimRecovery(rsn, accountHash, manifestJson, recoveryToken);
		}
		return requestDevice(rsn, accountHash, manifestJson);
	}

	private CompletableFuture<PublishResult> requestDevice(String rsn, long accountHash,
		String manifestJson)
	{
		JsonObject body = new JsonObject();
		body.addProperty("account_hash", Long.toString(accountHash));
		return HttpUtil.httpPostJson(httpClient, endpoint(rsn, "appearance/device"),
			gson.toJson(body)).thenCompose(response ->
		{
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return completed(Outcome.DISABLED);
			}
			if (response.code == 201)
			{
				String secret = stringValue(json, "device_secret");
				if (!validSecret(secret))
				{
					return completed(Outcome.FAILED);
				}
				return saveDeviceSecret(accountHash, secret)
					.thenCompose(saved -> saved
						? publishWithSecret(rsn, accountHash, manifestJson, secret, false)
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
				return saveRecovery(accountHash, token, activatesAt)
					.thenApply(saved -> saved
						? new PublishResult(Outcome.PENDING) : failed());
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_pending"))
			{
				return completed(Outcome.PENDING);
			}
			if (isProfileRequired(response.code, json))
			{
				return completed(Outcome.PROFILE_REQUIRED);
			}
			return completed(Outcome.FAILED);
		});
	}

	private CompletableFuture<PublishResult> claimRecovery(String rsn, long accountHash,
		String manifestJson, String recoveryToken)
	{
		JsonObject body = new JsonObject();
		body.addProperty("account_hash", Long.toString(accountHash));
		body.addProperty("recovery_token", recoveryToken);
		return HttpUtil.httpPostJson(httpClient, endpoint(rsn, "appearance/device/claim"),
			gson.toJson(body)).thenCompose(response ->
		{
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return completed(Outcome.DISABLED);
			}
			if (response.code >= 200 && response.code < 300)
			{
				String secret = stringValue(json, "device_secret");
				if (!validSecret(secret))
				{
					return completed(Outcome.FAILED);
				}
				return saveDeviceSecret(accountHash, secret)
					.thenCompose(saved -> saved
						? publishWithSecret(rsn, accountHash, manifestJson, secret, false)
						: completed(Outcome.FAILED));
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_wait"))
			{
				return completed(Outcome.PENDING);
			}
			if (isProfileRequired(response.code, json))
			{
				return completed(Outcome.PROFILE_REQUIRED);
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_missing"))
			{
				return clearRecovery(accountHash)
					.thenCompose(cleared -> cleared
						? requestDevice(rsn, accountHash, manifestJson)
						: completed(Outcome.FAILED));
			}
			return completed(Outcome.FAILED);
		});
	}

	private CompletableFuture<PublishResult> publishWithSecret(String rsn, long accountHash,
		String manifestJson, String secret, boolean recoverInvalidSecret)
	{
		return HttpUtil.httpPostJson(httpClient, endpoint(rsn, "appearance/publish"),
			manifestJson, secret).thenCompose(response ->
		{
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return completed(Outcome.DISABLED);
			}
			if (isRenderedPublishResponse(response.code, json))
			{
				return completed(Outcome.PUBLISHED);
			}
			if (isAcceptedPendingPublishResponse(response.code, json))
			{
				return completed(Outcome.PENDING);
			}
			if (response.code == 503 && hasError(json, "appearance_render_failed"))
			{
				return completed(Outcome.FAILED);
			}
			if (response.code == 409 && hasError(json, "appearance_superseded"))
			{
				return completed(Outcome.FAILED);
			}
			if (response.code == 401 && recoverInvalidSecret)
			{
				return clearDeviceSecret(accountHash)
					.thenCompose(cleared -> cleared
						? requestDevice(rsn, accountHash, manifestJson)
						: completed(Outcome.FAILED));
			}
			if (response.code == 429)
			{
				return completed(Outcome.FAILED);
			}
			if (shouldCancelPendingRecovery(response.code, json, recoverInvalidSecret))
			{
				return cancelRecoveryAndPublish(rsn, accountHash, manifestJson, secret);
			}
			if (response.code == 409 && hasError(json, "appearance_recovery_pending"))
			{
				return completed(Outcome.PENDING);
			}
			if (isProfileRequired(response.code, json))
			{
				return completed(Outcome.PROFILE_REQUIRED);
			}
			return completed(Outcome.FAILED);
		});
	}

	private CompletableFuture<PublishResult> cancelRecoveryAndPublish(String rsn,
		long accountHash, String manifestJson, String secret)
	{
		return HttpUtil.httpPostJson(httpClient, endpoint(rsn, "appearance/device/cancel"),
			"{}", secret).thenCompose(response ->
		{
			JsonObject json = parse(response.body);
			if (isDryRun(response, json))
			{
				return completed(Outcome.DISABLED);
			}
			boolean cancelled = response.code >= 200 && response.code < 300
				&& json != null && json.has("recovery_cancelled")
				&& json.get("recovery_cancelled").getAsBoolean();
			boolean alreadyClear = response.code == 409
				&& hasError(json, "appearance_recovery_missing");
			return cancelled || alreadyClear
				? retryPublishAfterCooldown(rsn, accountHash, manifestJson, secret)
				: completed(Outcome.FAILED);
		});
	}

	private CompletableFuture<PublishResult> retryPublishAfterCooldown(String rsn,
		long accountHash, String manifestJson, String secret)
	{
		CompletableFuture<Boolean> stillSelf = new CompletableFuture<>();
		try
		{
			executor.schedule(() -> clientThread.invokeLater(() ->
			{
				try
				{
					stillSelf.complete(isStillSelf(rsn, accountHash));
				}
				catch (RuntimeException e)
				{
					stillSelf.complete(false);
				}
			}), PUBLISH_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RuntimeException e)
		{
			stillSelf.complete(false);
		}
		return stillSelf.thenCompose(current -> current
			? publishWithSecret(rsn, accountHash, manifestJson, secret, false)
			: completed(Outcome.FAILED));
	}

	private CompletableFuture<Boolean> saveDeviceSecret(long accountHash, String secret)
	{
		return updateAccountConfig(() ->
		{
			configManager.setConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, DEVICE_SECRET_KEY), secret);
			configManager.unsetConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, RECOVERY_TOKEN_KEY));
			configManager.unsetConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, RECOVERY_AT_KEY));
		});
	}

	private CompletableFuture<Boolean> saveRecovery(long accountHash, String token,
		@Nullable String activatesAt)
	{
		return updateAccountConfig(() ->
		{
			configManager.setConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, RECOVERY_TOKEN_KEY), token);
			if (activatesAt != null)
			{
				configManager.setConfiguration(CONFIG_GROUP,
					scopedKey(accountHash, RECOVERY_AT_KEY), activatesAt);
			}
		});
	}

	private CompletableFuture<Boolean> clearDeviceSecret(long accountHash)
	{
		return updateAccountConfig(() -> configManager.unsetConfiguration(CONFIG_GROUP,
			scopedKey(accountHash, DEVICE_SECRET_KEY)));
	}

	private CompletableFuture<Boolean> clearRecovery(long accountHash)
	{
		return updateAccountConfig(() ->
		{
			configManager.unsetConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, RECOVERY_TOKEN_KEY));
			configManager.unsetConfiguration(CONFIG_GROUP,
				scopedKey(accountHash, RECOVERY_AT_KEY));
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
				log.debug("profile appearance credential storage failed", e);
				result.complete(false);
			}
		});
		return result;
	}

	private boolean isStillSelf(String expectedRsn, long accountHash)
	{
		Player local = client.getLocalPlayer();
		return local != null
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
		String value = configManager.getConfiguration(CONFIG_GROUP, storageKey, String.class);
		if ((value == null || value.isBlank())
			&& KillClogEndpoint.STAGING_API.equals(KillClogEndpoint.apiBaseUrl()))
		{
			// Early staging builds predated endpoint-scoped credentials. Move that
			// one developer credential away from the production namespace.
			String legacyKey = legacyScopedKey(accountHash, key);
			value = configManager.getConfiguration(CONFIG_GROUP, legacyKey, String.class);
			if (value != null && !value.isBlank())
			{
				configManager.setConfiguration(CONFIG_GROUP, storageKey, value);
				configManager.unsetConfiguration(CONFIG_GROUP, legacyKey);
			}
		}
		if (value != null && !value.isBlank() && !validSecret(value))
		{
			configManager.unsetConfiguration(CONFIG_GROUP, storageKey);
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
		return new PublishResult(Outcome.FAILED);
	}
}
