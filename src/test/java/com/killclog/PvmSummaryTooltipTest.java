package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class PvmSummaryTooltipTest
{
	@Test
	public void testSlayerXpReadsFullAmountUnderTenMillion()
	{
		// Solo PvM summary: exact digits below 10m, shorthand from 10m up.
		assertEquals("350,000", PvmSummaryTooltip.slayerXpText(350_000L));
		assertEquals("9,741,203", PvmSummaryTooltip.slayerXpText(9_741_203L));
		assertEquals("9,999,999", PvmSummaryTooltip.slayerXpText(9_999_999L));
		assertEquals("10.0M", PvmSummaryTooltip.slayerXpText(10_000_000L));
		assertEquals("14.2M", PvmSummaryTooltip.slayerXpText(14_200_000L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(0L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(-1L));
	}
}
