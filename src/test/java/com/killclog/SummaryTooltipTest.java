package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import org.junit.Test;

public class SummaryTooltipTest
{
	@Test
	public void theFigureLosesOnlyTheEmptyRowsAboveIt()
	{
		BufferedImage portrait = new BufferedImage(62, 104, BufferedImage.TYPE_INT_ARGB);
		portrait.setRGB(30, 17, 0xff302020);
		portrait.setRGB(30, 103, 0xff302020);

		BufferedImage trimmed = SummaryTooltip.withoutHeadroom(portrait);

		assertEquals(62, trimmed.getWidth());
		assertEquals(87, trimmed.getHeight());
		assertEquals(0xff302020, trimmed.getRGB(30, 0));
	}

	@Test
	public void aFigureWithoutHeadroomOrNoFigureIsLeftAlone()
	{
		BufferedImage cape = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
		cape.setRGB(5, 0, 0xffffffff);

		assertSame(cape, SummaryTooltip.withoutHeadroom(cape));
		assertNull(SummaryTooltip.withoutHeadroom(null));
	}

	@Test
	public void theBadgeStandsBeforeTheNameAtItsOwnSize()
	{
		// A name long enough to set the width past the card's floor.
		SummaryTooltip plain = new SummaryTooltip();
		plain.setData("WWWWWWWWWWWW", -1, null, null, null, null);
		SummaryTooltip badged = new SummaryTooltip();
		badged.setData("WWWWWWWWWWWW", -1, null, new BufferedImage(13, 12, BufferedImage.TYPE_INT_ARGB), null, null);

		assertEquals(13 + 4, badged.getPreferredSize().width - plain.getPreferredSize().width);
	}

	@Test
	public void aBadgeTallerThanTheTitleLineIsScaledToIt()
	{
		SummaryTooltip plain = new SummaryTooltip();
		plain.setData("WWWWWWWWWWWW", -1, null, null, null, null);
		SummaryTooltip badged = new SummaryTooltip();
		badged.setData("WWWWWWWWWWWW", -1, null, new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB), null, null);

		assertEquals(18 + 4, badged.getPreferredSize().width - plain.getPreferredSize().width);
	}

	@Test
	public void aShortNameKeepsTheOldTitlesWidth()
	{
		SummaryTooltip shortName = new SummaryTooltip();
		shortName.setData("Al", -1, null, new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB), "Ironman", null);
		SummaryTooltip oldTitle = new SummaryTooltip();
		oldTitle.setData("Player Summary", -1, null, null, null, null);
		SummaryTooltip longName = new SummaryTooltip();
		longName.setData("WWWWWWWWWWWW", -1, null, null, null, null);

		assertEquals(oldTitle.getPreferredSize().width, shortName.getPreferredSize().width);
		assertTrue(longName.getPreferredSize().width > oldTitle.getPreferredSize().width);
	}

	@Test
	public void theFigureStandsAboveTheStats()
	{
		BufferedImage portrait = new BufferedImage(62, 104, BufferedImage.TYPE_INT_ARGB);
		for (int y = 17; y < 104; y++)
		{
			portrait.setRGB(30, y, 0xff302020);
		}
		SummaryTooltip without = new SummaryTooltip();
		without.setData("Fixture", 12345, null, null, "Ironman", "Maxed Infernal");
		SummaryTooltip with = new SummaryTooltip();
		with.setData("Fixture", 12345, portrait, null, "Ironman", "Maxed Infernal");

		// The trimmed 87 px character and a 4 px gap, over the same two stat lines.
		assertEquals(87 + 4, with.getPreferredSize().height - without.getPreferredSize().height);
	}

	@Test
	public void petsFollowTheTitlesDividerWhenNothingStandsAboveThem() throws ReflectiveOperationException
	{
		SummaryTooltip bare = new SummaryTooltip();
		bare.setData("Fixture", -1, null, null, null, null);
		SummaryTooltip ranked = new SummaryTooltip();
		ranked.setData("Fixture", 12345, null, null, "Ironman", null);
		petTotal(bare, 65);
		petTotal(ranked, 65);

		// One stats line and its divider (6 + 1 + 6) above the pets, against nothing and no second divider.
		assertEquals(14 + 13, ranked.getPreferredSize().height - bare.getPreferredSize().height);
	}

	private static void petTotal(SummaryTooltip tip, int total) throws ReflectiveOperationException
	{
		Field field = SummaryTooltip.class.getDeclaredField("totalPetCount");
		field.setAccessible(true);
		field.setInt(tip, total);
	}

	@Test
	public void aMissingNameStillTitlesTheCard()
	{
		SummaryTooltip unnamed = new SummaryTooltip();
		unnamed.setData(null, -1, null, null, null, null);
		SummaryTooltip player = new SummaryTooltip();
		player.setData("Player", -1, null, null, null, null);

		assertEquals(player.getPreferredSize(), unnamed.getPreferredSize());
	}
}
