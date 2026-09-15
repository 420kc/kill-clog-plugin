package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChompyDisplayNameTest
{
	@Test
	public void labelsKeepTierRequirementAndWikiIdentity()
	{
		assertEquals("Ogre Bowman - 30", TooltipItemLink.itemName(null, 2978));
		assertEquals("Ogre Dragon Archer - 1700", TooltipItemLink.itemName(null, 2992));
		assertEquals("Expert - 1300", TooltipItemLink.itemName(null, 2991));
		assertEquals("Expert Dragon Archer - 4000", TooltipItemLink.itemName(null, 2995));
		assertEquals("Other item", TooltipItemLink.displayName(2996, "Other item"));
		assertTrue(TooltipItemLink.wikiUrl(2991).endsWith("id=2991"));
	}
}
