package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
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
				4151, "Item 4151", new Rectangle(0, 0, 32, 32), true, 4)));
			move(panel, 4, 4);
			assertEquals("Item 4151", hover.hoveredItemName());
			hover.setHitBoxes(Collections.singletonList(new TooltipItemHover.HitBox(
				4151, "Abyssal whip", new Rectangle(0, 0, 32, 32), true, 4)));
			move(panel, 5, 4);
			assertEquals("Abyssal whip", hover.hoveredItemName());
			assertEquals("x4", hover.hoveredDuplicateCountText());
			move(panel, 40, 40);
			assertNull(hover.hoveredItemName());
			assertNull(hover.hoveredDuplicateCountText());
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
		clog.setTierData(125, 1600, null);
		clog.setClogSources(true, true, true);
		set(clog, "specialCount", 1);
		set(clog, "specialSprites", new BufferedImage[]{tile(24)});
		set(clog, "specialIds", new int[]{13342});
		set(clog, "specialNames", new String[]{"A very long collection log trophy name"});
		set(clog, "recentCount", 1);
		set(clog, "recentSprites", new BufferedImage[]{tile(24)});
		set(clog, "recentIds", new int[]{4151});
		set(clog, "recentNames", new String[]{"Abyssal whip"});
		set(clog, "recentDates", new String[]{"Sep 11"});
		SummaryTooltip player = new SummaryTooltip();
		player.setData("Fixture", 12345, null, null, "Regular", null);
		set(player, "totalPetCount", 65);
		set(player, "petList", Arrays.asList(1337, 1338));
		set(player, "petNames", new String[]{"Pet snakeling", "Abyssal orphan"});
		set(player, "petSprites", new BufferedImage[]{tile(15), tile(15)});
		SkillsTooltip skills = new SkillsTooltip();
		skills.setData(null);
		return new TitleTooltip[]{pvm, clog, player, skills};
	}

	private static BufferedImage tile(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(120, 150, 160));
		g.fillRect(2, 2, size - 4, size - 4);
		g.dispose();
		return image;
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
