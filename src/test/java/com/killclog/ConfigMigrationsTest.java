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
	public void savedSettingsMoveAtStartUpBeforeThePanelIsBuiltAndNeverAtInjection() throws Exception
	{
		// RuneLite injects a plugin that is turned off too: handing it its config changes nothing.
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration("killclog", "skillDisplay")).thenReturn("TOOLTIP");
		KillClogPlugin plugin = new KillClogPlugin();
		plugin.provideConfig(config);
		verify(config, never()).setConfiguration(anyString(), anyString(), any(Object.class));

		// Started, the move comes first, then the panel, which reads its skill location as it is built.
		@SuppressWarnings("unchecked")
		javax.inject.Provider<KillClogPanel> panels = mock(javax.inject.Provider.class);
		when(panels.get()).thenThrow(new IllegalStateException("built"));
		set(plugin, "configManager", config);
		set(plugin, "panelProvider", panels);
		try
		{
			plugin.startUp();
		}
		catch (IllegalStateException built)
		{
			// The panel was asked for; nothing after it is under test here.
		}
		InOrder order = inOrder(config, panels);
		order.verify(config).setConfiguration("killclog", "skillDisplay", SkillDisplay.TRAY);
		order.verify(panels).get();
	}

	private static void set(Object target, String name, Object value) throws ReflectiveOperationException
	{
		java.lang.reflect.Field field = KillClogPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
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
