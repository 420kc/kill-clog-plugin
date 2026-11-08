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

public class ActivitySummaryTooltipsTest
{
	private final ComparisonController comparison = mock(ComparisonController.class);
	private final TooltipController tooltips = mock(TooltipController.class);
	private final ActivitySummaryTooltips cards = new ActivitySummaryTooltips(mock(LookupSession.class), comparison,
		mock(Cells.class), tooltips, mock(ItemManager.class), mock(CaRewardSprites.class), () -> null, mock(ClogPages.class));

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
			ActivitySummaryTooltips built = new ActivitySummaryTooltips(session, comparison, cells, tooltips,
				mock(ItemManager.class), mock(CaRewardSprites.class), () -> CardLinks.ON, pages);
			JLabel owner = new JLabel();
			new JPanel().add(owner);
			PvmSummaryTooltip tip = (PvmSummaryTooltip) built.buildPvm(owner, (JPanel) owner.getParent());
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
		ActivitySummaryTooltips built = new ActivitySummaryTooltips(session, comparison, cells, tooltips,
			mock(ItemManager.class), mock(CaRewardSprites.class), () -> CardLinks.ON, mock(ClogPages.class));
		JLabel owner = new JLabel();
		new JPanel().add(owner);
		PvmSummaryTooltip tip = (PvmSummaryTooltip) built.buildPvm(owner, (JPanel) owner.getParent());
		assertSame(3, (int) TestViews.field(tip, "mostKilledObtained"));
		assertSame(7, (int) TestViews.field(tip, "mostKilledTotal"));
	}
}
