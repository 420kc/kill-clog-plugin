package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ClogPagesTest
{
	@Test
	public void aPageOpensABossPopupOnlyWhenThePageIsThatBosss()
	{
		assertEquals(HiscoreSkill.ZULRAH, ClogPages.pageBoss("zulrah", "Zulrah"));
		// A page in its own name: Moons of Peril is the Lunar Chests.
		assertEquals(HiscoreSkill.LUNAR_CHESTS, ClogPages.pageBoss("moons_of_peril", "Moons of Peril"));
		// A page shared by a boss's modes opens the one it is named for.
		assertEquals(HiscoreSkill.CHAMBERS_OF_XERIC, ClogPages.pageBoss("chambers_of_xeric", "Chambers of Xeric"));
		assertEquals(HiscoreSkill.NIGHTMARE, ClogPages.pageBoss("the_nightmare", "The Nightmare"));
		// Pages shared by bosses, and pages no boss holds, have their own popups.
		assertNull(ClogPages.pageBoss("dagannoth_kings", "Dagannoth Kings"));
		assertNull(ClogPages.pageBoss("callisto_and_artio", "Callisto and Artio"));
		assertNull(ClogPages.pageBoss("barbarian_assault", "Barbarian Assault"));
	}

	@Test
	public void anItemsNameFindsEveryPageThatHoldsIt()
	{
		ClogPages pages = new ClogPages(mock(TooltipController.class), mock(Cells.class), mock(ComparisonController.class),
			mock(LookupSession.class), mock(TooltipDataBuilder.class), new KillClogConfig()
			{
			});
		ClogIndex index = mock(ClogIndex.class);
		when(index.itemNames()).thenReturn(java.util.Map.of(20997, "Twisted bow", 11920, "Dragon pickaxe", 22981, "Ferocious gloves"));
		when(index.categoryKeysForItem(20997)).thenReturn(java.util.List.of("chambers_of_xeric"));
		when(index.categoryKeysForItem(11920)).thenReturn(java.util.List.of("king_black_dragon", "kalphite_queen"));
		when(index.categoryKeysForItem(22981)).thenReturn(java.util.List.of("alchemical_hydra"));
		pages.setClogIndex(index);

		assertEquals(Set.of("chambers_of_xeric"), pages.pagesWithItem("twisted bow"));
		assertEquals(Set.of("king_black_dragon", "kalphite_queen"), pages.pagesWithItem("pickaxe"));
		assertTrue("two letters find nothing", pages.pagesWithItem("bo").isEmpty());
		assertTrue(pages.pagesWithItem("zzz").isEmpty());
		assertTrue(ClogPages.bossOn(HiscoreSkill.CHAMBERS_OF_XERIC, pages.pagesWithItem("twisted")));
		assertTrue(ClogPages.bossOn(HiscoreSkill.KALPHITE_QUEEN, pages.pagesWithItem("dragon pick")));
		assertTrue(!ClogPages.bossOn(HiscoreSkill.ZULRAH, pages.pagesWithItem("twisted")));
	}

	@Test
	public void everyPanelBossHasAPageInTheGamesLog()
	{
		JsonObject seed = new Gson().fromJson(new InputStreamReader(
			ClogPagesTest.class.getResourceAsStream("clog-structure-seed.json"), StandardCharsets.UTF_8), JsonObject.class);
		Set<String> pages = new HashSet<>();
		for (JsonElement tab : seed.getAsJsonArray("tabs"))
		{
			for (JsonElement page : tab.getAsJsonObject().getAsJsonArray("pages"))
			{
				pages.add(page.getAsJsonObject().get("key").getAsString());
			}
		}
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			if (boss == HiscoreSkill.MIMIC)
			{
				// Its one slot, the 3rd age ring, sits on the clue pages; the plugin keeps a page of its own for it.
				continue;
			}
			String key = ClogService.bossToCategory(PanelData.hiscoreName(boss.getName()));
			assertTrue(boss.getName() + " has no page " + key, pages.contains(key));
		}
	}

	@Test
	public void aTabOpenedFromTheCombatSummaryLeadsBackToItThroughItsPages()
	{
		// The Bosses tile's tab says "< Combat Summary", and so does the tab a page's "< Bosses" rebuilds (2.8.0 review).
		TooltipController controller = mock(TooltipController.class);
		Cells cells = mock(Cells.class);
		when(cells.buildPageTooltip(any(), any(), any(), any())).thenAnswer(i -> new ClogTabTooltip());
		ClogPages pages = new ClogPages(controller, cells, mock(ComparisonController.class), mock(LookupSession.class),
			mock(TooltipDataBuilder.class), new KillClogConfig()
			{
			});
		ClogIndex index = new ClogIndex();
		java.util.Map<String, java.util.List<Integer>> categories = new java.util.LinkedHashMap<>();
		categories.put("zulrah", java.util.Arrays.asList(1, 2, 3));
		java.util.Map<String, java.util.List<String>> tabs = new java.util.LinkedHashMap<>();
		tabs.put("Bosses", java.util.Collections.singletonList("zulrah"));
		TestViews.publish(index, categories, java.util.Collections.emptyMap(), tabs, java.util.Collections.emptyMap());
		pages.setClogIndex(index);
		javax.swing.JLabel combat = new javax.swing.JLabel()
		{
			@Override
			public javax.swing.JToolTip createToolTip()
			{
				return new PvmSummaryTooltip();
			}
		};
		new javax.swing.JPanel().add(combat);
		java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(combat, java.awt.event.MouseEvent.MOUSE_PRESSED,
			0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
		org.mockito.ArgumentCaptor<javax.swing.JToolTip> pinned = org.mockito.ArgumentCaptor.forClass(javax.swing.JToolTip.class);

		pages.openTab(combat, press, "Bosses", "< Combat Summary");
		verify(controller).pinTooltipFromPress(any(), any(), any(), pinned.capture());
		javax.swing.JToolTip tab = pinned.getValue();
		assertEquals("< Combat Summary", TestViews.field(tab, "backLabel"));
		java.util.function.ObjIntConsumer<java.awt.event.MouseEvent> open = TestViews.field(tab, "onOpenPage");
		open.accept(press, 0);
		verify(controller, times(2)).pinTooltipFromPress(any(), any(), any(), pinned.capture());
		javax.swing.JToolTip page = pinned.getValue();
		assertEquals("< Bosses", TestViews.field(page, "backLabel"));
		java.util.function.Consumer<java.awt.event.MouseEvent> back = TestViews.field(page, "onBack");
		back.accept(press);
		verify(controller, times(3)).pinTooltipFromPress(any(), any(), any(), pinned.capture());
		javax.swing.JToolTip again = pinned.getValue();
		assertEquals("< Combat Summary", TestViews.field(again, "backLabel"));
		java.util.function.Consumer<java.awt.event.MouseEvent> home = TestViews.field(again, "onBack");
		home.accept(press);
		verify(controller, times(4)).pinTooltipFromPress(any(), any(), any(), pinned.capture());
		assertTrue(pinned.getValue() instanceof PvmSummaryTooltip);
	}
}
