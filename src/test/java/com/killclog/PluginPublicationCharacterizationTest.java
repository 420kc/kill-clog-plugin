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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
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
	// Startup sees no session, as before; the player is then on a main-game members world.
	private GameState gameState;
	private KillClogPlugin plugin;
	private Runnable syncHandler;
	private Runnable publishHandler;
	private Runnable deleteHandler;
	private final List<CompletableFuture<ProfileAppearanceService.PublishResult>> deletes = new ArrayList<>();
	private Runnable captureListener;
	private java.util.function.Function<String, java.util.function.UnaryOperator<String>> selfPbs;
	private java.util.function.BiFunction<String, String, String> selfPb;

	private static final class Settings implements KillClogConfig
	{
		boolean sync = true;
		boolean character = true;
		boolean automatic = true;

		@Override
		public boolean killclogSync()
		{
			return sync;
		}

		@Override
		public boolean automaticSync()
		{
			return automatic;
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
		when(localClogCache.folder()).thenReturn(CompletableFuture.completedFuture(null));
		when(syncService.syncCollectionLog(any(),  any(),  any(),  any(),  any(),  anyInt(),  any(),  any(),  anyBoolean(),  any()))
			.thenAnswer(invocation ->
			{
				CompletableFuture<SyncService.SyncResult> sync = new CompletableFuture<>();
				syncs.add(sync);
				((Runnable) invocation.getArgument(9)).run();
				return sync;
			});
		when(appearance.publishCurrent(any(), any())).thenAnswer(invocation ->
		{
			CompletableFuture<ProfileAppearanceService.PublishResult> publish = new CompletableFuture<>();
			publishes.add(publish);
			return publish;
		});
		when(appearance.deleteCurrent(any(), any())).thenAnswer(invocation ->
		{
			CompletableFuture<ProfileAppearanceService.PublishResult> delete = new CompletableFuture<>();
			deletes.add(delete);
			return delete;
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
		// The game has named the player and the account: the tracker makes the login.
		((SessionTracker) field("sessions")).update(client);

		ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setKillclogSyncHandler(handler.capture());
		syncHandler = handler.getValue();
		handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setCharacterPublishHandler(handler.capture());
		publishHandler = handler.getValue();
		handler = ArgumentCaptor.forClass(Runnable.class);
		verify(panel).setCharacterDeleteHandler(handler.capture());
		deleteHandler = handler.getValue();
		handler = ArgumentCaptor.forClass(Runnable.class);
		verify(localClogCache).setFirstPartyChangedListener(handler.capture());
		captureListener = handler.getValue();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.function.Function<String, java.util.function.UnaryOperator<String>>> pbReader =
			ArgumentCaptor.forClass(java.util.function.Function.class);
		verify(panel).setSelfPbs(pbReader.capture());
		selfPbs = pbReader.getValue();
		selfPb = (league, boss) -> selfPbs.apply(league).apply(boss);
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
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Syncing collection log...");
		verify(panel).showSyncProgress(true, "syncing...", false);
		assertEquals(1, syncs.size());
		verify(syncService).syncCollectionLog(you(),  any(),  any(),  any(),  any(),  eq(1),
			eq(localClogCache),  eq("main"),  anyBoolean(),  any());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced 12 items"));
		settle();
		verify(panel).showSyncResult(true, true, "Synced 12 items");
		verify(chatNotifier).send(ChatNotice.SYNC_RESULT, "Synced 12 items");
	}

	@Test
	public void withAutomaticSyncOffOnlyAClickOrACharacterSends() throws Exception
	{
		config.automatic = false;
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		assertEquals(0, executor.live());
		settle();
		assertEquals(0, syncs.size());

		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();

		// A character publish that needs the profile first still gets its own sync, then publishes.
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void anAutomaticSyncKillclogAlreadyHoldsFlashesNothing() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		syncs.get(0).complete(SyncService.UNCHANGED);
		settle();
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_RESULT), any());
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
		verify(panel, times(2)).showSyncProgress(true, "syncing...", false);
		verify(panel).showSyncResult(true, true, "First");
		verify(panel).showSyncResult(true, true, "Second");
	}

	@Test
	public void captureDebounceCoalescesAndStaysQuiet() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		captureListener.run();
		verify(panel, times(2)).setFirstParty(PanelStatusRow.FirstParty.READY);
		assertEquals(1, executor.live());
		assertEquals(10_000L, executor.lastDelayMs());

		settle();
		verify(chatNotifier, never()).send(eq(ChatNotice.SYNC_RESULT), startsWith("Syncing"));
		verify(panel).showSyncProgress(false, "syncing...", false);
		assertEquals(1, syncs.size());

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel).showSyncResult(false, true, "Synced");
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, "Synced");
	}

	@Test
	public void aLogWalkSendsOnceItsCapturesPauseAndAtMostAMinuteAfterItsFirst() throws Exception
	{
		long[] now = {0};
		((PublicationCoordinator) field("publication")).clock = () -> now[0];
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		assertEquals(10_000L, executor.lastDelayMs());
		// The walk goes on: each page starts the quiet time again, and only one push waits.
		now[0] = 8_000;
		captureListener.run();
		assertEquals(10_000L, executor.lastDelayMs());
		assertEquals(1, executor.live());
		// Near the minute, what's left of it.
		now[0] = 55_000;
		captureListener.run();
		assertEquals(5_000L, executor.lastDelayMs());
		// Past it, the waiting push keeps its time.
		now[0] = 61_000;
		captureListener.run();
		assertEquals(5_000L, executor.lastDelayMs());
		assertEquals(1, executor.live());
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void anAccountTheServerTurnsAwayStopsSendingUntilAClick() throws Exception
	{
		String optedOut = "This account has opted out of Kill Clog sync.";
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		syncs.get(0).complete(SyncService.SyncResult.stopping(optedOut));
		settle();
		verify(chatNotifier, times(1)).send(ChatNotice.SYNC_RESULT, optedOut);
		// The rest of the session's captures send nothing, and say nothing.
		captureListener.run();
		captureListener.run();
		assertEquals(0, executor.live());
		settle();
		assertEquals(1, syncs.size());
		verify(chatNotifier, times(1)).send(ChatNotice.SYNC_RESULT, optedOut);
		// A click asks again.
		syncHandler.run();
		runClient();
		settle();
		assertEquals(2, syncs.size());
	}

	@Test
	public void aPressQueuedBeforeALogoutNeverPushesTheNextAccountsLog() throws Exception
	{
		// The press is still waiting on the client thread when the account logs out and another logs in.
		syncHandler.run();
		gameState = GameState.LOGIN_SCREEN;
		logout();
		gameState = GameState.LOGGED_IN;
		Player next = mock(Player.class);
		when(next.getName()).thenReturn("Next account");
		when(client.getLocalPlayer()).thenReturn(next);
		when(client.getAccountHash()).thenReturn(HASH + 1);
		((SessionTracker) field("sessions")).update(client);
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
		settle();
		assertTrue(syncs.isEmpty());
		verify(panel, never()).showSyncProgress(anyBoolean(), any(), anyBoolean());
		verify(panel).setFirstParty(PanelStatusRow.FirstParty.HIDDEN);
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
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel, never()).showSyncResult(anyBoolean(), anyBoolean(), any());
		verify(chatNotifier, never()).send(any(), any());
		login();

		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(2, syncs.size());
		verify(syncService, times(2)).syncCollectionLog(you(),  any(),  any(),  any(),  any(),  anyInt(),  any(),  any(),  anyBoolean(),  any());
	}

	@Test
	public void sessionChangeBeforeTheTimerFiresDropsThePush() throws Exception
	{
		syncHandler.run();
		runClient();
		logout();
		login();
		settle();
		assertTrue(syncs.isEmpty());
		verify(panel, never()).showSyncProgress(anyBoolean(), any(), anyBoolean());

		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void syncsFollowersReadOffWithItAndTurnOnWithIt() throws Exception
	{
		config.sync = false;
		configChanged("killclogSync");
		verify(configManager).setConfiguration("killclog", "automaticSync", false);
		verify(configManager).unsetConfiguration("killclog", "characterModel");
		config.automatic = false;
		config.character = false;

		// RuneLite can't gray a setting out: a follower ticked while Sync is off doesn't stay ticked.
		config.automatic = true;
		configChanged("automaticSync");
		verify(configManager, times(2)).setConfiguration("killclog", "automaticSync", false);
		config.automatic = false;

		config.sync = true;
		configChanged("killclogSync");
		verify(configManager).unsetConfiguration("killclog", "automaticSync");
		verify(configManager).setConfiguration("killclog", "characterModel", true);
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
			"Collection log sync failed. Try again later.", true, 20);
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

		String failed = "Collection log sync failed. Try again later.";
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

		String failed = "Collection log sync failed. Try again later.";
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
		verify(panel, never()).showCharacterPublishStatus(eq(CharacterStatus.FAILED),
			anyBoolean(), any());

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
		verify(appearance).publishCurrent(you(), any());
		verify(panel).showCharacterPublishStatus(CharacterStatus.RENDERING, false, null);

		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PUBLISHED, "ok"));
		settle();
		verify(panel).showCharacterPublishStatus(CharacterStatus.PUBLISHED, true, "ok");

		publishHandler.run();
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void aCharacterRequestIsForTheLoginItBeganInAndStandsDownWhenItEnds() throws Exception
	{
		publishHandler.run();
		settle();
		ArgumentCaptor<ProfileAppearanceService.Authorization> stillWanted =
			ArgumentCaptor.forClass(ProfileAppearanceService.Authorization.class);
		verify(appearance).publishCurrent(you(), stillWanted.capture());
		assertTrue(stillWanted.getValue().getAsBoolean());

		// Logged out, then back in on the same account: a new login, and the request from the old one never acts.
		logout();
		login();
		assertFalse(stillWanted.getValue().getAsBoolean());
	}

	@Test
	public void aShiftClickDeletesAndAPublishWaitsItsTurn() throws Exception
	{
		deleteHandler.run();
		publishHandler.run();
		settle();
		assertEquals(1, deletes.size());
		assertEquals(0, publishes.size());
		verify(appearance).deleteCurrent(you(), any());
		verify(panel).showCharacterPublishStatus(CharacterStatus.DELETING, false, null);

		deletes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.DELETED, null));
		settle();
		verify(panel).showCharacterPublishStatus(CharacterStatus.DELETED, true, null);

		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());
		assertEquals(1, deletes.size());
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
		verify(panel, times(3)).showCharacterPublishStatus(CharacterStatus.RENDERING, false, null);

		publishes.get(1).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, "still"));
		settle();
		verify(panel).showCharacterPublishStatus(CharacterStatus.FAILED, true, "still");
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
		verify(panel).showCharacterPublishStatus(CharacterStatus.FAILED, true, null);
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
		settle();
		verify(panel).showCharacterPublishStatus(CharacterStatus.CLEAR, false, null);

		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel, never()).showCharacterPublishStatus(eq(CharacterStatus.FAILED), anyBoolean(), any());
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
		verify(panel).showCharacterPublishStatus(CharacterStatus.CLEAR, false, null);
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PUBLISHED, "late"));
		settle();
		verify(panel, never()).showCharacterPublishStatus(eq(CharacterStatus.PUBLISHED), anyBoolean(), any());

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
		// The re-enabled plugin's login init runs on the client thread before the next press.
		runClient();
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
	public void aReEnableKeepsTheRealAppearanceTheGameLastSent() throws Exception
	{
		plugin.shutDown();
		plugin.startUp();
		verify(appearance, never()).clearOriginalAppearance();
		// A logout still drops it.
		logout();
		verify(appearance).clearOriginalAppearance();
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
		verify(syncService, times(call)).syncCollectionLog(any(),  type.capture(),  any(),  any(),  any(),  anyInt(),
			any(),  any(),  anyBoolean(),  any());
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
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(5);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)).thenReturn(10);
		varp(VarPlayerID.COLLECTION_COUNT);
		ticks(9);
		verify(localClogCache, never()).updateTotalsUpward(any(), anyInt(), anyInt());
		verify(caCache, never()).cacheResult(any(), anyLong(), any());
		ticks(1);
		verify(localClogCache).updateTotalsUpward(RSN, 5, 10);
		verify(caCache).cacheResult(eq(RSN), anyLong(), any());
		world(net.runelite.api.WorldType.SEASONAL);
		varp(VarPlayerID.COLLECTION_COUNT);
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
	public void yourOwnChatNeverRerunsYourLookup() throws Exception
	{
		LiveClogSync live = mock(LiveClogSync.class);
		replace("liveClogSync", live);
		when(panel.getDisplayedRsn()).thenReturn(RSN);
		for (net.runelite.api.ChatMessageType type : new net.runelite.api.ChatMessageType[]{
			net.runelite.api.ChatMessageType.PUBLICCHAT, net.runelite.api.ChatMessageType.PRIVATECHATOUT})
		{
			net.runelite.api.events.ChatMessage said = new net.runelite.api.events.ChatMessage();
			said.setType(type);
			// A private message carries the name it went to.
			said.setName(type == net.runelite.api.ChatMessageType.PUBLICCHAT ? RSN : "Friend");
			said.setMessage("hello");
			plugin.onChatMessage(said);
		}
		settle();
		verify(panel, never()).doLookup();
		// A new collection log item still updates the panel, through the unlock.
		unlockMessage();
		verify(live).handleUnlock(eq("Twisted bow"), eq(-1), eq(-1), any(), any(), any(), eq(localClogCache), any(), any(),
			anyBoolean());
		verify(panel, never()).doLookup();
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
		verify(client, never()).getVarpValue(VarPlayerID.COLLECTION_COUNT);
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		verify(client).getVarpValue(VarPlayerID.COLLECTION_COUNT);
	}

	@Test
	public void aLoginStraightOntoALeagueWorldLeavesTheMainStoreToTheMainGame() throws Exception
	{
		logout();
		LocalClogCache league = announceLeague();
		loggedIn();
		ticks(1);
		verify(league, atLeastOnce()).serve(you());
		verify(localClogCache, never()).serve(you());

		// A hop to the main game: the main store takes up the same login.
		world(net.runelite.api.WorldType.MEMBERS);
		hop();
		ticks(1);
		verify(localClogCache, atLeastOnce()).serve(you());
	}

	@Test
	public void aCharacterRequestWithdrawnWhileItHandsOverToASyncNeverPublishesForTheNextLogin() throws Exception
	{
		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());

		// The answer "sync first" lands on another thread and passes its checks; then the player logs out and another
		// account logs in before it hands over.
		java.util.concurrent.CountDownLatch checked = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch resume = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		KillClogConfig held = mock(KillClogConfig.class);
		when(held.killclogSync()).thenReturn(true);
		when(held.automaticSync()).thenReturn(true);
		when(held.characterModel()).thenAnswer(invocation ->
		{
			if (!SwingUtilities.isEventDispatchThread() && reads.incrementAndGet() == 1)
			{
				checked.countDown();
				assertTrue(resume.await(5, TimeUnit.SECONDS));
			}
			return true;
		});
		Object publication = field("publication");
		Field config = PublicationCoordinator.class.getDeclaredField("config");
		config.setAccessible(true);
		config.set(publication, held);
		CompletableFuture<Void> answer = CompletableFuture.runAsync(() -> publishes.get(0).complete(
			new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null)));
		assertTrue(checked.await(5, TimeUnit.SECONDS));
		logout();
		Player next = mock(Player.class);
		when(next.getName()).thenReturn("Other");
		when(client.getLocalPlayer()).thenReturn(next);
		when(client.getAccountHash()).thenReturn(HASH + 1);
		login();
		resume.countDown();
		answer.get(5, TimeUnit.SECONDS);
		settle();
		for (CompletableFuture<SyncService.SyncResult> sync : new ArrayList<>(syncs))
		{
			sync.complete(new SyncService.SyncResult(true, false, "Synced"));
		}
		settle();
		verify(appearance, times(1)).publishCurrent(any(), any());
	}

	@Test
	public void aPublishRetriedAfterItsSyncNeverActsForTheNextLogin() throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(1, syncs.size());
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		// Only the delayed retry waits on the scheduler now.
		drainEdt();

		// The retry passes its own check on the scheduler's thread; then the player logs out and another logs in.
		java.util.concurrent.CountDownLatch checked = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch resume = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
		KillClogConfig held = mock(KillClogConfig.class);
		when(held.killclogSync()).thenReturn(true);
		when(held.automaticSync()).thenReturn(true);
		when(held.characterModel()).thenAnswer(invocation ->
		{
			if (!SwingUtilities.isEventDispatchThread() && first.compareAndSet(true, false))
			{
				checked.countDown();
				assertTrue(resume.await(5, TimeUnit.SECONDS));
			}
			return true;
		});
		Field config = PublicationCoordinator.class.getDeclaredField("config");
		config.setAccessible(true);
		config.set(field("publication"), held);
		CompletableFuture<Void> retry = CompletableFuture.runAsync(executor::runAll);
		try
		{
			assertTrue(checked.await(5, TimeUnit.SECONDS));
			logout();
			Player next = mock(Player.class);
			when(next.getName()).thenReturn("Other");
			when(client.getLocalPlayer()).thenReturn(next);
			when(client.getAccountHash()).thenReturn(HASH + 1);
			login();
		}
		finally
		{
			resume.countDown();
		}
		retry.get(5, TimeUnit.SECONDS);
		settle();
		verify(appearance, times(1)).publishCurrent(any(), any());
	}

	// Work begun in one login never acts for the next: each case holds the work between a check and what it allows,
	// while the player logs out and another account logs in.

	@Test
	public void aPublishPressedJustBeforeALogoutNeverActsForTheNextLogin() throws Exception
	{
		pressedThenNextAccount(publishHandler, true);
		assertEquals(0, publishes.size());
	}

	@Test
	public void aDeletePressedJustBeforeALogoutNeverActsForTheNextLogin() throws Exception
	{
		pressedThenNextAccount(deleteHandler, true);
		assertEquals(0, deletes.size());
	}

	@Test
	public void aSyncPressedJustBeforeALogoutNeverSyncsTheNextLogin() throws Exception
	{
		pressedThenNextAccount(syncHandler, false);
		assertEquals(0, syncs.size());
	}

	@Test
	public void anOptInJustBeforeALogoutNeverSyncsTheNextLogin() throws Exception
	{
		try (Hold hold = new Hold(1))
		{
			KillClogConfig held = held(hold, false);
			Field pluginConfig = KillClogPlugin.class.getDeclaredField("config");
			pluginConfig.setAccessible(true);
			pluginConfig.set(plugin, held);
			ConfigChanged optIn = new ConfigChanged();
			optIn.setGroup("killclog");
			optIn.setKey("killclogSync");
			CompletableFuture<Void> done = hold.onEdt(() -> plugin.onConfigChanged(optIn));
			hold.reached();
			nextAccount();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertEquals(0, syncs.size());
	}

	@Test
	public void aServerAdvisedRetryNeverSyncsTheNextLogin() throws Exception
	{
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		try (Hold hold = new Hold(1))
		{
			held(hold, false);
			CompletableFuture<Void> done = hold.onWorker(() ->
				syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 2)));
			hold.reached();
			nextAccount();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void aQueuedSyncNeverRunsForTheNextLogin() throws Exception
	{
		syncHandler.run();
		settle();
		syncHandler.run();
		settle();
		assertEquals("the second press waits behind the first", 1, syncs.size());
		try (Hold hold = new Hold(2))
		{
			held(hold, false);
			CompletableFuture<Void> done = hold.onWorker(() ->
				syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced")));
			hold.reached();
			nextAccount();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void aStaleCharacterAnswerNeverFreesTheNextLoginsRequest() throws Exception
	{
		publishHandler.run();
		settle();
		nextAccount();
		publishHandler.run();
		settle();
		assertEquals("the next login's own request goes", 2, publishes.size());
		// The old login's answer lands after the new request began: the new one still holds the one-at-a-time slot.
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PUBLISHED, null));
		settle();
		publishHandler.run();
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void aSyncsChatLineNeverReachesTheNextLogin() throws Exception
	{
		syncHandler.run();
		settle();
		clearInvocations(chatNotifier);
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		nextAccount();
		settle();
		verify(chatNotifier, never()).send(ChatNotice.SYNC_RESULT, "Synced");
	}

	@Test
	public void turningAutomaticSyncOffTakesDownTheSyncInTheAir() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(1, syncs.size());
		config.automatic = false;
		configChanged("automaticSync");
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		verify(panel).resetSyncFeedback();
	}

	// A character request waits only on its own sync, which only it ends, and that sync always ends it.

	@Test
	public void aLoginTheServerTurnedAwayStillGivesALaterCharacterItsSync() throws Exception
	{
		syncHandler.run();
		settle();
		syncs.get(0).complete(SyncService.SyncResult.stopping("Update required"));
		settle();
		profileRequired(0);
		assertEquals("the character's own sync goes as a click's would", 2, syncs.size());
	}

	@Test
	public void aRetryFromAnEndedLoginNeverCancelsTheNextLoginsClick() throws Exception
	{
		syncHandler.run();
		settle();
		try (Hold hold = new Hold(1))
		{
			held(hold, false);
			CompletableFuture<Void> done = hold.onWorker(() ->
				syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 2)));
			hold.reached();
			nextAccount();
			syncHandler.run();
			runClient();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertEquals("the next login's click still goes", 2, syncs.size());
	}

	@Test
	public void anEarlierSyncNeverStandsInForTheNextCharactersOwn() throws Exception
	{
		profileRequired(0);
		assertEquals(1, syncs.size());
		nextAccount();
		// The next login's request waits on its own sync, queued behind the old one still in the air.
		profileRequired(1);
		assertEquals(1, syncs.size());
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals("its own sync goes", 2, syncs.size());
		assertFalse(syncs.get(1).isDone());
		assertEquals("the next character goes on only after its own sync", 2, publishes.size());
	}

	@Test
	public void turningAutomaticSyncOffWhileACharacterQueuesItsSyncKeepsThatSync() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(1, syncs.size());
		publishHandler.run();
		settle();
		config.automatic = false;
		Object publication = field("publication");
		Field gateField = PublicationCoordinator.class.getDeclaredField("syncGate");
		gateField.setAccessible(true);
		Object gate = gateField.get(publication);
		java.util.concurrent.atomic.AtomicReference<Thread> off = new java.util.concurrent.atomic.AtomicReference<>();
		CompletableFuture<Void> turnedOff;
		// Automatic sync goes off just as the character queues its sync behind the automatic push in the air.
		synchronized (gate)
		{
			turnedOff = CompletableFuture.runAsync(() ->
			{
				off.set(Thread.currentThread());
				((PublicationCoordinator) publication).cancelAutomaticSync();
			});
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while ((off.get() == null || off.get().getState() != Thread.State.BLOCKED) && System.nanoTime() < until)
			{
				Thread.yield();
			}
			assertEquals(Thread.State.BLOCKED, off.get().getState());
			publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
			settle();
		}
		turnedOff.get(5, TimeUnit.SECONDS);
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals("the character's own sync still goes", 2, syncs.size());
	}

	@Test
	public void turningAutomaticSyncOffDuringItsRetryTakesItsLineDown() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 2));
		drainEdt();
		config.automatic = false;
		configChanged("automaticSync");
		settle();
		verify(panel).resetSyncFeedback();
		assertEquals("the retry never goes", 1, syncs.size());
	}

	// A push keeps everything it is through the gate's queue and a retry: its retry, its game, its character.

	@Test
	public void aRetryThatQueuesBehindAClickStaysTheOneRetry() throws Exception
	{
		syncHandler.run();
		settle();
		syncs.get(0).complete(busy());
		// A click while the retry waits on its timer goes as that retry: the one advised retry, spent.
		syncHandler.run();
		runClient();
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(busy());
		settle();
		assertEquals("being the retry, it never retries again", 2, syncs.size());
	}

	@Test
	public void aCharacterRequestNeverLosesItsSyncToAnOlderRetry() throws Exception
	{
		// The first request's sync and a click merge while they wait on the timer.
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		runClient();
		syncHandler.run();
		runClient();
		settle();
		assertEquals(1, syncs.size());
		// The character setting off and on withdraws the first request; a second one waits on its own sync.
		config.character = false;
		configChanged("characterModel");
		config.character = true;
		configChanged("characterModel");
		publishHandler.run();
		settle();
		publishes.get(1).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		runClient();
		syncs.get(0).complete(busy());
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals("the second request goes on after the retry that carried its sync", 3, publishes.size());
	}

	@Test
	public void aRetryIsForTheGameItsAttemptWasFor() throws Exception
	{
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		announceLeague();
		settle();
		assertEquals(List.of("demonic-pacts"), sentModes());
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		syncs.get(0).complete(busy());
		settle();
		assertEquals(List.of("demonic-pacts", "demonic-pacts"), sentModes());
	}

	@Test
	public void aLaterMainGameClickIsNeverReplacedByAnEarlierLeagueRetry() throws Exception
	{
		announceLeague();
		syncHandler.run();
		settle();
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		syncHandler.run();
		runClient();
		syncs.get(0).complete(busy());
		settle();
		assertTrue(sentModes().contains("main"));
	}

	@Test
	public void automaticSyncOffJustAsARetryIsDecidedLeavesNoRetryingLine() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		try (Hold hold = new Hold(1))
		{
			KillClogConfig held = held(hold, false);
			CompletableFuture<Void> done = hold.onWorker(() -> syncs.get(0).complete(busy()));
			hold.reached();
			when(held.automaticSync()).thenReturn(false);
			config.automatic = false;
			configChanged("automaticSync");
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		verify(panel, never()).showSyncProgress(false, "retrying...", false);
		verify(panel, atLeastOnce()).resetSyncFeedback();
		assertEquals(1, syncs.size());
	}

	// Two pushes go as one the same way in whatever order they meet; a push that will not go ends what it began.

	@Test
	public void aLaterClickLeadsAnEarlierOneWaitingForTheSlot() throws Exception
	{
		syncHandler.run();
		settle();
		// A League click waits for the slot; then, back on the main game, a later click waits on its timer.
		announceLeague();
		ticks(1);
		syncHandler.run();
		runClient();
		executor.runAll();
		runClient();
		world(net.runelite.api.WorldType.MEMBERS);
		ticks(1);
		syncHandler.run();
		runClient();
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals(List.of("main", "main"), sentModes());
	}

	@Test
	public void aLateTimerFromAWithdrawnRequestNeverOverwritesTheNewerOnesSync() throws Exception
	{
		syncHandler.run();
		settle();
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		syncHandler.run();
		runClient();
		Runnable old = (Runnable) read(read(field("publication"), "pendingTimer"), "command");
		// The first request is withdrawn and a second one asks for its own sync; then the first's timer fires late.
		config.character = false;
		configChanged("characterModel");
		config.character = true;
		configChanged("characterModel");
		publishHandler.run();
		settle();
		publishes.get(1).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		old.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		syncs.get(syncs.size() - 1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals("the second request goes on after its sync", 3, publishes.size());
	}

	@Test
	public void aRetryForAWorldWhoseGameIsUnknownTakesItsLineDown() throws Exception
	{
		syncHandler.run();
		settle();
		syncs.get(0).complete(busy());
		drainEdt();
		world(net.runelite.api.WorldType.SEASONAL);
		settle();
		assertEquals(1, syncs.size());
		verify(panel, atLeastOnce()).resetSyncFeedback();
	}

	@Test
	public void aMainGameRetryAfterAHopToALeagueWaitsForTheNextMainSync() throws Exception
	{
		syncHandler.run();
		settle();
		assertEquals(List.of("main"), sentModes());
		// A League world can't name the main account type, so the main log is not sent from there.
		announceLeague();
		syncs.get(0).complete(busy());
		settle();
		assertEquals(List.of("main"), sentModes());
		verify(panel, atLeastOnce()).resetSyncFeedback();
	}

	@Test
	public void anOldTimerThatWaitsAtTheGateNeverTakesTheNextLoginsCharacterSync() throws Exception
	{
		syncHandler.run();
		runClient();
		Runnable old = (Runnable) read(read(field("publication"), "pendingTimer"), "command");
		nextAccount();
		// The old login's timer escaped its cancellation and fires now.
		old.run();
		settle();
		assertTrue(syncs.isEmpty());
		syncHandler.run();
		settle();
		profileRequired(0);
		assertEquals(1, syncs.size());
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals("the next login's character sync still goes", 2, syncs.size());
	}

	// A completion acts only while its attempt is the latest and nothing has cancelled it; a withdrawn character
	// request sends nothing.

	@Test
	public void anOlderRefusalNeverStopsTheLoginAfterANewerClickSucceeds() throws Exception
	{
		// A click waits behind the first push; the first push's refusal lands, then the waiting click succeeds.
		syncHandler.run();
		settle();
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
		syncs.get(0).complete(SyncService.SyncResult.stopping("Old refusal"));
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		((PublicationCoordinator) field("publication")).scheduleAutomaticSync();
		settle();
		assertEquals("automatic sync still goes for this login", 3, syncs.size());
	}

	@Test
	public void aCancelledCompletionNeverSpendsTheNextOptInsRetry() throws Exception
	{
		syncHandler.run();
		settle();
		PublicationCoordinator publication = (PublicationCoordinator) field("publication");
		try (Hold hold = new Hold(1))
		{
			KillClogConfig held = held(hold, false);
			CompletableFuture<Void> done = hold.onWorker(() -> syncs.get(0).complete(busy()));
			hold.reached();
			// Opted out and straight back in while the old answer waits.
			when(held.killclogSync()).thenReturn(false);
			publication.cancelSync();
			when(held.killclogSync()).thenReturn(true);
			publication.pushNow(((SessionTracker) field("sessions")).current(), publication.syncEra(), true);
			runClient();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertEquals(2, syncs.size());
		syncs.get(1).complete(busy());
		settle();
		assertEquals("the opt-in keeps its own one retry", 3, syncs.size());
	}

	@Test
	public void aWithdrawnCharacterRequestSendsNothing() throws Exception
	{
		publishHandler.run();
		settle();
		ArgumentCaptor<ProfileAppearanceService.Authorization> authorization =
			ArgumentCaptor.forClass(ProfileAppearanceService.Authorization.class);
		verify(appearance).publishCurrent(you(), authorization.capture());
		java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
		assertTrue(authorization.getValue().sendIf(sent::incrementAndGet));
		// Publish Character Model off: from the moment that returns, the request's send never goes.
		config.character = false;
		configChanged("characterModel");
		assertFalse(authorization.getValue().sendIf(sent::incrementAndGet));
		assertEquals(1, sent.get());
	}

	// A withdrawal's cleanup names what it withdraws; a press takes its era with its login; the server's wait holds.

	@Test
	public void aWithdrawalsCleanupLeavesARequestPressedAfterIt() throws Exception
	{
		PublicationCoordinator publication = (PublicationCoordinator) field("publication");
		// The withdrawal has moved the character's era; a press in the new era lands before its cleanup runs.
		Field era = PublicationCoordinator.class.getDeclaredField("characterEra");
		era.setAccessible(true);
		int moved = (int) era.get(publication) + 1;
		era.set(publication, moved);
		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());
		java.lang.reflect.Method withdraw = PublicationCoordinator.class.getDeclaredMethod("withdrawCharacter", int.class);
		withdraw.setAccessible(true);
		withdraw.invoke(publication, moved);
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PUBLISHED, null));
		settle();
		verify(panel).showCharacterPublishStatus(CharacterStatus.PUBLISHED, true, null);
	}

	@Test
	public void anOptOutsCleanupLeavesAPushAskedAfterIt() throws Exception
	{
		PublicationCoordinator publication = (PublicationCoordinator) field("publication");
		int moved = ((KillclogSyncGate<?>) read(publication, "syncGate")).cancel();
		// Pressed in the new era, waiting on its timer when the old withdrawal's cleanup runs.
		syncHandler.run();
		runClient();
		java.lang.reflect.Method withdraw = PublicationCoordinator.class.getDeclaredMethod("withdrawSync", int.class);
		withdraw.setAccessible(true);
		withdraw.invoke(publication, moved);
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void aClickHeldAsItsSyncIsTurnedOffNeverSends() throws Exception
	{
		PublicationCoordinator publication = (PublicationCoordinator) field("publication");
		try (Hold hold = new Hold(1))
		{
			held(hold, false);
			CompletableFuture<Void> done = hold.onEdt(syncHandler::run);
			hold.reached();
			publication.cancelSync();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
		assertTrue(syncs.isEmpty());
	}

	@Test
	public void aClickQueuedBehindACharactersSyncKeepsTheServersWait() throws Exception
	{
		profileRequired(0);
		assertEquals(1, syncs.size());
		syncHandler.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 20));
		assertEquals("the click rides the advised wait", 20_000L, executor.lastDelayMs());
	}

	@Test
	public void everyClickDuringTheServersWaitRidesIt() throws Exception
	{
		profileRequired(0);
		syncHandler.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 20));
		assertEquals(20_000L, executor.lastDelayMs());
		syncHandler.run();
		runClient();
		assertEquals("a second click rides it too", 20_000L, executor.lastDelayMs());
	}

	@Test
	public void aRequestFromAWithdrawnEraNeverBlocksANewerPress() throws Exception
	{
		publishHandler.run();
		settle();
		assertEquals(1, publishes.size());
		// The withdrawal has moved the era; its cleanup has not run yet when the player presses again.
		movedCharacterEra();
		publishHandler.run();
		settle();
		assertEquals("the newer press goes", 2, publishes.size());
	}

	@Test
	public void aLateCleanupLeavesANewerRequestsFinishedStatus() throws Exception
	{
		int moved = movedCharacterEra();
		publishHandler.run();
		settle();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PUBLISHED, null));
		settle();
		java.lang.reflect.Method withdraw = PublicationCoordinator.class.getDeclaredMethod("withdrawCharacter", int.class);
		withdraw.setAccessible(true);
		withdraw.invoke(field("publication"), moved);
		drainEdt();
		verify(panel, never()).showCharacterPublishStatus(CharacterStatus.CLEAR, false, null);
	}

	/** The first half of a character withdrawal: its era moved, its cleanup not yet run. The new era. */
	private int movedCharacterEra() throws Exception
	{
		Field era = PublicationCoordinator.class.getDeclaredField("characterEra");
		era.setAccessible(true);
		int moved = (int) era.get(field("publication")) + 1;
		era.set(field("publication"), moved);
		return moved;
	}

	@Test
	public void aLateAutomaticCleanupLeavesACaptureAskedAfterIt() throws Exception
	{
		PublicationCoordinator publication = (PublicationCoordinator) field("publication");
		publication.scheduleAutomaticSync();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Wait", true, 20));
		// Automatic sync off, its first half only: the era moves while the cleanup has not run yet. Back on, a new
		// capture is asked while the old retry still waits.
		Field gate = PublicationCoordinator.class.getDeclaredField("syncGate");
		gate.setAccessible(true);
		KillclogSyncGate<?> syncGate = (KillclogSyncGate<?>) gate.get(publication);
		int silenced = syncGate.withdrawAutomatic();
		int cutoff = syncGate.automaticEra();
		publication.scheduleAutomaticSync();
		java.lang.reflect.Method withdraw = PublicationCoordinator.class.getDeclaredMethod("withdrawAutomatic",
			int.class, int.class);
		withdraw.setAccessible(true);
		withdraw.invoke(publication, silenced, cutoff);
		settle();
		assertEquals("the capture asked after the cutoff goes", 2, syncs.size());
	}

	/** The server's "another sync holds the lock, try again in 2 s". */
	private static SyncService.SyncResult busy()
	{
		return new SyncService.SyncResult(false, false, "Busy", true, 2);
	}

	/** The game each sync so far was sent for. */
	private List<String> sentModes()
	{
		List<String> modes = new ArrayList<>();
		for (org.mockito.invocation.Invocation call : org.mockito.Mockito.mockingDetails(syncService).getInvocations())
		{
			if (call.getMethod().getName().equals("syncCollectionLog"))
			{
				modes.add(call.getArgument(7));
			}
		}
		return modes;
	}

	private static Object read(Object target, String name) throws Exception
	{
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
		{
			try
			{
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			}
			catch (NoSuchFieldException e)
			{
				// the field lives on a superclass
			}
		}
		throw new NoSuchFieldException(name);
	}

	/** A publish pressed now that the server answers "sync the log first": its own sync starts. */
	private void profileRequired(int publish) throws Exception
	{
		publishHandler.run();
		settle();
		publishes.get(publish).complete(new ProfileAppearanceService.PublishResult(ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
	}

	/** A press on the Swing thread, held at its first read of a setting while another account logs in. */
	private void pressedThenNextAccount(Runnable press, boolean characterRead) throws Exception
	{
		try (Hold hold = new Hold(1))
		{
			held(hold, characterRead);
			CompletableFuture<Void> done = hold.onEdt(press::run);
			hold.reached();
			nextAccount();
			hold.close();
			done.get(5, TimeUnit.SECONDS);
		}
		settle();
	}

	/** The publication flow's settings, the character's or sync's read held; everything else on. */
	private KillClogConfig held(Hold hold, boolean characterRead) throws Exception
	{
		KillClogConfig held = mock(KillClogConfig.class);
		when(held.automaticSync()).thenReturn(true);
		if (characterRead)
		{
			when(held.killclogSync()).thenReturn(true);
			when(held.characterModel()).thenAnswer(invocation -> hold.read());
		}
		else
		{
			when(held.characterModel()).thenReturn(true);
			when(held.killclogSync()).thenAnswer(invocation -> hold.read());
		}
		Field config = PublicationCoordinator.class.getDeclaredField("config");
		config.setAccessible(true);
		config.set(field("publication"), held);
		return held;
	}

	/** Logged out, then another account logs in. */
	private void nextAccount() throws Exception
	{
		logout();
		Player next = mock(Player.class);
		when(next.getName()).thenReturn("Other");
		when(client.getLocalPlayer()).thenReturn(next);
		when(client.getAccountHash()).thenReturn(HASH + 1);
		login();
	}

	/** Holds one thread at its {@code at}-th read of a setting until let go: the moment between a check and its act. */
	private static final class Hold implements AutoCloseable
	{
		private final java.util.concurrent.CountDownLatch reached = new java.util.concurrent.CountDownLatch(1);
		private final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
		private final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		private final int at;
		private volatile Thread worker;

		Hold(int at)
		{
			this.at = at;
		}

		boolean read() throws InterruptedException
		{
			if (Thread.currentThread() == worker && reads.incrementAndGet() == at)
			{
				reached.countDown();
				assertTrue(go.await(8, TimeUnit.SECONDS));
			}
			return true;
		}

		CompletableFuture<Void> onEdt(Work work)
		{
			CompletableFuture<Void> done = new CompletableFuture<>();
			SwingUtilities.invokeLater(() -> run(work, done));
			return done;
		}

		CompletableFuture<Void> onWorker(Work work)
		{
			CompletableFuture<Void> done = new CompletableFuture<>();
			new Thread(() -> run(work, done)).start();
			return done;
		}

		private void run(Work work, CompletableFuture<Void> done)
		{
			worker = Thread.currentThread();
			try
			{
				work.run();
				done.complete(null);
			}
			catch (Throwable e)
			{
				done.completeExceptionally(e);
			}
		}

		void reached() throws InterruptedException
		{
			assertTrue("the work reached its check", reached.await(5, TimeUnit.SECONDS));
		}

		@Override
		public void close()
		{
			go.countDown();
		}
	}

	@FunctionalInterface
	private interface Work
	{
		void run() throws Exception;
	}

	@Test
	public void aLeagueLearnedAfterTheLoginStillLeavesTheMainStoreToTheMainGame() throws Exception
	{
		// A cold client: the running League arrives with the sync index, a few ticks after the login.
		logout();
		world(net.runelite.api.WorldType.SEASONAL, net.runelite.api.WorldType.MEMBERS);
		loggedIn();
		ticks(1);
		clearInvocations(localClogCache);
		announceLeague();
		ticks(2);
		verify(localClogCache).serve(org.mockito.ArgumentMatchers.argThat((Session s) -> s != null && !s.loggedIn()));
		verify(localClogCache, never()).serve(you());
	}

	@Test
	public void aWorldWhoseGameIsUnknownStillShowsTheMainLog() throws Exception
	{
		logout();
		world(net.runelite.api.WorldType.DEADMAN);
		loggedIn();
		verify(localClogCache, atLeastOnce()).serve(you());
	}

	@Test
	public void aHopFromTheMainGameToALeagueKeepsTheMainStore() throws Exception
	{
		ticks(1);
		clearInvocations(localClogCache);
		announceLeague();
		hop();
		ticks(2);
		verify(localClogCache, never()).serve(org.mockito.ArgumentMatchers.argThat((Session s) -> s != null && !s.loggedIn()));
	}

	@Test
	public void anAccountThatArrivesAfterTheLoginStillMakesIt() throws Exception
	{
		SessionTracker sessions = (SessionTracker) field("sessions");
		logout();
		when(client.getAccountHash()).thenReturn(-1L);
		loggedIn();
		assertFalse(sessions.current().loggedIn());

		// The game sends the account a tick later.
		when(client.getAccountHash()).thenReturn(HASH);
		ticks(1);
		assertEquals(RSN, sessions.current().getName());
		assertEquals(HASH, sessions.current().getAccountHash());
	}

	@Test
	public void aCharacterRequestAsksItsOwnLoginEvenIfNothingCancelledIt() throws Exception
	{
		publishHandler.run();
		settle();
		ArgumentCaptor<ProfileAppearanceService.Authorization> stillWanted =
			ArgumentCaptor.forClass(ProfileAppearanceService.Authorization.class);
		verify(appearance).publishCurrent(you(), stillWanted.capture());

		// No listener hears the change, so only the request's own check stands between it and the next login.
		SessionTracker sessions = (SessionTracker) field("sessions");
		Field listeners = SessionTracker.class.getDeclaredField("listeners");
		listeners.setAccessible(true);
		((java.util.List<?>) listeners.get(sessions)).clear();
		when(client.getAccountHash()).thenReturn(HASH + 1);
		sessions.update(client);
		assertFalse(stillWanted.getValue().getAsBoolean());
	}

	/** The game says LOGGED_IN, as it does at a login and after every hop. */
	private void loggedIn()
	{
		gameState = GameState.LOGGED_IN;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(event);
	}

	/** A world hop: HOPPING, then LOGGED_IN on the new world. */
	private void hop()
	{
		gameState = GameState.HOPPING;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.HOPPING);
		plugin.onGameStateChanged(event);
		loggedIn();
	}

	/** The server announces a League, and the player is on one of its worlds. */
	private LocalClogCache announceLeague() throws Exception
	{
		when(((KillclogService) field("killclogService")).activeLeague()).thenReturn("demonic-pacts");
		LocalClogCache league = mock(LocalClogCache.class);
		when(league.serve(any())).thenReturn(true);
		TestViews.leagueCacheFactory(plugin, id ->
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
		verify(syncService).syncCollectionLog(you(),  org.mockito.ArgumentMatchers.isNull(),  any(),  any(),  any(),  anyInt(),  eq(league),  eq("demonic-pacts"),  anyBoolean(),  any());
		verify(syncService, never()).syncCollectionLog(any(),  any(),  any(),  any(),  any(),  anyInt(),
			eq(localClogCache),  eq("main"),  anyBoolean(),  any());
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
		verify(syncService, times(2)).syncCollectionLog(you(),  org.mockito.ArgumentMatchers.isNull(),  any(),
			any(),  any(),  anyInt(),  eq(league),  eq("demonic-pacts"),  anyBoolean(),  any());
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
		verify(syncService, times(2)).syncCollectionLog(you(),  org.mockito.ArgumentMatchers.isNull(),  any(),
			any(),  any(),  anyInt(),  eq(league),  eq("demonic-pacts"),  anyBoolean(),  any());
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
		verify(syncService).syncCollectionLog(you(),  org.mockito.ArgumentMatchers.isNull(),  pbs.capture(),  any(),  any(),  anyInt(),  eq(league),  eq("demonic-pacts"),  anyBoolean(),  any());
		return pbs.getValue();
	}

	private void recordPbs()
	{
		when(configManager.getRSProfiles()).thenReturn(List.of(
			new RuneScapeProfile(RSN, RuneScapeProfileType.STANDARD, HASH, "main"),
			new RuneScapeProfile(RSN, RuneScapeProfileType.RAGING_ECHOES_LEAGUE, HASH, "earlier"),
			new RuneScapeProfile(RSN, RuneScapeProfileType.DEMONIC_PACTS_LEAGUE, HASH, "league")));
		StoredPbs.stub(configManager, new Object[][]{{"personalbest", "rsprofile.main", "zulrah", 5.0},
			{"personalbest", "rsprofile.earlier", "zulrah", 7.0}, {"personalbest", "rsprofile.league", "zulrah", 9.6}});
	}

	/** Two fragments of your account and another account's, with times in RuneLite's store and the adventure log. */
	private void ladderPbs()
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
		StoredPbs.stub(configManager, stored);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void theLadderPayloadKeepsTeamSizesSplitAndNamesEachBestsLane() throws Exception
	{
		ladderPbs();
		syncHandler.run();
		settle();
		ArgumentCaptor<java.util.Map<String, Double>> collapsed = ArgumentCaptor.forClass(java.util.Map.class);
		ArgumentCaptor<java.util.Map<String, PbRecord>> detailed = ArgumentCaptor.forClass(java.util.Map.class);
		verify(syncService).syncCollectionLog(you(),  any(),  collapsed.capture(),  detailed.capture(),  any(),  anyInt(),  eq(localClogCache),  eq("main"),  anyBoolean(),  any());

		java.util.Map<String, String> got = new java.util.TreeMap<>();
		detailed.getValue().forEach((key, pb) -> got.put(key, pb.seconds + " " + pb.source.wire()));
		java.util.Map<String, String> want = new java.util.TreeMap<>();
		want.put("zulrah", "55.5 store");
		want.put("chambers of xeric solo", "1400.0 advlog");
		want.put("chambers of xeric 5 players", "1200.0 advlog");
		want.put("chambers of xeric challenge mode 3 players", "2000.0 store");
		want.put("vorkath", "70.0 advlog");
		want.put("the leviathan", "150.0 store");
		assertEquals("the adventure log is the game's: it takes RuneLite's time; another account never counts", want, got);
		assertEquals(Double.valueOf(55.5), collapsed.getValue().get("Zulrah"));
		assertEquals(Double.valueOf(1200.0), collapsed.getValue().get("Chambers of Xeric"));
		// Each boss's one time is the best of the records above, so a time only the game's own log holds is there too.
		assertEquals(Double.valueOf(70.0), collapsed.getValue().get("Vorkath"));
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
	public void yourPbsForEveryBossComeFromOneReadOfTheProfiles() throws Exception
	{
		ticks(1);
		ladderPbs();
		// What each boss read before: RuneLite's profiles listed again for every boss.
		java.util.List<String> bosses = new java.util.ArrayList<>();
		java.util.List<String> want = new java.util.ArrayList<>();
		for (net.runelite.client.hiscore.HiscoreSkill skill : net.runelite.client.hiscore.HiscoreSkill.values())
		{
			if (skill.getType() == net.runelite.client.hiscore.HiscoreSkillType.BOSS)
			{
				bosses.add(skill.getName());
				want.add(new PersonalBests(configManager, PersonalBests.profileKeys(configManager.getRSProfiles(),
					HASH, RuneScapeProfileType.STANDARD.name())).pbText(skill.getName()));
			}
		}
		org.junit.Assert.assertTrue(want.contains(PersonalBests.formatSeconds(55.5)));
		org.junit.Assert.assertTrue(want.contains("(5 players) " + PersonalBests.formatSeconds(1200.0)));
		clearInvocations(configManager);
		java.util.function.UnaryOperator<String> pbs = selfPbs.apply(null);
		java.util.List<String> got = new java.util.ArrayList<>();
		bosses.forEach(boss -> got.add(pbs.apply(boss)));
		assertEquals(want, got);
		verify(configManager, org.mockito.Mockito.times(1)).getRSProfiles();
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
	public void aLogoutTellsThePanelNobodyIsLoggedIn() throws Exception
	{
		// Logged out, a lookup of your name reads every site like anyone's, so it shows your character and clan.
		gameState = GameState.LOGIN_SCREEN;
		logout();
		drainEdt();
		verify(panel).setLoggedInPlayer(null, null);
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
		TestViews.leagueCacheFactory(plugin, id -> reopened);
		plugin.startUp();
		ticks(1);
		drainEdt();
		verify(panel).followWorld("demonic-pacts", reopened, "demonic-pacts");
	}

	@Test
	public void aWorldWithNoKnownGameStillShowsYouAtLogin() throws Exception
	{
		world(net.runelite.api.WorldType.DEADMAN);
		when(localClogCache.serve(any())).thenReturn(true);
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
		when(league.serve(any())).thenReturn(true);
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);
		ticks(1);
		drainEdt();
		verify(panel).doLookup();
	}

	@Test
	public void theLoginLookupLeavesALookupOfSomeoneElseStanding() throws Exception
	{
		when(localClogCache.serve(any())).thenReturn(true);
		// Looked up at the login screen, where no provider may have named the player yet.
		when(panel.getDisplayedRsn()).thenReturn("Friend");
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);
		ticks(1);
		drainEdt();
		verify(panel, org.mockito.Mockito.atLeastOnce()).getDisplayedRsn();
		verify(panel, never()).setPlayerName(any());
		verify(panel, never()).doLookup();

		// A lookup of yourself gives way to the login's own.
		gameState = GameState.LOGIN_SCREEN;
		logout();
		drainEdt();
		when(panel.getDisplayedRsn()).thenReturn(RSN.toLowerCase(java.util.Locale.ROOT));
		gameState = GameState.LOGGED_IN;
		plugin.onGameStateChanged(login);
		ticks(1);
		drainEdt();
		verify(panel).setPlayerName(RSN);
		verify(panel).doLookup();
	}

	@Test
	public void theLoginLookupKnowsALookupOfYouTypedAnotherWay() throws Exception
	{
		// The game spells the name with a non-breaking space; the lookup box with an underscore.
		when(client.getLocalPlayer().getName()).thenReturn("My\u00A0Name");
		when(localClogCache.serve(any())).thenReturn(true);
		when(panel.getDisplayedRsn()).thenReturn("my_name");
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
		verify(panel).setFirstParty(PanelStatusRow.FirstParty.READY);
		assertEquals(1, executor.live());
		settle();
		verify(syncService).syncCollectionLog(you(),  org.mockito.ArgumentMatchers.isNull(),  any(),  any(),  any(),  anyInt(),  eq(league),  eq("demonic-pacts"),  anyBoolean(),  any());
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
		verify(league, org.mockito.Mockito.atLeastOnce()).serve(you());
		logout();
		verify(league).serve(org.mockito.ArgumentMatchers.argThat((Session session) -> !session.loggedIn()));
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
		TestViews.leagueCacheFactory(plugin, id -> stores.computeIfAbsent(id, key -> mock(LocalClogCache.class)));
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
		when(entry.getParam1()).thenReturn(InterfaceID.COLLECTION << 16);
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
		gameState = GameState.LOGIN_SCREEN;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(event);
	}

	/** The same account logs in again: a new login. */
	private void login() throws Exception
	{
		gameState = GameState.LOGGED_IN;
		((SessionTracker) field("sessions")).update(client);
	}

	/** The player and account this test logs in as. */
	private static Session you()
	{
		return org.mockito.ArgumentMatchers.argThat((Session session) -> session != null && RSN.equals(session.getName())
			&& session.getAccountHash() == HASH);
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
			case "panelProvider": return (Provider<KillClogPanel>) () -> panel;
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
			Task task = new Task(command, lastDelayMs);
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
		// What is left of its delay: time never passes in these tests.
		private final long delayMs;
		private boolean cancelled;
		private boolean done;

		Task(Runnable command)
		{
			this(command, 0);
		}

		Task(Runnable command, long delayMs)
		{
			this.command = command;
			this.delayMs = delayMs;
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
			return unit.convert(delayMs, TimeUnit.MILLISECONDS);
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}
	}

	@Test
	public void automaticSyncOffStopsAnAutomaticSyncAtEitherHandoff() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);

		// Off after the timer fires, before the client thread gathers the push.
		captureListener.run();
		executor.runAll();
		config.automatic = false;
		configChanged("automaticSync");
		settle();
		assertTrue(syncs.isEmpty());

		// Off after the gather, before the dispatch.
		config.automatic = true;
		captureListener.run();
		fireOnce();
		config.automatic = false;
		configChanged("automaticSync");
		settle();
		assertTrue(syncs.isEmpty());

		// The chalice still sends.
		syncHandler.run();
		settle();
		assertEquals(1, syncs.size());
	}

	@Test
	public void anAutomaticSyncClaimedAsAutomaticSyncTurnsOffNeverSends() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		executor.runAll();
		// Off between the timer's own check and its claim on the slot, so the cancel found nothing to stop.
		config.automatic = false;
		settle();
		assertTrue(syncs.isEmpty());
	}

	@Test
	public void aRetiredAutomaticSyncLeavesALaterCharacterPublishAlone() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		fireOnce();
		config.automatic = false;
		configChanged("automaticSync");

		// A character publish that needs the profile first queues its sync behind the retired one.
		publishHandler.run();
		runClient();
		publishes.get(0).complete(new ProfileAppearanceService.PublishResult(
			ProfileAppearanceService.Outcome.PROFILE_REQUIRED, null));
		settle();
		assertEquals(1, syncs.size());
		syncs.get(0).complete(new SyncService.SyncResult(true, false, "Synced"));
		settle();
		assertEquals(2, publishes.size());
	}

	@Test
	public void anAutomaticRetryDroppedWhileItWaitsLeavesTheNextClickItsRetry() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		captureListener.run();
		settle();
		assertEquals(1, syncs.size());
		// The server says busy: the automatic sync's one retry waits on its timer.
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 2));
		config.automatic = false;
		configChanged("automaticSync");
		settle();
		assertEquals(1, syncs.size());

		// The next click still gets its own retry.
		syncHandler.run();
		settle();
		syncs.get(1).complete(new SyncService.SyncResult(false, false, "Busy", true, 2));
		settle();
		assertEquals(3, syncs.size());
	}

	@Test
	public void aDroppedAutomaticSyncNeverGivesAClickInFlightASecondRetry() throws Exception
	{
		when(localClogCache.hasFirstPartyDataForActive()).thenReturn(true);
		syncHandler.run();
		settle();
		syncs.get(0).complete(new SyncService.SyncResult(false, false, "Busy", true, 2));
		settle();
		assertEquals(2, syncs.size());

		// While the click's one retry is in flight, a capture's automatic sync is dropped.
		captureListener.run();
		config.automatic = false;
		configChanged("automaticSync");
		settle();
		syncs.get(1).complete(new SyncService.SyncResult(false, false, "Busy", true, 2));
		settle();
		assertEquals(2, syncs.size());
	}
}
