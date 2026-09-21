package com.killclog;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercise the real panel through its extracted status row; no network or game client is started. */
public class PanelStatusRowCharacterizationTest
{
	private KillClogPanel panel;
	private PanelStatusRow statusRow;
	private JLabel status;
	private JLabel sync;
	private JLabel character;

	@Before
	public void createPanel() throws Exception
	{
		ClogService catalog = mock(ClogService.class);
		// Keep catalog completion out of status-row tests.
		when(catalog.warmCatalog()).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		KillClogConfig config = new KillClogConfig()
		{
		};
		edt(() ->
		{
			panel = new KillClogPanel(mock(HiscoreService.class), catalog,
				mock(RuneProfileService.class), mock(KillclogService.class),
				config, mock(ConfigManager.class), sprites,
				mock(ItemManager.class), mock(ClientThread.class), new SkillIconManager(), mock(Client.class));
			statusRow = field(panel, "statusRow", PanelStatusRow.class);
			status = field("searchStatus", JLabel.class);
			sync = field("syncArrow", JLabel.class);
			character = field("characterPublish", JLabel.class);
		});
		drain();
	}

	@After
	public void stopPanelTimers() throws Exception
	{
		if (panel != null)
		{
			edt(panel::shutdown);
			drain();
		}
	}

	@Test
	public void controlsRequireLocalDataAndTheirOwnSetting() throws Exception
	{
		panel.setSyncArrowEnabled(true);
		panel.setCharacterPublishEnabled(true);
		drain();
		edt(() -> assertControls(false, false));
		panel.setSyncArrowHasData(true);
		drain();
		edt(() -> assertControls(true, true));
		panel.setSyncArrowEnabled(false);
		drain();
		edt(() -> assertControls(false, true));
		panel.setSyncArrowEnabled(true);
		panel.setCharacterPublishEnabled(false);
		drain();
		edt(() -> assertControls(true, false));
		panel.setSyncArrowHasData(false);
		drain();
		edt(() -> assertControls(false, false));
	}

