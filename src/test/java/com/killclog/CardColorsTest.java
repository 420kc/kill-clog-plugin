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
import static org.junit.Assert.assertTrue;

public class CardColorsTest
{
	private static final Color TEAL = new Color(0, 40, 40);
	private static final Color LIME = new Color(180, 230, 0);

	@After
	public void theGamesColors()
	{
		NativeTooltip.cardColors(null, null, null, null, null, null);
	}

	@Test
	public void aFlatBackgroundAndBorderPaintTheirColorsAndTheCardKeepsItsSize()
	{
		Dimension plain = card().getPreferredSize();
		BufferedImage before = paint(card());

		NativeTooltip.cardColors(null, null, null, TEAL, LIME, null);
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
		NativeTooltip.cardColors(null, null, null, NativeTooltip.FALLBACK_BG, NativeTooltip.BORDER_MID, NativeTooltip.OSRS_ORANGE);
		BufferedImage atDefault = paint(card());
		NativeTooltip.cardColors(null, null, null, null, null, null);
		BufferedImage unset = paint(card());
		assertEquals(Arrays.toString(pixels(unset)), Arrays.toString(pixels(atDefault)));
	}

	@Test
	public void theScrollBarTakesItsColorAndItsTrackADarkerShadeWhateverTheDividersAre()
	{
		Color orange = new Color(220, 138, 0);
		// Orange dividers no longer make an orange track: the track is the thumb's own darker shade.
		NativeTooltip.cardColors(null, null, orange, null, null, null);
		assertEquals(NativeTooltip.OSRS_ORANGE, NativeTooltip.railThumb());
		assertNotEquals(orange, NativeTooltip.railTrackFor(NativeTooltip.railThumb()));
		assertTrue("the track sits well under the thumb",
			brightness(NativeTooltip.railTrackFor(NativeTooltip.railThumb())) * 3 < brightness(NativeTooltip.railThumb()));
		NativeTooltip.cardColors(null, null, orange, null, null, LIME);
		assertEquals(LIME, NativeTooltip.railThumb());
		assertEquals(new Color(54, 69, 0), NativeTooltip.railTrackFor(LIME));
	}

	private static int brightness(Color c)
	{
		return c.getRed() + c.getGreen() + c.getBlue();
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
