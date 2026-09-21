package com.killclog;

import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClogSummaryFallbackTest
{
	private HiscoreResult hiscore(int count)
	{
		return new HiscoreService(null, new Gson()).parseHiscoreBody(
			"{\"skills\":[],\"activities\":[{\"name\":\"Collections Logged\",\"rank\":123,\"score\":"
				+ count + "}]}", AccountType.REGULAR);
	}

	@Test
	public void officialTotalSurvivesWithoutProviderData()
	{
		HiscoreResult result = hiscore(1198);
		assertEquals(123, result.getActivityRank("Collections Logged"));
		assertArrayEquals(new int[]{1198, -1}, ClogHelper.summaryTotals(null, result, null));
	}

	@Test
	public void missingIsDistinctFromZero()
	{
		assertArrayEquals(new int[]{-1, -1}, ClogHelper.summaryTotals(null, null, null));
		assertArrayEquals(new int[]{-1, -1}, ClogHelper.summaryTotals(null, hiscore(-1), null));
		assertArrayEquals(new int[]{0, -1}, ClogHelper.summaryTotals(null, hiscore(0), null));
	}

	@Test
	public void catalogAddsDenominatorWithoutInventingObtainedItems()
	{
		ClogResult catalog = emptyClog();
		catalog.setUniqueTotal(1717);
		assertArrayEquals(new int[]{1198, 1717}, ClogHelper.summaryTotals(null, hiscore(1198), catalog));
		assertTrue(catalog.getObtainedItems().isEmpty());
	}

	@Test
	public void capturedTotalKeepsPrecedenceOverOlderHiscores()
	{
		ClogResult local = emptyClog();
		local.setUniqueObtained(1199);
		local.setUniqueTotal(1717);
		assertArrayEquals(new int[]{1199, 1717}, ClogHelper.summaryTotals(local, hiscore(1198), null));
	}

	@Test
	public void comparisonShowsIndependentTotalsAndClearsUnknown() throws Exception
	{
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			CompareClogTotalsBar bar = new CompareClogTotalsBar(() -> true,
				new TooltipController(new KillClogConfig()
				{
				}), owner -> new javax.swing.JToolTip());
			PanelIconCache icons = new PanelIconCache(null, null, null);
			bar.update(ClogHelper.summaryTotals(null, hiscore(1198), null),
				ClogHelper.summaryTotals(null, hiscore(0), null), icons);
			javax.swing.JLabel blue = (javax.swing.JLabel) bar.component().getComponent(0);
			javax.swing.JLabel red = (javax.swing.JLabel) bar.component().getComponent(1);
			assertEquals("1198", blue.getText());
			assertEquals("0", red.getText());
			bar.update(new int[]{-1, -1}, new int[]{42, -1}, icons);
			assertEquals("--", blue.getText());
			assertNull(blue.getIcon());
			assertEquals("42", red.getText());
		});
	}

	private ClogResult emptyClog()
	{
		return new ClogResult("test", Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), null, null);
	}
}
