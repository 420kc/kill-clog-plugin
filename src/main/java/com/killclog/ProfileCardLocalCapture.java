package com.killclog;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;

/** Captures self-only game state for the profile card on the client thread. */
@Slf4j
final class ProfileCardLocalCapture
{
	private static final int STANDING_POSE_WAIT_CYCLES = 250;

	private final Client client;
	private final ClientThread clientThread;
	private String cachedStandingRsn;
	private int cachedStandingAppearanceHash;
	private int cachedStandingIdlePose = -1;
	private ProfileCardPlayerModel.Snapshot cachedStandingModel;

	ProfileCardLocalCapture(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	void capture(String expectedRsn, Consumer<Snapshot> callback)
	{
		AtomicInteger attempts = new AtomicInteger();
		clientThread.invokeLater(() ->
		{
			Snapshot snapshot = null;
			try
			{
				snapshot = captureNow(expectedRsn);
			}
			catch (RuntimeException e)
			{
				log.warn("profile card local snapshot failed", e);
			}
			if (snapshot == null && attempts.incrementAndGet() < STANDING_POSE_WAIT_CYCLES)
			{
				return false;
			}
			Snapshot result = snapshot;
			SwingUtilities.invokeLater(() -> callback.accept(result));
			return true;
		});
	}

	@Nullable
	private Snapshot captureNow(String expectedRsn)
	{
		Player player = client.getLocalPlayer();
		if (player == null
			|| !ProfileCardDataBuilder.isSelfPlayer(expectedRsn, player.getName()))
		{
			return null;
		}

		rememberStandingPose();
		ProfileCardPlayerModel.Snapshot playerModel = standingModel(player);
		if (playerModel == null)
		{
			return null;
		}
		return new Snapshot(client.getVarpValue(VarPlayerID.QP), playerModel,
			followerModel());
	}

	@Nullable
	private ProfileCardPlayerModel.Snapshot followerModel()
	{
		NPC follower = client.getFollower();
		if (follower == null)
		{
			return null;
		}
		Model model = follower.getModel();
		if (model == null)
		{
			log.debug("profile card follower model unavailable for npc {}", follower.getId());
			return null;
		}
		Model unskewed = model.getUnskewedModel();
		ProfileCardPlayerModel.Snapshot snapshot = ProfileCardPlayerModel.snapshot(
			unskewed != null ? unskewed : model,
			client.getTextureProvider(), client.getGameCycle());
		if (snapshot != null)
		{
			log.debug("profile card follower captured npc {}", follower.getId());
		}
		return snapshot;
	}

	/**
	 * Keep a frame-zero idle portrait ready so opening the card while walking or
	 * skilling can never freeze that transient animation into the artifact.
	 * FashionScape updates {@link Player#getIdlePoseAnimation()} for virtual
	 * weapon stances, so the cached frame follows the appearance the player sees.
	 */
	void rememberStandingPose()
	{
		Player player = client.getLocalPlayer();
		if (!isStandardStandingPose(player) || player.getName() == null)
		{
			return;
		}
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return;
		}
		int appearanceHash = appearanceHash(composition);
		if (ProfileCardDataBuilder.isSelfPlayer(cachedStandingRsn, player.getName())
			&& cachedStandingAppearanceHash == appearanceHash
			&& cachedStandingIdlePose == player.getIdlePoseAnimation()
			&& cachedStandingModel != null)
		{
			return;
		}
		Model model = player.getModel();
		if (model == null)
		{
			return;
		}
		Model unskewed = model.getUnskewedModel();
		ProfileCardPlayerModel.Snapshot snapshot = ProfileCardPlayerModel.snapshot(
			unskewed != null ? unskewed : model, client.getTextureProvider(), client.getGameCycle());
		if (snapshot != null)
		{
			cachedStandingRsn = player.getName().trim();
			cachedStandingAppearanceHash = appearanceHash;
			cachedStandingIdlePose = player.getIdlePoseAnimation();
			cachedStandingModel = snapshot;
		}
	}

	@Nullable
	private ProfileCardPlayerModel.Snapshot standingModel(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null
			|| !ProfileCardDataBuilder.isSelfPlayer(cachedStandingRsn, player.getName())
			|| cachedStandingAppearanceHash != appearanceHash(composition)
			|| cachedStandingIdlePose != player.getIdlePoseAnimation())
		{
			return null;
		}
		return cachedStandingModel;
	}

	static boolean isStandardStandingPose(@Nullable Player player)
	{
		return player != null
			&& player.getAnimation() == -1
			&& player.getPoseAnimation() == player.getIdlePoseAnimation()
			&& player.getPoseAnimationFrame() == 0;
	}

	private static int appearanceHash(PlayerComposition composition)
	{
		int hash = Arrays.hashCode(composition.getEquipmentIds());
		hash = 31 * hash + Arrays.hashCode(composition.getColors());
		hash = 31 * hash + composition.getGender();
		hash = 31 * hash + composition.getTransformedNpcId();
		ColorTextureOverride[] overrides = composition.getColorTextureOverrides();
		if (overrides != null)
		{
			for (ColorTextureOverride override : overrides)
			{
				hash = 31 * hash + (override == null ? 0
					: 31 * Arrays.hashCode(override.getColorToReplaceWith())
						+ Arrays.hashCode(override.getTextureToReplaceWith()));
			}
		}
		return hash;
	}

	void clear()
	{
		cachedStandingRsn = null;
		cachedStandingAppearanceHash = 0;
		cachedStandingIdlePose = -1;
		cachedStandingModel = null;
	}

	static final class Snapshot
	{
		final int questPoints;
		final ProfileCardPlayerModel.Snapshot playerModel;
		@Nullable
		final ProfileCardPlayerModel.Snapshot followerModel;

		private Snapshot(int questPoints, ProfileCardPlayerModel.Snapshot playerModel,
			@Nullable ProfileCardPlayerModel.Snapshot followerModel)
		{
			this.questPoints = questPoints;
			this.playerModel = playerModel;
			this.followerModel = followerModel;
		}
	}
}
