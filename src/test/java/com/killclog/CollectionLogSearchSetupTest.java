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
		capture.arm(200, 0, 0);
		capture.scheduleEmptySearchFinalization(200);

		assertFalse(capture.readyToFinalize(209));
		assertTrue(capture.readyToFinalize(210));
	}

	@Test
	public void itemStreamExtendsAnInitiallyEmptySearchDeadline()
	{
		BulkCaptureState capture = new BulkCaptureState();
		capture.arm(300, 0, 0);
		capture.scheduleEmptySearchFinalization(300);
		capture.captureScriptArguments(new Object[]{4100, 995, 42}, 302);

		assertFalse(capture.readyToFinalize(303));
		assertFalse(capture.readyToFinalize(304));
		assertTrue(capture.readyToFinalize(305));
	}

	@Test
	public void reportedItemsCancelEmptyDeadlineUntilScriptsArrive()
	{
		BulkCaptureState capture = new BulkCaptureState();
		capture.arm(400, 0, 0);
		capture.scheduleEmptySearchFinalization(400);
		capture.deferEmptyFinalizationIfItemsReported(5);

		assertFalse(capture.readyToFinalize(410));
		capture.captureScriptArguments(new Object[]{4100, 995, 42}, 411);
		assertTrue(capture.readyToFinalize(414));
	}
}
