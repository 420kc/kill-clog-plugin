package com.killclog;

import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncSettingsTest
{
	@Test
	public void followersReadOffWhileSyncIsOffAndTurnOnWithIt()
	{
		KillClogConfig config = mock(KillClogConfig.class);
		ConfigManager configManager = mock(ConfigManager.class);
		SyncSettings settings = new SyncSettings(config, configManager);

		when(config.killclogSync()).thenReturn(false);
		when(config.automaticSync()).thenReturn(true);
		when(config.characterModel()).thenReturn(true);
		settings.enforce();
		verify(configManager).setConfiguration("killclog", "automaticSync", false);
		verify(configManager).unsetConfiguration("killclog", "characterModel");

		settings.syncTurnedOn();
		verify(configManager).unsetConfiguration("killclog", "automaticSync");
		verify(configManager).setConfiguration("killclog", "characterModel", true);
	}

	@Test
	public void followersAreLeftAloneWhileSyncIsOn()
	{
		KillClogConfig config = mock(KillClogConfig.class);
		ConfigManager configManager = mock(ConfigManager.class);
		when(config.killclogSync()).thenReturn(true);
		new SyncSettings(config, configManager).enforce();
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}
}
