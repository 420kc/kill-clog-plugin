package com.killclog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PvmSummaryTooltipTest
{
	@Test
	public void testSlayerXpAlwaysReadsFullAmount()
	{
		// Solo PvM summary: exact digits at every magnitude, never rounded.
		// Max slayer xp is 200,000,000 and the tooltip has the width for it.
		assertEquals("350,000", PvmSummaryTooltip.slayerXpText(350_000L));
		assertEquals("9,741,203", PvmSummaryTooltip.slayerXpText(9_741_203L));
		assertEquals("14,200,000", PvmSummaryTooltip.slayerXpText(14_200_000L));
		assertEquals("200,000,000", PvmSummaryTooltip.slayerXpText(200_000_000L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(0L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(-1L));
	}

	@Test
	public void solHereditReplacesKcWithExactPositiveGlory()
	{
		assertTrue(ColosseumGlory.replacesKc("Sol Heredit"));
		assertFalse(ColosseumGlory.replacesKc("Fortis Colosseum"));
		assertEquals(33_333, ColosseumGlory.score(hiscoreWithGlory(33_333)));
		assertEquals("33,333", ColosseumGlory.format(33_333));
	}

	@Test
	public void absentGloryFollowsTheShibuiOmissionRule()
	{
		assertFalse(ColosseumGlory.isVisible(-1));
		assertFalse(ColosseumGlory.isVisible(0));
		assertTrue(ColosseumGlory.isVisible(1));
		assertEquals("--", ColosseumGlory.format(0));
		assertEquals(-1, ColosseumGlory.score(null));
	}

	@Test
	public void comparisonGloryRowAppearsOnlyForPositiveSignal()
	{
		CompareImgTooltip tip = new CompareImgTooltip();
		tip.setTitle("Sol Heredit");
		tip.setBluePlayer("Blue", 1, 10, 1, true);
		tip.setRedPlayer("Red", 2, 10, 2, true);
		int baseHeight = tip.getHeaderHeight();

		tip.setComparisonStat(ColosseumGlory.LABEL, 0, -1);
		assertFalse(tip.showComparisonStat());
		assertEquals(baseHeight, tip.getHeaderHeight());

		tip.setComparisonStat(ColosseumGlory.LABEL, 33_333, 0);
		assertTrue(tip.showComparisonStat());
		assertEquals(baseHeight + TitleTooltip.LINE_HEIGHT, tip.getHeaderHeight());
		assertEquals("33,333", tip.blueComparisonStatText());
		assertEquals("--", tip.redComparisonStatText());
	}

	private static HiscoreResult hiscoreWithGlory(int glory)
	{
		Map<String, Integer> scores = new HashMap<>();
		scores.put(ColosseumGlory.ACTIVITY_NAME, glory);
		return new HiscoreResult(AccountType.REGULAR,
			Collections.emptyMap(), Collections.emptyMap(), scores, Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}
}
