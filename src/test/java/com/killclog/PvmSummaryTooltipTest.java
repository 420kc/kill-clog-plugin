package com.killclog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PvmSummaryTooltipTest
{
	@Test
	public void megaRareHoverNamesUseFullDisplayNames()
	{
		assertArrayEquals(new String[]{"Twisted Bow", "Scythe of Vitur", "Tumeken's Shadow"},
			PanelData.MEGARARE_ITEM_NAMES);
	}

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
	public void absentGloryFallsBackToKnownSolKc()
	{
		assertTrue(ColosseumGlory.hasHeaderScore(0, 17));
		assertEquals("KC: ", ColosseumGlory.headerLabel(0));
		assertEquals("17", ColosseumGlory.headerValue(0, 17));
		assertEquals(ColosseumGlory.LABEL, ColosseumGlory.headerLabel(33_333));
		assertEquals("33,333", ColosseumGlory.headerValue(33_333, 17));
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
