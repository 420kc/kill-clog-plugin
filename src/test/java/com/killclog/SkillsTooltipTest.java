package com.killclog;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Field;
import java.util.Collections;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SkillsTooltipTest
{
	@Test
	public void totalXpAndOverallRankFillIdleRowsInBothSummaryModes()
	{
		HiscoreResult blue = hiscores(24_000_000L, 12_345);
		HiscoreResult red = hiscores(4_800_000_000L, 42);

		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(blue);
		assertEquals("24,000,000", solo.displayedXpText());
		assertEquals(blue.getOverallRank(), solo.displayedRank());

		SkillsTooltip redSide = new SkillsTooltip();
		redSide.setData(red);
		assertEquals("4,800,000,000", redSide.displayedXpText());
		assertEquals(red.getOverallRank(), redSide.displayedRank());
	}

	@Test
	public void missingHiscoresKeepBothIdleRowsEmpty()
	{
		SkillsTooltip solo = new SkillsTooltip();
		solo.setData(null);
		assertEquals("--", solo.displayedXpText());
		assertEquals(-1, solo.displayedRank());
	}

	@Test
	public void hoveringUsesSkillRankAndExitingRestoresOverallRank() throws Exception
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, 12_345));
		Field hovered = SkillsTooltip.class.getDeclaredField("hoveredSkill");
		hovered.setAccessible(true);
		hovered.set(tip, Skill.ATTACK);
		assertEquals(987, tip.displayedRank());
		assertEquals("13,034,431", tip.displayedXpText());

		MouseEvent exit = new MouseEvent(tip, MouseEvent.MOUSE_EXITED,
			System.currentTimeMillis(), 0, -1, -1, 0, false);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(exit);
		}
		assertEquals(12_345, tip.displayedRank());
		assertEquals("24,000,000", tip.displayedXpText());

		// An unranked hovered skill stays unknown instead of borrowing overall rank.
		hovered.set(tip, Skill.DEFENCE);
		assertEquals(-1, tip.displayedRank());
		tip.setData(null);
		assertEquals(-1, tip.displayedRank());
	}

	@Test
	public void unavailableOverallRankStaysUnknown()
	{
		SkillsTooltip tip = new SkillsTooltip();
		tip.setData(hiscores(24_000_000L, -1));
		assertEquals(-1, tip.displayedRank());
	}

	private static HiscoreResult hiscores(long totalXp, int overallRank)
	{
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.singletonMap("attack", 99),
			Collections.singletonMap("attack", 987), Collections.singletonMap("attack", 13_034_431L),
			2_376, totalXp, 126, overallRank);
	}
}
