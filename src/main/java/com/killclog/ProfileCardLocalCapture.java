package com.killclog;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;

/** Captures self-only game state for the profile card on the client thread. */
@Slf4j
final class ProfileCardLocalCapture
{
	private final Client client;
	private final ClientThread clientThread;

	ProfileCardLocalCapture(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	void capture(String expectedRsn, Consumer<Snapshot> callback)
	{
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
			Snapshot result = snapshot;
			SwingUtilities.invokeLater(() -> callback.accept(result));
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

		Model model = player.getModel();
		if (model == null)
		{
			return null;
		}
		Model unskewed = model.getUnskewedModel();
		ProfileCardPlayerModel.Snapshot playerModel =
			ProfileCardPlayerModel.snapshot(unskewed != null ? unskewed : model,
				client.getTextureProvider(), client.getGameCycle());
		if (playerModel == null)
		{
			return null;
		}
		return new Snapshot(client.getVarpValue(VarPlayerID.QP), playerModel);
	}

	static final class Snapshot
	{
		final int questPoints;
		final ProfileCardPlayerModel.Snapshot playerModel;

		private Snapshot(int questPoints, ProfileCardPlayerModel.Snapshot playerModel)
		{
			this.questPoints = questPoints;
			this.playerModel = playerModel;
		}
	}
}
