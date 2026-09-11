package com.killclog;

import org.junit.Test;
import static com.killclog.LookupTestFixture.*;
import static org.junit.Assert.*;

public class ComparisonSnapshotTest
{
	@Test
	public void replacementPublishesOnePlayerForEveryCaArrivalOrder() throws Exception
	{
		for (int caStage = 0; caStage < 3; caStage++)
		{
			for (boolean clogPresent : new boolean[]{false, true})
			{
				LookupTestFixture fixture = new LookupTestFixture();
				commitFirstPlayer(fixture);
				edt(() -> fixture.comparison.doCompareLookup("Second", "Blue"));
				assertPlayer(fixture, "First", 2, "First", 2);
				if (caStage == 0) fixture.cas.get("Second").complete(ca(3));
				fixture.hiscores.get("Second").complete(hiscore(3));
				edt(() -> assertTrue(fixture.comparison.isCompareLookupInFlight()));
				if (caStage == 1) fixture.cas.get("Second").complete(ca(3));
				assertPlayer(fixture, "First", 2, "First", 2);
				edt(() -> fixture.comparison.doCompareLookup("Third", "Blue"));
				assertFalse(fixture.hiscores.containsKey("Third"));
				assertEquals(1, fixture.events("onComparisonEnter"));
				fixture.clogs.get("Second").complete(clogPresent ? clog("Second") : null);
				assertPlayer(fixture, "Second", 3, clogPresent ? "Second" : null, caStage < 2 ? 3 : null);
				assertFalse(fixture.comparison.isCompareLookupInFlight());
				if (caStage == 2) fixture.cas.get("Second").complete(ca(3));
				assertPlayer(fixture, "Second", 3, clogPresent ? "Second" : null, 3);
				assertEquals(2, fixture.events("onComparisonEnter"));
			}
		}
	}

	@Test
	public void failedReplacementRetainsTheWholePreviousPlayer() throws Exception
	{
		for (boolean error : new boolean[]{false, true})
		{
			LookupTestFixture fixture = new LookupTestFixture();
			commitFirstPlayer(fixture);
			edt(() -> fixture.comparison.doCompareLookup("Missing", "Blue"));
			fixture.cas.get("Missing").complete(ca(3));
			if (error) fixture.hiscores.get("Missing").completeExceptionally(new IllegalStateException("fixture"));
			else fixture.hiscores.get("Missing").complete(null);
			assertPlayer(fixture, "First", 2, "First", 2);
			assertFalse(fixture.comparison.isCompareLookupInFlight());
			assertEquals(1, fixture.events("onCompareError"));
			assertEquals(0, fixture.events("onCompareDataReady"));
		}
	}

	@Test
	public void failedClogCommitsAbsentClogInsteadOfKeepingAnotherPlayersItems() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		commitFirstPlayer(fixture);
		edt(() -> fixture.comparison.doCompareLookup("Second", "Blue"));
		fixture.hiscores.get("Second").complete(hiscore(3));
		edt(() -> fixture.clogs.get("Second").completeExceptionally(new IllegalStateException("fixture")));
		assertPlayer(fixture, "Second", 3, null, null);
	}

	@Test
	public void mirrorCommitsBlueSnapshotAndFencesPreviousLateCa() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		edt(() -> fixture.comparison.doCompareLookup("First", "Blue"));
		fixture.hiscores.get("First").complete(hiscore(2));
		edt(() -> fixture.clogs.get("First").complete(clog("First")));
		edt(() -> fixture.comparison.doCompareLookup("Blue", "Blue"));
		fixture.cas.get("First").complete(ca(2));
		assertPlayer(fixture, "Blue", 1, "Blue", 1);
		assertFalse(fixture.hiscores.containsKey("Blue"));
		assertSame(fixture.primary.getNativeHiscoreResult(), fixture.comparison.getNativeCompareHiscoreResult());
	}

	private static void commitFirstPlayer(LookupTestFixture fixture) throws Exception
	{
		edt(() -> fixture.comparison.doCompareLookup("First", "Blue"));
		fixture.cas.get("First").complete(ca(2));
		fixture.hiscores.get("First").complete(hiscore(2));
		edt(() -> fixture.clogs.get("First").complete(clog("First")));
		assertPlayer(fixture, "First", 2, "First", 2);
	}

	private static void assertPlayer(LookupTestFixture fixture, String name, int level,
		String clogName, Integer points) throws Exception
	{
		edt(() ->
		{
			ComparisonController comparison = fixture.comparison;
			assertTrue(comparison.isComparisonMode());
			assertEquals(name, comparison.getCompareRsn());
			assertEquals(level, comparison.getCompareHiscoreResult().getTotalLevel());
			assertEquals(clogName, comparison.getCompareClogResult() == null
				? null : comparison.getCompareClogResult().getPlayerName());
			assertEquals(points, comparison.getCompareCaResult() == null
				? null : Integer.valueOf(comparison.getCompareCaResult().getTotalPoints()));
		});
	}
}
