package com.killclog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatPageCatalogTest
{
	private static Set<String> pages()
	{
		Set<String> pages = new HashSet<>();
		for (String[] row : CatalogTsv.rows(ChatPageCatalogTest.class, "chat-pages.tsv", 2))
		{
			pages.add(row[1]);
		}
		return pages;
	}

	@Test
	public void everyCatalogPageResolvesWithoutAPanelCell()
	{
		Set<String> pages = pages();
		assertEquals(125, pages.size());
		for (String page : pages)
		{
			assertEquals(page, KillClogChatCommand.resolvePage(page.replace('_', ' '), pages).categoryKey);
		}
		assertEquals("future_game_page", KillClogChatCommand.resolvePage(
			"Future Game Page", Set.of("future_game_page")).categoryKey);
	}

	@Test
	public void newAliasesAreExactUniqueAndPointAtRealPages()
	{
		Set<String> names = new HashSet<>();
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-page-aliases.tsv", 2))
		{
			assertTrue("Duplicate shorthand: " + row[0], names.add(KillClogChatCommand.normalize(row[0])));
			assertTrue("Unknown page: " + row[1], pages().contains(row[1]));
			assertEquals(row[0], row[1], KillClogChatCommand.resolvePage(row[0], pages()).categoryKey);
		}
	}

	@Test
	public void existingBossAndClueShortcutsKeepTheirTargets()
	{
		KillClogChatCommand.aliases().forEach((alias, boss) ->
		{
			KillClogChatCommand.ClogTarget target = KillClogChatCommand.resolvePage(alias, pages());
			assertEquals(alias, ClogService.bossToCategory(boss), target.categoryKey);
			assertEquals(alias, boss, target.boss);
		});
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-clue-aliases.tsv", 2))
		{
			assertEquals(KillClogChatCommand.resolveClueCategory(row[0]),
				KillClogChatCommand.resolvePage(row[0], pages()).categoryKey);
		}
	}

	@Test
	public void randomEventsNeverRoutesThroughVenAndUnknownInputDoesNotGuess()
	{
		assertEquals("random_events", KillClogChatCommand.resolvePage("random events", pages()).categoryKey);
		assertEquals("venenatis_and_spindel", KillClogChatCommand.resolvePage("ven", pages()).categoryKey);
		assertNull(KillClogChatCommand.resolvePage("random events garbage", pages()));
		assertNull(KillClogChatCommand.resolvePage("venomous", pages()));
		assertNull(KillClogChatCommand.resolvePage("a", pages()));
		assertNull(KillClogChatCommand.resolvePage("", pages()));
		assertNull(KillClogChatCommand.resolvePage("made up page", pages()));
		assertNull(KillClogChatCommand.resolvePage("mixology", pages()).boss);
		assertNull(KillClogChatCommand.resolvePage("pets", pages()).boss);
	}

	@Test
	public void casePunctuationAndSpacesDoNotDependOnSystemLocale()
	{
		Locale previous = Locale.getDefault();
		try
		{
			Locale.setDefault(new Locale("tr", "TR"));
			assertEquals("mastering_mixology", KillClogChatCommand.resolvePage("  MIXOLOGY  ", pages()).categoryKey);
			assertEquals("shades_of_mortton", KillClogChatCommand.resolvePage("Shades of Mort'ton", pages()).categoryKey);
			assertEquals("giants_foundry", KillClogChatCommand.resolvePage("Giant\u2019s Foundry", pages()).categoryKey);
			assertEquals("all_pets", KillClogChatCommand.resolvePage("ALL_PETS", pages()).categoryKey);
		}
		finally
		{
			Locale.setDefault(previous);
		}
	}

	@Test
	public void petDuplicatesAreExactWithoutAddingBossCategoryCounts()
	{
		ClogResult result = result(Map.of(
			"all_pets", Arrays.asList(item(1, 3), item(2, 1), item(1, 3), item(4, 0), item(99, 5)),
			"a_boss", List.of(item(1, 100))), Map.of("all_pets", Arrays.asList(1, 2, 3, 4, 1)));
		KillClogChatCommand.PageItems page = KillClogChatCommand.pageItems(result, "all_pets", null);
		assertTrue(page.available);
		assertEquals(List.of(1, 2, 3, 4), page.total);
		assertEquals(Map.of(1, 3, 2, 1), page.quantities);
		assertEquals("All Pets: 2/4 <img=10>x3 <img=11>", KillClogChatCommand.formatMessage(
			KillClogChatCommand.buildCommandHeader("All Pets", -1, page.quantities.size(), page.total.size(), false),
			new ArrayList<>(page.quantities.keySet()), page.quantities, Map.of(1, 10, 2, 11)));
	}

	@Test
	public void absentPageIsNotTheSameAsASyncedEmptyPage()
	{
		Map<String, List<Integer>> catalog = Map.of("all_pets", List.of(1, 2));
		KillClogChatCommand.PageItems absent = KillClogChatCommand.pageItems(
			result(Map.of("a_boss", List.of(item(1, 9))), Collections.emptyMap()), "all_pets", catalog);
		assertFalse(absent.available);
		assertTrue(absent.quantities.isEmpty());
		assertEquals(List.of(1, 2), absent.total);
		KillClogChatCommand.PageItems empty = KillClogChatCommand.pageItems(
			result(Collections.emptyMap(), catalog), "all_pets", catalog);
		assertTrue(empty.available);
		assertTrue(empty.quantities.isEmpty());
	}

	@Test
	public void rareShortcutsUseTheSameQuantityProjectionWithoutSummingRepeatedCategories()
	{
		int thirdAge = PanelData.THIRD_AGE_ITEMS[0];
		int gilded = PanelData.GILDED_ITEMS[0];
		ClogResult result = result(Map.of(
			"hard_treasure_trails", List.of(item(thirdAge, 3), item(gilded, 2)),
			"elite_treasure_trails", List.of(item(thirdAge, 3), item(gilded, 2))), Collections.emptyMap());
		assertEquals(Map.of(thirdAge, 3), KillClogChatCommand.pageItems(result, "third_age", null).quantities);
		assertEquals(Map.of(gilded, 2), KillClogChatCommand.pageItems(result, "gilded", null).quantities);
	}

	@Test
	public void pageOrderWinsOverProviderOrderAndFiltersUnrelatedItems()
	{
		ClogResult result = result(Map.of("random_events", List.of(item(3, 2), item(1, 4), item(99, 1))),
			Map.of("random_events", List.of(1, 2, 3)));
		assertEquals(List.of(1, 3), new ArrayList<>(KillClogChatCommand.pageItems(result, "random_events", null).quantities.keySet()));
	}

	@Test
	public void fullReferenceChartMatchesTheRuntimeAliases() throws Exception
	{
		String docs = Files.readString(Paths.get("docs/chat-commands.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
		assertTrue("Regenerate the page table with ChatPageCatalogTest.main", docs.contains(referenceTable()));
	}

	/** A generator and drift gate share this projection of the runtime catalogs. */
	static String referenceTable()
	{
		Map<String, Set<String>> names = new TreeMap<>();
		for (String page : pages())
		{
			names.put(page, new TreeSet<>());
		}
		KillClogChatCommand.aliases().forEach((alias, boss) -> names.get(ClogService.bossToCategory(boss)).add(alias));
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-clue-aliases.tsv", 2))
		{
			names.get(KillClogChatCommand.resolveClueCategory(row[0])).add(row[0]);
		}
		for (String[] row : CatalogTsv.rows(KillClogChatCommand.class, "chat-page-aliases.tsv", 2))
		{
			names.get(row[1]).add(row[0]);
		}
		StringBuilder table = new StringBuilder("| Page name | Also accepts |\n| --- | --- |\n");
		names.forEach((key, aliases) ->
		{
			String full = key.replace('_', ' ');
			aliases.remove(full);
			table.append("| `").append(full).append("` | ");
			table.append(aliases.isEmpty() ? "Full name" : "`" + String.join("`, `", aliases) + "`");
			table.append(" |\n");
		});
		return table.toString();
	}

	public static void main(String[] args)
	{
		System.out.print(referenceTable());
	}

	private static ClogResult result(Map<String, List<ClogResult.ClogItem>> obtained, Map<String, List<Integer>> categories)
	{
		return new ClogResult("Test", obtained, categories, new HashMap<>(), null, null);
	}

	private static ClogResult.ClogItem item(int id, int quantity)
	{
		return new ClogResult.ClogItem(id, quantity, null);
	}
}
