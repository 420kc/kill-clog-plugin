package com.killclog;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drive the real plugin's publication flow through its events and panel
 * handlers. The executor and client thread are queues the test pumps by hand,
 * HTTP and cache services are mocks whose futures the test completes, and the
 * panel is a mock whose feedback calls are the assertions. No RuneLite client,
 * network or disk is started.
 */
public class PluginPublicationCharacterizationTest
{
	private static final String RSN = "Dylan";
	private static final long HASH = 42L;

	private final Settings config = new Settings();
	private final QueuedScheduler executor = new QueuedScheduler();
	private final Deque<Runnable> clientQueue = new ArrayDeque<>();
	private final KillClogPanel panel = mock(KillClogPanel.class);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final LocalClogCache localClogCache = mock(LocalClogCache.class);
	private final SyncService syncService = mock(SyncService.class);
	private final ProfileAppearanceService appearance = mock(ProfileAppearanceService.class);
	private final KillClogChatNotifier chatNotifier = mock(KillClogChatNotifier.class);
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final List<CompletableFuture<SyncService.SyncResult>> syncs = new ArrayList<>();
	private final List<CompletableFuture<ProfileAppearanceService.PublishResult>> publishes = new ArrayList<>();
	private long epoch = 7;
	// Startup sees no session, as before; the player is then on a main-game members world.
	private GameState gameState;
	private KillClogPlugin plugin;
	private Runnable syncHandler;
	private Runnable publishHandler;
	private Runnable captureListener;

	private static final class Settings implements KillClogConfig
	{
		boolean sync = true;
		boolean character = true;

		@Override
		public boolean killclogSync()
		{
			return sync;
		}

		@Override
		public boolean characterModel()
		{
			return character;
		}
	}

	@Before
	public void startPlugin() throws Exception
	{
		Player local = mock(Player.class);
		when(local.getName()).thenReturn(RSN);
		when(client.getLocalPlayer()).thenReturn(local);
		when(client.getAccountHash()).thenReturn(HASH);
		when(client.getGameState()).thenAnswer(invocation -> gameState);
		when(client.getWorldType()).thenReturn(java.util.EnumSet.of(net.runelite.api.WorldType.MEMBERS));
		when(localClogCache.currentSessionEpoch()).thenAnswer(invocation -> epoch);
		when(syncService.syncCollectionLog(any(), anyLong(), any(), any(), any(), anyLong(), any(), anyInt()))
			.thenAnswer(invocation ->
			{
				CompletableFuture<SyncService.SyncResult> sync = new CompletableFuture<>();
				syncs.add(sync);
				return sync;
			});
		when(appearance.publishCurrent(any(), anyLong(), any())).thenAnswer(invocation ->
		{
			CompletableFuture<ProfileAppearanceService.PublishResult> publish = new CompletableFuture<>();
			publishes.add(publish);
			return publish;
		});
		doAnswer(invocation ->
		{
			clientQueue.add(invocation.getArgument(0));
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		doAnswer(invocation ->
		{
			clientQueue.add(invocation.getArgument(0));
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));

		plugin = new KillClogPlugin();
		for (Field field : KillClogPlugin.class.getDeclaredFields())
		{
			if (!field.isAnnotationPresent(javax.inject.Inject.class))
			{
				continue;
			}
			field.setAccessible(true);
			field.set(plugin, dependency(field));
		}
		plugin.startUp();
		gameState = GameState.LOGGED_IN;

		ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setKillclogSyncHandler(handler.capture());
		syncHandler = handler.getValue();
		handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setCharacterPublishHandler(handler.capture());
		publishHandler = handler.getValue();
		handler = ArgumentCaptor.forClass(Runnable.class);
		verify(localClogCache).setFirstPartyChangedListener(handler.capture());
		captureListener = handler.getValue();
		clearInvocations(panel, chatNotifier, localClogCache);
	}

	@After
	public void stopPlugin() throws Exception
	{
		plugin.shutDown();
		drainEdt();
	}

	@Test
	public void manualSyncNarratesAndReportsTheResult() throws Exception
	{
		syncHandler.run();
		assertEquals(1, executor.live());
		settle();
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Publishing collection log...");
		verify(panel).showSyncProgress(true, "publishing...", false);
		assertEquals(1, syncs.size());
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), any(), any(), any(), eq(7L), any(), eq(0));

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced 12 items"));
		settle();
		verify(panel).showSyncResult(true, true, "Synced 12 items");
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Synced 12 items");
	}

