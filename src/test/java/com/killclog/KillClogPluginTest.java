package com.killclog;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

public class KillClogPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		try
		{
			Class<? extends Plugin> fourTwenty =
				(Class<? extends Plugin>) Class.forName("com.fourtwentykc.FourTwentyKcPlugin");
			ExternalPluginManager.loadBuiltin(KillClogPlugin.class, fourTwenty);
		}
		catch (ClassNotFoundException e)
		{
			ExternalPluginManager.loadBuiltin(KillClogPlugin.class);
		}
		RuneLite.main(args);
	}
}
