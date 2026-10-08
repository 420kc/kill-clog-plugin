package com.killclog;

import net.runelite.client.config.ConfigManager;

/**
 * Sync and its two followers, Automatic sync and Publish Character Model. RuneLite can't gray a setting out,
 * so the dependency is kept at the data: the followers read off while Sync is off, and turn on with it.
 */
final class SyncSettings
{
	private final KillClogConfig config;
	private final ConfigManager configManager;

	SyncSettings(KillClogConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	/** At startup and whenever a follower changes: one ticked while Sync is off doesn't stay ticked. */
	void enforce()
	{
		if (!config.killclogSync())
		{
			if (config.automaticSync())
			{
				configManager.setConfiguration("killclog", "automaticSync", false);
			}
			if (config.characterModel())
			{
				configManager.unsetConfiguration("killclog", "characterModel");
			}
		}
	}

	/** Turning Sync on turns its followers on with it. */
	void syncTurnedOn()
	{
		configManager.unsetConfiguration("killclog", "automaticSync");
		configManager.setConfiguration("killclog", "characterModel", true);
	}
}
