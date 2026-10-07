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

	@Test
	public void theWholeCardScrollsUnderItsTitleAndBand()
	{
		// A character whose top ten rows are solid, the rest clear.
		BufferedImage figure = new BufferedImage(149, 200, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 10; y++)
		{
			for (int x = 0; x < 149; x++)
			{
				figure.setRGB(x, y, 0xffff00ff);
			}
		}
		SummaryTooltip few = withPets(null, 12);
		SummaryTooltip many = withPets(figure, 50);
		// A small card shows whole; past the window a card stops growing and scrolls instead.
		assertTrue(few.getPreferredSize().height < many.getPreferredSize().height);
		assertEquals(many.getPreferredSize(), withPets(figure, 70).getPreferredSize());

		// The character scrolls with the pets: three notches down, its top has left the window.
		int top = firstMagentaRow(paint(many));
		assertTrue(top > 0);
		for (java.awt.event.MouseWheelListener listener : many.getMouseWheelListeners())
		{
			listener.mouseWheelMoved(new java.awt.event.MouseWheelEvent(many, java.awt.event.MouseEvent.MOUSE_WHEEL,
				0, 0, 10, 100, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3));
		}
		assertEquals(-1, firstMagentaRow(paint(many)));
	}

	private static SummaryTooltip withPets(BufferedImage figure, int held)
	{
		SummaryTooltip tip = new SummaryTooltip();
		tip.setData("Fixture", 12345, figure, null, "Ironman", null);
		java.util.List<Integer> all = new java.util.ArrayList<>();
		java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
		for (int id = 1; id <= 71; id++)
		{
			all.add(id);
			if (id <= held)
			{
				counts.put(id, 1);
			}
		}
		tip.setPets(all, counts, null, id -> "Pet " + id);
		return tip;
	}

	private static BufferedImage paint(SummaryTooltip tip)
	{
		java.awt.Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		tip.paint(g);
		g.dispose();
		return image;
	}

	private static int firstMagentaRow(BufferedImage image)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			if (image.getRGB(image.getWidth() / 2, y) == 0xffff00ff)
			{
				return y;
			}
		}
		return -1;
	}

	@Test
	public void aWideCharactersPetsAllShowOrScroll() throws ReflectiveOperationException
	{
		// A character wider than five pet columns: the card paints wider than its header asked for.
		BufferedImage wide = new BufferedImage(205, 200, BufferedImage.TYPE_INT_ARGB);
		wide.setRGB(0, 0, 0xffffffff);
		Field hits = TooltipItemHover.class.getDeclaredField("hitBoxes");
		hits.setAccessible(true);
		Field bounds = TooltipItemHover.HitBox.class.getDeclaredField("bounds");
		bounds.setAccessible(true);
		for (int held = 1; held <= 71; held++)
		{
			SummaryTooltip card = withPets(wide, held);
			paint(card);
			for (Object hit : (java.util.List<?>) hits.get(card.itemHover))
			{
				java.awt.Rectangle box = (java.awt.Rectangle) bounds.get(hit);
				assertTrue(held + " pets", box.y + box.height <= card.getHeight() - NativeTooltip.getInset());
			}
		}
	}
}
