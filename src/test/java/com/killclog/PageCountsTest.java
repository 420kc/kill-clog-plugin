package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PageCountsTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static final String REVS = "Revenant kills";
	private static final String TOB_ENTRY = "Theatre of Blood (Entry) completions";

	@Test
	public void theLinesAreDylansSix()
	{
		List<String> rows = new ArrayList<>();
		for (PageCounts.Line line : PageCounts.LINES)
		{
			rows.add(line.page + "|" + (line.slot + 1) + "|" + line.label + "|" + line.card);
		}
		assertEquals(List.of("revenants|1|Revenant kills|null", "tormented_demons|1|Tormented Demon kills|null",
			"gloughs_experiments|2|Demonic Gorilla kills|null", "hunter_guild|1|Rumours Completed|null",
			"theatre_of_blood|2|Theatre of Blood (Entry) completions|Entry",
			"tombs_of_amascut|2|Tombs of Amascut (Entry) completions|Entry"), rows);
		for (PageCounts.Line line : PageCounts.LINES)
		{
			assertEquals("each page key is the one the log's index gives its page",
				line.page, ClogService.bossToCategory(pageName(line.page)));
		}
		assertNull("Tortured Gorillas aren't read", PageCounts.line("gloughs_experiments", "Tortured Gorilla kills: 5"));
		assertNull("the raid's own count is the hiscores'", PageCounts.line("theatre_of_blood",
			"Theatre of Blood completions: 40"));
		assertNull("a label on another page is that page's", PageCounts.line("tormented_demons", "Revenant kills: 4"));
		assertSame(PageCounts.LINES.get(0), PageCounts.line("revenants", "Revenant kills: 1,204"));
	}

	private static String pageName(String key)
	{
		switch (key)
		{
			case "gloughs_experiments": return "Glough's Experiments";
			case "tormented_demons": return "Tormented Demons";
			case "hunter_guild": return "Hunter Guild";
			case "theatre_of_blood": return "Theatre of Blood";
			case "tombs_of_amascut": return "Tombs of Amascut";
			default: return "Revenants";
		}
	}

	@Test
	public void theSyncCarriesTheCountsOnceRead() throws Exception
	{
		ClogResult log = new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null);
		assertFalse("none read: nothing is sent, so killclog.com keeps what it holds", body(log).has(PageCounts.FIELD));
		log.getPageCounts().put(REVS, 0);
		log.getPageCounts().put(TOB_ENTRY, 12);
		JsonObject sent = body(log).getAsJsonObject(PageCounts.FIELD);
		assertEquals(0, sent.get(REVS).getAsInt());
		assertEquals(12, sent.get(TOB_ENTRY).getAsInt());
		assertFalse("and never in the score map, which keeps the highest", body(log).has("activities"));
	}

	@Test
	public void aLookupReadsOnlyTheLinesKillClogShows()
	{
		KillclogService service = new KillclogService(null, new Gson(), null);
		ClogResult read = service.parseProofView("x", "{\"clog\":{\"items_by_category\":{}},\"page_counts\":"
			+ "{\"Revenant kills\":57,\"Made up\":3,\"Rumours Completed\":10000001,\"Tormented Demon kills\":\"x\","
			+ "\"Demonic Gorilla kills\":1e12,\"Tombs of Amascut (Entry) completions\":{}}}", "x");
		assertEquals(Map.of(REVS, 57), read.getPageCounts());
		assertTrue("never sent", service.parseProofView("x", "{\"clog\":{\"items_by_category\":{}}}", "x")
			.getPageCounts().isEmpty());
		assertTrue("not an object", service.parseProofView("x",
			"{\"clog\":{\"items_by_category\":{}},\"page_counts\":5}", "x").getPageCounts().isEmpty());
	}

	@Test
	public void everyCopyAndCombineOfALogKeepsItsCounts()
	{
		ClogResult synced = new ClogResult("8lD", Map.of("revenants", List.of(new ClogResult.ClogItem(21804, 1, null))),
			Map.of(), Map.of(), null, null);
		synced.getPageCounts().put(REVS, 57);
		ClogResult provider = new ClogResult("8lD", Map.of("hats", List.of(new ClogResult.ClogItem(2991, 1, null),
			new ClogResult.ClogItem(2993, 1, null))), Map.of(), Map.of(), null, null);
		assertEquals(57, (int) ClogResult.pickFullest(provider, synced).getPageCounts().get(REVS));
		assertEquals(57, (int) ClogResult.pickFullest(synced, provider).getPageCounts().get(REVS));
		assertEquals(57, (int) ClogResult.pickFullerProvider(provider, synced).getPageCounts().get(REVS));
		assertEquals(57, (int) synced.withSources(false, false, true).getPageCounts().get(REVS));
		assertEquals(57, (int) synced.withLocalSource(true).getPageCounts().get(REVS));
		ClogResult copy = synced.withLocalSource(true);
		copy.getPageCounts().put(REVS, 60);
		assertEquals("a copy is its own", 57, (int) synced.getPageCounts().get(REVS));
	}

	@Test
	public void aLookupKeepsTheCountsKillclogServedIt() throws Exception
	{
		// A looked-up player's counts, from killclog.com, survive the cache and reach their cards.
		LocalClogCache cache = LocalClogCacheTest.onDisk(temporaryFolder.newFolder());
		ClogResult served = new ClogResult("8lD", Map.of("revenants", List.of(new ClogResult.ClogItem(21804, 1, null))),
			Map.of("revenants", List.of(21804)), Map.of(), null, null);
		served.getPageCounts().put(REVS, 57);
		cache.cacheResult(served);
		assertEquals(Map.of(REVS, 57), cache.toClogResult("8lD", null).getPageCounts());
	}

	@Test
	public void aBossCellsCardAndAPagesOwnCardTakeTheirCountsFromThatPlayersLog()
	{
		// The boss cells (both sides) and the page cards (both sides) each build through one of these.
		ClogResult log = new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null);
		log.getPageCounts().put(TOB_ENTRY, 12);
		log.getPageCounts().put(REVS, 57);
		log.setHighGambles(1_057);
		TooltipData tob = TooltipData.builder().name("Theatre of Blood").kc(40).build();
		TooltipData cell = TooltipDataBuilder.bossCellCard(tob,
			net.runelite.client.hiscore.HiscoreSkill.THEATRE_OF_BLOOD, null, name -> null, log);
		assertNotNull("the raid's hard mode", cell.hardLabel);
		assertEquals(12, cell.entryKc);
		assertEquals("no log, no count", -1, TooltipDataBuilder.bossCellCard(tob,
			net.runelite.client.hiscore.HiscoreSkill.THEATRE_OF_BLOOD, null, name -> null, null).entryKc);
		assertEquals(57, PageCounts.pageCard("revenants", TooltipData.builder().name("Revenants").build(), log).kc);
		assertEquals(1_057, PageCounts.pageCard(HighGambles.CATEGORY,
			TooltipData.builder().name("Barbarian Assault").build(), log).kc);
	}

	@Test
	public void aPagesCardLeadsWithItsCountAndARaidsTakesItsOwnLine()
	{
		ClogResult log = new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null);
		TooltipData revs = TooltipData.builder().name("Revenants").obtainedCount(3).totalItems(14).build();
		assertSame("not carried: the card stays as it was", revs, PageCounts.onCard("revenants", revs, log));
		log.getPageCounts().put(REVS, 0);
		TooltipData shown = PageCounts.onCard("revenants", revs, log);
		assertEquals("Revenant kills: ", shown.kcLabel);
		assertEquals(0, shown.kc);
		assertEquals(-1, shown.entryKc);
		assertSame("another page never shows it", revs, PageCounts.onCard("tormented_demons", revs, log));

		TooltipData tob = TooltipData.builder().name("Theatre of Blood").kc(40).hardLabel("HM").hardKc(5).build();
		log.getPageCounts().put(TOB_ENTRY, 12);
		TooltipData raid = PageCounts.onCard("theatre_of_blood", tob, log);
		assertEquals("the raid's own count stays the hiscores'", 40, raid.kc);
		assertNull(raid.kcLabel);
		assertEquals("Entry", raid.entryLabel);
		assertEquals(12, raid.entryKc);
		assertNull(PageCounts.onCard("revenants", null, log));
		assertSame(revs, PageCounts.onCard("revenants", revs, null));
	}

	@Test
	public void aSyncCarriesOnlyTheCountsReadSinceKillclogTookOne() throws Exception
	{
		// A count another PC read since, newer on killclog.com, is never replaced by this PC's older one.
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = LocalClogCacheTest.onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", 77L));
		cache.cacheFirstPartyResult(new ClogResult("Tester", Map.of("revenants", List.of(
			new ClogResult.ClogItem(21804, 1, null))), Map.of("revenants", List.of(21804)), Map.of(), null, null));
		cache.setPageCount("Tester", REVS, 1204);
		cache.setPageCount("Tester", TOB_ENTRY, 6);
		assertEquals(Map.of(REVS, 1204, TOB_ENTRY, 6), cache.toFirstPartySyncResult("Tester").getPageCounts());

		// killclog.com took a sync with the Revenants' count, and the Theatre's was read again since, to another number.
		cache.setPageCount("Tester", TOB_ENTRY, 7);
		cache.pageCountsSent("Tester", Map.of(REVS, 1204, TOB_ENTRY, 6));
		assertEquals(Map.of(TOB_ENTRY, 7), cache.toFirstPartySyncResult("Tester").getPageCounts());
		assertEquals("the cards still show every count", Map.of(REVS, 1204, TOB_ENTRY, 7),
			cache.toClogResult("Tester", null).getPageCounts());
		assertEquals(Integer.valueOf(1204), cache.pageCount("Tester", REVS));

		// The same number read again is nothing new, so it stays home; a new one goes.
		cache.setPageCount("Tester", REVS, 1204);
		assertEquals(Map.of(TOB_ENTRY, 7), cache.toFirstPartySyncResult("Tester").getPageCounts());
		cache.setPageCount("Tester", REVS, 1300);
		assertEquals(Map.of(REVS, 1300, TOB_ENTRY, 7), cache.toFirstPartySyncResult("Tester").getPageCounts());

		// Saved: a restart doesn't send what killclog.com already took.
		cache.pageCountsSent("Tester", Map.of(REVS, 1300, TOB_ENTRY, 7));
		StoreLogin.logout(cache);
		LocalClogCache reloaded = LocalClogCacheTest.onDisk(dir);
		assertTrue(StoreLogin.activate(reloaded, "Tester", 77L));
		assertTrue(reloaded.toFirstPartySyncResult("Tester").getPageCounts().isEmpty());
		assertEquals(Integer.valueOf(1300), reloaded.pageCount("Tester", REVS));
		assertNull("only the serving account's own", reloaded.pageCount("Someone", REVS));
	}

	@Test
	public void theOwnLogKeepsTheNewestReadingSavesItAndASyncSendsIt() throws Exception
	{
		File dir = temporaryFolder.newFolder();
		LocalClogCache cache = LocalClogCacheTest.onDisk(dir);
		assertTrue(StoreLogin.activate(cache, "Tester", 77L));
		cache.setPageCount("Tester", REVS, 57);
		assertNull("no log yet, as with the gambles: nothing to keep it on", cache.toFirstPartySyncResult("Tester"));
		cache.cacheFirstPartyResult(new ClogResult("Tester", Map.of("revenants", List.of(
			new ClogResult.ClogItem(21804, 1, null))), Map.of("revenants", List.of(21804)), Map.of(), null, null));
		int[] changes = {0};
		cache.setFirstPartyChangedListener(() -> changes[0]++);
		cache.setPageCount("Tester", REVS, 57);
		assertEquals(1, changes[0]);
		cache.setPageCount("Tester", REVS, 57);
		assertEquals("the same reading changes nothing", 1, changes[0]);
		cache.setPageCount("Tester", REVS, 50);
		assertEquals("a lower reading replaces it: a misread gives way to the page's next new number", 50,
			(int) cache.toFirstPartySyncResult("Tester").getPageCounts().get(REVS));
		cache.setPageCount("Someone", REVS, 9);
		assertNull("only the serving account's own log", cache.toClogResult("Someone", null));

		// A walk carries none, so the log keeps its counts through it.
		cache.cacheFirstPartyResult(new ClogResult("Tester", Map.of("revenants", List.of(
			new ClogResult.ClogItem(21804, 1, null))), Map.of("revenants", List.of(21804)), Map.of(), null, null));
		assertEquals(50, (int) cache.toFirstPartySyncResult("Tester").getPageCounts().get(REVS));
		StoreLogin.logout(cache);
		LocalClogCache reloaded = LocalClogCacheTest.onDisk(dir);
		assertTrue(StoreLogin.activate(reloaded, "Tester", 77L));
		assertEquals("saved", 50, (int) reloaded.toFirstPartySyncResult("Tester").getPageCounts().get(REVS));

		// A Hardcore Worlds reset starts the counts over with the log.
		reloaded.startOverOnReset("Tester", 5_000_000, true);
		assertTrue(reloaded.startOverOnReset("Tester", 1_154, false));
		assertTrue(reloaded.toFirstPartySyncResult("Tester").getPageCounts().isEmpty());
	}

	private static JsonObject body(ClogResult log) throws Exception
	{
		Method build = SyncService.class.getDeclaredMethod("buildBody", long.class, AccountType.class,
			ClogResult.class, Map.class, Map.class, long.class);
		build.setAccessible(true);
		return (JsonObject) build.invoke(new SyncService(null, new Gson()), 1L, null, log, Map.of(), Map.of(), -1L);
	}
}
