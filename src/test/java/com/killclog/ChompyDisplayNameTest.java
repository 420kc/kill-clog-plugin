package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChompyDisplayNameTest
{
	@Test
	public void labelsKeepTierRequirementAndWikiIdentity()
	{
		assertEquals("30: Ogre Bowman", TooltipItemLink.itemName(null, 2978));
		assertEquals("1700: Ogre Dragon Archer", TooltipItemLink.itemName(null, 2992));
		assertEquals("1300: Expert", TooltipItemLink.itemName(null, 2991));
		assertEquals("4000: Expert Dragon Archer", TooltipItemLink.itemName(null, 2995));
		assertEquals("Other item", TooltipItemLink.displayName(2996, "Other item"));
		assertTrue(TooltipItemLink.wikiUrl(2991).endsWith("id=2991"));
	}
	@Test
	public void longLabelsKeepRequirementBeforeEllipsis() throws Exception
	{
		java.awt.FontMetrics metrics = new javax.swing.JLabel().getFontMetrics(
			net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		String name = TooltipItemLink.itemName(null, 2994);
		java.lang.reflect.Method fit = TitleTooltip.class.getDeclaredMethod(
			"fitHeaderText", java.awt.FontMetrics.class, String.class, int.class);
		fit.setAccessible(true);
		String fitted = (String) fit.invoke(null, metrics, name, 100);
		assertTrue(fitted.startsWith("3000: "));
		assertTrue(fitted.endsWith("..."));
		assertTrue(metrics.stringWidth(fitted) <= 100);
	}
}
