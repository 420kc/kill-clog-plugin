package com.killclog;

import com.fourtwentykc.FourTwentyKcPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KillClogPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(KillClogPlugin.class, FourTwentyKcPlugin.class);
        RuneLite.main(args);
    }
}
