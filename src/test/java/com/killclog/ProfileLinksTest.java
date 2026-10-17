package com.killclog;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ProfileLinksTest
{
	@Test
	public void profilePagesAreTheOnesKillclogComOpens()
	{
		// killclog.com's provider badges (kc-app.js) and its profile path (kc-util.js) write the same addresses.
		assertEquals("https://templeosrs.com/player/overview.php?player=420+kc", TooltipItemLink.templeUrl("420 kc"));
		assertEquals("https://runeprofile.com/420%20kc", TooltipItemLink.runeProfileUrl("420 kc"));
		assertEquals("https://killclog.com/p/420-kc", TooltipItemLink.killclogUrl(" 420  kc "));
		assertEquals("https://runeprofile.com/clan/Clannabis", TooltipItemLink.runeProfileClanUrl("Clannabis"));
	}
}
