package com.killclog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TooltipItemNameResolverTest
{
	@Test
	public void namesComeFromTheGameInBatchesOnTheClientThreadThenTheCardsRepaintOnce() throws Exception
	{
		Queue<Runnable> clientThreadQueue = new ArrayDeque<>();
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(invocation -> clientThreadQueue.add(invocation.getArgument(0)))
			.when(clientThread).invokeLater(any(Runnable.class));
		ItemManager items = mock(ItemManager.class);
		when(items.getItemComposition(anyInt())).thenAnswer(invocation ->
		{
			int id = invocation.getArgument(0);
			ItemComposition item = mock(ItemComposition.class);
			when(item.getName()).thenReturn(id == 7 ? "null" : "Item named " + id);
			return item;
		});
		List<Integer> page = new ArrayList<>();
		for (int id = 1; id <= 60; id++)
		{
			page.add(id);
		}
		Map<Integer, String> names = new HashMap<>();
		names.put(1, "Already named");
		ClogResult result = new ClogResult("Tester", Map.of(), Map.of("page", page), names, null, null);
		AtomicInteger repaints = new AtomicInteger();
		TooltipItemNameResolver resolver = new TooltipItemNameResolver(clientThread, items, repaints::incrementAndGet);

		resolver.resolve(result);
		resolver.resolve(result);
		assertEquals("one lane per result, however often it is asked", 1, clientThreadQueue.size());
		clientThreadQueue.poll().run();
		assertEquals("48 names a tick, then the rest", 1, clientThreadQueue.size());
		clientThreadQueue.poll().run();
		assertTrue(clientThreadQueue.isEmpty());
		SwingUtilities.invokeAndWait(() ->
		{
		});
		assertEquals(1, repaints.get());
		assertEquals("Already named", result.getItemName(1));
		assertEquals("Item named 60", result.getItemName(60));
		assertFalse("the game's placeholder name is no name", result.isItemResolved(7));

		resolver.resolve(result);
		assertEquals("only the unnamed one is asked again", 1, clientThreadQueue.size());
	}
}
