package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/** Captures self-only game state for the profile card on the client thread. */
@Slf4j
final class ProfileCardLocalCapture
{
	private static final Pattern FRACTION = Pattern.compile("([0-9][0-9,]*)\\s*/\\s*([0-9][0-9,]*)");

	private final Client client;
	private final ClientThread clientThread;
	private String cachedAchievementRsn;
	private int cachedAchievementsCompleted = -1;
	private int cachedTotalAchievements = -1;

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
		captureSummaryProgress();
		int[] achievements = ownedSummaryProgress(cachedAchievementRsn,
			cachedAchievementsCompleted, cachedTotalAchievements, player.getName());
		return new Snapshot(client.getVarpValue(VarPlayerID.QP),
			achievements[0], achievements[1], playerModel);
	}

	/** Cache native Account Summary progress whenever that interface naturally opens. */
	void captureSummaryProgress()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null || player.getName().isBlank())
		{
			return;
		}
		int[] achievements = summaryProgress(readSummaryText(), "achievements");
		if (achievements[0] >= 0 && achievements[1] >= 0)
		{
			cachedAchievementRsn = player.getName().trim();
			cachedAchievementsCompleted = achievements[0];
			cachedTotalAchievements = achievements[1];
		}
	}

	void clearSummaryProgress()
	{
		cachedAchievementRsn = null;
		cachedAchievementsCompleted = -1;
		cachedTotalAchievements = -1;
	}

	static int[] ownedSummaryProgress(@Nullable String owner, int completed, int total,
		@Nullable String expectedRsn)
	{
		return ProfileCardDataBuilder.isSelfPlayer(owner, expectedRsn)
			? new int[]{completed, total} : new int[]{-1, -1};
	}

	private List<String> readSummaryText()
	{
		Widget root = client.getWidget(InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL,
			InterfaceID.AccountSummarySidepanel.SUMMARY_CONTENTS);
		if (root == null || root.isHidden())
		{
			return Collections.emptyList();
		}
		List<String> text = new ArrayList<>();
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		collectText(root, visited, text);
		return text;
	}

	private static void collectText(Widget widget, Set<Widget> visited, List<String> text)
	{
		if (widget == null || !visited.add(widget))
		{
			return;
		}
		String value = widget.getText();
		if (value != null && !value.isBlank())
		{
			text.add(value);
		}
		collectChildren(widget.getChildren(), visited, text);
		collectChildren(widget.getDynamicChildren(), visited, text);
		collectChildren(widget.getStaticChildren(), visited, text);
		collectChildren(widget.getNestedChildren(), visited, text);
	}

	private static void collectChildren(Widget[] children, Set<Widget> visited, List<String> text)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				collectText(child, visited, text);
			}
		}
	}

	static int[] summaryProgress(List<String> text, String label)
	{
		for (int i = 0; i < text.size(); i++)
		{
			String normalized = normalize(text.get(i));
			if (!normalized.toLowerCase(java.util.Locale.ROOT).contains(label))
			{
				continue;
			}
			for (int j = i; j < Math.min(text.size(), i + 5); j++)
			{
				Matcher matcher = FRACTION.matcher(normalize(text.get(j)));
				if (matcher.find())
				{
					return new int[]{parseNumber(matcher.group(1)), parseNumber(matcher.group(2))};
				}
			}
		}
		return new int[]{-1, -1};
	}

	private static String normalize(String value)
	{
		return value.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
			.replaceAll("\\s+", " ").trim();
	}

	private static int parseNumber(String value)
	{
		try
		{
			return Integer.parseInt(value.replace(",", ""));
		}
		catch (NumberFormatException ignored)
		{
			return -1;
		}
	}

	static final class Snapshot
	{
		final int questPoints;
		final int achievementsCompleted;
		final int totalAchievements;
		final ProfileCardPlayerModel.Snapshot playerModel;

		private Snapshot(int questPoints, int achievementsCompleted,
			int totalAchievements, ProfileCardPlayerModel.Snapshot playerModel)
		{
			this.questPoints = questPoints;
			this.achievementsCompleted = achievementsCompleted;
			this.totalAchievements = totalAchievements;
			this.playerModel = playerModel;
		}
	}
}
