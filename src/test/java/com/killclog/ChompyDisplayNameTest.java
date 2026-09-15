package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChompyDisplayNameTest
{
	@Test
	public void labelsKeepTierRequirementAndWikiIdentity()
	{
		assertEquals("Chompy Bird Hat (Ogre bowman): 30", TooltipItemLink.itemName(null, 2978));
		assertEquals("Chompy Bird Hat (Expert): 1300", TooltipItemLink.itemName(null, 2991));
		assertEquals("Chompy Bird Hat (Expert dragon archer): 4000", TooltipItemLink.itemName(null, 2995));
		assertEquals("Other item", TooltipItemLink.displayName(2996, "Other item"));
		assertTrue(TooltipItemLink.wikiUrl(2991).endsWith("id=2991"));
	}
}
