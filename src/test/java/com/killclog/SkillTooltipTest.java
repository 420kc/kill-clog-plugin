package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A skill card's bar of the way to its next level, as the card paints it (Dylan 11/17). */
public class SkillTooltipTest
{
	@Test
	public void theBarUnderXpToLevelFillsWithTheWayThroughTheLevelInTheGamesYellow()
	{
		// Agility 97 with 11,416,331 XP: 65% of the way from 97's XP to 98's.
		long xp = 11_416_331L;
		BufferedImage card = TestViews.paint(card(Skill.AGILITY, 97, xp, true));
		double way = (double) (xp - Experience.getXpForLevel(97))
			/ (Experience.getXpForLevel(98) - Experience.getXpForLevel(97));
		int[] fill = TestViews.longestRun(card, TitleTooltip.CLOG_YELLOW);
		int track = card.getWidth() - 2 * NativeTooltip.getInset();
		assertEquals("the fill starts at the card's inset", NativeTooltip.getInset(), fill[0]);
		assertEquals("as long as the way through the level", Math.round(track * way), fill[2]);
		assertEquals("3 px tall", CardBody.BAR_HEIGHT, fill[3]);
		assertEquals("no green while the level isn't maxed", 0, TestViews.longestRun(card, TitleTooltip.CLOG_GREEN)[2]);
		// Straight under the last stat line: XP to Level's label is the lowest one, and the bar just under it.
		int label = TestViews.lowestRowAbove(card, NativeTooltip.labelColor, fill[1]);
		assertTrue("the bar sits just under XP to Level, " + (fill[1] - label) + " px below it",
			label > 0 && fill[1] - label <= 8);
		assertEquals("no label under the bar", label, TestViews.lowestRowAbove(card, NativeTooltip.labelColor,
			card.getHeight()));
	}

	@Test
	public void aMaxedLevelsBarIsFullAndGreenAsItsMaxedLine()
	{
		// Without virtual levels 99 is maxed; with them, 200M XP (126).
		for (BufferedImage card : new BufferedImage[]{TestViews.paint(card(Skill.FISHING, 99, 13_035_610L, false)),
			TestViews.paint(card(Skill.STRENGTH, 99, 200_000_000L, true))})
		{
			int track = card.getWidth() - 2 * NativeTooltip.getInset();
			int[] fill = TestViews.longestRun(card, TitleTooltip.CLOG_GREEN);
			assertEquals(NativeTooltip.getInset(), fill[0]);
			assertEquals("the whole track", track, fill[2]);
			assertEquals(CardBody.BAR_HEIGHT, fill[3]);
		}
		// With virtual levels a 99 isn't maxed: it's 1,179 XP into the way to 100, no green.
		BufferedImage toward100 = TestViews.paint(card(Skill.FISHING, 99, 13_035_610L, true));
		assertEquals(0, TestViews.longestRun(toward100, TitleTooltip.CLOG_GREEN)[2]);
	}

	@Test
	public void aSkillWithNoHiscoresKeepsAnEmptyTrackAndTheCardsHeight()
	{
		SkillTooltip unknown = new SkillTooltip();
		unknown.setData(Skill.AGILITY, null, true, Collections.emptyList(), null);
		BufferedImage card = TestViews.paint(unknown);
		assertEquals(0, TestViews.longestRun(card, TitleTooltip.CLOG_YELLOW)[2]);
		assertEquals(0, TestViews.longestRun(card, TitleTooltip.CLOG_GREEN)[2]);
		assertEquals("the bar's room stays, so nothing moves when the hiscores land",
			card(Skill.AGILITY, 97, 11_416_331L, true).getPreferredSize().height, card.getHeight());
	}

	private static SkillTooltip card(Skill skill, int level, long xp, boolean virtualLevels)
	{
		String key = skill.getName().toLowerCase(java.util.Locale.ROOT);
		Map<String, Integer> levels = new HashMap<>();
		levels.put(key, level);
		Map<String, Long> xps = new HashMap<>();
		xps.put(key, xp);
		HiscoreResult result = TestViews.hiscore(AccountType.REGULAR, HiscoreTable.STANDARD, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), levels,
			Collections.singletonMap(key, 31_644), xps, 2_000, 200_000_000L, 120, 1);
		SkillTooltip tip = new SkillTooltip();
		tip.setData(skill, result, virtualLevels, Collections.emptyList(), null);
		return tip;
	}
}
