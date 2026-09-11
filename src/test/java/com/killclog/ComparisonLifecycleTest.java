package com.killclog;

import java.util.HashMap;
import org.junit.Test;
import static com.killclog.LookupTestFixture.*;
import static org.junit.Assert.*;

public class ComparisonLifecycleTest
{
	@Test
	public void exitCancelsPendingLookupBeforeHiscoreAndAfterHiscore() throws Exception
	{
		for (boolean hiscoreFirst : new boolean[]{false, true})
		{
			LookupTestFixture fixture = new LookupTestFixture();
			edt(() -> fixture.comparison.doCompareLookup("Red", "Blue"));
			if (hiscoreFirst)
			{
				fixture.hiscores.get("Red").complete(hiscore(2));
				edt(() -> assertTrue(fixture.comparison.isCompareLookupInFlight()));
			}
			edt(fixture.comparison::exit);
			fixture.hiscores.get("Red").complete(hiscore(2));
			if (hiscoreFirst) fixture.clogs.get("Red").complete(clog("Red"));
			fixture.cas.get("Red").complete(ca(2));
			edt(() -> assertFalse(fixture.comparison.isComparisonMode()));
			assertEquals(0, fixture.events("onComparisonEnter"));
			assertEquals(1, fixture.events("onComparisonExit"));
			assertNull(fixture.comparison.getCompareCaResult());
		}
	}

	@Test
	public void quietResetFencesBothSessionsAndAcceptsFreshLookups() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() ->
		{
			fixture.comparison.doCompareLookup("Red", "Blue");
			fixture.primary.start("Green", null, null);
			fixture.comparison.reset();
			fixture.primary.reset();
		});
		HashMap<String, Integer> before = new HashMap<>(fixture.events);
		fixture.hiscores.get("Red").complete(hiscore(2));
		fixture.hiscores.get("Green").complete(hiscore(3));
		fixture.clogs.get("Green").complete(clog("Green"));
		fixture.cas.get("Green").complete(ca(3));
		fixture.cas.get("Red").complete(ca(2));
		edt(() -> assertEquals(before, fixture.events));
		assertNull(fixture.primary.getHiscoreResult());
		assertNull(fixture.comparison.getCompareHiscoreResult());
		edt(() -> fixture.primary.start("Fresh", null, null));
		fixture.hiscores.get("Fresh").complete(hiscore(4));
		edt(() -> assertEquals(4, fixture.primary.getHiscoreResult().getTotalLevel()));
		edt(() -> fixture.comparison.doCompareLookup("Other", "Fresh"));
		assertTrue(fixture.hiscores.containsKey("Other"));
	}

	@Test
	public void cancelAfterPrimarySettlementStillFencesOptionalCa() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.primary.start("Green", null, null));
		fixture.hiscores.get("Green").complete(hiscore(3));
		edt(() ->
		{
			assertFalse(fixture.primary.isLookupInFlight());
			fixture.primary.cancelInFlight();
		});
		fixture.cas.get("Green").complete(ca(3));
		fixture.clogs.get("Green").complete(clog("Green"));
		edt(() -> assertNull(fixture.primary.getCaResult()));
		assertEquals(0, fixture.events("onCaResult"));
		assertEquals(0, fixture.events("onClogResult"));
	}

	@Test
	public void failedComparisonFencesLateCa() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.comparison.doCompareLookup("Missing", "Blue"));
		fixture.hiscores.get("Missing").complete(null);
		edt(() -> assertEquals(1, fixture.events("onCompareError")));
		fixture.cas.get("Missing").complete(ca(3));
		edt(() -> assertNull(fixture.comparison.getCompareCaResult()));
		assertFalse(fixture.comparison.isComparisonMode());
	}
}
