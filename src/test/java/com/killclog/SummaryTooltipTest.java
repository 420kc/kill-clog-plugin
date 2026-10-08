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
		SummaryTooltip prestiged = new SummaryTooltip();
		prestiged.setData("Fixture", 12345, null, null, "Ironman", "Maxed Infernal");
		petTotal(bare, 65);
		petTotal(ranked, 65);
		petTotal(prestiged, 65);

		// The account line reads in the header, one line under the name: the pets still follow the title's divider.
		assertEquals(NativeTooltip.LINE_HEIGHT, ranked.getPreferredSize().height - bare.getPreferredSize().height);
		// A prestige line stands between: it and the band's own divider (4 + 1 + 6).
		assertEquals(14 + 11, prestiged.getPreferredSize().height - ranked.getPreferredSize().height);
	}

	@Test
	public void theAccountLineReadsUnderTheName()
	{
		SummaryTooltip tip = new SummaryTooltip();
		tip.setData("Fixture", 17000, null, null, "Ironman", null);
		SummaryTooltip unranked = new SummaryTooltip();
		unranked.setData("Fixture", -1, null, null, null, null);

		assertEquals(unranked.getHeaderHeight() + NativeTooltip.LINE_HEIGHT, tip.getHeaderHeight());
	}

	@Test
	public void aPublishedCharactersCapeNamesItsPrestigeFromTheHeader()
	{
		BufferedImage character = new BufferedImage(60, 120, BufferedImage.TYPE_INT_ARGB);
		BufferedImage cape = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++)
		{
			for (int x = 0; x < 36; x++)
			{
				cape.setRGB(x, y, 0xffff00ff);
			}
		}
		SummaryTooltip tip = new SummaryTooltip();
		tip.setData("Fixture", 12345, character, null, "Ironman", "Maxed Infernal");
		tip.setCape(cape);
		BufferedImage painted = paint(tip);

		int left = -1;
		int top = -1;
		for (int y = 0; y < painted.getHeight() && top < 0; y++)
		{
			for (int x = 0; x < painted.getWidth(); x++)
			{
				if (painted.getRGB(x, y) == 0xffff00ff)
				{
					left = x;
					top = y;
					break;
				}
			}
		}
		// In the header's right corner, above the title's divider.
		assertTrue(top >= 0 && top < tip.getHeaderHeight() + NativeTooltip.getInset());
		assertTrue(left > painted.getWidth() / 2);
		for (java.awt.event.MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(new java.awt.event.MouseEvent(tip, java.awt.event.MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, left + 18, top + 16, 0, false, java.awt.event.MouseEvent.NOBUTTON));
		}
		assertEquals("Maxed Infernal", tip.getHeaderHoverLineText());
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
	public void thePetsScrollUnderTheBandWhileTheCharacterStays()
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
		SummaryTooltip many = withPets(figure, 60);
		// A small card shows whole; past the window the pets stop growing the card and scroll instead.
		assertTrue(few.getPreferredSize().height < many.getPreferredSize().height);
		assertEquals(many.getPreferredSize(), withPets(figure, 70).getPreferredSize());

		// Three notches down the pets have moved, and the character stands where it was.
		BufferedImage before = paint(many);
		int top = firstMagentaRow(before);
		assertTrue(top > 0);
		for (java.awt.event.MouseWheelListener listener : many.getMouseWheelListeners())
		{
			listener.mouseWheelMoved(new java.awt.event.MouseWheelEvent(many, java.awt.event.MouseEvent.MOUSE_WHEEL,
				0, 0, 10, 100, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3));
		}
		BufferedImage after = paint(many);
		assertEquals(top, firstMagentaRow(after));
		assertTrue(differs(before, after));
	}

	private static boolean differs(BufferedImage a, BufferedImage b)
	{
		for (int y = 0; y < a.getHeight(); y++)
		{
			for (int x = 0; x < a.getWidth(); x++)
			{
				if (a.getRGB(x, y) != b.getRGB(x, y))
				{
					return true;
				}
			}
		}
		return false;
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

	@Test
	public void theHeadersCapeKeepsClearOfALongAccountLine()
	{
		SummaryTooltip without = new SummaryTooltip();
		without.setData("Al", 123456, null, null, "Hardcore Group Ironman", "Maxed Infernal");
		SummaryTooltip with = new SummaryTooltip();
		with.setData("Al", 123456, null, null, "Hardcore Group Ironman", "Maxed Infernal");
		with.setCape(new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));

		// The account line under a short name is the widest header line: the cape's room sits beside it.
		assertEquals(40, with.getPreferredSize().width - without.getPreferredSize().width);
	}
}
