package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;

/** Publishes the current player's bounded appearance recipe, never image data. */
@Slf4j
@Singleton
final class ProfileAppearanceService
{
	static final long PUBLISH_RETRY_DELAY_MS = 2250L;
	private static final PublishResult PROFILE_REQUIRED = new PublishResult(Outcome.PROFILE_REQUIRED,
		"Sync your Collection Log, then publish your character again.");

	enum Outcome
	{
		PUBLISHED,
		DELETED,
		NO_CHARACTER,
		PROFILE_REQUIRED,
		DISABLED,
		RENDERING,
		BUSY,
		CANCELLED,
		UNKNOWN,
		APPEARANCE_PENDING,
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

		PublishResult(Outcome outcome, @Nullable String message)
		{
			this.outcome = outcome;
			this.message = message;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private volatile RetryHold retryHold;
	private volatile OriginalAppearance originalAppearance;

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class OriginalAppearance
	{
		final Player player;
		final long account;
		final ProfileAppearanceManifest manifest;

	}

	/** Called before cosmetic plugins mutate the game's fresh appearance. */
	void captureOriginalAppearance(Player player)
	{
		if (player == null || player != client.getLocalPlayer()) return;
		long account = client.getAccountHash();
		ProfileAppearanceManifest manifest = ProfileAppearanceManifest.capture(
			player.getPlayerComposition(), client.getRevision(), SyncService.CLIENT_VERSION,
			-1, player.getIdlePoseAnimation());
		originalAppearance = account == 0 || manifest == null ? null
			: new OriginalAppearance(player, account, manifest);
	}

	void clearOriginalAppearance()
	{
		originalAppearance = null;
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class RetryHold
	{
		final long account;
		final long until;
		final PublishResult result;

	}
	private final okhttp3.OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	ProfileAppearanceService(Client client, ClientThread clientThread, okhttp3.OkHttpClient httpClient, Gson gson)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Publishes the logged-in player's character, for the login it was asked in: {@code authorized} says whether that
	 * login is still current and the request still wanted, and each step asks it again before it acts.
	 */
	CompletableFuture<PublishResult> publishCurrent(Session session, BooleanSupplier authorized)
	{
		return run(new PublishAttempt(session.getName(), session.getAccountHash(), authorized), this::captureAndPublish);
	}

	/**
	 * Removes the player's character from killclog.com, so their profile goes back to having none; nothing else of
	 * theirs changes.
	 */
	CompletableFuture<PublishResult> deleteCurrent(Session session, BooleanSupplier authorized)
	{
		return run(new PublishAttempt(session.getName(), session.getAccountHash(), authorized), attempt -> !attempt.active()
			? completed(Outcome.CANCELLED)
			: attempt.post("appearance/delete", "{}", (response, json) ->
			{
				if (response.code == 200 && json != null && json.has("deleted") && json.get("deleted").getAsBoolean())
				{
					return completed(Outcome.DELETED);
				}
				// No synced profile here, so no character on killclog.com: nothing to remove.
				return isProfileRequired(response.code, json) ? completed(Outcome.NO_CHARACTER)
					: failedResponse(attempt, "delete", response, json);
			}));
	}

	/** One character request at a time: it starts on the client thread and frees the slot when its chain settles. */
	private CompletableFuture<PublishResult> run(PublishAttempt attempt,
		Function<PublishAttempt, CompletableFuture<PublishResult>> request)
	{
		if (!inFlight.compareAndSet(false, true)) return completed(Outcome.BUSY);
		CompletableFuture<PublishResult> result = new CompletableFuture<>();
		try
		{
			clientThread.invokeLater(() ->
			{
				try
				{
					request.apply(attempt).whenComplete((value, error) ->
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
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null || worn.getItems() == null)
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.FAILED,
				"Your equipment is not ready. Wait a moment, then retry."));
		}
		OriginalAppearance original = originalAppearance;
		if (original == null || original.player != local || original.account != attempt.accountHash
			|| !original.manifest.matchesEquipment(worn.getItems()))
		{
			return CompletableFuture.completedFuture(new PublishResult(Outcome.APPEARANCE_PENDING,
				"Equip or unequip an item, then publish again so Kill Clog can read your real appearance."));
		}
		ProfileAppearanceManifest manifest = original.manifest.withFollower(visibleFollowerNpcId(client.getFollower()));
		return attempt.post("appearance/publish/main", gson.toJson(manifest), (response, json) ->
		{
			if (isRenderedPublishResponse(response.code, json))
			{
				return completed(Outcome.PUBLISHED);
			}
			if (isAcceptedPendingPublishResponse(response.code, json))
			{
				return holdRetry(attempt, new PublishResult(Outcome.RENDERING,
					"Your character was accepted and is still rendering. Check your profile shortly."), 15);
			}
			return isProfileRequired(response.code, json) ? CompletableFuture.completedFuture(PROFILE_REQUIRED)
				: failedResponse(attempt, "publish", response, json);
		});
	}

	/**
	 * Each HTTP dispatch asks again, on the client thread, whether its login is still current and the request still
	 * wanted. A request proves its account by the account hash its name is bound to, as a sync does.
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private final class PublishAttempt
	{
		private final String rsn;
		private final long accountHash;
		private final BooleanSupplier authorized;

		private boolean active()
		{
			return authorized.getAsBoolean();
		}

		private CompletableFuture<PublishResult> post(String suffix, String body,
			BiFunction<HttpUtil.HttpResult, JsonObject, CompletableFuture<PublishResult>> handle)
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
						HttpUtil.httpPostJson(httpClient, endpoint(rsn, suffix), body, Long.toString(accountHash))
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
			return result.thenCompose(response ->
			{
				JsonObject json = parse(response.body);
				return response.code == -2 ? completed(Outcome.CANCELLED)
					: isDryRun(response, json) ? CompletableFuture.completedFuture(new PublishResult(Outcome.DISABLED,
						"Character publishing is temporarily unavailable. Try again later."))
					: handle.apply(response, json);
			});
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

	private static String endpoint(String rsn, String suffix)
	{
		return KillClogEndpoint.apiBaseUrl() + "/player/"
			+ HttpUtil.pathSegment(rsn)
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