	@Test
	public void lookupFeedbackSurvivesSyncAndCharacterResults() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.onCompareStatus("Looking up player", Color.RED);
			panel.showSyncProgress(true, "publishing...", false);
			panel.showSyncResult(true, false, "HTTP 503");
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, "Render failed");
			panel.showSyncResult(true, true, null);
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_PUBLISHED_STATUS, true, true, null);
			assertEquals("Looking up player", status.getText());
			assertEquals(Color.RED, status.getForeground());
			assertControls(false, false);
			assertNull(expiry());
			assertNull(field("syncSuccessGlowTimer", Timer.class));
			assertNull(field("characterSuccessGlowTimer", Timer.class));
		});
	}

	@Test
	public void activeSyncAndPublishDoNotReplaceEachOther() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncProgress(true, "publishing...", false);
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);
			assertEquals("publishing...", status.getText());
			panel.showSyncResult(true, true, null);
			assertEquals(" ", status.getText());
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);
			panel.showSyncProgress(true, "retrying...", false);
			panel.showSyncResult(true, true, null);
			assertEquals(KillClogPlugin.CHARACTER_RENDERING_STATUS, status.getText());
			assertControls(false, false);
		});
	}

	@Test
	public void expiredFailureDoesNotEraseNewLookupFeedback() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(true, false, "HTTP 503");
			Timer expiry = expiry();
			assertNotNull(expiry);
			panel.onCompareStatus("Player not found", Color.RED);
			fire(expiry);
			assertEquals("Player not found", status.getText());
			assertControls(false, false);
		});
	}

	@Test
	public void silentFailureIsAvailableOnHoverAndSuccessClearsIt() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(false, false, "HTTP 503");
			assertEquals(" ", status.getText());
			assertNull(expiry());
			mouse(sync, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("publish failed - click to retry", status.getText());
			assertEquals("HTTP 503", sync.getToolTipText());
			mouse(sync, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);
			assertEquals(" ", status.getText());
			panel.showSyncResult(false, true, null);
			assertNull(field("syncSuccessGlowTimer", Timer.class));
			mouse(sync, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("publish collection log", status.getText());
			assertNull(sync.getToolTipText());
		});
	}

	@Test
	public void characterFailureExpiresIntoEscapedHoverDetails() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, "Bad <model> & retry");
			assertEquals(KillClogPlugin.CHARACTER_FAILED_STATUS, status.getText());
			assertControls(false, false);
			fire(expiry());
			assertEquals(" ", status.getText());
			assertControls(true, true);
			mouse(character, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("character failed - click to retry", status.getText());
			assertEquals("<html><div style='width:220px'>Bad &lt;model&gt; &amp; retry</div></html>", character.getToolTipText());
			panel.onCompareStatus("Looking up player", Color.RED);
			mouse(character, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);
			assertEquals("Looking up player", status.getText());
		});
	}

	@Test
	public void disablingAndReenablingControlsClearsTheirFailureHistory() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(false, false, "Old sync failure");
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, "Old publish failure");
			fire(expiry());
		});
		panel.setSyncArrowEnabled(false);
		panel.setCharacterPublishEnabled(false);
		drain();
		enableControls();
		edt(() ->
		{
			mouse(sync, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("publish collection log", status.getText());
			assertNull(sync.getToolTipText());
			mouse(sync, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);
			mouse(character, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("publish character", status.getText());
			assertNull(character.getToolTipText());
		});
	}

	@Test
	public void visibleControlsDispatchOnlyLeftClicksWithoutChangingRowHeight() throws Exception
	{
		enableControls();
		edt(() ->
		{
			AtomicInteger syncClicks = new AtomicInteger();
			AtomicInteger characterClicks = new AtomicInteger();
			panel.setKillclogSyncHandler(syncClicks::incrementAndGet);
			panel.setCharacterPublishHandler(characterClicks::incrementAndGet);
			int height = status.getParent().getPreferredSize().height;
			mouse(sync, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3);
			mouse(character, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3);
			assertEquals(0, syncClicks.get());
			assertEquals(0, characterClicks.get());
			mouse(sync, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
			mouse(character, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
			assertEquals(1, syncClicks.get());
			assertEquals(1, characterClicks.get());
			panel.showSyncProgress(true, "publishing...", false);
			mouse(sync, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
			mouse(character, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
			assertEquals(1, syncClicks.get());
			assertEquals(1, characterClicks.get());
			assertEquals(height, status.getParent().getPreferredSize().height);
		});
	}

	@Test
	public void syncResetPreservesCharacterTextAndClearsItsOwnFailure() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(false, false, "Old account failure");
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);
		});
		panel.resetSyncFeedback();
		drain();
		edt(() ->
		{
			assertEquals(KillClogPlugin.CHARACTER_RENDERING_STATUS, status.getText());
			panel.showCharacterPublishStatus(" ", false, false, null);
			mouse(sync, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals("publish collection log", status.getText());
			assertNull(sync.getToolTipText());
		});
	}

	@Test
	public void shutdownStopsFeedbackTimersAndAllowsCleanControlReuse() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(true, true, null);
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_PUBLISHED_STATUS, true, false, null);
			Timer syncGlow = field("syncSuccessGlowTimer", Timer.class);
			Timer characterGlow = field("characterSuccessGlowTimer", Timer.class);
			assertTrue(syncGlow.isRunning());
			assertTrue(characterGlow.isRunning());
			panel.shutdown();
			assertFalse(syncGlow.isRunning());
			assertFalse(characterGlow.isRunning());
			assertNull(field("syncSuccessGlowTimer", Timer.class));
			assertNull(field("characterSuccessGlowTimer", Timer.class));
			assertControls(false, false);
		});
		enableControls();
		edt(() ->
		{
			assertSame(sync, field("syncArrow", JLabel.class));
			assertControls(true, true);
			panel.showSyncResult(true, false, "Failure after restart");
			Timer failure = expiry();
			assertTrue(failure.isRunning());
			panel.shutdown();
			assertFalse(failure.isRunning());
			assertNull(expiry());
		});
	}

	@Test
	public void sameTextReplacementKeepsItsOwnExpiry() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(true, false, "HTTP 503");
			Timer first = expiry();
			panel.showSyncResult(true, false, "HTTP 503 again");
			Timer second = expiry();
			assertNotSame(first, second);
			assertFalse(first.isRunning());
			assertTrue(second.isRunning());
			fire(first);
			assertEquals("publish failed", status.getText());
			assertSame(second, expiry());
			assertTrue(second.isRunning());
			fire(second);
			assertEquals(" ", status.getText());
			assertNull(expiry());
			assertControls(true, true);
		});
	}

	@Test
	public void hoverOverNoticeOutlivesTheNoticeExpiry() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_APPEARANCE_STATUS, false, true, "Cosmetic override");
			assertEquals(KillClogPlugin.CHARACTER_APPEARANCE_STATUS, status.getText());
			assertControls(true, true);
			Timer notice = expiry();
			assertTrue(notice.isRunning());
			mouse(character, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			assertEquals(KillClogPlugin.CHARACTER_APPEARANCE_STATUS, status.getText());
			assertEquals(new Color(78, 240, 21), status.getForeground());
			assertFalse(notice.isRunning());
			assertNull(expiry());
			fire(notice);
			assertEquals(KillClogPlugin.CHARACTER_APPEARANCE_STATUS, status.getText());
			assertEquals("<html><div style='width:220px'>Cosmetic override</div></html>", character.getToolTipText());
			mouse(character, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);
			assertEquals(" ", status.getText());
			assertControls(true, true);
		});
	}

	@Test
	public void syncSuccessFlashesOnlyOverBlankOrSyncOwnedText() throws Exception
	{
		enableControls();
		edt(() ->
		{
			panel.showSyncResult(true, true, null);
			assertNotNull(field("syncSuccessGlowTimer", Timer.class));
			fire(field("syncSuccessGlowTimer", Timer.class));
			assertNull(field("syncSuccessGlowTimer", Timer.class));

			panel.showSyncProgress(true, "publishing...", false);
			panel.showSyncResult(true, true, null);
			assertEquals(" ", status.getText());
			assertNotNull(field("syncSuccessGlowTimer", Timer.class));
			fire(field("syncSuccessGlowTimer", Timer.class));

			mouse(sync, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			panel.showSyncResult(true, true, null);
			assertEquals(" ", status.getText());
			assertNotNull(field("syncSuccessGlowTimer", Timer.class));
			mouse(sync, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);
			fire(field("syncSuccessGlowTimer", Timer.class));

			mouse(character, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
			panel.showSyncResult(true, true, null);
			assertEquals("publish character", status.getText());
			assertNull(field("syncSuccessGlowTimer", Timer.class));
			mouse(character, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);

			panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);
			panel.showSyncResult(true, true, null);
			assertEquals(KillClogPlugin.CHARACTER_RENDERING_STATUS, status.getText());
			assertNull(field("syncSuccessGlowTimer", Timer.class));
			panel.showCharacterPublishStatus(" ", false, false, null);

			panel.onCompareStatus("Player not found", Color.RED);
			panel.showSyncResult(true, true, null);
			assertEquals("Player not found", status.getText());
			assertNull(field("syncSuccessGlowTimer", Timer.class));
		});
	}

	@Test
	public void syncResetLeavesCharacterExpiryRunning() throws Exception
	{
		enableControls();
		edt(() -> panel.showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, "Render failed"));
		panel.resetSyncFeedback();
		drain();
		edt(() ->
		{
			assertEquals(KillClogPlugin.CHARACTER_FAILED_STATUS, status.getText());
			Timer expiry = expiry();
			assertNotNull("character result keeps its expiry through a sync reset", expiry);
			assertTrue(expiry.isRunning());
			fire(expiry);
			assertEquals(" ", status.getText());
			assertControls(true, true);
		});
	}

	private Timer expiry()
	{
		StatusMessage current = field("current", StatusMessage.class);
		return current == null ? null : field(current, "expiry", Timer.class);
	}

	private void enableControls() throws Exception
	{
		panel.setSyncArrowEnabled(true);
		panel.setSyncArrowHasData(true);
		panel.setCharacterPublishEnabled(true);
		drain();
	}

	private void assertControls(boolean syncVisible, boolean characterVisible)
	{
		assertEquals(syncVisible, sync.isVisible());
		assertEquals(characterVisible, character.isVisible());
	}

	private static void mouse(JLabel label, int type, int button)
	{
		label.dispatchEvent(new MouseEvent(label, type, 0, 0, 1, 1, 1, false, button));
	}

	private static void fire(Timer timer)
	{
		// Deliver the real expiry callback on the EDT without a wall-clock wait.
		timer.stop();
		for (ActionListener listener : timer.getActionListeners())
		{
			listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "test expiry"));
		}
	}

	private <T> T field(String name, Class<T> type)
	{
		return field(statusRow, name, type);
	}

	private static <T> T field(Object owner, String name, Class<T> type)
	{
		try
		{
			Field field = owner.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(owner));
		}
		catch (ReflectiveOperationException exception)
		{
			throw new AssertionError(exception);
		}
	}

	private static void drain() throws Exception
	{
		edt(() ->
		{
		});
	}

	private static void edt(Runnable action) throws Exception
	{
		SwingUtilities.invokeAndWait(action);
	}
}
