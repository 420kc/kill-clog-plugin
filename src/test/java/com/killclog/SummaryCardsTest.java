package com.killclog;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SummaryCardsTest
{
	private final ComparisonController comparison = mock(ComparisonController.class);
	private final TooltipController tooltips = mock(TooltipController.class);

	{
		// The comparison pairs and holds the cards for real, over its stand-in answers.
		TestViews.set(comparison, "tooltipController", tooltips);
		when(comparison.held(any(), any(), any())).thenCallRealMethod();
	}
	private final SummaryCards cards = cards(mock(LookupSession.class), mock(Cells.class), mock(ClogPages.class));

	private SummaryCards cards(LookupSession session, Cells cells, ClogPages pages)
	{
		return new SummaryCards(session, comparison, cells, tooltips, mock(ItemManager.class),
			() -> CardLinks.ON, pages, mock(ClogService.class), mock(RuneProfileService.class), mock(PlayerPortraits.class),
			mock(PanelIconCache.class), mock(UnsyncedClogCatalog.class), () -> null, () -> "--");
	}

	@Test
	public void aloneTheSkillsCardShowsByItselfAndStaysOpenOverItsCell()
	{
		JPanel cell = new JPanel();
		JLabel owner = new JLabel();
		cell.add(owner);
		JToolTip tip = cards.buildSkills(owner);
		assertTrue(tip instanceof SkillsTooltip);
		verify(comparison, never()).wrapSideBySide(any(), any(), any());
		verify(tooltips).keepTooltipOnHover(tip, cell);
	}

	@Test
	public void comparingPairsTheSkillsCardWithTheRivals()
	{
		when(comparison.isComparisonMode()).thenReturn(true);
		when(comparison.showsPair()).thenReturn(true);
		JToolTip pair = new JToolTip();
		when(comparison.wrapSideBySide(any(), any(), any())).thenReturn(pair);
		JLabel owner = new JLabel();
		assertSame(pair, cards.buildSkills(owner));
		verify(comparison).wrapSideBySide(eq(owner), any(SkillsTooltip.class), any(SkillsTooltip.class));
	}

	@Test
	public void theMostKilledTileFindsItsBossWhateverItsHiscoreSpellingAndBossesOpensTheBossesTab()
	{
		// The hiscores key Calvar'ion as Cal'varion (PanelData.hiscoreName): its tile still finds the boss (2.8.0 review).
		for (net.runelite.client.hiscore.HiscoreSkill boss : new net.runelite.client.hiscore.HiscoreSkill[]{
			net.runelite.client.hiscore.HiscoreSkill.VORKATH, net.runelite.client.hiscore.HiscoreSkill.CALVARION})
		{
			LookupSession session = mock(LookupSession.class);
			java.util.Map<String, Integer> kills = new java.util.HashMap<>();
			kills.put(PanelData.hiscoreName(boss.getName()), 5000);
			when(session.getHiscoreResult()).thenReturn(TestViews.hiscore(AccountType.REGULAR, kills,
				java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
				java.util.Collections.emptyMap(), 0, 0, 0, -1));
			Cells cells = mock(Cells.class);
			java.util.Map<net.runelite.client.hiscore.HiscoreSkill, JLabel> labels = new java.util.LinkedHashMap<>();
			labels.put(boss, new JLabel(new javax.swing.ImageIcon(new java.awt.image.BufferedImage(20, 20,
				java.awt.image.BufferedImage.TYPE_INT_ARGB))));
			when(cells.getBossLabels()).thenReturn(labels);
			when(cells.bossIcon(boss)).thenReturn(new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB));
			when(cells.getPvpActivityIcons()).thenReturn(new java.awt.image.BufferedImage[5]);
			ClogPages pages = mock(ClogPages.class);
			SummaryCards built = cards(session, cells, pages);
			JLabel owner = new JLabel();
			new JPanel().add(owner);
			PvmSummaryTooltip tip = (PvmSummaryTooltip) built.buildPvm(owner);
			assertSame(boss, TestViews.field(tip, "mostKilledBoss"));
			assertTrue(TestViews.field(tip, "mostKilledIcon") != null);
			java.util.function.Consumer<java.awt.event.MouseEvent> bosses = TestViews.field(tip, "onOpenBosses");
			java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(owner, java.awt.event.MouseEvent.MOUSE_PRESSED,
				0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
			bosses.accept(press);
			verify(pages).openTab(eq(owner), eq(press), eq("Bosses"), eq("< Combat Summary"));
		}
	}

	@Test
	public void comparingLinesUpTheCombatSummariesWhenOnlyTheRivalHasCombatAchievements()
	{
		// The rival's tier puts its reward under the title and its lines in PvM; the blue side, with neither, keeps the
		// same row and lines, so the pair's tiles sit level.
		Cells cells = mock(Cells.class);
		when(cells.getBossLabels()).thenReturn(java.util.Collections.emptyMap());
		when(cells.getPvpActivityIcons()).thenReturn(new java.awt.image.BufferedImage[5]);
		when(comparison.isComparisonMode()).thenReturn(true);
		when(comparison.showsPair()).thenReturn(true);
		java.util.Map<CombatAchievementTier, Integer> done = new java.util.EnumMap<>(CombatAchievementTier.class);
		done.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		when(comparison.getCompareCaResult()).thenReturn(CombatAchievementResult.of(done, null));
		java.util.List<JToolTip> sides = new java.util.ArrayList<>();
		when(comparison.wrapSideBySide(any(), any(), any())).thenAnswer(call ->
		{
			sides.add(call.getArgument(1));
			sides.add(call.getArgument(2));
			return new JToolTip();
		});
		JLabel owner = new JLabel();
		new JPanel().add(owner);
		cards(mock(LookupSession.class), cells, mock(ClogPages.class)).buildPvm(owner);
		assertSame(2, sides.size());
		for (JToolTip side : sides)
		{
			assertTrue("the gear row", TestViews.<Boolean>field(side, "gearRow"));
			assertTrue("the Combat Achievements lines", TestViews.<Boolean>field(side, "caRows"));
		}
	}

	@Test
	public void aLookedUpPlayersCombatSummaryShowsTheFullerOfTheirSyncAndRuneProfile()
	{
		Cells cells = mock(Cells.class);
		when(cells.getBossLabels()).thenReturn(java.util.Collections.emptyMap());
		when(cells.getPvpActivityIcons()).thenReturn(new java.awt.image.BufferedImage[5]);
		LookupSession session = mock(LookupSession.class);
		java.util.Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		java.util.Map<CombatAchievementTier, Integer> mediumDone = new java.util.EnumMap<>(easyDone);
		mediumDone.put(CombatAchievementTier.MEDIUM, CombatAchievementTier.MEDIUM.totalTasks());
		CombatAchievementResult easy = CombatAchievementResult.of(easyDone, null);
		CombatAchievementResult medium = CombatAchievementResult.of(mediumDone, null);
		ClogResult clog = new ClogResult("Tester", java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), null, null);
		when(session.getClogResult()).thenReturn(clog);
		JLabel owner = new JLabel();
		new JPanel().add(owner);
		SummaryCards cards = cards(session, cells, mock(ClogPages.class));

		when(session.getCaResult()).thenReturn(easy);
		clog.setSyncedCa(medium);
		assertSame("the Medium their sync sent, not RuneProfile's Easy", medium,
			TestViews.field(cards.buildPvm(owner), "caResult"));

		when(session.getCaResult()).thenReturn(medium);
		clog.setSyncedCa(easy);
		assertSame("RuneProfile's Medium, not the Easy of a sync they stopped", medium,
			TestViews.field(cards.buildPvm(owner), "caResult"));
	}

	@Test
	public void eachRaidTileGetsItsOwnMegarareCount()
	{
		// The tiles read the counts in raids.tsv's order: here two Scythes on ToB's page and an Obligator on TFA's.
		LookupSession session = mock(LookupSession.class);
		when(session.getHiscoreResult()).thenReturn(TestViews.hiscore(AccountType.REGULAR, java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), 0, 0, 0, -1));
		java.util.Map<String, java.util.List<ClogResult.ClogItem>> obtained = new java.util.LinkedHashMap<>();
		obtained.put("theatre_of_blood", java.util.Collections.singletonList(new ClogResult.ClogItem(22486, 2, null)));
		obtained.put("the_fractured_archive", java.util.Collections.singletonList(new ClogResult.ClogItem(34717, 1, null)));
		java.util.Map<String, java.util.List<Integer>> pages = new java.util.LinkedHashMap<>();
		pages.put("theatre_of_blood", java.util.Collections.singletonList(22486));
		pages.put("the_fractured_archive", java.util.Collections.singletonList(34717));
		when(session.getClogResult()).thenReturn(new ClogResult("Probe", obtained, pages, java.util.Collections.emptyMap(),
			null, null));
		Cells cells = mock(Cells.class);
		when(cells.getBossLabels()).thenReturn(java.util.Collections.emptyMap());
		when(cells.getPvpActivityIcons()).thenReturn(new java.awt.image.BufferedImage[5]);
		SummaryCards built = cards(session, cells, mock(ClogPages.class));
		JLabel owner = new JLabel();
		new JPanel().add(owner);
		PvmSummaryTooltip tip = (PvmSummaryTooltip) built.buildPvm(owner);
		org.junit.Assert.assertArrayEquals(new int[]{0, 2, 0, 1}, TestViews.field(tip, "weaponCounts"));
	}

	@Test
	public void theMostKilledTileCarriesItsBossesLog()
	{
		LookupSession session = mock(LookupSession.class);
		java.util.Map<String, Integer> kills = new java.util.HashMap<>();
		kills.put("Vorkath", 900);
		when(session.getHiscoreResult()).thenReturn(TestViews.hiscore(AccountType.REGULAR, kills,
			java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), 0, 0, 0, -1));
		when(session.getClogResult()).thenReturn(new ClogResult("Tester", java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), null, null));
		Cells cells = mock(Cells.class);
		java.util.Map<net.runelite.client.hiscore.HiscoreSkill, JLabel> labels = new java.util.LinkedHashMap<>();
		labels.put(net.runelite.client.hiscore.HiscoreSkill.VORKATH, new JLabel());
		when(cells.getBossLabels()).thenReturn(labels);
		when(cells.getPvpActivityIcons()).thenReturn(new java.awt.image.BufferedImage[5]);
		java.util.Map<net.runelite.client.hiscore.HiscoreSkill, TooltipData> data = new java.util.HashMap<>();
		data.put(net.runelite.client.hiscore.HiscoreSkill.VORKATH, TooltipData.builder().name("Vorkath").obtainedCount(3)
			.totalItems(7).build());
		when(cells.getTooltipDataMap()).thenReturn(data);
		SummaryCards built = cards(session, cells, mock(ClogPages.class));
		JLabel owner = new JLabel();
		new JPanel().add(owner);
		PvmSummaryTooltip tip = (PvmSummaryTooltip) built.buildPvm(owner);
		assertSame(3, (int) TestViews.field(tip, "mostKilledObtained"));
		assertSame(7, (int) TestViews.field(tip, "mostKilledTotal"));
	}
}
