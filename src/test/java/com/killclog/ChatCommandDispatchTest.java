package com.killclog;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real handlers and message rewrite without game input or network I/O. */
public class ChatCommandDispatchTest
{
	@Test
	public void petsAndMissingPetsReachTheSameRenderer() throws Exception
	{
		Harness harness = new Harness(pets());
		for (String query : new String[]{"pets", "all pets", "allpets"})
		{
			assertEquals("All Pets: 2/3 <img=101>x3 <img=102>", harness.run("!kclog " + query));
			assertEquals("All Pets: 1/3 missing <img=103>", harness.run("!missing " + query));
		}
		assertEquals("All Pets: 1/3 missing <img=103>", harness.run("!log missing pets"));
	}

	@Test
	public void genericPagesWorkWithBothWarmAndColdGameCatalogs() throws Exception
	{
		ClogResult result = result(Map.of("random_events", List.of(item(1, 2)),
			"mastering_mixology", List.of(item(2, 1))),
			Map.of("random_events", List.of(1, 2), "mastering_mixology", List.of(2, 3)));
		Harness harness = new Harness(result);
		for (boolean warm : new boolean[]{true, false})
		{
			if (!warm)
			{
				harness.command.setClogIndex(null);
			}
			assertEquals("Random Events: 1/2 <img=101>x2", harness.run("!kclog random events"));
			assertEquals("Mastering Mixology: 1/2 <img=102>", harness.run("!kclog mixology"));
			assertEquals("Mastering Mixology: 1/2 missing <img=103>", harness.run("!missing mastering mixology"));
		}
	}

	@Test
	public void unknownMissingAndCompleteStatesAreDistinct() throws Exception
	{
		Harness harness = new Harness(pets());
		assertTrue(harness.run("!kclog random events garbage").startsWith("collection log page not recognized"));
		assertTrue(harness.run("!kclog").startsWith("usage !kclog <collection-log page>"));
		harness.result = null;
		assertEquals("All Pets: no clog data", harness.run("!kclog pets"));
		harness.result = result(Map.of(), Map.of());
		assertEquals("All Pets: page not synced", harness.run("!missing pets"));
		harness.result = result(Map.of("all_pets", List.of(item(1, 2))), Map.of("all_pets", List.of(1)));
		assertEquals("All Pets: complete", harness.run("!missing pets"));
		harness.result = result(Map.of(), Map.of("all_pets", List.of(1)));
		assertEquals("All Pets: 0/1", harness.run("!kclog pets"));
		assertEquals("All Pets: 1/1 missing <img=101>", harness.run("!missing pets"));
	}

	@Test
	public void rareShortcutsReachTheQuantityRenderer() throws Exception
	{
		int thirdAge = PanelData.THIRD_AGE_ITEMS[0];
		int gilded = PanelData.GILDED_ITEMS[0];
		Harness harness = new Harness(result(Map.of("hard_treasure_trails", List.of(item(thirdAge, 3), item(gilded, 2))), Map.of()));
		assertTrue(harness.run("!3a").endsWith("<img=" + (thirdAge + 100) + ">x3"));
		assertEquals(harness.run("!3a"), harness.run("!kclog third age"));
		assertTrue(harness.run("!gilded").endsWith("<img=" + (gilded + 100) + ">x2"));
		assertEquals(harness.run("!gilded"), harness.run("!kclog gilded"));
	}

	@Test
	public void outgoingPrivateChatLooksUpSenderNotRecipient() throws Exception
	{
		Harness harness = new Harness(pets());
		harness.run("!kclog pets", ChatMessageType.PRIVATECHATOUT);
		assertEquals("Local player", harness.lookedUp);
		harness.run("!kclog pets", ChatMessageType.PRIVATECHAT);
		assertEquals("Other player", harness.lookedUp);
	}

	private static final class Harness
	{
		private final KillClogChatCommand command = new KillClogChatCommand();
		private final AtomicReference<String> rewritten = new AtomicReference<>();
		private ClogResult result;
		private String lookedUp;

		@SuppressWarnings("unchecked")
		private Harness(ClogResult result) throws Exception
		{
			this.result = result;
			ClogIndex index = new ClogIndex();
			index.publishForTest(result.getCategoryItems(), Map.of());
			command.setClogIndex(index);
			set(command, "localClogCache", new LocalClogCache(null, new NoopScheduledExecutorService())
			{
				@Override
				public boolean isActivePlayer(String name)
				{
					return true;
				}
			});
			set(command, "clogService", new ClogService(null, null, null)
			{
				@Override
				public CompletableFuture<ClogResult> lookup(String name)
				{
					lookedUp = name;
					return CompletableFuture.completedFuture(Harness.this.result);
				}
			});
			set(command, "clientThread", new ClientThread()
			{
				@Override
				public void invoke(Runnable runnable)
				{
					runnable.run();
				}
			});
			Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
				(proxy, method, args) -> method.getName().equals("getName") ? "Local player" : null);
			Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> method.getName().equals("getLocalPlayer") ? player : null);
			set(command, "client", client);
			Field icons = KillClogChatCommand.class.getDeclaredField("itemIconIdx");
			icons.setAccessible(true);
			Map<Integer, Integer> registered = (Map<Integer, Integer>) icons.get(command);
			for (int id : new int[]{1, 2, 3, PanelData.THIRD_AGE_ITEMS[0], PanelData.GILDED_ITEMS[0]})
			{
				registered.put(id, id + 100);
			}
		}

		private String run(String text)
		{
			return run(text, ChatMessageType.PUBLICCHAT);
		}

		private String run(String text, ChatMessageType type)
		{
			rewritten.set(null);
			MessageNode node = (MessageNode) Proxy.newProxyInstance(MessageNode.class.getClassLoader(), new Class<?>[]{MessageNode.class},
				(proxy, method, args) ->
				{
					if (method.getName().equals("setRuneLiteFormatMessage"))
					{
						rewritten.set((String) args[0]);
					}
					return null;
				});
			ChatMessage message = new ChatMessage(node, type, "Other player", text, "", 0);
			if (text.startsWith("!log"))
			{
				command.handleLogCompatibility(message, text);
			}
			else if (text.startsWith("!missing"))
			{
				command.handleMissing(message, text);
			}
			else if (text.equals("!3a"))
			{
				command.handleThirdAge(message, text);
			}
			else if (text.equals("!gilded"))
			{
				command.handleGilded(message, text);
			}
			else
			{
				command.handle(message, text);
			}
			return rewritten.get();
		}
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static ClogResult pets()
	{
		return result(Map.of("all_pets", List.of(item(1, 3), item(2, 1))), Map.of("all_pets", List.of(1, 2, 3)));
	}

	private static ClogResult result(Map<String, List<ClogResult.ClogItem>> obtained, Map<String, List<Integer>> categories)
	{
		return new ClogResult("Test", obtained, categories, new HashMap<>(), null, null);
	}

	private static ClogResult.ClogItem item(int id, int quantity)
	{
		return new ClogResult.ClogItem(id, quantity, null);
	}
}