	@Test
	public void repeatClicksDuringAFlightQueueOneFollowUp() throws Exception
	{
		syncHandler.run();
		settle();
		syncHandler.run();
		settle();
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "First"));
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Second"));
		settle();
		assertEquals(2, syncs.size());
		verify(panel, times(2)).showSyncProgress(true, "publishing...", false);
		verify(panel).showSyncResult(true, true, "First");
		verify(panel).showSyncResult(true, true, "Second");
	}

	@Test
	public void captureDebounceCoalescesAndStaysQuiet() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		captureListener.run();
		verify(panel, times(2)).setSyncArrowHasData(true);
		assertEquals(1, executor.live());
		assertEquals(10_000L, executor.lastDelayMs());

		settle();
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_RESULT), startsWith("Publishing"));
		verify(panel).showSyncProgress(false, "publishing...", false);
		assertEquals(1, syncs.size());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel).showSyncResult(false, true, "Synced");
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, "Synced");
	}

	@Test
	public void logoutBeforeTheClientHopSilencesTheAttempt() throws Exception
	{
		syncHandler.run();
		executor.runAll();
		assertEquals(1, clientQueue.size());

		logout();
		epoch = 8;
		settle();
		assertTrue(syncs.isEmpty());
		verify(panel, never()).showSyncProgress(anyBoolean(), any(), anyBoolean());
		verify(panel).setSyncArrowHasData(false);
		verify(panel).resetSyncFeedback();
	}

	@Test
	public void lateCompletionAfterLogoutStaysSilentAndTheNextSessionPushesAgain() throws Exception
	{
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		clearInvocations(panel, chatNotifier);

		logout();
		epoch = 8;
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		verify(chatNotifier, never()).send(any(), any());

		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(2, syncs.size());
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), any(), any(), any(), eq(8L), any(), anyInt());
	}

	@Test
	public void sessionChangeBeforeTheTimerFiresDropsThePush() throws Exception
	{
		syncHandler.run();
		epoch = 8;
		settle();
		assertTrue(syncs.isEmpty());
		verify(panel, never()).showSyncProgress(anyBoolean(), any(), anyBoolean());

		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void optOutMidFlightThenOptInQueuesBehindTheOldRequest() throws Exception
	{
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());

		config.sync = false;
		configChanged("killclogSync");
		verify(panel).setSyncArrowEnabled(false);
		verify(panel).setCharacterPublishEnabled(false);
		verify(configManager).unsetConfiguration("killclog", "characterModel");
		config.character = false;

		config.sync = true;
		configChanged("killclogSync");
		verify(panel).setSyncArrowEnabled(true);
		settle();
		assertEquals(1, syncs.size());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Old"));
		settle();
		assertEquals(2, syncs.size());
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), eq("Old"));
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, "Old");

		syncs.get(1).complete(new SyncService.SyncResult(true, false, "New"));
		settle();
		verify(panel).showSyncResult(true, true, "New");
	}

	private static SyncService.SyncResult restarting()
	{
		return new SyncService.SyncResult(false, false,
			"Collection log publication failed (HTTP 502).", true, 20);
	}

	@Test
	public void serverContentionRetriesOnceThenReports() throws Exception
	{
		syncHandler.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Another sync holds the lock", true, 5));
		settle();
		verify(panel).showSyncProgress(true, "retrying...", false);
		assertEquals(5_000L, executor.lastDelayMs());
		assertEquals(2, syncs.size());

		syncs.get(1).complete(new SyncService.SyncResult(false, false, "Another sync holds the lock", true, 5));
		settle();
		assertEquals(2, syncs.size());
		verify(panel).showSyncResult(true, false, "Another sync holds the lock");
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Another sync holds the lock");
	}

	@Test
	public void aRestartingServerNeverReachesChatWhenTheQuietRetryLands() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(1, syncs.size());

		String failed = "Collection log publication failed (HTTP 502).";
		syncs.get(0).complete(new SyncService.SyncResult(false, false, failed, true, 20));
		settle();
		// Nobody is watching an automatic sync: it waits twice the advised delay.
		assertEquals(40_000L, executor.lastDelayMs());
		assertEquals(2, syncs.size());
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), eq(failed));
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, failed);

		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel).showSyncResult(false, true, "Synced");
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_RESULT), any());
	}

	@Test
	public void aServerStillDownAfterTheQuietRetryIsReportedOnce() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();

		String failed = "Collection log publication failed (HTTP 502).";
		syncs.get(0).complete(new SyncService.SyncResult(false, false, failed, true, 20));
		settle();
		syncs.get(1).complete(new SyncService.SyncResult(false, false, failed, true, 20));
		settle();
		assertEquals(2, syncs.size());
		verify(panel).showSyncResult(false, false, failed);
		verify(chatNotifier, times(1)).send(ChatNotice.SYNC_RESULT, failed);
	}

	@Test
	public void aCaptureWhileTheFirstPushIsInTheAirDoesNotShortenTheQuietRetry() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(1, syncs.size());
		// A log walk is still landing pages, which arms the ten second debounce.
		captureListener.run();
		assertEquals(10_000L, executor.lastDelayMs());

		syncs.get(0).complete(restarting());
		// The retry takes over from the debounce rather than yielding to it.
		assertEquals(40_000L, executor.lastDelayMs());
		assertEquals(1, executor.live());
		settle();
		assertEquals(2, syncs.size());
	}

	@Test
	public void everyEpisodeGetsItsOwnQuietRetry() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		syncs.get(0).complete(restarting());
		settle();
		syncs.get(1).complete(restarting());
		settle();
		assertEquals(2, syncs.size());

		captureListener.run();
		settle();
		assertEquals(3, syncs.size());
		syncs.get(2).complete(restarting());
		settle();
		assertEquals(4, syncs.size());
	}

	@Test
	public void logoutDuringTheWaitDropsTheQuietRetry() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		syncs.get(0).complete(restarting());
		assertEquals(1, executor.live());

		logout();
		epoch = 8;
		assertEquals(0, executor.live());
		settle();
		assertEquals(1, syncs.size());
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_RESULT), any());
	}

	@Test
	public void aCharacterPublishWaitsThroughTheRetryAndPublishesOnce() throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(1, syncs.size());
		syncs.get(0).complete(restarting());
		// Someone is waiting on this one, so it gets the advised delay as it is.
		assertEquals(20_000L, executor.lastDelayMs());

		// A second click during the wait is ignored: the publish is still parked.
		publishHandler.run();
		settle();
		assertEquals(2, syncs.size());
		assertEquals(1, publishes.size());
		verify(panel, never()).showCharacterPublishStatus(eq(KillClogPlugin.CHARACTER_FAILED_STATUS),
			anyBoolean(), anyBoolean(), any());

		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void publishRendersThenReportsAndIgnoresADoubleClick() throws Exception
	{
		publishHandler.run();
		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());
		verify(appearance).publishCurrent(eq(RSN), eq(HASH), any());
		verify(panel).showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);

		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PUBLISHED, "ok"));
		settle();
		verify(panel).showCharacterPublishStatus(KillClogPlugin.CHARACTER_PUBLISHED_STATUS, true, true, "ok");

		publishHandler.run();
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void profileRequiredRunsOneQuietPrerequisiteSyncThenRetriesOnce() throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(1, syncs.size());
		verify(panel, never()).showSyncProgress(anyBoolean(), any(), anyBoolean());
		verify(chatNotifier, never()).send(any(), any());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals(ProfileAppearanceService.PUBLISH_RETRY_DELAY_MS, executor.lastDelayMs());
		assertEquals(2, publishes.size());
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		// Rendering shows on the click, again when the publish parks behind the sync, and again on the retry.
		verify(panel, times(3)).showCharacterPublishStatus(KillClogPlugin.CHARACTER_RENDERING_STATUS, false, false, null);

		publishes.get(1).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, "still"));
		settle();
		verify(panel).showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, "still");
		assertEquals(1, syncs.size());
		assertEquals(2, publishes.size());
	}

	@Test
	public void failedPrerequisiteSyncFailsThePublishAndFreesTheSlot() throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Server unavailable"));
		settle();
		verify(panel).showCharacterPublishStatus(KillClogPlugin.CHARACTER_FAILED_STATUS, false, true, null);
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Server unavailable");
		assertEquals(1, publishes.size());

		publishHandler.run();
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void logoutDuringThePrerequisiteSyncWithdrawsThePublish() throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(1, syncs.size());
		clearInvocations(panel);

		logout();
		epoch = 8;
		settle();
		verify(panel).showCharacterPublishStatus(" ", false, false, null);

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel, never()).showCharacterPublishStatus(eq(KillClogPlugin.CHARACTER_FAILED_STATUS), anyBoolean(), anyBoolean(), any());
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		assertEquals(1, publishes.size());
	}

	@Test
	public void disablingCharacterPublishingSilencesTheFlightAndReenablingAllowsANewOne() throws Exception
	{
		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());

		config.character = false;
		configChanged("characterModel");
		settle();
		verify(panel).setCharacterPublishEnabled(false);
		verify(panel).showCharacterPublishStatus(" ", false, false, null);
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PUBLISHED, "late"));
		settle();
		verify(panel, never()).showCharacterPublishStatus(eq(KillClogPlugin.CHARACTER_PUBLISHED_STATUS), anyBoolean(), anyBoolean(), any());

		config.character = true;
		configChanged("characterModel");
		verify(panel).setCharacterPublishEnabled(true);
		publishHandler.run();
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void restartKeepsTheOccupiedSlotAndQueuesTheNextRequest() throws Exception
	{
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		clearInvocations(panel, chatNotifier);

		// RuneLite disables and re-enables the same plugin instance.
		plugin.shutDown();
		drainEdt();
		plugin.startUp();
		ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setKillclogSyncHandler(handler.capture());
		syncHandler = handler.getValue();
		clearInvocations(panel);

		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Old"));
		settle();
		assertEquals(2, syncs.size());
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), eq("Old"));
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, "Old");

		syncs.get(1).complete(new SyncService.SyncResult(true, false, "New"));
		settle();
		verify(panel).showSyncResult(true, true, "New");
	}

	@Test
	public void shutDownDropsAScheduledPush() throws Exception
	{
		syncHandler.run();
		assertEquals(1, executor.live());
		plugin.shutDown();
		assertEquals(0, executor.live());
		settle();
		assertTrue(syncs.isEmpty());
	}

	private void world(net.runelite.api.WorldType first, net.runelite.api.WorldType... rest)
	{
		when(client.getWorldType()).thenReturn(java.util.EnumSet.of(first, rest));
	}

	private void ticks(int count)
	{
		for (int i = 0; i < count; i++)
		{
			plugin.onGameTick(new net.runelite.api.events.GameTick());
		}
	}

	private void varp(int id)
	{
		net.runelite.api.events.VarbitChanged event = new net.runelite.api.events.VarbitChanged();
		event.setVarpId(id);
		plugin.onVarbitChanged(event);
	}

	private AccountType sentType(int call)
	{
		ArgumentCaptor<AccountType> type = ArgumentCaptor.forClass(AccountType.class);
		verify(syncService, times(call)).syncCollectionLog(any(), anyLong(), type.capture(), any(), any(), anyLong(), any(), anyInt());
		return type.getAllValues().get(call - 1);
	}

	@Test
	public void onALeagueWorldNothingSyncsOrPublishes() throws Exception
	{
		world(net.runelite.api.WorldType.SEASONAL, net.runelite.api.WorldType.MEMBERS);
		syncHandler.run();
		settle();
		publishHandler.run();
		settle();
		assertTrue(syncs.isEmpty());
		assertTrue(publishes.isEmpty());
	}

	@Test
	public void theAccountTypeIsSentOnlyOnceTheMainWorldHasSettled() throws Exception
	{
		syncHandler.run();
		settle();
		org.junit.Assert.assertNull("an unsettled reading is never sent", sentType(1));
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		ticks(10);
		syncHandler.run();
		settle();
		assertEquals(AccountType.REGULAR, sentType(2));
		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		net.runelite.api.events.VarbitChanged changed = new net.runelite.api.events.VarbitChanged();
		changed.setVarbitId(net.runelite.api.gameval.VarbitID.IRONMAN);
		plugin.onVarbitChanged(changed);
		ticks(9);
		syncHandler.run();
		settle();
		org.junit.Assert.assertNull("a type change settles again first", sentType(3));
	}

	@Test
	public void countersAndCombatAchievementsWaitForASettledMainWorld() throws Exception
	{
		LocalCaCache caCache = (LocalCaCache) field("localCaCache");
		when(localClogCache.hasDataFor(RSN)).thenReturn(true);
		when(client.getVarpValue(ClogVarps.OBTAINED)).thenReturn(5);
		when(client.getVarpValue(ClogVarps.TOTAL)).thenReturn(10);
		varp(ClogVarps.OBTAINED);
		ticks(9);
		verify(localClogCache, never()).updateTotalsUpward(any(), anyInt(), anyInt());
		verify(caCache, never()).cacheResult(any(), any());
		ticks(1);
		verify(localClogCache).updateTotalsUpward(RSN, 5, 10);
		verify(caCache).cacheResult(eq(RSN), any());
		world(net.runelite.api.WorldType.SEASONAL);
		varp(ClogVarps.OBTAINED);
		ticks(20);
		verify(localClogCache).updateTotalsUpward(RSN, 5, 10);
		verify(caCache).cacheResult(eq(RSN), any());
	}

	private void unlockMessage() throws Exception
	{
		net.runelite.api.events.ChatMessage unlock = new net.runelite.api.events.ChatMessage();
		unlock.setType(net.runelite.api.ChatMessageType.GAMEMESSAGE);
		unlock.setMessage("New item added to your collection log: Twisted bow");
		plugin.onChatMessage(unlock);
		settle();
	}

	@Test
	public void aLeagueWorldUnlockNeverReachesTheMainLog() throws Exception
	{
		world(net.runelite.api.WorldType.SEASONAL);
		unlockMessage();
		// On this harness the unlock's first step reports an unreadable log; it never runs here.
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_HELP), any());
		world(net.runelite.api.WorldType.MEMBERS);
		unlockMessage();
		verify(chatNotifier).send(eq(ChatNotice.SYNC_HELP), any());
	}

	@Test
	public void theCollectionLogWalkRunsOnlyOnTheMainGame() throws Exception
	{
		// The walk reads the obtained counter every tick; nothing else here does without a local log.
		world(net.runelite.api.WorldType.SEASONAL);
		ticks(3);
		verify(client, never()).getVarpValue(ClogVarps.OBTAINED);
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		verify(client).getVarpValue(ClogVarps.OBTAINED);
	}

	private Object field(String name) throws Exception
	{
		Field field = KillClogPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}

	private void logout()
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(event);
	}

	private void configChanged(String key)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup("killclog");
		event.setKey(key);
		plugin.onConfigChanged(event);
	}

	/** Pump the executor, the client thread and the EDT until nothing is left to run. */
	private void settle() throws Exception
	{
		while (executor.live() > 0 || !clientQueue.isEmpty())
		{
			executor.runAll();
			while (!clientQueue.isEmpty())
			{
				clientQueue.poll().run();
			}
			drainEdt();
		}
		drainEdt();
	}

	private static void drainEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private Object dependency(Field field)
	{
		switch (field.getName())
		{
			case "client": return client;
			case "config": return config;
			case "panel": return panel;
			case "clientThread": return clientThread;
			case "localClogCache": return localClogCache;
			case "syncService": return syncService;
			case "profileAppearanceService": return appearance;
			case "executor": return executor;
			case "chatNotifier": return chatNotifier;
			case "configManager": return configManager;
			case "menuManager":
				Provider<MenuManager> menus = mock(Provider.class);
				when(menus.get()).thenReturn(mock(MenuManager.class));
				return menus;
			default: return mock(field.getType());
		}
	}

	/** Records scheduled and executed tasks; nothing runs until the test pumps it. */
	private static final class QueuedScheduler extends ScheduledThreadPoolExecutor
	{
		private final List<Task> tasks = new ArrayList<>();
		private long lastDelayMs = -1;

		QueuedScheduler()
		{
			super(1);
		}

		@Override
		public void execute(Runnable command)
		{
			tasks.add(new Task(command));
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			lastDelayMs = unit.toMillis(delay);
			Task task = new Task(command);
			tasks.add(task);
			return task;
		}

		int live()
		{
			int count = 0;
			for (Task task : tasks)
			{
				if (task.live())
				{
					count++;
				}
			}
			return count;
		}

		long lastDelayMs()
		{
			return lastDelayMs;
		}

		/** Run every live task in queue order, including any queued while running. */
		void runAll()
		{
			for (int i = 0; i < tasks.size(); i++)
			{
				Task task = tasks.get(i);
				if (task.live())
				{
					task.run();
				}
			}
		}
	}

	private static final class Task implements ScheduledFuture<Object>
	{
		private final Runnable command;
		private boolean cancelled;
		private boolean done;

		Task(Runnable command)
		{
			this.command = command;
		}

		boolean live()
		{
			return !cancelled && !done;
		}

		void run()
		{
			done = true;
			command.run();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			if (done)
			{
				return false;
			}
			cancelled = true;
			return true;
		}

		@Override
		public boolean isCancelled()
		{
			return cancelled;
		}

		@Override
		public boolean isDone()
		{
			return done || cancelled;
		}

		@Override
		public Object get()
		{
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
		{
			return null;
		}

		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}
	}
}
