package com.killclog;

import java.lang.reflect.Proxy;
import net.runelite.api.Player;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileCardLocalCaptureTest
{
	@Test
	public void acceptsOnlyFrameZeroOfThePlayersIdlePose()
	{
		assertTrue(ProfileCardLocalCapture.isStandardStandingPose(player(-1, 808, 808, 0)));
		assertFalse(ProfileCardLocalCapture.isStandardStandingPose(player(829, 808, 808, 0)));
		assertFalse(ProfileCardLocalCapture.isStandardStandingPose(player(-1, 819, 808, 0)));
		assertFalse(ProfileCardLocalCapture.isStandardStandingPose(player(-1, 808, 808, 1)));
	}

	private static Player player(int animation, int pose, int idle, int frame)
	{
		return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
			new Class<?>[]{Player.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getAnimation":
						return animation;
					case "getPoseAnimation":
						return pose;
					case "getIdlePoseAnimation":
						return idle;
					case "getPoseAnimationFrame":
						return frame;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class)
		{
			return false;
		}
		if (type == byte.class || type == short.class || type == int.class || type == long.class)
		{
			return 0;
		}
		if (type == float.class || type == double.class)
		{
			return 0d;
		}
		if (type == char.class)
		{
			return '\0';
		}
		return null;
	}
}
