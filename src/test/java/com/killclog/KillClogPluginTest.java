package com.killclog;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

public class KillClogPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		java.util.List<Class<? extends Plugin>> plugins = new java.util.ArrayList<>();
		plugins.add(KillClogPlugin.class);
		tryLoad(plugins, "com.fourtwentykc.FourTwentyKcPlugin");
		tryLoad(plugins, "com.claudescape.ClaudescapePlugin");
		ExternalPluginManager.loadBuiltin(plugins.toArray(new Class[0]));
		RuneLite.main(args);
	}

	private static void tryLoad(java.util.List<Class<? extends Plugin>> list, String className)
	{
		try
		{
			@SuppressWarnings("unchecked")
			Class<? extends Plugin> cls = (Class<? extends Plugin>) Class.forName(className);
			list.add(cls);
		}
		catch (ClassNotFoundException ignored)
		{
		}
	}
}
