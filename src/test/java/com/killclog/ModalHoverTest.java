package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModalHoverTest
{
	@Test
	public void refreshedNameIsVisibleWithoutLeavingTheItem() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel panel = new JPanel();
			TooltipItemHover hover = new TooltipItemHover(panel);
			hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
				0, 4151, "Item 4151", new Rectangle(0, 0, 32, 32), true)));
			move(panel, 4, 4);
			assertEquals("Item 4151", hover.hoveredItemName());
			hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
				0, 4151, "Abyssal whip", new Rectangle(0, 0, 32, 32), true)));
			move(panel, 5, 4);
			assertEquals("Abyssal whip", hover.hoveredItemName());
			move(panel, 40, 40);
			assertNull(hover.hoveredItemName());
		});
	}

	@Test
	public void modalTitlesRemainPixelIdenticalDuringHover() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				for (TitleTooltip tip : fixtures())
				{
					BufferedImage idle = paint(tip);
					Dimension size = tip.getPreferredSize();
					assertTrue("No hover target in " + tip.getTitle(), hoverAny(tip));
					BufferedImage hovered = paint(tip);
					assertEquals(size, tip.getPreferredSize());
					for (int y = 0; y < 22; y++)
					{
						for (int x = 0; x < idle.getWidth(); x++)
						{
							assertEquals(tip.getTitle(), idle.getRGB(x, y), hovered.getRGB(x, y));
						}
					}
				}
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	private static TitleTooltip[] fixtures() throws ReflectiveOperationException
	{
		PvmSummaryTooltip pvm = new PvmSummaryTooltip();
		pvm.setData(126, 12345, 20, 60, "Zulrah", 5000);
		ClogSummaryTooltip clog = new ClogSummaryTooltip();
		clog.setTierData(125, 1600, null, null);
		clog.setClogSources(true, true, true, false);
		set(clog, "special", new ClogSummaryTooltip.Shelf(null, new int[]{13342},
			new String[]{"A very long collection log trophy name"}, new int[]{1}, null));
		set(clog, "recent", new ClogSummaryTooltip.Shelf(null, new int[]{4151},
			new String[]{"Abyssal whip"}, new int[]{3}, new String[]{"Sep 11"}));
		SummaryTooltip player = new SummaryTooltip();
		player.setData("Fixture", 12345, null, null, "Regular", null);
		set(player, "totalPetCount", 65);
		set(player, "petList", Arrays.asList(1337, 1338));
		Map<Integer, String> petNames = new HashMap<>();
		petNames.put(1337, "Pet snakeling");
		petNames.put(1338, "Abyssal orphan");
		set(player, "petNames", petNames);
		// The Skill Summary is two plain rows since 2.4.0: nothing in it hovers.
		return new TitleTooltip[]{pvm, clog, player};
	}

	private static void set(Object target, String name, Object value) throws ReflectiveOperationException
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static BufferedImage paint(TitleTooltip tip)
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		tip.paint(g);
		g.dispose();
		return image;
	}

	private static boolean hoverAny(TitleTooltip tip)
	{
		for (int y = 24; y < tip.getHeight(); y++)
		{
			for (int x = 0; x < tip.getWidth(); x++)
			{
				move(tip, x, y);
				if (tip.getHeaderHoverLineText() != null)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static void move(javax.swing.JComponent tip, int x, int y)
	{
		tip.dispatchEvent(new MouseEvent(tip, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
	}

	/** Standalone render fixture; synthetic tiles stand in for item sprites. */
	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				for (TitleTooltip tip : fixtures())
				{
					paint(tip);
					hoverAny(tip);
					ImageIO.write(paint(tip), "png", new File(args[0], tip.getTitle().replace(' ', '-') + ".png"));
				}
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}
}
