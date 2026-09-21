package com.killclog;

import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigMigrationsTest
{
	@Test
	public void skillSummaryOnlyPlayersMoveToTheActivityTray()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration("killclog", "skillDisplay")).thenReturn("TOOLTIP");

		ConfigMigrations.run(config);

		verify(config).setConfiguration("killclog", "skillDisplay", SkillDisplay.TRAY);
	}

	@Test
	public void everyOtherSkillLocationIsLeftAlone()
	{
		for (String saved : new String[]{"FIXED", "TRAY", null})
		{
			ConfigManager config = mock(ConfigManager.class);
			when(config.getConfiguration("killclog", "skillDisplay")).thenReturn(saved);

			ConfigMigrations.run(config);

			verify(config, never()).setConfiguration(eq("killclog"), eq("skillDisplay"), any(Object.class));
		}
	}

	@Test
	public void legacyCompletionColourStillBecomesAColourModeOnce()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration("killclog", "skillCompletionColor")).thenReturn("false");

		ConfigMigrations.run(config);

		verify(config).setConfiguration("killclog", "skillColorMode", SkillColorMode.SKILL_COLOR);
		verify(config).unsetConfiguration("killclog", "skillCompletionColor");

		// A mode the player already chose is never overwritten by the legacy value.
		ConfigManager chosen = mock(ConfigManager.class);
		when(chosen.getConfiguration("killclog", "skillCompletionColor")).thenReturn("true");
		when(chosen.getConfiguration("killclog", "skillColorMode")).thenReturn("CLOG_PROGRESSION");

		ConfigMigrations.run(chosen);

		verify(chosen, never()).setConfiguration(anyString(), eq("skillColorMode"), any(Object.class));
		verify(chosen).unsetConfiguration("killclog", "skillCompletionColor");
	}
}
