package com.killclog;

import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.mockito.InOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigMigrationsTest
{
	@Test
	public void wikiLinksTurnedOffStayOffAsCardLinks()
	{
		ConfigManager off = mock(ConfigManager.class);
		when(off.getConfiguration("killclog", "wikiItemLinks")).thenReturn("false");
		ConfigMigrations.run(off);
		verify(off).setConfiguration("killclog", "cardLinks", CardLinks.OFF);
		verify(off).unsetConfiguration("killclog", "wikiItemLinks");

		// Left on, they're the default: nothing to keep but the old key to clear.
		ConfigManager on = mock(ConfigManager.class);
		when(on.getConfiguration("killclog", "wikiItemLinks")).thenReturn("true");
		ConfigMigrations.run(on);
		verify(on, org.mockito.Mockito.never()).setConfiguration(org.mockito.ArgumentMatchers.eq("killclog"),
			org.mockito.ArgumentMatchers.eq("cardLinks"), org.mockito.ArgumentMatchers.<Object>any());
		verify(on).unsetConfiguration("killclog", "wikiItemLinks");
	}

	@Test
	public void aCardLinkOpensOnAClickAShiftClickOnlyOrNever()
	{
		java.awt.Component card = new java.awt.Canvas();
		java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED,
			0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
		java.awt.event.MouseEvent shiftClick = new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED,
			0, java.awt.event.InputEvent.SHIFT_DOWN_MASK, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
		org.junit.Assert.assertTrue(CardLinks.ON.opens(click));
		org.junit.Assert.assertTrue(CardLinks.ON.opens(shiftClick));
		org.junit.Assert.assertFalse(CardLinks.SHIFT.opens(click));
		org.junit.Assert.assertTrue(CardLinks.SHIFT.opens(shiftClick));
		org.junit.Assert.assertFalse(CardLinks.OFF.opens(click));
		org.junit.Assert.assertFalse(CardLinks.OFF.opens(shiftClick));
	}

	@Test
	public void characterCredentialsKeptBefore270AreCleared()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfigurationKeys("killclog-profile-appearance.")).thenReturn(java.util.List.of(
			"killclog-profile-appearance.1.production.deviceSecret",
			"killclog-profile-appearance.1.production.recoveryToken"));

		ConfigMigrations.run(config);

		verify(config).unsetConfiguration("killclog-profile-appearance", "1.production.deviceSecret");
		verify(config).unsetConfiguration("killclog-profile-appearance", "1.production.recoveryToken");
	}

	@Test
	public void skillSummaryOnlyPlayersMoveToTheActivityTray()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration("killclog", "skillDisplay")).thenReturn("TOOLTIP");

		ConfigMigrations.run(config);

		verify(config).setConfiguration("killclog", "skillDisplay", SkillDisplay.TRAY);
	}

	@Test
	public void savedSettingsMoveBeforeTheConfigIsHandedToAnything()
	{
		// The panel is built at injection and reads its skill location then,
		// so the move belongs to the provider rather than to startUp.
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration("killclog", "skillDisplay")).thenReturn("TOOLTIP");

		new KillClogPlugin().provideConfig(config);

		InOrder order = inOrder(config);
		order.verify(config).setConfiguration("killclog", "skillDisplay", SkillDisplay.TRAY);
		order.verify(config).getConfig(KillClogConfig.class);
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
