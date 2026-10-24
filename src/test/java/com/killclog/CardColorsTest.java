package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CardColorsTest
{
	private static final Color TEAL = new Color(0, 40, 40);
	private static final Color LIME = new Color(180, 230, 0);

	@After
	public void theGamesColors()
	{
		NativeTooltip.cardColors(null, null, null, null, null);
	}

	@Test
	public void aFlatBackgroundAndBorderPaintTheirColorsAndTheCardKeepsItsSize()
	{
		Dimension plain = card().getPreferredSize();
		BufferedImage before = paint(card());

		NativeTooltip.cardColors(null, null, null, TEAL, LIME);
		ClogTabTooltip card = card();
		assertEquals("nothing moves", plain, card.getPreferredSize());
		BufferedImage image = paint(card);
		int w = image.getWidth();
		int h = image.getHeight();
		// The frame: a dark outer line, then the border's color for the rest of its three pixels.
		assertEquals(new Color(26, 26, 26).getRGB(), image.getRGB(0, h / 2));
		assertEquals(LIME.getRGB(), image.getRGB(1, h / 2));
		assertEquals(LIME.getRGB(), image.getRGB(w - 3, h / 2));
		// Inside it, the background where nothing is drawn.
		assertEquals(TEAL.getRGB(), image.getRGB(4, h - 5));
		assertNotEquals(before.getRGB(4, h - 5), image.getRGB(4, h - 5));
	}

	@Test
	public void aColorAtItsDefaultKeepsTheSpritesAsUnsetDoes()
	{
		NativeTooltip.cardColors(null, null, null, NativeTooltip.FALLBACK_BG, NativeTooltip.BORDER_MID);
		BufferedImage atDefault = paint(card());
		NativeTooltip.cardColors(null, null, null, null, null);
		BufferedImage unset = paint(card());
		assertEquals(Arrays.toString(pixels(unset)), Arrays.toString(pixels(atDefault)));
	}

	private static ClogTabTooltip card()
	{
		ClogTabTooltip card = new ClogTabTooltip();
		card.setTab("Bosses", new int[]{12, 34}, 34);
		card.setPages(new String[]{"Vorkath"}, new int[]{3}, new int[]{9}, null);
		return card;
	}

	private static BufferedImage paint(ClogTabTooltip card)
	{
		Dimension size = card.getPreferredSize();
		card.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
