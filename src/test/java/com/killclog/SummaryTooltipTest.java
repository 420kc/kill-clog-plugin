package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
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
		// No stats, figure or pets: the title alone sets the width.
		SummaryTooltip plain = new SummaryTooltip();
		plain.setData("Fixture", -1, null, null, null, null);
		SummaryTooltip badged = new SummaryTooltip();
		badged.setData("Fixture", -1, null, new BufferedImage(13, 12, BufferedImage.TYPE_INT_ARGB), null, null);

		assertEquals(13 + 4, badged.getPreferredSize().width - plain.getPreferredSize().width);
	}

	@Test
	public void aBadgeTallerThanTheTitleLineIsScaledToIt()
	{
		SummaryTooltip plain = new SummaryTooltip();
		plain.setData("Fixture", -1, null, null, null, null);
		SummaryTooltip badged = new SummaryTooltip();
		badged.setData("Fixture", -1, null, new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB), null, null);

		assertEquals(18 + 4, badged.getPreferredSize().width - plain.getPreferredSize().width);
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
}
