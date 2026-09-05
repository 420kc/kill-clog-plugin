package com.killclog;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionLogSearchSetupTest
{
	@Test
	public void onlyCollectionLogSearchStartsFirstTimeCapture()
	{
		int collectionLogHeader = KillClogPlugin.CLOG_INTERFACE << 16 | 1;
		int unrelatedWidget = 320 << 16 | 1;

		assertTrue(KillClogPlugin.isCollectionLogSearchClick("Search", collectionLogHeader));
		assertTrue(KillClogPlugin.isCollectionLogSearchClick(
			"<col=ff9040>Search</col>", collectionLogHeader));
		assertFalse(KillClogPlugin.isCollectionLogSearchClick("Lookup", collectionLogHeader));
		assertFalse(KillClogPlugin.isCollectionLogSearchClick("Search", unrelatedWidget));
		assertFalse(KillClogPlugin.isCollectionLogSearchClick("Search", -1));
	}

	@Test
	public void searchStreamFinalizesAfterThreeQuietTicksWithoutBackAction()
	{
		BulkCaptureState capture = new BulkCaptureState();
		capture.arm(100, 1, 1_500);
		capture.captureScriptArguments(new Object[]{4100, 995, 42}, 101);

		assertFalse(capture.readyToFinalize(103));
		assertTrue(capture.readyToFinalize(104));
	}

	@Test
	public void emptySearchSchedulesFinalizationWithoutItemScripts()
	{
		BulkCaptureState capture = new BulkCaptureState();
		capture.arm(200, 0, 1_500);
		capture.scheduleEmptySearchFinalization(200);

		assertFalse(capture.readyToFinalize(202));
		assertTrue(capture.readyToFinalize(203));
	}
}
