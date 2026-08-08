package com.killclog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileCardDataBuilderTest
{
	@Test
	public void allowsOnlyTheLoggedInPlayersCard()
	{
		assertTrue(ProfileCardDataBuilder.isSelfPlayer("420 kc", "420 KC"));
		assertTrue(ProfileCardDataBuilder.isSelfPlayer(" 420 kc ", "420 kc"));
		assertFalse(ProfileCardDataBuilder.isSelfPlayer("Other player", "420 kc"));
		assertFalse(ProfileCardDataBuilder.isSelfPlayer("420 kc", null));
	}

	@Test
	public void makesReadablePlayerProfilePath()
	{
		assertEquals("killclog.com/p/420-kc", ProfileCardDataBuilder.profileUrl("420 kc"));
		assertEquals("log-chaser", ProfileCardShare.sanitize(" Log Chaser! "));
	}

	@Test
	public void localCardNeedsSelfAndLoadedDataButNotWebPublication()
	{
		assertTrue(ProfileCardDataBuilder.canBuild("420 kc", "420 KC", true, true));
		assertFalse(ProfileCardDataBuilder.canBuild("Other player", "420 kc", true, true));
		assertFalse(ProfileCardDataBuilder.canBuild("420 kc", "420 kc", false, true));
		assertFalse(ProfileCardDataBuilder.canBuild("420 kc", "420 kc", true, false));
	}
}
