package com.killclog;

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
}
