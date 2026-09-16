package com.killclog;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemClient;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogIndexParsingTest
{
	@Test
	public void runtimeAliasesProduceOneSlotAndOneReverseMembership() throws Exception
	{
		// These synthetic IDs exercise the runtime remap, not a known fallback.
		Map<Integer, EnumComposition> enums = Map.of(
			3721, enumeration(Map.of(10001, 10000)),
			2102, enumeration(Map.of(0, 10)),
			20, enumeration(Map.of(0, 30)),
			40, enumeration(Map.of(0, 10000, 1, 10001)));
		Client client = proxy(Client.class, (object, method, args) ->
		{
			switch (method.getName())
			{
				case "getEnum": return enums.get((Integer) args[0]);
				case "getStructComposition":
					int structId = (Integer) args[0];
					return proxy(StructComposition.class, (o, m, a) ->
						m.getName().equals("getStringValue") ? "Zulrah" : structId == 10 ? 20 : 40);
				case "getItemDefinition":
					int itemId = (Integer) args[0];
					return proxy(ItemComposition.class, (o, m, a) -> "Fixture " + itemId);
				default: throw new AssertionError(method.getName());
			}
		});
		Constructor<ItemManager> constructor = ItemManager.class.getDeclaredConstructor(
			Client.class, ScheduledExecutorService.class, ClientThread.class,
			EventBus.class, ItemClient.class, RuneLiteConfig.class);
		constructor.setAccessible(true);
		// No scheduled callbacks run, so this fixture never requests item prices.
		ScheduledExecutorService executor = proxy(ScheduledExecutorService.class, (o, m, a) -> null);
		ItemManager itemManager = constructor.newInstance(client, executor, null, new EventBus(), null, null);
		ClogIndex index = new ClogIndex();
		assertTrue(index.ensureParsed(client, itemManager));
		assertEquals(Collections.singletonList(10000), index.copyCategoryItems().get("zulrah"));
		assertEquals(Collections.singletonList("zulrah"), index.categoryKeysForItem(10000));
		assertEquals(Collections.singletonList("zulrah"), index.categoryKeysForItem(10001));
		assertEquals(10000, index.canonicalizeItems(Collections.singletonList(
			new ClogResult.ClogItem(10001, 2, null))).get(0).getId());
	}

	private static EnumComposition enumeration(Map<Integer, Integer> values)
	{
		int[] keys = values.keySet().stream().mapToInt(Integer::intValue).toArray();
		return proxy(EnumComposition.class, (object, method, args) ->
		{
			switch (method.getName())
			{
				case "getKeys": return keys;
				case "getIntVals": return java.util.Arrays.stream(keys).map(values::get).toArray();
				case "getIntValue": return values.get((Integer) args[0]);
				default: throw new AssertionError(method.getName());
			}
		});
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
