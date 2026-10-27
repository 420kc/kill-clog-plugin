package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.time.Instant;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalClogCacheTest
{
	private static final long HASH = 77L;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	// ── readiness and the account ──

	@Test
	public void nothingServesOrCapturesUntilTheFolderHasLoaded() throws Exception
	{
		CapturingScheduledExecutorService writer = new CapturingScheduledExecutorService();
		LocalClogCache cache = new LocalClogCache(new Gson(), writer);
		cache.open(() -> null);
		assertFalse("the store says no until its load lands", StoreLogin.activate(cache, "Tester", HASH));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1), obtainedItems("zulrah", 1)));
		assertFalse(cache.hasDataFor("Zezima"));
		writer.runQueued();
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		assertTrue(cache.folder().isDone());
		assertNull("a memory-only session has no folder", cache.folder().join());
	}

	@Test
	public void capturesNeedTheActivatedAccount() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		assertFalse(cache.serving("Tester"));
		cache.cacheFirstPartyResult(clog("Tester", categoryItems("zulrah", 1, 2), obtainedItems("zulrah", 1)));
		assertFalse("a capture before activation waits, as it waited for identity", cache.hasDataFor("Tester"));
		StoreLogin.logout(cache);
		assertFalse("and it landed nowhere else either", cache.hasDataFor("Tester"));
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		assertTrue(cache.serving("Tester"));
		cache.cacheFirstPartyResult(clog("Tester", categoryItems("zulrah", 1, 2), obtainedItems("zulrah", 1)));
		assertTrue(cache.hasFirstPartyDataFor("Tester"));
		assertTrue(cache.hasFirstPartyDataForActive());
		assertFalse("another name is another account", cache.serving("Someone"));
		assertFalse(StoreLogin.activate(cache, "Someone", -1L));
	}

	@Test
	public void logsAreFiledByAccountAndFollowANameChange() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Old Name", HASH));
		cache.cacheFirstPartyResult(clog("Old Name", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		File own = ownFile(dir, HASH);
		assertTrue(own.exists());
		PlayerClogData saved = new Gson().fromJson(Files.readString(own.toPath()), PlayerClogData.class);
		assertEquals("77", saved.ownerHash);
		assertEquals("Old Name", saved.playerName);

		StoreLogin.logout(cache);
		assertTrue(StoreLogin.activate(cache, "New Name", HASH));
		assertEquals("Old Name", cache.consumeRenameNotice());
		assertNull(cache.consumeRenameNotice());
		assertTrue(cache.hasFirstPartyDataFor("New Name"));
		assertEquals("New Name", new Gson().fromJson(Files.readString(own.toPath()), PlayerClogData.class).playerName);
		assertEquals("one file, renamed inside", 1, dir.list((d, n) -> n.endsWith(".json")).length);

		LocalClogCache restarted = onDisk(dir);
		assertTrue(StoreLogin.activate(restarted, "New Name", HASH));
		assertNull("no change, no notice", restarted.consumeRenameNotice());
		assertTrue(restarted.hasFirstPartyDataFor("New Name"));
	}

	@Test
	public void aLoginHeldBackAsNoOneIsTakenUpLater() throws Exception
	{
		// A login straight onto a League world: the main store serves no one under that login's number, then a hop
		// to the main game hands it the login itself.
		LocalClogCache cache = ready();
		Session login = new Session(7, "Tester", HASH);
		assertFalse(cache.serve(Session.none(login.getNumber())));
		assertFalse(cache.isActivePlayer("Tester"));
		assertTrue(cache.serve(login) || cache.serve(login));
		assertTrue(cache.isActivePlayer("Tester"));
		assertTrue(cache.servesAccount(login));
	}

	@Test
	public void theSyncPreflightNeedsThisSessionAccountAndName() throws Exception
	{
		LocalClogCache cache = memory("Tester");
		Session login = StoreLogin.session(cache, "Tester", HASH);
		assertTrue(cache.servesAccount(login));
		assertFalse(cache.servesAccount(new Session(login.getNumber(), "Tester", 78L)));
		assertFalse(cache.servesAccount(new Session(login.getNumber(), "Someone", HASH)));
		StoreLogin.logout(cache);
		assertFalse(cache.servesAccount(login));
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		assertFalse("a dead session never serves", cache.servesAccount(login));
		assertTrue(cache.servesAccount(StoreLogin.session(cache, "Tester", HASH)));
	}

	@Test
	public void aQueuedSaveStaysInTheAccountThatMadeIt() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		CapturingScheduledDebounceService writer = new CapturingScheduledDebounceService();
		LocalClogCache cache = new LocalClogCache(new Gson(), writer);
		cache.open(() -> TestFolders.folder(dir));
		assertTrue(StoreLogin.activate(cache, "Shared", HASH));
		cache.cacheFirstPartyResult(clog("Shared", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		StoreLogin.logout(cache);
		assertTrue(StoreLogin.activate(cache, "Shared", 78L));
		writer.runQueued();
		assertTrue(ownFile(dir, HASH).exists());
		assertFalse("the next account's file is not written by the last one's capture", ownFile(dir, 78L).exists());
		assertFalse(cache.hasDataFor("Shared"));
	}

	@Test
	public void anUnreadableOwnLogIsKeptAsideAndSetupCanRetry() throws Exception
	{
		for (String damaged : List.of("", "{broken"))
		{
			File dir = temporaryFolder.newFolder();
			Files.writeString(ownFile(dir, HASH).toPath(), damaged);
			LocalClogCache cache = onDisk(dir);
			assertTrue(StoreLogin.activate(cache, "Tester", HASH));
			assertFalse(cache.hasDataFor("Tester"));
			File[] preserved = dir.listFiles((folder, name) -> name.startsWith(".unreadable-"));
			assertEquals(1, preserved.length);
			assertEquals(damaged, Files.readString(preserved[0].toPath()));
			cache.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 1), obtainedItems("hats", 1)));
			LocalClogCache restarted = onDisk(dir);
			assertTrue(StoreLogin.activate(restarted, "Tester", HASH));
			assertNotNull(restarted.toFirstPartySyncResult("Tester"));
		}
	}

	@Test
	public void aFolderStillHoldingTwoPointFourFilesLoadsTheOwnLogAndLeavesTheRestByteForByte() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache first = onDisk(dir);
		assertTrue(StoreLogin.activate(first, "Tester", HASH));
		first.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 1), obtainedItems("hats", 1)));
		// 2.4's name-keyed files and the old move's leftovers beside the own log, and an own-log name one folder down.
		Map<String, String> left = new HashMap<>();
		left.put("tester.json", "{\"playerName\":\"Tester\"}");
		left.put("stranger.json", "{}");
		left.put(".kill-clog-identity.json", "{}");
		left.put(".kill-clog-layout", "2");
		left.put(".displaced-77-before.json", "{}");
		left.put(".unreadable-old.json", "{broken");
		left.put("legacy/main.json", "{}");
		left.put("ca/main.json", "{}");
		left.put("ca/" + StoreFiles.ownFileName(Long.toString(HASH)), "{\"ca\":1}");
		for (Map.Entry<String, String> entry : left.entrySet())
		{
			File file = new File(dir, entry.getKey());
			file.getParentFile().mkdirs();
			Files.writeString(file.toPath(), entry.getValue());
		}
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		assertTrue(cache.hasDataFor("Tester"));
		for (Map.Entry<String, String> entry : left.entrySet())
		{
			assertEquals(entry.getKey(), entry.getValue(), Files.readString(new File(dir, entry.getKey()).toPath()));
		}
		assertEquals(1, dir.listFiles((folder, name) -> name.startsWith(".unreadable-")).length);
	}

	@Test
	public void anOwnLogNamedForAnotherAccountIsSetAsideAndTheRestLoad() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache first = onDisk(dir);
		assertTrue(StoreLogin.activate(first, "Other", 78L));
		first.cacheFirstPartyResult(clog("Other", categoryItems("hats", 1), obtainedItems("hats", 1)));
		Files.copy(ownFile(dir, 78L).toPath(), ownFile(dir, HASH).toPath());
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Other", 78L));
		assertTrue(cache.hasDataFor("Other"));
		assertFalse(ownFile(dir, HASH).exists());
		assertEquals(1, dir.listFiles((folder, name) -> name.startsWith(".unreadable-")).length);
	}

	@Test
	public void aFreshInstallsFolderIsMadeOnLoad()
	{
		File dir = new File(temporaryFolder.getRoot(), "fresh-kill-clog");
		assertFalse(dir.exists());
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		assertTrue(dir.isDirectory());
	}

	// ── lookups and local alts ──

	@Test
	public void lookupsNeverEnterAnOwnLogAndAltsPreviewTheirOwn() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Alt", 78L));
		cache.cacheFirstPartyResult(clog("Alt", categoryItems("hats", 1, 2), obtainedItems("hats", 1)));
		StoreLogin.logout(cache);
		assertTrue(StoreLogin.activate(cache, "Main", HASH));

		assertTrue("a local alt previews its own log", cache.hasFirstPartyDataFor("Alt"));
		cache.cacheResult(clog("Alt", categoryItems("hats", 1, 2), obtainedItems("hats", 1, 2)));
		assertEquals("a fresh lookup then serves", 2,
			cache.toClogResult("Alt", Collections.emptyMap()).getObtainedItems().get("hats").size());
		PlayerClogData alt = new Gson().fromJson(Files.readString(ownFile(dir, 78L).toPath()), PlayerClogData.class);
		assertEquals("the alt's own log is untouched", 1, alt.obtained.get("hats").size());
		assertTrue(new File(dir, "lookups/alt.json").exists());
		assertNull("lookups never ship", cache.toFirstPartySyncResult("Alt"));
	}

	@Test
	public void aNameTwoLocalAccountsCarryPreviewsNothing() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		for (long hash : new long[]{78L, 79L})
		{
			assertTrue(StoreLogin.activate(cache, "Traded", hash));
			cache.cacheFirstPartyResult(clog("Traded", categoryItems("hats", 1), obtainedItems("hats", 1)));
			StoreLogin.logout(cache);
		}
		LocalClogCache restarted = onDisk(dir);
		assertTrue(StoreLogin.activate(restarted, "Main", HASH));
		assertFalse(restarted.hasDataFor("Traded"));
		assertTrue("the account itself still gets its own", StoreLogin.activate(restarted, "Traded", 79L));
		assertTrue(restarted.hasFirstPartyDataFor("Traded"));
	}

	@Test
	public void lookupsAreCappedAndTheNewestReload() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Main", HASH));
		for (int i = 0; i < 68; i++)
		{
			cache.cacheResult(clog("Player " + i, categoryItems("hats", 1), obtainedItems("hats", 1)));
			new File(dir, "lookups/player_" + i + ".json").setLastModified(1_000_000L + i * 1000L);
		}
		assertFalse("the oldest fell out of memory", cache.hasDataFor("Player 0"));
		assertTrue(cache.hasDataFor("Player 67"));
		assertEquals("and its file went with it, before any restart", 64, new File(dir, "lookups").list().length);
		assertFalse(new File(dir, "lookups/player_0.json").exists());
		LocalClogCache restarted = onDisk(dir);
		assertTrue(StoreLogin.activate(restarted, "Main", HASH));
		assertTrue(restarted.hasDataFor("Player 67"));
		assertTrue(restarted.hasDataFor("Player 4"));
		assertFalse("only the newest 64 load", restarted.hasDataFor("Player 3"));
		assertFalse("and the cap holds on disk", new File(dir, "lookups/player_3.json").exists());
		assertTrue(new File(dir, "lookups/player_4.json").exists());
		assertEquals(64, new File(dir, "lookups").list().length);
	}

	// ── capture, merge and provenance ──

	@Test
	public void pendingHatDateSurvivesRestartAndOnlyReconcilesForItsOwner() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Gson gson = new Gson();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		Map<String, List<Integer>> cats = categoryItems("hats", 2978, 2991, 2992);
		cache.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		String date = ClogDates.local(java.time.Instant.now().minusSeconds(60).toString());
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992), date);
		cache.rememberPendingUnlock("Tester", List.of(2992, 2991), date);
		PlayerClogData saved = gson.fromJson(Files.readString(ownFile(dir, HASH).toPath()), PlayerClogData.class);
		assertEquals(1, saved.pendingUnlocks.size());
		LocalClogCache restarted = onDisk(dir);
		assertTrue(StoreLogin.activate(restarted, "Tester", HASH));
		restarted.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978, 2991)));
		ClogResult result = restarted.toFirstPartySyncResult("Tester");
		assertEquals(date, LookupQueries.getRecentItems(result, 5).get(0).getDate());
		assertEquals(2991, LookupQueries.getRecentItems(result, 5).get(0).getId());
		restarted.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978, 2991)));
		assertEquals(date, LookupQueries.getRecentItems(restarted.toFirstPartySyncResult("Tester"), 5).get(0).getDate());
		Map<String, List<ClogResult.ClogItem>> wrongOwner = obtainedItems("hats", 2991);
		PendingClogUnlock.reconcile(saved.pendingUnlocks, wrongOwner, "other-owner");
		assertNull(wrongOwner.get("hats").get(0).getDate());
	}

	@Test
	public void pendingDatesRequireACompletedLocalBaseline() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Gson gson = new Gson();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		Map<String, List<Integer>> cats = categoryItems("hats", 2978, 2991, 2992);
		cache.cacheResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992));
		assertFalse("a provider copy makes no own log to hold it", ownFile(dir, HASH).exists());
		cache.cacheFirstPartyResult(clog("Tester", cats, obtainedItems("hats", 2978)));
		cache.rememberPendingUnlock("Tester", List.of(2991, 2992));
		PlayerClogData saved = gson.fromJson(Files.readString(ownFile(dir, HASH).toPath()), PlayerClogData.class);
		assertEquals(1, saved.pendingUnlocks.size());
	}

	@Test
	public void shutdownFlushAndNewSessionShareTheExistingWriterQueue() throws Exception
	{
		File directory = temporaryFolder.newFolder();
		ScheduledThreadPoolExecutor writer = new ScheduledThreadPoolExecutor(1);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try
		{
			LocalClogCache cache = new LocalClogCache(new Gson(), writer);
			cache.open(() -> TestFolders.folder(directory));
			cache.folder().get(3, TimeUnit.SECONDS);
			writer.execute(() ->
			{
				entered.countDown();
				try
				{
					assertTrue(release.await(3, TimeUnit.SECONDS));
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new AssertionError(e);
				}
			});
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			cache.cacheResult(clog("Tester", categoryItems("magus", 1, 2), obtainedItems("magus", 1)));
			cache.shutdown();
			assertFalse(writer.isShutdown());
			cache.cacheResult(clog("Tester", categoryItems("magus", 1, 2), obtainedItems("magus", 1, 2)));
			cache.shutdown();
			assertFalse(new File(directory, "lookups/tester.json").exists());
			release.countDown();
			writer.submit(() -> null).get(3, TimeUnit.SECONDS);
			LocalClogCache reloaded = onDisk(directory);
			assertTrue(reloaded.hasDataFor("Tester"));
			assertEquals(2, reloaded.toClogResult("Tester", Collections.emptyMap()).getObtainedItems().get("magus").size());
		}
		finally
		{
			release.countDown();
			writer.shutdownNow();
			assertTrue(writer.awaitTermination(3, TimeUnit.SECONDS));
		}
	}

	@Test
	public void testProviderAccountTypeSurvivesCachedRender() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(new ClogResult("Rng Shango", Collections.emptyMap(), Collections.emptyMap(),
			new HashMap<>(), "2026-05-28 03:21:32", AccountType.GROUP_IRONMAN));
		ClogResult cached = cache.toClogResult("Rng Shango", Collections.emptyMap());
		assertNotNull(cached);
		assertEquals(AccountType.GROUP_IRONMAN, cached.getProviderAccountType());
	}

	@Test
	public void testPartialProviderResultPreservesCachedCategories() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(clog("Fast 07", categoryItems("vetion", 1, 2, 3), obtainedItems("vetion", 1, 2),
			"2026-06-03 01:23:45", AccountType.GROUP_IRONMAN));
		cache.cacheResult(clog("Fast 07", categoryItems("venenatis", 4, 5, 6), obtainedItems("venenatis", 4)));

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertNotNull(cached);
		assertEquals(2, cached.getCategoryItems().size());
		assertEquals(2, cached.getObtainedItems().size());
		assertEquals(3, cached.getCategoryItems().get("vetion").size());
		assertEquals(3, cached.getCategoryItems().get("venenatis").size());
		assertEquals(2, cached.getObtainedItems().get("vetion").size());
		assertEquals(1, cached.getObtainedItems().get("venenatis").size());
		assertEquals("2026-06-03 01:23:45", cached.getLastChanged());
		assertEquals(AccountType.GROUP_IRONMAN, cached.getProviderAccountType());
	}

	@Test
	public void testMergeObtainedItemAddsItemToMappedCategories() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("magus", itemList(1, 2, 3));
		categories.put("all_pets", itemList(2, 4));
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("magus", 1)));

		boolean changed = cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus", "all_pets"), categories, 0, null);

		ClogResult cached = cache.toClogResult("Fast 07", Collections.emptyMap());
		assertTrue(changed);
		assertEquals(2, cached.getObtainedItems().get("magus").size());
		assertEquals(1, cached.getObtainedItems().get("all_pets").size());
		assertEquals(2, cached.getObtainedItems().get("all_pets").get(0).getId());
	}

	@Test
	public void testMergeObtainedItemIsIdempotent() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("magus", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("magus", 1, 2)));
		assertFalse(cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus"), categories, 0, null));
		assertEquals(2, cache.toClogResult("Fast 07", Collections.emptyMap()).getObtainedItems().get("magus").size());
	}

	@Test
	public void testHasObtainedItemChecksMappedCategories() throws Exception
	{
		LocalClogCache cache = memory("Main");
		cache.cacheResult(clog("Fast 07", categoryItems("magus", 1, 2, 3), obtainedItems("magus", 1, 2)));
		assertTrue(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 3, itemListAsStrings("magus")));
		assertFalse(cache.hasObtainedItem("Fast 07", 2, itemListAsStrings("venenatis")));
	}

	@Test
	public void onlyTheServedNameReachesItsOwnLog() throws Exception
	{
		LocalClogCache cache = onDisk(temporaryFolder.newFolder());
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		Map<String, List<Integer>> categories = Map.of("hats", itemList(1, 2));
		cache.cacheFirstPartyResult(clog("Tester", categories, obtainedItems("hats")));
		assertFalse("another name never raises your totals", cache.updateTotalsUpward("Zezima", 99, 99));
		assertFalse(cache.updateTotalsUpward(null, 99, 99));
		assertFalse("another name never adds to your log",
			cache.mergeObtainedItem("Zezima", 2, List.of("hats"), categories, 0, null));
		assertNull("and never sends it", cache.toFirstPartySyncResult("Zezima"));
		assertTrue(cache.updateTotalsUpward("Tester", 99, 99));
		assertEquals(99, cache.toFirstPartySyncResult("Tester").getUniqueObtained());
	}

	@Test
	public void aHardcoreResetStartsTheOwnLogOverAndNothingElseDoes() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		Map<String, List<Integer>> categories = Map.of("hats", itemList(1, 2));
		cache.cacheFirstPartyResult(clog("Tester", categories, obtainedItems("hats", 1, 2)));
		assertFalse("the first look", cache.startOverOnReset("Tester", 50_000, false));
		assertFalse("XP that grows", cache.startOverOnReset("Tester", 60_000, false));
		assertFalse("XP that stands", cache.startOverOnReset("Tester", 60_000, false));
		assertFalse("no reading", cache.startOverOnReset("Tester", 0, false));
		assertFalse("another name", cache.startOverOnReset("Zezima", 1_154, false));
		assertEquals(2, cache.toFirstPartySyncResult("Tester").getObtainedItems().get("hats").size());
		assertNull(cache.toFirstPartySyncResult("Tester").getRunStartedAt());

		assertTrue("XP that falls is a reset", cache.startOverOnReset("Tester", 1_154, false));
		ClogResult run = cache.toFirstPartySyncResult("Tester");
		assertTrue(run.getObtainedItems().isEmpty());
		assertNotNull(run.getRunStartedAt());
		assertEquals("the XP it started at", 1_154, run.getRunStartXp());
		assertFalse("the new run grows", cache.startOverOnReset("Tester", 1_200, false));

		StoreLogin.logout(cache);
		LocalClogCache restarted = onDisk(dir);
		assertTrue(StoreLogin.activate(restarted, "Tester", HASH));
		assertEquals("the new run is what's saved", run.getRunStartedAt(),
			restarted.toFirstPartySyncResult("Tester").getRunStartedAt());
		assertEquals(1_154, restarted.toFirstPartySyncResult("Tester").getRunStartXp());
		assertTrue(restarted.toFirstPartySyncResult("Tester").getObtainedItems().isEmpty());
	}

	@Test
	public void aHardcoreResetAcrossARelogIsStillSeen() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		cache.cacheFirstPartyResult(clog("Tester", Map.of("hats", itemList(1, 2)), obtainedItems("hats", 1, 2)));
		assertFalse("the settle tick keeps its reading", cache.startOverOnReset("Tester", 5_000_000, true));
		StoreLogin.logout(cache);

		LocalClogCache relogged = onDisk(dir);
		assertTrue(StoreLogin.activate(relogged, "Tester", HASH));
		assertTrue(relogged.startOverOnReset("Tester", 1_154, false));
		assertTrue(relogged.toFirstPartySyncResult("Tester").getObtainedItems().isEmpty());
	}

	// Sol 10/27: a log set up after its world settled has no settle tick left to save its XP, so its first reading
	// is saved at once, or a reset across the next relog compared with 0 and went unseen.
	@Test
	public void aLogSetUpAfterItsWorldSettledStillSeesAResetAcrossARelog() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		cache.cacheFirstPartyResult(clog("Tester", Map.of("hats", itemList(1, 2)), obtainedItems("hats", 1, 2)));
		assertFalse("a reading after the settle tick", cache.startOverOnReset("Tester", 5_000_000, false));
		StoreLogin.logout(cache);

		LocalClogCache relogged = onDisk(dir);
		assertTrue(StoreLogin.activate(relogged, "Tester", HASH));
		assertTrue(relogged.startOverOnReset("Tester", 1_154, false));
		assertTrue(relogged.toFirstPartySyncResult("Tester").getObtainedItems().isEmpty());
	}

	@Test
	public void aHealedDateMovesTheLastUpdatedStamp() throws Exception
	{
		LocalClogCache cache = onDisk(temporaryFolder.newFolder());
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		Map<String, List<ClogResult.ClogItem>> undated = new HashMap<>();
		undated.put("hats", new java.util.ArrayList<>(List.of(new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, null))));
		cache.cacheFirstPartyResult(clog("Tester", Map.of("hats", itemList(1, 2)), undated));
		assertNull(cache.toClogResult("Tester", Collections.emptyMap()).getLastChanged());
		Map<String, List<ClogResult.ClogItem>> provider = new HashMap<>();
		provider.put("hats", List.of(new ClogResult.ClogItem(1, 1, "2025-03-01 10:00:00"),
			new ClogResult.ClogItem(2, 1, "2024-01-01 10:00:00")));
		assertTrue(cache.mergeProviderDates("Tester", provider));
		assertEquals("the newest healed date", "2025-03-01 10:00:00",
			cache.toClogResult("Tester", Collections.emptyMap()).getLastChanged());
	}

	@Test
	public void liveUnlockTotalsFollowGameCountersInEitherEventOrder() throws Exception
	{
		for (int initial : List.of(0, 5))
		{
			for (boolean counterFirst : List.of(false, true))
			{
				File dir = temporaryFolder.newFolder();
				LocalClogCache cache = onDisk(dir);
				assertTrue(StoreLogin.activate(cache, "Tester", HASH));
				Map<String, List<Integer>> categories = Map.of("hats", itemList(1, 2), "other", itemList(1));
				ClogResult baseline = clog("Tester", categories, obtainedItems("hats"));
				baseline.setUniqueObtained(initial);
				cache.cacheFirstPartyResult(baseline);
				if (counterFirst) cache.updateTotalsUpward("Tester", initial + 1, 100);
				cache.mergeObtainedItem("Tester", 1, List.of("hats", "other"), categories, 42, "Boss");
				if (!counterFirst) cache.updateTotalsUpward("Tester", initial + 1, 100);
				// A duplicate notification and a lagging counter cannot add another slot.
				cache.mergeObtainedItem("Tester", 1, List.of("hats", "other"), categories, 0, null);
				cache.updateTotalsUpward("Tester", initial, 100);
				ClogResult payload = cache.toFirstPartySyncResult("Tester");
				assertEquals(initial + 1, payload.getUniqueObtained());
				assertNotNull(payload.getObtainedItems().get("hats").get(0).getDate());
				assertEquals(42, payload.getObtainedItems().get("hats").get(0).getObtainedAtKc());
				cache.mergeObtainedItem("Tester", 2, List.of("hats"), categories, 0, null);
				cache.updateTotalsUpward("Tester", initial + 2, 100);
				LocalClogCache restarted = onDisk(dir);
				assertTrue(StoreLogin.activate(restarted, "Tester", HASH));
				assertEquals(initial + 2, restarted.toFirstPartySyncResult("Tester").getUniqueObtained());
			}
		}
	}

	@Test
	public void testMergeObtainedItemRequiresExistingCache() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		assertFalse(cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("magus"), categoryItems("magus", 1, 2, 3), 0, null));
	}

	@Test
	public void testCategoryResyncPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		// A later chalice capture rebuilds the log with bare items; the
		// wholesale replace must not cost the drop its provenance.
		Map<String, List<ClogResult.ClogItem>> bare = new HashMap<>();
		bare.put("vorkath", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, null))));
		cache.cacheFirstPartyResult(clog("Fast 07", categories, bare));

		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
		assertNotNull(survived.getDate());
	}

	@Test
	public void testProviderRefreshPreservesLiveProvenance() throws Exception
	{
		LocalClogCache cache = memory("Fast 07");
		Map<String, List<Integer>> categories = categoryItems("vorkath", 1, 2, 3);
		cache.cacheFirstPartyResult(clog("Fast 07", categories, obtainedItems("vorkath", 1)));
		cache.mergeObtainedItem("Fast 07", 2, itemListAsStrings("vorkath"), categories, 421, "Vorkath");

		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("vorkath", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, null),
			new ClogResult.ClogItem(2, 1, "2026-07-19 10:00:00"))));
		cache.cacheResult(clog("Fast 07", categories, providerObtained));

		// The live unlock marked item 2, so its record is inviolable.
		ClogResult.ClogItem survived = obtainedItem(cache, "Fast 07", "vorkath", 2);
		assertNotNull(survived.getDate());
		assertNotEquals("2026-07-19 10:00:00", survived.getDate());
		assertEquals(421, survived.getObtainedAtKc());
		assertEquals("Vorkath", survived.getObtainedFrom());
	}

	@Test
	public void testOverlayRacingLiveUnlocksNeverDropsItems() throws Exception
	{
		// The provider-date overlay lands on an HTTP completion thread while
		// live unlocks merge from the client thread; neither may lose the other.
		for (int round = 0; round < 25; round++)
		{
			LocalClogCache cache = memory("Fast 07");
			List<Integer> all = new ArrayList<>();
			for (int i = 1; i <= 260; i++)
			{
				all.add(i);
			}
			Map<String, List<Integer>> categories = new HashMap<>();
			categories.put("vorkath", all);
			Map<String, List<ClogResult.ClogItem>> seeded = new HashMap<>();
			List<ClogResult.ClogItem> seedItems = new ArrayList<>();
			List<ClogResult.ClogItem> dated = new ArrayList<>();
			for (int i = 1; i <= 200; i++)
			{
				seedItems.add(new ClogResult.ClogItem(i, 1, null));
				dated.add(new ClogResult.ClogItem(i, 1, "2026-07-19 10:00:00"));
			}
			seeded.put("vorkath", seedItems);
			cache.cacheFirstPartyResult(clog("Fast 07", categories, seeded));
			Map<String, List<ClogResult.ClogItem>> providerDates = new HashMap<>();
			providerDates.put("vorkath", dated);

			CountDownLatch start = new CountDownLatch(1);
			Thread overlay = new Thread(() ->
			{
				try
				{
					start.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return;
				}
				cache.mergeProviderDates("Fast 07", providerDates);
			});
			overlay.start();
			start.countDown();
			for (int id = 201; id <= 260; id++)
			{
				cache.mergeObtainedItem("Fast 07", id, itemListAsStrings("vorkath"), categories, id, "Vorkath");
			}
			overlay.join();
			assertEquals("round " + round, 260,
				cache.toClogResult("Fast 07", Collections.emptyMap()).getObtainedItems().get("vorkath").size());
		}
	}

	@Test
	public void testNewestObtainedDateSkipsBareItems() throws Exception
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("slayer", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 1, "2026-07-12 17:18:29"),
			new ClogResult.ClogItem(2, 1, "2026-07-17 02:02:05"))));
		List<ClogResult.ClogItem> bare = new ArrayList<>(List.of(new ClogResult.ClogItem(3, 1, null)));
		obtained.put("brutus", bare);
		assertEquals("2026-07-17 02:02:05", LocalClogCache.newestObtainedDate(obtained));
		assertNull(LocalClogCache.newestObtainedDate(new HashMap<>()));
		assertNull(LocalClogCache.newestObtainedDate(Collections.singletonMap("brutus", bare)));
	}

	// First-party marking: the sync payload's provenance boundary.

	@Test
	public void providerResultsNeverEnterTheLoggedInPlayersOwnLog() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("provider writes never fire the sync trigger", 0, notified[0]);
		assertFalse("they make no own log", cache.hasDataFor("Zezima"));
		assertNull(cache.toFirstPartySyncResult("Zezima"));
		assertFalse("and a live unlock needs the account's own log", cache.mergeObtainedItem("Zezima", 3,
			itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3), 0, null));

		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1)));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("a later provider copy changes nothing", 1,
			cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3), 0, null);
		assertEquals(2, notified[0]);
		assertEquals(2, cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
		assertEquals(2, cache.toClogResult("Zezima", Collections.emptyMap()).getObtainedItems().get("zulrah").size());
	}

	@Test
	public void testBulkCaptureMarksEverythingAndFiresTheTrigger() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		final int[] notified = {0};
		cache.setFirstPartyChangedListener(() -> notified[0]++);
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		assertEquals("the chalice walk schedules a push like any capture", 1, notified[0]);
		assertEquals(2, cache.toFirstPartySyncResult("Zezima").getObtainedItems().get("zulrah").size());
	}

	@Test
	public void providerRefreshesCannotTouchACapturedRecord() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), new HashMap<>()));
		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3), 420, "Zulrah");

		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("zulrah", new ArrayList<>(List.of(
			new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"),
			new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), providerObtained));
		Map<String, List<ClogResult.ClogItem>> staleObtained = new HashMap<>();
		staleObtained.put("zulrah", new ArrayList<>(List.of(new ClogResult.ClogItem(2, 1, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), staleObtained));

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertEquals(1, payload.getObtainedItems().get("zulrah").size());
		ClogResult.ClogItem kept = payload.getObtainedItems().get("zulrah").get(0);
		assertEquals(1, kept.getId());
		assertEquals("client-observed quantity survives", 1, kept.getCount());
		assertEquals("provenance survives", 420, kept.getObtainedAtKc());
		assertEquals("the panel shows the own log", 1,
			cache.toClogResult("Zezima", Collections.emptyMap()).getObtainedItems().get("zulrah").size());
	}

	@Test
	public void aCaptureInOneCategoryCarriesNoProviderRecordFromAnother() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("clue_b", itemList(1, 5));
		categories.put("boss_a", itemList(1, 6));
		Map<String, List<ClogResult.ClogItem>> providerObtained = new HashMap<>();
		providerObtained.put("clue_b", new ArrayList<>(List.of(new ClogResult.ClogItem(1, 99, "2026-01-01 00:00:00"))));
		cache.cacheResult(clog("Zezima", categories, providerObtained));
		cache.cacheFirstPartyResult(clog("Zezima", categories, new HashMap<>()));
		cache.mergeObtainedItem("Zezima", 1, itemListAsStrings("boss_a"), categories, 420, "Boss A");

		ClogResult payload = cache.toFirstPartySyncResult("Zezima");
		assertNull("the provider-only category ships nothing", payload.getObtainedItems().get("clue_b"));
		assertEquals(1, payload.getObtainedItems().get("boss_a").get(0).getCount());
	}

	@Test
	public void testEmptyFirstCaptureShipsNoProviderItems() throws Exception
	{
		LocalClogCache cache = memory("Newbie");
		cache.cacheFirstPartyResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3), new HashMap<>()));
		cache.cacheResult(clog("Newbie", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2)));
		ClogResult payload = cache.toFirstPartySyncResult("Newbie");
		assertNotNull(payload);
		assertTrue("provider items cannot ride a zero-capture log", payload.getObtainedItems().isEmpty());
		assertFalse(cache.hasFirstPartyDataFor("Newbie"));
	}

	@Test
	public void testFirstPartyPresenceIsPayloadAware() throws Exception
	{
		LocalClogCache cache = memory("Zezima");
		cache.cacheResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), obtainedItems("zulrah", 1, 2),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertFalse(cache.hasFirstPartyDataFor("Zezima"));
		assertFalse(cache.hasCompletedFirstPartySetupFor("Zezima"));

		cache.cacheFirstPartyResult(clog("Zezima", categoryItems("zulrah", 1, 2, 3), new HashMap<>(),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertTrue("an empty walk completes setup", cache.hasCompletedFirstPartySetupFor("Zezima"));
		assertFalse("but carries nothing", cache.hasFirstPartyDataFor("Zezima"));
		cache.mergeObtainedItem("Zezima", 3, itemListAsStrings("zulrah"), categoryItems("zulrah", 1, 2, 3), 0, null);
		assertTrue(cache.hasFirstPartyDataFor("Zezima"));
		assertTrue(cache.hasFirstPartyDataForActive());
	}

	@Test
	public void testEmptyFirstCaptureIsNotAPayload() throws Exception
	{
		LocalClogCache cache = memory("Fresh Acct");
		cache.cacheFirstPartyResult(clog("Fresh Acct", new HashMap<>(), new HashMap<>(),
			"2026-08-01 00:00:00", AccountType.REGULAR));
		assertTrue("an empty first walk still completes local setup", cache.hasDataFor("Fresh Acct"));
		assertTrue(cache.hasCompletedFirstPartySetupFor("Fresh Acct"));
		assertNotNull(cache.toClogResult("Fresh Acct", new HashMap<>()));
		assertFalse("an empty first walk must not read as a sendable payload", cache.hasFirstPartyDataFor("Fresh Acct"));
	}

	@Test
	public void testSessionEndAndRequestCommitHaveOneAtomicOrder() throws Exception
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new NoopScheduledExecutorService());
		Session epoch = Session.none(0);
		CountDownLatch commitEntered = new CountDownLatch(1);
		CountDownLatch releaseCommit = new CountDownLatch(1);
		CountDownLatch logoutStarted = new CountDownLatch(1);
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try
		{
			Future<String> committed = workers.submit(() -> cache.commitIfSessionCurrent(epoch, () ->
			{
				commitEntered.countDown();
				try
				{
					releaseCommit.await(1, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				return "enqueued";
			}));
			assertTrue(commitEntered.await(1, TimeUnit.SECONDS));
			Future<?> logout = workers.submit(() ->
			{
				logoutStarted.countDown();
				StoreLogin.logout(cache);
			});
			assertTrue(logoutStarted.await(1, TimeUnit.SECONDS));
			assertFalse("logout waits behind an already-committed enqueue", logout.isDone());
			releaseCommit.countDown();
			assertEquals("enqueued", committed.get(1, TimeUnit.SECONDS));
			logout.get(1, TimeUnit.SECONDS);
			assertNull("the ended epoch cannot enqueue another request", cache.commitIfSessionCurrent(epoch, () -> "late"));
		}
		finally
		{
			releaseCommit.countDown();
			workers.shutdownNow();
		}
	}

	// ── helpers ──

	private static LocalClogCache memory(String player)
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		assertTrue(StoreLogin.activate(cache, player, HASH));
		return cache;
	}

	/** Open, in memory, nobody logged in. */
	static LocalClogCache ready()
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> null);
		return cache;
	}

	@Test
	public void aLoginReadsItsOwnLogFreshSoAnotherClientsDropsSurvive() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		Map<String, List<Integer>> categories = Map.of("hats", itemList(1, 2));
		LocalClogCache first = onDisk(dir);
		assertTrue(StoreLogin.activate(first, "Tester", HASH));
		first.cacheFirstPartyResult(clog("Tester", categories, obtainedItems("hats")));
		// A second client starts for an alt and loads the folder as it is now.
		LocalClogCache second = onDisk(dir);
		// The first plays on: a drop, dated, at its kill count.
		assertTrue(first.mergeObtainedItem("Tester", 1, List.of("hats"), categories, 42, "Boss"));
		StoreLogin.logout(first);
		// The account logs into the second client, whose next capture rewrites the log.
		assertTrue(StoreLogin.activate(second, "Tester", HASH));
		second.cacheFirstPartyResult(clog("Tester", categories, obtainedItems("hats", 1)));
		ClogResult.ClogItem drop = obtainedItem(onDisk(dir), "Tester", "hats", 1);
		assertEquals("the drop's kill count, which a page capture can't recover", 42, drop.getObtainedAtKc());
		assertNotNull(drop.getDate());
	}

	static LocalClogCache onDisk(File dir)
	{
		LocalClogCache cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService());
		cache.open(() -> TestFolders.folder(dir));
		return cache;
	}

	static File ownFile(File dir, long hash)
	{
		return new File(dir, StoreFiles.ownFileName(Long.toString(hash)));
	}

	private static ClogResult.ClogItem obtainedItem(LocalClogCache cache, String playerName, String category, int itemId)
	{
		for (ClogResult.ClogItem item : cache.toClogResult(playerName, Collections.emptyMap()).getObtainedItems().get(category))
		{
			if (item.getId() == itemId)
			{
				return item;
			}
		}
		return null;
	}

	static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained)
	{
		return clog(playerName, categories, obtained, null, null);
	}

	private static ClogResult clog(String playerName, Map<String, List<Integer>> categories,
		Map<String, List<ClogResult.ClogItem>> obtained, String lastChanged, AccountType accountType)
	{
		return new ClogResult(playerName, obtained, categories, new HashMap<>(), lastChanged, accountType);
	}

	static Map<String, List<Integer>> categoryItems(String category, int... itemIds)
	{
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put(category, itemList(itemIds));
		return categories;
	}

	private static List<Integer> itemList(int... itemIds)
	{
		List<Integer> items = new ArrayList<>();
		for (int itemId : itemIds)
		{
			items.add(itemId);
		}
		return items;
	}

	private static List<String> itemListAsStrings(String... items)
	{
		List<String> result = new ArrayList<>();
		Collections.addAll(result, items);
		return result;
	}

	static Map<String, List<ClogResult.ClogItem>> obtainedItems(String category, int... itemIds)
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		List<ClogResult.ClogItem> items = new ArrayList<>();
		for (int itemId : itemIds)
		{
			items.add(new ClogResult.ClogItem(itemId, 1, null));
		}
		obtained.put(category, items);
		return obtained;
	}

	@Test
	public void aLogDamagedSinceTheFolderLoadedIsSetAsideAtTheNextLogin() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		cache.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 2978), obtainedItems("hats", 2978)));
		StoreLogin.logout(cache);
		Files.writeString(ownFile(dir, HASH).toPath(), "{broken");
		assertTrue(StoreLogin.activate(cache, "Tester", HASH));
		String[] aside = dir.list((folder, name) -> name.startsWith(".unreadable-"));
		assertEquals("the damaged bytes are kept aside", 1, aside.length);
		assertTrue("and the copy in memory still serves", cache.hasFirstPartyDataFor("Tester"));
	}

	@Test
	public void aFreshReadOfALogRenamedOnAnotherClientLeavesTheOldNameToNobody() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache a = onDisk(dir);
		assertTrue(StoreLogin.activate(a, "Old Name", HASH));
		a.cacheFirstPartyResult(clog("Old Name", categoryItems("hats", 2978), obtainedItems("hats", 2978)));
		StoreLogin.logout(a);
		LocalClogCache b = onDisk(dir);
		assertTrue(StoreLogin.activate(b, "Main", 79L));
		StoreLogin.logout(b);
		// The account comes back as New Name on the first client, then logs in on the second.
		assertTrue(StoreLogin.activate(a, "New Name", HASH));
		StoreLogin.logout(a);
		assertTrue(StoreLogin.activate(b, "New Name", HASH));
		StoreLogin.logout(b);
		assertFalse("a stranger who takes the old name is never served this account's log", b.hasDataFor("Old Name"));
		assertTrue(b.hasDataFor("New Name"));
	}

	@Test
	public void eachLoginReadsWhatAnotherClientSavedSinceOnTheRealDiskThread() throws Exception
	{
		// The store's own disk thread, as the plugin runs it: the read lands later, behind any save it queued.
		File dir = temporaryFolder.newFolder();
		LocalClogCache played = new LocalClogCache(new Gson());
		LocalClogCache other = new LocalClogCache(new Gson());
		played.open(() -> TestFolders.folder(dir));
		other.open(() -> TestFolders.folder(dir));
		try
		{
			assertTrue(StoreLogin.activate(played, "Tester", HASH));
			played.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 2978, 2991), obtainedItems("hats", 2978)));
			StoreLogin.logout(played);
			String first = awaitSaved(dir, null);
			assertTrue("the other client takes up the account", StoreLogin.activate(other, "Tester", HASH));
			assertNotNull("with what the first one saved", obtainedItem(other, "Tester", "hats", 2978));
			StoreLogin.logout(other);

			// Played again on the first client: the next login on the other reads it fresh, not its earlier copy.
			assertTrue(StoreLogin.activate(played, "Tester", HASH));
			played.cacheFirstPartyResult(clog("Tester", categoryItems("hats", 2978, 2991), obtainedItems("hats", 2978, 2991)));
			StoreLogin.logout(played);
			awaitSaved(dir, first);
			assertTrue(StoreLogin.activate(other, "Tester", HASH));
			assertNotNull(obtainedItem(other, "Tester", "hats", 2991));
		}
		finally
		{
			played.shutdown();
			other.shutdown();
		}
	}

	/**
	 * Waits for the debounced save on the store's own thread to write the account's file anew; its contents. Generous:
	 * a loaded gate (compile, checkstyle and every test at once) once kept a 0.5 s debounce past 5 s.
	 */
	private static String awaitSaved(File dir, String before) throws Exception
	{
		long until = System.currentTimeMillis() + 15000;
		File own = ownFile(dir, HASH);
		while (true)
		{
			String now = own.isFile() ? Files.readString(own.toPath()) : null;
			if (now != null && !now.equals(before))
			{
				return now;
			}
			assertTrue("the save landed within 15 s", System.currentTimeMillis() < until);
			Thread.sleep(25);
		}
	}
}
