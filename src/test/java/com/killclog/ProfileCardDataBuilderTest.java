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
}
