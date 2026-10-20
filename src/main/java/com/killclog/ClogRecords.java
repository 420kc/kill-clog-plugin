package com.killclog;

/** Pure record rules for the local store: no state, no I/O. */
final class ClogRecords
{
	private ClogRecords()
	{
	}

	/** Only an explicit completed Collection Log Search counts; marks alone never do. */
	static boolean hasCompletedFirstPartySetup(PlayerClogData data)
	{
		return data != null && Boolean.TRUE.equals(data.firstPartySetupComplete);
	}
}
