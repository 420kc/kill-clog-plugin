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
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
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
	private java.util.function.BiFunction<String, String, String> selfPb;

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
		when(localClogCache.folder()).thenReturn(CompletableFuture.completedFuture(null));
		when(syncService.syncCollectionLog(any(), anyLong(), any(), any(), any(), anyLong(), any(), anyInt(), any(), any()))
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
		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.function.BiFunction<String, String, String>> pbReader =
			ArgumentCaptor.forClass(java.util.function.BiFunction.class);
		verify(panel).setSelfPb(pbReader.capture());
		selfPb = pbReader.getValue();
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
		// The press reads its game on the client thread before any timer exists.
		assertEquals(0, executor.live());
		runClient();
		assertEquals(1, executor.live());
		settle();
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Publishing collection log...");
		verify(panel).showSyncProgress(true, "publishing...", false);
		assertEquals(1, syncs.size());
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), any(), any(), any(), eq(7L), any(), eq(0),
			eq(localClogCache), eq("main"));

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced 12 items"));
		settle();
		verify(panel).showSyncResult(true, true, "Synced 12 items");
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Synced 12 items");
	}

	@Test
	public void aPressReadsItsGameOnTheClientThreadNotThePanels() throws Exception
	{
		clearInvocations(client);
		syncHandler.run();
		verify(client, never()).getWorldType();
		verify(client, never()).getGameState();
		runClient();
		verify(client).getWorldType();
		assertEquals(1, executor.live());
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
	public void aPressQueuedBeforeALogoutNeverPushesTheNextAccountsLog() throws Exception
	{
		// The press is still waiting on the client thread when the account logs out and another logs in.
		syncHandler.run();
		gameState = GameState.LOGIN_SCREEN;
		logout();
		epoch = 8;
		gameState = GameState.LOGGED_IN;
		Player next = mock(Player.class);
		when(next.getName()).thenReturn("Next account");
		when(client.getLocalPlayer()).thenReturn(next);
		when(client.getAccountHash()).thenReturn(HASH + 1);
		settle();
		assertTrue(syncs.isEmpty());
	}

	@Test
	public void logoutBeforeTheClientHopSilencesTheAttempt() throws Exception
	{
		syncHandler.run();
		runClient();
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
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), any(), any(), any(), eq(8L), any(), anyInt(), any(), any());
	}

	@Test
	public void sessionChangeBeforeTheTimerFiresDropsThePush() throws Exception
	{
		syncHandler.run();
		runClient();
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
		verify(panel, never()).showCharacterPublishStatus(eq(PublicationCoordinator.CHARACTER_FAILED_STATUS),
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
		verify(panel).showCharacterPublishStatus(PublicationCoordinator.CHARACTER_RENDERING_STATUS, false, false, null);

		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PUBLISHED, "ok"));
		settle();
		verify(panel).showCharacterPublishStatus(PublicationCoordinator.CHARACTER_PUBLISHED_STATUS, true, true, "ok");

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
		verify(panel, times(3)).showCharacterPublishStatus(PublicationCoordinator.CHARACTER_RENDERING_STATUS, false, false, null);

		publishes.get(1).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, "still"));
		settle();
		verify(panel).showCharacterPublishStatus(PublicationCoordinator.CHARACTER_FAILED_STATUS, false, true, "still");
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
		verify(panel).showCharacterPublishStatus(PublicationCoordinator.CHARACTER_FAILED_STATUS, false, true, null);
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
		verify(panel, never()).showCharacterPublishStatus(eq(PublicationCoordinator.CHARACTER_FAILED_STATUS), anyBoolean(), anyBoolean(), any());
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
		verify(panel, never()).showCharacterPublishStatus(eq(PublicationCoordinator.CHARACTER_PUBLISHED_STATUS), anyBoolean(), anyBoolean(), any());

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
		runClient();
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
		verify(syncService, times(call)).syncCollectionLog(any(), anyLong(), type.capture(), any(), any(), anyLong(), any(), anyInt(),
			any(), any());
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
		verify(caCache, never()).cacheResult(any(), anyLong(), any());
		ticks(1);
		verify(localClogCache).updateTotalsUpward(RSN, 5, 10);
		verify(caCache).cacheResult(eq(RSN), anyLong(), any());
		world(net.runelite.api.WorldType.SEASONAL);
		varp(ClogVarps.OBTAINED);
		ticks(20);
		verify(localClogCache).updateTotalsUpward(RSN, 5, 10);
		verify(caCache).cacheResult(eq(RSN), anyLong(), any());
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
	public void anUnknownWorldsUnlockReachesNoLog() throws Exception
	{
		LiveClogSync live = mock(LiveClogSync.class);
		replace("liveClogSync", live);
		world(net.runelite.api.WorldType.SEASONAL);
		unlockMessage();
		verify(live, never()).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), anyBoolean());
		world(net.runelite.api.WorldType.MEMBERS);
		unlockMessage();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(localClogCache), any(), any(), eq(false));
		ticks(10);
		unlockMessage();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(localClogCache), any(), any(), eq(true));
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

	/** The server announces a League, and the player is on one of its worlds. */
	private LocalClogCache announceLeague() throws Exception
	{
		when(((KillclogService) field("killclogService")).activeLeague()).thenReturn("demonic-pacts");
		LocalClogCache league = mock(LocalClogCache.class);
		when(league.currentSessionEpoch()).thenReturn(3L);
		when(league.activate(any(), anyLong())).thenReturn(true);
		plugin.setLeagueCacheFactory(id ->
		{
			assertEquals("demonic-pacts", id);
			return league;
		});
		world(net.runelite.api.WorldType.SEASONAL, net.runelite.api.WorldType.MEMBERS);
		return league;
	}

	@Test
	public void theAnnouncedLeagueSyncsItsOwnLogAndPbsToItsOwnPath() throws Exception
	{
		LocalClogCache league = announceLeague();
		when(((KillclogService) field("killclogService")).leagueProfileType("demonic-pacts")).thenReturn("DEMONIC_PACTS_LEAGUE");
		assertEquals(Double.valueOf(9.6), leaguePbs(league).get("Zulrah"));
		publishHandler.run();
		settle();
		assertTrue("a League appearance never publishes", publishes.isEmpty());
	}

	@Test
	public void aLeagueSyncScheduledBeforeAHopStillSyncsThatLeague() throws Exception
	{
		LocalClogCache league = announceLeague();
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(10);
		settle();
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), org.mockito.ArgumentMatchers.isNull(), any(), any(),
			eq(3L), any(), anyInt(), eq(league), eq("demonic-pacts"));
		verify(syncService, never()).syncCollectionLog(any(), anyLong(), any(), any(), any(), anyLong(), any(), anyInt(),
			eq(localClogCache), eq("main"));
	}

	@Test
	public void aLeaguesRetryAfterAHopStaysWithThatLeague() throws Exception
	{
		LocalClogCache league = announceLeague();
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(10);
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Another sync holds the lock", true, 5));
		settle();
		assertEquals(2, syncs.size());
		verify(syncService, times(2)).syncCollectionLog(eq(RSN), eq(HASH), org.mockito.ArgumentMatchers.isNull(), any(),
			any(), eq(3L), any(), anyInt(), eq(league), eq("demonic-pacts"));
	}

	@Test
	public void aLeagueClickQueuedBehindASyncStaysWithThatLeague() throws Exception
	{
		LocalClogCache league = announceLeague();
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		syncHandler.run();
		settle();
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(10);
		// The main world's own catch-up queues behind the same flight.
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "First"));
		settle();
		assertEquals(2, syncs.size());
		verify(syncService, times(2)).syncCollectionLog(eq(RSN), eq(HASH), org.mockito.ArgumentMatchers.isNull(), any(),
			any(), eq(3L), any(), anyInt(), eq(league), eq("demonic-pacts"));
	}

	@Test
	public void aSyncFiringBeforeTheNewWorldSettlesTriesOnceMore() throws Exception
	{
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		world(net.runelite.api.WorldType.SEASONAL);
		fireOnce();
		assertTrue("an unknown world syncs nothing", syncs.isEmpty());
		assertEquals("its own catch-up gets one more try", 1, executor.live());
		fireOnce();
		assertTrue(syncs.isEmpty());
		assertEquals("and no more after that", 0, executor.live());
	}

	@Test
	public void aLeagueRuneLiteDoesNotKnowYetSendsNoPbs() throws Exception
	{
		LocalClogCache league = announceLeague();
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.earlier");
		assertTrue(leaguePbs(league).isEmpty());
	}

	/** Main, an earlier League and this League each hold a Zulrah PB; returns what the League sync sent. */
	@SuppressWarnings("unchecked")
	private java.util.Map<String, Double> leaguePbs(LocalClogCache league) throws Exception
	{
		recordPbs();
		syncHandler.run();
		settle();
		ArgumentCaptor<java.util.Map<String, Double>> pbs = ArgumentCaptor.forClass(java.util.Map.class);
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), org.mockito.ArgumentMatchers.isNull(), pbs.capture(), any(),
			eq(3L), any(), anyInt(), eq(league), eq("demonic-pacts"));
		return pbs.getValue();
	}

	private void recordPbs()
	{
		when(configManager.getRSProfiles()).thenReturn(List.of(
			new RuneScapeProfile(RSN, RuneScapeProfileType.STANDARD, HASH, "main"),
			new RuneScapeProfile(RSN, RuneScapeProfileType.RAGING_ECHOES_LEAGUE, HASH, "earlier"),
			new RuneScapeProfile(RSN, RuneScapeProfileType.DEMONIC_PACTS_LEAGUE, HASH, "league")));
		String[][] recorded = {{"rsprofile.main", "5.0"}, {"rsprofile.earlier", "7.0"}, {"rsprofile.league", "9.6"}};
		for (String[] pb : recorded)
		{
			when(configManager.getConfiguration(eq("personalbest"), eq(pb[0]), eq("zulrah"),
				eq((java.lang.reflect.Type) double.class))).thenReturn(Double.valueOf(pb[1]));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void theLadderPayloadKeepsTeamSizesSplitAndNamesEachBestsLane() throws Exception
	{
		when(configManager.getRSProfiles()).thenReturn(List.of(
			new RuneScapeProfile(RSN, RuneScapeProfileType.STANDARD, HASH, "main"),
			new RuneScapeProfile(RSN, RuneScapeProfileType.STANDARD, HASH, "frag"),
			new RuneScapeProfile("Someone", RuneScapeProfileType.STANDARD, 99L, "other")));
		Object[][] stored = {
			{"personalbest", "rsprofile.main", "zulrah", 60.0},
			{"personalbest", "rsprofile.frag", "zulrah", 55.5},
			{"personalbest", "rsprofile.other", "zulrah", 1.0},
			{"personalbest", "rsprofile.main", "chambers of xeric solo", 1500.0},
			{"personalbest", "rsprofile.main", "chambers of xeric 5 players", 1200.0},
			{"killclog", "rsprofile.main", "advlogpb.chambers of xeric solo", 1400.0},
			{"killclog", "rsprofile.frag", "advlogpb.chambers of xeric 5 players", 1200.0},
			{"personalbest", "rsprofile.main", "chambers of xeric challenge mode 3 players", 2000.0},
			{"killclog", "rsprofile.main", "advlogpb.vorkath", 70.0},
			{"personalbest", "rsprofile.frag", "leviathan", 150.0},
			{"killclog", "rsprofile.other", "advlogpb.leviathan", 2.0}};
		for (Object[] pb : stored)
		{
			when(configManager.getConfiguration(eq((String) pb[0]), eq((String) pb[1]), eq((String) pb[2]),
				eq((java.lang.reflect.Type) double.class))).thenReturn((Double) pb[3]);
		}
		syncHandler.run();
		settle();
		ArgumentCaptor<java.util.Map<String, Double>> collapsed = ArgumentCaptor.forClass(java.util.Map.class);
		ArgumentCaptor<java.util.Map<String, SyncService.DetailedPb>> detailed = ArgumentCaptor.forClass(java.util.Map.class);
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), any(), collapsed.capture(), detailed.capture(),
			eq(7L), any(), anyInt(), eq(localClogCache), eq("main"));

		java.util.Map<String, String> got = new java.util.TreeMap<>();
		detailed.getValue().forEach((key, pb) -> got.put(key, pb.seconds + " " + pb.source));
		java.util.Map<String, String> want = new java.util.TreeMap<>();
		want.put("zulrah", "55.5 store");
		want.put("chambers of xeric solo", "1400.0 advlog");
		want.put("chambers of xeric 5 players", "1200.0 store");
		want.put("chambers of xeric challenge mode 3 players", "2000.0 store");
		want.put("vorkath", "70.0 advlog");
		want.put("the leviathan", "150.0 store");
		assertEquals("faster lane wins, a tie keeps the store, another account never counts", want, got);
		assertEquals(Double.valueOf(55.5), collapsed.getValue().get("Zulrah"));
		assertEquals(Double.valueOf(1200.0), collapsed.getValue().get("Chambers of Xeric"));
	}

	@Test
	public void yourPbsComeFromTheViewedGamesOwnProfiles() throws Exception
	{
		KillclogService service = (KillclogService) field("killclogService");
		ticks(1);
		recordPbs();
		when(service.leagueProfileType("demonic-pacts")).thenReturn("DEMONIC_PACTS_LEAGUE");
		assertEquals(PersonalBests.formatSeconds(5.0), selfPb.apply(null, "Zulrah"));
		assertEquals(PersonalBests.formatSeconds(9.6), selfPb.apply("demonic-pacts", "Zulrah"));
		when(service.leagueProfileType("demonic-pacts")).thenReturn(null);
		org.junit.Assert.assertNull("a League RuneLite does not know yet shows no PB", selfPb.apply("demonic-pacts", "Zulrah"));
	}

	@Test
	public void yourPbsFollowTheLoggedInAccount() throws Exception
	{
		recordPbs();
		// The login event can come before the name; the hash still arrives with the next tick.
		ticks(1);
		assertEquals(PersonalBests.formatSeconds(5.0), selfPb.apply(null, "Zulrah"));
		gameState = GameState.LOGIN_SCREEN;
		logout();
		org.junit.Assert.assertNull("no account, no PB", selfPb.apply(null, "Zulrah"));
		gameState = GameState.LOGGED_IN;
		when(client.getAccountHash()).thenReturn(HASH + 1);
		ticks(1);
		org.junit.Assert.assertNull("another account never shows these", selfPb.apply(null, "Zulrah"));
	}

	@Test
	public void aLogoutDropsTheCollectionLogsStructureAndReadsItAgainAtOnce() throws Exception
	{
		ClogIndex index = (ClogIndex) field("clogIndex");
		index.publishForTest(java.util.Collections.emptyMap(), java.util.Collections.emptyMap());
		clearInvocations(client);
		gameState = GameState.LOGIN_SCREEN;
		logout();
		// Read again even though one was held: the logout dropped it, and lookups at the login screen need it.
		verify(client).getEnum(ClogIndex.ENUM_CLOG_TABS);
	}

	@Test
	public void startingAtTheLoginScreenReadsTheCollectionLogsStructure() throws Exception
	{
		plugin.shutDown();
		gameState = GameState.LOGIN_SCREEN;
		clearInvocations(client);
		plugin.startUp();
		runClient();
		verify(client).getEnum(ClogIndex.ENUM_CLOG_TABS);
	}

	@Test
	public void aLogoutFromAMainWorldEndsAFlip() throws Exception
	{
		when(((KillclogService) field("killclogService")).activeLeague()).thenReturn("demonic-pacts");
		ticks(1);
		drainEdt();
		verify(panel).followWorld(null, null, "demonic-pacts");
		gameState = GameState.LOGIN_SCREEN;
		logout();
		drainEdt();
		verify(panel, times(2)).followWorld(null, null, "demonic-pacts");
	}

	@Test
	public void aRestartHandsThePanelTheWorldsGameAgain() throws Exception
	{
		LocalClogCache league = announceLeague();
		ticks(1);
		drainEdt();
		verify(panel).followWorld("demonic-pacts", league, "demonic-pacts");
		plugin.shutDown();
		LocalClogCache reopened = mock(LocalClogCache.class);
		plugin.setLeagueCacheFactory(id -> reopened);
		plugin.startUp();
		ticks(1);
		drainEdt();
		verify(panel).followWorld("demonic-pacts", reopened, "demonic-pacts");
	}

	@Test
	public void aWorldWithNoKnownGameStillShowsYouAtLogin() throws Exception
	{
		world(net.runelite.api.WorldType.DEADMAN);
		when(localClogCache.setActivePlayer(RSN)).thenReturn(true);
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);
		ticks(1);
		drainEdt();
		verify(panel).doLookup();
	}

	@Test
	public void theLoginLookupWaitsForTheLeaguesOwnLog() throws Exception
	{
		LocalClogCache league = announceLeague();
		when(league.setActivePlayer(RSN)).thenReturn(true);
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);
		ticks(1);
		drainEdt();
		verify(panel).doLookup();
	}

	@Test
	public void aLeagueWorldPointsThePanelAndChatAtItsLeague() throws Exception
	{
		KillClogChatCommand chat = (KillClogChatCommand) field("kclogCommand");
		ticks(1);
		drainEdt();
		verify(panel).followWorld(null, null, null);
		LocalClogCache league = announceLeague();
		ticks(3);
		drainEdt();
		verify(panel).followWorld("demonic-pacts", league, "demonic-pacts");
		verify(chat).readLeague("demonic-pacts", league);
		net.runelite.api.events.ChatMessage kc = new net.runelite.api.events.ChatMessage();
		kc.setType(net.runelite.api.ChatMessageType.PUBLICCHAT);
		kc.setMessage("!kc twisted bow");
		plugin.onChatMessage(kc);
		verify(chat).handleKcItem(eq(kc), any(), eq(league));
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		drainEdt();
		// A main world while a League runs: the panel reads main, and can switch to the League.
		verify(panel).followWorld(null, null, "demonic-pacts");
		verify(chat, times(2)).readLeague(null, null);
		world(net.runelite.api.WorldType.SEASONAL, net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		gameState = GameState.LOGIN_SCREEN;
		logout();
		drainEdt();
		verify(panel, times(2)).followWorld(null, null, "demonic-pacts");
		when(((KillclogService) field("killclogService")).activeLeague()).thenReturn(null);
		gameState = GameState.LOGGED_IN;
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		drainEdt();
		verify(panel, times(2)).followWorld(null, null, null);
	}

	@Test
	public void aLeagueCaptureSchedulesItsOwnSync() throws Exception
	{
		LocalClogCache league = announceLeague();
		ticks(1);
		ArgumentCaptor<Runnable> listener = ArgumentCaptor.forClass(Runnable.class);
		verify(league).setFirstPartyChangedListener(listener.capture());
		listener.getValue().run();
		assertEquals("an empty capture schedules nothing", 0, executor.live());
		when(league.hasFirstPartyDataForActive()).thenReturn(true);
		listener.getValue().run();
		verify(panel).setSyncArrowHasData(true);
		assertEquals(1, executor.live());
		settle();
		verify(syncService).syncCollectionLog(eq(RSN), eq(HASH), org.mockito.ArgumentMatchers.isNull(), any(), any(),
			eq(3L), any(), anyInt(), eq(league), eq("demonic-pacts"));
	}

	@Test
	public void theLeagueStoreChecksWhoseItIsAndEndsWithTheSession() throws Exception
	{
		LocalClogCache league = announceLeague();
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);
		ticks(1);
		settle();
		verify((KillclogService) field("killclogService")).refreshIndex();
		verify(league, org.mockito.Mockito.atLeastOnce()).activate(RSN, HASH);
		verify(league, org.mockito.Mockito.atLeastOnce()).setActivePlayer(RSN);
		verify(localClogCache, never()).activate(any(), anyLong());
		logout();
		verify(league).onSessionEnded();
	}

	@Test
	public void unlocksWalksAndSearchesLandInTheWorldsOwnLog() throws Exception
	{
		LiveClogSync live = mock(LiveClogSync.class);
		ManualClogSync walk = mock(ManualClogSync.class);
		replace("liveClogSync", live);
		replace("manualClogSync", walk);
		LocalClogCache league = announceLeague();
		unlockMessage();
		ticks(1);
		searchClick();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(league), any(), any(), anyBoolean());
		verify(walk).onGameTick(any(), any(), eq(league), any(), any(), any());
		verify(walk).onCollectionLogSearch(any(), any(), eq(league), any());
		verify(live, never()).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(localClogCache), any(), any(), anyBoolean());
		verify(walk, never()).onGameTick(any(), any(), eq(localClogCache), any(), any(), any());
		verify(walk, never()).onCollectionLogSearch(any(), any(), eq(localClogCache), any());
		world(net.runelite.api.WorldType.MEMBERS);
		unlockMessage();
		ticks(1);
		searchClick();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(localClogCache), any(), any(), anyBoolean());
		verify(walk).onGameTick(any(), any(), eq(localClogCache), any(), any(), any());
		verify(walk).onCollectionLogSearch(any(), any(), eq(localClogCache), any());
	}

	@Test
	public void aLeagueWorldSettlesAndReadsItsOwnCounters() throws Exception
	{
		LiveClogSync live = mock(LiveClogSync.class);
		replace("liveClogSync", live);
		LocalClogCache league = announceLeague();
		ticks(9);
		unlockMessage();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(league), any(), any(), eq(false));
		ticks(1);
		unlockMessage();
		verify(live).handleUnlock(any(), anyInt(), anyInt(), any(), any(), any(), eq(league), any(), any(), eq(true));
	}

	@Test
	public void aNewLeagueOpensItsOwnStoreAndClosesTheLast() throws Exception
	{
		ManualClogSync walk = mock(ManualClogSync.class);
		replace("manualClogSync", walk);
		KillclogService service = (KillclogService) field("killclogService");
		java.util.Map<String, LocalClogCache> stores = new java.util.HashMap<>();
		plugin.setLeagueCacheFactory(id -> stores.computeIfAbsent(id, key -> mock(LocalClogCache.class)));
		when(service.activeLeague()).thenReturn("raging-echoes");
		world(net.runelite.api.WorldType.SEASONAL, net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		when(service.activeLeague()).thenReturn("demonic-pacts");
		ticks(1);
		verify(stores.get("raging-echoes")).close();
		verify(walk).onGameTick(any(), any(), eq(stores.get("demonic-pacts")), any(), any(), any());
	}

	@Test
	public void aRegionLoadKeepsTheWorldSettledAndAHopDoesNot() throws Exception
	{
		ticks(10);
		GameStateChanged loading = new GameStateChanged();
		loading.setGameState(GameState.LOADING);
		plugin.onGameStateChanged(loading);
		syncHandler.run();
		settle();
		assertEquals(AccountType.REGULAR, sentType(1));
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		GameStateChanged hop = new GameStateChanged();
		hop.setGameState(GameState.HOPPING);
		plugin.onGameStateChanged(hop);
		syncHandler.run();
		settle();
		org.junit.Assert.assertNull(sentType(2));
	}

	private void searchClick()
	{
		net.runelite.api.MenuEntry entry = mock(net.runelite.api.MenuEntry.class);
		when(entry.getOption()).thenReturn("Search");
		when(entry.getParam1()).thenReturn(KillClogPlugin.CLOG_INTERFACE << 16);
		plugin.onMenuOptionClicked(new net.runelite.api.events.MenuOptionClicked(entry));
	}

	private void replace(String name, Object value) throws Exception
	{
		Field field = KillClogPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
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
	private void runClient()
	{
		while (!clientQueue.isEmpty())
		{
			clientQueue.poll().run();
		}
	}

	/** One pass: the timers due now, then what they hand the client thread. */
	private void fireOnce() throws Exception
	{
		executor.runAll();
		while (!clientQueue.isEmpty())
		{
			clientQueue.poll().run();
		}
		drainEdt();
	}

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
