package com.killclog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileCardSyncResultTest
{
	@Test
	public void onlyAPersistedSyncPublishesTheProfile()
	{
		SyncService.SyncResult published =
			new SyncService.SyncResult(true, false, 200, "published");
		SyncService.SyncResult dryRun =
			new SyncService.SyncResult(true, true, 200, "dry run");
		SyncService.SyncResult failed =
			new SyncService.SyncResult(false, false, 500, "failed");

		assertTrue(KillClogPlugin.profileWasPublished(published));
		assertEquals("synced!", KillClogPlugin.profileSyncStatus(published));
		assertFalse(KillClogPlugin.profileWasPublished(dryRun));
		assertEquals("sync not published", KillClogPlugin.profileSyncStatus(dryRun));
		assertFalse(KillClogPlugin.profileWasPublished(failed));
		assertEquals("sync failed", KillClogPlugin.profileSyncStatus(failed));
	}
}
