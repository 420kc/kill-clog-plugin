package com.killclog;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import javax.swing.SwingUtilities;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class CaRewardSprites
{
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Runnable onLoaded;
	private final Map<CombatAchievementReward, BufferedImage> cache =
		new EnumMap<>(CombatAchievementReward.class);
	private final Set<CombatAchievementReward> loading =
		EnumSet.noneOf(CombatAchievementReward.class);

	void preloadAll()
	{
		for (CombatAchievementReward reward : CombatAchievementReward.values())
		{
			request(reward);
		}
	}

	void request(CombatAchievementReward reward)
	{
		if (reward == null || cache.containsKey(reward) || !loading.add(reward))
		{
			return;
		}
		clientThread.invokeLater(() -> load(reward));
	}

	BufferedImage sprite(CombatAchievementReward reward, int height)
	{
		if (reward == null)
		{
			return null;
		}
		BufferedImage img = cache.get(reward);
		if (img == null)
		{
			request(reward);
			return null;
		}
		if (img.getHeight() <= 0)
		{
			return null;
		}
		int width = (int) Math.round((double) img.getWidth() / img.getHeight() * height);
		return ImageUtil.resizeImage(img, width, height);
	}

	private void load(CombatAchievementReward reward)
	{
		BufferedImage img = itemManager.getImage(reward.itemId(), 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() -> store(reward, img)));
		}
		else
		{
			SwingUtilities.invokeLater(() -> store(reward, img));
		}
	}

	private void store(CombatAchievementReward reward, BufferedImage img)
	{
		if (img != null)
		{
			cache.put(reward, img);
		}
		loading.remove(reward);
		onLoaded.run();
	}
}
