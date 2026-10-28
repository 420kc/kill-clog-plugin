package com.killclog;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaRewardSpritesTest
{
	@Test
	public void aRewardsIconLoadsOnceThenScalesToTheRowsHeight() throws Exception
	{
		Queue<Runnable> clientThreadQueue = new ArrayDeque<>();
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(invocation -> clientThreadQueue.add(invocation.getArgument(0)))
			.when(clientThread).invokeLater(any(Runnable.class));
		ItemManager items = mock(ItemManager.class);
		AsyncBufferedImage image = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		when(items.getImage(anyInt(), anyInt(), anyBoolean())).thenReturn(image);
		AtomicInteger loaded = new AtomicInteger();
		CaRewardSprites sprites = new CaRewardSprites(items, clientThread, loaded::incrementAndGet);
		CombatAchievementReward reward = CombatAchievementReward.values()[0];

		assertNull("not loaded yet: the row draws without it and asks", sprites.sprite(reward, 16));
		sprites.request(reward);
		assertEquals("one load however often it is asked", 1, clientThreadQueue.size());
		clientThreadQueue.poll().run();
		image.loaded();
		SwingUtilities.invokeAndWait(() ->
		{
		});
		assertEquals(1, loaded.get());
		BufferedImage icon = sprites.sprite(reward, 16);
		assertEquals(16, icon.getHeight());
		assertEquals("its own shape kept", 18, icon.getWidth());
		sprites.request(reward);
		assertEquals(0, clientThreadQueue.size());
		assertNull(sprites.sprite(null, 16));
	}
}
