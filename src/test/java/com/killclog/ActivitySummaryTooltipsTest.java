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
		mock(Cells.class), tooltips, mock(ItemManager.class), mock(CaRewardSprites.class), () -> null);

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
}
