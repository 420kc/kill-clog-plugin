package com.killclog;

import java.lang.reflect.Proxy;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProfileAppearanceServiceTest
{
	@Test
	public void visibleFollowerNpcIdUsesTransformedComposition()
	{
		NPCComposition visible = proxy(NPCComposition.class, 12293, null);
		NPC follower = proxy(NPC.class, 12296, visible);

		assertEquals(12293, ProfileAppearanceService.visibleFollowerNpcId(follower));
	}

	@Test
	public void visibleFollowerNpcIdFallsBackToNpcId()
	{
		NPC follower = proxy(NPC.class, 9399, null);

		assertEquals(9399, ProfileAppearanceService.visibleFollowerNpcId(follower));
		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(null));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, int id, NPCComposition transformed)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) ->
			{
				if (method.getName().equals("getId"))
				{
					return id;
				}
				if (method.getName().equals("getTransformedComposition"))
				{
					return transformed;
				}
				Class<?> returnType = method.getReturnType();
				if (returnType.equals(boolean.class))
				{
					return false;
				}
				if (returnType.equals(int.class))
				{
					return 0;
				}
				return null;
			});
	}
}
